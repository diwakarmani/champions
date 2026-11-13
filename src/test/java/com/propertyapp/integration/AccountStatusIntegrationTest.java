package com.propertyapp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.entity.user.User;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import com.propertyapp.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S12 review: what deactivation and an unconfirmed email really do to sign-in and to tokens. */
class AccountStatusIntegrationTest extends AbstractPostgisIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired PropertyTypeRepository propertyTypeRepository;
    @Autowired JdbcTemplate jdbc;

    Fixtures f;
    User admin;
    String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixtures(mockMvc, objectMapper, userRepository, propertyRepository, propertyTypeRepository, jdbc);
        admin = f.user("SUPER_ADMIN");
        adminToken = f.login(admin.getEmail());
    }

    private String loginBody(String identifier, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("identifier", identifier, "password", password));
    }

    @Test
    void aRefreshTokenCannotAuthoriseARequest_andAnAccessTokenCannotRenewASession() throws Exception {
        // S20 security pass: both kinds were the same JWT, so the 90-day mobile refresh token was also
        // a 90-day bearer token.
        User buyer = f.user("BUYER");
        Map<String, String> tokens = f.tokens(buyer.getEmail());
        mockMvc.perform(Fixtures.authed(get("/api/users/me"), tokens.get("refreshToken")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh-token").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", tokens.get("accessToken")))))
                .andExpect(status().isUnauthorized());

        // The right kind still works both ways, and the renewed access token is an access token.
        String renewed = objectMapper.readTree(mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", tokens.get("refreshToken")))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).at("/data/accessToken").asText();
        mockMvc.perform(Fixtures.authed(get("/api/users/me"), renewed)).andExpect(status().isOk());
        mockMvc.perform(Fixtures.authed(get("/api/users/me"), tokens.get("accessToken"))).andExpect(status().isOk());
    }

    @Test
    void deactivatingAnAccountStopsItsTokensAndItsSignIn() throws Exception {
        User buyer = f.user("BUYER");
        Map<String, String> tokens = f.tokens(buyer.getEmail());
        mockMvc.perform(Fixtures.authed(get("/api/users/me"), tokens.get("accessToken"))).andExpect(status().isOk());

        mockMvc.perform(Fixtures.authed(post("/api/users/" + buyer.getId() + "/deactivate"), adminToken))
                .andExpect(status().isOk());

        // The token it already had stops working at once.
        mockMvc.perform(Fixtures.authed(get("/api/users/me"), tokens.get("accessToken")))
                .andExpect(status().isUnauthorized());
        // It cannot renew the session.
        mockMvc.perform(post("/api/auth/refresh-token").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", tokens.get("refreshToken")))))
                .andExpect(status().isUnauthorized());
        // The right password is told why; a wrong one gets the usual answer.
        mockMvc.perform(post("/api/auth/login-with-identifier").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(buyer.getEmail(), Fixtures.PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Account is inactive. Please contact support."));
        mockMvc.perform(post("/api/auth/login-with-identifier").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(buyer.getEmail(), "Wrong@Passw0rd")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));

        // Reactivated: signs in again, and reactivation did not confirm anything on its own.
        mockMvc.perform(Fixtures.authed(post("/api/users/" + buyer.getId() + "/activate"), adminToken))
                .andExpect(status().isOk());
        f.login(buyer.getEmail());
    }

    @Test
    void anUnconfirmedEmailIsMentionedOnlyAfterTheRightPassword() throws Exception {
        User buyer = f.user("BUYER");
        jdbc.update("UPDATE users SET email_verified = false WHERE id = ?", buyer.getId());

        mockMvc.perform(post("/api/auth/login-with-identifier").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(buyer.getEmail(), "Wrong@Passw0rd")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
        mockMvc.perform(post("/api/auth/login-with-identifier").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(buyer.getEmail(), Fixtures.PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Account not verified. Please verify your email."));
    }

    @Test
    void activatingDoesNotConfirmTheEmail() throws Exception {
        User buyer = f.user("BUYER");
        jdbc.update("UPDATE users SET email_verified = false, is_active = false WHERE id = ?", buyer.getId());
        mockMvc.perform(Fixtures.authed(post("/api/users/" + buyer.getId() + "/activate"), adminToken))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE id = ?", Boolean.class, buyer.getId()))
                .isFalse();
    }

    @Test
    void anAdminCannotDeactivateThemselves() throws Exception {
        mockMvc.perform(Fixtures.authed(post("/api/users/" + admin.getId() + "/deactivate"), adminToken))
                .andExpect(status().isConflict());
        mockMvc.perform(Fixtures.authed(get("/api/users/me"), adminToken)).andExpect(status().isOk());
    }

    @Test
    void askingForACodeAnswersTheSameWhetherOrNotTheAccountExists() throws Exception {
        User buyer = f.user("BUYER");
        String unknown = "nobody-" + java.util.UUID.randomUUID() + "@example.com";
        String known = mockMvc.perform(Fixtures.from(post("/api/auth/otp/send"), Fixtures.freshIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", buyer.getEmail()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(Fixtures.from(post("/api/auth/otp/send"), Fixtures.freshIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", unknown))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(missing).path("data").path("message"))
                .isEqualTo(objectMapper.readTree(known).path("data").path("message"));
        assertThat(missing).doesNotContain("No account");
        // Nothing was issued for the unknown address.
        assertThat(f.count("SELECT count(*) FROM otp_tokens WHERE identifier = ?", unknown)).isZero();
        assertThat(f.count("SELECT count(*) FROM otp_tokens WHERE identifier = ?", buyer.getEmail())).isEqualTo(1);
    }

    @Test
    void onlyAnAdminCanDeactivate() throws Exception {
        User buyer = f.user("BUYER");
        User other = f.user("BUYER");
        mockMvc.perform(Fixtures.authed(post("/api/users/" + other.getId() + "/deactivate"), f.login(buyer.getEmail())))
                .andExpect(status().isForbidden());
    }
}
