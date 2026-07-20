package com.propertyapp.util;

import com.propertyapp.enums.ClientType;
import jakarta.servlet.http.HttpServletRequest;

public final class ClientTypeResolver {

    public static final String HEADER = "X-Client-Type";

    private ClientTypeResolver() {}

    // Fails safe to WEB on a missing/unrecognized header so any caller that doesn't
    // explicitly identify as MOBILE keeps today's refresh-token lifetime unchanged.
    public static ClientType resolve(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.trim().equalsIgnoreCase("MOBILE")) {
            return ClientType.MOBILE;
        }
        return ClientType.WEB;
    }
}
