package com.propertyapp.dto.publicapi;

import com.propertyapp.dto.property.PropertyDTO;
import com.propertyapp.util.Slugs;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A listing in a public list, such as an agent's profile. Same allowlist rule as PublicPropertyDTO. */
@Value
@Builder
public class PublicListingCardDTO {

    Long id;
    String slug;
    String path;
    String title;
    String listingType;
    String status;
    BigDecimal price;
    String propertyTypeName;
    String locality;
    String city;
    String state;
    Integer bedrooms;
    Integer bathrooms;
    Integer builtUpArea;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.propertyapp.storage.ImageUrlSerializer.class)
    String primaryImageUrl;
    LocalDateTime updatedAt;

    /** {@code cardImageUrl}: the cover's card-size copy, else the full photo (ADR-0016 Amendment 2). */
    public static PublicListingCardDTO from(PropertyDTO mapped, String cardImageUrl) {
        String slug = PublicPropertyDTO.slugOf(mapped.getTitle(), mapped.getCity(), mapped.getState());
        return PublicListingCardDTO.builder()
                .id(mapped.getId())
                .slug(slug)
                .path(Slugs.propertyPath(slug, mapped.getId()))
                .title(mapped.getTitle())
                .listingType(mapped.getListingType())
                .status(mapped.getStatus())
                .price(mapped.getPrice())
                .propertyTypeName(mapped.getPropertyTypeName())
                .locality(mapped.getLocality())
                .city(mapped.getCity())
                .state(mapped.getState())
                .bedrooms(mapped.getBedrooms())
                .bathrooms(mapped.getBathrooms())
                .builtUpArea(mapped.getBuiltUpArea())
                .primaryImageUrl(cardImageUrl)
                .updatedAt(mapped.getUpdatedAt())
                .build();
    }
}
