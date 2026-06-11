package com.propertyapp.controller;

import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.location.CityResponse;
import com.propertyapp.dto.location.StateResponse;
import com.propertyapp.entity.locality.Country;
import com.propertyapp.service.locality.LocationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/locations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Locality Management", description = "Admin locality management APIs")
@Slf4j
public class AdminLocationController {

    private final LocationService locationService;

    @PostMapping("/cities/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activateCity(@PathVariable Long id) {
        locationService.activateCity(id);
        return ResponseEntity.ok(ApiResponse.success("City activation started", null));
    }

    @PostMapping("/bootstrap/country")
    public ResponseEntity<ApiResponse<Void>> importCountry(@RequestParam String name) {
        try {
            locationService.importCountry(name);
            return ResponseEntity.ok(ApiResponse.success("Country '" + name + "' imported successfully", null));
        } catch (Exception e) {
            log.error("Failed to import country {}: {}", name, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Import failed: " + e.getMessage()));
        }
    }

    @PostMapping("/bootstrap/{countryId}/states")
    public ResponseEntity<ApiResponse<Void>> importStates(@PathVariable Long countryId) {
        try {
            locationService.importStates(countryId);
            return ResponseEntity.ok(ApiResponse.success("States imported successfully", null));
        } catch (Exception e) {
            log.error("Failed to import states for country {}: {}", countryId, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Import failed: " + e.getMessage()));
        }
    }

    @PostMapping("/bootstrap/{stateId}/cities")
    public ResponseEntity<ApiResponse<Void>> importCities(@PathVariable Long stateId) {
        try {
            locationService.importCities(stateId);
            return ResponseEntity.ok(ApiResponse.success("Cities imported successfully", null));
        } catch (Exception e) {
            log.error("Failed to import cities for state {}: {}", stateId, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Import failed: " + e.getMessage()));
        }
    }

    // GET APIs

    @GetMapping("/countries")
    public List<Country> getCountries() {
        return locationService.getCountries();
    }

    @GetMapping("/countries/{countryId}/states")
    public List<StateResponse> getStates(@PathVariable Long countryId) {
        return locationService.getStates(countryId);
    }

    @GetMapping("/states/{stateId}/cities")
    public List<CityResponse> getCities(@PathVariable Long stateId) {
        return locationService.getCities(stateId);
    }
}
