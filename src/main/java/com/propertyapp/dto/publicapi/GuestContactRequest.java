package com.propertyapp.dto.publicapi;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body for revealing an agent's phone and email; empty unless Turnstile is on. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestContactRequest {

    @Size(max = 2048)
    private String turnstileToken;
}
