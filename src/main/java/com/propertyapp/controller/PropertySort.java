package com.propertyapp.controller;

import com.propertyapp.exception.BadRequestException;
import org.springframework.data.domain.Sort;

import java.util.Set;

/** Public sort fields are an allowlist so ordering cannot probe related or private fields (ADR-0006). */
final class PropertySort {

    private static final Set<String> ALLOWED = Set.of("createdAt", "updatedAt", "price", "bedrooms", "title");

    private PropertySort() {
    }

    static Sort of(String field, String direction) {
        if (!ALLOWED.contains(field)) {
            throw new BadRequestException("Unsupported sort field: " + field);
        }
        return Sort.by("ASC".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC, field);
    }
}
