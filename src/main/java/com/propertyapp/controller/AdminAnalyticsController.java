package com.propertyapp.controller;

import com.propertyapp.dto.analytics.PlatformStatsDTO;
import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.enums.GroupStatus;
import com.propertyapp.repository.group.RealtorGroupRepository;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@Tag(name = "Admin Analytics", description = "Platform-level analytics APIs")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminAnalyticsController {

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final RealtorGroupRepository realtorGroupRepository;

    @GetMapping("/platform")
    @Operation(summary = "Get platform stats", description = "Returns platform-wide aggregate statistics for super admin")
    public ResponseEntity<ApiResponse<PlatformStatsDTO>> getPlatformStats() {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        PlatformStatsDTO stats = PlatformStatsDTO.builder()
                .totalUsers(userRepository.count())
                .totalProperties(propertyRepository.count())
                .activeListings(propertyRepository.countByStatusAndDeletedAtIsNull("ACTIVE"))
                .pendingApprovals(propertyRepository.countByStatusAndDeletedAtIsNull("PENDING_APPROVAL"))
                .soldProperties(propertyRepository.countByStatusAndDeletedAtIsNull("SOLD"))
                .rentedProperties(propertyRepository.countByStatusAndDeletedAtIsNull("RENTED"))
                .totalGroups(realtorGroupRepository.count())
                .activeGroups(realtorGroupRepository.countByStatus(GroupStatus.ACTIVE))
                .newUsersThisMonth(userRepository.countCreatedAfter(startOfMonth))
                .newPropertiesThisMonth(propertyRepository.countCreatedAfter(startOfMonth))
                .build();

        return ResponseEntity.ok(ApiResponse.success("Platform analytics retrieved", stats));
    }
}
