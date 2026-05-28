package com.propertyapp.dto.property;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertySubTypeCreateRequest {

    @jakarta.validation.constraints.NotNull
    private Long propertyTypeId;

    @jakarta.validation.constraints.NotBlank
    private String name;

    private String description;

    @jakarta.validation.constraints.NotNull
    private Integer displayOrder;
}
