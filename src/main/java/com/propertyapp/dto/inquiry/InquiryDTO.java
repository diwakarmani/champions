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
    /** Set only when the property owner is a REALTOR — used by the frontend to show the Rate button. */
    private Long realtorId;
    private String realtorName;
    private String name;
    private String email;
    private String phone;
    private String message;
    private String status;
    private String createdAt;
}
