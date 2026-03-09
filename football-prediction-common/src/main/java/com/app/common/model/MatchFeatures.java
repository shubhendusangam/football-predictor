package com.app.common.model;

import lombok.Builder;
import lombok.Data;

/**
 * Feature vector for match prediction.
 * Contains all computed features used for model training and prediction.
 */
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

   // ── Phase 3 features ───────────────────────────────────
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

   // ── Phase 4 features (Half-Time & League Position) ─────
   /**
    * Rate at which home team leads at half-time.
    * Indicates team's ability to start strong.
    */
   @Builder.Default
   private double homeHalfTimeLeadRate = 0.0;

   /**
    * Rate at which away team leads at half-time.
    */
   @Builder.Default
   private double awayHalfTimeLeadRate = 0.0;

   /**
    * Rate at which home team comes back after trailing at half-time.
    * Indicates mental strength and fitness.
    */
   @Builder.Default
   private double homeComebackRate = 0.0;

   /**
    * Rate at which away team comes back after trailing at half-time.
    */
   @Builder.Default
   private double awayComebackRate = 0.0;

   /**
    * Home team's league position (1 = top, 20 = bottom).
    * Lower is better.
    */
   @Builder.Default
   private int homeLeaguePosition = 10;

   /**
    * Away team's league position (1 = top, 20 = bottom).
    */
   @Builder.Default
   private int awayLeaguePosition = 10;

   // ── Phase 5 features (Possession Proxy) ────────────────────
   /**
    * Estimated home team possession (0.0 to 1.0).
    * Calculated as: (shotRatio × 0.6) + (cornerRatio × 0.4)
    * where shotRatio = teamShots / (teamShots + opponentShots)
    */
   @Builder.Default
   private double homePossessionProxy = 0.5;

   /**
    * Estimated away team possession (0.0 to 1.0).
    * Should approximately equal (1.0 - homePossessionProxy).
    */
   @Builder.Default
   private double awayPossessionProxy = 0.5;

   // ── Phase 6 features (Elo Ratings) ─────────────────────
   /**
    * Home team's current Elo rating (default 1500).
    */
   @Builder.Default
   private double homeEloRating = 1500.0;

   /**
    * Away team's current Elo rating (default 1500).
    */
   @Builder.Default
   private double awayEloRating = 1500.0;

   // ── Phase 7 features (Derived Interaction Features) ────
   /**
    * formDifference = homeFormPoints - awayFormPoints.
    * Positive means home team has better recent form.
    */
   @Builder.Default
   private double formDifference = 0.0;

   /**
    * goalDiffDifference = homeGoalDifference - awayGoalDifference.
    * Captures relative attacking/defensive balance.
    */
   @Builder.Default
   private double goalDiffDifference = 0.0;

   /**
    * h2hDominance = h2hHomeWinRate - h2hAwayWinRate.
    * Positive means home team historically dominates this matchup.
    */
   @Builder.Default
   private double h2hDominance = 0.0;

   /**
    * restAdvantage = homeDaysSinceLastMatch - awayDaysSinceLastMatch.
    * Positive means home team had more rest.
    */
   @Builder.Default
   private double restAdvantage = 0.0;

   /**
    * eloDifference = homeEloRating - awayEloRating.
    * Positive means home team is rated higher.
    */
   @Builder.Default
   private double eloDifference = 0.0;

   /**
    * Recency-weighted form for home team (exponential decay, most recent weighted highest).
    */
   @Builder.Default
   private double homeWeightedForm = 0.0;

   /**
    * Recency-weighted form for away team (exponential decay, most recent weighted highest).
    */
   @Builder.Default
   private double awayWeightedForm = 0.0;

   // ── Label (training only) ──────────────────────────────
   private String actualResult;        // "H", "D", "A" — null at prediction time
}

