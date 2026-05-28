package com.propertyapp.controller;

import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.property.*;
import com.propertyapp.service.property.PropertyService;
import com.propertyapp.util.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
@Tag(name = "Property Management", description = "Property listing and management APIs")
public class PropertyController {
    
    private final PropertyService propertyService;
    
    @GetMapping
    @Operation(summary = "Search properties with filters", 
               description = "Public endpoint to search active properties with various filters")
    public ResponseEntity<ApiResponse<PageResponse<PropertyDTO>>> searchProperties(
            @Parameter(description = "City name") @RequestParam(required = false) String city,
            @Parameter(description = "State name") @RequestParam(required = false) String state,
            @Parameter(description = "Listing type (SALE/RENT/LEASE)") @RequestParam(required = false) String listingType,
            @Parameter(description = "Property type ID") @RequestParam(required = false) Long propertyTypeId,
            @Parameter(description = "Minimum price") @RequestParam(required = false) java.math.BigDecimal minPrice,
            @Parameter(description = "Maximum price") @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @Parameter(description = "Minimum bedrooms") @RequestParam(required = false) Integer minBedrooms,
            @Parameter(description = "Maximum bedrooms") @RequestParam(required = false) Integer maxBedrooms,
            @Parameter(description = "Furnished status") @RequestParam(required = false) String furnishedStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        PropertySearchRequest searchRequest = PropertySearchRequest.builder()
                .city(city)
                .state(state)
                .listingType(listingType)
                .propertyTypeId(propertyTypeId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .minBedrooms(minBedrooms)
                .maxBedrooms(maxBedrooms)
                .furnishedStatus(furnishedStatus)
                .build();
        
        Sort.Direction direction = sortDirection.equalsIgnoreCase("ASC") 
            ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, Math.min(size, Constants.MAX_PAGE_SIZE),
            Sort.by(direction, sortBy));
        
        PageResponse<PropertyDTO> properties = propertyService.searchProperties(searchRequest, pageable);
        return ResponseEntity.ok(ApiResponse.success(properties));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get property by ID", description = "Get detailed property information")
    public ResponseEntity<ApiResponse<PropertyDTO>> getPropertyById(@PathVariable Long id) {
        PropertyDTO property = propertyService.getPropertyById(id);
        return ResponseEntity.ok(ApiResponse.success(property));
    }
    
    @PostMapping
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Create new property", description = "Seller/Realtor can create property listing")
    public ResponseEntity<ApiResponse<PropertyDTO>> createProperty(
            @Valid @RequestBody PropertyCreateRequest request
    ) {
        PropertyDTO property = propertyService.createProperty(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Property created successfully", property));
    }
    
    @PutMapping("/{id}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Update property", description = "Owner can update their property")
    public ResponseEntity<ApiResponse<PropertyDTO>> updateProperty(
            @PathVariable Long id,
            @Valid @RequestBody PropertyUpdateRequest request
    ) {
        PropertyDTO property = propertyService.updateProperty(id, request);
        return ResponseEntity.ok(ApiResponse.success("Property updated successfully", property));
    }
    
    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Delete property", description = "Owner or Admin can delete property (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deleteProperty(@PathVariable Long id) {
        propertyService.deleteProperty(id);
        return ResponseEntity.ok(ApiResponse.success("Property deleted successfully", null));
    }
    
    @PatchMapping("/{id}/publish")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Publish property", description = "Owner can publish draft property")
    public ResponseEntity<ApiResponse<PropertyDTO>> publishProperty(@PathVariable Long id) {
        PropertyDTO property = propertyService.publishProperty(id);
        return ResponseEntity.ok(ApiResponse.success("Property published successfully", property));
    }
    
    @PatchMapping("/{id}/status")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Update property status", description = "Admin/Realtor can update property status")
    public ResponseEntity<ApiResponse<PropertyDTO>> updatePropertyStatus(
            @PathVariable Long id,
            @RequestParam String status
    ) {
        PropertyDTO property = propertyService.updatePropertyStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Property status updated", property));
    }
    
    @GetMapping("/my-listings")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Get my property listings", description = "Get current user's properties")
    public ResponseEntity<ApiResponse<PageResponse<PropertyDTO>>> getMyListings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        Sort.Direction direction = sortDirection.equalsIgnoreCase("ASC") 
            ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, Math.min(size, Constants.MAX_PAGE_SIZE),
            Sort.by(direction, sortBy));
        
        PageResponse<PropertyDTO> properties = propertyService.getMyListings(pageable);
        return ResponseEntity.ok(ApiResponse.success(properties));
    }
    
    @GetMapping("/status/{status}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Get properties by status", description = "Admin can view properties by status")
    public ResponseEntity<ApiResponse<PageResponse<PropertyDTO>>> getPropertiesByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, Constants.MAX_PAGE_SIZE));
        PageResponse<PropertyDTO> properties = propertyService.getPropertiesByStatus(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(properties));
    }
    
    @GetMapping("/featured")
    @Operation(summary = "Get featured properties", description = "Get list of featured properties")
    public ResponseEntity<ApiResponse<List<PropertyDTO>>> getFeaturedProperties(
            @RequestParam(defaultValue = "10") int limit
    ) {
        List<PropertyDTO> properties = propertyService.getFeaturedProperties(limit);
        return ResponseEntity.ok(ApiResponse.success(properties));
    }
    
    @PatchMapping("/{id}/toggle-featured")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Toggle featured status", description = "Admin can mark property as featured")
    public ResponseEntity<ApiResponse<PropertyDTO>> toggleFeatured(@PathVariable Long id) {
        PropertyDTO property = propertyService.toggleFeatured(id);
        return ResponseEntity.ok(ApiResponse.success("Featured status updated", property));
    }
    
    @PatchMapping("/{id}/toggle-verified")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Toggle verified status", description = "Admin/Realtor can verify property")
    public ResponseEntity<ApiResponse<PropertyDTO>> toggleVerified(@PathVariable Long id) {
        PropertyDTO property = propertyService.toggleVerified(id);
        return ResponseEntity.ok(ApiResponse.success("Verified status updated", property));
    }
    
    // Image management endpoints
    
    @GetMapping("/{id}/images")
    @Operation(summary = "Get property images", description = "Get all images for a property")
    public ResponseEntity<ApiResponse<List<PropertyImageDTO>>> getPropertyImages(
            @PathVariable Long id
    ) {
        List<PropertyImageDTO> images = propertyService.getPropertyImages(id);
        return ResponseEntity.ok(ApiResponse.success(images));
    }
    
    @PostMapping("/{id}/images")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Add property image", description = "Owner can upload property images")
    public ResponseEntity<ApiResponse<PropertyImageDTO>> addPropertyImage(
            @PathVariable Long id,
            @Valid @RequestBody PropertyImageDTO imageDTO
    ) {
        PropertyImageDTO image = propertyService.addPropertyImage(id, imageDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Image added successfully", image));
    }
    
    @DeleteMapping("/{propertyId}/images/{imageId}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Delete property image", description = "Owner can delete property images")
    public ResponseEntity<ApiResponse<Void>> deletePropertyImage(
            @PathVariable Long propertyId,
            @PathVariable Long imageId
    ) {
        propertyService.deletePropertyImage(propertyId, imageId);
        return ResponseEntity.ok(ApiResponse.success("Image deleted successfully", null));
    }
    
    @PatchMapping("/{propertyId}/images/{imageId}/set-primary")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Set primary image", description = "Owner can set primary property image")
    public ResponseEntity<ApiResponse<Void>> setPrimaryImage(
            @PathVariable Long propertyId,
            @PathVariable Long imageId
    ) {
        propertyService.setPrimaryImage(propertyId, imageId);
        return ResponseEntity.ok(ApiResponse.success("Primary image set successfully", null));
    }
}