package com.app.footballprediction.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Configuration for Football Data API client.
 * <p>
 * Free tier limits:
 * - 10 requests per minute
 * - Premier League (PL), Bundesliga (BL1), Serie A (SA), La Liga (PD), Ligue 1 (FL1)
 * <p>
 * Includes correlation ID propagation: X-Request-ID, X-Trace-ID, and X-Span-ID
 * headers from the incoming request MDC are forwarded to outgoing API calls
 * for end-to-end distributed tracing.
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
                .filter(correlationIdPropagationFilter())
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(5 * 1024 * 1024)) // 5MB buffer for large responses
                .build();
    }

    /**
     * WebClient filter that propagates correlation IDs from the current MDC
     * to outgoing HTTP requests for end-to-end tracing.
     * <p>
     * Propagated headers: X-Request-ID, X-Trace-ID, X-Span-ID.
     */
    private ExchangeFilterFunction correlationIdPropagationFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            ClientRequest.Builder builder = ClientRequest.from(clientRequest);

            String requestId = MDC.get("requestId");
            if (requestId != null && !requestId.isBlank()) {
                builder.header("X-Request-ID", requestId);
            }

            String traceId = MDC.get("traceId");
            if (traceId != null && !traceId.isBlank()) {
                builder.header("X-Trace-ID", traceId);
            }

            String spanId = MDC.get("spanId");
            if (spanId != null && !spanId.isBlank()) {
                builder.header("X-Span-ID", spanId);
            }

            return Mono.just(builder.build());
        });
    }
}
