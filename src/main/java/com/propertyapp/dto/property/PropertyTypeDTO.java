package com.propertyapp.dto.property;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PropertyTypeDTO {
    
    private Long id;
    private String name;
    private String description;
    private String iconUrl;
    private Integer displayOrder;
    private List<PropertySubTypeDTO> subTypes;
    private Boolean isActive;
}