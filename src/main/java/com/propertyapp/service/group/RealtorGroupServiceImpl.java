package com.propertyapp.service.group;

import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.group.*;
import com.propertyapp.dto.property.PropertyCardDTO;
import com.propertyapp.entity.group.GroupMembership;
import com.propertyapp.entity.group.RealtorGroup;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.user.User;
import com.propertyapp.enums.GroupStatus;
import com.propertyapp.enums.MembershipRole;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.exception.DuplicateResourceException;
import com.propertyapp.exception.ResourceNotFoundException;
import com.propertyapp.exception.UnauthorizedException;
import com.propertyapp.repository.group.GroupMembershipRepository;
import com.propertyapp.repository.group.RealtorGroupRepository;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.util.Constants;
import com.propertyapp.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealtorGroupServiceImpl implements RealtorGroupService {

    private final RealtorGroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Group Admin operations
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    @PreAuthorize("hasRole('REALTOR_GROUP_ADMIN')")
    public RealtorGroupDTO createGroup(CreateGroupRequest request) {
        Long adminId = currentUserId();
        User admin = loadUser(adminId);

        if (groupRepository.findByGroupAdminAndDeletedAtIsNull(admin).isPresent()) {
            throw new BadRequestException("You already manage a group. Each admin can manage one group.");
        }
        if (groupRepository.existsByNameAndDeletedAtIsNull(request.getName())) {
            throw new DuplicateResourceException("Group", "name", request.getName());
        }

        RealtorGroup group = RealtorGroup.builder()
                .name(request.getName())
                .companyName(request.getCompanyName())
                .businessLicense(request.getBusinessLicense())
                .address(request.getAddress())
                .description(request.getDescription())
                .logoUrl(request.getLogoUrl())
                .website(request.getWebsite())
                .groupAdmin(admin)
                .status(GroupStatus.PENDING_APPROVAL)
                .isActive(true)
                .build();

        group = groupRepository.save(group);
        log.info("Group created: {} by admin userId={}", group.getName(), adminId);
        return toDTO(group, false);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('REALTOR_GROUP_ADMIN')")
    public RealtorGroupDTO updateGroup(Long groupId, UpdateGroupRequest request) {
        RealtorGroup group = loadOwnGroup();

        if (!group.getId().equals(groupId)) {
            throw new UnauthorizedException("You can only update your own group");
        }

        if (request.getName() != null) group.setName(request.getName());
        if (request.getCompanyName() != null) group.setCompanyName(request.getCompanyName());
        if (request.getBusinessLicense() != null) group.setBusinessLicense(request.getBusinessLicense());
        if (request.getAddress() != null) group.setAddress(request.getAddress());
        if (request.getDescription() != null) group.setDescription(request.getDescription());
        if (request.getLogoUrl() != null) group.setLogoUrl(request.getLogoUrl());
        if (request.getWebsite() != null) group.setWebsite(request.getWebsite());

        group = groupRepository.save(group);
        return toDTO(group, true);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('REALTOR_GROUP_ADMIN')")
    public RealtorGroupDTO getMyGroup() {
        return toDTO(loadOwnGroup(), true);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('REALTOR_GROUP_ADMIN')")
    public GroupDashboardStatsDTO getGroupDashboardStats() {
        RealtorGroup group = loadOwnGroup();
        List<GroupMembership> memberships = membershipRepository.findByGroupAndIsActiveTrue(group);

        long activeListings = countListingsByGroupWithStatus(group, "ACTIVE");
        long pendingApprovals = countListingsByGroupWithStatus(group, "PENDING_APPROVAL");
        long soldThisMonth = countListingsByGroupWithStatusAndMonth(group, "SOLD");
        long rentedThisMonth = countListingsByGroupWithStatusAndMonth(group, "RENTED");

        List<GroupMemberDTO> topPerformers = memberships.stream()
                .map(m -> toMemberDTO(m, true))
                .sorted(Comparator.comparingLong(GroupMemberDTO::getSoldThisMonth).reversed())
                .limit(5)
                .collect(Collectors.toList());

        return GroupDashboardStatsDTO.builder()
                .totalMembers(memberships.size())
                .activeListings(activeListings)
                .pendingApprovals(pendingApprovals)
                .soldThisMonth(soldThisMonth)
                .rentedThisMonth(rentedThisMonth)
                .topPerformers(topPerformers)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('REALTOR_GROUP_ADMIN')")
    public PageResponse<GroupMemberDTO> getGroupMembers(Pageable pageable) {
        RealtorGroup group = loadOwnGroup();
        List<GroupMembership> memberships = membershipRepository.findByGroupAndIsActiveTrue(group);
        List<GroupMemberDTO> dtos = memberships.stream()
                .map(m -> toMemberDTO(m, true))
                .collect(Collectors.toList());
        // Manual pagination from list
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), dtos.size());
        List<GroupMemberDTO> page = start >= dtos.size() ? List.of() : dtos.subList(start, end);
        return PageResponse.<GroupMemberDTO>builder()
                .content(page)
                .pageNumber(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalElements(dtos.size())
                .totalPages((int) Math.ceil((double) dtos.size() / pageable.getPageSize()))
                .last(end >= dtos.size())
                .build();
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('REALTOR_GROUP_ADMIN')")
    public GroupMemberDTO addMember(AddMemberRequest request) {
        RealtorGroup group = loadOwnGroup();

        if (!group.isApproved()) {
            throw new BadRequestException("Group must be approved before adding members");
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getUserId()));

        boolean hasRealtorRole = user.getRoles().stream()
                .anyMatch(r -> Constants.ROLE_REALTOR.equals(r.getName()));
        if (!hasRealtorRole) {
            throw new BadRequestException("User must have REALTOR role to join a group");
        }

        if (membershipRepository.existsByGroupAndUserAndIsActiveTrue(group, user)) {
            throw new DuplicateResourceException("GroupMembership", "userId", request.getUserId());
        }

        GroupMembership membership = GroupMembership.builder()
                .group(group)
                .user(user)
                .membershipRole(MembershipRole.MEMBER)
                .commissionPercent(request.getCommissionPercent())
                .monthlyTarget(request.getMonthlyTarget())
                .isActive(true)
                .build();

        membership = membershipRepository.save(membership);
        log.info("User {} added to group {}", user.getId(), group.getId());
        return toMemberDTO(membership, false);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('REALTOR_GROUP_ADMIN')")
    public void removeMember(Long userId) {
        RealtorGroup group = loadOwnGroup();
        User user = loadUser(userId);

        GroupMembership membership = membershipRepository.findByGroupAndUser(group, user)
                .orElseThrow(() -> new ResourceNotFoundException("GroupMembership", "userId", userId));

        membership.setActive(false);
        membershipRepository.save(membership);
        log.info("User {} removed from group {}", userId, group.getId());
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('REALTOR_GROUP_ADMIN')")
    public GroupMemberDTO updateMemberSettings(Long userId, AddMemberRequest request) {
        RealtorGroup group = loadOwnGroup();
        User user = loadUser(userId);

        GroupMembership membership = membershipRepository.findByGroupAndUser(group, user)
                .orElseThrow(() -> new ResourceNotFoundException("GroupMembership", "userId", userId));

        if (request.getCommissionPercent() != null) membership.setCommissionPercent(request.getCommissionPercent());
        if (request.getMonthlyTarget() != null) membership.setMonthlyTarget(request.getMonthlyTarget());
        membership = membershipRepository.save(membership);
        return toMemberDTO(membership, false);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('REALTOR_GROUP_ADMIN')")
    public void approveListing(Long propertyId) {
        RealtorGroup group = loadOwnGroup();
        Property property = loadProperty(propertyId);

        assertPropertyBelongsToGroup(property, group);

        if (!"PENDING_APPROVAL".equals(property.getStatus())) {
            throw new BadRequestException("Property is not in PENDING_APPROVAL status");
        }

        property.setStatus("ACTIVE");
        property.setPublishedAt(LocalDateTime.now());
        propertyRepository.save(property);
        log.info("Property {} approved by group admin of group {}", propertyId, group.getId());
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('REALTOR_GROUP_ADMIN')")
    public void rejectListing(Long propertyId, String reason) {
        RealtorGroup group = loadOwnGroup();
        Property property = loadProperty(propertyId);

        assertPropertyBelongsToGroup(property, group);

        if (!"PENDING_APPROVAL".equals(property.getStatus())) {
            throw new BadRequestException("Property is not in PENDING_APPROVAL status");
        }

        property.setStatus("DRAFT");
        propertyRepository.save(property);
        log.info("Property {} rejected by group admin of group {}, reason: {}", propertyId, group.getId(), reason);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('REALTOR_GROUP_ADMIN')")
    public PageResponse<?> getPendingListings(Pageable pageable) {
        RealtorGroup group = loadOwnGroup();

        // Get all member user IDs
        List<Long> memberIds = membershipRepository.findByGroupAndIsActiveTrue(group)
                .stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toList());

        if (memberIds.isEmpty()) {
            return PageResponse.<PropertyCardDTO>builder()
                    .content(List.of())
                    .pageNumber(0).pageSize(pageable.getPageSize())
                    .totalElements(0).totalPages(0).last(true)
                    .build();
        }

        Page<Property> pending = propertyRepository
                .findByOwnerIdInAndStatusAndDeletedAtIsNull(memberIds, "PENDING_APPROVAL", pageable);

        return PageResponse.of(pending.map(p -> PropertyCardDTO.builder()
                .id(p.getId())
                .title(p.getTitle())
                .listingType(p.getListingType())
                .price(p.getPrice())
                .city(p.getCity())
                .locality(p.getLocality())
                .bedrooms(p.getBedrooms())
                .furnishedStatus(p.getFurnishedStatus())
                .primaryImageUrl(p.getImages().stream()
                        .filter(i -> i.isPrimary()).findFirst()
                        .map(i -> i.getImageUrl()).orElse(null))
                .verified(p.isVerified())
                .premium(p.isPremium())
                .ownerName(p.getOwner().getFirstName() + " " + p.getOwner().getLastName())
                .build()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Super Admin operations
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PageResponse<RealtorGroupDTO> getAllGroups(String status, Pageable pageable) {
        Page<RealtorGroup> groups = (status != null && !status.isBlank())
                ? groupRepository.findByStatusAndDeletedAtIsNull(GroupStatus.valueOf(status.toUpperCase()), pageable)
                : groupRepository.findByDeletedAtIsNull(pageable);

        return PageResponse.of(groups.map(g -> toDTO(g, false)));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public RealtorGroupDTO getGroupById(Long id) {
        return toDTO(loadGroup(id), true);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public RealtorGroupDTO approveGroup(Long id) {
        RealtorGroup group = loadGroup(id);
        if (GroupStatus.ACTIVE.equals(group.getStatus())) {
            throw new BadRequestException("Group is already approved");
        }
        group.setStatus(GroupStatus.ACTIVE);
        group.setRejectionReason(null);
        group = groupRepository.save(group);
        log.info("Group {} approved by super admin", id);
        return toDTO(group, false);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public RealtorGroupDTO rejectGroup(Long id, String reason) {
        RealtorGroup group = loadGroup(id);
        group.setStatus(GroupStatus.REJECTED);
        group.setRejectionReason(reason);
        group = groupRepository.save(group);
        log.info("Group {} rejected, reason: {}", id, reason);
        return toDTO(group, false);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public RealtorGroupDTO suspendGroup(Long id, String reason) {
        RealtorGroup group = loadGroup(id);
        group.setStatus(GroupStatus.SUSPENDED);
        group.setRejectionReason(reason);
        group = groupRepository.save(group);
        log.info("Group {} suspended, reason: {}", id, reason);
        return toDTO(group, false);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void deleteGroup(Long id) {
        RealtorGroup group = loadGroup(id);
        Long adminId = currentUserId();
        group.markAsDeleted(adminId);
        groupRepository.save(group);
        log.info("Group {} soft-deleted", id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared utility
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public boolean isUserInGroup(Long userId) {
        User user = loadUser(userId);
        return !membershipRepository.findActiveByUser(user).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Long currentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));
    }

    private User loadUser(Long id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private RealtorGroup loadGroup(Long id) {
        return groupRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("RealtorGroup", "id", id));
    }

    private RealtorGroup loadOwnGroup() {
        User admin = loadUser(currentUserId());
        return groupRepository.findByGroupAdminAndDeletedAtIsNull(admin)
                .orElseThrow(() -> new ResourceNotFoundException("RealtorGroup", "groupAdmin", admin.getId()));
    }

    private Property loadProperty(Long id) {
        return propertyRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));
    }

    private void assertPropertyBelongsToGroup(Property property, RealtorGroup group) {
        Long ownerId = property.getOwner().getId();
        User owner = loadUser(ownerId);
        boolean isMember = membershipRepository.existsByGroupAndUserAndIsActiveTrue(group, owner);
        if (!isMember) {
            throw new UnauthorizedException("Property does not belong to a realtor in your group");
        }
    }

    private long countListingsByGroupWithStatus(RealtorGroup group, String status) {
        List<Long> memberIds = membershipRepository.findByGroupAndIsActiveTrue(group)
                .stream().map(m -> m.getUser().getId()).collect(Collectors.toList());
        if (memberIds.isEmpty()) return 0;
        return propertyRepository.countByOwnerIdInAndStatusAndDeletedAtIsNull(memberIds, status);
    }

    private long countListingsByGroupWithStatusAndMonth(RealtorGroup group, String status) {
        List<Long> memberIds = membershipRepository.findByGroupAndIsActiveTrue(group)
                .stream().map(m -> m.getUser().getId()).collect(Collectors.toList());
        if (memberIds.isEmpty()) return 0;
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        return propertyRepository.countByOwnerIdInAndStatusAndUpdatedAtAfterAndDeletedAtIsNull(
                memberIds, status, startOfMonth);
    }

    private RealtorGroupDTO toDTO(RealtorGroup group, boolean includeMembers) {
        List<GroupMemberDTO> members = includeMembers
                ? membershipRepository.findByGroupAndIsActiveTrue(group).stream()
                        .map(m -> toMemberDTO(m, false))
                        .collect(Collectors.toList())
                : null;

        return RealtorGroupDTO.builder()
                .id(group.getId())
                .name(group.getName())
                .companyName(group.getCompanyName())
                .businessLicense(group.getBusinessLicense())
                .address(group.getAddress())
                .description(group.getDescription())
                .logoUrl(group.getLogoUrl())
                .website(group.getWebsite())
                .status(group.getStatus().name())
                .rejectionReason(group.getRejectionReason())
                .isActive(group.isActive())
                .groupAdminId(group.getGroupAdmin().getId())
                .groupAdminName(group.getGroupAdmin().getFirstName() + " " + group.getGroupAdmin().getLastName())
                .groupAdminEmail(group.getGroupAdmin().getEmail())
                .memberCount(includeMembers && members != null ? members.size()
                        : (int) membershipRepository.countActiveMembersByGroup(group))
                .members(members)
                .createdAt(group.getCreatedAt())
                .build();
    }

    private GroupMemberDTO toMemberDTO(GroupMembership m, boolean includeStats) {
        User user = m.getUser();
        long soldThisMonth = 0;
        long activeListings = 0;

        if (includeStats) {
            LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
            soldThisMonth = propertyRepository.countByOwnerIdInAndStatusAndUpdatedAtAfterAndDeletedAtIsNull(
                    List.of(user.getId()), "SOLD", startOfMonth);
            activeListings = propertyRepository.countByOwnerIdInAndStatusAndDeletedAtIsNull(
                    List.of(user.getId()), "ACTIVE");
        }

        return GroupMemberDTO.builder()
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .profileImageUrl(user.getProfileImageUrl())
                .membershipRole(m.getMembershipRole().name())
                .commissionPercent(m.getCommissionPercent())
                .monthlyTarget(m.getMonthlyTarget())
                .isActive(m.isActive())
                .joinedAt(m.getCreatedAt())
                .soldThisMonth(soldThisMonth)
                .activeListings(activeListings)
                .build();
    }
}
