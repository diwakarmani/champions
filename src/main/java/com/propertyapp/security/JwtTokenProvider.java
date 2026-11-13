package com.propertyapp.security;

import com.propertyapp.config.JwtConfig;
import com.propertyapp.enums.ClientType;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {
    
    static final String USER_ID_CLAIM = "uid";
    /**
     * "access" or "refresh" (S20). Without it a refresh token (90 days on mobile) also worked as a
     * bearer token on every request, and an access token could renew a session.
     */
    static final String TYPE_CLAIM = "typ";
    static final String ACCESS = "access";
    static final String REFRESH = "refresh";

    private final JwtConfig jwtConfig;
    
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }
    
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put(TYPE_CLAIM, ACCESS);
        return buildToken(claims, userDetails, jwtConfig.getExpiration());
    }
    
    public String generateRefreshToken(UserDetails userDetails) {
        return generateRefreshToken(userDetails, ClientType.WEB);
    }

    // Mobile gets a long-lived refresh token (fewer forced re-logins); web keeps today's
    // shorter lifetime unchanged. Callers that don't pass a ClientType (above) default to
    // WEB, so any caller unaware of this distinction sees no behavior change.
    public String generateRefreshToken(UserDetails userDetails, ClientType clientType) {
        long expiration = clientType == ClientType.MOBILE
                ? jwtConfig.getRefreshExpirationMobile()
                : jwtConfig.getRefreshExpiration();
        Map<String, Object> claims = new HashMap<>();
        claims.put(TYPE_CLAIM, REFRESH);
        return buildToken(claims, userDetails, expiration);
    }
    
    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration
    ) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        // Ties the token to one account row, so a deleted account's tokens cannot open a new
        // account that reuses the email (ADR-0008 Amendment 1).
        if (userDetails instanceof CustomUserDetails custom && custom.getId() != null) {
            claims.put(USER_ID_CLAIM, custom.getId());
        }
        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey())
                .compact();
    }
    
    /** A token that may authorise a request. A refresh token may not (S20). */
    public boolean isAccessTokenValid(String token, UserDetails userDetails) {
        return isTokenValid(token, userDetails, ACCESS);
    }

    /** A token that may renew a session. An access token may not (S20). */
    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        return isTokenValid(token, userDetails, REFRESH);
    }

    private boolean isTokenValid(String token, UserDetails userDetails, String type) {
        try {
            final Claims claims = extractAllClaims(token);
            if (!claims.getSubject().equals(userDetails.getUsername()) || claims.getExpiration().before(new Date())) {
                return false;
            }
            // Tokens from before S20 carry no type and are refused: one sign-in after the deploy.
            if (!type.equals(claims.get(TYPE_CLAIM))) {
                return false;
            }
            if (userDetails instanceof CustomUserDetails custom) {
                // Tokens from before S4 carry no id and are refused: one sign-in after the deploy.
                Object uid = claims.get(USER_ID_CLAIM);
                return uid instanceof Number number && number.longValue() == custom.getId();
            }
            return true;
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
    
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .clockSkewSeconds(60)
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtConfig.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}