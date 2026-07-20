package com.propertyapp.service.auth;

import com.propertyapp.dto.auth.*;
import com.propertyapp.enums.ClientType;

public interface OtpService {

    /**
     * Send OTP to email or mobile (auto-detect)
     */
    OtpSendResponse sendOtp(OtpLoginRequest request, String ipAddress, String userAgent);

    /**
     * Verify OTP and return JWT token
     */
    OtpVerificationResponse verifyOtp(OtpVerificationRequest request, ClientType clientType);
    
    /**
     * Resend OTP
     */
    OtpSendResponse resendOtp(OtpResendRequest request, String ipAddress, String userAgent);
    
    /**
     * Cleanup expired OTPs
     */
    void cleanupExpiredOtps();

    /**
     * Send an OTP to a contact (phone or email) that the authenticated user wants
     * to adopt as their new phone/email. Does NOT require the identifier to already
     * belong to any user — the uniqueness check is the caller's responsibility.
     */
    void sendContactChangeOtp(String newContact, String ipAddress);

    /**
     * Verify an OTP that was sent via sendContactChangeOtp. Throws BadRequestException
     * on wrong/expired code. Does not issue JWT tokens — just confirms ownership.
     */
    void verifyContactChangeOtp(String newContact, String otpCode);
}