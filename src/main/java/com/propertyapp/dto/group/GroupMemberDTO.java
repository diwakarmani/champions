package com.propertyapp.dto.group;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class GroupMemberDTO {
    private Long userId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String profileImageUrl;
    private String membershipRole;
    private BigDecimal commissionPercent;
    private Integer monthlyTarget;
    private boolean isActive;
    private LocalDateTime joinedAt;
    // stats
    private long activeListings;
    private long soldThisMonth;
}
