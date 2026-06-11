package com.propertyapp.controller;

import com.propertyapp.dto.common.ApiResponse;
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
    @Operation(summary = "Create property type")
    public ResponseEntity<ApiResponse<PropertyTypeDTO>> createType(@RequestBody PropertyTypeDTO dto) {
        return ResponseEntity.ok(ApiResponse.success("Type created", propertyTypeService.createType(dto)));
    }

    @PutMapping("/types/{id}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Update property type")
    public ResponseEntity<ApiResponse<PropertyTypeDTO>> updateType(
            @PathVariable Long id,
            @RequestBody PropertyTypeDTO dto) {
        return ResponseEntity.ok(ApiResponse.success("Type updated", propertyTypeService.updateType(id, dto)));
    }

    @PatchMapping("/types/{id}/order")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Update property type display order")
    public ResponseEntity<ApiResponse<PropertyTypeDTO>> updateDisplayOrder(
            @PathVariable Long id,
            @RequestParam Integer displayOrder) {
        return ResponseEntity.ok(ApiResponse.success("Order updated",
                propertyTypeService.updateDisplayOrder(id, displayOrder)));
    }

    @PostMapping("/sub-types")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Create property sub-type")
    public ResponseEntity<ApiResponse<PropertySubTypeDTO>> createSubType(
            @RequestBody PropertySubTypeDTO dto) {
        return ResponseEntity.ok(ApiResponse.success("Sub-type created",
                propertyTypeService.createSubType(dto)));
    }

    @PostMapping("/amenities")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Create property amenity")
    public ResponseEntity<ApiResponse<PropertyAmenityDTO>> createAmenity(
            @RequestBody PropertyAmenityDTO dto) {
        return ResponseEntity.ok(ApiResponse.success("Amenity created",
                propertyTypeService.createAmenity(dto)));
    }
}
