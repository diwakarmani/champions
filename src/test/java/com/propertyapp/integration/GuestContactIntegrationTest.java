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
import java.util.Map;
import java.util.UUID;

import static com.propertyapp.integration.Fixtures.authed;
import static com.propertyapp.integration.Fixtures.freshIp;
import static com.propertyapp.integration.Fixtures.from;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Visitors contacting an agent without an account, and the protection around it (ADR-0007). */
class GuestContactIntegrationTest extends AbstractPostgisIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired PropertyTypeRepository propertyTypeRepository;
    @Autowired JdbcTemplate jdbc;
    @MockBean JavaMailSender mailSender;

    Fixtures f;
    User agent;
    long listing;
    String ip;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(call -> new MimeMessage((Session) null));
        f = new Fixtures(mockMvc, objectMapper, userRepository, propertyRepository, propertyTypeRepository, jdbc);
        agent = f.user("REALTOR", "+1737" + (1_000_000 + (int) (Math.random() * 8_999_999)));
        listing = f.listing(agent, "ACTIVE");
        ip = freshIp();
    }

    // ── Guest inquiry ────────────────────────────────────────────────────────

    @Test
    void aGuestInquiryIsStoredWithoutAnAccountAndTheAgentAndGuestAreTold() throws Exception {
        String email = "Guest." + UUID.randomUUID() + "@Example.com";
        inquire(listing, Map.of("name", " Guest Person ", "email", email, "phone", "+1 512 555 0100",
                "message", "Is it still available?", "source", "MOBILE"), ip)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.received").value(true));

        var row = jdbc.queryForMap("SELECT inquirer_id, name, email, status, source FROM inquiries WHERE property_id = ?",
                listing);
        assertThat(row).containsEntry("name", "Guest Person")
                .containsEntry("email", email.toLowerCase())
                .containsEntry("status", "NEW")
                .containsEntry("source", "GUEST_MOBILE");
        assertThat(row.get("inquirer_id")).isNull();
        assertThat(f.count("SELECT inquiry_count FROM properties WHERE id = ?", listing)).isEqualTo(1);
        awaitNotification(agent.getId(), "INQUIRY_RECEIVED");
        verify(mailSender, timeout(5000).times(2)).send(any(MimeMessage.class)); // agent, and guest's receipt
    }

    /** S14: guest leads reached the database and the agent's email, but not the agent's inbox in the apps. */
    @Test
    void theAgentSeesGuestInquiriesInInquiriesReceived() throws Exception {
        String email = "guest." + UUID.randomUUID() + "@example.com";
        inquire(listing, Map.of("name", "Guest Person", "email", email, "message", "Is it still available?"), ip)
                .andExpect(status().isAccepted());

        mockMvc.perform(authed(get("/api/inquiries/received"), f.login(agent.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].email").value(email))
                .andExpect(jsonPath("$.data.content[0].name").value("Guest Person"))
                .andExpect(jsonPath("$.data.content[0].inquirerId").doesNotExist());
    }

    @Test
    void theBodyCannotChooseTheListingStatusAccountOrSource() throws Exception {
        long other = f.listing(agent, "ACTIVE");
        User someone = f.user("BUYER");
        Map<String, Object> body = new HashMap<>(validInquiry());
        body.putAll(Map.of("propertyId", other, "inquirerId", someone.getId(), "inquirer", Map.of("id", someone.getId()),
                "status", "CLOSED", "source", "ADMIN", "id", 1));

        inquire(listing, body, ip).andExpect(status().isAccepted());

        var row = jdbc.queryForMap("SELECT property_id, inquirer_id, status, source FROM inquiries WHERE email = ?",
                body.get("email"));
        assertThat(row).containsEntry("property_id", listing).containsEntry("status", "NEW")
                .containsEntry("source", "GUEST_WEB");
        assertThat(row.get("inquirer_id")).isNull();
        assertThat(f.count("SELECT count(*) FROM inquiries WHERE property_id = ?", other)).isZero();
    }

    @Test
    void aFilledHoneypotLooksAcceptedButStoresNothing() throws Exception {
        Map<String, Object> body = new HashMap<>(validInquiry());
        body.put("website", "http://spam.example");
        inquire(listing, body, ip).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.received").value(true));
        assertThat(f.count("SELECT count(*) FROM inquiries WHERE property_id = ?", listing)).isZero();
    }

    @Test
    void theSameEmailOnTheSameListingWithin24HoursIsStoredOnce() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        inquire(listing, inquiryFrom(email), ip).andExpect(status().isAccepted());
        inquire(listing, inquiryFrom(email.toUpperCase()), freshIp()).andExpect(status().isAccepted());
        assertThat(f.count("SELECT count(*) FROM inquiries WHERE property_id = ?", listing)).isEqualTo(1);

        jdbc.update("UPDATE inquiries SET created_at = now() - interval '25 hours' WHERE property_id = ?", listing);
        inquire(listing, inquiryFrom(email), freshIp()).andExpect(status().isAccepted());
        assertThat(f.count("SELECT count(*) FROM inquiries WHERE property_id = ?", listing)).isEqualTo(2);
    }

    @Test
    void hiddenListingsDoNotExistAndClosedOnesSaySo() throws Exception {
        inquire(f.listing(agent, "DRAFT"), validInquiry(), freshIp()).andExpect(status().isNotFound());
        inquire(Long.MAX_VALUE, validInquiry(), freshIp()).andExpect(status().isNotFound());
        inquire(f.listing(agent, "SOLD"), validInquiry(), freshIp()).andExpect(status().isBadRequest());
        contact(f.listing(agent, "DRAFT"), freshIp()).andExpect(status().isNotFound());
        contact(f.listing(agent, "RENTED"), freshIp()).andExpect(status().isBadRequest());

        f.suspend(agent);
        inquire(listing, validInquiry(), freshIp()).andExpect(status().isNotFound());
        contact(listing, freshIp()).andExpect(status().isNotFound());
    }

    @Test
    void invalidInquiriesAreRejected() throws Exception {
        inquire(listing, Map.of("name", "A", "email", "not-an-email", "message", "Hi"), ip)
                .andExpect(status().isBadRequest());
        inquire(listing, Map.of("email", "a@example.com", "message", "Hi"), ip).andExpect(status().isBadRequest());
        inquire(listing, Map.of("name", "A", "email", "a@example.com", "message", "x".repeat(2001)), ip)
                .andExpect(status().isBadRequest());
        inquire(listing, Map.of("name", "A", "email", "a@example.com", "message", "Hi", "phone", "<script>"), ip)
                .andExpect(status().isBadRequest());
    }

    @Test
    void fiveInquiriesPerAddressPerHour_thenTooManyWithRetryAfter() throws Exception {
        for (int i = 0; i < 5; i++) {
            inquire(listing, inquiryFrom("ip-" + i + "-" + UUID.randomUUID() + "@example.com"), ip)
                    .andExpect(status().isAccepted());
        }
        inquire(listing, validInquiry(), ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // A forged X-Forwarded-For does not reset the count: no proxy is trusted by default.
        mockMvc.perform(from(post("/api/public/properties/{id}/inquiries", listing), ip)
                        .header("X-Forwarded-For", freshIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validInquiry())))
                .andExpect(status().isTooManyRequests());
        // The honeypot does not dodge the limit either.
        Map<String, Object> bot = new HashMap<>(validInquiry());
        bot.put("website", "x");
        inquire(listing, bot, ip).andExpect(status().isTooManyRequests());

        inquire(listing, validInquiry(), freshIp()).andExpect(status().isAccepted());
    }

    @Test
    void tenInquiriesPerEmailPerDay_fromAnyAddress() throws Exception {
        String email = "busy-" + UUID.randomUUID() + "@example.com";
        for (int i = 0; i < 10; i++) {
            inquire(f.listing(agent, "ACTIVE"), inquiryFrom(email), freshIp()).andExpect(status().isAccepted());
        }
        inquire(f.listing(agent, "ACTIVE"), inquiryFrom(email.toUpperCase()), freshIp())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    // ── Revealing the agent's phone and email ────────────────────────────────

    @Test
    void aGuestCanRevealTheAgentsContactAndItIsRecorded() throws Exception {
        contact(listing, ip)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentId").value(agent.getId()))
                .andExpect(jsonPath("$.data.phone").value(agent.getPhone()))
                .andExpect(jsonPath("$.data.email").value(agent.getEmail()));
        mockMvc.perform(from(post("/api/public/properties/{id}/contact", listing), ip)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userId\": 1, \"contactedBy\": {\"id\": 1}}"))
                .andExpect(status().isOk());

        assertThat(f.count("SELECT count(*) FROM property_contact_events WHERE property_id = ? AND user_id IS NULL",
                listing)).isEqualTo(2);
        assertThat(f.count("SELECT count(*) FROM property_contact_events WHERE property_id = ? AND user_id IS NOT NULL",
                listing)).isZero();
        assertThat(f.count("SELECT contact_count FROM properties WHERE id = ?", listing)).isEqualTo(2);
    }

    @Test
    void thirtyRevealsPerAddressPerHour() throws Exception {
        for (int i = 0; i < 30; i++) {
            contact(listing, ip).andExpect(status().isOk());
        }
        contact(listing, ip).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        contact(listing, freshIp()).andExpect(status().isOk());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ResultActions inquire(long propertyId, Map<String, ?> body, String fromIp) throws Exception {
        return mockMvc.perform(from(post("/api/public/properties/{id}/inquiries", propertyId), fromIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions contact(long propertyId, String fromIp) throws Exception {
        return mockMvc.perform(from(post("/api/public/properties/{id}/contact", propertyId), fromIp));
    }

    private Map<String, Object> validInquiry() {
        return inquiryFrom("guest-" + UUID.randomUUID() + "@example.com");
    }

    private Map<String, Object> inquiryFrom(String email) {
        return Map.of("name", "Guest", "email", email, "message", "Is it still available?");
    }

    private void awaitNotification(long recipient, String type) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (f.count("SELECT count(*) FROM notifications WHERE recipient_id = ? AND type = ?", recipient, type) > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No " + type + " notification for " + recipient);
    }
}
