package com.propertyapp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.entity.user.User;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import com.propertyapp.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S12: price and bedroom filters on the public search that city pages use. */
class PublicSearchFiltersIntegrationTest extends AbstractPostgisIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired PropertyTypeRepository propertyTypeRepository;
    @Autowired JdbcTemplate jdbc;

    Fixtures f;
    long cheap2, mid3, dear5, hidden;

    @BeforeEach
    void setUp() {
        f = new Fixtures(mockMvc, objectMapper, userRepository, propertyRepository, propertyTypeRepository, jdbc);
        User agent = f.user("REALTOR");
        cheap2 = home(agent, 200_000, 2, "ACTIVE");
        mid3 = home(agent, 450_000, 3, "ACTIVE");
        dear5 = home(agent, 900_000, 5, "ACTIVE");
        hidden = home(agent, 450_000, 3, "DRAFT");
    }

    private long home(User agent, int price, int beds, String status) {
        long id = f.listing(agent, status);
        jdbc.update("UPDATE properties SET price = ?, bedrooms = ? WHERE id = ?", price, beds, id);
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions search(String query) throws Exception {
        return mockMvc.perform(get("/api/public/properties?listingType=SALE&city=" + f.city + query));
    }

    @Test
    void noFiltersGivesEveryPublicHomeInTheCity() throws Exception {
        search("").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    void priceRangeAndBedroomsNarrowTheResults() throws Exception {
        search("&minPrice=300000&maxPrice=1000000").andExpect(jsonPath("$.data.content[*].id",
                containsInAnyOrder((int) mid3, (int) dear5)));
        search("&maxPrice=450000").andExpect(jsonPath("$.data.content[*].id",
                containsInAnyOrder((int) cheap2, (int) mid3)));
        search("&minBeds=4").andExpect(jsonPath("$.data.content[*].id", containsInAnyOrder((int) dear5)));
        search("&minBeds=3&maxPrice=500000").andExpect(jsonPath("$.data.content[*].id",
                containsInAnyOrder((int) mid3)));
    }

    @Test
    void impossibleOrNegativeRangesAreRefused() throws Exception {
        search("&minPrice=500000&maxPrice=100000").andExpect(status().isBadRequest());
        search("&minPrice=-1").andExpect(status().isBadRequest());
        search("&minBeds=-2").andExpect(status().isBadRequest());
        search("&minPrice=abc").andExpect(status().isBadRequest());
    }
}
