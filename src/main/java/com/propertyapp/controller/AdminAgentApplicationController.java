package com.propertyapp.controller;

import com.propertyapp.dto.agent.AdminReasonRequest;
import com.propertyapp.dto.agent.AgentApplicationDTO;
import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.entity.agent.AgentApplicationStatus;
import com.propertyapp.service.agent.AgentApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/agent-applications")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Agent Applications", description = "Review requests to become an agent")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminAgentApplicationController {

    private final AgentApplicationService applicationService;

    @GetMapping
    @Operation(summary = "List applications, newest first, optionally by status")
    public ResponseEntity<ApiResponse<PageResponse<AgentApplicationDTO>>> list(
            @RequestParam(required = false) AgentApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success("Applications retrieved",
                applicationService.getApplications(status, pageable)));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve: the applicant becomes an agent immediately")
    public ResponseEntity<ApiResponse<AgentApplicationDTO>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Application approved", applicationService.approve(id)));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject, with a reason the applicant will see")
    public ResponseEntity<ApiResponse<AgentApplicationDTO>> reject(
            @PathVariable Long id, @Valid @RequestBody AdminReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Application rejected",
                applicationService.reject(id, request.getReason())));
    }
}
