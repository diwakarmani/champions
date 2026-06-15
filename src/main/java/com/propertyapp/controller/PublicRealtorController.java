package com.propertyapp.controller;

import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.property.ConnectRealtorRequest;
import com.propertyapp.dto.property.ConnectRealtorResponse;
import com.propertyapp.dto.property.RealtorProfileDTO;
import com.propertyapp.exception.UnauthorizedException;
import com.propertyapp.service.realtor.PublicRealtorService;
import com.propertyapp.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/realtors")
@RequiredArgsConstructor
@Tag(name = "Public Realtor Profiles", description = "Authenticated realtor profile and connection APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class PublicRealtorController {

    private final PublicRealtorService realtorService;

    @GetMapping("/{realtorId}")
    @Operation(summary = "Get an active realtor profile")
    public ResponseEntity<ApiResponse<RealtorProfileDTO>> getProfile(@PathVariable Long realtorId) {
        return ResponseEntity.ok(ApiResponse.success("Realtor profile retrieved", realtorService.getProfile(realtorId)));
    }

    @PostMapping("/{realtorId}/connect")
    @PreAuthorize("hasRole('BUYER')")
    @Operation(summary = "Record a buyer connection with a realtor")
    public ResponseEntity<ApiResponse<ConnectRealtorResponse>> connect(
            @PathVariable Long realtorId,
            @Valid @RequestBody ConnectRealtorRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Not authenticated"));
        return ResponseEntity.ok(ApiResponse.success(
                "Realtor connection recorded",
                realtorService.connect(realtorId, userId, request)
        ));
    }
}
