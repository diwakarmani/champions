package com.propertyapp.dto.property;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReassignListingRequest {

    @NotNull(message = "Agent is required")
    private Long agentId;
}
