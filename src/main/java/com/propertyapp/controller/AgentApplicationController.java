package com.propertyapp.controller;

import com.propertyapp.dto.agent.AgentApplicationDTO;
import com.propertyapp.dto.agent.AgentApplicationRequest;
import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.service.agent.AgentApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent-applications")
@RequiredArgsConstructor
@Tag(name = "Agent Applications", description = "Apply to become an agent")
@SecurityRequirement(name = "Bearer Authentication")
public class AgentApplicationController {

    private final AgentApplicationService applicationService;

    @PostMapping
    @Operation(summary = "Apply to become an agent; grants nothing until an admin approves")
    public ResponseEntity<ApiResponse<AgentApplicationDTO>> apply(@Valid @RequestBody AgentApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Application submitted", applicationService.apply(request)));
    }

    @GetMapping("/me")
    @Operation(summary = "My applications, newest first; empty if I never applied")
    public ResponseEntity<ApiResponse<List<AgentApplicationDTO>>> mine() {
        return ResponseEntity.ok(ApiResponse.success("Applications retrieved", applicationService.getMyApplications()));
    }
}
