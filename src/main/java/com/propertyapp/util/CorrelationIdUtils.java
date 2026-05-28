package com.propertyapp.util;

import org.slf4j.MDC;
import java.util.UUID;

public final class CorrelationIdUtils {
    
    public static final String KEY = "correlationId";
    
    private CorrelationIdUtils() {}
    
    public static String generate() {
        return UUID.randomUUID().toString();
    }
    
    public static void set(String id) {
        MDC.put(KEY, id);
    }
    
    public static String get() {
        String id = MDC.get(KEY);
        if (id == null) {
            id = generate();
            set(id);
        }
        return id;
    }
    
    public static void clear() {
        MDC.remove(KEY);
    }
}