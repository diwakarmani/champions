package com.propertyapp.controller;

import com.propertyapp.service.locality.LocationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
@Tag(name = "Location Controller", description = "User location APIs")
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/cities")
    public ResponseEntity<?> getCities(){
        return ResponseEntity.ok(locationService.getActiveCities());
    }

    @GetMapping("/localities")
    public ResponseEntity<?> searchLocalities(
            @RequestParam Long cityId,
            @RequestParam String keyword){

        return ResponseEntity.ok(
            locationService.searchLocalities(cityId,keyword));
    }
}