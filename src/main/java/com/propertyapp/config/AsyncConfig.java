package com.propertyapp.config;

import com.propertyapp.dto.common.AsyncProperties;
import com.propertyapp.exception.GlobalAsyncExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(AsyncProperties.class)
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    private final AsyncProperties properties;

    public AsyncConfig(AsyncProperties properties) {
        this.properties = properties;
    }

    @Bean(name = "emailExecutor")
    public ThreadPoolTaskExecutor emailExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(properties.getEmail().getCorePoolSize());
        executor.setMaxPoolSize(properties.getEmail().getMaxPoolSize());
        executor.setQueueCapacity(properties.getEmail().getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getEmail().getKeepAliveSeconds());
        executor.setThreadNamePrefix(properties.getEmail().getThreadNamePrefix());

        // Important for correlationId propagation
        executor.setTaskDecorator(new MdcTaskDecorator());

        // Production safe rejection policy
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return emailExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new GlobalAsyncExceptionHandler();
    }
}
