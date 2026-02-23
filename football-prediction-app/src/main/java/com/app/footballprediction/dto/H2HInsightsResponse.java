package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response DTO for enhanced Head-to-Head (H2H) insights between two teams.
 */
@Data
@Builder
public class H2HInsightsResponse {

    private String homeTeam;
    private String awayTeam;

    // Historical Record Display: "Arsenal leads 15-8-7 vs Chelsea" format
    private HistoricalRecord historicalRecord;

    // Recent H2H Timeline: Last 5 meetings with results and scorelines
    private List<H2HMatch> recentMeetings;

    // H2H Goal Stats: Average goals when these teams meet
    private H2HGoalStats goalStats;

    // Common Results: Most frequent outcome in H2H matchups
    private CommonResultStats commonResults;

    // Venue Advantage: H2H win % based on home/away
    private VenueAdvantageStats venueAdvantage;

    /**
     * Historical record display in "Team leads W-D-L vs Opponent" format.
     */
    @Data
    @Builder
    public static class HistoricalRecord {
        private int totalMatches;
        private int homeTeamWins;   // Wins for homeTeam across all H2H (regardless of venue)
        private int draws;
        private int awayTeamWins;   // Wins for awayTeam across all H2H
        private String summary;     // e.g., "Arsenal leads 15-8-7 vs Chelsea"
        private String dominantTeam; // "HOME", "AWAY", or "EVEN"
        private double homeTeamWinPercentage;
        private double awayTeamWinPercentage;
        private double drawPercentage;
    }

    /**
     * Individual H2H match details for recent meetings timeline.
     */
    @Data
    @Builder
    public static class H2HMatch {
        private String date;
        private String homeTeamInMatch;  // Actual home team in that match
        private String awayTeamInMatch;  // Actual away team in that match
        private int homeGoals;
        private int awayGoals;
        private String score;           // e.g., "2-1"
        private String result;          // "H", "D", "A"
        private String winner;          // Team name or "Draw"
        private String season;          // e.g., "2024-25"
    }

    /**
     * Goal statistics when these teams meet.
     */
    @Data
    @Builder
    public static class H2HGoalStats {
        private double avgTotalGoals;        // Average total goals per H2H match
        private double avgHomeTeamGoals;     // Avg goals for homeTeam in H2H (regardless of venue)
        private double avgAwayTeamGoals;     // Avg goals for awayTeam in H2H
        private int totalGoalsAllTime;       // Sum of all goals in H2H history
        private int highestScoringMatch;     // Most goals in a single match
        private String highestScoringMatchDetails; // e.g., "5-4 (Arsenal vs Chelsea, 2024-10-15)"
        private int cleanSheetsHomeTeam;     // Clean sheets for homeTeam
        private int cleanSheetsAwayTeam;     // Clean sheets for awayTeam
    }

    /**
     * Most frequent result outcomes in H2H matches.
     */
    @Data
    @Builder
    public static class CommonResultStats {
        private String mostCommonResult;       // Most frequent exact score, e.g., "1-1"
        private int mostCommonResultCount;     // How often that score occurred
        private String mostCommonOutcome;      // "HOME_WIN", "DRAW", "AWAY_WIN"
        private int homeWinCount;
        private int drawCount;
        private int awayWinCount;
        private List<ScoreFrequency> topScorelines;  // Top 5 most common scores
    }

    /**
     * Frequency of a particular scoreline.
     */
    @Data
    @Builder
    public static class ScoreFrequency {
        private String scoreline;    // e.g., "1-1", "2-0"
        private int count;
        private double percentage;
    }

    /**
     * Venue-based advantage statistics.
     */
    @Data
    @Builder
    public static class VenueAdvantageStats {
        // When homeTeam plays at HOME vs awayTeam
        private int homeTeamHomeMatches;      // Matches where homeTeam was at home
        private int homeTeamHomeWins;
        private int homeTeamHomeDraws;
        private int homeTeamHomeLosses;
        private double homeTeamHomeWinPercentage;

        // When awayTeam plays at HOME vs homeTeam
        private int awayTeamHomeMatches;      // Matches where awayTeam was at home
        private int awayTeamHomeWins;
        private int awayTeamHomeDraws;
        private int awayTeamHomeLosses;
        private double awayTeamHomeWinPercentage;

        // Summary
        private String homeAdvantageDescription;  // e.g., "Arsenal have strong home advantage (75% win rate)"
    }
}

