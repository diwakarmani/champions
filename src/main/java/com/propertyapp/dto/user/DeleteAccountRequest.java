package com.propertyapp.dto.user;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Proof that the account holder is asking: a password, or an OTP sent to their own email or phone (ADR-0008). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteAccountRequest {

    @Size(max = 200)
    private String password;

    @Size(max = 100)
    private String otpIdentifier;

    @Size(max = 10)
    private String otpCode;
}
