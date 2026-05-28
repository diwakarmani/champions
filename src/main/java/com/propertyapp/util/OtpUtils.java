package com.propertyapp.util;

import java.security.SecureRandom;
import java.time.LocalDateTime;

public final class OtpUtils {
    
    private static final SecureRandom RANDOM = new SecureRandom();
    
    private OtpUtils() {}
    
    public static String generate(int length) {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < length; i++) {
            otp.append(RANDOM.nextInt(10));
        }
        return otp.toString();
    }
    
    public static String generate() {
        return generate(6);
    }
    
    public static LocalDateTime getExpiry(int minutes) {
        return LocalDateTime.now().plusMinutes(minutes);
    }
    
    public static boolean isExpired(LocalDateTime expiry) {
        return LocalDateTime.now().isAfter(expiry);
    }
}