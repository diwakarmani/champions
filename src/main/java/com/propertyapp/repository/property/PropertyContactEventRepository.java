package com.propertyapp.repository.property;

import com.propertyapp.entity.property.PropertyContactEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyContactEventRepository extends JpaRepository<PropertyContactEvent, Long> {

    boolean existsByPropertyIdAndContactedById(Long propertyId, Long userId);
}
