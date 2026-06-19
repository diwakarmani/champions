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
    // Always include isActive (even when false) so the edit form initialises
    // correctly. NON_NULL at class level would omit a null Boolean, and the
    // frontend would default it to true — causing inactive types to appear
    // active in the edit popup (Bug 6).
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Boolean isActive;
}