package com.propertyapp.exception;

import com.propertyapp.dto.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug 6 — {@code GlobalExceptionHandler} previously imported {@code org.apache.http.auth.AuthenticationException}
 * (an Apache HttpClient class) instead of {@code org.springframework.security.core.AuthenticationException},
 * so its {@code @ExceptionHandler} could never actually match a real Spring Security authentication failure
 * (e.g. {@link BadCredentialsException}, thrown by {@code AuthenticationManager.authenticate(...)}).
 * This test uses a genuine Spring Security exception to prove the handler now compiles against and
 * correctly handles the right exception hierarchy.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesRealSpringSecurityAuthenticationExceptionWith401() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/auth/login-with-identifier");

        ResponseEntity<ErrorResponse> response =
                handler.handleAuthenticationException(new BadCredentialsException("Bad credentials"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError()).isEqualTo("Authentication Failed");
        assertThat(response.getBody().getStatus()).isEqualTo(401);
        assertThat(response.getBody().getPath()).isEqualTo("/api/auth/login-with-identifier");
    }
}
