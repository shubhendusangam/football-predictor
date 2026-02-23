package com.app.footballprediction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Comprehensive Team Analytics DTO.
 * Contains all analytics data for a team including:
 * - Team information
 * - Upcoming matches
 * - Season history
 * - Model accuracy metrics
 * - Prediction comparisons
 * - Home vs Away trends
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamAnalyticsDto {

    /**
     * Basic team information
     */
    private TeamInfo teamInfo;

    /**
     * Upcoming matches for the team
     */
    private List<UpcomingMatch> upcomingMatches;

    /**
     * Historical season statistics
     */
    private List<SeasonHistory> seasonHistory;

    /**
     * Model accuracy metrics for this team
     */
    private ModelAccuracy modelAccuracy;

    /**
     * Prediction vs Actual performance comparison
     */
    private List<PredictionComparison> predictionComparison;

    /**
     * Home vs Away performance trends
     */
    private HomeAwayTrend homeAwayTrend;

    /**
     * Data freshness timestamp
     */
    private String lastUpdated;

    // ═══════════════════════════════════════════════════════════════════
    // Nested DTOs
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Basic team information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamInfo {
        private Long id;
        private String name;
        private String shortName;
        private String logoUrl;
        private String primaryColor;
        private int totalMatches;
        private String currentSeason;
        private int currentSeasonMatches;
    }

    /**
     * Upcoming match details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpcomingMatch {
        private Long matchId;
        private LocalDate matchDate;
        private String opponent;
        private String opponentLogoUrl;
        private boolean isHome;
        private String venue;
        private String predictedResult;
        private Double confidence;
        private Double probHomeWin;
        private Double probDraw;
        private Double probAwayWin;
        private String formLast5;
        private String opponentFormLast5;
        /**
         * True if this fixture is generated from historical data rather than from official schedule.
         * Used for non-Premier League teams where we don't have API access to real fixtures.
         */
        @Builder.Default
        private boolean simulated = false;
    }

    /**
     * Season-wise historical statistics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeasonHistory {
        private String season;
        private int matchesPlayed;
        private int wins;
        private int draws;
        private int losses;
        private int goalsScored;
        private int goalsConceded;
        private int goalDifference;
        private int points;
        private double winRate;
        private double avgGoalsScored;
        private double avgGoalsConceded;
        private int homeWins;
        private int awayWins;
        private int cleanSheets;
        private Integer leaguePosition;  // If available
    }

    /**
     * Model accuracy metrics for the team
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelAccuracy {
        private int totalPredictions;
        private int correctPredictions;
        private double overallAccuracy;
        private int highConfidencePredictions;
        private int correctHighConfidencePredictions;
        private double highConfidenceAccuracy;
        private int homePredictions;
        private int correctHomePredictions;
        private double homeAccuracy;
        private int awayPredictions;
        private int correctAwayPredictions;
        private double awayAccuracy;
        private double averageConfidence;
        private AccuracyByResult accuracyByResult;
        private String accuracyTrend;  // IMPROVING, STABLE, DECLINING
    }

    /**
     * Accuracy broken down by predicted result type
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccuracyByResult {
        private int winPredictions;
        private int correctWinPredictions;
        private double winPredictionAccuracy;
        private int drawPredictions;
        private int correctDrawPredictions;
        private double drawPredictionAccuracy;
        private int lossPredictions;
        private int correctLossPredictions;
        private double lossPredictionAccuracy;
    }

    /**
     * Prediction vs Actual comparison for a season
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionComparison {
        private String season;
        private int predictedWins;
        private int actualWins;
        private int predictedDraws;
        private int actualDraws;
        private int predictedLosses;
        private int actualLosses;
        private int predictedPoints;
        private int actualPoints;
        private double predictedGoalsPerGame;
        private double actualGoalsPerGame;
        private int totalMatches;
        private int correctPredictions;
        private double seasonAccuracy;
    }

    /**
     * Home vs Away performance trend
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HomeAwayTrend {
        private HomeTrend homeTrend;
        private AwayTrend awayTrend;
        private String strongerVenue;  // HOME, AWAY, BALANCED
        private double homeAdvantage;  // Percentage difference
    }

    /**
     * Home performance trend data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HomeTrend {
        private int totalMatches;
        private int wins;
        private int draws;
        private int losses;
        private double winRate;
        private int goalsScored;
        private int goalsConceded;
        private double avgGoalsScored;
        private double avgGoalsConceded;
        private int cleanSheets;
        private double cleanSheetRate;
        private List<RecentResult> recentResults;
        private int currentStreak;  // Positive = winning, negative = losing
        private String streakType;  // WIN, DRAW, LOSS, UNBEATEN, WINLESS
    }

    /**
     * Away performance trend data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AwayTrend {
        private int totalMatches;
        private int wins;
        private int draws;
        private int losses;
        private double winRate;
        private int goalsScored;
        private int goalsConceded;
        private double avgGoalsScored;
        private double avgGoalsConceded;
        private int cleanSheets;
        private double cleanSheetRate;
        private List<RecentResult> recentResults;
        private int currentStreak;
        private String streakType;
    }

    /**
     * Recent match result for trend display
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentResult {
        private LocalDate date;
        private String opponent;
        private int goalsScored;
        private int goalsConceded;
        private String result;  // W, D, L
        private boolean isHome;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Builder helper methods
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Create an empty analytics response with default values
     */
    public static TeamAnalyticsDto empty(String teamName) {
        return TeamAnalyticsDto.builder()
                .teamInfo(TeamInfo.builder()
                        .name(teamName)
                        .totalMatches(0)
                        .build())
                .upcomingMatches(List.of())
                .seasonHistory(List.of())
                .modelAccuracy(ModelAccuracy.builder()
                        .totalPredictions(0)
                        .correctPredictions(0)
                        .overallAccuracy(0.0)
                        .build())
                .predictionComparison(List.of())
                .homeAwayTrend(HomeAwayTrend.builder()
                        .strongerVenue("BALANCED")
                        .homeAdvantage(0.0)
                        .build())
                .lastUpdated(java.time.LocalDateTime.now().toString())
                .build();
    }
}

