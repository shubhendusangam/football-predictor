package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * DTO for fixture congestion and fatigue analysis.
 *
 * <p>Contains days-between-matches data, fatigue index, and
 * historical win rates segmented by rest days.</p>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@Value
@Builder
public class FixtureCongestionDTO {

    String teamName;

    /** Days between each consecutive match (most recent gaps first). */
    @Builder.Default
    List<Integer> daysBetweenMatches = Collections.emptyList();

    /** Average days between matches. */
    double avgDaysBetween;

    /** Fatigue index 0–100 (100 = very congested, 0 = well rested). */
    int fatigueIndex;

    /** Human-readable fatigue level: High / Medium / Low. */
    String fatigueLevel;

    /** Win rate when rest &lt; 3 days (0–100). */
    double winRateShortRest;

    /** Win rate when rest 3–5 days (0–100). */
    double winRateNormalRest;

    /** Win rate when rest &gt; 5 days (0–100). */
    double winRateLongRest;

    /** Matches analysed for short-rest bucket. */
    int matchesShortRest;

    /** Matches analysed for normal-rest bucket. */
    int matchesNormalRest;

    /** Matches analysed for long-rest bucket. */
    int matchesLongRest;

    /** Date of the team's most recent match. */
    LocalDate lastMatchDate;

    /** Days since last match (relative to asOfDate). */
    int daysSinceLastMatch;

    /** Number of recent matches analysed for the gap list. */
    int matchesAnalyzed;

    /** Total matches used for historical win-rate breakdown. */
    int totalHistoricalMatches;

    /** Description of win-rate impact, e.g. "Win rate drops 14% when &lt;4 days rest". */
    String impactSummary;

    /**
     * Create an empty result for a team with no data.
     */
    public static FixtureCongestionDTO empty(String teamName) {
        return FixtureCongestionDTO.builder()
                .teamName(teamName)
                .daysBetweenMatches(Collections.emptyList())
                .avgDaysBetween(0)
                .fatigueIndex(0)
                .fatigueLevel("Unknown")
                .winRateShortRest(0)
                .winRateNormalRest(0)
                .winRateLongRest(0)
                .matchesShortRest(0)
                .matchesNormalRest(0)
                .matchesLongRest(0)
                .daysSinceLastMatch(0)
                .matchesAnalyzed(0)
                .totalHistoricalMatches(0)
                .impactSummary("Insufficient data")
                .build();
    }
}

