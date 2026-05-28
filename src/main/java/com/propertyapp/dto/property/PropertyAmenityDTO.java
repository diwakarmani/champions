package com.propertyapp.dto.property;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PropertyAmenityDTO {
    
    private Long id;
    private String name;
    private String iconClass;
    private String category;
    private Integer displayOrder;
    private Boolean isActive;
}