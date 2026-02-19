package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response DTO for Live/Trending Insights.
 * Provides real-time team performance trends and match predictions.
 */
@Data
@Builder
public class TrendingInsightsResponse {

    // 🔥 Hot Teams: Teams on 3+ match winning streaks
    private List<HotTeam> hotTeams;

    // ❄️ Cold Teams: Teams without a win in 5+ matches
    private List<ColdTeam> coldTeams;

    // ⚽ Top Scorers: Teams scoring most goals recently
    private List<TopScorer> topScorers;

    // 🧱 Defensive Walls: Teams with most clean sheets recently
    private List<DefensiveWall> defensiveWalls;

    // 🎯 Upset Alerts: Upcoming matches where away team has >50% win probability
    private List<UpsetAlert> upsetAlerts;

    // 🎉 Goal Fest Predictions: Matches with highest expected total goals
    private List<GoalFestMatch> goalFestMatches;

    // Metadata
    private String generatedAt;
    private int totalTeamsAnalyzed;

    /**
     * Team on a winning streak (3+ consecutive wins).
     */
    @Data
    @Builder
    public static class HotTeam {
        private String teamName;
        private int winStreak;              // Number of consecutive wins
        private int goalsScored;            // Goals in streak period
        private int goalsConceded;          // Goals conceded in streak period
        private String recentForm;          // e.g., "WWWWW"
        private List<String> lastOpponents; // Last 3 opponents beaten
        private String streakStartDate;
    }

    /**
     * Team without a win in 5+ matches.
     */
    @Data
    @Builder
    public static class ColdTeam {
        private String teamName;
        private int matchesWithoutWin;      // Consecutive matches without a win
        private int draws;                  // Draws in this period
        private int losses;                 // Losses in this period
        private int goalsScored;
        private int goalsConceded;
        private String recentForm;          // e.g., "LLDLD"
        private String lastWinDate;         // Date of last win
        private String lastWinOpponent;     // Opponent in last win
    }

    /**
     * Team scoring the most goals in recent matches.
     */
    @Data
    @Builder
    public static class TopScorer {
        private String teamName;
        private int goalsScored;            // Total goals in last N matches
        private int matchesAnalyzed;        // Number of matches analyzed
        private double avgGoalsPerMatch;
        private int highestScoringMatch;    // Most goals in a single match
        private String highestScoringOpponent;
        private String recentForm;
    }

    /**
     * Team with strong defensive record (most clean sheets).
     */
    @Data
    @Builder
    public static class DefensiveWall {
        private String teamName;
        private int cleanSheets;            // Clean sheets in last N matches
        private int matchesAnalyzed;
        private double cleanSheetPercentage;
        private int goalsConceded;          // Total goals conceded
        private double avgGoalsConceded;
        private int currentCleanSheetStreak; // Consecutive clean sheets
    }

    /**
     * Upcoming match where away team is predicted to win.
     */
    @Data
    @Builder
    public static class UpsetAlert {
        private String homeTeam;
        private String awayTeam;
        private String matchDate;
        private double awayWinProbability;   // >50%
        private double homeWinProbability;
        private double drawProbability;
        private String confidence;           // HIGH, MEDIUM, LOW
        private String reason;               // Why this is an upset (form differential, etc.)
        private int homeTeamFormPoints;      // Recent form as points
        private int awayTeamFormPoints;
    }

    /**
     * Match predicted to have high goal scoring.
     */
    @Data
    @Builder
    public static class GoalFestMatch {
        private String homeTeam;
        private String awayTeam;
        private String matchDate;
        private double expectedTotalGoals;   // Predicted total goals
        private double homeTeamAvgScoring;   // Home team's recent avg goals scored
        private double awayTeamAvgScoring;   // Away team's recent avg goals scored
        private double homeTeamAvgConceding; // Home team's recent avg goals conceded
        private double awayTeamAvgConceding; // Away team's recent avg goals conceded
        private double over25Probability;    // Estimated probability of over 2.5 goals
        private double bttsPercentage;       // Both teams to score probability
    }
}

