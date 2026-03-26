package com.propertyapp.repository.property;

import com.propertyapp.entity.property.PropertySubType;
import com.propertyapp.entity.property.PropertyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropertySubTypeRepository extends JpaRepository<PropertySubType, Long> {

    boolean existsByNameAndPropertyType(String name, PropertyType propertyType);

    List<PropertySubType> findByPropertyTypeAndIsActiveTrueOrderByDisplayOrderAsc(PropertyType propertyType);

    @Query("SELECT COALESCE(MAX(p.displayOrder), 0) FROM PropertySubType p WHERE p.propertyType = :propertyType")
    Integer findMaxDisplayOrderByPropertyType(@Param("propertyType") PropertyType propertyType);

}