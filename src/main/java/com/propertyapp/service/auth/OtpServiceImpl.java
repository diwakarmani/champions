package com.propertyapp.service.auth;

import com.propertyapp.dto.auth.*;
import com.propertyapp.enums.ClientType;
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
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.otp.dev-fixed-code:}")
    private String devFixedCode;

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

        // Reject unknown identifiers — prevents OTP codes from being issued to
        // non-existent accounts (typo emails would otherwise allow login via a
        // non-existent account).
        if (userRepository.findByEmailOrPhone(identifier, identifier).isEmpty()) {
            throw new UnauthorizedException(
                    "No account found for " + identifier + ". Please register first.");
        }

        // Rate limiting check
        long recentOtps = otpTokenRepository.countRecentOtps(
                identifier, LocalDateTime.now().minusMinutes(RATE_LIMIT_MINUTES));

        if (recentOtps >= MAX_OTP_PER_HOUR) {
            throw new BadRequestException(
                    "Too many OTP requests. Please try again later.");
        }

        // Generate OTP
        String otpCode = generateOtp();

        // Expire any previous unverified OTPs for this identifier so only one is active
        otpTokenRepository.expirePendingOtps(identifier, LocalDateTime.now().minusSeconds(1));

        // Save OTP token
        OtpToken otpToken = OtpToken.builder()
                .identifier(identifier)
                .identifierType(identifierType)
                .otpCode(otpCode)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        otpTokenRepository.save(otpToken);

        // Dev mode: fixed code is already known — skip delivery to avoid spurious errors
        // when Twilio/SMTP credentials are not configured locally.
        if (devFixedCode != null && !devFixedCode.isBlank()) {
            log.info("DEV mode: OTP delivery skipped for '{}' (fixed code active)", identifier);
            return buildSendResponse(identifier, identifierType);
        }

        // Both send methods are @Async — they execute on a background thread and return
        // immediately, so no exception can propagate here. Delivery failures are logged
        // inside each service method. This is intentional fire-and-forget behaviour.
        if ("EMAIL".equals(identifierType)) {
            emailService.sendOtpEmail(identifier, otpCode);
        } else if ("MOBILE".equals(identifierType)) {
            smsService.sendOtpSms(identifier, otpCode);
        }

        log.info("OTP sent successfully to: {}", identifier);
        return buildSendResponse(identifier, identifierType);
    }

    private OtpSendResponse buildSendResponse(String identifier, String identifierType) {
        return OtpSendResponse.builder()
                .identifier(maskIdentifier(identifier, identifierType))
                .identifierType(identifierType)
                .expiresIn(OTP_EXPIRY_MINUTES * 60)
                .message("OTP sent successfully")
                .build();
    }

    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public OtpVerificationResponse verifyOtp(OtpVerificationRequest request, ClientType clientType) {
        log.info("Verifying OTP for: {}", request.getIdentifier());

        String identifier = request.getIdentifier().trim();
        String otpCode = request.getOtpCode().trim();

        // Find most recent valid OTP (LIMIT 1 — avoids NonUniqueResultException if duplicates exist)
        OtpToken otpToken = otpTokenRepository
                .findFirstByIdentifierAndIsVerifiedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        identifier, LocalDateTime.now())
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

        // Account must exist — sendOtp already rejected unknown identifiers.
        // Using orElseThrow (not orElseGet) so a deleted-then-OTP-replayed account
        // cannot be silently resurrected as a new User record.
        User user = userRepository.findByEmailOrPhone(identifier, identifier)
                .orElseThrow(() -> new UnauthorizedException(
                        "No account found for this identifier. Please register first."));

        // Make sure user is active
        if (!user.isActive()) {
            throw new UnauthorizedException("Account is inactive. Please contact support.");
        }

        // ✅ FIXED: Create CustomUserDetails and generate JWT token
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken = jwtTokenProvider.generateToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails, clientType);

        log.info("OTP verification successful for: {}", identifier);

        return OtpVerificationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400L) // 24 hours
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles().stream().map(role -> role.getName()).toList())
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

    private static final int MAX_OTP_PER_HOUR_PER_IP = 20;

    @Override
    @Transactional
    public void sendContactChangeOtp(String newContact, String ipAddress) {
        String identifierType = detectIdentifierType(newContact);

        // Per-identifier cap: prevents hammering one specific contact
        long recentOtps = otpTokenRepository.countRecentOtps(
                newContact, LocalDateTime.now().minusMinutes(RATE_LIMIT_MINUTES));
        if (recentOtps >= MAX_OTP_PER_HOUR) {
            throw new BadRequestException("Too many OTP requests. Please try again later.");
        }

        // Per-IP cap: prevents an authenticated user from using different contact values
        // to send unlimited OTPs to arbitrary phone numbers / email addresses.
        if (ipAddress != null && !ipAddress.isBlank()) {
            long recentByIp = otpTokenRepository.countRecentOtpsByIp(
                    ipAddress, LocalDateTime.now().minusMinutes(RATE_LIMIT_MINUTES));
            if (recentByIp >= MAX_OTP_PER_HOUR_PER_IP) {
                throw new BadRequestException("Too many OTP requests from this device. Please try again later.");
            }
        }

        String otpCode = generateOtp();
        otpTokenRepository.expirePendingOtps(newContact, LocalDateTime.now().minusSeconds(1));

        OtpToken token = OtpToken.builder()
                .identifier(newContact)
                .identifierType(identifierType)
                .otpCode(otpCode)
                .ipAddress(ipAddress)
                .build();
        otpTokenRepository.save(token);

        if (devFixedCode != null && !devFixedCode.isBlank()) {
            log.info("DEV mode: contact-change OTP skipped for '{}' (fixed code active)", newContact);
            return;
        }

        if ("EMAIL".equals(identifierType)) {
            emailService.sendOtpEmail(newContact, otpCode);
        } else {
            smsService.sendOtpSms(newContact, otpCode);
        }

        log.info("Contact-change OTP sent to: {}", newContact);
    }

    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public void verifyContactChangeOtp(String newContact, String otpCode) {
        OtpToken token = otpTokenRepository
                .findFirstByIdentifierAndIsVerifiedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        newContact, LocalDateTime.now())
                .orElseThrow(() -> new BadRequestException("Invalid or expired OTP"));

        if (token.isMaxAttemptsReached()) {
            throw new BadRequestException("Maximum attempts reached. Please request a new OTP.");
        }

        if (token.isExpired()) {
            throw new BadRequestException("OTP has expired. Please request a new one.");
        }

        if (!token.getOtpCode().equals(otpCode)) {
            token.incrementAttempts();
            otpTokenRepository.save(token);
            int remaining = token.getMaxAttempts() - token.getAttempts();
            throw new BadRequestException(
                    String.format("Invalid OTP. %d attempts remaining.", remaining));
        }

        token.verify();
        otpTokenRepository.save(token);
        log.info("Contact-change OTP verified for: {}", newContact);
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
        if (devFixedCode != null && !devFixedCode.isBlank()) {
            log.debug("DEV mode: using fixed OTP code '{}'", devFixedCode);
            return devFixedCode;
        }
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000);
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

}