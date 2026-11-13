package com.propertyapp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.entity.user.User;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import com.propertyapp.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S12: profile edits the web now makes, and the values the backend now refuses. */
class ProfileUpdateIntegrationTest extends AbstractPostgisIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired PropertyTypeRepository propertyTypeRepository;
    @Autowired JdbcTemplate jdbc;

    User buyer;
    String token;

    @BeforeEach
    void setUp() throws Exception {
        Fixtures f = new Fixtures(mockMvc, objectMapper, userRepository, propertyRepository, propertyTypeRepository, jdbc);
        buyer = f.user("BUYER");
        token = f.login(buyer.getEmail());
    }

    private org.springframework.test.web.servlet.ResultActions update(Map<String, Object> body) throws Exception {
        return mockMvc.perform(Fixtures.authed(put("/api/users/me"), token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    @Test
    void namesAndWebsiteAreSaved() throws Exception {
        update(Map.of("firstName", "Ada", "lastName", "Lovelace", "website", "https://ada.example.com/about"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Ada"))
                .andExpect(jsonPath("$.data.website").value("https://ada.example.com/about"));
        update(Map.of("website", "ada.example.com")).andExpect(status().isOk());
        update(Map.of("website", "")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "data:text/html,hi", "not a site", "https://a.example.com/<script>"})
    void scriptAndJunkWebsitesAreRefused(String website) throws Exception {
        update(Map.of("website", website)).andExpect(status().isBadRequest());
    }

    @Test
    void photoMustBeHttpAndValuesMustFitTheColumns() throws Exception {
        update(Map.of("profileImageUrl", "javascript:alert(1)")).andExpect(status().isBadRequest());
        update(Map.of("profileImageUrl", "https://cdn.example.com/" + "a".repeat(240))).andExpect(status().isBadRequest());
        update(Map.of("occupation", "x".repeat(101))).andExpect(status().isBadRequest());
        update(Map.of("gender", "x".repeat(11))).andExpect(status().isBadRequest());
        update(Map.of("bio", "x".repeat(2001))).andExpect(status().isBadRequest());
        update(Map.of("firstName", "   ")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT first_name FROM users WHERE id = ?", String.class, buyer.getId()))
                .isEqualTo("Test");
    }
}
