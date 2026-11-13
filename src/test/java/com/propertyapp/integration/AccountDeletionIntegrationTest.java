package com.propertyapp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.entity.user.User;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.util.PasswordUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Date;
import java.util.Map;

import static com.propertyapp.integration.Fixtures.PASSWORD;
import static com.propertyapp.integration.Fixtures.authed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Self-service account deletion and what it does to tokens (ADR-0008 Amendment 1). */
class AccountDeletionIntegrationTest extends AbstractPostgisIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired PropertyTypeRepository propertyTypeRepository;
    @Autowired JdbcTemplate jdbc;
    @MockBean JavaMailSender mailSender;
    @Value("${jwt.secret}") String jwtSecret;

    Fixtures f;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(call -> new MimeMessage((Session) null));
        f = new Fixtures(mockMvc, objectMapper, userRepository, propertyRepository, propertyTypeRepository, jdbc);
    }

    @Test
    void aBuyerDeletesTheirAccount_dataIsAnonymisedAndEveryTokenStopsWorking() throws Exception {
        User buyer = f.user("BUYER", "+1415" + (1_000_000 + (int) (Math.random() * 8_999_999)));
        String email = buyer.getEmail();
        Map<String, String> tokens = f.tokens(email);
        User agent = f.user("REALTOR");
        long listing = f.listing(agent, "ACTIVE");
        jdbc.update("INSERT INTO user_favorites (user_id, property_id, created_at, version) VALUES (?, ?, now(), 0)",
                buyer.getId(), listing);
        jdbc.update("INSERT INTO user_addresses (user_id, is_default, address_line1, city, state, country, postal_code, created_at, version) "
                + "VALUES (?, false, '9 Home St', 'Austin', 'TX', 'USA', '78701', now(), 0)", buyer.getId());
        mockMvc.perform(authed(post("/api/inquiries"), tokens.get("accessToken"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("propertyId", listing, "name", "Real Name",
                                "email", email, "phone", "+15125550111", "message", "Keep this message"))))
                .andExpect(status().isOk());

        deleteMe(tokens.get("accessToken"), Map.of("password", PASSWORD)).andExpect(status().isOk());

        var row = jdbc.queryForMap("SELECT first_name, last_name, email, phone, is_active, deleted_at FROM users "
                + "WHERE id = ?", buyer.getId());
        assertThat(row).containsEntry("first_name", "Deleted").containsEntry("last_name", "user")
                .containsEntry("email", "deleted-" + buyer.getId() + "@deleted.invalid")
                .containsEntry("is_active", false);
        assertThat(row.get("phone")).isNull();
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(f.count("SELECT count(*) FROM user_favorites WHERE user_id = ?", buyer.getId())).isZero();
        assertThat(f.count("SELECT count(*) FROM user_addresses WHERE user_id = ?", buyer.getId())).isZero();
        var inquiry = jdbc.queryForMap("SELECT name, email, phone, message FROM inquiries WHERE inquirer_id = ?",
                buyer.getId());
        assertThat(inquiry).containsEntry("name", "Deleted user").containsEntry("message", "Keep this message")
                .doesNotContainValue(email);
        assertThat(inquiry.get("phone")).isNull();

        mockMvc.perform(authed(get("/api/users/me"), tokens.get("accessToken"))).andExpect(status().isUnauthorized());
        refresh(tokens.get("refreshToken")).andExpect(status().isUnauthorized());
        login(email).andExpect(status().isUnauthorized());

        // Someone new signs up with the freed email: the old tokens must not open their account.
        userRepository.saveAndFlush(User.builder().email(email).passwordHash(PasswordUtils.hash(PASSWORD))
                .firstName("New").lastName("Person").emailVerified(true).mobileVerified(false)
                .isActive(true).isLocked(false).build());
        mockMvc.perform(authed(get("/api/users/me"), tokens.get("accessToken"))).andExpect(status().isUnauthorized());
        refresh(tokens.get("refreshToken")).andExpect(status().isUnauthorized());
        mockMvc.perform(authed(get("/api/users/me"), f.login(email))).andExpect(status().isOk());
    }

    @Test
    void deletionNeedsTheRightConfirmation() throws Exception {
        User buyer = f.user("BUYER");
        String token = f.login(buyer.getEmail());

        deleteMe(null, Map.of("password", PASSWORD)).andExpect(status().isUnauthorized());
        deleteMe(token, Map.of()).andExpect(status().isBadRequest());
        deleteMe(token, Map.of("password", "Wrong@Passw0rd")).andExpect(status().isBadRequest());
        // A valid code issued to someone else's email does not confirm this account's deletion.
        User other = f.user("BUYER");
        mockMvc.perform(post("/api/auth/otp/send").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", other.getEmail()))))
                .andExpect(status().isOk());
        String othersCode = jdbc.queryForObject("SELECT otp_code FROM otp_tokens WHERE identifier = ? "
                + "ORDER BY created_at DESC LIMIT 1", String.class, other.getEmail());
        deleteMe(token, Map.of("otpIdentifier", other.getEmail(), "otpCode", othersCode))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findByIdAndDeletedAtIsNull(buyer.getId())).isPresent();
        mockMvc.perform(authed(get("/api/users/me"), token)).andExpect(status().isOk());
    }

    @Test
    void aOneTimeCodeSentToTheirOwnEmailAlsoConfirms() throws Exception {
        User buyer = f.user("BUYER");
        String token = f.login(buyer.getEmail());
        mockMvc.perform(post("/api/auth/otp/send").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", buyer.getEmail()))))
                .andExpect(status().isOk());
        String code = jdbc.queryForObject("SELECT otp_code FROM otp_tokens WHERE identifier = ? "
                + "ORDER BY created_at DESC LIMIT 1", String.class, buyer.getEmail());
        String wrong = code.equals("000000") ? "111111" : "000000";

        deleteMe(token, Map.of("otpIdentifier", buyer.getEmail(), "otpCode", wrong)).andExpect(status().isBadRequest());
        // The failed attempt is counted, not rolled back.
        assertThat(f.count("SELECT attempts FROM otp_tokens WHERE identifier = ? ORDER BY created_at DESC LIMIT 1",
                buyer.getEmail())).isEqualTo(1);

        deleteMe(token, Map.of("otpIdentifier", buyer.getEmail(), "otpCode", code)).andExpect(status().isOk());
        assertThat(userRepository.findByIdAndDeletedAtIsNull(buyer.getId())).isEmpty();
    }

    @Test
    void anAgentWithOpenListingsMustHandOverFirst() throws Exception {
        User agent = f.user("REALTOR");
        long active = f.listing(agent, "ACTIVE");
        long pending = f.listing(agent, "PENDING_APPROVAL");
        f.listing(agent, "SOLD");
        String token = f.login(agent.getEmail());

        deleteMe(token, Map.of("password", PASSWORD)).andExpect(status().isConflict());
        jdbc.update("UPDATE properties SET status = 'INACTIVE' WHERE id = ?", active);
        deleteMe(token, Map.of("password", PASSWORD)).andExpect(status().isConflict());
        jdbc.update("UPDATE properties SET status = 'INACTIVE' WHERE id = ?", pending);

        deleteMe(token, Map.of("password", PASSWORD)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT license_number FROM users WHERE id = ?", String.class, agent.getId()))
                .isNull();
        mockMvc.perform(get("/api/public/agents/{id}", agent.getId())).andExpect(status().isNotFound());
    }

    @Test
    void anApplicantDeletesTheirAccount_applicationIsClosedAndLosesTheirDetails() throws Exception {
        User applicant = f.user("BUYER");
        String token = f.login(applicant.getEmail());
        String response = mockMvc.perform(authed(post("/api/agent-applications"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("licenseNumber", "TX-424242",
                                "licenseState", "TX", "brokerageName", "Private Realty",
                                "bio", "My own words", "serviceAreas", "Austin"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long application = objectMapper.readTree(response).at("/data/id").asLong();

        deleteMe(token, Map.of("password", PASSWORD)).andExpect(status().isOk());

        var row = jdbc.queryForMap("SELECT status, license_number, license_state, brokerage_name, bio, "
                + "service_areas, rejection_reason FROM agent_applications WHERE id = ?", application);
        assertThat(row).containsEntry("status", "REJECTED").containsEntry("rejection_reason", "Account deleted")
                .doesNotContainValue("TX-424242").doesNotContainValue("Private Realty")
                .doesNotContainValue("TX");
        assertThat(row.get("bio")).isNull();
        assertThat(row.get("service_areas")).isNull();

        User admin = f.user("SUPER_ADMIN");
        mockMvc.perform(authed(post("/api/admin/agent-applications/{id}/approve", application),
                        f.login(admin.getEmail())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anAdminCannotDeleteThemselvesThisWay() throws Exception {
        User admin = f.user("SUPER_ADMIN");
        deleteMe(f.login(admin.getEmail()), Map.of("password", PASSWORD)).andExpect(status().isConflict());
        assertThat(userRepository.findByIdAndDeletedAtIsNull(admin.getId())).isPresent();
    }

    @Test
    void tokensWithoutTheUserIdClaimAreRefused() throws Exception {
        User buyer = f.user("BUYER");
        String legacy = Jwts.builder()
                .subject(buyer.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)))
                .compact();
        mockMvc.perform(authed(get("/api/users/me"), legacy)).andExpect(status().isUnauthorized());
        refresh(legacy).andExpect(status().isUnauthorized());
        mockMvc.perform(authed(get("/api/users/me"), f.login(buyer.getEmail()))).andExpect(status().isOk());
    }

    private ResultActions deleteMe(String token, Map<String, ?> body) throws Exception {
        return mockMvc.perform(authed(delete("/api/users/me"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh-token").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
    }

    private ResultActions login(String identifier) throws Exception {
        return mockMvc.perform(post("/api/auth/login-with-identifier").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("identifier", identifier, "password", PASSWORD))));
    }
}
