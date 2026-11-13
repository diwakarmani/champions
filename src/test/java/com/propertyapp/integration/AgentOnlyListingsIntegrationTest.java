package com.propertyapp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.user.User;
import com.propertyapp.repository.property.ListingReassignmentRepository;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.util.PasswordUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Client rule: listings are created and managed through approved agents only (ADR-0003, ADR-0005). */
class AgentOnlyListingsIntegrationTest extends AbstractPostgisIntegrationTest {

    private static final String PASSWORD = "Str0ng@Passw0rd";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired PropertyTypeRepository propertyTypeRepository;
    @Autowired ListingReassignmentRepository reassignmentRepository;
    @Autowired JdbcTemplate jdbc;

    String adminToken;
    String buyerToken;
    String realtorToken;
    User realtor;
    User realtorTwo;
    User buyer;
    User legacySeller;
    String legacySellerToken;
    String city;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin@propertyapp.com", "Admin@123");
        buyerToken = login("buyer@propertyapp.com", "Demo@123");
        realtorToken = login("realtor@propertyapp.com", "Demo@123");
        realtor = userRepository.findByEmailAndDeletedAtIsNull("realtor@propertyapp.com").orElseThrow();
        realtorTwo = userRepository.findByEmailAndDeletedAtIsNull("realtor2@propertyapp.com").orElseThrow();
        buyer = userRepository.findByEmailAndDeletedAtIsNull("buyer@propertyapp.com").orElseThrow();
        legacySeller = user("SELLER");
        legacySellerToken = login(legacySeller.getEmail(), PASSWORD);
        city = "City" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ── Who can create ───────────────────────────────────────────────────────

