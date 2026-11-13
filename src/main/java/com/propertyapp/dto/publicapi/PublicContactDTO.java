package com.propertyapp.dto.publicapi;

import lombok.Builder;
import lombok.Value;

/** Returned only by the contact POST, never by a GET (ADR-0007). */
@Value
@Builder
public class PublicContactDTO {

    Long agentId;
    String agentName;
    String phone;
    String email;
}
