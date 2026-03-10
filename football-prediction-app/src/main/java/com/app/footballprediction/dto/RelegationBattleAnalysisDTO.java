package com.app.footballprediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for the complete relegation battle analysis.
 * Contains season context, survival thresholds, and all teams in danger zone.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelegationBattleAnalysisDTO {

    /**
     * Season identifier (e.g., "2025-26").
     */
    private String season;

    /**
     * Date the analysis was calculated as of.
     */
    private LocalDate asOfDate;

    /**
     * List of teams in the relegation battle (positions 14-20).
     * Sorted by current position.
     */
    private List<RelegationBattleDTO> teamsInBattle;

    /**
     * Estimated points target for survival (typically 35-40).
     */
    private int survivalPointsTarget;

    /**
     * Total number of matches in the season (typically 38 for Premier League).
     */
    private int totalMatchesInSeason;

    /**
     * Number of matchdays completed.
     */
    private int matchdaysCompleted;

    /**
     * Percentage of season completed.
     */
    private double seasonProgressPercent;

    /**
     * Summary of the relegation battle intensity.
     */
    private RelegationSummary summary;

    /**
     * Last updated timestamp (ISO format).
     */
    private String lastUpdated;

    /**
     * Summary of the relegation battle.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelegationSummary {
        /**
         * Number of teams realistically in danger (within 6 points of 18th).
         */
        private int teamsInDanger;

        /**
         * Number of teams already relegated (mathematically).
         */
        private int teamsRelegated;

        /**
         * Number of teams safe from relegation.
         */
        private int teamsSafe;

        /**
         * Points of the team in 17th (safety line).
         */
        private int safetyLinePoints;

        /**
         * Points of the team in 18th (first relegation spot).
         */
        private int relegationLinePoints;

        /**
         * Gap between 17th and 18th place.
         */
        private int gapAtRelegationLine;

        /**
         * Intensity of the relegation battle.
         * Values: "Calm" | "Tense" | "Critical" | "Dramatic"
         */
        private String intensity;

        /**
         * Most likely team to be relegated.
         */
        private String mostLikelyToGoDown;

        /**
         * Team with best chance of escaping.
         */
        private String bestEscapeChance;
    }
}

