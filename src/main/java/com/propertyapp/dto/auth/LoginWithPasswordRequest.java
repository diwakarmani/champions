package com.propertyapp.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginWithPasswordRequest {
    
    @NotBlank(message = "Email or mobile number is required")
    private String identifier; // Email or mobile number
    
    @NotBlank(message = "Password is required")
    private String password;
}