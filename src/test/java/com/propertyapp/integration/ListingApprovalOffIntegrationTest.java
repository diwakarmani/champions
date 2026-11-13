package com.propertyapp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.user.User;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.util.PasswordUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Own database: the approval step is switched off here (ADR-0005). */
@SpringBootTest(properties = "app.listing.approval-required=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ListingApprovalOffIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(AbstractPostgisIntegrationTest.POSTGIS);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        AbstractPostgisIntegrationTest.register(registry, POSTGRES);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PropertyRepository propertyRepository;
    @Autowired PropertyTypeRepository propertyTypeRepository;
    @Autowired UserRepository userRepository;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void publishGoesStraightToActiveAndIsPubliclyVisible() throws Exception {
        User realtor = userRepository.findByEmailAndDeletedAtIsNull("realtor@propertyapp.com").orElseThrow();
        long id = draft(realtor);

        mockMvc.perform(patch("/api/properties/{id}/publish", id)
                        .header("Authorization", "Bearer " + login("realtor@propertyapp.com", "Demo@123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(propertyRepository.findById(id).orElseThrow().getPublishedAt()).isNotNull();
        mockMvc.perform(get("/api/properties/{id}", id)).andExpect(status().isOk());
    }

    @Test
    void theFlagNeverLetsAListingGoLiveWithoutAnAgent() throws Exception {
        User seller = userRepository.saveAndFlush(User.builder()
                .email("former-seller@example.com").passwordHash(PasswordUtils.hash("Str0ng@Passw0rd"))
                .firstName("Former").lastName("Seller").emailVerified(true).mobileVerified(false)
                .isActive(true).isLocked(false).build());
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = 'SELLER'",
                seller.getId());
        long id = draft(seller);

        int status = mockMvc.perform(patch("/api/properties/{id}/publish", id)
                        .header("Authorization", "Bearer " + login(seller.getEmail(), "Str0ng@Passw0rd")))
                .andReturn().getResponse().getStatus();

        assertThat(status).isIn(400, 401, 403);
        assertThat(propertyRepository.findById(id).orElseThrow().getStatus()).isEqualTo("DRAFT");
    }

    private long draft(User owner) {
        return propertyRepository.saveAndFlush(Property.builder()
                .title("Approval off " + owner.getId()).owner(owner)
                .propertyType(propertyTypeRepository.findAll().get(0))
                .listingType("SALE").price(BigDecimal.valueOf(300_000))
                .addressLine1("1 Test St").city("Austin").state("Texas").country("USA")
                .status("DRAFT").build()).getId();
    }

    private String login(String identifier, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login-with-identifier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", identifier, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/accessToken").asText();
    }
}
