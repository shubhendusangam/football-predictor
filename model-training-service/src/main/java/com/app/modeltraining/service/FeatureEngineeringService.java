package com.app.modeltraining.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

import com.app.common.model.Match;
import com.app.common.model.MatchFeatures;
import com.app.common.repository.MatchRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureEngineeringService {

   private final MatchRepository matchRepository;

   @Value("${feature.form.window:5}")
   private int formWindow;

   // ── Public API ────────────────────────────────────────────────────────


   /**
    * For TRAINING — pass the actual match so we can set the label
    * and use matchDate as the cutoff (no leakage).
    */
   public MatchFeatures buildFeaturesForTraining(Match match) {
      MatchFeatures features = buildFeatures(
            match.getHomeTeam(),
            match.getAwayTeam(),
            match.getMatchDate()
      );
      features.setActualResult(match.getFullTimeResult());
      return features;
   }


   // ── Core builder ──────────────────────────────────────────────────────

   private MatchFeatures buildFeatures(String homeTeam, String awayTeam, LocalDate beforeDate) {
      log.debug("Building features: {} vs {} (before {})", homeTeam, awayTeam, beforeDate);

      // Fetch all relevant histories from DB in one go
      List<Match> homeTeamHomeMatches = matchRepository
            .findHomeMatchesByTeamBeforeDate(homeTeam, beforeDate);

      List<Match> awayTeamAwayMatches = matchRepository
            .findAwayMatchesByTeamBeforeDate(awayTeam, beforeDate);

      List<Match> homeTeamAllMatches = matchRepository
            .findByTeamBeforeDate(homeTeam, beforeDate);

      List<Match> awayTeamAllMatches = matchRepository
            .findByTeamBeforeDate(awayTeam, beforeDate);

      List<Match> h2hMatches = matchRepository
            .findH2HBeforeDate(homeTeam, awayTeam, beforeDate);

      return MatchFeatures.builder()
            .homeTeam(homeTeam)
            .awayTeam(awayTeam)

            // Form: points per game in last N home/away matches
            .homeFormPoints(
                  calcFormPoints(homeTeamHomeMatches, homeTeam, formWindow))
            .awayFormPoints(
                  calcFormPoints(awayTeamAwayMatches, awayTeam, formWindow))

            // Goals: avg scored and conceded at home / away
            .homeGoalsScoredAvg(
                  calcGoalsScoredAvg(homeTeamHomeMatches, homeTeam))
            .homeGoalsConcededAvg(
                  calcGoalsConcededAvg(homeTeamHomeMatches, homeTeam))
            .awayGoalsScoredAvg(
                  calcGoalsScoredAvg(awayTeamAwayMatches, awayTeam))
            .awayGoalsConcededAvg(
                  calcGoalsConcededAvg(awayTeamAwayMatches, awayTeam))

            // Total goals per game across all matches
            .homeTotalGoalsAvg(
                  calcTotalGoalsAvg(homeTeamAllMatches, formWindow))
            .awayTotalGoalsAvg(
                  calcTotalGoalsAvg(awayTeamAllMatches, formWindow))

            // Head-to-head rates
            .h2hHomeWinRate(calcH2HWinRate(h2hMatches, homeTeam))
            .h2hDrawRate(calcH2HDrawRate(h2hMatches))
            .h2hAwayWinRate(calcH2HWinRate(h2hMatches, awayTeam))

            // Phase 2: shots on target averages
            .homeShotsOnTargetAvg(
                  calcShotsOnTargetAvg(homeTeamHomeMatches, true))
            .awayShotsOnTargetAvg(
                  calcShotsOnTargetAvg(awayTeamAwayMatches, false))

            // Phase 2: corners averages
            .homeCornersAvg(
                  calcCornersAvg(homeTeamHomeMatches, true))
            .awayCornersAvg(
                  calcCornersAvg(awayTeamAwayMatches, false))

            // Phase 3: Goal difference (attacking strength - defensive weakness)
            .homeGoalDifference(
                  calcGoalDifference(homeTeamAllMatches, homeTeam, formWindow))
            .awayGoalDifference(
                  calcGoalDifference(awayTeamAllMatches, awayTeam, formWindow))

            // Phase 3: Overall form (all matches, not just home/away specific)
            .homeOverallFormPoints(
                  calcFormPoints(homeTeamAllMatches, homeTeam, formWindow))
            .awayOverallFormPoints(
                  calcFormPoints(awayTeamAllMatches, awayTeam, formWindow))

            // Phase 3: Win streaks (momentum)
            .homeWinStreak(calcWinStreak(homeTeamAllMatches, homeTeam))
            .awayWinStreak(calcWinStreak(awayTeamAllMatches, awayTeam))

            // Phase 3: Unbeaten streaks
            .homeUnbeatenStreak(calcUnbeatenStreak(homeTeamAllMatches, homeTeam))
            .awayUnbeatenStreak(calcUnbeatenStreak(awayTeamAllMatches, awayTeam))

            // Phase 3: Days since last match (rest/fatigue)
            .homeDaysSinceLastMatch(calcDaysSinceLastMatch(homeTeamAllMatches, beforeDate))
            .awayDaysSinceLastMatch(calcDaysSinceLastMatch(awayTeamAllMatches, beforeDate))

            // Phase 5: Possession proxy (estimated from shots + corners)
            .homePossessionProxy(estimatePossession(homeTeamHomeMatches, true))
            .awayPossessionProxy(estimatePossession(awayTeamAwayMatches, false))

            .build();
   }

   // ── Feature calculators ───────────────────────────────────────────────

   /**
    * Average points per game across last `window` matches.
    */
   private double calcFormPoints(List<Match> matches, String teamName, int window) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .limit(window)
            .mapToInt(m -> m.getPointsForTeam(teamName))
            .average()
            .orElse(0.0);
   }

   private double calcGoalsScoredAvg(List<Match> matches, String teamName) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .limit(20)
            .mapToInt(m -> m.getGoalsScoredByTeam(teamName))
            .average()
            .orElse(0.0);
   }

   private double calcGoalsConcededAvg(List<Match> matches, String teamName) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .limit(20)
            .mapToInt(m -> m.getGoalsConcededByTeam(teamName))
            .average()
            .orElse(0.0);
   }

   private double calcTotalGoalsAvg(List<Match> matches, int window) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .limit(window)
            .mapToInt(m -> m.getFullTimeHomeGoals()
                  + m.getFullTimeAwayGoals())
            .average()
            .orElse(0.0);
   }

   private double calcH2HWinRate(List<Match> h2hMatches, String teamName) {
      if (h2hMatches.isEmpty()) return 0.33;

      long wins = h2hMatches.stream()
            .filter(m -> {
               if (m.getHomeTeam().equalsIgnoreCase(teamName))
                  return "H".equals(m.getFullTimeResult());
               if (m.getAwayTeam().equalsIgnoreCase(teamName))
                  return "A".equals(m.getFullTimeResult());
               return false;
            })
            .count();

      return (double) wins / h2hMatches.size();
   }

   private double calcH2HDrawRate(List<Match> h2hMatches) {
      if (h2hMatches.isEmpty()) return 0.33;

      long draws = h2hMatches.stream()
            .filter(m -> "D".equals(m.getFullTimeResult()))
            .count();

      return (double) draws / h2hMatches.size();
   }

   private double calcShotsOnTargetAvg(List<Match> matches, boolean isHome) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .limit(10)
            .filter(m -> isHome
                  ? m.getHomeShotsOnTarget() != null
                  : m.getAwayShotsOnTarget() != null)
            .mapToInt(m -> isHome
                  ? m.getHomeShotsOnTarget()
                  : m.getAwayShotsOnTarget())
            .average()
            .orElse(0.0);
   }

   private double calcCornersAvg(List<Match> matches, boolean isHome) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .limit(10)
            .filter(m -> isHome
                  ? m.getHomeCorners() != null
                  : m.getAwayCorners() != null)
            .mapToInt(m -> isHome
                  ? m.getHomeCorners()
                  : m.getAwayCorners())
            .average()
            .orElse(0.0);
   }

   private double calcGoalDifference(List<Match> matches, String teamName, int window) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .limit(window)
            .mapToInt(m -> m.getGoalsScoredByTeam(teamName) - m.getGoalsConcededByTeam(teamName))
            .average()
            .orElse(0.0);
   }

   private int calcWinStreak(List<Match> matches, String teamName) {
      if (matches.isEmpty()) return 0;

      int streak = 0;
      for (Match m : matches) {
         if (m.getPointsForTeam(teamName) == 3) {
            streak++;
         } else {
            break;
         }
      }
      return streak;
   }

   private int calcUnbeatenStreak(List<Match> matches, String teamName) {
      if (matches.isEmpty()) return 0;

      int streak = 0;
      for (Match m : matches) {
         int points = m.getPointsForTeam(teamName);
         if (points >= 1) {
            streak++;
         } else {
            break;
         }
      }
      return streak;
   }

   private int calcDaysSinceLastMatch(List<Match> matches, LocalDate beforeDate) {
      if (matches.isEmpty()) return 14;

      LocalDate lastMatchDate = matches.getFirst().getMatchDate();
      long days = java.time.temporal.ChronoUnit.DAYS.between(lastMatchDate, beforeDate);
      return (int) Math.min(days, 30);
   }

   /**
    * Estimate possession percentage using shots and corners as proxies.
    *
    * Formula: possession = (shotRatio × 0.6) + (cornerRatio × 0.4)
    * Where:
    * - shotRatio = teamShots / (teamShots + opponentShots)
    * - cornerRatio = teamCorners / (teamCorners + opponentCorners)
    *
    * @param matches List of matches to analyze
    * @param isHome Whether the team is playing at home in these matches
    * @return Estimated possession as double (0.0 to 1.0, representing 0% to 100%)
    */
   public double estimatePossession(List<Match> matches, boolean isHome) {
      if (matches == null || matches.isEmpty()) {
         return 0.5; // Default to 50% when no data
      }

      double totalShotRatio = 0.0;
      double totalCornerRatio = 0.0;
      int validShotMatches = 0;
      int validCornerMatches = 0;

      for (Match match : matches) {
         // Calculate shot ratio
         Integer teamShots;
         Integer opponentShots;
         Integer teamCorners;
         Integer opponentCorners;

         if (isHome) {
            teamShots = match.getHomeShots();
            opponentShots = match.getAwayShots();
            teamCorners = match.getHomeCorners();
            opponentCorners = match.getAwayCorners();
         } else {
            teamShots = match.getAwayShots();
            opponentShots = match.getHomeShots();
            teamCorners = match.getAwayCorners();
            opponentCorners = match.getHomeCorners();
         }

         // Shot ratio calculation (null-safe)
         if (teamShots != null && opponentShots != null) {
            int totalShots = teamShots + opponentShots;
            if (totalShots > 0) {
               totalShotRatio += (double) teamShots / totalShots;
               validShotMatches++;
            }
         }

         // Corner ratio calculation (null-safe)
         if (teamCorners != null && opponentCorners != null) {
            int totalCorners = teamCorners + opponentCorners;
            if (totalCorners > 0) {
               totalCornerRatio += (double) teamCorners / totalCorners;
               validCornerMatches++;
            }
         }
      }

      // Calculate average ratios
      double avgShotRatio = validShotMatches > 0 ? totalShotRatio / validShotMatches : 0.5;
      double avgCornerRatio = validCornerMatches > 0 ? totalCornerRatio / validCornerMatches : 0.5;

      // Weighted formula: 60% shots, 40% corners
      double possession = (avgShotRatio * 0.6) + (avgCornerRatio * 0.4);

      // Clamp to [0.0, 1.0] range
      return Math.max(0.0, Math.min(1.0, possession));
   }
}

