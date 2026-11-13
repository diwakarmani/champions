package com.propertyapp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.entity.user.User;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import com.propertyapp.repository.user.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.propertyapp.integration.Fixtures.PASSWORD;
import static com.propertyapp.integration.Fixtures.authed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every POST anyone may call, sent with privileges a client must never be able to grant itself
 * (S4: the sign-up hole of S2 was a client-supplied role). One test per route. The two guest
 * contact routes are covered in GuestContactIntegrationTest
 * (theBodyCannotChooseTheListingStatusAccountOrSource, aGuestCanRevealTheAgentsContactAndItIsRecorded).
 */
class PublicPostPrivilegeAuditTest extends AbstractPostgisIntegrationTest {

    /** Field names a client might try, in the spellings our DTOs and entities use. */
    private static final Map<String, Object> PRIVILEGES = Map.of(
            "roles", List.of("SUPER_ADMIN", "REALTOR"),
            "role", "SUPER_ADMIN",
            "isActive", true,
            "emailVerified", true,
            "isVerified", true,
            "agentSuspended", false,
            "id", 1,
            "userId", 1);

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired PropertyTypeRepository propertyTypeRepository;
    @Autowired JdbcTemplate jdbc;
    @MockBean JavaMailSender mailSender;

    Fixtures f;
    User buyer;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(call -> new MimeMessage((Session) null));
        f = new Fixtures(mockMvc, objectMapper, userRepository, propertyRepository, propertyTypeRepository, jdbc);
        buyer = f.user("BUYER");
    }

    @Test
    void register() throws Exception {
        String email = "audit-" + UUID.randomUUID() + "@example.com";
        postJson("/api/auth/register", with(Map.of("email", email, "password", PASSWORD,
                "firstName", "Audit", "lastName", "Person", "phone", "+1206" + (1_000_000 + (int) (Math.random() * 8_999_999))))).andExpect(status().isCreated());
        long id = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        assertThat(rolesOf(id)).containsExactly("BUYER");
        assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE id = ?", Boolean.class, id)).isFalse();
    }

    @Test
    void loginWithIdentifier() throws Exception {
        String body = postJson("/api/auth/login-with-identifier",
                with(Map.of("identifier", buyer.getEmail(), "password", PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).at("/data/accessToken").asText();
        assertThat(objectMapper.readTree(body).at("/data/roles").toString()).isEqualTo("[\"BUYER\"]");
        assertOnlyBuyer(token);
    }

    @Test
    void refreshToken() throws Exception {
        String refresh = f.tokens(buyer.getEmail()).get("refreshToken");
        String body = postJson("/api/auth/refresh-token", with(Map.of("refreshToken", refresh)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertOnlyBuyer(objectMapper.readTree(body).at("/data/accessToken").asText());
    }

    @Test
    void resendVerification() throws Exception {
        mockMvc.perform(post("/api/auth/resend-verification").param("email", buyer.getEmail())
                .param("roles", "SUPER_ADMIN").param("emailVerified", "true")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(PRIVILEGES)));
        assertThat(rolesOf(buyer.getId())).containsExactly("BUYER");
    }

    @Test
    void forgotPassword() throws Exception {
        String hashBefore = passwordHashOf(buyer.getId());
        mockMvc.perform(post("/api/auth/forgot-password").param("email", buyer.getEmail())
                .param("roles", "SUPER_ADMIN").param("newPassword", "Hijack@12345")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(PRIVILEGES)));
        assertThat(rolesOf(buyer.getId())).containsExactly("BUYER");
        assertThat(passwordHashOf(buyer.getId())).isEqualTo(hashBefore);
    }

    @Test
    void resetPassword() throws Exception {
        String hashBefore = passwordHashOf(buyer.getId());
        postJson("/api/auth/reset-password", with(Map.of("token", "not-a-real-token", "newPassword", "Hijack@12345",
                "email", buyer.getEmail()))).andExpect(status().is4xxClientError());
        assertThat(passwordHashOf(buyer.getId())).isEqualTo(hashBefore);
        assertThat(rolesOf(buyer.getId())).containsExactly("BUYER");
    }

    @Test
    void otpSend() throws Exception {
        postJson("/api/auth/otp/send", with(Map.of("identifier", buyer.getEmail(), "otpCode", "000000",
                "verified", true, "attempts", -100))).andExpect(status().isOk());
        assertLatestOtpIsFresh(buyer.getEmail());
    }

    @Test
    void otpResend() throws Exception {
        postJson("/api/auth/otp/resend", with(Map.of("identifier", buyer.getEmail(), "otpCode", "000000",
                "verified", true, "attempts", -100)));
        if (f.count("SELECT count(*) FROM otp_tokens WHERE identifier = ?", buyer.getEmail()) > 0) {
            assertLatestOtpIsFresh(buyer.getEmail());
        }
        assertThat(rolesOf(buyer.getId())).containsExactly("BUYER");
    }

    @Test
    void otpVerify() throws Exception {
        postJson("/api/auth/otp/send", Map.of("identifier", buyer.getEmail())).andExpect(status().isOk());
        String code = jdbc.queryForObject("SELECT otp_code FROM otp_tokens WHERE identifier = ? "
                + "ORDER BY created_at DESC LIMIT 1", String.class, buyer.getEmail());
        String body = postJson("/api/auth/otp/verify", with(Map.of("identifier", buyer.getEmail(), "otpCode", code)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).at("/data/roles").toString()).isEqualTo("[\"BUYER\"]");
        assertOnlyBuyer(objectMapper.readTree(body).at("/data/accessToken").asText());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> with(Map<String, Object> legitimate) {
        Map<String, Object> body = new HashMap<>(PRIVILEGES);
        body.putAll(legitimate);
        return body;
    }

    private ResultActions postJson(String url, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private void assertOnlyBuyer(String token) throws Exception {
        assertThat(token).isNotBlank();
        mockMvc.perform(authed(get("/api/admin/agents"), token)).andExpect(status().isForbidden());
        assertThat(rolesOf(buyer.getId())).containsExactly("BUYER");
    }

    private void assertLatestOtpIsFresh(String identifier) {
        var row = jdbc.queryForMap("SELECT is_verified, attempts FROM otp_tokens WHERE identifier = ? "
                + "ORDER BY created_at DESC LIMIT 1", identifier);
        assertThat(row).containsEntry("is_verified", false).containsEntry("attempts", 0);
        assertThat(rolesOf(buyer.getId())).containsExactly("BUYER");
    }

    private List<String> rolesOf(long userId) {
        return jdbc.queryForList("SELECT r.name FROM roles r JOIN user_roles ur ON ur.role_id = r.id "
                + "WHERE ur.user_id = ? ORDER BY r.name", String.class, userId);
    }

    private String passwordHashOf(long userId) {
        return jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId);
    }
}
