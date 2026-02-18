package com.app.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
    * Get all unique team names from the database.
    */
   public Set<String> getAllTeams() {
      List<Match> allMatches = matchRepository.findAll();
      Set<String> teams = new TreeSet<>();
      for (Match match : allMatches) {
         teams.add(match.getHomeTeam());
         teams.add(match.getAwayTeam());
      }
      return teams;
   }

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

   /**
    * For PREDICTION — no label, use today as cutoff so all
    * past matches are included.
    */
   public MatchFeatures buildFeaturesForPrediction(String homeTeam, String awayTeam) {
      return buildFeatures(homeTeam, awayTeam, LocalDate.now());
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

            .build();
   }

   // ── Feature calculators ───────────────────────────────────────────────

   /**
    * Average points per game across last `window` matches.
    * W=3, D=1, L=0 — standard football points system.
    * <p>
    * Example: [W, W, D, L, W] → (3+3+1+0+3) / 5 = 2.0
    */
   private double calcFormPoints(List<Match> matches, String teamName, int window) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .limit(window)
            .mapToInt(m -> m.getPointsForTeam(teamName))
            .average()
            .orElse(0.0);
   }

   /**
    * Average goals scored across last `window` matches.
    */
   private double calcGoalsScoredAvg(List<Match> matches, String teamName) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .limit(20)
            .mapToInt(m -> m.getGoalsScoredByTeam(teamName))
            .average()
            .orElse(0.0);
   }

   /**
    * Average goals conceded across last `window` matches.
    */
   private double calcGoalsConcededAvg(List<Match> matches, String teamName) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .limit(20)
            .mapToInt(m -> m.getGoalsConcededByTeam(teamName))
            .average()
            .orElse(0.0);
   }

   /**
    * Average total goals (home + away) per game.
    * Captures how "open" a team's matches tend to be.
    */
   private double calcTotalGoalsAvg(List<Match> matches, int window) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .limit(window)
            .mapToInt(m -> m.getFullTimeHomeGoals()
                  + m.getFullTimeAwayGoals())
            .average()
            .orElse(0.0);
   }

   /**
    * Win rate for a specific team across all H2H matches.
    * Returns 0.33 (neutral prior) when no H2H history exists.
    */
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

   /**
    * Draw rate across all H2H matches.
    * Returns 0.33 (neutral prior) when no H2H history exists.
    */
   private double calcH2HDrawRate(List<Match> h2hMatches) {
      if (h2hMatches.isEmpty()) return 0.33;

      long draws = h2hMatches.stream()
            .filter(m -> "D".equals(m.getFullTimeResult()))
            .count();

      return (double) draws / h2hMatches.size();
   }

   /**
    * Average shots on target.
    * isHome=true → reads homeShotsOnTarget column
    * isHome=false → reads awayShotsOnTarget column
    */
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

   /**
    * Average corners per game.
    */
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

   // ── Phase 3 feature calculators ───────────────────────────────────────

   /**
    * Goal difference over last N matches.
    * Positive = scoring more than conceding (strong team)
    * Negative = conceding more than scoring (weak team)
    */
   private double calcGoalDifference(List<Match> matches, String teamName, int window) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .limit(window)
            .mapToInt(m -> m.getGoalsScoredByTeam(teamName) - m.getGoalsConcededByTeam(teamName))
            .average()
            .orElse(0.0);
   }

   /**
    * Current win streak — consecutive wins from most recent match.
    * Returns 0 if last match wasn't a win.
    */
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

   /**
    * Unbeaten streak — consecutive matches without a loss.
    * Counts wins and draws.
    */
   private int calcUnbeatenStreak(List<Match> matches, String teamName) {
      if (matches.isEmpty()) return 0;

      int streak = 0;
      for (Match m : matches) {
         int points = m.getPointsForTeam(teamName);
         if (points >= 1) { // Win or Draw
            streak++;
         } else {
            break;
         }
      }
      return streak;
   }

   /**
    * Days since last match — rest/fatigue factor.
    * More rest = fresher team
    * Less rest = potential fatigue (especially < 3 days)
    */
   private int calcDaysSinceLastMatch(List<Match> matches, LocalDate beforeDate) {
      if (matches.isEmpty()) return 14; // Default: assume 2 weeks

      LocalDate lastMatchDate = matches.getFirst().getMatchDate();
      long days = java.time.temporal.ChronoUnit.DAYS.between(lastMatchDate, beforeDate);
      return (int) Math.min(days, 30); // Cap at 30 days
   }
}

