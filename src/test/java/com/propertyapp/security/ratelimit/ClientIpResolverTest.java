package com.propertyapp.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void forwardedHeadersAreIgnoredUnlessTheCallerIsATrustedProxy() {
        ClientIpResolver resolver = new ClientIpResolver("");
        assertThat(resolver.resolve(request("203.0.113.9", "198.51.100.1"))).isEqualTo("203.0.113.9");
    }

    @Test
    void behindTrustedProxiesTheRightmostUntrustedHopIsTheClient() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1, 10.0.0.2");
        // The client may forge the left part; only what our proxies appended counts.
        assertThat(resolver.resolve(request("10.0.0.1", "6.6.6.6, 198.51.100.7, 10.0.0.2"))).isEqualTo("198.51.100.7");
        assertThat(resolver.resolve(request("10.0.0.1", null))).isEqualTo("10.0.0.1");
        assertThat(resolver.resolve(request("10.0.0.1", "10.0.0.2"))).isEqualTo("10.0.0.2");
    }

    @Test
    void theStoreAllowsTheCapacityThenSaysHowLongToWait() {
        InMemoryRateLimitStore store = new InMemoryRateLimitStore();
        for (int i = 0; i < 3; i++) {
            assertThat(store.tryConsume("k", 3, Duration.ofHours(1))).isZero();
        }
        long wait = store.tryConsume("k", 3, Duration.ofHours(1));
        assertThat(wait).isBetween(1L, 3600L);
        assertThat(store.tryConsume("other", 3, Duration.ofHours(1))).isZero();
    }

    private static MockHttpServletRequest request(String remote, String forwarded) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        if (forwarded != null) {
            request.addHeader("X-Forwarded-For", forwarded);
        }
        return request;
    }
}
