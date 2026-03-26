package com.propertyapp.controller;

import com.propertyapp.dto.location.CityResponse;
import com.propertyapp.dto.location.StateResponse;
import com.propertyapp.entity.locality.Country;
import com.propertyapp.service.locality.LocationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/locations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Locality Management", description = "Admin locality management APIs")
public class AdminLocationController {

    private final LocationService locationService;

//    @PostMapping("/countries")
//    public ResponseEntity<?> createCountry(@RequestParam String name){
//        return ResponseEntity.ok(locationService.createCountry(name));
//    }
//
//    @PostMapping("/states")
//    public ResponseEntity<?> createState(@RequestParam Long countryId,
//                                         @RequestParam String name){
//        return ResponseEntity.ok(locationService.createState(countryId,name));
//    }
//
//    @PostMapping("/cities")
//    public ResponseEntity<CityResponseDTO> createCity(
//            @RequestBody CreateCityRequestDTO request) {
//
//        CityResponseDTO response = locationService.createCity(request);
//
//        return ResponseEntity.status(HttpStatus.CREATED).body(response);
//    }

    @PostMapping("/cities/{id}/activate")
    public ResponseEntity<?> activateCity(@PathVariable Long id){
        locationService.activateCity(id);
        return ResponseEntity.ok("City activation started");
    }

//    @PostMapping("/admin/bootstrap/cities")
//    public ResponseEntity<Void> bootstrapCities(
//            @RequestParam String country) {
//
//        locationService.importAllCitiesByCountry(country);
//        return ResponseEntity.ok().build();
//    }

    @PostMapping("/bootstrap/country")
    public void importCountry(@RequestParam String name) {
        locationService.importCountry(name);
    }

    @PostMapping("/bootstrap/{countryId}/states")
    public void importStates(@PathVariable Long countryId) {
        locationService.importStates(countryId);
    }

    @PostMapping("/bootstrap/{stateId}/cities")
    public void importCities(@PathVariable Long stateId) {
        locationService.importCities(stateId);
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