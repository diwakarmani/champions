package com.propertyapp.dto.property;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PropertySubTypeDTO {
    
    private Long id;
    private String name;
    private String description;
    private Long propertyTypeId;
    private Integer displayOrder;
    private Boolean isActive;
}