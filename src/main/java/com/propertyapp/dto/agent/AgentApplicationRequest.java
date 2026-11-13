package com.propertyapp.dto.agent;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Only what an applicant may state about themselves. Status and review fields are never client-supplied. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentApplicationRequest {

    @NotBlank(message = "License number is required")
    @Size(max = 50, message = "License number is too long")
    private String licenseNumber;

    @NotBlank(message = "License state is required")
    @Pattern(regexp = "^[A-Za-z]{2}$", message = "License state must be a 2-letter state code")
    private String licenseState;

    @NotBlank(message = "Brokerage name is required")
    @Size(max = 150, message = "Brokerage name is too long")
    private String brokerageName;

    @Min(value = 0, message = "Years of experience cannot be negative")
    @Max(value = 80, message = "Years of experience is too high")
    private Integer yearsExperience;

    @Size(max = 500, message = "Service areas are too long")
    private String serviceAreas;

    @Size(max = 2000, message = "Bio is too long")
    private String bio;

    @Size(max = 500, message = "Photo URL is too long")
    @Pattern(regexp = "^https?://\\S+$", message = "Photo must be an http or https URL")
    private String photoUrl;
}
