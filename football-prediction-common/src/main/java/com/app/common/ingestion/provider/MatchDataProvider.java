package com.app.common.ingestion.provider;

import com.app.common.ingestion.dto.InternalMatchDto;

import java.util.List;

/**
 * Provider interface for match data.
 * Implementations can fetch from CSV, API, database, or any other source.
 *
 * <p>This abstraction enables:
 * <ul>
 *   <li>Third-party API agnostic design</li>
 *   <li>Easy provider switching via feature flags</li>
 *   <li>Fallback chains for fault tolerance</li>
 *   <li>Shadow mode validation with multiple providers</li>
 * </ul>
 *
 * <p><b>IMPORTANT:</b> Implementations MUST map their data to canonical DTOs.
 * They should NOT directly modify any existing entities or services.
 */
public interface MatchDataProvider {

    /**
     * Unique provider identifier for logging and metrics.
     * Examples: "FOOTBALL_DATA_ORG", "LEGACY_CSV", "ESPN_API"
     *
     * @return Provider name
     */
    String getProviderName();

    /**
     * Fetch all matches for a specific season.
     *
     * @param season Season identifier (e.g., "2025-26")
     * @return List of matches in canonical format, empty list if none found
     */
    List<InternalMatchDto> getMatchesBySeason(String season);

    /**
     * Fetch recent completed matches.
     * Used for updating match results and triggering stat recalculations.
     *
     * @param competition Competition code (e.g., "PL" for Premier League)
     * @param limit Maximum number of matches to return
     * @return List of recent completed matches, sorted by date descending
     */
    List<InternalMatchDto> getRecentCompletedMatches(String competition, int limit);

    /**
     * Fetch upcoming/scheduled matches.
     * Used for predictions and upcoming match displays.
     *
     * @param competition Competition code
     * @return List of scheduled matches, sorted by date ascending
     */
    List<InternalMatchDto> getScheduledMatches(String competition);

    /**
     * Fetch matches for a specific matchday.
     *
     * @param competition Competition code
     * @param matchday Matchday number
     * @return List of matches for the matchday
     */
    default List<InternalMatchDto> getMatchesByMatchday(String competition, int matchday) {
        // Default implementation filters from all matches
        // Providers can override for efficiency
        return getScheduledMatches(competition).stream()
            .filter(m -> m.getMatchday() != null && m.getMatchday() == matchday)
            .toList();
    }

    /**
     * Check if this provider is currently available and healthy.
     * Used for health checks and fallback decisions.
     *
     * @return true if provider can serve requests
     */
    boolean isAvailable();

    /**
     * Provider priority for fallback ordering.
     * Lower values indicate higher priority.
     *
     * <p>Example priorities:
     * <ul>
     *   <li>1-10: Primary live APIs</li>
     *   <li>11-50: Secondary/backup APIs</li>
     *   <li>51-100: Local/cached sources</li>
     * </ul>
     *
     * @return Priority value (lower = higher priority)
     */
    int getPriority();

    /**
     * Get supported competitions for this provider.
     *
     * @return List of competition codes this provider supports
     */
    default List<String> getSupportedCompetitions() {
        return List.of("PL"); // Default: Premier League only
    }

    /**
     * Check if this provider supports a specific competition.
     *
     * @param competition Competition code
     * @return true if supported
     */
    default boolean supportsCompetition(String competition) {
        return getSupportedCompetitions().contains(competition);
    }
}

