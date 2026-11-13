package com.propertyapp.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.propertyapp.entity.agent.AgentApplication;
import com.propertyapp.entity.agent.AgentApplicationStatus;
import com.propertyapp.entity.user.User;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentApplicationDTO {

    Long id;
    Long userId;
    String applicantName;
    String applicantEmail;
    String applicantPhone;
    AgentApplicationStatus status;
    String licenseNumber;
    String licenseState;
    String brokerageName;
    Integer yearsExperience;
    String serviceAreas;
    String bio;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.propertyapp.storage.ImageUrlSerializer.class)
    String photoUrl;
    LocalDateTime reviewedAt;
    String rejectionReason;
    LocalDateTime createdAt;

    public static AgentApplicationDTO from(AgentApplication application) {
        User applicant = application.getUser();
        return AgentApplicationDTO.builder()
                .id(application.getId())
                .userId(applicant.getId())
                .applicantName((applicant.getFirstName() + " " + applicant.getLastName()).trim())
                .applicantEmail(applicant.getEmail())
                .applicantPhone(applicant.getPhone())
                .status(application.getStatus())
                .licenseNumber(application.getLicenseNumber())
                .licenseState(application.getLicenseState())
                .brokerageName(application.getBrokerageName())
                .yearsExperience(application.getYearsExperience())
                .serviceAreas(application.getServiceAreas())
                .bio(application.getBio())
                .photoUrl(application.getPhotoUrl())
                .reviewedAt(application.getReviewedAt())
                .rejectionReason(application.getRejectionReason())
                .createdAt(application.getCreatedAt())
                .build();
    }
}
