package com.propertyapp.util;

public final class Constants {
    
    private Constants() {}
    
    public static final String ROLE_BUYER = "BUYER";
    public static final String ROLE_SELLER = "SELLER";
    public static final String ROLE_REALTOR = "REALTOR";
    public static final String ROLE_REALTOR_GROUP_ADMIN = "REALTOR_GROUP_ADMIN";
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
    
    public static final String CACHE_PROPERTIES = "properties";
    public static final String CACHE_USERS = "users";
    public static final String CACHE_PROPERTY_TYPES = "propertyTypes";
    
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    // Property Status
    public static final String PROPERTY_STATUS_DRAFT = "DRAFT";
    public static final String PROPERTY_STATUS_PENDING = "PENDING_APPROVAL";
    public static final String PROPERTY_STATUS_ACTIVE = "ACTIVE";
    public static final String PROPERTY_STATUS_SOLD = "SOLD";
    public static final String PROPERTY_STATUS_RENTED = "RENTED";
    public static final String PROPERTY_STATUS_INACTIVE = "INACTIVE";

    // Listing Types
    public static final String LISTING_TYPE_SALE = "SALE";
    public static final String LISTING_TYPE_RENT = "RENT";
    public static final String LISTING_TYPE_LEASE = "LEASE";

    // Furnished Status
    public static final String FURNISHED = "FURNISHED";
    public static final String SEMI_FURNISHED = "SEMI_FURNISHED";
    public static final String UNFURNISHED = "UNFURNISHED";
}