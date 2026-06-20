package com.propertyapp.controller;

import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.rating.CreateRatingRequest;
import com.propertyapp.dto.rating.RatingDTO;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.exception.UnauthorizedException;
import com.propertyapp.service.rating.RealtorRatingService;
import com.propertyapp.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/realtors/{realtorId}/ratings")
@RequiredArgsConstructor
@Tag(name = "Realtor Ratings", description = "Submit and view realtor ratings (BUYER-only write, public read)")
public class RealtorRatingController {

    private final RealtorRatingService ratingService;

    // ── POST /api/realtors/{realtorId}/ratings ───────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Submit or update a rating for a realtor (BUYER only, must have been contacted)")
    public ResponseEntity<ApiResponse<RatingDTO>> submitRating(
            @PathVariable Long realtorId,
            @Valid @RequestBody CreateRatingRequest request) {

        Long raterId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));

        if (realtorId.equals(raterId)) {
            throw new BadRequestException("You cannot rate your own realtor profile");
        }

        RatingDTO dto = ratingService.submitRating(realtorId, raterId, request);
        return ResponseEntity.ok(ApiResponse.success("Rating submitted successfully", dto));
    }

    // ── GET /api/realtors/{realtorId}/ratings ────────────────────────────────
    @GetMapping
    @Operation(summary = "Get paginated ratings for a realtor")
    public ResponseEntity<ApiResponse<PageResponse<RatingDTO>>> getRatings(
            @PathVariable Long realtorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageResponse<RatingDTO> ratings = ratingService.getRatings(realtorId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Ratings retrieved", ratings));
    }

    // ── GET /api/realtors/{realtorId}/ratings/my?propertyId={propertyId} ────
    @GetMapping("/my")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Get the current user's rating for a realtor+property (null if eligible but not yet rated; empty if not eligible)")
    public ResponseEntity<ApiResponse<RatingDTO>> getMyRating(
            @PathVariable Long realtorId,
            @RequestParam Long propertyId) {
        Long raterId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));

        return ratingService.getMyRating(realtorId, raterId, propertyId)
                .map(dto -> ResponseEntity.ok(ApiResponse.success("Your rating retrieved", dto)))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.success("No rating yet", null)));
    }
}
