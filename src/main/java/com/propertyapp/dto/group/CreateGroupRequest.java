package com.propertyapp.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateGroupRequest {

    @NotBlank(message = "Group name is required")
    @Size(max = 100)
    private String name;

    @Size(max = 150)
    private String companyName;

    @Size(max = 100)
    private String businessLicense;

    private String address;

    private String description;

    private String logoUrl;

    @Size(max = 200)
    private String website;
}
