package com.propertyapp.security.ratelimit;

import com.propertyapp.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimitInterceptorTest {

    private AuthRateLimitInterceptor interceptor(boolean enabled) {
        return new AuthRateLimitInterceptor(new InMemoryRateLimitStore(), new ClientIpResolver(""),
                enabled, 1, 1, 1, 1, 1, 1, 1);
    }

    private static MockHttpServletRequest req(String method, String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
        r.setRemoteAddr("203.0.113.9");
        return r;
    }

    @Test
    void secondLoginFromSameAddressIsRefused() {
        var i = interceptor(true);
        assertThat(i.preHandle(req("POST", "/api/auth/login-with-identifier"), new MockHttpServletResponse(), null)).isTrue();
        assertThatThrownBy(() -> i.preHandle(req("POST", "/api/auth/login-with-identifier"), new MockHttpServletResponse(), null))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void disabledGetAndUnknownPathsPass() {
        var off = interceptor(false);
        for (int n = 0; n < 3; n++) {
            assertThat(off.preHandle(req("POST", "/api/auth/register"), new MockHttpServletResponse(), null)).isTrue();
        }
        var on = interceptor(true);
        for (int n = 0; n < 3; n++) {
            assertThat(on.preHandle(req("GET", "/api/auth/login-with-identifier"), new MockHttpServletResponse(), null)).isTrue();
            assertThat(on.preHandle(req("POST", "/api/auth/refresh-token"), new MockHttpServletResponse(), null)).isTrue();
        }
    }

    @Test
    void forwardedForFromAnUntrustedPeerIsIgnored() {
        var i = interceptor(true);
        var a = req("POST", "/api/auth/register");
        a.addHeader("X-Forwarded-For", "1.1.1.1");
        var b = req("POST", "/api/auth/register");
        b.addHeader("X-Forwarded-For", "2.2.2.2");
        i.preHandle(a, new MockHttpServletResponse(), null);
        assertThatThrownBy(() -> i.preHandle(b, new MockHttpServletResponse(), null))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void registersEveryAuthRoute() {
        assertThat(interceptor(true).paths()).contains(
                "/api/auth/login-with-identifier", "/api/auth/register", "/api/auth/otp/send", "/api/auth/otp/resend",
                "/api/auth/otp/verify", "/api/auth/forgot-password", "/api/auth/resend-verification", "/api/auth/reset-password");
    }
}
