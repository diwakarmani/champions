package com.propertyapp.aspect;

import com.propertyapp.util.CorrelationIdUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Aspect for logging controller and service method executions
 */
@Aspect
@Component
@Slf4j
public class LoggingAspect {
    
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerMethods() {}
    
    @Pointcut("within(@org.springframework.stereotype.Service *)")
    public void serviceMethods() {}
    
    @Pointcut("@annotation(com.propertyapp.annotation.Loggable)")
    public void loggableMethod() {}
    
    @Around("controllerMethods() || loggableMethod()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        String correlationId = CorrelationIdUtils.get();
        
        log.info("[{}] Executing {}.{}() with arguments: {}", 
                correlationId, className, methodName, Arrays.toString(joinPoint.getArgs()));
        
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("[{}] Completed {}.{}() in {}ms", 
                    correlationId, className, methodName, executionTime);
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("[{}] Error in {}.{}() after {}ms: {}", 
                    correlationId, className, methodName, executionTime, e.getMessage(), e);
            throw e;
        }
    }
}