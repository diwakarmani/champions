package com.propertyapp.integration;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S12: per-address limits on the auth routes, through the real stack (ADR-0008 Amendment 3).
 * Each test uses its own made-up address so buckets never leak between tests.
 */
@TestPropertySource(properties = {
        "app.rate-limit.auth.enabled=true",
        "app.rate-limit.auth.login-per-ip-per-15min=3",
        "app.rate-limit.auth.register-per-ip-per-hour=2",
        "app.rate-limit.auth.otp-send-per-ip-per-hour=2",
        "app.rate-limit.auth.otp-verify-per-ip-per-15min=3",
        "app.rate-limit.auth.mail-per-ip-per-hour=5",
        "app.rate-limit.auth.mail-per-email-per-hour=2",
        "app.rate-limit.auth.reset-per-ip-per-15min=2",
})
class AuthRateLimitIntegrationTest extends AbstractPostgisIntegrationTest {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(10);

    @Autowired MockMvc mockMvc;
    @MockBean JavaMailSender mailSender;

    @BeforeEach
    void mail() {
        when(mailSender.createMimeMessage()).thenAnswer(call -> new MimeMessage((Session) null));
    }

    private static String freshIp() {
        return "198.51.100." + NEXT_IP.getAndIncrement();
    }

    private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder b, String ip) {
        return b.with(req -> { req.setRemoteAddr(ip); return req; });
    }

    private static String json(String body) {
        return body;
    }

    @Test
    void wrongPasswordsFromOneAddressAreCutOffWith429AndRetryAfter() throws Exception {
        String ip = freshIp();
        String body = json("{\"identifier\":\"nobody-" + UUID.randomUUID() + "@example.com\",\"password\":\"Wrong@123\"}");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(from(post("/api/auth/login-with-identifier"), ip)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(from(post("/api/auth/login-with-identifier"), ip)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."));
    }

    @Test
    void anotherAddressIsNotAffected() throws Exception {
        String busy = freshIp();
        String body = json("{\"identifier\":\"nobody@example.com\",\"password\":\"Wrong@123\"}");
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(from(post("/api/auth/login-with-identifier"), busy)
                    .contentType(MediaType.APPLICATION_JSON).content(body));
        }
        mockMvc.perform(from(post("/api/auth/login-with-identifier"), freshIp())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPasswordCannotFloodOneInboxEvenFromManyAddresses() throws Exception {
        String victim = "victim-" + UUID.randomUUID() + "@example.com";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(from(post("/api/auth/forgot-password").param("email", victim), freshIp()))
                    .andExpect(status().isOk());
        }
        // Third request for the same address, from yet another address, case changed.
        mockMvc.perform(from(post("/api/auth/forgot-password").param("email", victim.toUpperCase()), freshIp()))
                .andExpect(status().isTooManyRequests());
        // A different inbox is still served.
        mockMvc.perform(from(post("/api/auth/forgot-password").param("email", "other-" + UUID.randomUUID() + "@example.com"), freshIp()))
                .andExpect(status().isOk());
    }

    @Test
    void oneAddressCannotSweepForgotPasswordAcrossManyInboxes() throws Exception {
        String ip = freshIp();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(from(post("/api/auth/forgot-password").param("email", "a" + i + "-" + UUID.randomUUID() + "@example.com"), ip))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(from(post("/api/auth/resend-verification").param("email", "b-" + UUID.randomUUID() + "@example.com"), ip))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void sendAndResendShareOneCodeAllowance() throws Exception {
        String ip = freshIp();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(from(post("/api/auth/otp/send"), ip).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"identifier\":\"c" + i + "-" + UUID.randomUUID() + "@example.com\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(from(post("/api/auth/otp/resend"), ip).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"d-" + UUID.randomUUID() + "@example.com\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void codeGuessingAndResetAndRegisterAreLimited() throws Exception {
        String ip = freshIp();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(from(post("/api/auth/otp/verify"), ip).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"identifier\":\"x@example.com\",\"otpCode\":\"000000\"}"));
        }
        mockMvc.perform(from(post("/api/auth/otp/verify"), ip).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"x@example.com\",\"otpCode\":\"000000\"}"))
                .andExpect(status().isTooManyRequests());

        String resetBody = "{\"token\":\"nope\",\"newPassword\":\"N3w@Passw0rd!\",\"confirmPassword\":\"N3w@Passw0rd!\"}";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(from(post("/api/auth/reset-password"), ip).contentType(MediaType.APPLICATION_JSON).content(resetBody));
        }
        mockMvc.perform(from(post("/api/auth/reset-password"), ip).contentType(MediaType.APPLICATION_JSON).content(resetBody))
                .andExpect(status().isTooManyRequests());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(from(post("/api/auth/register"), ip).contentType(MediaType.APPLICATION_JSON).content("{}"));
        }
        mockMvc.perform(from(post("/api/auth/register"), ip).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void refreshAndOtherRoutesAreNotLimited() throws Exception {
        String ip = freshIp();
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(from(post("/api/auth/refresh-token"), ip).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"bad\"}"))
                    .andExpect(result -> {
                        if (result.getResponse().getStatus() == 429) {
                            throw new AssertionError("refresh-token must not be rate limited here");
                        }
                    });
        }
    }
}
