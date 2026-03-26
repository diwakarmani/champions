package com.propertyapp.controller;

import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.property.PropertyDTO;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.user.User;
import com.propertyapp.entity.user.UserFavorite;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
@Tag(name = "Favorites", description = "Save and manage favourite properties")
@SecurityRequirement(name = "Bearer Authentication")
public class FavoriteController {

    private final UserFavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyMapper propertyMapper;

    @GetMapping
    @Operation(summary = "Get my favorites", description = "Returns paginated list of saved properties")
    public ResponseEntity<ApiResponse<PageResponse<PropertyDTO>>> getMyFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Pageable pageable = PageRequest.of(page, size);
        Page<UserFavorite> favorites = favoriteRepository.findByUserId(userId, pageable);

        List<PropertyDTO> dtos = favorites.getContent().stream()
                .map(f -> propertyMapper.toDTO(f.getProperty()))
                .toList();

        Page<PropertyDTO> dtoPage = new PageImpl<>(dtos, pageable, favorites.getTotalElements());
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(dtoPage)));
    }

    @PostMapping("/{propertyId}")
    @Operation(summary = "Add to favorites")
    public ResponseEntity<ApiResponse<Void>> addFavorite(@PathVariable Long propertyId) {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (favoriteRepository.existsByUserIdAndPropertyId(userId, propertyId)) {
            return ResponseEntity.ok(ApiResponse.success("Already in favorites", null));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));

        UserFavorite favorite = UserFavorite.builder()
                .user(user)
                .property(property)
                .build();
        favoriteRepository.save(favorite);

        return ResponseEntity.ok(ApiResponse.success("Added to favorites", null));
    }

    @DeleteMapping("/{propertyId}")
    @Operation(summary = "Remove from favorites")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(@PathVariable Long propertyId) {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        favoriteRepository.deleteByUserIdAndPropertyId(userId, propertyId);
        return ResponseEntity.ok(ApiResponse.success("Removed from favorites", null));
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