    @Test
    void onlyAgentsAndAdminsCanCreateListings() throws Exception {
        mockMvc.perform(create(legacySellerToken, null)).andExpect(status().isForbidden());
        mockMvc.perform(create(buyerToken, null)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/properties").contentType(MediaType.APPLICATION_JSON).content(payload(null)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(create(realtorToken, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ownerId").value(realtor.getId()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void anAdminMustNameAnActiveAgentForANewListing() throws Exception {
        mockMvc.perform(create(adminToken, null)).andExpect(status().isBadRequest());
        mockMvc.perform(create(adminToken, buyer.getId())).andExpect(status().isBadRequest());
        mockMvc.perform(create(adminToken, realtorTwo.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ownerId").value(realtorTwo.getId()));
    }

    @Test
    void anAgentCannotCreateAListingInAnotherAgentsName() throws Exception {
        org.springframework.test.web.servlet.ResultActions result =
                mockMvc.perform(create(realtorToken, realtorTwo.getId()));
        assertThat(result.andReturn().getResponse().getStatus()).isIn(401, 403);
    }

    @Test
    void theSellerRoleCanNoLongerBeAssignedButAgentCan() throws Exception {
        User target = user("BUYER");
        mockMvc.perform(roles(target, "SELLER")).andExpect(status().isBadRequest());
        mockMvc.perform(roles(target, "BUYER", "REALTOR")).andExpect(status().isOk());
    }

    // ── Who can manage ───────────────────────────────────────────────────────

    @Test
    void aFormerSellerCannotManageTheirOldListings() throws Exception {
        long id = listing(legacySeller, "DRAFT");

        expectDenied(mockMvc.perform(put("/api/properties/{id}", id).header("Authorization", bearer(legacySellerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Changed\"}")));
        expectDenied(mockMvc.perform(patch("/api/properties/{id}/publish", id)
                .header("Authorization", bearer(legacySellerToken))));
        expectDenied(mockMvc.perform(post("/api/properties/{id}/request-deletion", id)
                .header("Authorization", bearer(legacySellerToken))));
        expectDenied(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/properties/{id}", id).header("Authorization", bearer(legacySellerToken))));
        expectDenied(mockMvc.perform(multipart("/api/properties/{id}/images", id)
                .header("Authorization", bearer(legacySellerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"imageUrl\":\"https://img.example/y.jpg\"}")
                .with(r -> { r.setMethod("POST"); return r; })));
    }

    @Test
    void aListingCannotGoLiveWithoutAnActiveAgent() throws Exception {
        long pending = listing(legacySeller, "PENDING_APPROVAL");
        mockMvc.perform(post("/api/admin/properties/{id}/approve", pending).header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());

        long draft = listing(legacySeller, "DRAFT");
        mockMvc.perform(patch("/api/properties/{id}/status", draft).param("status", "ACTIVE")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());

        assertThat(propertyRepository.findById(pending).orElseThrow().getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(propertyRepository.findById(draft).orElseThrow().getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void publishGoesToApprovalByDefault() throws Exception {
        long id = listing(realtor, "DRAFT");
        mockMvc.perform(patch("/api/properties/{id}/publish", id).header("Authorization", bearer(realtorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));
    }

    // ── Who the public sees ──────────────────────────────────────────────────

    @Test
    void everyPublicSurfaceHidesListingsWithoutAnActiveAgent() throws Exception {
        long agentListing = listing(realtor, "ACTIVE");
        long sellerListing = listing(legacySeller, "ACTIVE");
        User suspendedStyleAgent = user("REALTOR");
        long inactiveAgentListing = listing(suspendedStyleAgent, "ACTIVE");
        jdbc.update("UPDATE users SET is_active = false WHERE id = ?", suspendedStyleAgent.getId());

        for (long listing : new long[] {agentListing, sellerListing, inactiveAgentListing}) {
            jdbc.update("INSERT INTO discovery_cache (user_id, category, city, property_id, score, rank) "
                    + "VALUES (0, 'POPULAR', ?, ?, 1, 1)", city, listing);
        }

        mockMvc.perform(get("/api/properties/{id}", agentListing)).andExpect(status().isOk());
        mockMvc.perform(get("/api/properties/{id}", sellerListing)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/properties/{id}", inactiveAgentListing)).andExpect(status().isNotFound());

        assertThat(ids(get("/api/properties").param("city", city), "/data/content"))
                .contains(agentListing).doesNotContain(sellerListing, inactiveAgentListing);
        assertThat(ids(get("/api/properties/featured").param("limit", "100"), "/data"))
                .contains(agentListing).doesNotContain(sellerListing, inactiveAgentListing);
        assertThat(ids(get("/api/discovery/home/view-more").param("category", "POPULAR").param("city", city)
                        .param("size", "100"), "/data/content"))
                .contains(agentListing).doesNotContain(sellerListing, inactiveAgentListing);
        assertThat(ids(get("/api/discovery/home/view-more").param("category", "NEAREST").param("city", city)
                        .param("lat", "30.26").param("lng", "-97.74").param("size", "100"), "/data/content"))
                .doesNotContain(sellerListing, inactiveAgentListing);
        String home = mockMvc.perform(get("/api/discovery/home").param("city", city)
                        .param("lat", "30.26").param("lng", "-97.74"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(allIds(objectMapper.readTree(home))).contains(agentListing)
                .doesNotContain(sellerListing, inactiveAgentListing);

        mockMvc.perform(post("/api/properties/{id}/reveal-contact", sellerListing)
                        .header("Authorization", bearer(buyerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theFormerOwnerAndAdminCanStillOpenAHiddenListing() throws Exception {
        long id = listing(legacySeller, "ACTIVE");
        mockMvc.perform(get("/api/properties/{id}", id).header("Authorization", bearer(legacySellerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/properties/{id}", id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    // ── Reassignment ─────────────────────────────────────────────────────────

    @Test
    void anAdminCanReassignAListing_whichBringsItBackAndRecordsWhoDidIt() throws Exception {
        long id = listing(legacySeller, "ACTIVE");
        mockMvc.perform(get("/api/properties/{id}", id)).andExpect(status().isNotFound());

        mockMvc.perform(reassign(adminToken, id, realtorTwo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").value(realtorTwo.getId()));

        mockMvc.perform(get("/api/properties/{id}", id)).andExpect(status().isOk());

        var audit = reassignmentRepository.findByPropertyIdOrderByCreatedAtDesc(id);
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).getFromUserId()).isEqualTo(legacySeller.getId());
        assertThat(audit.get(0).getToUserId()).isEqualTo(realtorTwo.getId());
        assertThat(audit.get(0).getReassignedBy())
                .isEqualTo(userRepository.findByEmailAndDeletedAtIsNull("admin@propertyapp.com").orElseThrow().getId());
        assertThat(audit.get(0).getCreatedAt()).isNotNull();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE recipient_id = ? AND type = 'LISTING_REASSIGNED' AND entity_id = ?",
                Long.class, realtorTwo.getId(), id)).isEqualTo(1L);
    }

    @Test
    void reassignmentMovesExistingEnquiriesToTheNewAgent() throws Exception {
        long id = listing(realtor, "ACTIVE");
        mockMvc.perform(post("/api/inquiries").header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("propertyId", id, "name", "Buyer",
                                "email", "buyer@propertyapp.com", "message", "Still available?"))))
                .andExpect(status().isOk());

        mockMvc.perform(reassign(adminToken, id, realtorTwo.getId())).andExpect(status().isOk());

        String received = mockMvc.perform(get("/api/inquiries/received").param("size", "100")
                        .header("Authorization", bearer(login("realtor2@propertyapp.com", "Demo@123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(received).at("/data/content").findValues("propertyId").stream()
                .map(JsonNode::asLong).toList()).contains(id);
    }

    @Test
    void reassignmentIsAdminOnlyAndOnlyToAnActiveAgent() throws Exception {
        long id = listing(realtor, "ACTIVE");
        mockMvc.perform(reassign(realtorToken, id, realtorTwo.getId())).andExpect(status().isForbidden());
        mockMvc.perform(reassign(buyerToken, id, realtorTwo.getId())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/properties/{id}/reassign", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":" + realtorTwo.getId() + "}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(reassign(adminToken, id, buyer.getId())).andExpect(status().isBadRequest());
        mockMvc.perform(reassign(adminToken, id, legacySeller.getId())).andExpect(status().isBadRequest());
        mockMvc.perform(reassign(adminToken, id, realtor.getId())).andExpect(status().isBadRequest());
        mockMvc.perform(reassign(adminToken, id, 999_999_999L)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/properties/{id}/reassign", id).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(propertyRepository.findById(id).orElseThrow().getOwner().getId()).isEqualTo(realtor.getId());
        assertThat(reassignmentRepository.findByPropertyIdOrderByCreatedAtDesc(id)).isEmpty();
    }

    // ── View counts and seed data ────────────────────────────────────────────

    @Test
    void viewsAreCountedOnEveryOpenAndAViewIsNotAnEdit() throws Exception {
        long id = listing(realtor, "ACTIVE");
        var before = jdbc.queryForObject("SELECT updated_at FROM properties WHERE id = ?", java.sql.Timestamp.class, id);

        mockMvc.perform(get("/api/properties/{id}", id)).andExpect(status().isOk());
        mockMvc.perform(get("/api/properties/{id}", id)).andExpect(status().isOk());
        mockMvc.perform(get("/api/properties/{id}", id)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT view_count FROM properties WHERE id = ?", Integer.class, id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM properties WHERE id = ?", java.sql.Timestamp.class, id))
                .isEqualTo(before);
    }

    @Test
    void seededPropertyTypesUseUsTermsOnly() {
        assertThat(jdbc.queryForList("SELECT name FROM property_types", String.class))
                .contains("Single Family Home", "Condo", "Townhouse", "Apartment", "Land", "Commercial")
                .doesNotContain("Villa", "Independent House", "Plot");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM property_sub_types WHERE name ~* '(BHK|\\mRK\\M|godown)'", Long.class)).isZero();
    }

    @Test
    void demoSellerIsNoLongerSeeded() {
        assertThat(userRepository.findByEmailAndDeletedAtIsNull("seller@propertyapp.com")).isEmpty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void expectDenied(org.springframework.test.web.servlet.ResultActions result) {
        assertThat(result.andReturn().getResponse().getStatus()).isIn(401, 403);
    }

    private List<Long> ids(org.springframework.test.web.servlet.RequestBuilder request, String pointer) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Long> ids = new java.util.ArrayList<>();
        objectMapper.readTree(body).at(pointer).forEach(card -> ids.add(card.get("id").asLong()));
        return ids;
    }

    private List<Long> allIds(JsonNode node) {
        return node.findValues("id").stream().map(JsonNode::asLong).toList();
    }

    private org.springframework.test.web.servlet.RequestBuilder create(String token, Long agentId) throws Exception {
        return post("/api/properties").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(payload(agentId));
    }

    private org.springframework.test.web.servlet.RequestBuilder reassign(String token, long id, long agentId) {
        return post("/api/admin/properties/{id}/reassign", id).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"agentId\":" + agentId + "}");
    }

    private org.springframework.test.web.servlet.RequestBuilder roles(User target, String... names) throws Exception {
        return post("/api/users/{id}/roles", target.getId()).header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(names));
    }

    private String payload(Long agentId) throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("title", "Listing " + UUID.randomUUID());
        node.put("description", "A pleasant home close to everything you need.");
        node.put("propertyTypeId", propertyTypeRepository.findAll().get(0).getId());
        node.put("listingType", "SALE");
        node.put("price", 450000);
        node.put("addressLine1", "1 Test St");
        node.put("city", "Austin");
        node.put("state", "Texas");
        node.put("country", "USA");
        node.put("pincode", "78701");
        node.put("bedrooms", 3);
        node.put("bathrooms", 2);
        if (agentId != null) {
            node.put("agentId", agentId);
        }
        return objectMapper.writeValueAsString(node);
    }

    private long listing(User owner, String status) {
        return propertyRepository.saveAndFlush(Property.builder()
                .title("Listing " + UUID.randomUUID())
                .owner(owner)
                .propertyType(propertyTypeRepository.findAll().get(0))
                .listingType("SALE")
                .price(BigDecimal.valueOf(500_000))
                .addressLine1("1 Test St")
                .city(city)
                .state("Texas")
                .country("USA")
                .status(status)
                .isFeatured(true)
                .viewCount(0)
                .publishedAt(java.time.LocalDateTime.now())
                .build()).getId();
    }

    private User user(String roleName) {
        User created = userRepository.saveAndFlush(User.builder()
                .email(roleName.toLowerCase() + "-" + UUID.randomUUID() + "@example.com")
                .passwordHash(PasswordUtils.hash(PASSWORD))
                .firstName("Test")
                .lastName(roleName)
                .emailVerified(true)
                .mobileVerified(false)
                .isActive(true)
                .isLocked(false)
                .build());
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = ?",
                created.getId(), roleName);
        return created;
    }

    private String login(String identifier, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login-with-identifier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", identifier, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
