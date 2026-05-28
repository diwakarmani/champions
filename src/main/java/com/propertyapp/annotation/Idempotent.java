package com.propertyapp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that should be idempotent
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    
    /**
     * Whether idempotency key is required
     */
    boolean required() default false;
    
    /**
     * Expiration time for idempotency key in seconds
     */
    long expirationSeconds() default 3600; // 1 hour default
}