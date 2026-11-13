package com.propertyapp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.user.User;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.util.PasswordUtils;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Users, listings and sign-in for integration tests. Every name is unique, so tests never collide. */
final class Fixtures {

    static final String PASSWORD = "Str0ng@Passw0rd";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyTypeRepository propertyTypeRepository;
    private final JdbcTemplate jdbc;
    final String city = "City" + UUID.randomUUID().toString().substring(0, 8);

    Fixtures(MockMvc mockMvc, ObjectMapper objectMapper, UserRepository userRepository,
             PropertyRepository propertyRepository, PropertyTypeRepository propertyTypeRepository, JdbcTemplate jdbc) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
        this.propertyTypeRepository = propertyTypeRepository;
        this.jdbc = jdbc;
    }

    /** A fresh client address, so rate limits from one test never reach another. */
    static String freshIp() {
        int n = NEXT_IP.getAndIncrement();
        return "10." + ((n >> 16) & 255) + "." + ((n >> 8) & 255) + "." + (n & 255);
    }

    static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request, String ip) {
        return request.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }

    static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    User user(String roleName) {
        return user(roleName, null);
    }

    User user(String roleName, String phone) {
        User created = userRepository.saveAndFlush(User.builder()
                .email(roleName.toLowerCase() + "-" + UUID.randomUUID() + "@example.com")
                .phone(phone)
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

    long listing(User owner, String status) {
        return propertyRepository.saveAndFlush(Property.builder()
                .title("Sunny Café Home " + UUID.randomUUID().toString().substring(0, 6))
                .description("A pleasant home close to everything you need.")
                .owner(owner)
                .propertyType(propertyTypeRepository.findAll().get(0))
                .listingType("SALE")
                .price(BigDecimal.valueOf(500_000))
                .addressLine1("1 Test St")
                .city(city)
                .state("Texas")
                .country("USA")
                .status(status)
                .viewCount(0)
                .inquiryCount(0)
                .contactCount(0)
                .publishedAt(LocalDateTime.now())
                .build()).getId();
    }

    String login(String identifier) throws Exception {
        return tokens(identifier).get("accessToken");
    }

    Map<String, String> tokens(String identifier) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login-with-identifier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("identifier", identifier, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var data = objectMapper.readTree(response).at("/data");
        assertThat(data.path("accessToken").asText()).isNotBlank();
        return Map.of("accessToken", data.path("accessToken").asText(),
                "refreshToken", data.path("refreshToken").asText());
    }

    void suspend(User agent) {
        jdbc.update("UPDATE users SET agent_suspended_at = now(), agent_suspension_reason = 'test' WHERE id = ?",
                agent.getId());
    }

    long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
