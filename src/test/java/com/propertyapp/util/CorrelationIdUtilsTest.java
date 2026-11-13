package com.propertyapp.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** S20 security pass: a caller's correlation id reaches every log line, so only plain short ids pass. */
class CorrelationIdUtilsTest {

    @Test
    void keepsAPlainIdFromTheClient() {
        assertThat(CorrelationIdUtils.fromHeader("web-3f2a9c_01")).isEqualTo("web-3f2a9c_01");
        String uuid = java.util.UUID.randomUUID().toString();
        assertThat(CorrelationIdUtils.fromHeader(uuid)).isEqualTo(uuid);
    }

    @Test
    void replacesMissingLongOrFreeTextIds() {
        for (String bad : new String[]{null, "", "a".repeat(65), "x y", "id\" fake=1", "évil", "a\tb"}) {
            String id = CorrelationIdUtils.fromHeader(bad);
            assertThat(id).as(String.valueOf(bad)).isNotEqualTo(bad).matches("[0-9a-f-]{36}");
        }
    }
}
