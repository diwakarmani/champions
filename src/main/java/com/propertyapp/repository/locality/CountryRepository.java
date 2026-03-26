package com.propertyapp.repository.locality;

import com.propertyapp.entity.locality.Country;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CountryRepository extends JpaRepository<Country, Long> {
    boolean existsByName(String name);

    Optional<Country> findByNameIgnoreCase(String name);

    Optional<Country> findByOsmId(Long osmId);

    boolean existsByOsmId(Long osmId);
}