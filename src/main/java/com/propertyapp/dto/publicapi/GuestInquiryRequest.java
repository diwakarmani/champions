package com.propertyapp.dto.publicapi;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Only what a visitor may say about themselves. Listing, status and account come from the server,
 * never from the body (ADR-0007).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestInquiryRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name is too long")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email is not valid")
    @Size(max = 100, message = "Email is too long")
    private String email;

    @Size(max = 20, message = "Phone is too long")
    @Pattern(regexp = "^[0-9+()\\-. ]*$", message = "Phone may contain digits, spaces and + ( ) - . only")
    private String phone;

    @NotBlank(message = "Message is required")
    @Size(max = 2000, message = "Message is too long")
    private String message;

    /** WEB or MOBILE; anything else is stored as WEB. */
    @Size(max = 10)
    private String source;

    /** Honeypot: hidden from people, so only bots fill it. */
    @Size(max = 500)
    private String website;

    @Size(max = 2048)
    private String turnstileToken;
}
