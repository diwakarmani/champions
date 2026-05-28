package com.propertyapp.util;

import org.apache.commons.lang3.StringUtils;
import java.util.regex.Pattern;

public final class ValidationUtils {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^[+]?[(]?[0-9]{1,4}[)]?[-\\s.]?[(]?[0-9]{1,4}[)]?[-\\s.]?[0-9]{1,9}$"
    );

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,}$"
    );
    
    private ValidationUtils() {}
    
    public static boolean isValidEmail(String email) {
        return StringUtils.isNotBlank(email) && EMAIL_PATTERN.matcher(email).matches();
    }
    
    public static boolean isValidPhone(String phone) {
        return StringUtils.isNotBlank(phone) && PHONE_PATTERN.matcher(phone).matches();
    }
    
    public static boolean isValidPassword(String password) {
        return StringUtils.isNotBlank(password) && PASSWORD_PATTERN.matcher(password).matches();
    }
}