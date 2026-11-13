package com.propertyapp.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import com.propertyapp.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static com.propertyapp.integration.Fixtures.authed;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Only agents and admins may store photos (ADR-0016 Amendment 1, S14). */
class UploadAccessIntegrationTest extends AbstractPostgisIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired PropertyTypeRepository propertyTypeRepository;
    @Autowired JdbcTemplate jdbc;

    Fixtures f;
    MockMultipartFile png;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixtures(mockMvc, objectMapper, userRepository, propertyRepository, propertyTypeRepository, jdbc);
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", out);
        png = new MockMultipartFile("file", "house.png", "image/png", out.toByteArray());
    }

    private MockHttpServletRequestBuilder upload() {
        return multipart("/api/upload/image").file(png);
    }

    @Test
    void aBuyerCannotStorePhotos() throws Exception {
        mockMvc.perform(authed(upload(), f.login(f.user("BUYER").getEmail()))).andExpect(status().isForbidden());
    }

    @Test
    void agentsAndAdminsCan() throws Exception {
        mockMvc.perform(authed(upload(), f.login(f.user("REALTOR").getEmail()))).andExpect(status().isOk());
        mockMvc.perform(authed(upload(), f.login(f.user("SUPER_ADMIN").getEmail()))).andExpect(status().isOk());
    }

    @Test
    void signedOutIsRefused() throws Exception {
        mockMvc.perform(upload()).andExpect(status().isUnauthorized());
    }
}
