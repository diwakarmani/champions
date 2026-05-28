package com.propertyapp.controller;

import com.propertyapp.service.property.DiscoveryScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/discovery")
@RequiredArgsConstructor
@Tag(name = "Refresh user interaction", description = "Refreshing user interaction to properties APIs")
public class DiscoveryAdminController {

    private final DiscoveryScheduler discoveryScheduler;

    @PostMapping("/refresh")
    @Operation(summary = "Refresh api")
    public ResponseEntity<String> refresh() {
        discoveryScheduler.refreshDiscoveryCache();
        return ResponseEntity.ok("Discovery cache refreshed");
    }
}