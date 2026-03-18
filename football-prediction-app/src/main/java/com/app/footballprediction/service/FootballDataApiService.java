package com.app.footballprediction.service;

import com.app.common.util.TeamNameNormalizer;
import com.app.footballprediction.dto.external.FootballApiResponse;
import com.app.footballprediction.dto.external.StandingsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Service for fetching data from football-data.org API.
 *
 * Free tier limits: 10 requests per minute
 * Supported competitions: PL (Premier League), BL1 (Bundesliga),
 *                         SA (Serie A), PD (La Liga), FL1 (Ligue 1)
 *
 * This service uses Spring's Caffeine cache for efficient caching with TTL.
 * Cache configuration is managed in CacheConfig.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FootballDataApiService {

    private final WebClient footballApiClient;

    /**
     * Fetch finished matches for current season.
     * Used to get recent results and form.
     * Cached for 5 minutes by default.
     */
    @Cacheable(value = "matches", key = "'finished_' + #competitionCode", unless = "#result == null")
    public FootballApiResponse getFinishedMatches(String competitionCode) {
        log.info("Fetching finished matches for {} from external API", competitionCode);
        return fetchWithRetry(() -> footballApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/competitions/{code}/matches")
                        .queryParam("status", "FINISHED")
                        .build(competitionCode))
                .retrieve()
                .bodyToMono(FootballApiResponse.class));
    }

    /**
     * Fetch upcoming/scheduled matches.
     * These are the matches we want to predict.
     * Uses TIMED and SCHEDULED statuses to get all upcoming matches.
     * Cached for 5 minutes by default.
     */
    @Cacheable(value = "matches", key = "'scheduled_' + #competitionCode", unless = "#result == null")
    public FootballApiResponse getScheduledMatches(String competitionCode) {
        log.info("Fetching scheduled matches for {} from external API", competitionCode);
        return fetchScheduledMatchesFromApi(competitionCode);
    }

    /**
     * Fetch upcoming/scheduled matches WITHOUT caching.
     * Use this for real-time updates when fresh data is needed.
     */
    public FootballApiResponse getScheduledMatchesFresh(String competitionCode) {
        log.info("Fetching FRESH scheduled matches for {} from external API (bypassing cache)", competitionCode);
        // First clear the cache to ensure subsequent cached calls get fresh data
        clearMatchesCache();
        return fetchScheduledMatchesFromApi(competitionCode);
    }

    /**
     * Internal method to fetch scheduled matches from API.
     */
    private FootballApiResponse fetchScheduledMatchesFromApi(String competitionCode) {
        return fetchWithRetry(() -> footballApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/competitions/{code}/matches")
                        .queryParam("status", "SCHEDULED,TIMED")
                        .build(competitionCode))
                .retrieve()
                .bodyToMono(FootballApiResponse.class));
    }

    /**
     * Fetch current standings with team form.
     * Provides current season statistics for each team.
     * Cached for 5 minutes by default.
     */
    @Cacheable(value = "standings", key = "#competitionCode", unless = "#result == null")
    public StandingsResponse getStandings(String competitionCode) {
        log.info("Fetching standings for {} from external API", competitionCode);
        return fetchWithRetry(() -> footballApiClient.get()
                .uri("/competitions/{code}/standings", competitionCode)
                .retrieve()
                .bodyToMono(StandingsResponse.class));
    }

    /**
     * Fetch matches for a specific matchday.
     * Cached for 5 minutes by default.
     */
    @Cacheable(value = "matches", key = "'matchday_' + #competitionCode + '_' + #matchday", unless = "#result == null")
    public FootballApiResponse getMatchdayMatches(String competitionCode, int matchday) {
        log.info("Fetching matchday {} for {} from external API", matchday, competitionCode);
        return fetchWithRetry(() -> footballApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/competitions/{code}/matches")
                        .queryParam("matchday", matchday)
                        .build(competitionCode))
                .retrieve()
                .bodyToMono(FootballApiResponse.class));
    }

    /**
     * Helper method to fetch with retry logic and proper error handling.
     * Uses Reactor's retryWhen with exponential backoff for non-blocking retries.
     */
    private <T> T fetchWithRetry(java.util.function.Supplier<Mono<T>> monoSupplier) {
        return monoSupplier.get()
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(30))
                        .jitter(0.5)
                        .filter(throwable -> throwable instanceof WebClientResponseException.TooManyRequests
                                || throwable instanceof WebClientResponseException.ServiceUnavailable
                                || throwable instanceof java.util.concurrent.TimeoutException)
                        .doBeforeRetry(signal -> log.warn(
                                "Retry #{} after error: {}",
                                signal.totalRetries() + 1,
                                signal.failure().getMessage()))
                        .onRetryExhaustedThrow((spec, signal) -> {
                            log.error("All {} retries exhausted. Last error: {}",
                                    signal.totalRetries(), signal.failure().getMessage());
                            return signal.failure();
                        }))
                .doOnError(e -> log.error("Error fetching data: {}", e.getMessage()))
                .block();
    }

    // ══════════════════════════════════════════════════════════════
    // Reactive (Mono) fetch methods – used by DataSyncService
    // ══════════════════════════════════════════════════════════════

    /**
     * Reactive fetch of finished matches with exponential-backoff retry.
     * Returns a {@link Mono} for non-blocking pipeline composition.
     */
    public Mono<FootballApiResponse> getFinishedMatchesReactive(String competitionCode) {
        return footballApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/competitions/{code}/matches")
                        .queryParam("status", "FINISHED")
                        .build(competitionCode))
                .retrieve()
                .bodyToMono(FootballApiResponse.class)
                .timeout(Duration.ofSeconds(30))
                .retryWhen(reactorRetrySpec())
                .doOnSubscribe(s -> log.debug("Reactive fetch: finished matches for {}", competitionCode));
    }

    /**
     * Reactive fetch of scheduled/timed matches with exponential-backoff retry.
     */
    public Mono<FootballApiResponse> getScheduledMatchesReactive(String competitionCode) {
        return footballApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/competitions/{code}/matches")
                        .queryParam("status", "SCHEDULED,TIMED")
                        .build(competitionCode))
                .retrieve()
                .bodyToMono(FootballApiResponse.class)
                .timeout(Duration.ofSeconds(30))
                .retryWhen(reactorRetrySpec())
                .doOnSubscribe(s -> log.debug("Reactive fetch: scheduled matches for {}", competitionCode));
    }

    /**
     * Reactive fetch of standings with exponential-backoff retry.
     */
    public Mono<StandingsResponse> getStandingsReactive(String competitionCode) {
        return footballApiClient.get()
                .uri("/competitions/{code}/standings", competitionCode)
                .retrieve()
                .bodyToMono(StandingsResponse.class)
                .timeout(Duration.ofSeconds(30))
                .retryWhen(reactorRetrySpec())
                .doOnSubscribe(s -> log.debug("Reactive fetch: standings for {}", competitionCode));
    }

    /**
     * Shared Reactor Retry spec: 3 retries, 2s initial backoff, 30s max, 50% jitter.
     * Retries on 429 Too Many Requests, 503 Service Unavailable, and timeouts.
     */
    private Retry reactorRetrySpec() {
        return Retry.backoff(3, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(30))
                .jitter(0.5)
                .filter(throwable -> throwable instanceof WebClientResponseException.TooManyRequests
                        || throwable instanceof WebClientResponseException.ServiceUnavailable
                        || throwable instanceof java.util.concurrent.TimeoutException)
                .doBeforeRetry(signal -> log.warn(
                        "Retry #{} after error: {}",
                        signal.totalRetries() + 1,
                        signal.failure().getMessage()))
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    /**
     * Get team name mapping between API names and our database names.
     * Some teams have different names in the API vs our CSV data.
     */
    public String normalizeTeamName(String apiTeamName) {
        // First, try the centralized normalizer which has comprehensive mappings
        String normalized = TeamNameNormalizer.normalize(apiTeamName);
        if (!normalized.equals(apiTeamName)) {
            return normalized;
        }

        // Fallback: remove common suffixes (FC, AFC) if present
        String cleaned = apiTeamName
                .replaceAll("\\s*AFC$", "")
                .replaceAll("\\s*FC$", "")
                .trim();
        if (!cleaned.equals(apiTeamName)) {
            log.debug("Team name not mapped: {} -> {}", apiTeamName, cleaned);
        }
        return cleaned;
    }

    /**
     * Clear all football API related caches.
     * Useful for forcing fresh data fetch.
     */
    @Caching(evict = {
        @CacheEvict(value = "standings", allEntries = true),
        @CacheEvict(value = "matches", allEntries = true)
    })
    public void clearCache() {
        log.info("Football API caches cleared (standings and matches)");
    }

    /**
     * Clear only standings cache for a specific competition.
     */
    @CacheEvict(value = "standings", key = "#competitionCode")
    public void clearStandingsCache(String competitionCode) {
        log.info("Standings cache cleared for competition: {}", competitionCode);
    }

    /**
     * Clear only matches cache for a specific competition.
     */
    @CacheEvict(value = "matches", allEntries = true)
    public void clearMatchesCache() {
        log.info("Matches cache cleared");
    }
}

