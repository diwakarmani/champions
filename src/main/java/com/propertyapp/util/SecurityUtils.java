package com.propertyapp.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Optional;

public final class SecurityUtils {
    
    private SecurityUtils() {}
    
    public static Optional<String> getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails) {
            return Optional.of(((UserDetails) principal).getUsername());
        }
        return Optional.empty();
    }
    
    public static Optional<Long> getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        
        Object principal = auth.getPrincipal();
        if (principal instanceof com.propertyapp.security.CustomUserDetails) {
            return Optional.of(((com.propertyapp.security.CustomUserDetails) principal).getId());
        }
        return Optional.empty();
    }
    
    public static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() 
            && !(auth.getPrincipal() instanceof String);
    }

    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;

        return auth.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(roleWithPrefix));
    }

    public static boolean hasAnyRole(String... roles) {
        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }
}