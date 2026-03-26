package com.propertyapp.dto.location;

public record StateResponse(
        Long id,
        String name,
        Long countryId,
        String countryName,
        Boolean isActive
) {}