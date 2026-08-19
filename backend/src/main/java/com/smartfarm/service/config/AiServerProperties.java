package com.smartfarm.service.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai-server")
public record AiServerProperties(
        String url,
        Duration connectTimeout,
        Duration readTimeout
) {
}
