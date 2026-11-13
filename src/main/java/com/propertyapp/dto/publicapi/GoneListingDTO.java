package com.propertyapp.dto.publicapi;

import lombok.Builder;
import lombok.Value;

/**
 * Body of a 410 for a listing that was public and has been taken down: only what the web needs to
 * offer similar homes (ADR-0009 Amendment 2). Nothing about the agent or the reason.
 */
@Value
@Builder
public class GoneListingDTO {
    Long id;
    String listingType;
    String city;
    String state;
}
