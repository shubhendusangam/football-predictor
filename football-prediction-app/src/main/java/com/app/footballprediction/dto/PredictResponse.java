package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PredictResponse {

   private String homeTeam;
   private String awayTeam;

   private String prediction;      // "HOME_WIN", "DRAW", "AWAY_WIN"
   private String predictionCode;  // "H", "D", "A"

   private double probHomeWin;
   private double probDraw;
   private double probAwayWin;

   private String confidence;      // "HIGH", "MEDIUM", "LOW"

   private FeatureSummary features;

   // Enhanced H2H Insights
   private H2HSummary h2hInsights;

   // Elo Rating Fields
   private Double homeElo;
   private Double awayElo;
   private Double eloDifference;
   private Boolean upsetAlert;
   private String upsetTeam;
   private PredictionExplanation explanation;

   @Data
   @Builder
   public static class FeatureSummary {
      private double homeFormPoints;
      private double awayFormPoints;
      private double homeGoalsScoredAvg;
      private double awayGoalsScoredAvg;
      private double h2hHomeWinRate;
      private double h2hDrawRate;
      private double h2hAwayWinRate;

      // Pre-Match Insights Panel fields
      private double homeGoalsConcededAvg;
      private double awayGoalsConcededAvg;
      private int homeWinStreak;
      private int awayWinStreak;
      private int homeUnbeatenStreak;
      private int awayUnbeatenStreak;
      private int homeDaysSinceLastMatch;
      private int awayDaysSinceLastMatch;
      private double homeGoalThreat;         // Goal threat meter (0-100)
      private double awayGoalThreat;         // Goal threat meter (0-100)
   }

   /**
    * Enhanced H2H insights summary for prediction response.
    */
   @Data
   @Builder
   public static class H2HSummary {
      // Historical Record: "Arsenal leads 15-8-7 vs Chelsea"
      private String historicalRecord;
      private int totalMeetings;
      private int homeTeamWins;
      private int draws;
      private int awayTeamWins;
      private String dominantTeam;     // "HOME", "AWAY", "EVEN"

      // Recent H2H Timeline (last 5 meetings)
      private List<RecentH2HMatch> recentMeetings;

      // H2H Goal Stats
      private double avgGoalsPerMatch;
      private double avgHomeTeamGoals;
      private double avgAwayTeamGoals;

      // Common Results
      private String mostCommonScore;
      private String mostCommonOutcome; // "HOME_WIN", "DRAW", "AWAY_WIN"

      // Venue Advantage
      private double homeTeamHomeWinPct;  // Win % when homeTeam plays at home vs awayTeam
      private double awayTeamHomeWinPct;  // Win % when awayTeam plays at home vs homeTeam
      private String venueAdvantageNote;
   }

   /**
    * Recent H2H match for timeline display.
    */
   @Data
   @Builder
   public static class RecentH2HMatch {
      private String date;
      private String homeTeamInMatch;
      private String awayTeamInMatch;
      private String score;      // e.g., "2-1"
      private String winner;     // Team name or "Draw"
      private String season;
   }
}
