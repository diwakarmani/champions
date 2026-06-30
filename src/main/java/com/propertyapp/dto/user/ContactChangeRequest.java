package com.propertyapp.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactChangeRequest {

    @NotBlank(message = "New contact value is required")
    @Size(max = 100, message = "Contact value must not exceed 100 characters")
    private String newContact;
}
