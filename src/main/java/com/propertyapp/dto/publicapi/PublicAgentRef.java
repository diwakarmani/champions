package com.propertyapp.dto.publicapi;

import com.propertyapp.entity.user.User;
import com.propertyapp.util.Slugs;
import lombok.Builder;
import lombok.Value;

/** The listing agent as a visitor sees them on a property. No email or phone (ADR-0007). */
@Value
@Builder
public class PublicAgentRef {

    Long id;
    String name;
    String slug;
    String path;
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.propertyapp.storage.ImageUrlSerializer.class)
    String photoUrl;
    String brokerageName;

    public static PublicAgentRef from(User agent) {
        String name = PublicNames.of(agent);
        String slug = Slugs.of("agent", name);
        return PublicAgentRef.builder()
                .id(agent.getId())
                .name(name)
                .slug(slug)
                .path(Slugs.agentPath(slug, agent.getId()))
                .photoUrl(agent.getProfileImageUrl())
                .brokerageName(agent.getBrokerageName())
                .build();
    }
}
