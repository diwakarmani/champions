package com.propertyapp.dto.property;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyUpdateRequest {
    
    @Size(max = 200)
    private String title;
    
    @Size(max = 5000)
    private String description;
    
    @Pattern(regexp = "SALE|RENT|LEASE")
    private String listingType;
    
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal price;
    
    @DecimalMin(value = "0.0")
    private BigDecimal depositAmount;
    
    @DecimalMin(value = "0.0")
    private BigDecimal maintenanceCharge;
    
    private String addressLine1;
    private String addressLine2;
    
    @Size(max = 100)
    private String locality;
    
    @Size(max = 100)
    private String city;
    
    @Size(max = 100)
    private String state;
    
    @Size(max = 20)
    private String postalCode;
    
    @Min(value = 0)
    private Integer bedrooms;
    
    @Min(value = 0)
    private Integer bathrooms;
    
    @Min(value = 0)
    private Integer balconies;
    
    @Min(value = 0)
    private Integer carpetArea;
    
    @Min(value = 0)
    private Integer builtUpArea;
    
    @Pattern(regexp = "FURNISHED|SEMI_FURNISHED|UNFURNISHED")
    private String furnishedStatus;
    
    @Min(value = 0)
    private Integer parkingCovered;
    
    @Min(value = 0)
    private Integer parkingOpen;
    
    private LocalDateTime availableFrom;
    
    private Set<Long> amenityIds;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private BigDecimal longitude;

    private Long localityId;
}