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
                .bodyToMono(FootballApiResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block());
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
                .bodyToMono(FootballApiResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block());
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
                .bodyToMono(StandingsResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block());
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
                .bodyToMono(FootballApiResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block());
    }

    /**
     * Helper method to fetch with retry logic and proper error handling.
     */
    private <T> T fetchWithRetry(java.util.function.Supplier<T> fetcher) {
        int maxRetries = 2;

        for (int retryCount = 0; retryCount <= maxRetries; retryCount++) {
            try {
                return fetcher.get();
            } catch (WebClientResponseException.TooManyRequests e) {
                if (retryCount >= maxRetries) {
                    log.error("Rate limit exceeded after {} retries", maxRetries);
                    throw e;
                }
                log.warn("Rate limit hit, waiting before retry {} of {}", retryCount + 1, maxRetries);
                try {
                    Thread.sleep(6000L * (retryCount + 1)); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry wait", ie);
                }
            } catch (Exception e) {
                log.error("Error fetching data: {}", e.getMessage());
                throw e;
            }
        }

        throw new RuntimeException("Failed to fetch after retries");
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

