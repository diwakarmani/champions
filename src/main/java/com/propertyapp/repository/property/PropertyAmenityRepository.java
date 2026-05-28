package com.propertyapp.repository.property;

import com.propertyapp.entity.property.PropertyAmenity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface PropertyAmenityRepository extends JpaRepository<PropertyAmenity, Long> {
    
    List<PropertyAmenity> findByIsActiveTrueOrderByDisplayOrder();
    
    Set<PropertyAmenity> findByIdIn(Set<Long> ids);
    
    List<PropertyAmenity> findByCategoryOrderByDisplayOrder(String category);

    boolean existsByName(String name);

    List<PropertyAmenity> findByIsActiveTrueOrderByDisplayOrderAsc();

    List<PropertyAmenity> findByCategoryAndIsActiveTrueOrderByDisplayOrderAsc(String category);

    @Query("""
        SELECT COALESCE(MAX(a.displayOrder),0)
        FROM PropertyAmenity a
        WHERE a.category = :category
    """)
    Integer findMaxDisplayOrderByCategory(@Param("category") String category);
}