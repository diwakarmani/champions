package com.propertyapp.repository.user;

import com.propertyapp.entity.user.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    
    Optional<VerificationToken> findByToken(String token);

    Optional<VerificationToken> findByUserIdAndTokenTypeAndUsedAtIsNull(Long userId, String tokenType);
    
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}