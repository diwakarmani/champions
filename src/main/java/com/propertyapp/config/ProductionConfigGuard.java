package com.propertyapp.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuses to start production with values that are published in this public repository.
 * Runs during context start-up, before DataInitializer can seed anything.
 */
@Component
@Profile("prod")
public class ProductionConfigGuard implements InitializingBean {

    static final String PUBLISHED_JWT_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5971";
    static final String PUBLISHED_ADMIN_PASSWORD = "Admin@123";

    private final String jwtSecret;
    private final String adminPassword;
    private final String otpFixedCode;
    private final boolean seedDemoUsers;
    private final String corsAllowedOrigins;

    public ProductionConfigGuard(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${app.admin.password}") String adminPassword,
            @Value("${app.otp.dev-fixed-code:}") String otpFixedCode,
            @Value("${app.seed.demo-users:false}") boolean seedDemoUsers,
            @Value("${app.cors.allowed-origins:}") String corsAllowedOrigins) {
        this.jwtSecret = jwtSecret;
        this.adminPassword = adminPassword;
        this.otpFixedCode = otpFixedCode;
        this.seedDemoUsers = seedDemoUsers;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> problems = problems(jwtSecret, adminPassword, otpFixedCode, seedDemoUsers, corsAllowedOrigins);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Unsafe production configuration: " + String.join("; ", problems));
        }
    }

    /** Messages name the setting only, never its value. */
    static List<String> problems(String jwtSecret, String adminPassword, String otpFixedCode, boolean seedDemoUsers,
                                 String corsAllowedOrigins) {
        List<String> problems = new ArrayList<>();
        if (PUBLISHED_JWT_SECRET.equals(jwtSecret)) {
            problems.add("JWT_SECRET is the published default");
        }
        if (PUBLISHED_ADMIN_PASSWORD.equals(adminPassword)) {
            problems.add("ADMIN_PASSWORD is the published default");
        }
        if (otpFixedCode != null && !otpFixedCode.isBlank()) {
            problems.add("app.otp.dev-fixed-code must be empty");
        }
        if (seedDemoUsers) {
            problems.add("app.seed.demo-users must be false");
        }
        if (corsAllowedOrigins == null || corsAllowedOrigins.isBlank() || corsAllowedOrigins.contains("*")) {
            problems.add("CORS_ALLOWED_ORIGINS must list the allowed origins, not * or nothing");
        }
        return problems;
    }
}
