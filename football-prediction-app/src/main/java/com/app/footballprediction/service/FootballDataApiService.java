package com.app.footballprediction.service;

import com.app.footballprediction.dto.external.FootballApiResponse;
import com.app.footballprediction.dto.external.StandingsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for fetching data from football-data.org API.
 *
 * Free tier limits: 10 requests per minute
 * Supported competitions: PL (Premier League), BL1 (Bundesliga),
 *                         SA (Serie A), PD (La Liga), FL1 (Ligue 1)
 *
 * This service implements caching to minimize API calls.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FootballDataApiService {

    private final WebClient footballApiClient;

    // Simple in-memory cache to respect rate limits (backup to Spring cache)
    private final Map<String, CacheEntry<?>> cache = new HashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    /**
     * Fetch finished matches for current season.
     * Used to get recent results and form.
     */
    @Cacheable(value = "matches", key = "'finished_' + #competitionCode")
    public FootballApiResponse getFinishedMatches(String competitionCode) {
        String cacheKey = "finished_" + competitionCode;
        return getCachedOrFetch(cacheKey, () -> {
            log.info("Fetching finished matches for {} from external API", competitionCode);
            return footballApiClient.get()
                    .uri("/competitions/{code}/matches?status={status}", competitionCode, "FINISHED")
                    .retrieve()
                    .bodyToMono(FootballApiResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .doOnError(e -> log.error("Error fetching finished matches: {}", e.getMessage()))
                    .block();
        });
    }

    /**
     * Fetch upcoming/scheduled matches.
     * These are the matches we want to predict.
     */
    @Cacheable(value = "matches", key = "'scheduled_' + #competitionCode")
    public FootballApiResponse getScheduledMatches(String competitionCode) {
        String cacheKey = "scheduled_" + competitionCode;
        return getCachedOrFetch(cacheKey, () -> {
            log.info("Fetching scheduled matches for {} from external API", competitionCode);
            return footballApiClient.get()
                    .uri("/competitions/{code}/matches?status={status}", competitionCode, "SCHEDULED")
                    .retrieve()
                    .bodyToMono(FootballApiResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .doOnError(e -> log.error("Error fetching scheduled matches: {}", e.getMessage()))
                    .block();
        });
    }

    /**
     * Fetch current standings with team form.
     * Provides current season statistics for each team.
     */
    @Cacheable(value = "standings", key = "#competitionCode")
    public StandingsResponse getStandings(String competitionCode) {
        String cacheKey = "standings_" + competitionCode;
        return getCachedOrFetch(cacheKey, () -> {
            log.info("Fetching standings for {} from external API", competitionCode);
            return footballApiClient.get()
                    .uri("/competitions/{code}/standings", competitionCode)
                    .retrieve()
                    .bodyToMono(StandingsResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .doOnError(e -> log.error("Error fetching standings: {}", e.getMessage()))
                    .block();
        });
    }

    /**
     * Fetch matches for a specific matchday.
     */
    @Cacheable(value = "matches", key = "'matchday_' + #competitionCode + '_' + #matchday")
    public FootballApiResponse getMatchdayMatches(String competitionCode, int matchday) {
        String cacheKey = "matchday_" + competitionCode + "_" + matchday;
        return getCachedOrFetch(cacheKey, () -> {
            log.info("Fetching matchday {} for {} from external API", matchday, competitionCode);
            return footballApiClient.get()
                    .uri("/competitions/{code}/matches?matchday={matchday}", competitionCode, matchday)
                    .retrieve()
                    .bodyToMono(FootballApiResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .doOnError(e -> log.error("Error fetching matchday: {}", e.getMessage()))
                    .block();
        });
    }

    /**
     * Get team name mapping between API names and our database names.
     * Some teams have different names in the API vs our CSV data.
     */
    public String normalizeTeamName(String apiTeamName) {
        // Common mappings between football-data.org names and CSV names
        return switch (apiTeamName) {
            case "Manchester United FC" -> "Man United";
            case "Manchester City FC" -> "Man City";
            case "Tottenham Hotspur FC" -> "Tottenham";
            case "Newcastle United FC" -> "Newcastle";
            case "West Ham United FC" -> "West Ham";
            case "Wolverhampton Wanderers FC" -> "Wolves";
            case "Leicester City FC" -> "Leicester";
            case "Brighton & Hove Albion FC" -> "Brighton";
            case "Nottingham Forest FC" -> "Nott'm Forest";
            case "AFC Bournemouth" -> "Bournemouth";
            case "Ipswich Town FC" -> "Ipswich";
            case "Southampton FC" -> "Southampton";
            case "Everton FC" -> "Everton";
            case "Fulham FC" -> "Fulham";
            case "Crystal Palace FC" -> "Crystal Palace";
            case "Brentford FC" -> "Brentford";
            case "Aston Villa FC" -> "Aston Villa";
            case "Chelsea FC" -> "Chelsea";
            case "Arsenal FC" -> "Arsenal";
            case "Liverpool FC" -> "Liverpool";
            default -> {
                // Remove "FC" suffix if present
                String cleaned = apiTeamName.replaceAll("\\s*FC$", "").trim();
                log.debug("Team name not mapped: {} -> {}", apiTeamName, cleaned);
                yield cleaned;
            }
        };
    }

    /**
     * Simple cache implementation to avoid hitting rate limits.
     */
    @SuppressWarnings("unchecked")
    private <T> T getCachedOrFetch(String key, java.util.function.Supplier<T> fetcher) {
        CacheEntry<?> entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            log.debug("Cache hit for key: {}", key);
            return (T) entry.value;
        }

        T result = fetcher.get();
        cache.put(key, new CacheEntry<>(result, System.currentTimeMillis() + CACHE_TTL_MS));
        return result;
    }

    /**
     * Clear cache (useful for testing or forced refresh).
     */
    @CacheEvict(value = {"standings", "matches"}, allEntries = true)
    public void clearCache() {
        cache.clear();
        log.info("External API cache cleared (both internal and Spring cache)");
    }

    private record CacheEntry<T>(T value, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}

