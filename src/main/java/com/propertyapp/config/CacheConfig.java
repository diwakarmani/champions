package com.propertyapp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
            "properties",
            "users",
            "propertyTypes",
            "roles",
            "compareResults"
        );
        // Expire 60 s after write — ensures DB edits are visible within 1 refresh cycle
        // even when the update bypasses the service layer (direct SQL / admin tools).
        manager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(1000));
        return manager;
    }
}