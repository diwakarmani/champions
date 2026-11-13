package com.propertyapp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.property.PropertyType;
import com.propertyapp.entity.user.User;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import com.propertyapp.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** P0 access rules (ADR-0006). Each test here must fail if its guard is removed. */
class AccessControlIntegrationTest extends AbstractPostgisIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired PropertyTypeRepository propertyTypeRepository;

    String adminToken;
    String buyerToken;
    String realtorToken;
    User buyer;
    User realtor;

    @BeforeEach
    void logIn() throws Exception {
        adminToken = login("admin@propertyapp.com", "Admin@123");
        buyerToken = login("buyer@propertyapp.com", "Demo@123");
        realtorToken = login("realtor@propertyapp.com", "Demo@123");
        buyer = userRepository.findByEmailAndDeletedAtIsNull("buyer@propertyapp.com").orElseThrow();
        realtor = userRepository.findByEmailAndDeletedAtIsNull("realtor@propertyapp.com").orElseThrow();
    }

    @Test
    void publicSignUpCanNeverGrantARole() throws Exception {
        for (String requested : new String[] {"SUPER_ADMIN", "REALTOR"}) {
            String email = "signup-" + requested.toLowerCase() + "@example.com";
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "email", email,
                                    "password", "Str0ng@Passw0rd",
                                    "firstName", "New",
                                    "lastName", "User",
                                    "phone", "+1512555" + (requested.equals("REALTOR") ? "0101" : "0102"),
                                    "roles", java.util.List.of(requested)))))
                    .andExpect(status().is2xxSuccessful());

            org.assertj.core.api.Assertions.assertThat(jdbc.queryForList(
                            "SELECT r.name FROM roles r JOIN user_roles ur ON ur.role_id = r.id "
                                    + "JOIN users u ON u.id = ur.user_id WHERE u.email = ?", String.class, email))
                    .containsExactly("BUYER");
        }
    }

    @Test
    void userDetailsAreVisibleOnlyToThatUserOrAnAdmin_evenWhenAlreadyCached() throws Exception {
        // Admin read first, so the record is in the cache when the buyer tries.
        mockMvc.perform(get("/api/users/{id}", realtor.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/{id}", realtor.getId()).header("Authorization", bearer(buyerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/{id}", buyer.getId()).header("Authorization", bearer(buyerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/{id}", buyer.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersCanUpdateThemselvesByIdButNotOthers() throws Exception {
        String body = "{\"firstName\":\"Demo\"}";
        mockMvc.perform(put("/api/users/{id}", buyer.getId()).header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/users/{id}", realtor.getId()).header("Authorization", bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void hiddenListingsAre404ForEveryoneExceptOwnerAndAdmin_evenAfterOwnerPrimesTheCache() throws Exception {
        for (String hidden : new String[] {"DRAFT", "PENDING_APPROVAL", "REJECTED", "INACTIVE", "DELETION_REQUESTED"}) {
            long id = listing(hidden);

            mockMvc.perform(get("/api/properties/{id}", id).header("Authorization", bearer(realtorToken)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/properties/{id}", id).header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/properties/{id}", id)).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/properties/{id}", id).header("Authorization", bearer(buyerToken)))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/properties/{id}/images", id)).andExpect(status().isNotFound());
        }
    }

    @Test
    void activeSoldAndRentedListingsArePublic() throws Exception {
        for (String open : new String[] {"ACTIVE", "SOLD", "RENTED"}) {
            mockMvc.perform(get("/api/properties/{id}", listing(open))).andExpect(status().isOk());
        }
    }

    @Test
    void aListingThatLeavesActiveDisappearsFromThePublicAtOnce() throws Exception {
        long id = listing("ACTIVE");
        mockMvc.perform(get("/api/properties/{id}", id)).andExpect(status().isOk()); // now cached

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/properties/{id}/status", id).param("status", "INACTIVE")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/properties/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void onlyActiveListingsAcceptEnquiriesOrContactReveal() throws Exception {
        mockMvc.perform(inquiry(listing("DRAFT"))).andExpect(status().isNotFound());
        mockMvc.perform(inquiry(listing("SOLD"))).andExpect(status().isBadRequest());
        mockMvc.perform(inquiry(listing("ACTIVE"))).andExpect(status().isOk());

        mockMvc.perform(reveal(listing("PENDING_APPROVAL"))).andExpect(status().isNotFound());
        mockMvc.perform(reveal(listing("RENTED"))).andExpect(status().isBadRequest());
        mockMvc.perform(reveal(listing("ACTIVE"))).andExpect(status().isOk());
    }

    @Test
    void privateListingRoutesRequireLoginEvenThoughOtherPropertyReadsArePublic() throws Exception {
        mockMvc.perform(get("/api/properties/my-listings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/properties/status/DRAFT")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/properties/status/DRAFT").header("Authorization", bearer(buyerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicSearchOnlySortsByAllowedFields() throws Exception {
        mockMvc.perform(get("/api/properties").param("sortBy", "owner.passwordHash"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/properties").param("sortBy", "price").param("sortDirection", "ASC"))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.RequestBuilder inquiry(long propertyId) throws Exception {
        return post("/api/inquiries").header("Authorization", bearer(buyerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "propertyId", propertyId, "name", "Demo Buyer",
                        "email", "buyer@propertyapp.com", "message", "Is this still available?")));
    }

    private org.springframework.test.web.servlet.RequestBuilder reveal(long propertyId) {
        return post("/api/properties/{id}/reveal-contact", propertyId).header("Authorization", bearer(buyerToken));
    }

    private long listing(String status) {
        PropertyType type = propertyTypeRepository.findAll().get(0);
        Property property = propertyRepository.saveAndFlush(Property.builder()
                .title("Access test " + status)
                .owner(realtor)
                .propertyType(type)
                .listingType("SALE")
                .price(BigDecimal.valueOf(500_000))
                .addressLine1("1 Test St")
                .city("Austin")
                .state("Texas")
                .country("USA")
                .status(status)
                .build());
        return property.getId();
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
