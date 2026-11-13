package com.propertyapp.config;

import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import com.propertyapp.security.JwtAuthenticationFilter;
import com.propertyapp.util.PasswordUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    private static final String[] PUBLIC_URLS = {
        "/api/auth/**",
        "/uploads/**",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/api-docs/**",
        "/actuator/health",
        "/actuator/info"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configure(http))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_URLS).permitAll()
                // Public API: every method, so guest contact POSTs work without a token (ADR-0006).
                .requestMatchers("/api/public/**").permitAll()
                // Must stay above the public GET wildcard below.
                .requestMatchers(HttpMethod.GET,
                        "/api/properties/my-listings",
                        "/api/properties/status/**"
                ).authenticated()
                .requestMatchers(HttpMethod.GET,
                        "/api/discovery/**",
                        "/api/properties/**",
                        "/api/property-types/**",
                        "/api/locations/**",
                        "/api/realtors/*/ratings"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .headers(headers -> headers
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=(), payment=()"))
                // The API serves JSON and images only; Swagger UI needs its own scripts, so it is left out.
                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                        new NegatedRequestMatcher(new AntPathRequestMatcher("/swagger-ui/**")),
                        new StaticHeadersWriter("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")))
            )
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, ex) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write(
                        "{\"success\":false,\"message\":\"Authentication required\",\"status\":401}");
                })
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        // Spring's default checks "enabled" before the password, so a wrong password on an unconfirmed
        // or deactivated account answered differently from one on a normal account (S12 review).
        // Only the lock is checked first (it is already public: AuthServiceImpl says so up front);
        // "enabled" is checked once the password is known to be right.
        provider.setPreAuthenticationChecks(user -> {
            if (!user.isAccountNonLocked()) {
                throw new org.springframework.security.authentication.LockedException("Account is locked");
            }
        });
        provider.setPostAuthenticationChecks(user -> {
            if (!user.isEnabled()) {
                throw new org.springframework.security.authentication.DisabledException("Account is disabled");
            }
        });
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordUtils.getEncoder();
    }
}
