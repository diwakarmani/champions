package com.propertyapp.service.group;

import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.group.*;
import org.springframework.data.domain.Pageable;

public interface RealtorGroupService {

    // ── Group Admin (REALTOR_GROUP_ADMIN) ──────────────────────────────────

    /** Create a new group — caller becomes group admin */
    RealtorGroupDTO createGroup(CreateGroupRequest request);

    /** Update own group details */
    RealtorGroupDTO updateGroup(Long groupId, UpdateGroupRequest request);

    /** Get own group (group admin view) */
    RealtorGroupDTO getMyGroup();

    /** Dashboard stats for current group admin */
    GroupDashboardStatsDTO getGroupDashboardStats();

    /** List members of own group */
    PageResponse<GroupMemberDTO> getGroupMembers(Pageable pageable);

    /** Add a realtor to own group */
    GroupMemberDTO addMember(AddMemberRequest request);

    /** Remove a realtor from own group */
    void removeMember(Long userId);

    /** Update member commission/target */
    GroupMemberDTO updateMemberSettings(Long userId, AddMemberRequest request);

    /** Approve a property listing submitted by a group realtor */
    void approveListing(Long propertyId);

    /** Reject a property listing submitted by a group realtor */
    void rejectListing(Long propertyId, String reason);

    /** Get pending listings for own group */
    PageResponse<?> getPendingListings(Pageable pageable);

    // ── Super Admin ────────────────────────────────────────────────────────

    /** Get all groups with optional status filter */
    PageResponse<RealtorGroupDTO> getAllGroups(String status, Pageable pageable);

    /** Get group by ID */
    RealtorGroupDTO getGroupById(Long id);

    /** Approve a group registration */
    RealtorGroupDTO approveGroup(Long id);

    /** Reject a group registration */
    RealtorGroupDTO rejectGroup(Long id, String reason);

    /** Suspend a group */
    RealtorGroupDTO suspendGroup(Long id, String reason);

    /** Delete group (soft) */
    void deleteGroup(Long id);

    // ── Shared utility ─────────────────────────────────────────────────────

    /**
     * Check whether a user is an active member of any group.
     * Used by property publish flow to decide ACTIVE vs PENDING_APPROVAL.
     */
    boolean isUserInGroup(Long userId);
}
