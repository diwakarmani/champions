package com.propertyapp.dto.property;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyTypeUpdateRequest {

    @jakarta.validation.constraints.NotBlank
    private String name;

    private String description;

    private Integer displayOrder;

    private Boolean isActive;
}
