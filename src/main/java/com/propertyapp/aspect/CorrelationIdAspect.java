package com.propertyapp.aspect;

import com.propertyapp.util.CorrelationIdUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * Aspect to ensure correlation ID is set for all service methods
 */
@Aspect
@Component
@Slf4j
public class CorrelationIdAspect {
    
    @Pointcut("within(@org.springframework.stereotype.Service *)")
    public void serviceMethods() {}
    
    @Around("serviceMethods()")
    public Object ensureCorrelationId(ProceedingJoinPoint joinPoint) throws Throwable {
        // Ensure correlation ID exists
        String correlationId = CorrelationIdUtils.get();
        
        try {
            return joinPoint.proceed();
        } finally {
            // Correlation ID cleanup is handled by filter
        }
    }
}