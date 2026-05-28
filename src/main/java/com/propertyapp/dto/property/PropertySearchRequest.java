package com.propertyapp.dto.property;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertySearchRequest {
    
    private String city;
    private String state;
    private String listingType;
    private Long propertyTypeId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer minBedrooms;
    private Integer maxBedrooms;
    private String furnishedStatus;
}