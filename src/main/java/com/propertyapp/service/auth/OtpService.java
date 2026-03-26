package com.propertyapp.service.auth;

import com.propertyapp.dto.auth.*;

public interface OtpService {
    
    /**
     * Send OTP to email or mobile (auto-detect)
     */
    OtpSendResponse sendOtp(OtpLoginRequest request, String ipAddress, String userAgent);
    
    /**
     * Verify OTP and return JWT token
     */
    OtpVerificationResponse verifyOtp(OtpVerificationRequest request);
    
    /**
     * Resend OTP
     */
    OtpSendResponse resendOtp(OtpResendRequest request, String ipAddress, String userAgent);
    
    /**
     * Cleanup expired OTPs
     */
    void cleanupExpiredOtps();
}