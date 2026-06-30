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
    private String firstName;
    
    @Size(max = 50)
    private String lastName;

    private LocalDate dateOfBirth;
    private String gender;
    private String bio;
    private String profileImageUrl;
    private String occupation;
    private String website;
}