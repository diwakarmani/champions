package com.propertyapp.aspect;

import com.propertyapp.util.CorrelationIdUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Logs controller calls. Arguments are logged by type only, with numbers, booleans and enums as
 * values: request bodies carry passwords, one-time codes, tokens and guests' contact details, and
 * none of that may reach the log file.
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
                correlationId, className, methodName, describe(joinPoint.getArgs()));
        
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

    /** Ids and flags help tracing; anything that can hold text is reduced to its type name. */
    static String describe(Object[] args) {
        if (args == null) {
            return "[]";
        }
        return Arrays.stream(args).map(LoggingAspect::describeOne).collect(Collectors.joining(", ", "[", "]"));
    }

    private static String describeOne(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (arg instanceof Number || arg instanceof Boolean || arg instanceof Enum<?>) {
            return String.valueOf(arg);
        }
        return arg.getClass().getSimpleName();
    }
}
