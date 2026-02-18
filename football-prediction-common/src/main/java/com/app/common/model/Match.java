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

   public int getPointsForTeam(String teamName) {
      if (homeTeam.equalsIgnoreCase(teamName)) {
         return switch (fullTimeResult) {
            case "H" -> 3;
            case "D" -> 1;
            default  -> 0;
         };
      } else if (awayTeam.equalsIgnoreCase(teamName)) {
         return switch (fullTimeResult) {
            case "A" -> 3;
            case "D" -> 1;
            default  -> 0;
         };
      }
      return 0;
   }

   public int getGoalsScoredByTeam(String teamName) {
      if (homeTeam.equalsIgnoreCase(teamName)) return fullTimeHomeGoals;
      if (awayTeam.equalsIgnoreCase(teamName)) return fullTimeAwayGoals;
      return 0;
   }

   public int getGoalsConcededByTeam(String teamName) {
      if (homeTeam.equalsIgnoreCase(teamName)) return fullTimeAwayGoals;
      if (awayTeam.equalsIgnoreCase(teamName)) return fullTimeHomeGoals;
      return 0;
   }
}

