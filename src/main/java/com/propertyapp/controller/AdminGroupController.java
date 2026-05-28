package com.propertyapp.controller;

import com.propertyapp.annotation.Loggable;
import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.group.ApproveRejectRequest;
import com.propertyapp.dto.group.RealtorGroupDTO;
import com.propertyapp.service.group.RealtorGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints for SUPER_ADMIN to manage all realtor groups.
 * Routes under /api/admin/groups/**
 */
@RestController
@RequestMapping("/api/admin/groups")
@RequiredArgsConstructor
@Tag(name = "Admin - Groups", description = "Super admin group management")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminGroupController {

    private final RealtorGroupService groupService;

    @GetMapping
    @Loggable
    @Operation(summary = "List all groups, optionally filtered by status")
    public ResponseEntity<ApiResponse<PageResponse<RealtorGroupDTO>>> getAllGroups(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success("Groups fetched", groupService.getAllGroups(status, pageable)));
    }

    @GetMapping("/{id}")
    @Loggable
    @Operation(summary = "Get group details by ID")
    public ResponseEntity<ApiResponse<RealtorGroupDTO>> getGroup(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Group fetched", groupService.getGroupById(id)));
    }

    @PostMapping("/{id}/approve")
    @Loggable
    @Operation(summary = "Approve a pending group registration")
    public ResponseEntity<ApiResponse<RealtorGroupDTO>> approveGroup(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Group approved", groupService.approveGroup(id)));
    }

    @PostMapping("/{id}/reject")
    @Loggable
    @Operation(summary = "Reject a group registration")
    public ResponseEntity<ApiResponse<RealtorGroupDTO>> rejectGroup(
            @PathVariable Long id,
            @RequestBody ApproveRejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Group rejected", groupService.rejectGroup(id, request.getReason())));
    }

    @PostMapping("/{id}/suspend")
    @Loggable
    @Operation(summary = "Suspend an active group")
    public ResponseEntity<ApiResponse<RealtorGroupDTO>> suspendGroup(
            @PathVariable Long id,
            @RequestBody ApproveRejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Group suspended", groupService.suspendGroup(id, request.getReason())));
    }

    @DeleteMapping("/{id}")
    @Loggable
    @Operation(summary = "Soft delete a group")
    public ResponseEntity<ApiResponse<String>> deleteGroup(@PathVariable Long id) {
        groupService.deleteGroup(id);
        return ResponseEntity.ok(ApiResponse.success("Group deleted", null));
    }
}
