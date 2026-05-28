package com.propertyapp.dto.location;

import java.math.BigDecimal;

public record LocalityDTO(
        Long id,
        String name,
        Long cityId,
        BigDecimal latitude,
        BigDecimal longitude
) {}
