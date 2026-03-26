package com.propertyapp.repository.property;

import com.propertyapp.entity.property.PropertyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyTypeRepository extends JpaRepository<PropertyType, Long> {
    
    Optional<PropertyType> findByName(String name);
    
    List<PropertyType> findByIsActiveTrueOrderByDisplayOrder();
    
    boolean existsByName(String name);

    List<PropertyType> findByIsActiveTrueOrderByDisplayOrderAsc();

    Optional<PropertyType> findByIdAndIsActiveTrue(Long id);
}