package com.propertyapp.service.auth;

import com.propertyapp.dto.auth.*;

public interface AuthService {
    
    AuthResponse register(RegisterRequest request);
    
    AuthResponse login(LoginRequest request);
    
    AuthResponse refreshToken(RefreshTokenRequest request);
    
    void verifyEmail(String token);
    
    void resendVerificationEmail(String email);
    
    void forgotPassword(String email);
    
    void resetPassword(PasswordResetRequest request);

    AuthResponse loginWithIdentifier(LoginRequest request);
}