package com.app.modeltraining.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MatchFeatures {

   // not Weka features — just for logging
   private String homeTeam;
   private String awayTeam;

   // ── Phase 1 features ───────────────────────────────────
   private double homeFormPoints;
   private double awayFormPoints;
   private double homeGoalsScoredAvg;
   private double homeGoalsConcededAvg;
   private double awayGoalsScoredAvg;
   private double awayGoalsConcededAvg;
   private double h2hHomeWinRate;
   private double h2hDrawRate;
   private double h2hAwayWinRate;
   private double homeTotalGoalsAvg;
   private double awayTotalGoalsAvg;

   // ── Phase 2 features ───────────────────────────────────
   private double homeShotsOnTargetAvg;
   private double awayShotsOnTargetAvg;
   private double homeCornersAvg;
   private double awayCornersAvg;

   // ── Phase 3 features (NEW) ─────────────────────────────
   private double homeGoalDifference;     // Goals scored - goals conceded (last N)
   private double awayGoalDifference;
   private double homeOverallFormPoints;  // Form across ALL matches (not just home/away)
   private double awayOverallFormPoints;
   private int homeWinStreak;             // Current consecutive wins (0 if last wasn't win)
   private int awayWinStreak;
   private int homeUnbeatenStreak;        // Consecutive matches without loss
   private int awayUnbeatenStreak;
   private int homeDaysSinceLastMatch;    // Rest days (fatigue factor)
   private int awayDaysSinceLastMatch;

   // ── Phase 5 features (Possession Proxy) ────────────────
   @Builder.Default
   private double homePossessionProxy = 0.5;  // Estimated possession (0.0 to 1.0)
   @Builder.Default
   private double awayPossessionProxy = 0.5;

   // ── Label (training only) ──────────────────────────────
   private String actualResult;        // "H", "D", "A" — null at prediction time
}

