package com.app.footballprediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for comprehensive pre-match insights.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreMatchInsightsResponse {

    private String homeTeam;
    private String awayTeam;
    private FormComparison formComparison;
    private List<StreakIndicator> streakIndicators;
    private RestAnalysis restAnalysis;
    private GoalThreatMeter goalThreatMeter;
    private MarketPredictions marketPredictions;
    private List<String> keyInsights;
    private String generatedAt;

    /**
     * Form comparison between home and away teams.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormComparison {
        private int homeFormPoints;
        private String homeFormString;
        private double homeFormRating;
        private int homeMaxPoints;
        private int awayFormPoints;
        private String awayFormString;
        private double awayFormRating;
        private int awayMaxPoints;
        private String formAdvantage;
        private int pointsDifference;
    }

    /**
     * Streak indicator for a team.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreakIndicator {
        private String team;
        private String streakType; // WIN, LOSS, UNBEATEN, WINLESS, SCORING, CLEAN_SHEET
        private int streakLength;
        private String emoji;
        private String description;
        private String impact; // POSITIVE, NEGATIVE
        private boolean isHomeTeam;
    }

    /**
     * Rest days analysis between matches.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RestAnalysis {
        private String homeTeamLastMatch;
        private String awayTeamLastMatch;
        private int homeTeamRestDays;
        private int awayTeamRestDays;
        private int restDifference;
        private String restAdvantage;
        private List<String> fatigueWarnings;
        private boolean homeFatigueRisk;
        private boolean awayFatigueRisk;
    }

    /**
     * Goal threat meter based on scoring/conceding averages.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoalThreatMeter {
        private double homeTeamAvgScored;
        private double homeTeamAvgConceded;
        private double awayTeamAvgScored;
        private double awayTeamAvgConceded;
        private double homeExpectedGoals;
        private double awayExpectedGoals;
        private double totalExpectedGoals;
        private double homeThreatRating;
        private double awayThreatRating;
    }

    /**
     * Market predictions for goal-based analysis.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketPredictions {
        private double expectedHomeGoals;
        private double expectedAwayGoals;
        private double expectedTotalGoals;
        private String recommendation;
    }
}

