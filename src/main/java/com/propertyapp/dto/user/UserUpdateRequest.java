package com.propertyapp.dto.user;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {
    
    @Size(max = 50)
    @Pattern(regexp = "(?s).*\\S.*", message = "First name cannot be blank")
    private String firstName;
    
    @Size(max = 50)
    @Pattern(regexp = "(?s).*\\S.*", message = "Last name cannot be blank")
    private String lastName;

    private LocalDate dateOfBirth;

    // Limits match the columns (a longer value was a 500) and refuse script URLs (S12 review).
    @Size(max = 10)
    private String gender;

    @Size(max = 2000, message = "Bio can be up to 2000 characters")
    private String bio;

    @Size(max = 255)
    @Pattern(regexp = "^$|^https?://[^\\s<>\"']+$", message = "Photo must be an http(s) address")
    private String profileImageUrl;

    @Size(max = 100)
    private String occupation;

    @Size(max = 255)
    @Pattern(regexp = "^$|^(https?://)?[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(/[^\\s<>\"']*)?$",
            message = "Enter a web address such as example.com")
    private String website;
}