package com.propertyapp.controller;

import com.propertyapp.service.property.PropertyVisibility;
import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.property.PropertyCompareDTO;
import com.propertyapp.dto.property.PropertyDTO;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.user.User;
import com.propertyapp.entity.user.UserFavorite;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.exception.ResourceNotFoundException;
import com.propertyapp.mapper.PropertyMapper;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.user.UserFavoriteRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
@PreAuthorize("hasRole('BUYER')")
@Tag(name = "Favorites", description = "Save and manage favourite properties")
@SecurityRequirement(name = "Bearer Authentication")
public class FavoriteController {

    private final UserFavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyMapper propertyMapper;

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Get my favorites", description = "Returns paginated list of saved properties")
    public ResponseEntity<ApiResponse<PageResponse<PropertyDTO>>> getMyFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Pageable pageable = PageRequest.of(page, size);

        // Step 1: paginated IDs (no collection join — no in-memory pagination)
        Page<Long> idPage = favoriteRepository.findPropertyIdsByUserId(userId, pageable);

        // Step 2: fetch full entities with images eagerly loaded
        List<Property> properties = idPage.isEmpty()
                ? List.of()
                : favoriteRepository.findByIdsWithImages(idPage.getContent());

        // Restore original sort order from idPage
        Map<Long, Property> byId = properties.stream()
                .collect(Collectors.toMap(Property::getId, p -> p));
        List<PropertyDTO> ordered = idPage.getContent().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(propertyMapper::toDTO)
                .toList();

        Page<PropertyDTO> dtoPage = new PageImpl<>(ordered, pageable, idPage.getTotalElements());
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(dtoPage)));
    }

    /** Most saved ids one call returns; far above any real buyer's list, and it bounds the response. */
    static final int SAVED_IDS_LIMIT = 1000;

    @GetMapping("/ids")
    @Transactional(readOnly = true)
    @Operation(summary = "Ids of my saved homes",
            description = "Ids of the caller's saved listings that the public may see, newest first, at most 1000. "
                    + "For save hearts on listing cards: one call per page, not one per card.")
    public ResponseEntity<ApiResponse<List<Long>>> getMyFavoriteIds() {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        List<Long> ids = favoriteRepository
                .findPropertyIdsByUserId(userId, PageRequest.of(0, SAVED_IDS_LIMIT))
                .getContent();
        return ResponseEntity.ok(ApiResponse.success(ids));
    }

    @PostMapping("/{propertyId}")
    @Transactional
    @Operation(summary = "Add to favorites")
    public ResponseEntity<ApiResponse<Void>> addFavorite(@PathVariable Long propertyId) {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (favoriteRepository.existsByUserIdAndPropertyId(userId, propertyId)) {
            return ResponseEntity.ok(ApiResponse.success("Already in favorites", null));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        // Only listings the public may see can be saved; hidden ones do not exist (ADR-0006).
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(propertyId)
                .filter(PropertyVisibility::isPubliclyVisible)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));

        UserFavorite favorite = UserFavorite.builder()
                .user(user)
                .property(property)
                .build();
        favoriteRepository.save(favorite);

        return ResponseEntity.ok(ApiResponse.success("Added to favorites", null));
    }

    @DeleteMapping("/{propertyId}")
    @Transactional
    @Operation(summary = "Remove from favorites")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(@PathVariable Long propertyId) {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        favoriteRepository.deleteByUserIdAndPropertyId(userId, propertyId);
        return ResponseEntity.ok(ApiResponse.success("Removed from favorites", null));
    }

    @GetMapping("/compare")
    @Transactional(readOnly = true)
    @Operation(summary = "Compare 2–3 favorited properties side-by-side")
    public ResponseEntity<ApiResponse<List<PropertyCompareDTO>>> compareProperties(
            @RequestParam List<Long> ids
    ) {
        if (ids == null || ids.size() < 2) {
            throw new BadRequestException("Select at least 2 properties to compare.");
        }
        if (ids.size() > 3) {
            throw new BadRequestException("Cannot compare more than 3 properties at once.");
        }

        // Deduplicate while preserving order
        List<Long> uniqueIds = ids.stream().distinct().collect(Collectors.toList());
        if (uniqueIds.size() != ids.size()) {
            throw new BadRequestException("Duplicate property IDs are not allowed.");
        }

        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Single-query ownership check — all IDs must be in the user's favorites
        long ownedCount = favoriteRepository.countByUserIdAndPropertyIdIn(userId, uniqueIds);
        if (ownedCount < uniqueIds.size()) {
            throw new BadRequestException("One or more selected properties are not in your favorites.");
        }

        List<Property> properties = favoriteRepository.findByIdsWithImagesAndAmenities(uniqueIds);
        // A saved listing that has since been hidden cannot be compared (ADR-0006 Amendment 1).
        boolean anyHidden = properties.size() < uniqueIds.size() || properties.stream()
                .anyMatch(p -> p.isDeleted() || !PropertyVisibility.isPubliclyVisible(p));
        if (anyHidden) {
            throw new ResourceNotFoundException("One or more selected properties are no longer available.");
        }

        // Restore request order
        Map<Long, Property> byId = properties.stream()
                .collect(Collectors.toMap(Property::getId, p -> p));
        List<PropertyCompareDTO> result = uniqueIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(propertyMapper::toCompareDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Properties fetched for comparison", result));
    }

    @GetMapping("/{propertyId}/check")
    @Operation(summary = "Check if property is favorited")
    public ResponseEntity<ApiResponse<Boolean>> checkFavorite(@PathVariable Long propertyId) {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isFavorite = favoriteRepository.existsByUserIdAndPropertyId(userId, propertyId);
        return ResponseEntity.ok(ApiResponse.success(isFavorite));
    }
}
