package com.propertyapp.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** One PostGIS container, shared by every Spring test in the JVM. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractPostgisIntegrationTest {

    public static final DockerImageName POSTGIS = DockerImageName
            .parse("postgis/postgis:17-3.4")
            .asCompatibleSubstituteFor("postgres");

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGIS)
            .withDatabaseName("property_test")
            .withUsername("property_test")
            .withPassword("property_test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        register(registry, POSTGRES);
    }

    public static void register(DynamicPropertyRegistry registry, PostgreSQLContainer<?> container) {
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        // The real migrations build the schema, and Hibernate checks the entities against it.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
