package com.propertyapp.dto.property;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtorProfileDTO {
    private Long id;
    private String name;
    private String profilePhotoUrl;
    private String phone;
    private String email;
    private String bio;
    private List<String> areasServed;
    private List<String> languages;
    private Double ratingAverage;
    private long ratingCount;
    private long totalUserInteractions;
    private long activeListingsCount;
    private String verificationStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
