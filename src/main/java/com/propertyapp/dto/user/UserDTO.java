package com.propertyapp.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO {
    
    private Long id;
    private String email;
    private String phone;
    private String firstName;
    private String lastName;
    private String profileImageUrl;
    private LocalDateTime dateOfBirth;
    private String gender;
    private String bio;
    
    private boolean emailVerified;
    private boolean phoneVerified;
    private boolean isLocked;
    
    private LocalDateTime lastLoginAt;
    private Set<String> roles;
    private List<UserAddressDTO> addresses;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}