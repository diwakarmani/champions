package com.propertyapp.controller;

import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.property.RealtorStatsDTO;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/realtor")
@RequiredArgsConstructor
@Tag(name = "Realtor", description = "Realtor-specific APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class RealtorController {

    private final PropertyRepository propertyRepository;

    @GetMapping("/stats")
    @Operation(summary = "Get realtor stats", description = "Returns listing counts and total views for the authenticated realtor")
    @PreAuthorize("hasAnyRole('REALTOR', 'REALTOR_GROUP_ADMIN')")
    public ResponseEntity<ApiResponse<RealtorStatsDTO>> getRealtorStats() {
        Long ownerId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new com.propertyapp.exception.UnauthorizedException("Not authenticated"));

        RealtorStatsDTO stats = RealtorStatsDTO.builder()
                .activeListings(propertyRepository.countByOwnerAndStatus(ownerId, "ACTIVE"))
                .draftListings(propertyRepository.countByOwnerAndStatus(ownerId, "DRAFT"))
                .pendingApprovals(propertyRepository.countByOwnerAndStatus(ownerId, "PENDING_APPROVAL"))
                .soldCount(propertyRepository.countByOwnerAndStatus(ownerId, "SOLD"))
                .rentedCount(propertyRepository.countByOwnerAndStatus(ownerId, "RENTED"))
                .totalViews(propertyRepository.sumViewCountByOwnerId(ownerId))
                .build();

        return ResponseEntity.ok(ApiResponse.success("Realtor stats retrieved", stats));
    }
}
