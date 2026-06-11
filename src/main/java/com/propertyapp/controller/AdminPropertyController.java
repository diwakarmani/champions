package com.propertyapp.controller;

import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.property.PropertyDTO;
import com.propertyapp.service.property.PropertyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/properties")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Property Management", description = "Admin property moderation APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminPropertyController {

    private final PropertyService propertyService;

    @GetMapping
    @Operation(summary = "List properties by status")
    public ResponseEntity<ApiResponse<PageResponse<PropertyDTO>>> getProperties(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        Sort sort = sortDirection.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        PageResponse<PropertyDTO> result;
        if (status == null || status.equalsIgnoreCase("ALL")) {
            result = propertyService.getAllProperties(pageable);
        } else {
            result = propertyService.getPropertiesByStatus(status, pageable);
        }
        return ResponseEntity.ok(ApiResponse.success("Properties retrieved", result));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a property listing")
    public ResponseEntity<ApiResponse<PropertyDTO>> approve(@PathVariable Long id) {
        PropertyDTO dto = propertyService.approveProperty(id);
        return ResponseEntity.ok(ApiResponse.success("Property approved", dto));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a property listing")
    public ResponseEntity<ApiResponse<PropertyDTO>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        PropertyDTO dto = propertyService.rejectProperty(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Property rejected", dto));
    }

    @PatchMapping("/{id}/toggle-featured")
    @Operation(summary = "Toggle featured flag")
    public ResponseEntity<ApiResponse<PropertyDTO>> toggleFeatured(@PathVariable Long id) {
        PropertyDTO dto = propertyService.toggleFeatured(id);
        return ResponseEntity.ok(ApiResponse.success("Featured status updated", dto));
    }

    @PatchMapping("/{id}/toggle-verified")
    @Operation(summary = "Toggle verified flag")
    public ResponseEntity<ApiResponse<PropertyDTO>> toggleVerified(@PathVariable Long id) {
        PropertyDTO dto = propertyService.toggleVerified(id);
        return ResponseEntity.ok(ApiResponse.success("Verified status updated", dto));
    }
}
