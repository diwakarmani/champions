package com.propertyapp.dto.location;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCityRequestDTO {

    private String name;
    private Long stateId;
}