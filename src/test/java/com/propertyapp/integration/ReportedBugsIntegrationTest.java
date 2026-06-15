package com.propertyapp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.property.PropertyAmenity;
import com.propertyapp.entity.property.PropertyType;
import com.propertyapp.entity.user.User;
import com.propertyapp.repository.inquiry.InquiryRepository;
import com.propertyapp.repository.locality.LocalityRepository;
import com.propertyapp.repository.property.PropertyAmenityRepository;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import com.propertyapp.repository.realtor.RealtorUserInteractionRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.repository.user.UserFavoriteRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportedBugsIntegrationTest {

    private static final DockerImageName POSTGIS = DockerImageName
            .parse("postgis/postgis:17-3.4")
            .asCompatibleSubstituteFor("postgres");

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGIS)
            .withDatabaseName("property_test")
            .withUsername("property_test")
            .withPassword("property_test");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.jpa.defer-datasource-initialization", () -> "false");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired PropertyTypeRepository propertyTypeRepository;
    @Autowired PropertyAmenityRepository propertyAmenityRepository;
    @Autowired LocalityRepository localityRepository;
    @Autowired InquiryRepository inquiryRepository;
    @Autowired UserFavoriteRepository userFavoriteRepository;
    @Autowired RealtorUserInteractionRepository realtorUserInteractionRepository;

    private String adminToken;
    private String buyerToken;
    private String sellerToken;
    private String realtorToken;

    @BeforeAll
    void authenticateSeededUsers() throws Exception {
        adminToken = login("admin@propertyapp.com", "Admin@123");
        buyerToken = login("buyer@propertyapp.com", "Demo@123");
        sellerToken = login("seller@propertyapp.com", "Demo@123");
        realtorToken = login("realtor@propertyapp.com", "Demo@123");
    }

    @Test
    void notificationContractsAreAuthenticatedAndReturnEmptySuccessResponses() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty());

        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void buyerCanLoadAndConnectToRealtorProfileEndToEnd() throws Exception {
        User realtor = userRepository.findByEmailAndDeletedAtIsNull("realtor@propertyapp.com").orElseThrow();
        User buyer = userRepository.findByEmailAndDeletedAtIsNull("buyer@propertyapp.com").orElseThrow();
        User seller = userRepository.findByEmailAndDeletedAtIsNull("seller@propertyapp.com").orElseThrow();
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();
        MvcResult createdResult = mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer(realtorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propertyPayload(typeId, localityId, "Realtor profile target")))
                .andExpect(status().isCreated())
                .andReturn();
        long realtorPropertyId = objectMapper.readTree(createdResult.getResponse().getContentAsString())
                .at("/data/id").asLong();
        mockMvc.perform(patch("/api/properties/{id}/publish", realtorPropertyId)
                        .header("Authorization", bearer(realtorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        Property realtorProperty = propertyRepository.findById(realtorPropertyId).orElseThrow();

        mockMvc.perform(get("/api/realtors/{id}", realtor.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/realtors/{id}", realtor.getId())
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(realtor.getId()))
                .andExpect(jsonPath("$.data.name").value("Demo Realtor"))
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.activeListingsCount").isNumber())
                .andExpect(jsonPath("$.data.areasServed").isArray());

        long before = realtorUserInteractionRepository.countDistinctUsersByRealtorId(realtor.getId());
        String payload = "{\"propertyId\":%d,\"message\":\"Interested in this listing\"}"
                .formatted(realtorProperty.getId());

        MvcResult first = mockMvc.perform(post("/api/realtors/{id}/connect", realtor.getId())
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.propertyId").value(realtorProperty.getId()))
                .andExpect(jsonPath("$.data.totalUserInteractions").value(before + 1))
                .andReturn();

        long interactionId = objectMapper.readTree(first.getResponse().getContentAsString())
                .at("/data/interactionId").asLong();
        assertThat(realtorUserInteractionRepository.findById(interactionId)).isPresent();

        mockMvc.perform(post("/api/realtors/{id}/connect", realtor.getId())
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interactionId").value(interactionId))
                .andExpect(jsonPath("$.data.totalUserInteractions").value(before + 1));

        JsonNode sellerPropertyResponse = createProperty(typeId, localityId, "Wrong owner target");
        Property sellerProperty = propertyRepository.findById(sellerPropertyResponse.at("/data/id").asLong()).orElseThrow();
        mockMvc.perform(post("/api/realtors/{id}/connect", realtor.getId())
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"propertyId\":%d}".formatted(sellerProperty.getId())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/realtors/{id}/connect", realtor.getId())
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/realtors/{id}", buyer.getId())
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void profileDateAcceptsIsoDatePersistsMidnightAndRejectsDateTime() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Demo","dateOfBirth":"2002-12-19","phone":"+1 415-555-0101"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User buyer = userRepository.findByEmailAndDeletedAtIsNull("buyer@propertyapp.com").orElseThrow();
        assertThat(buyer.getDateOfBirth()).isEqualTo(LocalDateTime.of(2002, 12, 19, 0, 0));

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateOfBirth\":\"not-a-date\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"123456789012345678901\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void propertyTypeAndAmenityResponsesMatchContractAndPersistAfterCachePrime() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String typeName = "Test Type " + suffix;
        String amenityName = "Test Amenity " + suffix;

        mockMvc.perform(get("/api/property-types/amenities"))
                .andExpect(status().isOk());

        MvcResult typeResult = mockMvc.perform(post("/api/admin/property-config/types")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                            put("name", typeName);
                            put("description", "Integration test type");
                            put("isActive", true);
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(typeName))
                .andReturn();

        long typeId = objectMapper.readTree(typeResult.getResponse().getContentAsString()).at("/data/id").asLong();
        assertThat(propertyTypeRepository.findById(typeId)).get()
                .extracting(PropertyType::getName).isEqualTo(typeName);

        mockMvc.perform(post("/api/admin/property-config/amenities")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","iconClass":"test-icon","category":"TEST","isActive":true}
                                """.formatted(amenityName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(amenityName));

        assertThat(propertyAmenityRepository.findAll()).extracting(PropertyAmenity::getName)
                .contains(amenityName);
        mockMvc.perform(get("/api/property-types/amenities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name", hasItem(amenityName)));

        mockMvc.perform(put("/api/admin/property-config/types/{id}", typeId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Inactive","isActive":false}
                                """.formatted(typeName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));

        assertThat(propertyTypeRepository.findById(typeId)).get()
                .extracting(PropertyType::isActive).isEqualTo(false);
        mockMvc.perform(get("/api/property-types"))
                .andExpect(jsonPath("$.data[*].name", not(hasItem(typeName))));
    }

    @Test
    void propertyCreateValidatesLocalityPersistsCanonicalAddressAndRejectsBuyer() throws Exception {
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();
        long countBeforeRejectedRequests = propertyRepository.count();

        mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propertyPayload(typeId, null, "Missing locality")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*]", hasItem("localityId: Locality is required")));

        mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propertyPayload(typeId, localityId, "Buyer forbidden")))
                .andExpect(status().isForbidden());
        assertThat(propertyRepository.count()).isEqualTo(countBeforeRejectedRequests);

        mockMvc.perform(get("/api/properties/{id}", 999_999_999L))
                .andExpect(status().isNotFound());

        JsonNode created = createProperty(typeId, localityId, "Seller persisted");
        Property stored = propertyRepository.findById(created.at("/data/id").asLong()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo("DRAFT");
        assertThat(stored.getLocalityRef().getId()).isEqualTo(localityId);
        assertThat(stored.getCity()).isEqualTo(created.at("/data/city").asText());
        assertThat(stored.getCity()).isNotEqualTo("Client supplied city");
    }

    @Test
    void inquiryCreateAndReceivedFlowPersistsAndReturnsOwnersData() throws Exception {
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();
        JsonNode property = createProperty(typeId, localityId, "Inquiry target");
        long propertyId = property.at("/data/id").asLong();

        mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"propertyId":%d,"name":"Buyer","email":"buyer@propertyapp.com","message":"Please contact me"}
                                """.formatted(propertyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEW"));

        assertThat(inquiryRepository.count()).isGreaterThan(0);
        long inquiryCountAfterCreate = inquiryRepository.count();
        mockMvc.perform(get("/api/inquiries/received")
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].propertyId", hasItem((int) propertyId)));

        mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"propertyId\":%d,\"name\":\"Buyer\",\"email\":\"bad\",\"message\":\"x\"}".formatted(propertyId)))
                .andExpect(status().isBadRequest());
        assertThat(inquiryRepository.count()).isEqualTo(inquiryCountAfterCreate);
    }

    @Test
    void adminApprovalAndFeatureTogglesPersistAcrossFreshReads() throws Exception {
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();
        JsonNode property = createProperty(typeId, localityId, "Approval target");
        long propertyId = property.at("/data/id").asLong();

        mockMvc.perform(get("/api/admin/properties").param("status", "ALL"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/properties")
                        .param("status", "ALL")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/properties")
                        .param("status", "ALL")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/properties/{id}/approve", propertyId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        assertThat(propertyRepository.findById(propertyId)).get()
                .extracting(Property::getStatus).isEqualTo("ACTIVE");

        mockMvc.perform(patch("/api/admin/properties/{id}/toggle-featured", propertyId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFeatured").value(true))
                .andExpect(jsonPath("$.data.featured").doesNotExist());
        mockMvc.perform(patch("/api/admin/properties/{id}/toggle-verified", propertyId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isVerified").value(true));

        Property fresh = propertyRepository.findById(propertyId).orElseThrow();
        assertThat(fresh.isFeatured()).isTrue();
        assertThat(fresh.isVerified()).isTrue();

        mockMvc.perform(get("/api/admin/properties").param("status", "ACTIVE")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", hasItem((int) propertyId)));

        for (String statusFilter : java.util.List.of("ALL", "ACTIVE", "REJECTED", "PENDING_APPROVAL")) {
            mockMvc.perform(get("/api/admin/properties")
                            .param("status", statusFilter)
                            .header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Test
    void favoritesAreBuyerOnlyAndPersistForBuyer() throws Exception {
        long propertyId = propertyRepository.findAll().getFirst().getId();

        mockMvc.perform(post("/api/favorites/{propertyId}", propertyId))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/favorites/{propertyId}", propertyId)
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/favorites/{propertyId}", propertyId)
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/favorites/{propertyId}", propertyId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/favorites/{propertyId}", propertyId)
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk());
        assertThat(userFavoriteRepository.existsByUserIdAndPropertyId(
                userRepository.findByEmailAndDeletedAtIsNull("buyer@propertyapp.com").orElseThrow().getId(), propertyId
        )).isTrue();

        mockMvc.perform(get("/api/favorites/{propertyId}/check", propertyId)
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
        mockMvc.perform(get("/api/favorites")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", hasItem((int) propertyId)));

        mockMvc.perform(delete("/api/favorites/{propertyId}", propertyId)
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk());
        assertThat(userFavoriteRepository.existsByUserIdAndPropertyId(
                userRepository.findByEmailAndDeletedAtIsNull("buyer@propertyapp.com").orElseThrow().getId(), propertyId
        )).isFalse();
    }

    private JsonNode createProperty(long typeId, long localityId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propertyPayload(typeId, localityId, title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String propertyPayload(long typeId, Long localityId, String title) throws Exception {
        var payload = objectMapper.createObjectNode();
        payload.put("title", title + " " + UUID.randomUUID());
        payload.put("description", "A property created by the reported bug integration suite");
        payload.put("propertyTypeId", typeId);
        payload.put("listingType", "SALE");
        payload.put("price", 250000);
        payload.put("addressLine1", "1 Test Street");
        payload.put("city", "Client supplied city");
        payload.put("state", "Client supplied state");
        payload.put("country", "Client supplied country");
        if (localityId != null) payload.put("localityId", localityId);
        return objectMapper.writeValueAsString(payload);
    }

    private String login(String identifier, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login-with-identifier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
