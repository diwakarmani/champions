package com.propertyapp.dto.location;

import java.math.BigDecimal;

public record CityResponse(
        Long id,
        String name,
        Long stateId,
        String stateName,
        BigDecimal latitude,
        BigDecimal longitude
) {}