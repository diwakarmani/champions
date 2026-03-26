package com.propertyapp.service.locality;

import com.propertyapp.dto.location.CityResponse;
import com.propertyapp.dto.location.CityResponseDTO;
import com.propertyapp.dto.location.CreateCityRequestDTO;
import com.propertyapp.dto.location.StateResponse;
import com.propertyapp.entity.locality.City;
import com.propertyapp.entity.locality.Country;
import com.propertyapp.entity.locality.Locality;
import com.propertyapp.entity.locality.State;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.exception.ExternalServiceException;
import com.propertyapp.exception.ResourceNotFoundException;
import com.propertyapp.repository.locality.CityRepository;
import com.propertyapp.repository.locality.CountryRepository;
import com.propertyapp.repository.locality.LocalityRepository;
import com.propertyapp.repository.locality.StateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LocationServiceImpl implements LocationService {

    private final CountryRepository countryRepo;
    private final StateRepository stateRepo;
    private final CityRepository cityRepo;
    private final LocalityRepository localityRepo;
    private final RestClient restClient;
    private final CityRepository cityRepository;
    private final StateRepository stateRepository;

    private Point createPoint(Double lat, Double lon) {
        GeometryFactory factory =
            new GeometryFactory(new PrecisionModel(), 4326);
        return factory.createPoint(new Coordinate(lon, lat));
    }

    @Override
    public Country createCountry(String name) {
        if (countryRepo.existsByName(name))
            throw new BadRequestException("Country already exists");
        return countryRepo.save(Country.builder().name(name).build());
    }

    @Override
    public State createState(Long countryId, String name) {
        Country country = countryRepo.findById(countryId)
            .orElseThrow(() -> new ResourceNotFoundException("Country","id",countryId));

        if (stateRepo.existsByNameAndCountry(name,country))
            throw new BadRequestException("State already exists");

        return stateRepo.save(State.builder()
                .name(name)
                .country(country)
                .build());
    }

    @Transactional
    public CityResponseDTO createCity(CreateCityRequestDTO request) {

        State state = stateRepo.findById(request.getStateId())
                .orElseThrow(() -> new RuntimeException("State not found"));

        /*
         * Fetch administrative boundary relation directly from Overpass
         * admin_level=8 = city/municipality in US
         */

        long stateAreaId = 3600000000L + state.getOsmId();

        String query = """
        [out:json][timeout:180];
        area(%d)->.searchArea;
        relation
          ["boundary"="administrative"]
          ["admin_level"="8"]
          ["name"="%s"]
          (area.searchArea);
        out ids center tags;
    """.formatted(
                stateAreaId,
                request.getName()
        );

        Map<String, Object> body = callOverpass(query);

        if (body == null || !body.containsKey("elements")) {
            throw new RuntimeException("City not found in OSM boundary data");
        }

        List<Map<String, Object>> elements =
                (List<Map<String, Object>>) body.get("elements");

        if (elements.isEmpty()) {
            throw new RuntimeException(
                    "Administrative boundary relation not found for city"
            );
        }

        Map<String, Object> relation = elements.get(0);

        Long osmId = Long.valueOf(relation.get("id").toString());

        Map<String, Object> center =
                (Map<String, Object>) relation.get("center");

        BigDecimal lat =
                new BigDecimal(center.get("lat").toString());
        BigDecimal lon =
                new BigDecimal(center.get("lon").toString());

        if (cityRepo.existsByOsmIdAndOsmType(osmId, "relation")) {
            throw new RuntimeException("City already exists");
        }

        City city = City.builder()
                .name(request.getName())
                .state(state)
                .latitude(lat)
                .longitude(lon)
                .osmType("relation") // 🔥 ALWAYS RELATION
                .osmId(osmId)
                .isActive(false)
                .isImported(false)
                .build();

        City saved = cityRepo.save(city);

        return mapToDTO(saved);
    }
    private CityResponseDTO mapToDTO(City city) {

        return CityResponseDTO.builder()
                .id(city.getId())
                .name(city.getName())
                .state(city.getState().getName())
                .country(city.getState().getCountry().getName())
                .latitude(city.getLatitude())
                .longitude(city.getLongitude())
                .isActive(city.getIsActive())
                .osmId(city.getOsmId())
                .osmType(city.getOsmType())
                .build();
    }

    @Transactional
    public void importAllCitiesByCountry(String countryName) {

        // 1️⃣ Find country in DB
        Country country = countryRepo.findByNameIgnoreCase(countryName)
                .orElseThrow(() -> new RuntimeException("Country not found in DB"));

        if (!"relation".equalsIgnoreCase(country.getOsmType())) {
            throw new RuntimeException("Country must be stored as OSM relation");
        }

        /*
         * 2️⃣ Convert country relation → areaId
         * areaId = 3600000000 + relationId
         */
        long areaId = 3600000000L + country.getOsmId();

        String query = """
        [out:json][timeout:600];
        area(%d)->.searchArea;
        relation["boundary"="administrative"]["admin_level"="8"](area.searchArea);
        out ids tags center;
    """.formatted(areaId);

        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);

        Map<String, Object> body = restClient.post()
                .uri("https://overpass-api.de/api/interpreter")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("data=" + encoded)
                .retrieve()
                .body(Map.class);

        if (body == null || !body.containsKey("elements")) {
            return;
        }

        List<Map<String, Object>> elements =
                (List<Map<String, Object>>) body.get("elements");

        for (Map<String, Object> el : elements) {

            Map<String, Object> tags =
                    (Map<String, Object>) el.get("tags");

            if (tags == null || !tags.containsKey("name")) continue;

            String cityName = tags.get("name").toString();
            Long osmId = Long.valueOf(el.get("id").toString());

            if (cityRepo.existsByOsmId(osmId)) continue;

            Map<String, Object> center =
                    (Map<String, Object>) el.get("center");

            if (center == null) continue;

            BigDecimal lat = new BigDecimal(center.get("lat").toString());
            BigDecimal lon = new BigDecimal(center.get("lon").toString());

            /*
             * 3️⃣ Extract state name from tags (addr:state or is_in:state)
             */
            String stateName = tags.getOrDefault("addr:state",
                    tags.getOrDefault("is_in:state", "Unknown")).toString();

            State state = stateRepo
                    .findByNameIgnoreCaseAndCountry(stateName, country)
                    .orElseGet(() -> stateRepo.save(
                            State.builder()
                                    .name(stateName)
                                    .country(country)
                                    .osmType("relation")
                                    .osmId(0L) // optional if you later import states properly
                                    .isActive(true)
                                    .build()
                    ));

            City city = City.builder()
                    .name(cityName)
                    .state(state)
                    .osmId(osmId)
                    .osmType("relation")
                    .latitude(lat)
                    .longitude(lon)
                    .isActive(false)
                    .isImported(false)
                    .build();

            cityRepo.save(city);
        }
    }

    @Async
    @Override
    public void activateCity(Long cityId) {

        City city = cityRepo.findById(cityId)
            .orElseThrow(() -> new ResourceNotFoundException("City","id",cityId));

        if (city.getIsImported()) return;

        city.setIsActive(true);
        cityRepo.save(city);

        importLocalitiesFromOverpass(city);
    }

    @Retryable(
            retryFor = { ExternalServiceException.class },
            maxAttempts = 2,
            backoff = @Backoff(delay = 2000)
    )
