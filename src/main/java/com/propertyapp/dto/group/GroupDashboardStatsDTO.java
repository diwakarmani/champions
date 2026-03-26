package com.propertyapp.dto.group;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GroupDashboardStatsDTO {
    private long totalMembers;
    private long activeListings;
    private long pendingApprovals;
    private long soldThisMonth;
    private long rentedThisMonth;
    private List<GroupMemberDTO> topPerformers;
}
