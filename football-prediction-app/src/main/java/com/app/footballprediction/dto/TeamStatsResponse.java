package com.app.footballprediction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response DTO for comprehensive team statistics.
 */
@Data
@Builder
public class TeamStatsResponse {

    private String teamName;
    private OverallStats overall;
    private HomeAwayStats homeStats;
    private HomeAwayStats awayStats;
    private GoalStats goalStats;
    private FormStats formStats;
    private List<RecentMatch> recentMatches;
    private SeasonStats currentSeason;
    private List<H2HRecord> topRivals;

    /**
     * Overall statistics across all matches.
     */
    @Data
    @Builder
    public static class OverallStats {
        private int totalMatches;
        private int wins;
        private int draws;
        private int losses;
        private double winPercentage;
        private int goalsScored;
        private int goalsConceded;
        private int goalDifference;
        private int points;
        private double pointsPerGame;
    }

    /**
     * Home or Away specific statistics.
     */
    @Data
    @Builder
    public static class HomeAwayStats {
        private int matches;
        private int wins;
        private int draws;
        private int losses;
        private double winPercentage;
        private int goalsScored;
        private int goalsConceded;
        private int cleanSheets;
        private double avgGoalsScored;
        private double avgGoalsConceded;
    }

    /**
     * Goal scoring and conceding patterns.
     */
    @Data
    @Builder
    public static class GoalStats {
        private double avgGoalsScored;
        private double avgGoalsConceded;
        private double avgTotalGoalsPerMatch;
        private int firstHalfGoals;
        private int secondHalfGoals;
        private int firstHalfConceded;
        private int secondHalfConceded;
        private double firstHalfScoringRate;  // % of goals in first half
        private double secondHalfScoringRate; // % of goals in second half
        private int cleanSheets;
        private int failedToScore;  // matches with 0 goals
        private double cleanSheetPercentage;
    }

    /**
     * Form and momentum statistics.
     */
    @Data
    @Builder
    public static class FormStats {
        private double last5FormPoints;       // avg points last 5
        private double last10FormPoints;      // avg points last 10
        private String last5Form;             // e.g., "WWDLW"
        private String last10Form;            // e.g., "WWDLWWLDWD"
        private int currentWinStreak;
        private int currentUnbeatenStreak;
        private int currentWinlessStreak;
        private int longestWinStreak;
        private int longestUnbeatenStreak;
        private double avgShotsOnTarget;
        private double avgCorners;
        private double shotConversionRate;    // goals / shots on target
    }

    /**
     * Recent match result for form visualization.
     */
    @Data
    @Builder
    public static class RecentMatch {
        private String date;
        private String opponent;
        @JsonProperty("isHome")
        private boolean isHome;
        private int goalsFor;
        private int goalsAgainst;
        private String result;  // "W", "D", "L"
        private String score;   // e.g., "2-1"
    }

    /**
     * Current season summary.
     */
    @Data
    @Builder
    public static class SeasonStats {
        private String season;
        private int matchesPlayed;
        private int wins;
        private int draws;
        private int losses;
        private int points;
        private int goalsScored;
        private int goalsConceded;
        private int goalDifference;
        private int position;  // estimated league position
    }

    /**
     * Head-to-head record against a specific team.
     */
    @Data
    @Builder
    public static class H2HRecord {
        private String opponent;
        private int totalMatches;
        private int wins;
        private int draws;
        private int losses;
        private int goalsFor;
        private int goalsAgainst;
        private double winPercentage;
    }
}

