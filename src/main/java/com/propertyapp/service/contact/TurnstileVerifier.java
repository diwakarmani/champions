package com.propertyapp.service.contact;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cloudflare Turnstile check for guest contact from the web. Off unless {@code app.turnstile.enabled};
 * mobile does not send a token, so the mobile path relies on limits, honeypot and dedupe (ADR-0007).
 */
@Component
@Slf4j
public class TurnstileVerifier {

    static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final boolean enabled;
    private final String secret;
    private final RestClient restClient;

    @Autowired
    public TurnstileVerifier(
            @Value("${app.turnstile.enabled:false}") boolean enabled,
            @Value("${app.turnstile.secret:}") String secret,
            RestClient.Builder builder) {
        this(enabled, secret, builder.requestFactory(shortTimeouts()).build());
    }

    TurnstileVerifier(boolean enabled, String secret, RestClient restClient) {
        this.enabled = enabled;
        this.secret = secret;
        this.restClient = restClient;
    }

    private static SimpleClientHttpRequestFactory shortTimeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return factory;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** True when the check is off, or Cloudflare confirms the token. Any failure counts as not human. */
    public boolean verify(String token, String clientIp) {
        if (!enabled) {
            return true;
        }
        if (!StringUtils.hasText(token) || !StringUtils.hasText(secret)) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token);
        form.add("remoteip", clientIp);
        try {
            JsonNode body = restClient.post()
                    .uri(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            return body != null && body.path("success").asBoolean(false);
        } catch (RestClientException e) {
            log.warn("Turnstile check failed: {}", e.getMessage());
            return false;
        }
    }
}
