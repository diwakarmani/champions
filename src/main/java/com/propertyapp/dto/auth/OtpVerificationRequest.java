package com.propertyapp.dto.auth;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerificationRequest {
    
    @NotBlank(message = "Email or mobile number is required")
    @Size(max = 100, message = "Identifier must not exceed 100 characters")
    private String identifier;
    
    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
    private String otpCode;
}