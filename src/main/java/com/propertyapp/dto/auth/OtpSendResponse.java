package com.propertyapp.dto.auth;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpSendResponse {
    
    private String identifier; // Masked
    private String identifierType; // EMAIL or MOBILE
    private Integer expiresIn; // Seconds
    private String message;
}