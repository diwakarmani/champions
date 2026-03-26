package com.propertyapp.entity.group;

import com.propertyapp.entity.base.SoftDeletableEntity;
import com.propertyapp.entity.user.User;
import com.propertyapp.enums.GroupStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "realtor_groups", indexes = {
    @Index(name = "idx_group_status", columnList = "status"),
    @Index(name = "idx_group_admin", columnList = "group_admin_id")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class RealtorGroup extends SoftDeletableEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "company_name", length = 150)
    private String companyName;

    @Column(name = "business_license", length = 100)
    private String businessLicense;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "website", length = 200)
    private String website;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private GroupStatus status = GroupStatus.PENDING_APPROVAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_admin_id", nullable = false)
    private User groupAdmin;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<GroupMembership> memberships = new HashSet<>();

    public boolean isApproved() {
        return GroupStatus.ACTIVE.equals(this.status);
    }
}
