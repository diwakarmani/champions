package com.propertyapp.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformStatsDTO {
    private long totalUsers;
    private long totalProperties;
    private long activeListings;
    private long pendingApprovals;
    private long totalGroups;
    private long activeGroups;
    private long soldProperties;
    private long rentedProperties;
    private long newUsersThisMonth;
    private long newPropertiesThisMonth;
}
