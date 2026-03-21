package com.app.footballprediction.client;

import com.app.common.dto.InjuryApiResponse;
import com.app.common.exception.ApiQuotaExceededException;
import com.app.footballprediction.ratelimit.ApiFootballRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;

/**
 * WebClient wrapper for API-Football (api-sports.io).
 * <p>
 * Every outgoing request:
 * <ol>
 *   <li>Checks the daily quota via {@link ApiFootballRateLimiter}</li>
 *   <li>Sends the request with the x-apisports-key header</li>
 *   <li>Reads the x-ratelimit-requests-remaining response header to sync the rate limiter</li>
 * </ol>
 */
@Component
@Slf4j
public class ApiFootballClient {

    private final WebClient webClient;
    private final ApiFootballRateLimiter rateLimiter;

    public ApiFootballClient(
            ApiFootballRateLimiter rateLimiter,
            @Value("${apifootball.api.base-url:https://v3.football.api-sports.io}") String baseUrl,
            @Value("${apifootball.api.key:demo}") String apiKey) {

        this.rateLimiter = rateLimiter;

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(10))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000);

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-apisports-key", apiKey)
                .defaultHeader("Accept", "application/json")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(rateLimitResponseFilter())
                .build();

        log.info("ApiFootballClient initialised: baseUrl={}", baseUrl);
    }

    /**
     * Fetch injuries for a specific fixture.
     */
    public Mono<InjuryApiResponse> getInjuriesByFixture(long fixtureId) {
        if (!rateLimiter.tryConsume(1)) {
            return Mono.error(new ApiQuotaExceededException("Daily API-Football quota exhausted"));
        }

        log.debug("API-Football request: GET /injuries?fixture={}", fixtureId);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/injuries")
                        .queryParam("fixture", fixtureId)
                        .build())
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException(
                                        "API-Football 4xx error for fixture " + fixtureId + ": " + body))))
                .bodyToMono(InjuryApiResponse.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorReturn(buildFallbackResponse());
    }

    /**
     * Fetch injuries for a team in a specific season.
     */
    public Mono<InjuryApiResponse> getInjuriesByTeam(int teamId, int season) {
        if (!rateLimiter.tryConsume(1)) {
            return Mono.error(new ApiQuotaExceededException("Daily API-Football quota exhausted"));
        }

        log.debug("API-Football request: GET /injuries?team={}&season={}&league=39", teamId, season);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/injuries")
                        .queryParam("team", teamId)
                        .queryParam("season", season)
                        .queryParam("league", 39) // Premier League
                        .build())
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException(
                                        "API-Football 4xx error for team " + teamId + ": " + body))))
                .bodyToMono(InjuryApiResponse.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorReturn(buildFallbackResponse());
    }

    /**
     * Exchange filter that reads the x-ratelimit-requests-remaining header
     * after every API response and syncs the rate limiter.
     */
    private ExchangeFilterFunction rateLimitResponseFilter() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            String remainingHeader = clientResponse.headers()
                    .asHttpHeaders()
                    .getFirst("x-ratelimit-requests-remaining");

            if (remainingHeader != null) {
                try {
                    int remaining = Integer.parseInt(remainingHeader);
                    rateLimiter.syncFromHeaders(remaining);
                } catch (NumberFormatException e) {
                    log.warn("Could not parse x-ratelimit-requests-remaining header: {}", remainingHeader);
                }
            }
            return Mono.just(clientResponse);
        });
    }

    private InjuryApiResponse buildFallbackResponse() {
        return InjuryApiResponse.builder()
                .results(0)
                .response(List.of())
                .build();
    }
}

