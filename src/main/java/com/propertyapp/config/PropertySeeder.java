package com.propertyapp.config;

import com.propertyapp.entity.locality.City;
import com.propertyapp.entity.locality.Locality;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.property.PropertyImage;
import com.propertyapp.entity.property.PropertyType;
import com.propertyapp.entity.user.User;
import com.propertyapp.repository.locality.CityRepository;
import com.propertyapp.repository.locality.LocalityRepository;
import com.propertyapp.repository.property.PropertyImageRepository;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import com.propertyapp.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Seeds a set of ACTIVE demo properties (with images) for non-prod profiles so
 * the Buyer discovery flow — Popular / Recommended / Nearest, search and
 * property detail — has real listings to show on a fresh database.
 *
 * <p>Runs at order 3 — after {@link DataInitializer} (1, users + property
 * types) and {@link LocalityInitializer} (2, cities + localities), both of
 * which it depends on. Gated by profile; idempotent — skips entirely once any
 * property exists, so it never duplicates on repeated boots and never touches
 * a database that already has real data.
 */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class PropertySeeder implements CommandLineRunner {

    private final PropertyRepository propertyRepository;
    private final PropertyImageRepository propertyImageRepository;
    private final PropertyTypeRepository propertyTypeRepository;
    private final CityRepository cityRepository;
    private final LocalityRepository localityRepository;
    private final UserRepository userRepository;
    private final Environment environment;

    /** Type-specific Unsplash photos — US residential property exteriors, no people. */
    private static final List<String> APARTMENT_PHOTOS = List.of(
            "1486325212027-8081e485255e", // contemporary condo building (verified)
            "1545324418-cc1a3fa10c00",   // apartment building exterior (verified)
            "1512917774080-9991f1c4c750", // multi-unit residential USA (verified)
            "1600566753086-00f18fb6b3ea"  // urban residential block (verified)
    );

    private static final List<String> VILLA_PHOTOS = List.of(
            "1600596542815-ffad4c1539a9", // modern villa with pool (verified)
            "1600585154340-be6161a56a0c", // contemporary luxury home (verified)
            "1605276374104-dee2a0ed3cd6", // luxury detached house (verified)
            "1600607687939-ce8a6c25118c"  // upscale villa exterior (verified)
    );

    private static final List<String> HOUSE_PHOTOS = List.of(
            "1568605114967-8130f3a36994", // classic American suburban home (verified)
            "1570129477492-45c003edd2be", // house with garage, USA (verified)
            "1512917774080-9991f1c4c750", // white American home exterior (verified)
            "1605276374104-dee2a0ed3cd6", // traditional American home (verified)
            "1600566753086-00f18fb6b3ea", // home with landscaping (verified)
            "1600585154340-be6161a56a0c"  // contemporary home exterior (verified)
    );

    private static final List<String> PENTHOUSE_PHOTOS = List.of(
            "1600607687939-ce8a6c25118c", // modern luxury home exterior (verified)
            "1605276374104-dee2a0ed3cd6", // upscale property USA (verified)
            "1600585154340-be6161a56a0c", // contemporary luxury exterior (verified)
            "1568605114967-8130f3a36994"  // premium American home (verified)
    );

    private static List<String> photosForType(String typeName) {
        if (typeName == null) return HOUSE_PHOTOS;
        return switch (typeName) {
            case "Apartment" -> APARTMENT_PHOTOS;
            case "Villa"     -> VILLA_PHOTOS;
            case "Penthouse" -> PENTHOUSE_PHOTOS;
            default          -> HOUSE_PHOTOS;
        };
    }

    private static final String[] FURNISHING =
            {"FURNISHED", "SEMI_FURNISHED", "UNFURNISHED"};
    /** How many listings to seed per city. */
    private static final int PER_CITY = 4;

    /**
     * Only residential types are seeded — a buyer/renter feed of "4 BHK
     * Agricultural Land" reads as broken. Falls back to all types if none of
     * these names exist.
     */
    private static final List<String> RESIDENTIAL_TYPES = List.of(
            "Apartment", "Villa", "Independent House", "Penthouse");

    @Override
    @Transactional
    public void run(String... args) {
        // Skip prod (never demo data) and test (the suite owns its own
        // fixtures — a background seed would make property assertions flaky).
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        if (profiles.contains("prod") || profiles.contains("test")) {
            log.info("Prod/test profile — skipping demo property seed");
            return;
        }
        if (propertyRepository.count() > 0) {
            log.info("Properties already present — skipping demo property seed");
            return;
        }

        List<City> cities = cityRepository.findAll();
        List<PropertyType> allTypes = propertyTypeRepository.findAll();
        if (cities.isEmpty() || allTypes.isEmpty()) {
            log.warn("No cities/property types found — cannot seed demo properties");
            return;
        }
        // Seed only residential types so every listing reads naturally as "N BHK ...".
        List<PropertyType> types = allTypes.stream()
                .filter(t -> RESIDENTIAL_TYPES.contains(t.getName()))
                .toList();
        if (types.isEmpty()) {
            types = allTypes;
        }

        // Owners: seeded SELLER + REALTOR. Falls back to any user so the seed
        // still runs if the demo accounts were customised.
        User seller = findUser("seller@propertyapp.com")
                .or(() -> userRepository.findAll().stream().findFirst())
                .orElse(null);
        User realtor = findUser("realtor@propertyapp.com").orElse(seller);
        if (seller == null) {
            log.warn("No users found — cannot seed demo properties");
            return;
        }

        log.info("Seeding demo properties (non-prod)...");
        int created = 0;

        for (int ci = 0; ci < cities.size(); ci++) {
            City city = cities.get(ci);
            String stateName = city.getState() != null ? city.getState().getName() : "";
            String countryName = city.getState() != null && city.getState().getCountry() != null
                    ? city.getState().getCountry().getName() : "United States";
            List<Locality> cityLocalities = localityRepository.findAll().stream()
                    .filter(l -> l.getCity() != null && l.getCity().getId().equals(city.getId()))
                    .toList();

            for (int j = 0; j < PER_CITY; j++) {
                int seq = ci * PER_CITY + j;
                PropertyType type = types.get(seq % types.size());
                Locality locality = cityLocalities.isEmpty()
                        ? null : cityLocalities.get(j % cityLocalities.size());
                String localityName = locality != null ? locality.getName() : city.getName();

                boolean forRent = j % 2 == 1;
                int beds = 1 + ((ci + j) % 4);
                // USD: rentals ~$1.8k–$4.8k/mo, sales ~$350k–$1.6M.
                long price = forRent
                        ? 1_800L + (long) (seq % 6) * 600L
                        : 350_000L + (long) (seq % 8) * 180_000L;

                // Jitter the coordinates so listings aren't all the same point.
                double lat = city.getLatitude().doubleValue() + (j - 1.5) * 0.012;
                double lng = city.getLongitude().doubleValue() + (ci % 3 - 1) * 0.012;

                String ownershipType = "FREEHOLD";
                String possessionStatus = forRent ? "READY_TO_MOVE" : (seq % 5 == 0 ? "WITHIN_3_MONTHS" : "READY_TO_MOVE");
                String kitchenType = switch (type.getName()) {
                    case "Penthouse" -> "MODULAR_KITCHEN";
                    case "Apartment" -> seq % 3 == 0 ? "OPEN_KITCHEN" : "MODULAR_KITCHEN";
                    case "Villa"     -> seq % 2 == 0 ? "MODULAR_KITCHEN" : "OPEN_KITCHEN";
                    default          -> seq % 3 == 0 ? "MODULAR_KITCHEN" : "CLOSED_KITCHEN";
                };
                String waterSupply = seq % 3 == 0 ? "CORPORATION_WATER" : "24_7_SUPPLY";

                Property property = Property.builder()
                        .title(beds + " Bed " + type.getName() + " in " + localityName)
                        .description(beds + "-bedroom " + type.getName().toLowerCase()
                                + " in " + localityName + ", " + city.getName()
                                + ". " + FURNISHING[seq % 3].toLowerCase().replace('_', ' ')
                                + ", well-connected and move-in ready.")
                        .propertyType(type)
                        .owner(j % 2 == 0 ? seller : realtor)
                        .listingType(forRent ? "RENT" : "SALE")
                        .price(BigDecimal.valueOf(price))
                        .addressLine1((100 + seq) + " " + localityName + " Ave")
                        .locality(localityName)
                        .localityRef(locality)
                        .city(city.getName())
                        .state(stateName)
                        .country(countryName)
                        .latitude(BigDecimal.valueOf(lat))
                        .longitude(BigDecimal.valueOf(lng))
                        .location(point(lat, lng))
                        .bedrooms(beds)
                        .bathrooms(Math.max(1, beds - 1))
                        .balconies(j % 3)
                        .carpetArea(550 + beds * 220)
                        .builtUpArea(700 + beds * 250)
                        .furnishedStatus(FURNISHING[seq % 3])
                        .ownershipType(ownershipType)
                        .possessionStatus(possessionStatus)
                        .kitchenType(kitchenType)
                        .waterSupply(waterSupply)
                        .status("ACTIVE")
                        .publishedAt(LocalDateTime.now().minusDays(seq))
                        .isVerified(j != 1)
                        .isPremium(j == 0)
                        .viewCount(40 + seq * 17 % 300)
                        .build();

                property = propertyRepository.save(property);
                seedImages(property, seq);
                created++;
            }
        }

        log.info("Demo property seed completed — {} properties across {} cities",
                created, cities.size());
    }

    /** Attaches 3 type-matched images to a property, the first marked primary. */
    private void seedImages(Property property, int seq) {
        String typeName = property.getPropertyType() != null ? property.getPropertyType().getName() : null;
        List<String> photos = photosForType(typeName);
        for (int k = 0; k < 3; k++) {
            String photoId = photos.get((seq * 3 + k) % photos.size());
            String url = "https://images.unsplash.com/photo-" + photoId
                    + "?w=800&q=80&auto=format&fit=crop";
            propertyImageRepository.save(PropertyImage.builder()
                    .property(property)
                    .imageUrl(url)
                    .thumbnailUrl(url)
                    .caption(property.getTitle())
                    .displayOrder(k)
                    .isPrimary(k == 0)
                    .build());
        }
    }

    private Optional<User> findUser(String email) {
        return userRepository.findByEmailAndDeletedAtIsNull(email);
    }

    private Point point(double lat, double lng) {
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
        return factory.createPoint(new Coordinate(lng, lat));
    }
}
