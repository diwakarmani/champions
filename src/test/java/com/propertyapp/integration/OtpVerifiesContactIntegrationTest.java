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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S11: password sign-in needs a verified email, and web sign-up confirms the email with a one-time
 * code. A correct code to an address proves the person controls it, so it marks that address verified.
 */
class OtpVerifiesContactIntegrationTest extends AbstractPostgisIntegrationTest {

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
        buyer = f.user("BUYER", "+1512" + (1_000_000 + (int) (Math.random() * 8_999_999)));
        jdbc.update("UPDATE users SET email_verified = false, mobile_verified = false WHERE id = ?", buyer.getId());
    }

    @Test
    void aCorrectCodeToTheEmailVerifiesItAndPasswordSignInThenWorks() throws Exception {
        login().andExpect(status().isUnauthorized());
        send(buyer.getEmail());
        verify(buyer.getEmail(), codeFor(buyer.getEmail())).andExpect(status().isOk());
        assertThat(flag("email_verified")).isTrue();
        assertThat(flag("mobile_verified")).isFalse();
        login().andExpect(status().isOk());
    }

    @Test
    void aCodeToThePhoneVerifiesThePhoneOnly() throws Exception {
        send(buyer.getPhone());
        verify(buyer.getPhone(), codeFor(buyer.getPhone())).andExpect(status().isOk());
        assertThat(flag("mobile_verified")).isTrue();
        assertThat(flag("email_verified")).isFalse();
    }

    @Test
    void aWrongCodeVerifiesNothing() throws Exception {
        send(buyer.getEmail());
        String right = codeFor(buyer.getEmail());
        verify(buyer.getEmail(), right.equals("000000") ? "111111" : "000000").andExpect(status().isBadRequest());
        assertThat(flag("email_verified")).isFalse();
    }

    private void send(String identifier) throws Exception {
        mockMvc.perform(Fixtures.from(post("/api/auth/otp/send"), Fixtures.freshIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", identifier))))
                .andExpect(status().isOk());
    }

    private ResultActions verify(String identifier, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/otp/verify").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("identifier", identifier, "otpCode", code))));
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(Fixtures.from(post("/api/auth/login-with-identifier"), Fixtures.freshIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("identifier", buyer.getEmail(), "password", Fixtures.PASSWORD))));
    }

    private String codeFor(String identifier) {
        return jdbc.queryForObject("SELECT otp_code FROM otp_tokens WHERE identifier = ? ORDER BY id DESC LIMIT 1",
                String.class, identifier);
    }

    private boolean flag(String column) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT " + column + " FROM users WHERE id = ?", Boolean.class, buyer.getId()));
    }
}