private void importLocalitiesFromOverpass(City city) {

    try {

        String query;

        if ("relation".equalsIgnoreCase(city.getOsmType())
                || "way".equalsIgnoreCase(city.getOsmType())) {

            long areaId = "relation".equalsIgnoreCase(city.getOsmType())
                    ? 3600000000L + city.getOsmId()
                    : 2400000000L + city.getOsmId();

            query = """
                [out:json][timeout:180];
                area(%d)->.searchArea;
                (
                  relation["place"~"suburb|neighbourhood|quarter|locality|hamlet"](area.searchArea);
                  way["place"~"suburb|neighbourhood|quarter|locality|hamlet"](area.searchArea);
                  node["place"~"suburb|neighbourhood|quarter|locality|hamlet"](area.searchArea);
                );
                out center tags;
            """.formatted(areaId);

        } else {

            // Fallback for node cities (very common in US)

            double lat = city.getLatitude().doubleValue();
            double lon = city.getLongitude().doubleValue();

            query = """
                [out:json][timeout:180];
                (
                  relation["place"~"suburb|neighbourhood|quarter|locality|hamlet"]
                    (around:10000,%f,%f);
                  way["place"~"suburb|neighbourhood|quarter|locality|hamlet"]
                    (around:10000,%f,%f);
                  node["place"~"suburb|neighbourhood|quarter|locality|hamlet"]
                    (around:10000,%f,%f);
                );
                out center tags;
            """.formatted(lat, lon, lat, lon, lat, lon);
        }

        Map<String, Object> body = callOverpass(query);

        if (body == null || !body.containsKey("elements")) return;

        List<Map<String, Object>> elements =
                (List<Map<String, Object>>) body.get("elements");

        log.info("Locality elements returned: {}", elements.size());

        Map<String, Map<String, Object>> unique = new HashMap<>();

        for (Map<String, Object> el : elements) {

            Map<String, Object> tags =
                    (Map<String, Object>) el.get("tags");

            if (tags == null || !tags.containsKey("name")) continue;

            String name = tags.get("name").toString().trim();

            if (name.equalsIgnoreCase(city.getName())) continue;

            String normalized = name.toLowerCase()
                    .replaceAll("[^a-z0-9]", "");

            unique.merge(normalized, el, (existing, current) -> {

                String existingType = existing.get("type") != null
                        ? existing.get("type").toString()
                        : "node";

                String currentType = current.get("type") != null
                        ? current.get("type").toString()
                        : "node";

                return priority(currentType) > priority(existingType)
                        ? current
                        : existing;
            });
        }

        log.info("Unique localities after dedupe: {}", unique.size());

        for (Map<String, Object> el : unique.values()) {

            String osmType = el.get("type").toString();
            Long osmId = Long.valueOf(el.get("id").toString());

            if (localityRepo.existsByOsmIdAndOsmType(osmId, osmType))
                continue;

            Map<String, Object> center =
                    (Map<String, Object>) el.get("center");

            if (center == null && el.containsKey("lat")) {
                center = Map.of("lat", el.get("lat"), "lon", el.get("lon"));
            }

            if (center == null) continue;

            BigDecimal lat =
                    new BigDecimal(center.get("lat").toString());
            BigDecimal lon =
                    new BigDecimal(center.get("lon").toString());

            localityRepo.save(Locality.builder()
                    .name(((Map<String, Object>) el.get("tags")).get("name").toString())
                    .city(city)
                    .osmId(osmId)
                    .osmType(osmType)
                    .latitude(lat)
                    .longitude(lon)
                    .location(createPoint(lat.doubleValue(), lon.doubleValue()))
                    .build());
        }

        city.setIsImported(true);
        cityRepo.save(city);

    } catch (Exception ex) {
        throw new ExternalServiceException(
                "Locality import failed for city " + city.getName(), ex);
    }
}

    private int priority(String type) {

        if (type == null) return 0;

        return switch (type.toLowerCase()) {
            case "relation" -> 3;
            case "way" -> 2;
            case "node" -> 1;
            default -> 0;
        };
    }

    @Recover
    private void recover(ExternalServiceException ex, City city) {
        log.error("Overpass API failed after retries for city: {}",
                city.getName(), ex);
    }

    @Override
    @Transactional(readOnly=true)
    public List<City> getActiveCities() {
        return cityRepo.findByIsActiveTrue();
    }

    @Override
    @Transactional(readOnly=true)
    public List<Locality> searchLocalities(Long cityId,String keyword) {

        if (keyword.length()<2)
            throw new BadRequestException("Minimum 2 characters required");

        Pageable pageable = PageRequest.of(0,10);
        return localityRepo.searchLocalities(cityId,keyword,pageable);
    }

    private void validateLatLng(BigDecimal lat,BigDecimal lng){
        if(lat.doubleValue()<-90 || lat.doubleValue()>90)
            throw new BadRequestException("Invalid latitude");
        if(lng.doubleValue()<-180 || lng.doubleValue()>180)
            throw new BadRequestException("Invalid longitude");
    }

    @Transactional
    public void importCountry(String countryName) {

        String query = """
        [out:json][timeout:120];
        relation["boundary"="administrative"]
                ["admin_level"="2"]
                ["name"="%s"];
        out ids tags center;
    """.formatted(countryName);

        Map<String, Object> body = callOverpass(query);

        List<Map<String, Object>> elements =
                (List<Map<String, Object>>) body.get("elements");

        if (elements.isEmpty())
            throw new RuntimeException("Country not found");

        Map<String, Object> el = elements.get(0);

        Long osmId = Long.valueOf(el.get("id").toString());

        if (countryRepo.existsByOsmId(osmId))
            throw new RuntimeException("Country already exist");

        Map<String, Object> center =
                (Map<String, Object>) el.get("center");

        Country country = Country.builder()
                .name(countryName)
                .osmId(osmId)
                .osmType("relation")
                .latitude(new BigDecimal(center.get("lat").toString()))
                .longitude(new BigDecimal(center.get("lon").toString()))
                .build();

         countryRepo.save(country);
    }

    @Transactional
    public void importStates(Long countryId) {

        Country country = countryRepo.findById(countryId)
                .orElseThrow();

        long areaId = 3600000000L + country.getOsmId();

        String query = """
        [out:json][timeout:300];
        area(%d)->.searchArea;
        relation["boundary"="administrative"]
                ["admin_level"="4"]
                (area.searchArea);
        out ids tags center;
    """.formatted(areaId);

        Map<String, Object> body = callOverpass(query);

        List<Map<String, Object>> elements =
                (List<Map<String, Object>>) body.get("elements");

        for (Map<String, Object> el : elements) {

            Long osmId = Long.valueOf(el.get("id").toString());
            if (stateRepo.existsByOsmId(osmId)) continue;

            Map<String, Object> tags =
                    (Map<String, Object>) el.get("tags");

            String name = tags.get("name").toString();

            Map<String, Object> center =
                    (Map<String, Object>) el.get("center");

            State state = State.builder()
                    .name(name)
                    .country(country)
                    .osmId(osmId)
                    .osmType("relation")
                    .latitude(new BigDecimal(center.get("lat").toString()))
                    .longitude(new BigDecimal(center.get("lon").toString()))
                    .build();

            stateRepo.save(state);
        }
    }

