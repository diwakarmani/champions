package com.propertyapp.dto.rating;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RatingDTO {
    private Long id;
    private Long realtorId;
    private Long propertyId;
    private Long raterId;
    private String raterName;
    private String raterPhotoUrl;
    private int rating;
    private String comment;
    private String createdAt;
    private String updatedAt;
}
