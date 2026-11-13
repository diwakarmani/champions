package com.propertyapp.dto.publicapi;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * An agent's public profile (ADR-0006). License number and state appear only when the agent
 * chose to show them. Never email or phone. Listings come from the paged listings route.
 */
@Value
@Builder
public class PublicAgentProfileDTO {

    Long id;
    String name;
    String slug;
    String path;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.propertyapp.storage.ImageUrlSerializer.class)
    String photoUrl;
    String bio;
    String brokerageName;
    String licenseNumber;
    String licenseState;
    String serviceAreas;
    Integer yearsExperience;
    Double ratingAverage;
    long ratingCount;
    long activeListingCount;
    LocalDateTime memberSince;
}
