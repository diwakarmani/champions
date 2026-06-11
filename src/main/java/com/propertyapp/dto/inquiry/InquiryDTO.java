package com.propertyapp.dto.inquiry;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InquiryDTO {
    private Long id;
    private Long propertyId;
    private String propertyTitle;
    private Long inquirerId;
    private String name;
    private String email;
    private String phone;
    private String message;
    private String status;
    private String createdAt;
}
