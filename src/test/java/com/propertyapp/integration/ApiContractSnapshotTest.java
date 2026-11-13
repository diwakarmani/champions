package com.propertyapp.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API changes must be visible in review (ADR-0011). After an intended change:
 * {@code mvn test -Dtest=ApiContractSnapshotTest -Dcontract.update=true}, then commit contracts/openapi.json.
 */
class ApiContractSnapshotTest extends AbstractPostgisIntegrationTest {

    private static final Path SNAPSHOT = Path.of("contracts/openapi.json");

    @Autowired MockMvc mockMvc;

    @Test
    void committedContractMatchesTheRunningApi() throws Exception {
        String actual = normalise(mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        if (Boolean.getBoolean("contract.update")) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, actual);
        }

        assertThat(SNAPSHOT).as("run with -Dcontract.update=true to create it").exists();
        assertThat(Files.readString(SNAPSHOT))
                .as("API changed: review it, then run with -Dcontract.update=true and commit the file")
                .isEqualTo(actual);
    }

    private String normalise(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        JsonNode tree = mapper.readTree(json);
        // Tag order is not stable between runs and carries no meaning.
        if (tree.get("tags") instanceof ArrayNode tags) {
            List<JsonNode> sorted = new ArrayList<>();
            tags.forEach(sorted::add);
            sorted.sort(Comparator.comparing(t -> t.path("name").asText()));
            tags.removeAll();
            tags.addAll(sorted);
        }
        Object ordered = mapper.treeToValue(tree, Object.class);
        return mapper.writeValueAsString(ordered) + "\n";
    }
}
