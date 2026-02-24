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
     * Current streak summary for home team.
     * Always populated (shows "No active streak" if count is 0).
     */
    private CurrentStreak homeCurrentStreak;

    /**
     * Current streak summary for away team.
     * Always populated (shows "No active streak" if count is 0).
     */
    private CurrentStreak awayCurrentStreak;

    /**
     * Current streak summary for a team.
     * This is always populated - shows 0 count with "No active streak" description if no streak.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentStreak {
        private String teamName;
        private int winlessStreak;      // Count of consecutive matches without a win (0 if last match was a win)
        private int winStreak;          // Count of consecutive wins (0 if last match was not a win)
        private int lossStreak;         // Count of consecutive losses (0 if last match was not a loss)
        private int unbeatenStreak;     // Count of consecutive matches unbeaten (0 if last match was a loss)
        private String primaryStreakType;  // WIN, LOSS, UNBEATEN, WINLESS, or NONE
        private int primaryStreakCount;    // Count for primary streak
        private String displayText;        // Human-readable text e.g., "6 Winless" or "No active streak"
        private String emoji;              // Emoji for display
        private String recentResults;      // Last 6 results e.g., "LDLDLL"
    }

    /**
     * Form comparison between home and away teams.
     * Scope: Last 5 matches only.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormComparison {
        private int homeFormPoints;           // Total points in last 5 matches
        private double homePointsPerGame;     // PPG = homeFormPoints / 5
        private String homeFormString;        // e.g., "WWDLW"
        private double homeFormRating;        // 0-100 rating
        private int homeMaxPoints;            // Always 15 (5 matches * 3 points)
        private int awayFormPoints;           // Total points in last 5 matches
        private double awayPointsPerGame;     // PPG = awayFormPoints / 5
        private String awayFormString;        // e.g., "LDWDW"
        private double awayFormRating;        // 0-100 rating
        private int awayMaxPoints;            // Always 15 (5 matches * 3 points)
        private String formAdvantage;         // Team with better form
        private int pointsDifference;         // homeFormPoints - awayFormPoints
        private String dataScope;             // Always "Last 5 Matches"
    }

    /**
     * Streak indicator for a team.
     * Scope: Consecutive results from latest match backwards.
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
        private String dataScope;  // "Consecutive from latest match"
    }

    /**
     * Rest days analysis between matches.
     * Scope: Days since last match (today - last_match_date).
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
        private String dataScope;  // "Days since last match"
    }

    /**
     * Goal threat meter based on scoring/conceding averages.
     * Scope: Season averages (all matches in current season).
     * Goal Threat Index formula: (avgGoalsScored / leagueMaxAvg) * 100
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoalThreatMeter {
        private double homeTeamAvgScored;      // Season avg goals scored
        private double homeTeamAvgConceded;    // Season avg goals conceded
        private double awayTeamAvgScored;      // Season avg goals scored
        private double awayTeamAvgConceded;    // Season avg goals conceded
        private double homeExpectedGoals;      // Expected goals this match
        private double awayExpectedGoals;      // Expected goals this match
        private double totalExpectedGoals;     // Total expected goals
        private double homeThreatRating;       // 0-100 threat index
        private double awayThreatRating;       // 0-100 threat index
        private double homeGoalThreatIndex;    // Goal Threat Index (0-100)
        private double awayGoalThreatIndex;    // Goal Threat Index (0-100)
        private String dataScope;              // "Season Average (All Matches)"
        private String threatIndexFormula;    // "(Avg Goals Scored / 2.0) * 100"
    }

    /**
     * Market predictions for goal-based analysis.
     * Scope: Based on season averages.
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
        private String dataScope;  // "Season Average (All Matches)"
    }
}

