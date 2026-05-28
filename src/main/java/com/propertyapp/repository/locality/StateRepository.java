package com.propertyapp.repository.locality;

import com.propertyapp.entity.locality.Country;
import com.propertyapp.entity.locality.State;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StateRepository extends JpaRepository<State, Long> {
    boolean existsByNameAndCountry(String name, Country country);

    Optional<State> findByNameIgnoreCaseAndCountry(String name, Country country);

    List<State> findByCountry(Country country);

    Optional<State> findByOsmId(Long osmId);

    boolean existsByOsmId(Long osmId);

    List<State> findByCountryId(Long countryId);



}