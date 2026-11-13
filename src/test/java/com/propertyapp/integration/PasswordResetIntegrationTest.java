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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S11 review of the reset flow the web now uses. */
class PasswordResetIntegrationTest extends AbstractPostgisIntegrationTest {

    static final String NEW_PASSWORD = "N3w@Passw0rd!";

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
    void unknownEmailGetsTheSameAnswerAsAKnownOneSoAccountsCannotBeFound() throws Exception {
        String unknown = "nobody-" + UUID.randomUUID() + "@example.com";
        String unknownBody = forgot(unknown);
        String knownBody = forgot(buyer.getEmail());
        // Identical apart from the timestamp.
        assertThat(withoutTimestamp(unknownBody)).isEqualTo(withoutTimestamp(knownBody));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM verification_tokens t JOIN users u ON u.id = t.user_id WHERE u.email = ?",
                Integer.class, unknown)).isZero();
        assertThat(tokenOf(buyer, "PASSWORD_RESET")).isNotNull();
    }

    @Test
    void anEmailVerificationTokenCannotResetAPassword() throws Exception {
        String hashBefore = hashOf(buyer);
        String verification = insertToken(buyer, "EMAIL_VERIFICATION");
        reset(verification).andExpect(status().isBadRequest());
        assertThat(hashOf(buyer)).isEqualTo(hashBefore);
    }

    @Test
    void aResetTokenCannotVerifyAnEmail() throws Exception {
        jdbc.update("UPDATE users SET email_verified = false WHERE id = ?", buyer.getId());
        forgot(buyer.getEmail());
        mockMvc.perform(get("/api/auth/verify-email").param("token", tokenOf(buyer, "PASSWORD_RESET")))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE id = ?", Boolean.class, buyer.getId())).isFalse();
    }

    @Test
    void theResetCodeFromTheEmailSetsANewPasswordOnce() throws Exception {
        forgot(buyer.getEmail());
        String code = tokenOf(buyer, "PASSWORD_RESET");
        reset(code).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login-with-identifier").contentType(MediaType.APPLICATION_JSON)
                        .with(r -> { r.setRemoteAddr(Fixtures.freshIp()); return r; })
                        .content(objectMapper.writeValueAsString(Map.of("identifier", buyer.getEmail(), "password", NEW_PASSWORD))))
                .andExpect(status().isOk());
        reset(code).andExpect(status().isBadRequest());
    }

    private String forgot(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/forgot-password").param("email", email)
                        .with(r -> { r.setRemoteAddr(Fixtures.freshIp()); return r; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.ResultActions reset(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token, "newPassword", NEW_PASSWORD))));
    }

    private String tokenOf(User user, String type) {
        return jdbc.queryForObject("SELECT token FROM verification_tokens WHERE user_id = ? AND token_type = ? ORDER BY id DESC LIMIT 1",
                String.class, user.getId(), type);
    }

    private String insertToken(User user, String type) {
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO verification_tokens (user_id, token, token_type, expires_at, created_at) VALUES (?, ?, ?, now() + interval '1 hour', now())",
                user.getId(), token, type);
        return token;
    }

    private static String withoutTimestamp(String body) {
        return body.replaceAll("\"timestamp\":\"[^\"]*\"", "");
    }

    private String hashOf(User user) {
        return jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, user.getId());
    }
}
