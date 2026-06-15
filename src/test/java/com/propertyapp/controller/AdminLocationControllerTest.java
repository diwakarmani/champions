package com.propertyapp.controller;

import com.propertyapp.service.locality.LocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminLocationControllerTest {

    private LocationService locationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        locationService = mock(LocationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminLocationController(locationService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void countryBootstrapConvertsExternalFailureToStable400Contract() throws Exception {
        doThrow(new RuntimeException("provider unavailable")).when(locationService).importCountry("Bhutan");
        mockMvc.perform(post("/api/admin/locations/bootstrap/country").param("name", "Bhutan"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Import failed: provider unavailable"));
    }

    @Test
    void stateAndCityBootstrapUseTheSameErrorContract() throws Exception {
        doThrow(new RuntimeException("states failed")).when(locationService).importStates(39L);
        doThrow(new RuntimeException("cities failed")).when(locationService).importCities(39L);

        mockMvc.perform(post("/api/admin/locations/bootstrap/39/states"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Import failed: states failed"));
        mockMvc.perform(post("/api/admin/locations/bootstrap/39/cities"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Import failed: cities failed"));
    }
}
