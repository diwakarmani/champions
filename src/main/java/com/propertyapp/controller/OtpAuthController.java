package com.propertyapp.controller;

import com.propertyapp.dto.auth.*;
import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.service.auth.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/otp")
@RequiredArgsConstructor
@Tag(name = "OTP Authentication", description = "OTP-based login APIs")
public class OtpAuthController {
    
    private final OtpService otpService;
    
    @PostMapping("/send")
    @Operation(summary = "Send OTP", description = "Send OTP to email or mobile number")
    public ResponseEntity<ApiResponse<OtpSendResponse>> sendOtp(
            @Valid @RequestBody OtpLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        
        OtpSendResponse response = otpService.sendOtp(request, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @PostMapping("/verify")
    @Operation(summary = "Verify OTP", description = "Verify OTP and get JWT token")
    public ResponseEntity<ApiResponse<OtpVerificationResponse>> verifyOtp(
            @Valid @RequestBody OtpVerificationRequest request
    ) {
        OtpVerificationResponse response = otpService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }
    
    @PostMapping("/resend")
    @Operation(summary = "Resend OTP", description = "Resend OTP code")
    public ResponseEntity<ApiResponse<OtpSendResponse>> resendOtp(
            @Valid @RequestBody OtpResendRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        
        OtpSendResponse response = otpService.resendOtp(request, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}