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
    
    // Find most recent valid OTP (not verified, not expired) — findFirst adds LIMIT 1
    Optional<OtpToken> findFirstByIdentifierAndIsVerifiedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String identifier, LocalDateTime now);

    // Expire all pending OTPs for an identifier (called before issuing a new one)
    @Modifying
    @Query("UPDATE OtpToken o SET o.expiresAt = :past WHERE o.identifier = :identifier AND o.isVerified = false")
    int expirePendingOtps(@Param("identifier") String identifier, @Param("past") LocalDateTime past);
    
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