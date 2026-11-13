package com.propertyapp.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The explanation an admin must give when rejecting an application or suspending an agent. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminReasonRequest {

    @NotBlank(message = "A reason is required")
    @Size(max = 1000, message = "Reason is too long")
    private String reason;
}
