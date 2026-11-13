package com.propertyapp.entity.agent;

import com.propertyapp.entity.base.AuditableEntity;
import com.propertyapp.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/** A request to become an agent (ADR-0004). Re-applying after rejection makes a new row, so history is kept. */
@Entity
@Table(name = "agent_applications")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentApplication extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AgentApplicationStatus status = AgentApplicationStatus.PENDING;

    @Column(name = "license_number", nullable = false, length = 50)
    private String licenseNumber;

    @Column(name = "license_state", nullable = false, length = 2)
    private String licenseState;

    @Column(name = "brokerage_name", nullable = false, length = 150)
    private String brokerageName;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Column(name = "service_areas", length = 500)
    private String serviceAreas;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    public void approve(Long adminId) {
        review(AgentApplicationStatus.APPROVED, adminId);
    }

    public void reject(Long adminId, String reason) {
        review(AgentApplicationStatus.REJECTED, adminId);
        this.rejectionReason = reason;
    }

    private void review(AgentApplicationStatus decision, Long adminId) {
        this.status = decision;
        this.reviewedBy = adminId;
        this.reviewedAt = LocalDateTime.now();
    }
}
