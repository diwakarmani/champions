package com.propertyapp.dto.property;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyCardDTO {

    private Long id;
    private String title;
    private String listingType;   // RENT / SALE
    private BigDecimal price;

    private String city;
    private String locality;

    private Integer bedrooms;
    private String furnishedStatus;

    private String primaryImageUrl;

    private boolean verified;
    private boolean premium;

    // only for nearest
    private Double distanceInKm;

    // owner info (used in pending-listings view for group admins)
    private String ownerName;
}