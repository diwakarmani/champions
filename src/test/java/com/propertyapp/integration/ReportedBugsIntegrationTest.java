package com.propertyapp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.property.PropertyAmenity;
import com.propertyapp.entity.property.PropertyType;
import com.propertyapp.entity.user.User;
import com.propertyapp.entity.inquiry.InquiryStatus;
import com.propertyapp.repository.inquiry.InquiryRepository;
import com.propertyapp.repository.locality.LocalityRepository;
import com.propertyapp.repository.property.PropertyAmenityRepository;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.property.PropertySubTypeRepository;
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
    @Autowired PropertySubTypeRepository propertySubTypeRepository;
    @Autowired PropertyAmenityRepository propertyAmenityRepository;
    @Autowired LocalityRepository localityRepository;
    @Autowired InquiryRepository inquiryRepository;
    @Autowired com.propertyapp.repository.notification.NotificationRepository notificationRepository;
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

    /**
     * Bug 12 — the single-object PUT response above always reflected isActive correctly (it uses
     * a hand-rolled mapping), which is exactly why the earlier "explicit false preserved" fix
     * looked complete. The regression lived in the LIST endpoints, which use the MapStruct
     * mapper: PropertyMapper.toTypeDTO/toSubTypeDTO never mapped isActive at all (entity getter
     * isActive() resolves to JavaBean property "active", not "isActive", so MapStruct's implicit
     * matching silently missed it under unmappedTargetPolicy=IGNORE) — every LIST response
     * returned isActive:null regardless of the true value. This test hits the LIST endpoints
     * themselves, which the old coverage never did.
     */
    // ── Net-new: Property Type delete + Amenity edit/delete (endpoints did not exist at all
    // before this pass — the service layer already had unwired deactivateType/deactivateSubType
    // methods, but no controller endpoint called them, and no update/deactivate methods existed
    // for amenities at all) ──

    @Test
    void deletingATypeDeactivatesItAndRemovesItFromThePublicList() throws Exception {
        String typeName = "DelType" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult created = mockMvc.perform(post("/api/admin/property-config/types")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Delete-type regression","isActive":true}
                                """.formatted(typeName)))
                .andExpect(status().isOk())
                .andReturn();
        long typeId = objectMapper.readTree(created.getResponse().getContentAsString()).at("/data/id").asLong();

        mockMvc.perform(get("/api/property-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(typeId)).exists());

        mockMvc.perform(delete("/api/admin/property-config/types/{id}", typeId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/property-config/types")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)].isActive".formatted(typeId), hasItem(false)));

        mockMvc.perform(get("/api/property-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(typeId)).doesNotExist());
    }

    @Test
    void updatingAnAmenityPersistsTheChangeInTheAdminList() throws Exception {
        String amenityName = "UpdAmenity" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult created = mockMvc.perform(post("/api/admin/property-config/amenities")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","category":"Original","isActive":true}
                                """.formatted(amenityName)))
                .andExpect(status().isOk())
                .andReturn();
        long amenityId = objectMapper.readTree(created.getResponse().getContentAsString()).at("/data/id").asLong();

        String renamed = amenityName + "-Renamed";
        mockMvc.perform(put("/api/admin/property-config/amenities/{id}", amenityId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","category":"Updated","isActive":true}
                                """.formatted(renamed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(renamed))
                .andExpect(jsonPath("$.data.category").value("Updated"));

        mockMvc.perform(get("/api/admin/property-config/amenities")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)].name".formatted(amenityId), hasItem(renamed)));
    }

    @Test
    void deletingAnAmenityDeactivatesItAndRemovesItFromThePublicList() throws Exception {
        String amenityName = "DelAmenity" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult created = mockMvc.perform(post("/api/admin/property-config/amenities")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","category":"ToDelete","isActive":true}
                                """.formatted(amenityName)))
                .andExpect(status().isOk())
                .andReturn();
        long amenityId = objectMapper.readTree(created.getResponse().getContentAsString()).at("/data/id").asLong();

        mockMvc.perform(get("/api/property-types/amenities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(amenityId)).exists());

        mockMvc.perform(delete("/api/admin/property-config/amenities/{id}", amenityId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/property-config/amenities")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)].isActive".formatted(amenityId), hasItem(false)));

        mockMvc.perform(get("/api/property-types/amenities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(amenityId)).doesNotExist());
    }

    @Test
    void nonAdminCannotDeleteTypesOrAmenities() throws Exception {
        String typeName = "GuardType" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult created = mockMvc.perform(post("/api/admin/property-config/types")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","isActive":true}
                                """.formatted(typeName)))
                .andExpect(status().isOk())
                .andReturn();
        long typeId = objectMapper.readTree(created.getResponse().getContentAsString()).at("/data/id").asLong();

        mockMvc.perform(delete("/api/admin/property-config/types/{id}", typeId)
                        .header("Authorization", bearer(realtorToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminTypeListReflectsDeactivatedStateNotJustTheSinglePutResponse() throws Exception {
        String typeName = "B12Admin" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult created = mockMvc.perform(post("/api/admin/property-config/types")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Bug 12 regression","isActive":true}
                                """.formatted(typeName)))
                .andExpect(status().isOk())
                .andReturn();
        long typeId = objectMapper.readTree(created.getResponse().getContentAsString()).at("/data/id").asLong();

        mockMvc.perform(put("/api/admin/property-config/types/{id}", typeId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Now inactive","isActive":false}
                                """.formatted(typeName)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/property-config/types")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)].isActive".formatted(typeId), hasItem(false)));
    }

    @Test
    void publicTypeListReflectsCorrectSubTypeActiveStateNotNull() throws Exception {
        String typeName = "B12Parent" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult created = mockMvc.perform(post("/api/admin/property-config/types")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Bug 12 subtype regression","isActive":true}
                                """.formatted(typeName)))
                .andExpect(status().isOk())
                .andReturn();
        long typeId = objectMapper.readTree(created.getResponse().getContentAsString()).at("/data/id").asLong();

        String activeSubName = "ActSub" + UUID.randomUUID().toString().substring(0, 8);
        String inactiveSubName = "InactSub" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/admin/property-config/sub-types")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","propertyTypeId":%d,"isActive":true}
                                """.formatted(activeSubName, typeId)))
                .andExpect(status().isOk());

        MvcResult inactiveSubResult = mockMvc.perform(post("/api/admin/property-config/sub-types")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","propertyTypeId":%d,"isActive":true}
                                """.formatted(inactiveSubName, typeId)))
                .andExpect(status().isOk())
                .andReturn();
        long inactiveSubId = objectMapper.readTree(inactiveSubResult.getResponse().getContentAsString())
                .at("/data/id").asLong();
        com.propertyapp.entity.property.PropertySubType subType =
                propertySubTypeRepository.findById(inactiveSubId).orElseThrow();
        subType.setActive(false);
        propertySubTypeRepository.save(subType);

        mockMvc.perform(get("/api/property-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data[?(@.id == %d)].subTypes[?(@.name == '%s')].isActive".formatted(typeId, activeSubName),
                        hasItem(true)))
                .andExpect(jsonPath(
                        "$.data[?(@.id == %d)].subTypes[?(@.name == '%s')].isActive".formatted(typeId, inactiveSubName),
                        hasItem(false)));
    }

    /**
     * Bug 34 — localityId was previously @NotNull, preventing CreateListingScreen (which has
     * no locality picker) from ever submitting. It is now optional:
     *   - When supplied   → locality entity is resolved; city/state/country are overwritten with
     *                       the canonical values from the locality (the old behaviour, preserved).
     *   - When omitted    → free-text address fields from the request body are used; the property
     *                       is created successfully in DRAFT status (the new behaviour).
     * Buyer is still forbidden from creating properties regardless of localityId.
     */
    @Test
    void propertyCreateWithLocalityPersistsCanonicalAddressAndWithoutLocalityUsesFreetextAddress() throws Exception {
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();

        // Buyer forbidden even with a valid localityId
        long countBefore = propertyRepository.count();
        mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propertyPayload(typeId, localityId, "Buyer forbidden")))
                .andExpect(status().isForbidden());
        assertThat(propertyRepository.count()).isEqualTo(countBefore);

        // GET on non-existent property returns 404
        mockMvc.perform(get("/api/properties/{id}", 999_999_999L))
                .andExpect(status().isNotFound());

        // WITH localityId — canonical address overwrites free-text city/state/country
        JsonNode withLocality = createProperty(typeId, localityId, "With locality");
        Property storedWithLocality = propertyRepository.findById(withLocality.at("/data/id").asLong()).orElseThrow();
        assertThat(storedWithLocality.getStatus()).isEqualTo("DRAFT");
        assertThat(storedWithLocality.getLocalityRef().getId()).isEqualTo(localityId);
        assertThat(storedWithLocality.getCity()).isEqualTo(withLocality.at("/data/city").asText());
        // Locality canonical name must replace the "Client supplied city" sent in the body
        assertThat(storedWithLocality.getCity()).isNotEqualTo("Client supplied city");

        // WITHOUT localityId — free-text address is accepted; property created in DRAFT
        MvcResult noLocalityResult = mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propertyPayload(typeId, null, "Without locality")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        Property storedWithoutLocality = propertyRepository.findById(
                objectMapper.readTree(noLocalityResult.getResponse().getContentAsString())
                        .at("/data/id").asLong()).orElseThrow();
        assertThat(storedWithoutLocality.getLocalityRef()).isNull();
        // Free-text city from the payload must be preserved
        assertThat(storedWithoutLocality.getCity()).isEqualTo("Client supplied city");
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

    /**
     * Bug 26 — PUT /api/users/me must persist all editable fields and the updated
     * values must be immediately visible on the subsequent GET /api/users/me call.
     *
     * This is the server-side contract for the frontend's
     * onSubmit → UserService.updateMe() → dispatch(fetchUser()) flow.
     * Validation edge-cases (invalid DOB, phone too long) are covered by the
     * existing profileDateAcceptsIsoDatePersistsMidnightAndRejectsDateTime test.
     */
    @Test
    void editProfilePutPersistsAllFieldsAndGetReflectsChangesImmediately() throws Exception {
        String updateJson = """
                {
                  "firstName": "UpdatedBuyer",
                  "lastName":  "TestLast",
                  "bio":       "Integration test bio",
                  "occupation":"QA Engineer",
                  "gender":    "MALE",
                  "dateOfBirth":"1995-06-15"
                }
                """;

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // dispatch(fetchUser()) performs GET /api/users/me — must return the new values
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("UpdatedBuyer"))
                .andExpect(jsonPath("$.data.lastName").value("TestLast"))
                .andExpect(jsonPath("$.data.bio").value("Integration test bio"))
                .andExpect(jsonPath("$.data.occupation").value("QA Engineer"))
                .andExpect(jsonPath("$.data.gender").value("MALE"));

        // Unauthenticated PUT must be rejected
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isForbidden());
    }

    /**
     * Bug 38 — GET /api/users?role=BUYER must delegate filtering to the database
     * (via UserRepository.findByRole) and return only users that hold the given role.
     * Without a role param the endpoint returns all users (original behaviour, preserved).
     */
    @Test
    void getUsersWithRoleFilterReturnOnlyMatchingRoleUsers() throws Exception {
        // Unauthenticated request must be rejected
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());

        // Without role param — at least admin + buyer + seller + realtor are seeded
        mockMvc.perform(get("/api/users")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(4)));

        // With role=BUYER — every returned user must have BUYER in their roles
        mockMvc.perform(get("/api/users")
                        .param("role", "BUYER")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // At least the seeded buyer exists
                .andExpect(jsonPath("$.data.totalElements").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                // All returned users must carry the BUYER role
                .andExpect(jsonPath("$.data.content[*].roles[*]",
                        hasItem("BUYER")));

        // With role=REALTOR — only realtor is seeded; SUPER_ADMIN or BUYER must NOT appear
        mockMvc.perform(get("/api/users")
                        .param("role", "REALTOR")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.content[*].roles[*]",
                        hasItem("REALTOR")));

        // Non-admin must be forbidden even with role param
        mockMvc.perform(get("/api/users")
                        .param("role", "BUYER")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isForbidden());
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

    @Test
    void postingInquiryIncrementsPropertyInquiryCountVisibleOnMyListings() throws Exception {
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();
        JsonNode property = createProperty(typeId, localityId, "InquiryCount target");
        long propertyId = property.at("/data/id").asLong();

        // Before any inquiry — inquiryCount must be 0
        com.propertyapp.entity.property.Property saved =
                propertyRepository.findById(propertyId).orElseThrow();
        assertThat(saved.getInquiryCount()).isEqualTo(0);

        // Buyer submits an inquiry
        mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"propertyId":%d,"name":"Buyer","email":"buyer@propertyapp.com","message":"Interested!"}
                                """.formatted(propertyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEW"));

        // DB counter incremented
        com.propertyapp.entity.property.Property afterInquiry =
                propertyRepository.findById(propertyId).orElseThrow();
        assertThat(afterInquiry.getInquiryCount()).isEqualTo(1);

        // Seller sees inquiryCount = 1 on GET /api/properties/my-listings
        mockMvc.perform(get("/api/properties/my-listings")
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.content[?(@.id == %d)].inquiryCount".formatted(propertyId),
                        hasItem(1)));
    }

    // ── Net-new Inquiry coverage (found to have zero backend test coverage during Bug 3 investigation) ──

    @Test
    void selfInquiryOnOwnListingIsRejected() throws Exception {
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();
        JsonNode property = createProperty(typeId, localityId, "Self Inquiry Guard Target");
        long propertyId = property.at("/data/id").asLong();

        // The seller who OWNS this listing tries to inquire on it themselves.
        mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"propertyId":%d,"name":"Seller","email":"seller@propertyapp.com","message":"Can I inquire on my own listing?"}
                                """.formatted(propertyId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(inquiryRepository.findSentByInquirerId(
                userRepository.findByEmailAndDeletedAtIsNull("seller@propertyapp.com").orElseThrow().getId(),
                org.springframework.data.domain.PageRequest.of(0, 20)).getTotalElements())
                .isZero();
    }

    // ── Net-new reveal-contact coverage (zero pre-existing tests found; endpoint's role check was
    // widened from BUYER-only to BUYER+REALTOR per product decision on 2026-07-12, and a
    // self-owner guard was added at the same time since it was previously missing entirely) ──

    @Test
    void buyerCanRevealContactOnAnotherOwnersListing() throws Exception {
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();
        JsonNode property = createProperty(typeId, localityId, "Reveal Contact Buyer Target");
        long propertyId = property.at("/data/id").asLong();

        mockMvc.perform(post("/api/properties/{id}/reveal-contact", propertyId)
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyContacted").value(false))
                .andExpect(jsonPath("$.data.email").value("seller@propertyapp.com"));

        assertThat(propertyRepository.findById(propertyId).orElseThrow().getContactCount()).isEqualTo(1);
    }

    @Test
    void realtorCanRevealContactOnAnotherOwnersListing() throws Exception {
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();
        JsonNode property = createProperty(typeId, localityId, "Reveal Contact Realtor Target");
        long propertyId = property.at("/data/id").asLong();

        mockMvc.perform(post("/api/properties/{id}/reveal-contact", propertyId)
                        .header("Authorization", bearer(realtorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadyContacted").value(false))
                .andExpect(jsonPath("$.data.email").value("seller@propertyapp.com"));

        assertThat(propertyRepository.findById(propertyId).orElseThrow().getContactCount()).isEqualTo(1);
    }

    @Test
    void sellerCannotRevealContact() throws Exception {
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();
        JsonNode property = createProperty(typeId, localityId, "Reveal Contact Seller Forbidden Target");
        long propertyId = property.at("/data/id").asLong();

        mockMvc.perform(post("/api/properties/{id}/reveal-contact", propertyId)
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void realtorRevealingContactOnTheirOwnListingIsRejected() throws Exception {
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();
        MvcResult createResult = mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer(realtorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propertyPayload(typeId, localityId, "Realtor Self Reveal Guard Target")))
                .andExpect(status().isCreated())
                .andReturn();
        long propertyId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/id").asLong();

        mockMvc.perform(post("/api/properties/{id}/reveal-contact", propertyId)
                        .header("Authorization", bearer(realtorToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(propertyRepository.findById(propertyId).orElseThrow().getContactCount()).isEqualTo(0);
    }

    @Test
    void inquiryLifecycleNotifiesOwnerTransitionsStatusAndNotifiesBuyer() throws Exception {
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();
        JsonNode property = createProperty(typeId, localityId, "Inquiry Lifecycle Target");
        long propertyId = property.at("/data/id").asLong();
        long sellerId = userRepository.findByEmailAndDeletedAtIsNull("seller@propertyapp.com").orElseThrow().getId();
        long buyerId = userRepository.findByEmailAndDeletedAtIsNull("buyer@propertyapp.com").orElseThrow().getId();

        MvcResult createResult = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"propertyId":%d,"name":"Buyer","email":"buyer@propertyapp.com","message":"Is this still available?"}
                                """.formatted(propertyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andReturn();
        long inquiryId = objectMapper.readTree(createResult.getResponse().getContentAsString()).at("/data/id").asLong();

        // Owner was notified of the new inquiry.
        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(
                        sellerId, org.springframework.data.domain.Pageable.unpaged()).getContent())
                .anyMatch(n -> n.getType() == com.propertyapp.enums.NotificationType.INQUIRY_RECEIVED
                        && propertyId == n.getEntityId());

        // Owner sees it under /received.
        mockMvc.perform(get("/api/inquiries/received")
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == %d)]".formatted(inquiryId)).exists());

        // Buyer sees it under /sent.
        mockMvc.perform(get("/api/inquiries/sent")
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == %d)]".formatted(inquiryId)).exists());

        // Owner transitions NEW -> CONTACTED.
        mockMvc.perform(patch("/api/inquiries/{id}/status", inquiryId)
                        .header("Authorization", bearer(sellerToken))
                        .param("status", "CONTACTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONTACTED"));

        // Buyer was notified of the status change.
        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(
                        buyerId, org.springframework.data.domain.Pageable.unpaged()).getContent())
                .anyMatch(n -> n.getType() == com.propertyapp.enums.NotificationType.CONTACT_REVEALED
                        && propertyId == n.getEntityId());
    }

    @Test
    void invalidInquiryStatusTransitionIsRejected() throws Exception {
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();
        JsonNode property = createProperty(typeId, localityId, "Invalid Transition Target");
        long propertyId = property.at("/data/id").asLong();

        MvcResult createResult = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"propertyId":%d,"name":"Buyer","email":"buyer@propertyapp.com","message":"Interested"}
                                """.formatted(propertyId)))
                .andExpect(status().isOk())
                .andReturn();
        long inquiryId = objectMapper.readTree(createResult.getResponse().getContentAsString()).at("/data/id").asLong();

        // NEW -> CLOSED directly (skipping CONTACTED) is not an allowed transition.
        mockMvc.perform(patch("/api/inquiries/{id}/status", inquiryId)
                        .header("Authorization", bearer(sellerToken))
                        .param("status", "CLOSED"))
                .andExpect(status().isBadRequest());

        assertThat(inquiryRepository.findById(inquiryId).orElseThrow().getStatus())
                .isEqualTo(InquiryStatus.NEW);
    }

    @Test
    void nonOwnerCannotUpdateInquiryStatusOnSomeoneElsesProperty() throws Exception {
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();
        JsonNode property = createProperty(typeId, localityId, "Ownership Guard Target");
        long propertyId = property.at("/data/id").asLong();

        MvcResult createResult = mockMvc.perform(post("/api/inquiries")
                        .header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"propertyId":%d,"name":"Buyer","email":"buyer@propertyapp.com","message":"Interested"}
                                """.formatted(propertyId)))
                .andExpect(status().isOk())
                .andReturn();
        long inquiryId = objectMapper.readTree(createResult.getResponse().getContentAsString()).at("/data/id").asLong();

        // realtorToken does not own this property (sellerToken does) — must be rejected.
        mockMvc.perform(patch("/api/inquiries/{id}/status", inquiryId)
                        .header("Authorization", bearer(realtorToken))
                        .param("status", "CONTACTED"))
                .andExpect(status().isUnauthorized());

        assertThat(inquiryRepository.findById(inquiryId).orElseThrow().getStatus())
                .isEqualTo(InquiryStatus.NEW);
    }

    @Test
    void receivedInquiriesAreScopedToEachOwnersOwnProperties() throws Exception {
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();

        JsonNode sellerProperty = createProperty(typeId, localityId, "Scoping Target Seller");
        long sellerPropertyId = sellerProperty.at("/data/id").asLong();

        MvcResult realtorPropertyResult = mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer(realtorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propertyPayloadWithExactTitle(typeId, localityId, "Scoping Target Realtor " + UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        long realtorPropertyId = objectMapper.readTree(realtorPropertyResult.getResponse().getContentAsString())
                .at("/data/id").asLong();

        mockMvc.perform(post("/api/inquiries")
                .header("Authorization", bearer(buyerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"propertyId":%d,"name":"Buyer","email":"buyer@propertyapp.com","message":"On seller's listing"}
                        """.formatted(sellerPropertyId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/inquiries")
                .header("Authorization", bearer(buyerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"propertyId":%d,"name":"Buyer","email":"buyer@propertyapp.com","message":"On realtor's listing"}
                        """.formatted(realtorPropertyId)))
                .andExpect(status().isOk());

        // Seller's /received only shows the inquiry on their own property.
        mockMvc.perform(get("/api/inquiries/received")
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].propertyId", hasItem(Long.valueOf(sellerPropertyId).intValue())))
                .andExpect(jsonPath("$.data.content[*].propertyId", not(hasItem(Long.valueOf(realtorPropertyId).intValue()))));

        // Realtor's /received only shows the inquiry on their own property.
        mockMvc.perform(get("/api/inquiries/received")
                        .header("Authorization", bearer(realtorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].propertyId", hasItem(Long.valueOf(realtorPropertyId).intValue())))
                .andExpect(jsonPath("$.data.content[*].propertyId", not(hasItem(Long.valueOf(sellerPropertyId).intValue()))));
    }

    @Test
    void duplicatePropertySubmissionWithinShortWindowIsRejectedNotDuplicated() throws Exception {
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();
        String fixedTitle = "Bug 9 Duplicate Guard Target " + UUID.randomUUID();

        var payload = objectMapper.createObjectNode();
        payload.put("title", fixedTitle);
        payload.put("description", "Simulates a double-tap create-listing submission");
        payload.put("propertyTypeId", typeId);
        payload.put("listingType", "SALE");
        payload.put("price", 250000);
        payload.put("addressLine1", "1 Test Street");
        payload.put("city", "Client supplied city");
        payload.put("state", "Client supplied state");
        payload.put("country", "Client supplied country");
        payload.put("localityId", localityId);
        String body = objectMapper.writeValueAsString(payload);

        // First submission succeeds.
        mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // A second, near-simultaneous submission with the identical title from the same
        // owner is rejected as a duplicate rather than creating a second row.
        mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        long matchingRows = propertyRepository.findAll().stream()
                .filter(p -> fixedTitle.equals(p.getTitle()))
                .count();
        assertThat(matchingRows).isEqualTo(1);
    }

    @Test
    void differentOwnersCanUseTheSameTitleWithoutTrippingTheDuplicateGuard() throws Exception {
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();
        String sharedTitle = "Shared Title Across Owners " + UUID.randomUUID();

        mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propertyPayloadWithExactTitle(typeId, localityId, sharedTitle)))
                .andExpect(status().isCreated());

        // A different owner using the exact same title is a legitimate, independent listing —
        // the duplicate guard is scoped per-owner and must not block this.
        mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer(realtorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propertyPayloadWithExactTitle(typeId, localityId, sharedTitle)))
                .andExpect(status().isCreated());
    }

    /**
     * Bug E — same MapStruct silent-mapping-gap pattern as Bug 12: PropertyMapper.toImageDTO had
     * no explicit @Mapping for isPrimary (entity getter isPrimary() resolves to JavaBean property
     * "primary", not "isPrimary"), so GET /api/properties/{id}/images — the LIST endpoint used by
     * PropertyImagesScreen — always returned isPrimary:false regardless of the true DB value. This
     * test hits that LIST endpoint directly, which is exactly where the bug hid.
     */
    @Test
    void propertyImagesListReflectsPrimaryFlagCorrectly() throws Exception {
        long typeId = propertyTypeRepository.findAll().getFirst().getId();
        long localityId = localityRepository.findAll().getFirst().getId();
        JsonNode property = createProperty(typeId, localityId, "Primary Image Target");
        long propertyId = property.at("/data/id").asLong();

        MvcResult primaryResult = mockMvc.perform(post("/api/properties/{id}/images", propertyId)
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"imageUrl":"http://example.com/primary.jpg","isPrimary":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long primaryImageId = objectMapper.readTree(primaryResult.getResponse().getContentAsString())
                .at("/data/id").asLong();

        mockMvc.perform(post("/api/properties/{id}/images", propertyId)
                        .header("Authorization", bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"imageUrl":"http://example.com/secondary.jpg","isPrimary":false}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/properties/{id}/images", propertyId)
                        .header("Authorization", bearer(sellerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)].isPrimary".formatted(primaryImageId), hasItem(true)))
                .andExpect(jsonPath("$.data[?(@.id != %d)].isPrimary".formatted(primaryImageId), hasItem(false)));
    }

    private String propertyPayloadWithExactTitle(long typeId, long localityId, String exactTitle) throws Exception {
        var payload = objectMapper.createObjectNode();
        payload.put("title", exactTitle);
        payload.put("description", "A property created by the reported bug integration suite");
        payload.put("propertyTypeId", typeId);
        payload.put("listingType", "SALE");
        payload.put("price", 250000);
        payload.put("addressLine1", "1 Test Street");
        payload.put("city", "Client supplied city");
        payload.put("state", "Client supplied state");
        payload.put("country", "Client supplied country");
        payload.put("localityId", localityId);
        return objectMapper.writeValueAsString(payload);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
