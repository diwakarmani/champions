package com.propertyapp.dto.publicapi;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * A city and listing type that has at least one public listing: what the web builds city pages and
 * the city sitemap from (ADR-0009 Amendment 2). Slugs are computed, like listing slugs.
 */
@Value
@Builder
public class PublicMarketDTO {
    String state;
    String stateSlug;
    String city;
    String citySlug;
    String listingType;
    long listings;
    LocalDateTime lastModified;
}
