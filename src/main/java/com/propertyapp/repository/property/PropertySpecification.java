package com.propertyapp.repository.property;

import com.propertyapp.entity.property.Property;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class PropertySpecification {
    
    public static Specification<Property> withFilters(
            String city,
            String state,
            String listingType,
            Long propertyTypeId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minBedrooms,
            Integer maxBedrooms,
            String furnishedStatus,
            String status
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Always exclude deleted
            predicates.add(criteriaBuilder.isNull(root.get("deletedAt")));
            
            if (city != null && !city.isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                    criteriaBuilder.lower(root.get("city")),
                    city.toLowerCase()
                ));
            }
            
            if (state != null && !state.isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                    criteriaBuilder.lower(root.get("state")),
                    state.toLowerCase()
                ));
            }
            
            if (listingType != null && !listingType.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("listingType"), listingType));
            }
            
            if (propertyTypeId != null) {
                predicates.add(criteriaBuilder.equal(
                    root.get("propertyType").get("id"),
                    propertyTypeId
                ));
            }
            
            if (minPrice != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    root.get("price"),
                    minPrice
                ));
            }
            
            if (maxPrice != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                    root.get("price"),
                    maxPrice
                ));
            }
            
            if (minBedrooms != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    root.get("bedrooms"),
                    minBedrooms
                ));
            }
            
            if (maxBedrooms != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                    root.get("bedrooms"),
                    maxBedrooms
                ));
            }
            
            if (furnishedStatus != null && !furnishedStatus.isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                    root.get("furnishedStatus"),
                    furnishedStatus
                ));
            }
            
            if (status != null && !status.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            } else {
                // Default to ACTIVE if no status specified
                predicates.add(criteriaBuilder.equal(root.get("status"), "ACTIVE"));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}