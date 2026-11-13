package com.propertyapp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Every route the application registers, called without a token (ADR-0006 decision 6). Each must be
 * listed in {@code access/anonymous-access.txt} as PUBLIC or AUTH, so a new route is never public by
 * accident. AUTH must answer 401; PUBLIC must not answer 401 or 403; nothing may answer 5xx.
 * After a deliberate change: {@code -Daccess.update=true}, then review the diff.
 */
class AnonymousAccessMatrixTest extends AbstractPostgisIntegrationTest {

    private static final Path EXPECTED = Path.of("src/test/resources/access/anonymous-access.txt");

    @Autowired MockMvc mockMvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyRouteIsClassifiedAndBehavesAsClassified() throws Exception {
        Map<String, String> observed = new TreeMap<>();
        List<String> wrong = new ArrayList<>();
        Map<String, String> expected = Files.exists(EXPECTED) ? read() : Map.of();

        for (String route : routes()) {
            String method = route.substring(0, route.indexOf(' '));
            String path = route.substring(route.indexOf(' ') + 1).replaceAll("\\{[^}]+}", "1");
            MockHttpServletResponse response = mockMvc.perform(Fixtures.from(
                            request(HttpMethod.valueOf(method), path)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"), Fixtures.freshIp()))
                    .andReturn().getResponse();
            int status = response.getStatus();
            String actual = status == 401 ? "AUTH" : (status == 403 ? "FORBIDDEN" : "PUBLIC");
            observed.put(route, actual);
            if (status >= 500) {
                wrong.add(route + ": anonymous call with an empty body answered " + status);
            }
            String classified = expected.get(route);
            if (classified != null && !classified.equals(actual)) {
                wrong.add(route + ": listed " + classified + ", anonymous call answered " + status);
            }
        }

        if (Boolean.getBoolean("access.update")) {
            Files.createDirectories(EXPECTED.getParent());
            Files.writeString(EXPECTED, observed.entrySet().stream()
                    .map(e -> e.getKey() + " " + e.getValue())
                    .collect(Collectors.joining("\n", "", "\n")));
            return;
        }

        assertThat(observed.keySet()).as("routes missing from or extra in " + EXPECTED)
                .containsExactlyInAnyOrderElementsOf(expected.keySet());
        assertThat(wrong).as("anonymous behaviour differs from the classification").isEmpty();
        assertThat(expected.values()).as("classifications").allMatch(Set.of("PUBLIC", "AUTH")::contains);
    }

    private List<String> routes() {
        List<String> routes = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, org.springframework.web.method.HandlerMethod> entry
                : handlerMapping.getHandlerMethods().entrySet()) {
            if (!entry.getValue().getBeanType().getPackageName().startsWith("com.propertyapp")) {
                continue;
            }
            RequestMappingInfo info = entry.getKey();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            assertThat(methods).as("every route names its HTTP method: " + info).isNotEmpty();
            for (String pattern : info.getPatternValues()) {
                for (RequestMethod method : methods) {
                    routes.add(method.name() + " " + pattern);
                }
            }
        }
        return routes;
    }

    private Map<String, String> read() throws Exception {
        Map<String, String> expected = new TreeMap<>();
        for (String line : Files.readAllLines(EXPECTED)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int last = line.lastIndexOf(' ');
            expected.put(line.substring(0, last), line.substring(last + 1));
        }
        return expected;
    }
}
