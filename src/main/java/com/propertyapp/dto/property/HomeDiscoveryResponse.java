package com.propertyapp.dto.property;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeDiscoveryResponse {

    private List<PropertyCardDTO> popular;
    private List<PropertyCardDTO> recommended;
    private List<PropertyCardDTO> nearest;
}