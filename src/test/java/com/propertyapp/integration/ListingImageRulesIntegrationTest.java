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
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static com.propertyapp.integration.Fixtures.authed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S11: the web listing editor adds photos through this route; web and mobile render the URLs. */
class ListingImageRulesIntegrationTest extends AbstractPostgisIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired PropertyTypeRepository propertyTypeRepository;
    @Autowired JdbcTemplate jdbc;

    Fixtures f;
    long listing;
    String token;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixtures(mockMvc, objectMapper, userRepository, propertyRepository, propertyTypeRepository, jdbc);
        User agent = f.user("REALTOR");
        listing = f.listing(agent, "DRAFT");
        token = f.login(agent.getEmail());
    }

    @Test
    void onlyPlainHttpAddressesThatFitTheColumnAreAccepted() throws Exception {
        add(Map.of("imageUrl", "javascript:alert(1)")).andExpect(status().isBadRequest());
        add(Map.of("imageUrl", "data:image/svg+xml,<svg onload=alert(1)>")).andExpect(status().isBadRequest());
        add(Map.of("imageUrl", "https://img.example/" + "a".repeat(260) + ".jpg")).andExpect(status().isBadRequest());
        add(Map.of("imageUrl", "https://img.example/ok.jpg", "thumbnailUrl", "javascript:x")).andExpect(status().isBadRequest());
        assertThat(count("")).isZero();
        add(Map.of("imageUrl", "https://img.example/ok.jpg")).andExpect(status().isCreated());
    }

    @Test
    void theFirstPhotoIsTheCoverAndASentIsPrimaryCannotMakeASecondCover() throws Exception {
        add(Map.of("imageUrl", "https://img.example/1.jpg", "isPrimary", false)).andExpect(status().isCreated());
        add(Map.of("imageUrl", "https://img.example/2.jpg", "isPrimary", true)).andExpect(status().isCreated());
        assertThat(count("")).isEqualTo(2);
        assertThat(count(" AND is_primary")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT image_url FROM property_images WHERE property_id = ? AND is_primary",
                String.class, listing)).endsWith("/1.jpg");
    }

    private ResultActions add(Map<String, Object> body) throws Exception {
        return mockMvc.perform(authed(post("/api/properties/" + listing + "/images"), token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private int count(String extra) {
        return jdbc.queryForObject("SELECT count(*) FROM property_images WHERE property_id = ?" + extra, Integer.class, listing);
    }
}
