package com.propertyapp.dto.property;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyAmenityCreateRequest {

    @jakarta.validation.constraints.NotBlank
    private String name;

    private String iconClass;

    @jakarta.validation.constraints.NotBlank
    private String category;

    @jakarta.validation.constraints.NotNull
    private Integer displayOrder;
}
