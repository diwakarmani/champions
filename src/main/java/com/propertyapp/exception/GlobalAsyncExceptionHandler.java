package com.propertyapp.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;

@Slf4j
public class GlobalAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    @Override
    public void handleUncaughtException(
            Throwable ex,
            Method method,
            Object... params
    ) {

        log.error("Async error in method: {}", method.getName(), ex);

        // OPTIONAL PRODUCTION ADVANCED:
        // 1. Persist to DB (email failure table)
        // 2. Send alert to monitoring system
        // 3. Publish domain event
    }
}
