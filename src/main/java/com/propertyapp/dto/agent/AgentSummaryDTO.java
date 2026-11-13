package com.propertyapp.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.propertyapp.entity.user.User;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/** An agent as an admin sees them. */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentSummaryDTO {

    Long id;
    String name;
    String email;
    String phone;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.propertyapp.storage.ImageUrlSerializer.class)
    String photoUrl;
    String brokerageName;
    String licenseNumber;
    String licenseState;
    Integer yearsExperience;
    String serviceAreas;
    AgentStatus status;
    LocalDateTime suspendedAt;
    String suspensionReason;

    public static AgentSummaryDTO from(User agent) {
        return AgentSummaryDTO.builder()
                .id(agent.getId())
                .name((agent.getFirstName() + " " + agent.getLastName()).trim())
                .email(agent.getEmail())
                .phone(agent.getPhone())
                .photoUrl(agent.getProfileImageUrl())
                .brokerageName(agent.getBrokerageName())
                .licenseNumber(agent.getLicenseNumber())
                .licenseState(agent.getLicenseState())
                .yearsExperience(agent.getYearsExperience())
                .serviceAreas(agent.getServiceAreas())
                .status(agent.isAgentSuspended() ? AgentStatus.SUSPENDED : AgentStatus.ACTIVE)
                .suspendedAt(agent.getAgentSuspendedAt())
                .suspensionReason(agent.getAgentSuspensionReason())
                .build();
    }
}
