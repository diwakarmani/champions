package com.propertyapp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S13: requests no controller handles answered 500 "unexpected error". */
class UnknownRouteIntegrationTest extends AbstractPostgisIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void unknownPathIs404() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/api/public/no-such-thing")).andExpect(status().isNotFound());
    }

    @Test
    void wrongMethodIs405AndWrongBodyTypeIs415() throws Exception {
        mockMvc.perform(delete("/api/auth/register")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
