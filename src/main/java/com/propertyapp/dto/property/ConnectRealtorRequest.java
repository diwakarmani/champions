package com.propertyapp.dto.property;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectRealtorRequest {
    private Long propertyId;

    @Size(max = 2000, message = "Message must not exceed 2000 characters")
    private String message;
}
