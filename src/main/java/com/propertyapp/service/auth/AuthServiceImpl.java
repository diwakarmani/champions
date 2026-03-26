package com.propertyapp.service.auth;

import com.propertyapp.dto.auth.*;
import com.propertyapp.entity.user.Role;
import com.propertyapp.entity.user.User;
import com.propertyapp.entity.user.VerificationToken;
import com.propertyapp.exception.*;
import com.propertyapp.repository.user.RoleRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.repository.user.VerificationTokenRepository;
import com.propertyapp.security.CustomUserDetails;
import com.propertyapp.security.JwtTokenProvider;
import com.propertyapp.service.communication.EmailService;
import com.propertyapp.util.OtpUtils;
import com.propertyapp.util.PasswordUtils;
import com.propertyapp.util.ValidationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {
    
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VerificationTokenRepository tokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @Value("${app.verification.base-url}")
    private String baseUrl;

    @Value("${app.verification.verify-endpoint}")
    private String verifyEndpoint;

    @Value("${app.verification.expiry-hours}")
    private int expiryHours;


    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        log.info("Registering new user with email: {}", request.getEmail());

        // Validate email
        if (!ValidationUtils.isValidEmail(request.getEmail())) {
            throw new BadRequestException("Invalid email format");
        }

        // Validate password
        if (!ValidationUtils.isValidPassword(request.getPassword())) {
            throw new BadRequestException(
                    "Password must be at least 8 characters with uppercase, lowercase, number and special character"
            );
        }

        // Check duplicates
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateResourceException("User", "phone", request.getPhone());
        }

        // Assign roles
        Set<Role> roles = new HashSet<>();
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            roles = roleRepository.findByNameIn(request.getRoles());
        } else {
            roleRepository.findByName("BUYER").ifPresent(roles::add);
        }

        // Create user
        User user = User.builder()
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(PasswordUtils.hash(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .emailVerified(false)
                .mobileVerified(false)
                .isActive(true)
                .isLocked(false)
                .roles(roles)
                .build();

        user = userRepository.save(user);

        // Generate verification token
        String token = UUID.randomUUID().toString();

        VerificationToken verificationToken = VerificationToken.builder()
                .user(user)
                .token(token)
                .tokenType("EMAIL_VERIFICATION")
                .expiresAt(LocalDateTime.now().plusHours(expiryHours))
                .build();

        tokenRepository.save(verificationToken);

        // Build verification URL
        String verificationUrl = baseUrl + verifyEndpoint + "?token=" + token;

        // Build HTML email
        String htmlContent = buildVerificationEmailTemplate(
                user.getFirstName(),
                verificationUrl
        );

        // Send email asynchronously
        emailService.sendEmailAsync(
                user.getEmail(),
                "Verify Your Email - Property Management System",
                htmlContent
        );

        log.info("Verification email triggered for: {}", user.getEmail());

        return AuthResponse.builder()
                .message("Registration successful. Please check your email to verify your account.")
                .email(user.getEmail())
                .build();
    }


    private String buildVerificationEmailTemplate(String firstName, String verificationUrl) {

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
        </head>
        <body style="font-family: Arial, sans-serif; background-color: #f4f6f8; padding: 20px;">
            <div style="max-width: 600px; margin: auto; background: #ffffff; padding: 30px; border-radius: 8px;">
                
                <h2 style="color: #2c3e50;">Welcome to Property Management System</h2>
                
                <p>Hi %s,</p>
                
                <p>Thank you for registering. Please verify your email address by clicking the button below:</p>
                
                <div style="text-align: center; margin: 30px 0;">
                    <a href="%s" 
                       style="background-color: #007bff; color: white; padding: 12px 20px; 
                              text-decoration: none; border-radius: 5px; display: inline-block;">
                        Verify Email
                    </a>
                </div>
                
                <p>This link will expire in %d hours.</p>
                
                <p>If you did not register, please ignore this email.</p>
                
                <hr/>
                <p style="font-size: 12px; color: gray;">
                    © %d Property Management System
                </p>
            </div>
        </body>
        </html>
        """.formatted(
                firstName,
                verificationUrl,
                expiryHours,
                LocalDateTime.now().getYear()
        );
    }

    @Override
    @Transactional
    public AuthResponse loginWithIdentifier(LoginRequest request) {
        log.info("Login attempt with identifier: {}", request.getEmail());

        // Try to find user by email OR mobile
        User user = userRepository.findByEmailOrPhone(request.getEmail(), request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        // Check if account is locked
        if (user.isLocked()) {
            throw new UnauthorizedException("Account is locked. Please contact support.");
        }

        try {
            // Authenticate using email (even if mobile was provided, we use email for auth)
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getEmail(), request.getPassword())
            );

            // Update last login
            user.updateLastLogin(request.getIpAddress());
            userRepository.save(user);

            // Generate tokens
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            String accessToken = jwtTokenProvider.generateToken(userDetails);
            String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

            log.info("User logged in successfully: {}", user.getEmail());

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(86400L) // 24 hours
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .roles(user.getRoles().stream().map(Role::getName).toList())
                    .message("Login successful")
                    .build();

        } catch (org.springframework.security.authentication.DisabledException d) {
            throw new DisabledException("Account is disabled");
        } catch (Exception e) {
            // Increment failed login attempts
            user.incrementFailedLoginAttempts();

            // Lock account after 5 failed attempts
            if (user.getFailedLoginAttempts() >= 5) {
                user.setLocked(true);
                log.warn("Account locked due to multiple failed login attempts: {}", user.getEmail());
            }

            userRepository.save(user);
            throw new UnauthorizedException("Invalid credentials");
        }
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for user: {}", request.getEmail());
        
        // Find user
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        
        // Check if account is locked
        if (user.isLocked()) {
            throw new UnauthorizedException("Account is locked. Please contact support.");
        }
        
        try {
            // Authenticate
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
            
            // Update last login
            user.updateLastLogin(request.getIpAddress());
            userRepository.save(user);
            
            // Generate tokens
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            String accessToken = jwtTokenProvider.generateToken(userDetails);
            String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);
            
            log.info("User logged in successfully: {}", request.getEmail());
            
            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(86400L) // 24 hours
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .roles(user.getRoles().stream().map(Role::getName).toList())
                    .message("Login successful")
                    .build();
            
        } catch (Exception e) {
            // Increment failed login attempts
            user.incrementFailedLoginAttempts();
            
            // Lock account after 5 failed attempts
            if (user.getFailedLoginAttempts() >= 5) {
                user.setLocked(true);
                log.warn("Account locked due to multiple failed login attempts: {}", request.getEmail());
            }
            
            userRepository.save(user);
            throw new UnauthorizedException("Invalid credentials");
        }
    }
    
    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        
        try {
            String email = jwtTokenProvider.extractUsername(refreshToken);
            User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                    .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
            
            CustomUserDetails userDetails = new CustomUserDetails(user);
            
            if (jwtTokenProvider.isTokenValid(refreshToken, userDetails)) {
                String newAccessToken = jwtTokenProvider.generateToken(userDetails);
                
                return AuthResponse.builder()
                        .accessToken(newAccessToken)
                        .refreshToken(refreshToken)
                        .tokenType("Bearer")
                        .expiresIn(86400L)
                        .message("Token refreshed successfully")
                        .build();
            }
            
            throw new UnauthorizedException("Invalid refresh token");
            
        } catch (Exception e) {
            log.error("Error refreshing token", e);
            throw new UnauthorizedException("Invalid refresh token");
        }
    }
    
    @Override
    @Transactional
    public void verifyEmail(String token) {
        log.info("Verifying email with token: {}", token);
        
        VerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid verification token"));
        
        if (verificationToken.isUsed()) {
            throw new BadRequestException("Token already used");
        }
        
        if (verificationToken.isExpired()) {
            throw new BadRequestException("Token expired");
        }
        
        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        
        verificationToken.markAsUsed();
        tokenRepository.save(verificationToken);
        
        log.info("Email verified successfully for user: {}", user.getEmail());
    }
    
    @Override
    @Transactional
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void resendVerificationEmail(String email) {
        log.info("Resending verification email to: {}", email);
        
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (user.isEmailVerified()) {
            throw new BadRequestException("Email already verified");
        }
        
        // Generate new token
        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = VerificationToken.builder()
                .user(user)
                .token(token)
                .tokenType("EMAIL_VERIFICATION")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        
        tokenRepository.save(verificationToken);
        
        // TODO: Send verification email
        log.info("Verification email resent to: {}", email);
    }
    
    @Override
    @Transactional
    public void forgotPassword(String email) {
        log.info("Password reset requested for: {}", email);
        
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        // Generate reset token
        String token = UUID.randomUUID().toString();
        VerificationToken resetToken = VerificationToken.builder()
                .user(user)
                .token(token)
                .tokenType("PASSWORD_RESET")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        
        tokenRepository.save(resetToken);
        
        // TODO: Send password reset email
        log.info("Password reset token generated for: {}", email);
    }
    
    @Override
    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        log.info("Resetting password with token");
        
        VerificationToken resetToken = tokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid reset token"));
        
        if (resetToken.isUsed()) {
            throw new BadRequestException("Token already used");
        }
        
        if (resetToken.isExpired()) {
            throw new BadRequestException("Token expired");
        }
        
        if (!ValidationUtils.isValidPassword(request.getNewPassword())) {
            throw new BadRequestException(
                "Password must be at least 8 characters with uppercase, lowercase, number and special character"
            );
        }
        
        User user = resetToken.getUser();
        user.setPasswordHash(PasswordUtils.hash(request.getNewPassword()));
        user.resetFailedLoginAttempts();
        user.setLocked(false);
        userRepository.save(user);
        
        resetToken.markAsUsed();
        tokenRepository.save(resetToken);
        
        log.info("Password reset successfully for user: {}", user.getEmail());
    }
}