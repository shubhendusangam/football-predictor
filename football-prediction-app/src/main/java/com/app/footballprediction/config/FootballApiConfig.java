package com.app.footballprediction.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for Football Data API client.
 *
 * Free tier limits:
 * - 10 requests per minute
 * - Premier League (PL), Bundesliga (BL1), Serie A (SA), La Liga (PD), Ligue 1 (FL1)
 *
 * @see <a href="https://www.football-data.org/documentation/quickstart">API Documentation</a>
 */
@Configuration
public class FootballApiConfig {

    @Value("${football.api.base-url:https://api.football-data.org/v4}")
    private String baseUrl;

    @Value("${football.api.key:}")
    private String apiKey;

    @Bean
    public WebClient footballApiClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Auth-Token", apiKey)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(5 * 1024 * 1024)) // 5MB buffer for large responses
                .build();
    }
}

