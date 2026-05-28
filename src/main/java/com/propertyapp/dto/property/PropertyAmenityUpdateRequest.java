package com.propertyapp.dto.property;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyAmenityUpdateRequest {

    @jakarta.validation.constraints.NotBlank
    private String name;

    private String iconClass;

    private String category;

    private Integer displayOrder;

    private Boolean isActive;
}
