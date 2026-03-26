package com.propertyapp.repository.auth;

import com.propertyapp.entity.auth.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {
    
    // Find latest OTP for identifier
    Optional<OtpToken> findFirstByIdentifierOrderByCreatedAtDesc(String identifier);
    
    // Find valid OTP (not verified, not expired)
    @Query("SELECT o FROM OtpToken o WHERE o.identifier = :identifier " +
           "AND o.isVerified = false AND o.expiresAt > :now " +
           "ORDER BY o.createdAt DESC")
    Optional<OtpToken> findValidOtp(@Param("identifier") String identifier, 
                                    @Param("now") LocalDateTime now);
    
    // Delete expired OTPs (cleanup)
    @Modifying
    @Query("DELETE FROM OtpToken o WHERE o.expiresAt < :now")
    int deleteExpiredOtps(@Param("now") LocalDateTime now);
    
    // Count OTPs sent in last N minutes (rate limiting)
    @Query("SELECT COUNT(o) FROM OtpToken o WHERE o.identifier = :identifier " +
           "AND o.createdAt > :since")
    long countRecentOtps(@Param("identifier") String identifier, 
                        @Param("since") LocalDateTime since);
    
    // Check if valid OTP exists
    boolean existsByIdentifierAndOtpCodeAndIsVerifiedFalseAndExpiresAtAfter(
        String identifier, String otpCode, LocalDateTime now);
}