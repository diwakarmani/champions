package com.propertyapp.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.dto.property.PropertyImageDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** One name for the primary flag, both ways (the API schema once listed "primary" as well). */
class PropertyImageDTOJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void writesAndReadsOnlyIsPrimary() throws Exception {
        String json = mapper.writeValueAsString(PropertyImageDTO.builder().imageUrl("u").isPrimary(true).build());
        assertThat(json).contains("\"isPrimary\":true").doesNotContain("\"primary\"");
        assertThat(mapper.readValue("{\"imageUrl\":\"u\",\"isPrimary\":true}", PropertyImageDTO.class).isPrimary())
                .isTrue();
    }
}
