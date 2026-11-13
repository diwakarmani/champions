package com.propertyapp.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The address a limit is counted against. {@code X-Forwarded-For} is trusted only when the request
 * came from a configured proxy, otherwise anyone could pick their own address (ADR-0007).
 */
@Component
public class ClientIpResolver {

    private final Set<String> trustedProxies;

    public ClientIpResolver(@Value("${app.security.trusted-proxies:}") String trustedProxies) {
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!trustedProxies.contains(remote)) {
            return remote;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return remote;
        }
        // Right to left: the first address no trusted proxy vouches for is the client.
        List<String> hops = Arrays.stream(forwarded.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        for (int i = hops.size() - 1; i >= 0; i--) {
            if (!trustedProxies.contains(hops.get(i))) {
                return hops.get(i);
            }
        }
        return hops.isEmpty() ? remote : hops.get(0);
    }
}
