package com.propertyapp.config;

import org.junit.jupiter.api.Test;

import static com.propertyapp.config.ProductionConfigGuard.PUBLISHED_ADMIN_PASSWORD;
import static com.propertyapp.config.ProductionConfigGuard.PUBLISHED_JWT_SECRET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigGuardTest {

    @Test
    void safeConfigurationStarts() {
        assertThatCode(new ProductionConfigGuard("a-private-secret", "a-private-password", "", false, "https://app.example.com")::afterPropertiesSet)
                .doesNotThrowAnyException();
    }

    @Test
    void eachPublishedOrDevelopmentValueBlocksStartUp() {
        assertThat(ProductionConfigGuard.problems(PUBLISHED_JWT_SECRET, "ok", "", false, "https://app.example.com")).hasSize(1);
        assertThat(ProductionConfigGuard.problems("ok", PUBLISHED_ADMIN_PASSWORD, "", false, "https://app.example.com")).hasSize(1);
        assertThat(ProductionConfigGuard.problems("ok", "ok", "123456", false, "https://app.example.com")).hasSize(1);
        assertThat(ProductionConfigGuard.problems("ok", "ok", "", true, "https://app.example.com")).hasSize(1);
        assertThat(ProductionConfigGuard.problems("ok", "ok", "", false, "*")).hasSize(1);
        assertThat(ProductionConfigGuard.problems("ok", "ok", "", false, "https://a.example.com,*")).hasSize(1);
        assertThat(ProductionConfigGuard.problems("ok", "ok", "", false, "")).hasSize(1);
    }

    @Test
    void failureNamesTheSettingButNeverPrintsTheSecretValue() {
        assertThatThrownBy(new ProductionConfigGuard(PUBLISHED_JWT_SECRET, "ok", "", false, "https://app.example.com")::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageNotContaining(PUBLISHED_JWT_SECRET);
    }

    @Test
    void publishedSecretConstantMatchesTheDefaultInApplicationYaml() throws Exception {
        try (var yaml = getClass().getResourceAsStream("/application.yaml")) {
            String text = new String(yaml.readAllBytes());
            assertThat(text).contains(PUBLISHED_JWT_SECRET).contains(PUBLISHED_ADMIN_PASSWORD);
        }
    }
}
