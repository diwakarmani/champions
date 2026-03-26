package com.propertyapp.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtpResendRequest {
    
    @NotBlank(message = "Email or mobile number is required")
    @Size(max = 100, message = "Identifier must not exceed 100 characters")
    private String identifier;
}