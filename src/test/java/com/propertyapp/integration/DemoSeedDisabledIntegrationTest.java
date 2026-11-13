package com.propertyapp.integration;

import com.propertyapp.repository.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** Own database: this context seeds differently from the shared one. */
@SpringBootTest(properties = "app.seed.demo-users=false")
@ActiveProfiles("test")
@Testcontainers
class DemoSeedDisabledIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(AbstractPostgisIntegrationTest.POSTGIS);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        AbstractPostgisIntegrationTest.register(registry, POSTGRES);
    }

    @Autowired UserRepository userRepository;

    @Test
    void demoAccountsAreNotCreatedByDefaultButTheAdminStillIs() {
        assertThat(userRepository.findByEmailAndDeletedAtIsNull("buyer@propertyapp.com")).isEmpty();
        assertThat(userRepository.findByEmailAndDeletedAtIsNull("realtor@propertyapp.com")).isEmpty();
        assertThat(userRepository.findByEmailAndDeletedAtIsNull("seller@propertyapp.com")).isEmpty();
        assertThat(userRepository.findByEmailAndDeletedAtIsNull("admin@propertyapp.com")).isPresent();
    }
}
