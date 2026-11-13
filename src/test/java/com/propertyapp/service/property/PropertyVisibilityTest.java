package com.propertyapp.service.property;

import com.propertyapp.entity.user.Role;
import com.propertyapp.entity.user.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyVisibilityTest {

    @Test
    void anActiveAgentHasTheRoleIsActiveNotDeletedAndNotSuspended() {
        assertThat(PropertyVisibility.isActiveAgent(agent())).isTrue();

        User inactive = agent();
        inactive.setActive(false);
        assertThat(PropertyVisibility.isActiveAgent(inactive)).isFalse();

        User deleted = agent();
        deleted.setDeletedAt(LocalDateTime.now());
        assertThat(PropertyVisibility.isActiveAgent(deleted)).isFalse();

        User suspended = agent();
        suspended.suspendAsAgent("test");
        assertThat(PropertyVisibility.isActiveAgent(suspended)).isFalse();

        suspended.reinstateAsAgent();
        assertThat(PropertyVisibility.isActiveAgent(suspended)).isTrue();

        User buyer = agent();
        buyer.setRoles(new HashSet<>(Set.of(Role.builder().name("BUYER").build())));
        assertThat(PropertyVisibility.isActiveAgent(buyer)).isFalse();

        assertThat(PropertyVisibility.isActiveAgent(null)).isFalse();
    }

    private User agent() {
        return User.builder()
                .isActive(true)
                .roles(new HashSet<>(Set.of(Role.builder().name("REALTOR").build())))
                .build();
    }
}
