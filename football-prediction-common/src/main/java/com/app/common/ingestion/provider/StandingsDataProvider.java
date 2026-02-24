package com.app.common.ingestion.provider;

import com.app.common.ingestion.dto.InternalStandingDto;

import java.util.List;

/**
 * Provider interface for league standings data.
 * Implementations fetch current standings from various sources.
 *
 * <p>This abstraction enables provider-agnostic standings retrieval
 * for use in predictions, displays, and analytics.
 */
public interface StandingsDataProvider {

    /**
     * Unique provider identifier for logging and metrics.
     *
     * @return Provider name
     */
    String getProviderName();

    /**
     * Get current standings for a competition.
     *
     * @param competition Competition code (e.g., "PL")
     * @return List of standings entries in canonical format, ordered by position
     */
    List<InternalStandingDto> getStandings(String competition);

    /**
     * Get team form (last N matches results).
     *
     * @param teamName Team name
     * @param competition Competition code
     * @return Form string (e.g., "WWLDW") or null if unavailable
     */
    String getTeamForm(String teamName, String competition);

    /**
     * Get a specific team's standing.
     *
     * @param teamName Team name
     * @param competition Competition code
     * @return Standing entry or null if not found
     */
    default InternalStandingDto getTeamStanding(String teamName, String competition) {
        return getStandings(competition).stream()
            .filter(s -> s.getTeamName().equalsIgnoreCase(teamName))
            .findFirst()
            .orElse(null);
    }

    /**
     * Check if this provider is currently available and healthy.
     *
     * @return true if provider can serve requests
     */
    boolean isAvailable();

    /**
     * Provider priority for fallback ordering.
     * Lower values indicate higher priority.
     *
     * @return Priority value (lower = higher priority)
     */
    int getPriority();
}

