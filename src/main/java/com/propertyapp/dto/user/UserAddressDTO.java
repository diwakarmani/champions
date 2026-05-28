package com.propertyapp.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserAddressDTO {
    
    private Long id;
    
    @NotBlank(message = "Address line 1 is required")
    private String addressLine1;
    
    private String addressLine2;
    
    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;
    
    @NotBlank(message = "State is required")
    @Size(max = 100)
    private String state;
    
    @NotBlank(message = "Country is required")
    @Size(max = 100)
    private String country;
    
    @NotBlank(message = "Postal code is required")
    @Size(max = 20)
    private String postalCode;
    
    private String addressType;
    private boolean isDefault;
}