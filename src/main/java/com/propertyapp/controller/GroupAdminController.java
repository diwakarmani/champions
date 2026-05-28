package com.propertyapp.controller;

import com.propertyapp.annotation.Loggable;
import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.group.*;
import com.propertyapp.service.group.RealtorGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints for REALTOR_GROUP_ADMIN role.
 * All routes under /api/group-admin/**
 */
@RestController
@RequestMapping("/api/group-admin")
@RequiredArgsConstructor
@Tag(name = "Group Admin", description = "Group admin operations for REALTOR_GROUP_ADMIN")
@PreAuthorize("hasRole('REALTOR_GROUP_ADMIN')")
public class GroupAdminController {

    private final RealtorGroupService groupService;

    // ── Group management ───────────────────────────────────────────────────

    @PostMapping("/groups")
    @Loggable
    @Operation(summary = "Create a new realtor group")
    public ResponseEntity<ApiResponse<RealtorGroupDTO>> createGroup(
            @Valid @RequestBody CreateGroupRequest request) {
        RealtorGroupDTO dto = groupService.createGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Group created and pending approval", dto));
    }

    @PutMapping("/groups/{groupId}")
    @Loggable
    @Operation(summary = "Update own group details")
    public ResponseEntity<ApiResponse<RealtorGroupDTO>> updateGroup(
            @PathVariable Long groupId,
            @RequestBody UpdateGroupRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Group updated", groupService.updateGroup(groupId, request)));
    }

    @GetMapping("/groups/mine")
    @Loggable
    @Operation(summary = "Get own group details")
    public ResponseEntity<ApiResponse<RealtorGroupDTO>> getMyGroup() {
        return ResponseEntity.ok(ApiResponse.success("Group fetched", groupService.getMyGroup()));
    }

    // ── Dashboard ──────────────────────────────────────────────────────────

    @GetMapping("/dashboard/stats")
    @Loggable
    @Operation(summary = "Get group dashboard statistics")
    public ResponseEntity<ApiResponse<GroupDashboardStatsDTO>> getDashboardStats() {
        return ResponseEntity.ok(ApiResponse.success("Stats fetched", groupService.getGroupDashboardStats()));
    }

    // ── Member management ──────────────────────────────────────────────────

    @GetMapping("/members")
    @Loggable
    @Operation(summary = "List all active members of own group")
    public ResponseEntity<ApiResponse<PageResponse<GroupMemberDTO>>> getMembers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success("Members fetched", groupService.getGroupMembers(pageable)));
    }

    @PostMapping("/members")
    @Loggable
    @Operation(summary = "Add a realtor to own group")
    public ResponseEntity<ApiResponse<GroupMemberDTO>> addMember(
            @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Member added to group", groupService.addMember(request)));
    }

    @DeleteMapping("/members/{userId}")
    @Loggable
    @Operation(summary = "Remove a realtor from own group")
    public ResponseEntity<ApiResponse<String>> removeMember(@PathVariable Long userId) {
        groupService.removeMember(userId);
        return ResponseEntity.ok(ApiResponse.success("Member removed from group", null));
    }

    @PatchMapping("/members/{userId}/settings")
    @Loggable
    @Operation(summary = "Update member commission/target settings")
    public ResponseEntity<ApiResponse<GroupMemberDTO>> updateMemberSettings(
            @PathVariable Long userId,
            @RequestBody AddMemberRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Member settings updated",
                groupService.updateMemberSettings(userId, request)));
    }

    // ── Listing approval ───────────────────────────────────────────────────

    @GetMapping("/listings/pending")
    @Loggable
    @Operation(summary = "Get listings pending approval for own group")
    public ResponseEntity<ApiResponse<PageResponse<?>>> getPendingListings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success("Pending listings fetched",
                groupService.getPendingListings(pageable)));
    }

    @PostMapping("/listings/{propertyId}/approve")
    @Loggable
    @Operation(summary = "Approve a listing from own group")
    public ResponseEntity<ApiResponse<String>> approveListing(@PathVariable Long propertyId) {
        groupService.approveListing(propertyId);
        return ResponseEntity.ok(ApiResponse.success("Listing approved and now live", null));
    }

    @PostMapping("/listings/{propertyId}/reject")
    @Loggable
    @Operation(summary = "Reject a listing and return it to DRAFT")
    public ResponseEntity<ApiResponse<String>> rejectListing(
            @PathVariable Long propertyId,
            @RequestBody ApproveRejectRequest request) {
        groupService.rejectListing(propertyId, request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Listing rejected and returned to draft", null));
    }
}
