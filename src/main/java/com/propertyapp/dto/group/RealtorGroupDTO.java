package com.propertyapp.dto.group;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RealtorGroupDTO {
    private Long id;
    private String name;
    private String companyName;
    private String businessLicense;
    private String address;
    private String description;
    private String logoUrl;
    private String website;
    private String status;
    private String rejectionReason;
    private boolean isActive;
    private Long groupAdminId;
    private String groupAdminName;
    private String groupAdminEmail;
    private int memberCount;
    private List<GroupMemberDTO> members;
    private LocalDateTime createdAt;
}
