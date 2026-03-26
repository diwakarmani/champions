package com.propertyapp.dto.property;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtorStatsDTO {
    private long activeListings;
    private long draftListings;
    private long pendingApprovals;
    private long soldCount;
    private long rentedCount;
    private long totalViews;
}
