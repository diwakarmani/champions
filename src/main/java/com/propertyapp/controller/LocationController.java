package com.propertyapp.controller;

import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.location.CityResponse;
import com.propertyapp.dto.location.LocalityDTO;
import com.propertyapp.service.locality.LocationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
@Tag(name = "Location Controller", description = "User location APIs")
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/cities")
    public ResponseEntity<ApiResponse<List<CityResponse>>> getCities() {
        return ResponseEntity.ok(ApiResponse.success(locationService.getActiveCities()));
    }

    @GetMapping("/localities")
    public ResponseEntity<ApiResponse<List<LocalityDTO>>> searchLocalities(
            @RequestParam Long cityId,
            @RequestParam(required = false, defaultValue = "") String keyword) {
        return ResponseEntity.ok(ApiResponse.success(locationService.searchLocalities(cityId, keyword)));
    }
}