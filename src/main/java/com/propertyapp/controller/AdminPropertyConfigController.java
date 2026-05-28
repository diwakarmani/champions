package com.propertyapp.controller;

import com.propertyapp.dto.property.PropertyAmenityDTO;
import com.propertyapp.dto.property.PropertySubTypeDTO;
import com.propertyapp.dto.property.PropertyTypeDTO;
import com.propertyapp.service.property.PropertyTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/property-config")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Property Management", description = "Admin Property management APIs")
public class AdminPropertyConfigController {

    private final PropertyTypeService propertyTypeService;

    @PostMapping("/types")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Create property type", description = "Admin can create property type")
    public ResponseEntity<?> createType(@RequestBody PropertyTypeDTO dto) {
        return ResponseEntity.ok(propertyTypeService.createType(dto));
    }

    @PutMapping("/types/{id}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Update property type", description = "Admin can update property type")
    public ResponseEntity<?> updateType(
            @PathVariable Long id,
            @RequestBody PropertyTypeDTO dto) {
        return ResponseEntity.ok(propertyTypeService.updateType(id, dto));
    }

    @PatchMapping("/types/{id}/order")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Update property type display order", description = "Admin can update property type display order")
    public ResponseEntity<?> updateDisplayOrder(
            @PathVariable Long id,
            @RequestParam Integer displayOrder) {
        return ResponseEntity.ok(
                propertyTypeService.updateDisplayOrder(id, displayOrder));
    }

    @PostMapping("/sub-types")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Create property sub-type", description = "Admin can create property sub-type")
    public ResponseEntity<?> createSubType(
            @RequestBody PropertySubTypeDTO dto) {
        return ResponseEntity.ok(
                propertyTypeService.createSubType(dto));
    }

    @PostMapping("/amenities")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Create property amenities", description = "Admin can create property amenities")
    public ResponseEntity<?> createAmenity(
            @RequestBody PropertyAmenityDTO dto) {
        return ResponseEntity.ok(
                propertyTypeService.createAmenity(dto));
    }
}
