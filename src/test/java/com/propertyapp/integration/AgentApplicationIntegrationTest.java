package com.propertyapp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.user.User;
import com.propertyapp.repository.agent.AgentStatusEventRepository;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.util.PasswordUtils;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Becoming an agent, and losing and regaining the right to list (ADR-0004, ADR-0005). */
class AgentApplicationIntegrationTest extends AbstractPostgisIntegrationTest {

    private static final String PASSWORD = "Str0ng@Passw0rd";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired PropertyTypeRepository propertyTypeRepository;
    @Autowired AgentStatusEventRepository statusEventRepository;
    @Autowired JdbcTemplate jdbc;
    @MockBean JavaMailSender mailSender;

    String adminToken;
    long adminId;
    String city;

    @BeforeEach
    void setUp() throws Exception {
        when(mailSender.createMimeMessage()).thenAnswer(call -> new MimeMessage((Session) null));
        adminToken = login("admin@propertyapp.com", "Admin@123");
        adminId = userRepository.findByEmailAndDeletedAtIsNull("admin@propertyapp.com").orElseThrow().getId();
        city = "City" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ── Applying and approval ────────────────────────────────────────────────

    @Test
    void anApprovedApplicantCanListStraightAwayWithTheSameToken() throws Exception {
        User person = user("BUYER");
        String token = login(person.getEmail(), PASSWORD);

        mockMvc.perform(createListing(token)).andExpect(status().isForbidden());

        long application = apply(token);
        mockMvc.perform(createListing(token)).andExpect(status().isForbidden()); // applying grants nothing

        mockMvc.perform(authed(post("/api/admin/agent-applications/{id}/approve", application), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(createListing(token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ownerId").value(person.getId()));

        mockMvc.perform(authed(get("/api/users/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[?(@ == 'REALTOR')]").exists())
                .andExpect(jsonPath("$.data.roles[?(@ == 'BUYER')]").exists())
                .andExpect(jsonPath("$.data.agentSuspended").value(false));

        assertThat(jdbc.queryForMap("SELECT status, reviewed_by, reviewed_at FROM agent_applications WHERE id = ?",
                application))
                .containsEntry("status", "APPROVED")
                .containsEntry("reviewed_by", adminId)
                .extractingByKey("reviewed_at").isNotNull();
        awaitNotifications(person.getId(), "AGENT_APPLICATION_APPROVED", 1);
        verify(mailSender, timeout(5000)).send(any(MimeMessage.class));
    }

    @Test
    void approvalCopiesTheProfileAndOnlyOverwritesBioAndPhotoWhenSupplied() throws Exception {
        User withDetails = user("BUYER");
        approve(apply(login(withDetails.getEmail(), PASSWORD), Map.of(
                "licenseNumber", " TX-1234 ", "licenseState", "tx", "brokerageName", "Lone Star Realty",
                "yearsExperience", 7, "serviceAreas", "Austin, Round Rock", "bio", "Ten years in Austin.",
                "photoUrl", "https://img.example/me.jpg")));

        User copied = userRepository.findById(withDetails.getId()).orElseThrow();
        assertThat(copied.getLicenseNumber()).isEqualTo("TX-1234");
        assertThat(copied.getLicenseState()).isEqualTo("TX");
        assertThat(copied.getBrokerageName()).isEqualTo("Lone Star Realty");
        assertThat(copied.getYearsExperience()).isEqualTo(7);
        assertThat(copied.getServiceAreas()).isEqualTo("Austin, Round Rock");
        assertThat(copied.getBio()).isEqualTo("Ten years in Austin.");
        assertThat(copied.getProfileImageUrl()).isEqualTo("https://img.example/me.jpg");
        assertThat(copied.isLicensePublic()).isFalse();

        User keepsOwn = user("BUYER");
        jdbc.update("UPDATE users SET bio = 'Existing bio', profile_image_url = 'https://x/old.jpg' WHERE id = ?",
                keepsOwn.getId());
        approve(apply(login(keepsOwn.getEmail(), PASSWORD)));
        User kept = userRepository.findById(keepsOwn.getId()).orElseThrow();
        assertThat(kept.getBio()).isEqualTo("Existing bio");
        assertThat(kept.getProfileImageUrl()).isEqualTo("https://x/old.jpg");
    }

    @Test
    void anApplicantCannotSupplyTheirOwnReviewOrRoles() throws Exception {
        User person = user("BUYER");
        Map<String, Object> body = new LinkedHashMap<>(validApplication());
        body.put("status", "APPROVED");
        body.put("reviewedBy", person.getId());
        body.put("reviewedAt", "2020-01-01T00:00:00");
        body.put("userId", 1);
        body.put("roles", List.of("SUPER_ADMIN", "REALTOR"));

        mockMvc.perform(submit(login(person.getEmail(), PASSWORD), body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.userId").value(person.getId()))
                .andExpect(jsonPath("$.data.reviewedAt").doesNotExist());

        assertThat(rolesOf(person.getId())).containsExactly("BUYER");
    }

    @Test
    void applicationsAreValidated() throws Exception {
        String token = login(user("BUYER").getEmail(), PASSWORD);
        for (Map.Entry<String, Object> bad : List.of(
                Map.entry("licenseNumber", (Object) " "),
                Map.entry("licenseState", (Object) "Texas"),
                Map.entry("brokerageName", (Object) ""),
                Map.entry("yearsExperience", (Object) Integer.valueOf(-1)),
                Map.entry("yearsExperience", (Object) 81),
                Map.entry("photoUrl", (Object) "javascript:alert(1)"),
                Map.entry("serviceAreas", (Object) "x".repeat(501)))) {
            Map<String, Object> body = new LinkedHashMap<>(validApplication());
            body.put(bad.getKey(), bad.getValue());
            mockMvc.perform(submit(token, body)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(authed(get("/api/agent-applications/me"), token))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void onlyOneOpenApplicationPerPerson() throws Exception {
        String token = login(user("BUYER").getEmail(), PASSWORD);
        apply(token);
        mockMvc.perform(submit(token, validApplication())).andExpect(status().isConflict());
    }

    @Test
    void twoSimultaneousApplicationsLeaveOneRow() throws Exception {
        User person = user("BUYER");
        String token = login(person.getEmail(), PASSWORD);

        List<Integer> statuses = together(() -> mockMvc.perform(submit(token, validApplication()))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_applications WHERE user_id = ?",
                Long.class, person.getId())).isEqualTo(1L);
    }

    @Test
    void agentsAndSuspendedAgentsCannotApply() throws Exception {
        User agent = user("REALTOR");
        String agentToken = login(agent.getEmail(), PASSWORD);
        mockMvc.perform(submit(agentToken, validApplication())).andExpect(status().isBadRequest());

        suspend(agent);
        mockMvc.perform(submit(agentToken, validApplication())).andExpect(status().isBadRequest());

        // Taking the role away does not open a way round the suspension.
        mockMvc.perform(setRoles(agent, "BUYER")).andExpect(status().isOk());
        mockMvc.perform(submit(agentToken, validApplication())).andExpect(status().isBadRequest());
    }

    // ── Rejection and history ────────────────────────────────────────────────

    @Test
    void aRejectedApplicantSeesTheReasonAndCanReapplyWithHistoryKept() throws Exception {
        User person = user("BUYER");
        String token = login(person.getEmail(), PASSWORD);
        long first = apply(token);

        mockMvc.perform(authed(post("/api/admin/agent-applications/{id}/reject", first), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(authed(post("/api/admin/agent-applications/{id}/reject", first), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        reject(first, "License not found");
        assertThat(rolesOf(person.getId())).containsExactly("BUYER");
        awaitNotifications(person.getId(), "AGENT_APPLICATION_REJECTED", 1);

        long second = apply(token);
        assertThat(second).isNotEqualTo(first);

        mockMvc.perform(authed(get("/api/agent-applications/me"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(second))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[1].id").value(first))
                .andExpect(jsonPath("$.data[1].rejectionReason").value("License not found"));
    }

    @Test
    void theEmailEscapesWhatAnAdminTyped() throws Exception {
        User person = user("BUYER");
        long application = apply(login(person.getEmail(), PASSWORD));

        reject(application, "<script>alert('x')</script> not valid");

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(5000)).send(sent.capture());
        MimeMessage message = sent.getValue();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(person.getEmail());
        assertThat(message.getSubject()).isEqualTo("Your agent application was not approved");
        assertThat(htmlOf(message)).contains("&lt;script&gt;").doesNotContain("<script>");
    }

    // ── Who may review, and when ─────────────────────────────────────────────

    @Test
    void anAdminCannotReviewTheirOwnApplication() throws Exception {
        User secondAdmin = user("SUPER_ADMIN");
        String token = login(secondAdmin.getEmail(), PASSWORD);
        long own = apply(token);

        mockMvc.perform(authed(post("/api/admin/agent-applications/{id}/approve", own), token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(authed(post("/api/admin/agent-applications/{id}/reject", own), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"no\"}"))
                .andExpect(status().isBadRequest());
        assertThat(rolesOf(secondAdmin.getId())).containsExactly("SUPER_ADMIN");

        approve(own); // a different admin can
        assertThat(rolesOf(secondAdmin.getId())).containsExactlyInAnyOrder("SUPER_ADMIN", "REALTOR");
    }

    @Test
    void aReviewedApplicationCannotBeReviewedAgain() throws Exception {
        long approved = apply(login(user("BUYER").getEmail(), PASSWORD));
        approve(approved);
        mockMvc.perform(authed(post("/api/admin/agent-applications/{id}/approve", approved), adminToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(authed(post("/api/admin/agent-applications/{id}/reject", approved), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"changed my mind\"}"))
                .andExpect(status().isBadRequest());

        long rejected = apply(login(user("BUYER").getEmail(), PASSWORD));
        reject(rejected, "No");
        mockMvc.perform(authed(post("/api/admin/agent-applications/{id}/approve", rejected), adminToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(authed(post("/api/admin/agent-applications/{id}/approve", 999_999_999L), adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void anApplicantWhoseAccountWasClosedCannotBeApproved() throws Exception {
        User person = user("BUYER");
        long application = apply(login(person.getEmail(), PASSWORD));
        jdbc.update("UPDATE users SET is_active = false WHERE id = ?", person.getId());

        mockMvc.perform(authed(post("/api/admin/agent-applications/{id}/approve", application), adminToken))
                .andExpect(status().isBadRequest());
        assertThat(rolesOf(person.getId())).containsExactly("BUYER");
    }

    @Test
    void approvalCannotLiftASuspension() throws Exception {
        User person = user("BUYER");
        long application = apply(login(person.getEmail(), PASSWORD));
        mockMvc.perform(setRoles(person, "BUYER", "REALTOR")).andExpect(status().isOk());
        suspend(person);

        mockMvc.perform(authed(post("/api/admin/agent-applications/{id}/approve", application), adminToken))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findById(person.getId()).orElseThrow().isAgentSuspended()).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM agent_applications WHERE id = ?", String.class, application))
                .isEqualTo("PENDING");
    }

    @Test
    void twoAdminsApprovingAtOnceProduceOneDecisionAndNoServerError() throws Exception {
        User person = user("BUYER");
        long application = apply(login(person.getEmail(), PASSWORD));

        List<Integer> statuses = together(() -> mockMvc.perform(
                        authed(post("/api/admin/agent-applications/{id}/approve", application), adminToken))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).contains(200).hasSize(2);
        assertThat(statuses.stream().filter(code -> code == 200)).hasSize(1);
        assertThat(statuses.stream().filter(code -> code != 200)).allMatch(code -> code == 400 || code == 409);
        awaitNotifications(person.getId(), "AGENT_APPLICATION_APPROVED", 1);
    }

    @Test
    void adminsAreToldOfANewApplicationAndCanFilterByStatus() throws Exception {
        long pending = apply(login(user("BUYER").getEmail(), PASSWORD));
        long rejected = apply(login(user("BUYER").getEmail(), PASSWORD));
        reject(rejected, "No");

        awaitEntityNotification(adminId, "AGENT_APPLICATION_SUBMITTED", pending);

        assertThat(idsOf(get("/api/admin/agent-applications").param("status", "PENDING").param("size", "100")))
                .contains(pending).doesNotContain(rejected);
        assertThat(idsOf(get("/api/admin/agent-applications").param("status", "REJECTED").param("size", "100")))
                .contains(rejected).doesNotContain(pending);
        assertThat(idsOf(get("/api/admin/agent-applications").param("size", "100")))
                .contains(pending, rejected);

        mockMvc.perform(authed(get("/api/admin/agent-applications").param("status", "NOPE"), adminToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(authed(get("/api/admin/agent-applications").param("size", "100000"), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(100));
    }

    // ── Role matrix ──────────────────────────────────────────────────────────

    @Test
    void roleMatrixForEveryNewRoute() throws Exception {
        long application = apply(login(user("BUYER").getEmail(), PASSWORD));
        long target = user("REALTOR").getId();
        String buyerToken = login(user("BUYER").getEmail(), PASSWORD);
        String agentToken = login(user("REALTOR").getEmail(), PASSWORD);
        String sellerToken = login(user("SELLER").getEmail(), PASSWORD);
        String reason = "{\"reason\":\"test\"}";

        // Applicant routes: any logged-in person, nobody anonymous.
        expectStatus(401, mockMvc.perform(get("/api/agent-applications/me")));
        expectStatus(401, mockMvc.perform(submit(null, validApplication())));
        for (String token : List.of(buyerToken, agentToken, sellerToken, adminToken)) {
            expectStatus(200, mockMvc.perform(authed(get("/api/agent-applications/me"), token)));
        }

        // Admin routes: admin only. Each case is refused for all three other kinds of caller.
        List<java.util.function.Function<String, RequestBuilder>> adminRoutes = List.of(
                token -> authed(get("/api/admin/agent-applications"), token),
                token -> authed(post("/api/admin/agent-applications/{id}/approve", application), token),
                token -> authed(post("/api/admin/agent-applications/{id}/reject", application), token)
                        .contentType(MediaType.APPLICATION_JSON).content(reason),
                token -> authed(get("/api/admin/agents"), token),
                token -> authed(post("/api/admin/agents/{id}/suspend", target), token)
                        .contentType(MediaType.APPLICATION_JSON).content(reason),
                token -> authed(post("/api/admin/agents/{id}/reinstate", target), token));
        for (var route : adminRoutes) {
            expectStatus(401, mockMvc.perform(route.apply(null)));
            for (String token : List.of(buyerToken, agentToken, sellerToken)) {
                expectStatus(403, mockMvc.perform(route.apply(token)));
            }
        }

        // None of those attempts changed anything.
        assertThat(jdbc.queryForObject("SELECT status FROM agent_applications WHERE id = ?", String.class, application))
                .isEqualTo("PENDING");
        assertThat(userRepository.findById(target).orElseThrow().isAgentSuspended()).isFalse();

        expectStatus(200, mockMvc.perform(authed(get("/api/admin/agent-applications"), adminToken)));
        expectStatus(200, mockMvc.perform(authed(get("/api/admin/agents"), adminToken)));
    }

    @Test
    void onlyAnAdminCanFlipTheVerifiedBadge() throws Exception {
        User agent = user("REALTOR");
        long own = listing(agent, "ACTIVE");
        long others = listing(user("REALTOR"), "ACTIVE");
        String token = login(agent.getEmail(), PASSWORD);

        expectStatus(403, mockMvc.perform(authed(patch("/api/properties/{id}/toggle-verified", own), token)));
        expectStatus(403, mockMvc.perform(authed(patch("/api/properties/{id}/toggle-verified", others), token)));
        expectStatus(200, mockMvc.perform(authed(patch("/api/properties/{id}/toggle-verified", own), adminToken)));
    }

    // ── Suspension ───────────────────────────────────────────────────────────

    @Test
    void suspensionHidesEveryPublicSurfaceAndReinstatingBringsThemBackUnchanged() throws Exception {
        User agent = user("REALTOR");
        long active = listing(agent, "ACTIVE");
        long sold = listing(agent, "SOLD");
        long draft = listing(agent, "DRAFT");
        long other = listing(user("REALTOR"), "ACTIVE");
        for (long id : new long[] {active, other}) {
            jdbc.update("INSERT INTO discovery_cache (user_id, category, city, property_id, score, rank) "
                    + "VALUES (0, 'POPULAR', ?, ?, 1, 1)", city, id);
        }
        String buyerToken = login(user("BUYER").getEmail(), PASSWORD);

        assertVisibleEverywhere(active, true);
        mockMvc.perform(get("/api/properties/{id}", sold)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/realtors/{id}", agent.getId()), buyerToken)).andExpect(status().isOk());
        mockMvc.perform(get("/api/realtors/{id}/ratings", agent.getId())).andExpect(status().isOk());
        // An admin's read is cached: it must not keep showing the agent as active after suspension.
        mockMvc.perform(authed(get("/api/users/{id}", agent.getId()), adminToken))
                .andExpect(jsonPath("$.data.agentSuspended").value(false));

        suspend(agent);

        mockMvc.perform(authed(get("/api/users/{id}", agent.getId()), adminToken))
                .andExpect(jsonPath("$.data.agentSuspended").value(true));
        mockMvc.perform(get("/api/realtors/{id}/ratings", agent.getId())).andExpect(status().isNotFound());
        mockMvc.perform(authed(post("/api/realtors/{id}/ratings", agent.getId()), buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("propertyId", active, "rating", 5))))
                .andExpect(status().isNotFound());

        assertVisibleEverywhere(active, false);
        assertVisibleEverywhere(other, true);
        mockMvc.perform(get("/api/properties/{id}", sold)).andExpect(status().isNotFound());
        mockMvc.perform(authed(get("/api/realtors/{id}", agent.getId()), buyerToken)).andExpect(status().isNotFound());
        mockMvc.perform(authed(post("/api/realtors/{id}/connect", agent.getId()), buyerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(authed(post("/api/properties/{id}/reveal-contact", active), buyerToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(authed(post("/api/inquiries"), buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("propertyId", active, "name", "Buyer",
                                "email", "buyer@example.com", "message", "Still available?"))))
                .andExpect(status().isNotFound());

        // No listing was rewritten.
        assertThat(statusOf(active)).isEqualTo("ACTIVE");
        assertThat(statusOf(sold)).isEqualTo("SOLD");
        assertThat(statusOf(draft)).isEqualTo("DRAFT");

        reinstate(agent);

        assertVisibleEverywhere(active, true);
        mockMvc.perform(get("/api/realtors/{id}/ratings", agent.getId())).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/users/{id}", agent.getId()), adminToken))
                .andExpect(jsonPath("$.data.agentSuspended").value(false));
        mockMvc.perform(get("/api/properties/{id}", sold)).andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/realtors/{id}", agent.getId()), buyerToken)).andExpect(status().isOk());
        assertThat(statusOf(draft)).isEqualTo("DRAFT");
    }

    @Test
    void aSuspendedAgentCanLogInAndReadButNotAct() throws Exception {
        User agent = user("REALTOR");
        long draft = listing(agent, "DRAFT");
        long active = listing(agent, "ACTIVE");
        String token = login(agent.getEmail(), PASSWORD);

        suspend(agent);
        // The token from before the suspension is refused too, and so is a fresh login's.
        String freshToken = login(agent.getEmail(), PASSWORD);

        for (String t : List.of(token, freshToken)) {
            mockMvc.perform(createListing(t)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Your agent account is suspended, so you cannot create listings"));
            expectDenied(mockMvc.perform(authed(put("/api/properties/{id}", draft), t)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Changed\"}")));
            expectDenied(mockMvc.perform(authed(patch("/api/properties/{id}/publish", draft), t)));
            expectDenied(mockMvc.perform(authed(post("/api/properties/{id}/request-deletion", active), t)));
            expectDenied(mockMvc.perform(authed(delete("/api/properties/{id}", active), t)));
            expectDenied(mockMvc.perform(authed(post("/api/properties/{id}/images", active), t)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"imageUrl\":\"https://img.example/y.jpg\"}")));
        }

        mockMvc.perform(authed(get("/api/users/me"), freshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentSuspended").value(true));
        mockMvc.perform(authed(get("/api/properties/{id}", active), freshToken)).andExpect(status().isOk());
        assertThat(rolesOf(agent.getId())).contains("REALTOR");
        assertThat(statusOf(draft)).isEqualTo("DRAFT");

        reinstate(agent);
        mockMvc.perform(authed(patch("/api/properties/{id}/publish", draft), token)).andExpect(status().isOk());
        mockMvc.perform(createListing(token)).andExpect(status().isCreated());
    }

    @Test
    void aSuspendedAgentsListingsCannotGoLiveAndCannotReceiveMore() throws Exception {
        User agent = user("REALTOR");
        long pending = listing(agent, "PENDING_APPROVAL");
        long draft = listing(agent, "DRAFT");
        long elsewhere = listing(user("REALTOR"), "ACTIVE");
        suspend(agent);

        mockMvc.perform(authed(post("/api/admin/properties/{id}/approve", pending), adminToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(authed(patch("/api/properties/{id}/status", draft), adminToken).param("status", "ACTIVE"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(authed(post("/api/admin/properties/{id}/reassign", elsewhere), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"agentId\":" + agent.getId() + "}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(createListing(adminToken, agent.getId())).andExpect(status().isBadRequest());
    }

    @Test
    void anAdminCanMoveAListingAwayFromASuspendedAgent() throws Exception {
        User agent = user("REALTOR");
        User replacement = user("REALTOR");
        long id = listing(agent, "ACTIVE");
        suspend(agent);

        mockMvc.perform(get("/api/properties/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(authed(post("/api/admin/properties/{id}/reassign", id), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"agentId\":" + replacement.getId() + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/properties/{id}", id)).andExpect(status().isOk());
    }

    @Test
    void suspensionSurvivesTakingAwayAndRegrantingTheRole() throws Exception {
        User agent = user("REALTOR");
        long id = listing(agent, "ACTIVE");
        suspend(agent);

        mockMvc.perform(setRoles(agent, "BUYER")).andExpect(status().isOk());
        mockMvc.perform(setRoles(agent, "BUYER", "REALTOR")).andExpect(status().isOk());

        mockMvc.perform(get("/api/properties/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void suspendAndReinstateAreAuditedAndNotified() throws Exception {
        User agent = user("REALTOR");
        mockMvc.perform(authed(post("/api/admin/agents/{id}/suspend", agent.getId()), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" Complaints \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.data.suspensionReason").value("Complaints"))
                .andExpect(jsonPath("$.data.suspendedAt").exists());
        mockMvc.perform(authed(post("/api/admin/agents/{id}/reinstate", agent.getId()), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.suspensionReason").doesNotExist());

        var events = statusEventRepository.findByAgentIdOrderByCreatedAtDescIdDesc(agent.getId());
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getAction()).isEqualTo("REINSTATED");
        assertThat(events.get(1).getAction()).isEqualTo("SUSPENDED");
        assertThat(events.get(1).getReason()).isEqualTo("Complaints");
        assertThat(events.get(1).getPerformedBy()).isEqualTo(adminId);
        assertThat(events.get(1).getCreatedAt()).isNotNull();

        awaitNotifications(agent.getId(), "AGENT_SUSPENDED", 1);
        awaitNotifications(agent.getId(), "AGENT_REINSTATED", 1);
        verify(mailSender, timeout(5000).times(2)).send(any(MimeMessage.class));
    }

    @Test
    void suspensionRules() throws Exception {
        User agent = user("REALTOR");
        User buyer = user("BUYER");
        String reason = "{\"reason\":\"test\"}";

        // Only agents.
        mockMvc.perform(authed(post("/api/admin/agents/{id}/suspend", buyer.getId()), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(reason))
                .andExpect(status().isNotFound());
        mockMvc.perform(authed(post("/api/admin/agents/{id}/suspend", 999_999_999L), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(reason))
                .andExpect(status().isNotFound());

        // A reason is required, and state must make sense.
        mockMvc.perform(authed(post("/api/admin/agents/{id}/suspend", agent.getId()), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(authed(post("/api/admin/agents/{id}/reinstate", agent.getId()), adminToken))
                .andExpect(status().isBadRequest());
        suspend(agent);
        mockMvc.perform(authed(post("/api/admin/agents/{id}/suspend", agent.getId()), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(reason))
                .andExpect(status().isBadRequest());

        // Not your own.
        User adminAgent = user("SUPER_ADMIN");
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = 'REALTOR'",
                adminAgent.getId());
        mockMvc.perform(authed(post("/api/admin/agents/{id}/suspend", adminAgent.getId()),
                        login(adminAgent.getEmail(), PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(reason))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theAgentListFiltersByStatus() throws Exception {
        User active = user("REALTOR");
        User suspended = user("REALTOR");
        User buyer = user("BUYER");
        suspend(suspended);

        assertThat(agentIds(null)).contains(active.getId(), suspended.getId()).doesNotContain(buyer.getId());
        assertThat(agentIds("ACTIVE")).contains(active.getId()).doesNotContain(suspended.getId());
        assertThat(agentIds("SUSPENDED")).contains(suspended.getId()).doesNotContain(active.getId());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void assertVisibleEverywhere(long id, boolean visible) throws Exception {
        mockMvc.perform(get("/api/properties/{id}", id)).andExpect(status().is(visible ? 200 : 404));
        for (RequestBuilder surface : List.of(
                get("/api/properties").param("city", city).param("size", "100"),
                get("/api/properties/featured").param("limit", "100"),
                get("/api/discovery/home/view-more").param("category", "POPULAR").param("city", city).param("size", "100"),
                get("/api/discovery/home").param("city", city).param("lat", "30.26").param("lng", "-97.74"))) {
            String body = mockMvc.perform(surface).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            List<Long> ids = objectMapper.readTree(body).findValues("id").stream().map(JsonNode::asLong).toList();
            if (visible) {
                assertThat(ids).contains(id);
            } else {
                assertThat(ids).doesNotContain(id);
            }
        }
    }

    private void suspend(User agent) throws Exception {
        mockMvc.perform(authed(post("/api/admin/agents/{id}/suspend", agent.getId()), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"test\"}"))
                .andExpect(status().isOk());
    }

    private void reinstate(User agent) throws Exception {
        mockMvc.perform(authed(post("/api/admin/agents/{id}/reinstate", agent.getId()), adminToken))
                .andExpect(status().isOk());
    }

    private void approve(long application) throws Exception {
        mockMvc.perform(authed(post("/api/admin/agent-applications/{id}/approve", application), adminToken))
                .andExpect(status().isOk());
    }

    private void reject(long application, String reason) throws Exception {
        mockMvc.perform(authed(post("/api/admin/agent-applications/{id}/reject", application), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", reason))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    private long apply(String token) throws Exception {
        return apply(token, validApplication());
    }

    private long apply(String token, Map<String, Object> body) throws Exception {
        String response = mockMvc.perform(submit(token, body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private Map<String, Object> validApplication() {
        return Map.of("licenseNumber", "TX-999", "licenseState", "TX", "brokerageName", "Test Realty");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder submit(
            String token, Map<String, Object> body) throws Exception {
        return authed(post("/api/agent-applications"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private RequestBuilder createListing(String token) throws Exception {
        return createListing(token, null);
    }

    private RequestBuilder createListing(String token, Long agentId) throws Exception {
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
        return authed(post("/api/properties"), token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(node));
    }

    private RequestBuilder setRoles(User target, String... names) throws Exception {
        return authed(post("/api/users/{id}/roles", target.getId()), adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(names));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authed(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    private List<Long> idsOf(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String body = mockMvc.perform(authed(request, adminToken)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Long> ids = new ArrayList<>();
        objectMapper.readTree(body).at("/data/content").forEach(row -> ids.add(row.get("id").asLong()));
        return ids;
    }

    private List<Long> agentIds(String status) throws Exception {
        var request = get("/api/admin/agents").param("size", "100");
        return idsOf(status == null ? request : request.param("status", status));
    }

    private void expectStatus(int expected, ResultActions result) {
        assertThat(result.andReturn().getResponse().getStatus())
                .as(result.andReturn().getRequest().getMethod() + " " + result.andReturn().getRequest().getRequestURI())
                .isEqualTo(expected);
    }

    private void expectDenied(ResultActions result) {
        assertThat(result.andReturn().getResponse().getStatus())
                .as(result.andReturn().getRequest().getRequestURI()).isIn(401, 403);
    }

    private String statusOf(long id) {
        return propertyRepository.findById(id).orElseThrow().getStatus();
    }

    private List<String> rolesOf(long userId) {
        return jdbc.queryForList("SELECT r.name FROM roles r JOIN user_roles ur ON ur.role_id = r.id "
                + "WHERE ur.user_id = ?", String.class, userId);
    }

    /** Notices are sent asynchronously after commit. */
    private void awaitNotifications(long recipient, String type, long expected) throws Exception {
        long found = 0;
        for (int attempt = 0; attempt < 100; attempt++) {
            found = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id = ? AND type = ?",
                    Long.class, recipient, type);
            if (found >= expected) {
                break;
            }
            Thread.sleep(50);
        }
        Thread.sleep(100); // a second notice, if there wrongly is one, shows up in this time
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id = ? AND type = ?",
                Long.class, recipient, type)).isEqualTo(expected);
    }

    private void awaitEntityNotification(long recipient, String type, long entityId) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id = ? AND type = ? "
                    + "AND entity_id = ?", Long.class, recipient, type, entityId) > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No " + type + " notification for entity " + entityId);
    }

    /** Runs the same call on two threads released together. */
    private <T> List<T> together(Callable<T> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private String htmlOf(MimeMessage message) throws Exception {
        return textOf(message.getContent());
    }

    private String textOf(Object content) throws Exception {
        if (content instanceof Multipart multipart) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                text.append(textOf(part.getContent()));
            }
            return text.toString();
        }
        return String.valueOf(content);
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
                .publishedAt(LocalDateTime.now())
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
}
