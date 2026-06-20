package com.propertyapp.dto.property;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PropertyCompareDTO {

    private Long id;
    private String title;
    private String primaryImageUrl;

    // Type & listing
    private String propertyTypeName;
    private String propertySubTypeName;
    private String listingType;

    // Financials
    private BigDecimal price;
    private BigDecimal depositAmount;
    private BigDecimal maintenanceCharge;

    // Size
    private Integer bedrooms;
    private Integer bathrooms;
    private Integer balconies;
    private Integer carpetArea;
    private Integer builtUpArea;
    private Integer plotArea;

    // Floor / structure
    private Integer floorNumber;
    private Integer totalFloors;
    private Integer ageOfProperty;

    // Interior
    private String furnishedStatus;
    private String kitchenType;

    // Infrastructure
    private Integer parkingCovered;
    private Integer parkingOpen;
    private String waterSupply;

    // Legal / possession
    private String ownershipType;
    private String possessionStatus;
    private String facingDirection;
    private LocalDateTime availableFrom;

    // Location
    private String locality;
    private String city;

    // Badges
    private boolean isVerified;
    private boolean isFeatured;
    private boolean isPremium;

    // Activity
    private Integer viewCount;
    private Integer inquiryCount;

    // Amenities
    private Set<PropertyAmenityDTO> amenities;
}
