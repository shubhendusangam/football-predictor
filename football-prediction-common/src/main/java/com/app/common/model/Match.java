package com.app.common.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "matches")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Match {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   // ── Identity ──────────────────────────────────────────
   private LocalDate matchDate;
   private String homeTeam;
   private String awayTeam;
   private String season;        // e.g., "2023-24"
   private String referee;       // Match referee

   // ── Full-Time Result ───────────────────────────────────
   private Integer fullTimeHomeGoals;   // FTHG
   private Integer fullTimeAwayGoals;   // FTAG
   private String fullTimeResult;      // FTR  ← THE LABEL

   // ── Half-Time Result ───────────────────────────────────
   private Integer halfTimeHomeGoals;   // HTHG
   private Integer halfTimeAwayGoals;   // HTAG
   private String halfTimeResult;      // HTR

   // ── Phase 2 stats ──────────────────────────────────────
   private Integer homeShots;           // HS
   private Integer awayShots;           // AS
   private Integer homeShotsOnTarget;   // HST
   private Integer awayShotsOnTarget;   // AST
   private Integer homeCorners;         // HC
   private Integer awayCorners;         // AC
   private Integer homeYellowCards;     // HY
   private Integer awayYellowCards;     // AY
   private Integer homeRedCards;        // HR
   private Integer awayRedCards;        // AR

   // ── Betting Odds ───────────────────────────────────────
   // Bet365
   private Double b365H;    // Bet365 Home Win odds
   private Double b365D;    // Bet365 Draw odds
   private Double b365A;    // Bet365 Away Win odds

   // Betway
   private Double bwH;      // Betway Home Win
   private Double bwD;      // Betway Draw
   private Double bwA;      // Betway Away

   // Interwetten
   private Double iwH;      // Interwetten Home Win
   private Double iwD;      // Interwetten Draw
   private Double iwA;      // Interwetten Away

   // Pinnacle Sports
   private Double psH;      // Pinnacle Home Win
   private Double psD;      // Pinnacle Draw
   private Double psA;      // Pinnacle Away

   // William Hill
   private Double whH;      // William Hill Home Win
   private Double whD;      // William Hill Draw
   private Double whA;      // William Hill Away

   public int getPointsForTeam(String teamName) {
      if (teamName == null) return 0;
      String normalizedName = teamName.trim();
      if (homeTeam != null && homeTeam.trim().equalsIgnoreCase(normalizedName)) {
         return switch (fullTimeResult) {
            case "H" -> 3;
            case "D" -> 1;
            default  -> 0;
         };
      } else if (awayTeam != null && awayTeam.trim().equalsIgnoreCase(normalizedName)) {
         return switch (fullTimeResult) {
            case "A" -> 3;
            case "D" -> 1;
            default  -> 0;
         };
      }
      return 0;
   }

   public int getGoalsScoredByTeam(String teamName) {
      if (teamName == null) return 0;
      String normalizedName = teamName.trim();
      if (homeTeam != null && homeTeam.trim().equalsIgnoreCase(normalizedName)) {
         return fullTimeHomeGoals != null ? fullTimeHomeGoals : 0;
      }
      if (awayTeam != null && awayTeam.trim().equalsIgnoreCase(normalizedName)) {
         return fullTimeAwayGoals != null ? fullTimeAwayGoals : 0;
      }
      return 0;
   }

   public int getGoalsConcededByTeam(String teamName) {
      if (teamName == null) return 0;
      String normalizedName = teamName.trim();
      if (homeTeam != null && homeTeam.trim().equalsIgnoreCase(normalizedName)) {
         return fullTimeAwayGoals != null ? fullTimeAwayGoals : 0;
      }
      if (awayTeam != null && awayTeam.trim().equalsIgnoreCase(normalizedName)) {
         return fullTimeHomeGoals != null ? fullTimeHomeGoals : 0;
      }
      return 0;
   }
}

