package com.app.footballprediction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import com.app.common.dto.MatchInjuryContextDTO;
import java.util.List;

@Data
@Builder
@Schema(description = "Match prediction response with probabilities, features, and H2H insights")
public class PredictResponse {

   @Schema(description = "Home team name", example = "Arsenal")
   private String homeTeam;
   @Schema(description = "Away team name", example = "Chelsea")
   private String awayTeam;

   @Schema(description = "Predicted outcome", example = "HOME_WIN", allowableValues = {"HOME_WIN", "DRAW", "AWAY_WIN"})
   private String prediction;
   @Schema(description = "Short prediction code", example = "H", allowableValues = {"H", "D", "A"})
   private String predictionCode;

   @Schema(description = "Probability of home win (0-1)", example = "0.55")
   private double probHomeWin;
   @Schema(description = "Probability of draw (0-1)", example = "0.25")
   private double probDraw;
   @Schema(description = "Probability of away win (0-1)", example = "0.20")
   private double probAwayWin;

   @Schema(description = "Prediction confidence level", example = "HIGH", allowableValues = {"HIGH", "MEDIUM", "LOW"})
   private String confidence;

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

   // Score prediction (Poisson model)
   private ScorePredictionDTO scorePrediction;

   // Player Availability Context (Phase 10)
   @Schema(description = "Home team squad availability")
   private PlayerAvailabilityDTO homeAvailability;
   @Schema(description = "Away team squad availability")
   private PlayerAvailabilityDTO awayAvailability;
    @Schema(description = "Summary note about player availability impact",
            example = "Chelsea missing Reece James (injury)")
    private String availabilityNote;

    // Injury Context (API-Football integration)
    @Schema(description = "Detailed injury/suspension context from API-Football")
    private MatchInjuryContextDTO injuryContext;
    @Schema(description = "Note about injury-based probability adjustments")
    private String injuryAdjustmentNote;

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
