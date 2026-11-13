package com.propertyapp.dto.publicapi;

import com.propertyapp.dto.property.PropertyAmenityDTO;
import com.propertyapp.dto.property.PropertyDTO;
import com.propertyapp.dto.property.PropertyImageDTO;
import com.propertyapp.entity.property.Property;
import com.propertyapp.util.Slugs;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A listing as anyone may see it (ADR-0006). This class is the field allowlist: no owner contact,
 * no rejection reason, no counters. A test pins the exact JSON keys.
 */
@Value
@Builder
public class PublicPropertyDTO {

    Long id;
    String slug;
    String path;
    String title;
    String description;
    String listingType;
    String status;
    /** Only ACTIVE listings take new contact; SOLD and RENTED show as closed. */
    boolean contactable;
    BigDecimal price;
    BigDecimal depositAmount;
    BigDecimal maintenanceCharge;
    String propertyTypeName;
    String propertySubTypeName;
    String addressLine1;
    String addressLine2;
    String locality;
    String city;
    String state;
    String postalCode;
    String country;
    BigDecimal latitude;
    BigDecimal longitude;
    Integer bedrooms;
    Integer bathrooms;
    Integer balconies;
    Integer floorNumber;
    Integer totalFloors;
    Integer carpetArea;
    Integer builtUpArea;
    Integer plotArea;
    String facingDirection;
    String furnishedStatus;
    Integer parkingCovered;
    Integer parkingOpen;
    Integer ageOfProperty;
    LocalDateTime availableFrom;
    String possessionStatus;
    boolean verified;
    boolean featured;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.propertyapp.storage.ImageUrlSerializer.class)
    String primaryImageUrl;
    List<Image> images;
    List<String> amenities;
    PublicAgentRef agent;
    LocalDateTime publishedAt;
    LocalDateTime updatedAt;

    @Value
    @Builder
    public static class Image {
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.propertyapp.storage.ImageUrlSerializer.class)
        String url;
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.propertyapp.storage.ImageUrlSerializer.class)
        String thumbnailUrl;
        String caption;
        boolean primary;
    }

    public static String slugOf(String title, String city, String state) {
        return Slugs.of("home", title, city, state);
    }

    public static PublicPropertyDTO from(Property property, PropertyDTO mapped) {
        String slug = slugOf(property.getTitle(), property.getCity(), property.getState());
        List<PropertyImageDTO> images = mapped.getImages() == null ? List.of() : mapped.getImages();
        return PublicPropertyDTO.builder()
                .id(property.getId())
                .slug(slug)
                .path(Slugs.propertyPath(slug, property.getId()))
                .title(mapped.getTitle())
                .description(mapped.getDescription())
                .listingType(mapped.getListingType())
                .status(mapped.getStatus())
                .contactable("ACTIVE".equals(mapped.getStatus()))
                .price(mapped.getPrice())
                .depositAmount(mapped.getDepositAmount())
                .maintenanceCharge(mapped.getMaintenanceCharge())
                .propertyTypeName(mapped.getPropertyTypeName())
                .propertySubTypeName(mapped.getPropertySubTypeName())
                .addressLine1(mapped.getAddressLine1())
                .addressLine2(mapped.getAddressLine2())
                .locality(mapped.getLocality())
                .city(mapped.getCity())
                .state(mapped.getState())
                .postalCode(mapped.getPostalCode())
                .country(mapped.getCountry())
                .latitude(mapped.getLatitude())
                .longitude(mapped.getLongitude())
                .bedrooms(mapped.getBedrooms())
                .bathrooms(mapped.getBathrooms())
                .balconies(mapped.getBalconies())
                .floorNumber(mapped.getFloorNumber())
                .totalFloors(mapped.getTotalFloors())
                .carpetArea(mapped.getCarpetArea())
                .builtUpArea(mapped.getBuiltUpArea())
                .plotArea(mapped.getPlotArea())
                .facingDirection(mapped.getFacingDirection())
                .furnishedStatus(mapped.getFurnishedStatus())
                .parkingCovered(mapped.getParkingCovered())
                .parkingOpen(mapped.getParkingOpen())
                .ageOfProperty(mapped.getAgeOfProperty())
                .availableFrom(mapped.getAvailableFrom())
                .possessionStatus(mapped.getPossessionStatus())
                .verified(mapped.isVerified())
                .featured(mapped.isFeatured())
                .primaryImageUrl(mapped.getPrimaryImageUrl())
                .images(images.stream()
                        .sorted(Comparator.comparing(PropertyImageDTO::getDisplayOrder,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(image -> Image.builder()
                                .url(image.getImageUrl())
                                .thumbnailUrl(image.getThumbnailUrl())
                                .caption(image.getCaption())
                                .primary(image.isPrimary())
                                .build())
                        .toList())
                .amenities(mapped.getAmenities() == null ? List.of() : mapped.getAmenities().stream()
                        .map(PropertyAmenityDTO::getName)
                        .filter(Objects::nonNull)
                        .sorted()
                        .toList())
                .agent(PublicAgentRef.from(property.getOwner()))
                .publishedAt(mapped.getPublishedAt())
                .updatedAt(mapped.getUpdatedAt())
                .build();
    }
}
