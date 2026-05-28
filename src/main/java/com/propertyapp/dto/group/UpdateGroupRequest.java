package com.propertyapp.dto.group;

import lombok.Data;

@Data
public class UpdateGroupRequest {
    private String name;
    private String companyName;
    private String businessLicense;
    private String address;
    private String description;
    private String logoUrl;
    private String website;
}
