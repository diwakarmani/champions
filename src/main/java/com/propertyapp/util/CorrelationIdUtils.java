package com.propertyapp.util;

import org.slf4j.MDC;
import java.util.UUID;

public final class CorrelationIdUtils {
    
    public static final String KEY = "correlationId";
    
    private CorrelationIdUtils() {}
    
    private static final java.util.regex.Pattern SAFE = java.util.regex.Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /**
     * The id a client sent, if it is short and plain; otherwise a new one (S20). It goes into every log
     * line and back in the response, so free text from a caller must not.
     */
    public static String fromHeader(String header) {
        return header != null && SAFE.matcher(header).matches() ? header : generate();
    }

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