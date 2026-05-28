package com.propertyapp.service.auth;

import com.propertyapp.dto.auth.*;
import com.propertyapp.entity.auth.OtpToken;
import com.propertyapp.entity.user.User;
import com.propertyapp.exception.*;
import com.propertyapp.repository.auth.OtpTokenRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.security.CustomUserDetails;
import com.propertyapp.security.JwtTokenProvider;
import com.propertyapp.service.communication.EmailService;
import com.propertyapp.service.communication.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpServiceImpl implements OtpService {

    private final OtpTokenRepository otpTokenRepository;
    private final UserRepository userRepository;
    private final SmsService smsService;
    private final EmailService emailService;
    private final JwtTokenProvider jwtTokenProvider;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern MOBILE_PATTERN =
            Pattern.compile("^\\+?[1-9]\\d{1,14}$"); // E.164 format

    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int MAX_OTP_PER_HOUR = 5;
    private static final int RATE_LIMIT_MINUTES = 60;

    @Override
    @Transactional
    public OtpSendResponse sendOtp(OtpLoginRequest request, String ipAddress, String userAgent) {
        log.info("Sending OTP to: {}", request.getIdentifier());

        String identifier = request.getIdentifier().trim();
        String identifierType = detectIdentifierType(identifier);

        // Rate limiting check
        long recentOtps = otpTokenRepository.countRecentOtps(
                identifier, LocalDateTime.now().minusMinutes(RATE_LIMIT_MINUTES));

        if (recentOtps >= MAX_OTP_PER_HOUR) {
            throw new BadRequestException(
                    "Too many OTP requests. Please try again later.");
        }

        // Generate OTP
        String otpCode = generateOtp();

        // Save OTP token
        OtpToken otpToken = OtpToken.builder()
                .identifier(identifier)
                .identifierType(identifierType)
                .otpCode(otpCode)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        otpTokenRepository.save(otpToken);

        // Send OTP
        try {
            if ("EMAIL".equals(identifierType)) {
               emailService.sendOtpEmail(identifier, otpCode);
            } else if ("MOBILE".equals(identifierType)) {
                smsService.sendOtpSms(identifier, otpCode);
            }
        } catch (Exception e) {
            log.error("Failed to send OTP: {}", e.getMessage());
            throw new UnableToSendNotificationException("Failed to send OTP. Please try again.");
        }

        log.info("OTP sent successfully to: {}", identifier);

        return OtpSendResponse.builder()
                .identifier(maskIdentifier(identifier, identifierType))
                .identifierType(identifierType)
                .expiresIn(OTP_EXPIRY_MINUTES * 60) // seconds
                .message("OTP sent successfully")
                .build();
    }

    @Override
    @Transactional
    public OtpVerificationResponse verifyOtp(OtpVerificationRequest request) {
        log.info("Verifying OTP for: {}", request.getIdentifier());

        String identifier = request.getIdentifier().trim();
        String otpCode = request.getOtpCode().trim();

        // Find valid OTP
        OtpToken otpToken = otpTokenRepository.findValidOtp(identifier, LocalDateTime.now())
                .orElseThrow(() -> new BadRequestException("Invalid or expired OTP"));

        // Check if max attempts reached
        if (otpToken.isMaxAttemptsReached()) {
            throw new BadRequestException("Maximum verification attempts reached. " +
                    "Please request a new OTP.");
        }

        // Check if expired
        if (otpToken.isExpired()) {
            throw new BadRequestException("OTP has expired. Please request a new one.");
        }

        // Verify OTP code
        if (!otpToken.getOtpCode().equals(otpCode)) {
            otpToken.incrementAttempts();
            otpTokenRepository.save(otpToken);

            int remainingAttempts = otpToken.getMaxAttempts() - otpToken.getAttempts();
            throw new BadRequestException(
                    String.format("Invalid OTP. %d attempts remaining.", remainingAttempts));
        }

        // Mark as verified
        otpToken.verify();
        otpTokenRepository.save(otpToken);

        // Find or create user
        User user = userRepository.findByEmailOrPhone(identifier, identifier)
                .orElseGet(() -> createUserFromOtp(identifier, otpToken.getIdentifierType()));

        // Make sure user is active
        if (!user.isActive()) {
            throw new UnauthorizedException("Account is inactive. Please contact support.");
        }

        // ✅ FIXED: Create CustomUserDetails and generate JWT token
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken = jwtTokenProvider.generateToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        log.info("OTP verification successful for: {}", identifier);

        return OtpVerificationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400L) // 24 hours
                .userId(user.getId())
                .email(user.getEmail())
                .mobile(user.getPhone())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .message("Login successful")
                .build();
    }

    @Override
    @Transactional
    public OtpSendResponse resendOtp(OtpResendRequest request,
                                     String ipAddress, String userAgent) {
        log.info("Resending OTP to: {}", request.getIdentifier());

        // Check recent OTP
        OtpToken recentOtp = otpTokenRepository
                .findFirstByIdentifierOrderByCreatedAtDesc(request.getIdentifier())
                .orElse(null);

        if (recentOtp != null &&
                recentOtp.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(1))) {
            throw new BadRequestException(
                    "Please wait 1 minute before requesting a new OTP.");
        }

        // Send new OTP
        OtpLoginRequest loginRequest = new OtpLoginRequest();
        loginRequest.setIdentifier(request.getIdentifier());

        return sendOtp(loginRequest, ipAddress, userAgent);
    }

    @Override
    @Transactional
    public void cleanupExpiredOtps() {
        log.info("Cleaning up expired OTPs");
        int deleted = otpTokenRepository.deleteExpiredOtps(LocalDateTime.now());
        log.info("Deleted {} expired OTPs", deleted);
    }

    // Helper methods

    private String detectIdentifierType(String identifier) {
        if (EMAIL_PATTERN.matcher(identifier).matches()) {
            return "EMAIL";
        } else if (MOBILE_PATTERN.matcher(identifier).matches()) {
            return "MOBILE";
        } else {
            throw new BadRequestException(
                    "Invalid identifier. Please provide a valid email or mobile number.");
        }
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000); // 6-digit OTP
        return String.valueOf(otp);
    }

    private String maskIdentifier(String identifier, String type) {
        if ("EMAIL".equals(type)) {
            String[] parts = identifier.split("@");
            if (parts.length == 2) {
                String username = parts[0];
                String masked = username.substring(0, Math.min(2, username.length())) +
                        "***" +
                        (username.length() > 2 ? username.substring(username.length() - 1) : "");
                return masked + "@" + parts[1];
            }
        } else if ("MOBILE".equals(type)) {
            if (identifier.length() > 4) {
                return identifier.substring(0, 2) + "****" +
                        identifier.substring(identifier.length() - 2);
            }
        }
        return identifier;
    }

    private User createUserFromOtp(String identifier, String identifierType) {
        User user = new User();

        if ("EMAIL".equals(identifierType)) {
            user.setEmail(identifier);
            user.setEmailVerified(true); // Auto-verify since OTP verified
            user.setFirstName("User");
            user.setLastName(identifier.split("@")[0]);
        } else if ("MOBILE".equals(identifierType)) {
            user.setPhone(identifier);
            user.setMobileVerified(true); // Auto-verify since OTP verified
            user.setEmail(identifier + "@temp.com"); // Temp email
            user.setFirstName("User");
            user.setLastName(identifier.substring(Math.max(0, identifier.length() - 4)));
        }

        user.setActive(true);

        return userRepository.save(user);
    }
}