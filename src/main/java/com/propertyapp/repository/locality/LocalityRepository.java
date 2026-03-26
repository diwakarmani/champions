package com.propertyapp.repository.locality;

import com.propertyapp.entity.locality.City;
import com.propertyapp.entity.locality.Locality;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LocalityRepository extends JpaRepository<Locality, Long> {

    boolean existsByNameAndCity(String name, City city);

    @Query("""
        SELECT l FROM Locality l
        WHERE l.city.id = :cityId
        AND l.isActive = true
        AND LOWER(l.name) LIKE LOWER(CONCAT(:keyword,'%'))
        ORDER BY l.name ASC
    """)
    List<Locality> searchLocalities(Long cityId, String keyword, Pageable pageable);

    boolean existsByOsmIdAndOsmType(Long osmId, String osmType);
}