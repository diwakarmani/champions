package com.propertyapp.entity.user;

import com.propertyapp.entity.base.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_email", columnList = "email"),
    @Index(name = "idx_phone", columnList = "phone"),
    @Index(name = "idx_users_deleted_at", columnList = "deleted_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends SoftDeletableEntity {
    
    @Column(name = "email", unique = true, nullable = false, length = 100)
    private String email;
    
    @Column(name = "phone", unique = true, length = 20)
    private String phone;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;
    
    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;
    
    @Column(name = "profile_image_url")
    private String profileImageUrl;
    
    @Column(name = "date_of_birth")
    private LocalDateTime dateOfBirth;
    
    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "is_active")
    private boolean isActive;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;
    
    @Column(name = "mobile_verified", nullable = false)
    @Builder.Default
    private boolean mobileVerified = false;
    
    @Column(name = "is_locked", nullable = false)
    @Builder.Default
    private boolean isLocked = false;
    
    @Column(name = "failed_login_attempts")
    @Builder.Default
    private int failedLoginAttempts = 0;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;
    
    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "occupation", length = 100)
    private String occupation;

    @Column(name = "website", length = 255)
    private String website;

    /** Set while an admin has suspended this agent. Not the same as is_active: they can still log in (ADR-0004). */
    @Column(name = "agent_suspended_at")
    private LocalDateTime agentSuspendedAt;

    @Column(name = "agent_suspension_reason", length = 1000)
    private String agentSuspensionReason;

    @Column(name = "brokerage_name", length = 150)
    private String brokerageName;

    @Column(name = "license_number", length = 50)
    private String licenseNumber;

    @Column(name = "license_state", length = 2)
    private String licenseState;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Column(name = "service_areas", length = 500)
    private String serviceAreas;

    /** The license number appears on the public profile only if this is true. */
    @Column(name = "license_public", nullable = false)
    @Builder.Default
    private boolean licensePublic = false;
    
    @ManyToMany(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<UserAddress> addresses = new HashSet<>();
    
    public void addRole(Role role) {
        this.roles.add(role);
    }
    
    public void removeRole(Role role) {
        this.roles.remove(role);
    }
    
    public void addAddress(UserAddress address) {
        this.addresses.add(address);
        address.setUser(this);
    }
    
    public void removeAddress(UserAddress address) {
        this.addresses.remove(address);
        address.setUser(null);
    }
    
    public boolean isAgentSuspended() {
        return agentSuspendedAt != null;
    }

    public void suspendAsAgent(String reason) {
        this.agentSuspendedAt = LocalDateTime.now();
        this.agentSuspensionReason = reason;
    }

    public void reinstateAsAgent() {
        this.agentSuspendedAt = null;
        this.agentSuspensionReason = null;
    }

    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts++;
    }
    
    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
    }
    
    public void updateLastLogin(String ipAddress) {
        this.lastLoginAt = LocalDateTime.now();
        this.lastLoginIp = ipAddress;
        this.resetFailedLoginAttempts();
    }
}