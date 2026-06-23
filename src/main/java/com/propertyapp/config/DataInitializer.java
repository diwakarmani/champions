package com.propertyapp.config;

import com.propertyapp.entity.locality.City;
import com.propertyapp.entity.locality.Country;
import com.propertyapp.entity.locality.Locality;
import com.propertyapp.entity.locality.State;
import com.propertyapp.entity.property.PropertyAmenity;
import com.propertyapp.entity.property.PropertySubType;
import com.propertyapp.entity.property.PropertyType;
import com.propertyapp.entity.user.Role;
import com.propertyapp.entity.user.User;
import com.propertyapp.repository.locality.CityRepository;
import com.propertyapp.repository.locality.CountryRepository;
import com.propertyapp.repository.locality.LocalityRepository;
import com.propertyapp.repository.locality.StateRepository;
import com.propertyapp.repository.property.PropertyAmenityRepository;
import com.propertyapp.repository.property.PropertySubTypeRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import com.propertyapp.repository.user.RoleRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.util.PasswordUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Initializes default application data on startup
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PropertyTypeRepository propertyTypeRepository;
    private final PropertyAmenityRepository propertyAmenityRepository;
    private final PropertySubTypeRepository propertySubTypeRepository;
    private final CountryRepository countryRepository;
    private final StateRepository stateRepository;
    private final CityRepository cityRepository;
    private final LocalityRepository localityRepository;

    @Value("${app.admin.email:admin@propertyapp.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@123}")
    private String adminPassword;

    @Value("${app.admin.first-name:Admin}")
    private String adminFirstName;

    @Value("${app.admin.last-name:User}")
    private String adminLastName;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Initializing application data...");

        initializeRoles();
        initializeAdminUser();
        initializeDemoUsers();
        initializePropertyTypes();
        initializePropertySubTypes();
        initializePropertyAmenities();
        initializeLocationData();
        log.info("Application data initialization completed successfully");
    }

    private void initializeRoles() {
        log.info("Initializing roles...");

        String[] roleNames = {"BUYER", "SELLER", "REALTOR", "SUPER_ADMIN"};
        String[] roleDescriptions = {
            "Property buyer - can search and inquire about properties",
            "Property seller - can list and manage properties",
            "Real estate agent - can create and manage property listings",
            "System administrator - full access to all features"
        };

        for (int i = 0; i < roleNames.length; i++) {
            if (!roleRepository.existsByName(roleNames[i])) {
                Role role = Role.builder()
                        .name(roleNames[i])
                        .description(roleDescriptions[i])
                        .build();
                roleRepository.save(role);
                log.info("Created role: {}", roleNames[i]);
            }
        }
    }

    private void initializeAdminUser() {
        log.info("Checking for admin user...");

        if (!userRepository.existsByEmail(adminEmail)) {
            Role adminRole = roleRepository.findByName("SUPER_ADMIN")
                    .orElseThrow(() -> new RuntimeException("SUPER_ADMIN role not found"));

            Set<Role> roles = new HashSet<>();
            roles.add(adminRole);

            User admin = User.builder()
                    .email(adminEmail)
                    .passwordHash(PasswordUtils.hash(adminPassword))
                    .firstName(adminFirstName)
                    .lastName(adminLastName)
                    .emailVerified(true)
                    .mobileVerified(false)
                    .isActive(true)
                    .isLocked(false)
                    .roles(roles)
                    .build();

            userRepository.save(admin);
            log.info("Created admin user: {}", adminEmail);
        } else {
            log.info("Admin user already exists");
        }
    }

    private void initializeDemoUsers() {
        record DemoUser(String email, String firstName, String lastName, String role) {}
        List<DemoUser> demos = List.of(
                new DemoUser("buyer@propertyapp.com",   "Demo",  "Buyer",   "BUYER"),
                new DemoUser("seller@propertyapp.com",  "Demo",  "Seller",  "SELLER"),
                new DemoUser("realtor@propertyapp.com", "Demo",  "Realtor", "REALTOR")
        );
        for (DemoUser d : demos) {
            if (!userRepository.existsByEmail(d.email())) {
                Role role = roleRepository.findByName(d.role())
                        .orElseThrow(() -> new RuntimeException(d.role() + " role not found"));
                Set<Role> roles = new HashSet<>();
                roles.add(role);
                User user = User.builder()
                        .email(d.email())
                        .passwordHash(PasswordUtils.hash("Demo@123"))
                        .firstName(d.firstName())
                        .lastName(d.lastName())
                        .emailVerified(true)
                        .mobileVerified(false)
                        .isActive(true)
                        .isLocked(false)
                        .roles(roles)
                        .build();
                userRepository.save(user);
                log.info("Created demo user: {}", d.email());
            }
        }
    }

    private void initializePropertyTypes() {
        log.info("Initializing property types...");

        String[][] propertyTypesData = {
            {"Apartment", "Multi-story residential building with individual units"},
            {"Villa", "Standalone luxury house with private amenities"},
            {"Independent House", "Standalone residential house"},
            {"Plot", "Land for construction"},
            {"Commercial Space", "Office or retail space"},
            {"Warehouse", "Industrial storage facility"},
            {"Agricultural Land", "Farmland for agricultural purposes"},
            {"Penthouse", "Luxury apartment on top floor"},
            {"Studio Apartment", "Compact single-room living space"}
        };

        for (int i = 0; i < propertyTypesData.length; i++) {
            String name = propertyTypesData[i][0];
            String description = propertyTypesData[i][1];

            if (!propertyTypeRepository.existsByName(name)) {
                PropertyType propertyType = PropertyType.builder()
                        .name(name)
                        .description(description)
                        .displayOrder(i + 1)
                        .isActive(true)
                        .build();
                propertyTypeRepository.save(propertyType);
                log.info("Created property type: {}", name);
            }
        }
    }

    private void initializePropertyAmenities() {
        log.info("Initializing property amenities...");

        String[][] amenitiesData = {
            // Safety & Security
            {"24/7 Security", "fa-shield", "SAFETY"},
            {"CCTV Surveillance", "fa-video", "SAFETY"},
            {"Gated Community", "fa-lock", "SAFETY"},
            {"Fire Safety", "fa-fire-extinguisher", "SAFETY"},

            // Recreation
            {"Swimming Pool", "fa-swimming-pool", "RECREATION"},
            {"Gymnasium", "fa-dumbbell", "RECREATION"},
            {"Children's Play Area", "fa-child", "RECREATION"},
            {"Clubhouse", "fa-home", "RECREATION"},
            {"Garden", "fa-tree", "RECREATION"},
            {"Sports Facility", "fa-basketball-ball", "RECREATION"},

            // Utilities
            {"Power Backup", "fa-bolt", "UTILITIES"},
            {"Water Supply", "fa-tint", "UTILITIES"},
            {"Gas Pipeline", "fa-burn", "UTILITIES"},
            {"Waste Disposal", "fa-trash", "UTILITIES"},
            {"Sewage Treatment", "fa-recycle", "UTILITIES"},

            // Parking & Access
            {"Covered Parking", "fa-car", "PARKING"},
            {"Open Parking", "fa-parking", "PARKING"},
            {"Visitor Parking", "fa-car-side", "PARKING"},
            {"Lift", "fa-elevator", "ACCESS"},
            {"Wheelchair Access", "fa-wheelchair", "ACCESS"},

            // Modern Amenities
            {"Air Conditioning", "fa-snowflake", "MODERN"},
            {"Internet/WiFi", "fa-wifi", "MODERN"},
            {"Intercom", "fa-phone", "MODERN"},
            {"Modular Kitchen", "fa-utensils", "MODERN"},
            {"Piped Gas", "fa-fire", "MODERN"},

            // Services
            {"Maintenance Staff", "fa-tools", "SERVICES"},
            {"Laundry Service", "fa-soap", "SERVICES"},
            {"Shopping Complex", "fa-shopping-cart", "SERVICES"},
            {"School/College", "fa-school", "SERVICES"},
            {"Hospital", "fa-hospital", "SERVICES"}
        };

        for (int i = 0; i < amenitiesData.length; i++) {
            String name = amenitiesData[i][0];

            if (!propertyAmenityRepository.existsByName(name)) {
                PropertyAmenity amenity = PropertyAmenity.builder()
                        .name(name)
                        .iconClass(amenitiesData[i][1])
                        .category(amenitiesData[i][2])
                        .displayOrder(i + 1)
                        .isActive(true)
                        .build();

                propertyAmenityRepository.save(amenity);
                log.info("Created amenity: {}", name);
            }
        }

        log.info("Created {} property amenities", amenitiesData.length);
    }


    private void initializePropertySubTypes() {

        log.info("Initializing property sub types with correct display order...");

        List<PropertyType> propertyTypes = propertyTypeRepository.findAll();

        for (PropertyType type : propertyTypes) {

            String typeName = type.getName();

            String[] subTypes = getSubTypesForType(typeName);

            for (String subTypeName : subTypes) {

                boolean exists = propertySubTypeRepository
                        .existsByNameAndPropertyType(subTypeName, type);

                if (!exists) {

                    Integer maxOrder = propertySubTypeRepository
                            .findMaxDisplayOrderByPropertyType(type);

                    int nextDisplayOrder = (maxOrder == null ? 0 : maxOrder) + 1;

                    PropertySubType subType = PropertySubType.builder()
                            .propertyType(type)
                            .name(subTypeName)
                            .description(subTypeName + " under " + typeName)
                            .displayOrder(nextDisplayOrder)
                            .isActive(true)
                            .build();

                    propertySubTypeRepository.save(subType);

                    log.info("Created subtype: {} -> {} (order {})",
                            typeName, subTypeName, nextDisplayOrder);
                }
            }
        }

        log.info("Property sub types initialized successfully");
    }

    private void initializeLocationData() {
        if (countryRepository.existsByName("United States")) {
            return;
        }
        log.info("Seeding US location data (cities + localities)...");

        Country us = countryRepository.save(Country.builder()
                .name("United States").osmId(148838L).osmType("relation")
                .latitude(new BigDecimal("37.09024")).longitude(new BigDecimal("-95.712891"))
                .isActive(true).build());

        State ca = saveState("California",    us, -101L, new BigDecimal("36.778261"), new BigDecimal("-119.417932"));
        State fl = saveState("Florida",       us, -102L, new BigDecimal("27.664827"), new BigDecimal("-81.515754"));
        State il = saveState("Illinois",      us, -103L, new BigDecimal("40.633125"), new BigDecimal("-89.398529"));
        State ny = saveState("New York",      us, -104L, new BigDecimal("42.165726"), new BigDecimal("-74.948051"));
        State tx = saveState("Texas",         us, -105L, new BigDecimal("31.968599"), new BigDecimal("-99.901810"));
        State wa = saveState("Washington",    us, -106L, new BigDecimal("47.751074"), new BigDecimal("-120.740139"));

        City losAngeles   = saveCity("Los Angeles",   ca, -201L, "34.0522342",  "-118.2436849");
        City sanFrancisco = saveCity("San Francisco", ca, -202L, "37.7792588",  "-122.4193286");
        City miami        = saveCity("Miami",         fl, -203L, "25.7616798",  "-80.1917902");
        City chicago      = saveCity("Chicago",       il, -204L, "41.8781136",  "-87.6297982");
        City newYork      = saveCity("New York",      ny, -205L, "40.7127753",  "-74.0059731");
        City austin       = saveCity("Austin",        tx, -206L, "30.2711286",  "-97.7436995");
        City houston      = saveCity("Houston",       tx, -207L, "29.7604267",  "-95.3698028");
        City seattle      = saveCity("Seattle",       wa, -208L, "47.6062095",  "-122.3320708");

        saveLocalities(losAngeles,   new String[]{"Downtown LA", "Hollywood", "Santa Monica", "Venice"},
                new long[]{-301,-302,-303,-304}, new String[]{"34.0430","34.0928","34.0195","33.9850"}, new String[]{"-118.2673","-118.3287","-118.4912","-118.4695"});
        saveLocalities(sanFrancisco, new String[]{"Mission District", "SoMa", "Nob Hill", "Marina District"},
                new long[]{-305,-306,-307,-308}, new String[]{"37.7599","37.7785","37.7930","37.8030"}, new String[]{"-122.4148","-122.3987","-122.4159","-122.4364"});
        saveLocalities(miami,        new String[]{"South Beach", "Brickell", "Wynwood", "Coconut Grove"},
                new long[]{-309,-310,-311,-312}, new String[]{"25.7825","25.7617","25.7997","25.7290"}, new String[]{"-80.1300","-80.1918","-80.1993","-80.2401"});
        saveLocalities(chicago,      new String[]{"The Loop", "Lincoln Park", "Wicker Park", "Hyde Park"},
                new long[]{-313,-314,-315,-316}, new String[]{"41.8827","41.9217","41.9088","41.7943"}, new String[]{"-87.6233","-87.6355","-87.6773","-87.5907"});
        saveLocalities(newYork,      new String[]{"Manhattan", "Brooklyn", "Queens", "The Bronx"},
                new long[]{-317,-318,-319,-320}, new String[]{"40.7831","40.6501","40.7282","40.8448"}, new String[]{"-73.9712","-73.9496","-73.7949","-73.8648"});
        saveLocalities(austin,       new String[]{"Downtown Austin", "South Congress", "East Austin", "Zilker"},
                new long[]{-321,-322,-323,-324}, new String[]{"30.2672","30.2451","30.2627","30.2610"}, new String[]{"-97.7431","-97.7502","-97.7166","-97.7726"});
        saveLocalities(houston,      new String[]{"Downtown", "Montrose", "The Heights", "Midtown"},
                new long[]{-325,-326,-327,-328}, new String[]{"29.7589","29.7474","29.7988","29.7370"}, new String[]{"-95.3677","-95.3905","-95.3996","-95.3858"});
        saveLocalities(seattle,      new String[]{"Capitol Hill", "Ballard", "Fremont", "Belltown"},
                new long[]{-329,-330,-331,-332}, new String[]{"47.6232","47.6677","47.6510","47.6145"}, new String[]{"-122.3212","-122.3829","-122.3501","-122.3488"});

        log.info("US location data seeded successfully");
    }

    private State saveState(String name, Country country, long osmId, BigDecimal lat, BigDecimal lon) {
        if (stateRepository.existsByNameAndCountry(name, country)) {
            return stateRepository.findByNameIgnoreCaseAndCountry(name, country).orElseThrow();
        }
        return stateRepository.save(State.builder()
                .name(name).country(country).osmId(osmId).osmType("relation")
                .latitude(lat).longitude(lon).isActive(true).build());
    }

    private City saveCity(String name, State state, long osmId, String lat, String lon) {
        if (cityRepository.existsByNameAndState(name, state)) {
            return cityRepository.findByNameIgnoreCaseAndState(name, state).orElseThrow();
        }
        return cityRepository.save(City.builder()
                .name(name).state(state).osmId(osmId).osmType("relation")
                .latitude(new BigDecimal(lat)).longitude(new BigDecimal(lon))
                .isActive(true).isImported(false).build());
    }

    private void saveLocalities(City city, String[] names, long[] osmIds, String[] lats, String[] lons) {
        for (int i = 0; i < names.length; i++) {
            if (!localityRepository.existsByNameAndCity(names[i], city)) {
                localityRepository.save(Locality.builder()
                        .name(names[i]).city(city).osmId(osmIds[i]).osmType("relation")
                        .latitude(new BigDecimal(lats[i])).longitude(new BigDecimal(lons[i]))
                        .isActive(true).build());
            }
        }
    }

    private String[] getSubTypesForType(String typeName) {

        return switch (typeName) {

            case "Apartment" -> new String[]{
                    "1 RK", "1 BHK", "1.5 BHK", "2 BHK", "2.5 BHK",
                    "3 BHK", "3.5 BHK", "4 BHK", "4+ BHK",
                    "Studio Apartment", "Penthouse",
                    "Serviced Apartment", "Loft Apartment",
                    "Duplex Apartment", "Triplex Apartment"
            };

            case "Villa" -> new String[]{
                    "Luxury Villa", "Duplex Villa", "Triplex Villa",
                    "Row Villa", "Independent Villa",
                    "Farm Villa", "Beach Villa", "Hill View Villa"
            };

            case "Independent House" -> new String[]{
                    "1 BHK House", "2 BHK House", "3 BHK House",
                    "4 BHK House", "Bungalow",
                    "Duplex House", "Triplex House",
                    "Row House", "Corner House", "Farm House"
            };

            case "Commercial Space" -> new String[]{
                    "Office Space", "Co-working Space",
                    "Retail Shop", "Showroom",
                    "Business Center", "IT Park Office",
                    "Commercial Floor", "Commercial Building",
                    "Mall Space", "Food Court Space"
            };

            case "Warehouse" -> new String[]{
                    "Cold Storage", "Godown", "Industrial Shed",
                    "Distribution Center", "Logistics Warehouse",
                    "Manufacturing Unit"
            };

            case "Agricultural Land" -> new String[]{
                    "Farmland", "Orchard", "Plantation",
                    "Dairy Farm Land", "Poultry Farm Land",
                    "Greenhouse Farm"
            };

            case "Plot" -> new String[]{
                    "Residential Plot", "Commercial Plot",
                    "Industrial Plot", "Corner Plot",
                    "Gated Community Plot", "Farm Plot",
                    "NA Plot"
            };

            default -> new String[0];
        };
    }

}