package com.propertyapp.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

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

    // User info
    private Long userId;
    private String email;
    private String mobile;
    private String firstName;
    private String lastName;

    private String message;
}