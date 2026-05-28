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
public class PropertyCreateRequest {
    
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;
    
    @NotBlank(message = "Description is required")
    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;
    
    @NotNull(message = "Property type is required")
    private Long propertyTypeId;
    
    private Long propertySubTypeId;
    
    @NotBlank(message = "Listing type is required")
    @Pattern(regexp = "SALE|RENT|LEASE", message = "Listing type must be SALE, RENT, or LEASE")
    private String listingType;
    
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal price;
    
    @DecimalMin(value = "0.0", message = "Deposit amount must be non-negative")
    private BigDecimal depositAmount;
    
    @DecimalMin(value = "0.0", message = "Maintenance charge must be non-negative")
    private BigDecimal maintenanceCharge;
    
    // Address fields
    @NotBlank(message = "Address line 1 is required")
    private String addressLine1;
    
    private String addressLine2;
    
    @Size(max = 100)
    private String locality;
    
    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;
    
    @NotBlank(message = "State is required")
    @Size(max = 100)
    private String state;
    
    @NotBlank(message = "Country is required")
    @Size(max = 100)
    private String country;
    
    @Size(max = 20)
    private String postalCode;
    
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private BigDecimal latitude;
    
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private BigDecimal longitude;
    
    // Property details
    @Min(value = 0, message = "Bedrooms must be non-negative")
    private Integer bedrooms;
    
    @Min(value = 0, message = "Bathrooms must be non-negative")
    private Integer bathrooms;
    
    @Min(value = 0, message = "Balconies must be non-negative")
    private Integer balconies;
    
    private Integer floorNumber;
    private Integer totalFloors;
    
    @Min(value = 0, message = "Carpet area must be non-negative")
    private Integer carpetArea;
    
    @Min(value = 0, message = "Built-up area must be non-negative")
    private Integer builtUpArea;
    
    @Min(value = 0, message = "Plot area must be non-negative")
    private Integer plotArea;
    
    @Pattern(regexp = "NORTH|SOUTH|EAST|WEST|NORTH_EAST|NORTH_WEST|SOUTH_EAST|SOUTH_WEST", 
             message = "Invalid facing direction")
    private String facingDirection;
    
    @Pattern(regexp = "FURNISHED|SEMI_FURNISHED|UNFURNISHED", 
             message = "Furnished status must be FURNISHED, SEMI_FURNISHED, or UNFURNISHED")
    private String furnishedStatus;
    
    @Min(value = 0)
    private Integer parkingCovered;
    
    @Min(value = 0)
    private Integer parkingOpen;
    
    @Min(value = 0)
    private Integer ageOfProperty;
    
    private LocalDateTime availableFrom;
    
    // Amenities
    private Set<Long> amenityIds;

    private Long localityId;
}