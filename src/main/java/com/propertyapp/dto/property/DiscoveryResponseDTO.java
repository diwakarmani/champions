package com.propertyapp.dto.property;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DiscoveryResponseDTO {
    private List<PropertyDTO> popular;
    private List<PropertyDTO> recommended;
    private List<PropertyDTO> nearest;
}
