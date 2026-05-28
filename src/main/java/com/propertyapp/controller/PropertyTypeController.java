package com.propertyapp.controller;

import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.property.PropertyAmenityDTO;
import com.propertyapp.dto.property.PropertyTypeDTO;
import com.propertyapp.service.property.PropertyTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/property-types")
@RequiredArgsConstructor
@Tag(name = "Property Types", description = "Property types and amenities APIs")
public class PropertyTypeController {
    
    private final PropertyTypeService propertyTypeService;
    
    @GetMapping
    @Operation(summary = "Get all property types", description = "Get list of all active property types")
    public ResponseEntity<ApiResponse<List<PropertyTypeDTO>>> getAllPropertyTypes() {
        List<PropertyTypeDTO> propertyTypes = propertyTypeService.getAllPropertyTypes();
        return ResponseEntity.ok(ApiResponse.success(propertyTypes));
    }
    
    @GetMapping("/amenities")
    @Operation(summary = "Get all amenities", description = "Get list of all active property amenities")
    public ResponseEntity<ApiResponse<List<PropertyAmenityDTO>>> getAllAmenities() {
        List<PropertyAmenityDTO> amenities = propertyTypeService.getAllAmenities();
        return ResponseEntity.ok(ApiResponse.success(amenities));
    }
    
    @GetMapping("/amenities/category/{category}")
    @Operation(summary = "Get amenities by category", description = "Get amenities filtered by category")
    public ResponseEntity<ApiResponse<List<PropertyAmenityDTO>>> getAmenitiesByCategory(
            @PathVariable String category
    ) {
        List<PropertyAmenityDTO> amenities = propertyTypeService.getAmenitiesByCategory(category);
        return ResponseEntity.ok(ApiResponse.success(amenities));
    }
}