package com.propertyapp.repository.locality;

import com.propertyapp.entity.locality.City;
import com.propertyapp.entity.locality.State;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {
    boolean existsByNameAndState(String name, State state);
    List<City> findByIsActiveTrue();

    Optional<City> findByOsmId(Long osmId);

    boolean existsByOsmId(Long osmId);

    List<City> findByState(State state);

    List<City> findByStateCountryId(Long countryId);

    Optional<City> findByNameIgnoreCaseAndState(String name, State state);

    List<City> findByStateId(Long stateId);

    boolean existsByOsmIdAndOsmType(Long osmId, String osmType);
}