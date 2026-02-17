package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Data;

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
   }
}
