package com.propertyapp.aspect;

import com.propertyapp.annotation.Idempotent;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Aspect to handle idempotency for annotated methods
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyAspect {
    
    private final CacheManager cacheManager;
    private static final String IDEMPOTENCY_CACHE = "idempotency";
    
    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            // Not in request context, proceed normally
            return joinPoint.proceed();
        }
        
        String idempotencyKey = attributes.getRequest().getHeader(Constants.IDEMPOTENCY_KEY_HEADER);
        
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            if (idempotent.required()) {
                throw new BadRequestException("Idempotency key is required for this operation");
            }
            // Key not required, proceed normally
            return joinPoint.proceed();
        }
        
        Cache cache = cacheManager.getCache(IDEMPOTENCY_CACHE);
        if (cache == null) {
            log.warn("Idempotency cache not configured, proceeding without idempotency check");
            return joinPoint.proceed();
        }
        
        // Check if this key was already processed
        Cache.ValueWrapper existingResult = cache.get(idempotencyKey);
        if (existingResult != null) {
            log.info("Idempotency key {} already processed, returning cached result", idempotencyKey);
            return existingResult.get();
        }
        
        // Process the request
        Object result = joinPoint.proceed();
        
        // Store the result
        cache.put(idempotencyKey, result);
        log.info("Stored result for idempotency key: {}", idempotencyKey);
        
        return result;
    }
}