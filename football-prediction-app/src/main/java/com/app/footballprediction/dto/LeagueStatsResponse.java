package com.app.footballprediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for comprehensive league statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeagueStatsResponse {

    private SeasonOverview seasonOverview;
    private List<GoalsTrend> goalsTrends;
    private HomeAdvantageStats homeAdvantage;
    private RecordMatches recordMatches;
    private List<ScorelineStats> commonScorelines;
    private HalfTimeStats halfTimeStats;
    private List<RefereeStats> refereeStats;
    private int totalMatchesAnalyzed;
    private String generatedAt;

    /**
     * Season Overview Panel data.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeasonOverview {
        private int totalMatches;
        private int totalGoals;
        private int homeGoals;
        private int awayGoals;
        private double avgGoalsPerMatch;
        private double avgHomeGoals;
        private double avgAwayGoals;
        private double homeWinPercentage;
        private double drawPercentage;
        private double awayWinPercentage;
        private double cleanSheetPercentage;
    }

    /**
     * Goals trend data per season.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoalsTrend {
        private String season;
        private int totalGoals;
        private double avgGoalsPerMatch;
        private int homeGoals;
        private int awayGoals;
        private int matchesPlayed;
        private double homeWinRate;
    }

    /**
     * Home advantage statistics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HomeAdvantageStats {
        private double overallHomeWinRate;
        private double overallAwayWinRate;
        private int homeWinCount;
        private int awayWinCount;
        private List<SeasonHomeAdvantage> seasonTrends;
    }

    /**
     * Home advantage trend per season.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeasonHomeAdvantage {
        private String season;
        private double homeWinRate;
        private double awayWinRate;
        private double homeGoalsAvg;
        private double awayGoalsAvg;
    }

    /**
     * Record matches data.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecordMatches {
        private List<RecordMatch> biggestWins;
        private List<RecordMatch> highestScoringGames;
    }

    /**
     * Individual record match.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecordMatch {
        private String homeTeam;
        private String awayTeam;
        private int homeGoals;
        private int awayGoals;
        private String date;
        private String season;
        private int totalGoals;
        private int goalDifference;
    }

    /**
     * Scoreline statistics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScorelineStats {
        private String scoreline;
        private int count;
        private double percentage;
    }

    /**
     * Half-time statistics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HalfTimeStats {
        private int totalFirstHalfGoals;
        private int totalSecondHalfGoals;
        private double avgFirstHalfGoals;
        private double avgSecondHalfGoals;
        private double firstHalfPercentage;
        private double secondHalfPercentage;
        private int matchesAnalyzed;
    }

    /**
     * Referee statistics.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefereeStats {
        private String refereeName;
        private int matchesOfficiatedAsReferee;
        private int totalCards;
        private double avgCardsPerMatch;
        private int totalRedCards;
        private double homeWinRate;
    }
}

