package com.propertyapp.entity.group;

import com.propertyapp.entity.base.AuditableEntity;
import com.propertyapp.entity.user.User;
import com.propertyapp.enums.MembershipRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "group_memberships",
    uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"}),
    indexes = {
        @Index(name = "idx_membership_group", columnList = "group_id"),
        @Index(name = "idx_membership_user", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class GroupMembership extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private RealtorGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_role", nullable = false, length = 30)
    @Builder.Default
    private MembershipRole membershipRole = MembershipRole.MEMBER;

    /**
     * Commission percentage override for this realtor in this group.
     * If null, group default applies.
     */
    @Column(name = "commission_percent", precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    /**
     * Monthly sales target (number of properties to sell/rent).
     */
    @Column(name = "monthly_target")
    private Integer monthlyTarget;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;
}
