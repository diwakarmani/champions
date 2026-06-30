package com.propertyapp.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyContactChangeRequest {

    @NotBlank(message = "New contact value is required")
    @Size(max = 100, message = "Contact value must not exceed 100 characters")
    private String newContact;

    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "\\d{6}", message = "OTP must be exactly 6 digits")
    private String otpCode;
}
