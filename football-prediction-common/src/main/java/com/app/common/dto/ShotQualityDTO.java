package com.app.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for shot quality analysis metrics.
 * Provides comprehensive shot efficiency statistics for a team.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShotQualityDTO {

    /**
     * Team name
     */
    private String teamName;

    /**
     * Average shots per match
     */
    private double avgShots;

    /**
     * Average shots on target per match
     */
    private double avgShotsOnTarget;

    /**
     * Shot accuracy percentage (shotsOnTarget / totalShots * 100)
     */
    private double shotAccuracy;

    /**
     * Conversion rate (goals / shotsOnTarget)
     */
    private double conversionRate;

    /**
     * Quality score on 0-10 scale
     * Formula: (shotAccuracy × 10 × 0.4) + (conversionRate × 10 × 0.6)
     */
    private double qualityScore;

    /**
     * Text rating based on quality score:
     * - Excellent: 8-10
     * - Good: 6-8
     * - Average: 4-6
     * - Poor: < 4
     */
    private String rating;

    /**
     * Whether this represents home or away stats
     */
    private Boolean isHome;

    /**
     * Number of matches analyzed
     */
    private int matchesAnalyzed;

    /**
     * Total goals scored in analyzed matches
     */
    private int totalGoals;

    /**
     * Total shots in analyzed matches
     */
    private int totalShots;

    /**
     * Total shots on target in analyzed matches
     */
    private int totalShotsOnTarget;

    /**
     * Shot trend data for last 10 games (for sparkline chart)
     */
    private List<ShotTrendPoint> shotsTrend;

    /**
     * Comparison with league average
     */
    private LeagueComparison leagueComparison;

    /**
     * Data point for shot trend sparkline
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShotTrendPoint {
        private String matchDate;
        private String opponent;
        private int shots;
        private int shotsOnTarget;
        private int goals;
        private double shotAccuracy;
    }

    /**
     * Comparison metrics against league average
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeagueComparison {
        /**
         * League average shot accuracy (32%)
         */
        private double leagueAvgShotAccuracy;

        /**
         * League average conversion rate (0.28)
         */
        private double leagueAvgConversionRate;

        /**
         * Difference from league average shot accuracy
         */
        private double shotAccuracyDiff;

        /**
         * Difference from league average conversion rate
         */
        private double conversionRateDiff;

        /**
         * Whether team is above league average for shot accuracy
         */
        private boolean aboveAvgShotAccuracy;

        /**
         * Whether team is above league average for conversion rate
         */
        private boolean aboveAvgConversion;
    }

    /**
     * Calculate the rating based on quality score
     */
    public static String calculateRating(double qualityScore) {
        if (qualityScore >= 8.0) {
            return "Excellent";
        } else if (qualityScore >= 6.0) {
            return "Good";
        } else if (qualityScore >= 4.0) {
            return "Average";
        } else {
            return "Poor";
        }
    }
}