@Transactional
public void importCities(Long stateId) {

    State state = stateRepo.findById(stateId)
            .orElseThrow(() -> new RuntimeException("State not found"));

    if (!"relation".equalsIgnoreCase(state.getOsmType())) {
        throw new RuntimeException("State must be OSM relation");
    }

    long areaId = 3600000000L + state.getOsmId();

    String query = """
        [out:json][timeout:900];
        area(%d)->.searchArea;
        relation
          ["boundary"="administrative"]
          ["admin_level"="8"]
          ["population"]
          (if: t["population"] >= 25000)
          (area.searchArea);
        out ids center tags;
    """.formatted(areaId);

    Map<String, Object> body = callOverpass(query);

    if (body == null || !body.containsKey("elements")) return;

    List<Map<String, Object>> elements =
            (List<Map<String, Object>>) body.get("elements");

    log.info("Found {} boundary cities >25k in {}",
            elements.size(), state.getName());

    for (Map<String, Object> el : elements) {

        Map<String, Object> tags =
                (Map<String, Object>) el.get("tags");

        if (tags == null || !tags.containsKey("name")) continue;

        Long osmId = Long.valueOf(el.get("id").toString());

        if (cityRepo.existsByOsmIdAndOsmType(osmId, "relation"))
            continue;

        Map<String, Object> center =
                (Map<String, Object>) el.get("center");

        if (center == null) continue;

        City city = City.builder()
                .name(tags.get("name").toString())
                .state(state)
                .osmId(osmId)
                .osmType("relation") // 🔥 ALWAYS RELATION
                .latitude(new BigDecimal(center.get("lat").toString()))
                .longitude(new BigDecimal(center.get("lon").toString()))
                .isActive(true)
                .isImported(false)
                .build();

        cityRepo.save(city);
    }
}

    private Map<String, Object> callOverpass(String query) {

        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);

        return restClient.post()
                .uri("https://overpass-api.de/api/interpreter")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("data=" + encoded)
                .retrieve()
                .body(Map.class);
    }

    @Transactional(readOnly = true)
    public List<Country> getCountries() {
        return countryRepo.findAll();
    }

    @Transactional(readOnly = true)
    public List<StateResponse> getStates(Long countryId) {
        return stateRepo.findByCountryId(countryId)
                .stream()
                .map(state -> new StateResponse(
                        state.getId(),
                        state.getName(),
                        state.getCountry().getId(),
                        state.getCountry().getName(),
                        state.getIsActive()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CityResponse> getCities(Long stateId) {
        return cityRepo.findByStateId(stateId)
                .stream()
                .map(city -> new CityResponse(
                        city.getId(),
                        city.getName(),
                        city.getState().getId(),
                        city.getState().getName(),
                        city.getLatitude(),
                        city.getLongitude()
                ))
                .toList();
    }
}