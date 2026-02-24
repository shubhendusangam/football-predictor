package com.app.common.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Canonical match representation independent of data source.
 * All providers map their data to this format before processing.
 *
 * <p>This DTO serves as the "lingua franca" between:
 * <ul>
 *   <li>External API providers (football-data.org, ESPN, etc.)</li>
 *   <li>CSV file parsers</li>
 *   <li>Database entities</li>
 * </ul>
 *
 * <p><b>IMPORTANT:</b> This DTO does NOT replace the Match entity.
 * It is used ONLY for data transformation during ingestion.
 * The final persistence still uses the existing Match entity unchanged.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalMatchDto {

    // ══════════════════════════════════════════════════════════════
    // Identity Fields
    // ══════════════════════════════════════════════════════════════

    /**
     * External identifier from the source provider.
     * Used for tracking and deduplication across providers.
     */
    private String externalId;

    /**
     * Name of the provider that supplied this data.
     * Examples: "FOOTBALL_DATA_ORG", "LEGACY_CSV", "ESPN_API"
     */
    private String providerName;

    /**
     * Match date (local date without time).
     */
    private LocalDate matchDate;

    /**
     * Home team name.
     */
    private String homeTeam;

    /**
     * Away team name.
     */
    private String awayTeam;

    /**
     * Season identifier (e.g., "2025-26").
     */
    private String season;

    /**
     * Competition code (e.g., "PL" for Premier League).
     */
    private String competition;

    // ══════════════════════════════════════════════════════════════
    // Result Fields (null if match not yet played)
    // ══════════════════════════════════════════════════════════════

    /**
     * Full-time home team goals.
     */
    private Integer fullTimeHomeGoals;

    /**
     * Full-time away team goals.
     */
    private Integer fullTimeAwayGoals;

    /**
     * Full-time result: "H" (home win), "D" (draw), "A" (away win).
     */
    private String fullTimeResult;

    /**
     * Half-time home team goals.
     */
    private Integer halfTimeHomeGoals;

    /**
     * Half-time away team goals.
     */
    private Integer halfTimeAwayGoals;

    /**
     * Half-time result.
     */
    private String halfTimeResult;

    // ══════════════════════════════════════════════════════════════
    // Match Details
    // ══════════════════════════════════════════════════════════════

    /**
     * Matchday number within the season.
     */
    private Integer matchday;

    /**
     * Match status: SCHEDULED, TIMED, IN_PLAY, PAUSED, FINISHED, POSTPONED, CANCELLED
     */
    private String status;

    /**
     * Match referee.
     */
    private String referee;

    /**
     * Match venue/stadium.
     */
    private String venue;

    // ══════════════════════════════════════════════════════════════
    // Statistics (optional, may be null)
    // ══════════════════════════════════════════════════════════════

    private Integer homeShots;
    private Integer awayShots;
    private Integer homeShotsOnTarget;
    private Integer awayShotsOnTarget;
    private Integer homeCorners;
    private Integer awayCorners;
    private Integer homeYellowCards;
    private Integer awayYellowCards;
    private Integer homeRedCards;
    private Integer awayRedCards;
    private Integer homeFouls;
    private Integer awayFouls;
    private Integer homePossession;
    private Integer awayPossession;

    // ══════════════════════════════════════════════════════════════
    // Betting Odds (optional, may be null)
    // ══════════════════════════════════════════════════════════════

    /**
     * Home win odds (decimal format).
     */
    private Double homeWinOdds;

    /**
     * Draw odds (decimal format).
     */
    private Double drawOdds;

    /**
     * Away win odds (decimal format).
     */
    private Double awayWinOdds;

    /**
     * Odds provider/bookmaker name.
     */
    private String oddsProvider;

    // ══════════════════════════════════════════════════════════════
    // Team Crests/Logos (optional)
    // ══════════════════════════════════════════════════════════════

    private String homeTeamCrest;
    private String awayTeamCrest;

    // ══════════════════════════════════════════════════════════════
    // Metadata
    // ══════════════════════════════════════════════════════════════

    /**
     * Timestamp when this data was fetched.
     */
    private Instant fetchedAt;

    /**
     * Hash of raw data for change detection.
     */
    private String rawDataHash;

    // ══════════════════════════════════════════════════════════════
    // Business Logic Methods
    // ══════════════════════════════════════════════════════════════

    /**
     * Generate unique business key for idempotency.
     * This matches the existing unique constraint in the Match entity:
     * (matchDate, homeTeam, awayTeam)
     *
     * @return Business key string
     */
    public String getBusinessKey() {
        return String.format("%s|%s|%s",
            matchDate,
            normalizeTeamName(homeTeam),
            normalizeTeamName(awayTeam));
    }

    /**
     * Check if this match has been completed (has a result).
     *
     * @return true if match is finished
     */
    public boolean isCompleted() {
        return fullTimeResult != null && !fullTimeResult.isEmpty();
    }

    /**
     * Check if this is a scheduled/upcoming match.
     *
     * @return true if match is scheduled
     */
    public boolean isScheduled() {
        return "SCHEDULED".equals(status) || "TIMED".equals(status);
    }

    /**
     * Calculate the full-time result from scores if not provided.
     *
     * @return Result string: "H", "D", or "A"
     */
    public String calculateResult() {
        if (fullTimeHomeGoals == null || fullTimeAwayGoals == null) {
            return null;
        }
        if (fullTimeHomeGoals > fullTimeAwayGoals) return "H";
        if (fullTimeAwayGoals > fullTimeHomeGoals) return "A";
        return "D";
    }

    /**
     * Normalize team name for comparison.
     */
    private String normalizeTeamName(String name) {
        if (name == null) return "";
        return name.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    /**
     * Create a builder with defaults set.
     */
    public static InternalMatchDtoBuilder defaultBuilder() {
        return builder()
            .fetchedAt(Instant.now())
            .status("SCHEDULED");
    }
}

