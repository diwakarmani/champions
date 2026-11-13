package com.propertyapp.controller;

import com.propertyapp.dto.agent.AdminReasonRequest;
import com.propertyapp.dto.agent.AgentStatus;
import com.propertyapp.dto.agent.AgentSummaryDTO;
import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.service.agent.AgentAccountService;
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
@RequestMapping("/api/admin/agents")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Agents", description = "List, suspend and reinstate agents")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminAgentController {

    private final AgentAccountService agentAccountService;

    @GetMapping
    @Operation(summary = "List agents, optionally by status")
    public ResponseEntity<ApiResponse<PageResponse<AgentSummaryDTO>>> list(
            @RequestParam(required = false) AgentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success("Agents retrieved",
                agentAccountService.getAgents(status, pageable)));
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend: blocks listing actions and hides the agent's listings; changes no listing")
    public ResponseEntity<ApiResponse<AgentSummaryDTO>> suspend(
            @PathVariable Long id, @Valid @RequestBody AdminReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Agent suspended",
                agentAccountService.suspend(id, request.getReason())));
    }

    @PostMapping("/{id}/reinstate")
    @Operation(summary = "Reinstate: the agent's listings return with the status they had")
    public ResponseEntity<ApiResponse<AgentSummaryDTO>> reinstate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Agent reinstated", agentAccountService.reinstate(id)));
    }
}
