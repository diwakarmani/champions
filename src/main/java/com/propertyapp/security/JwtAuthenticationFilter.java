package com.propertyapp.security;

import com.propertyapp.config.JwtConfig;
import com.propertyapp.util.CorrelationIdUtils;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final JwtConfig jwtConfig;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Correlation ID handling
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId == null) {
            correlationId = CorrelationIdUtils.generate();
        }
        CorrelationIdUtils.set(correlationId);
        response.setHeader("X-Correlation-Id", correlationId);

        try {

            final String authHeader = request.getHeader(jwtConfig.getHeader());

            if (authHeader == null || !authHeader.startsWith(jwtConfig.getPrefix())) {
                filterChain.doFilter(request, response);
                return;
            }

            final String jwt = authHeader.substring(jwtConfig.getPrefix().length());

            String userEmail = null;

            try {
                userEmail = jwtTokenProvider.extractUsername(jwt);
            } catch (ExpiredJwtException ex) {
                log.warn("JWT token expired: {}", ex.getMessage());
            } catch (JwtException ex) {
                log.warn("Invalid JWT token: {}", ex.getMessage());
            }

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                if (jwtTokenProvider.isTokenValid(jwt, userDetails)) {

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            CorrelationIdUtils.clear();
        }
    }
}