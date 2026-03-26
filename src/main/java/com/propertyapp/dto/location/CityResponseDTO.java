package com.propertyapp.dto.location;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CityResponseDTO {

    private Long id;
    private String name;
    private String state;
    private String country;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Boolean isActive;
    private String osmType; // relation | way | node
    private Long osmId;
}