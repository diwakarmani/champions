package com.propertyapp.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OtpVerificationResponse {

    // JWT tokens (same as password login)
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;

    // User info — `id` aligns with password-login AuthResponse and /users/me
    private Long id;
    private String email;
    private String phone;
    private String firstName;
    private String lastName;
    private List<String> roles;

    private String message;
}