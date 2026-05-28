package com.propertyapp.dto.property;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PropertyDTO {
    
    private Long id;
    private String title;
    private String description;
    
    // Type information
    private Long propertyTypeId;
    private String propertyTypeName;
    private Long propertySubTypeId;
    private String propertySubTypeName;
    
    // Owner information
    private Long ownerId;
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;
    
    // Pricing
    private String listingType;
    private BigDecimal price;
    private BigDecimal depositAmount;
    private BigDecimal maintenanceCharge;
    
    // Address
    private String addressLine1;
    private String addressLine2;
    private String locality;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    
    // Property details
    private Integer bedrooms;
    private Integer bathrooms;
    private Integer balconies;
    private Integer floorNumber;
    private Integer totalFloors;
    private Integer carpetArea;
    private Integer builtUpArea;
    private Integer plotArea;
    private String facingDirection;
    private String furnishedStatus;
    private Integer parkingCovered;
    private Integer parkingOpen;
    private Integer ageOfProperty;
    private LocalDateTime availableFrom;
    
    // Status
    private String status;
    private boolean isVerified;
    private boolean isFeatured;
    private boolean isPremium;
    
    // Metrics
    private Integer viewCount;
    private Integer inquiryCount;
    private LocalDateTime publishedAt;
    
    // Relationships
    private List<PropertyImageDTO> images;
    private String primaryImageUrl;
    private Set<PropertyAmenityDTO> amenities;
    
    // Audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}