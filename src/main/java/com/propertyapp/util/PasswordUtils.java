package com.propertyapp.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class PasswordUtils {
    
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(12);
    
    private PasswordUtils() {}
    
    public static String hash(String plain) {
        return ENCODER.encode(plain);
    }
    
    public static boolean matches(String plain, String hashed) {
        return ENCODER.matches(plain, hashed);
    }
    
    public static PasswordEncoder getEncoder() {
        return ENCODER;
    }
}