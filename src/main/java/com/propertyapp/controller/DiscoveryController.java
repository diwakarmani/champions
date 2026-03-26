package com.propertyapp.controller;

import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.property.HomeDiscoveryResponse;
import com.propertyapp.dto.property.PropertyCardDTO;
import com.propertyapp.enums.DiscoveryCategory;
import com.propertyapp.service.property.DiscoveryService;
import com.propertyapp.util.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
@Tag(name = "Discovery APIs")
public class DiscoveryController {

    private final DiscoveryService discoveryService;

//    @GetMapping("/home")
//    public ResponseEntity<ApiResponse<DiscoveryResponseDTO>> getHomeFeed(
//            @RequestParam(required = false) String city,
//            @RequestParam(required = false) Double lat,
//            @RequestParam(required = false) Double lng
//    ) {
//
//        Long userId = SecurityUtils.getCurrentUserId().orElse(null);
//
//        return ResponseEntity.ok(
//                ApiResponse.success(
//                        discoveryService.getHomeDiscovery(userId, city, lat, lng)
//                )
//        );
//    }

    @GetMapping("/home")
    public ResponseEntity<ApiResponse<HomeDiscoveryResponse>> getHomeFeed(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng
    ) {

        Long userId = SecurityUtils.getCurrentUserId().orElse(null);

        return ResponseEntity.ok(
                ApiResponse.success(
                        discoveryService.getHomeDiscoveryUpdated(userId, city, lat, lng)
                )
        );
    }

    @PostMapping("/track/{propertyId}")
    public ResponseEntity<Void> trackInteraction(
            @PathVariable Long propertyId,
            @RequestParam String type
    ) {

        Long userId = SecurityUtils.getCurrentUserId().orElse(null);

        discoveryService.logInteraction(userId, propertyId, type);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/home/view-more")
    public ResponseEntity<ApiResponse<Page<PropertyCardDTO>>> viewMore(
            @RequestParam DiscoveryCategory category,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {

        Long userId = SecurityUtils.getCurrentUserId().orElse(null);

        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(
                ApiResponse.success(
                        discoveryService.viewMore(
                                userId,
                                category,
                                lat,
                                lng,
                                pageable
                        )
                )
        );
    }

}
