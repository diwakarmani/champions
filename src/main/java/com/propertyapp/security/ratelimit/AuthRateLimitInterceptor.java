package com.propertyapp.security.ratelimit;

import com.propertyapp.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * Per-address limits on the sign-in, sign-up, code and password-reset routes (ADR-0008 Amendment 3).
 * These sit on top of the per-account lockout and the per-identifier code limit in {@code OtpService}:
 * those stop guessing one account, these stop one address from sweeping many, and the per-email limit on
 * the two routes that send mail stops anyone from flooding a stranger's inbox.
 * A refused request answers 429 with Retry-After through {@code GlobalExceptionHandler}.
 */
@Slf4j
@Component
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    /** One bucket per group; paths in the same group share the allowance. */
    record Limit(String group, int capacity, Duration window) { }

    private final RateLimitStore store;
    private final ClientIpResolver ipResolver;
    private final boolean enabled;
    private final Map<String, Limit> byPath;
    private final int mailPerEmailPerHour;

    public AuthRateLimitInterceptor(
            RateLimitStore store,
            ClientIpResolver ipResolver,
            @Value("${app.rate-limit.auth.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.auth.login-per-ip-per-15min:20}") int login,
            @Value("${app.rate-limit.auth.register-per-ip-per-hour:10}") int register,
            @Value("${app.rate-limit.auth.otp-send-per-ip-per-hour:10}") int otpSend,
            @Value("${app.rate-limit.auth.otp-verify-per-ip-per-15min:30}") int otpVerify,
            @Value("${app.rate-limit.auth.mail-per-ip-per-hour:5}") int mailPerIp,
            @Value("${app.rate-limit.auth.mail-per-email-per-hour:3}") int mailPerEmail,
            @Value("${app.rate-limit.auth.reset-per-ip-per-15min:10}") int reset) {
        this.store = store;
        this.ipResolver = ipResolver;
        this.enabled = enabled;
        this.mailPerEmailPerHour = mailPerEmail;
        Duration quarter = Duration.ofMinutes(15);
        Duration hour = Duration.ofHours(1);
        this.byPath = Map.of(
                "/api/auth/login-with-identifier", new Limit("login", login, quarter),
                "/api/auth/register", new Limit("register", register, hour),
                "/api/auth/otp/send", new Limit("otp-send", otpSend, hour),
                "/api/auth/otp/resend", new Limit("otp-send", otpSend, hour),
                "/api/auth/otp/verify", new Limit("otp-verify", otpVerify, quarter),
                "/api/auth/forgot-password", new Limit("mail", mailPerIp, hour),
                "/api/auth/resend-verification", new Limit("mail", mailPerIp, hour),
                "/api/auth/reset-password", new Limit("reset", reset, quarter));
    }

    /** The paths this interceptor must be registered for. */
    public java.util.Set<String> paths() {
        return byPath.keySet();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled || !"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Limit limit = byPath.get(request.getRequestURI());
        if (limit == null) {
            return true;
        }
        String ip = ipResolver.resolve(request);
        consume("auth:" + limit.group() + ":ip:" + ip, limit.capacity(), limit.window(), limit.group());

        if ("mail".equals(limit.group())) {
            String email = request.getParameter("email");
            if (email != null && !email.isBlank()) {
                consume("auth:mail:email:" + email.trim().toLowerCase(Locale.ROOT),
                        mailPerEmailPerHour, Duration.ofHours(1), "mail-per-email");
            }
        }
        return true;
    }

    private void consume(String key, int capacity, Duration window, String what) {
        long retryAfter = store.tryConsume(key, capacity, window);
        if (retryAfter > 0) {
            // The key holds an address or email: log only which limit fired.
            log.warn("Auth rate limit reached: {}", what);
            throw new RateLimitExceededException(retryAfter);
        }
    }
}
