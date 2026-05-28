package com.propertyapp.controller;

import com.propertyapp.dto.auth.*;
import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication management APIs")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", response));
    }

//    @PostMapping("/login")
//    @Operation(summary = "Login with email and password")
//    public ResponseEntity<ApiResponse<AuthResponse>> login(
//            @Valid @RequestBody LoginRequest request,
//            HttpServletRequest httpRequest
//    ) {
//        // Get IP address
//        request.setIpAddress(getClientIp(httpRequest));
//
//        AuthResponse response = authService.login(request);
//        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
//    }

    @PostMapping("/login-with-identifier")
    @Operation(summary = "Login with email OR mobile and password")
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithIdentifier(
            @Valid @RequestBody LoginWithPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        // Convert to LoginRequest
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(request.getIdentifier()); // Can be email or mobile
        loginRequest.setPassword(request.getPassword());
        loginRequest.setIpAddress(getClientIp(httpRequest));

        AuthResponse response = authService.loginWithIdentifier(loginRequest);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Verify email address")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @RequestParam String token
    ) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", null));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @RequestParam String email
    ) {
        authService.resendVerificationEmail(email);
        return ResponseEntity.ok(ApiResponse.success("Verification email sent", null));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @RequestParam String email
    ) {
        authService.forgotPassword(email);
        return ResponseEntity.ok(ApiResponse.success("Password reset email sent", null));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password with token")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successful", null));
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