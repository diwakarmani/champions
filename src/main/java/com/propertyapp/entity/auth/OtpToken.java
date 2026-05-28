package com.propertyapp.entity.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "otp_tokens", indexes = {
    @Index(name = "idx_identifier", columnList = "identifier"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "identifier", nullable = false, length = 100)
    private String identifier; // Email or mobile number
    
    @Column(name = "identifier_type", nullable = false, length = 10)
    private String identifierType; // EMAIL or MOBILE
    
    @Column(name = "otp_code", nullable = false, length = 6)
    private String otpCode;
    
    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;
    
    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;
    
    @Column(name = "max_attempts")
    @Builder.Default
    private Integer maxAttempts = 3;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusMinutes(10); // 10 min expiry
        }
    }
    
    // Helper methods
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    public boolean isMaxAttemptsReached() {
        return attempts >= maxAttempts;
    }
    
    public void incrementAttempts() {
        this.attempts++;
    }
    
    public void verify() {
        this.isVerified = true;
        this.verifiedAt = LocalDateTime.now();
    }
}