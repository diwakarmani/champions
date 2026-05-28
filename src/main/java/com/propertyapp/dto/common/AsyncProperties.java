package com.propertyapp.dto.common;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.async")
@Getter
@Setter
public class AsyncProperties {

    private Email email = new Email();

    @Getter
    @Setter
    public static class Email {
        private int corePoolSize;
        private int maxPoolSize;
        private int queueCapacity;
        private int keepAliveSeconds;
        private String threadNamePrefix;
    }
}
