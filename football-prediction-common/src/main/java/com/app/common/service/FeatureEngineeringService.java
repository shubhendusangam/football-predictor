package com.app.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.app.common.model.Match;
import com.app.common.model.MatchFeatures;
import com.app.common.repository.MatchRepository;

/**
 * Service for computing match features from historical data.
 * Features are used for both model training and real-time predictions.
 *
 * <p><strong>IMPORTANT: Season-Based Filtering</strong></p>
 * <p>All feature calculations are scoped to the current season to align with
 * official Premier League statistical methodology:</p>
 * <ul>
 *   <li>All queries include season + matchDate < beforeDate filters</li>
 *   <li>LIMIT is applied at DB level (not Java slicing)</li>
 *   <li>Cross-season data is excluded (except H2H which spans 5 years)</li>
 *   <li>League positions calculated within season only</li>
 * </ul>
 *
 * <p><strong>Ordering Convention</strong></p>
 * <p>All repository queries return matches in DESCENDING order (newest first).
 * Streak and form logic depends on this ordering.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureEngineeringService {

   private final MatchRepository matchRepository;

   @Autowired(required = false)
   private LeaguePositionService leaguePositionService;

   @Value("${feature.form.window:5}")
   private int formWindow;

   // ── Standardized Feature Windows ──────────────────────────────────────
   // These constants define the lookback windows for different feature types.
   // Applied at DB level via LIMIT clause for efficiency.

   /** Window for form-related features (recent momentum, ~1 month) */
   private static final int RECENT_FORM_WINDOW = 5;

   /** Window for goal and shot statistics (medium-term trends, ~2 months) */
   private static final int MEDIUM_TERM_WINDOW = 10;

   /** Window for season-level statistics (~half season) */
   private static final int SEASON_WINDOW = 20;

   /** Maximum H2H matches to consider (spans multiple seasons) */
   private static final int H2H_WINDOW = 5;

   /** Default rest days when no same-season match exists */
   private static final int DEFAULT_REST_DAYS = 14;

   /** Maximum rest days to cap feature value */
   private static final int MAX_REST_DAYS = 30;

   // ── H2H Historical Priors (Premier League 1992–2024) ──────────────────
   // When no H2H history exists, use league-wide historical distribution.
   // Source: Premier League historical data 1992-2024

   /** Historical home win rate when no H2H data exists */
   private static final double PRIOR_HOME_WIN = 0.462;

   /** Historical draw rate when no H2H data exists */
   private static final double PRIOR_DRAW = 0.268;

   /** Historical away win rate when no H2H data exists */
   private static final double PRIOR_AWAY_WIN = 0.270;

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
    * Season is derived from the match itself.
    */
   public MatchFeatures buildFeaturesForTraining(Match match) {
      String season = match.getSeason();
      if (season == null || season.isBlank()) {
         season = deriveSeason(match.getMatchDate());
      }

      MatchFeatures features = buildFeatures(
            match.getHomeTeam(),
            match.getAwayTeam(),
            match.getMatchDate(),
            season
      );
      features.setActualResult(match.getFullTimeResult());
      return features;
   }

   /**
    * For PREDICTION — no label, use today as cutoff.
    * Season is determined from the current date.
    */
   public MatchFeatures buildFeaturesForPrediction(String homeTeam, String awayTeam) {
      LocalDate today = LocalDate.now();
      String season = determineCurrentSeason(today);
      return buildFeatures(homeTeam, awayTeam, today, season);
   }

   /**
    * For PREDICTION with explicit season — used when season is known.
    */
   public MatchFeatures buildFeaturesForPrediction(String homeTeam, String awayTeam, String season) {
      return buildFeatures(homeTeam, awayTeam, LocalDate.now(), season);
   }

   // ── Core builder ──────────────────────────────────────────────────────

   /**
    * Build all features for a match with strict season-based filtering.
    *
    * @param homeTeam   Home team name
    * @param awayTeam   Away team name
    * @param beforeDate Cutoff date (exclusive) - no matches on or after this date
    * @param season     Season identifier (e.g., "2024-25")
    * @return MatchFeatures with all computed features
    */
   private MatchFeatures buildFeatures(String homeTeam, String awayTeam,
                                        LocalDate beforeDate, String season) {
      log.debug("Building features: {} vs {} (season={}, before={})",
                homeTeam, awayTeam, season, beforeDate);

      // ── Fetch all data with season filtering and DB-level limits ──────

      // Home team's home matches (season-filtered, limited)
      List<Match> homeTeamHomeMatches = matchRepository
            .findHomeMatchesByTeamSeasonBeforeDateLimited(
                  homeTeam, season, beforeDate, SEASON_WINDOW);

      // Away team's away matches (season-filtered, limited)
      List<Match> awayTeamAwayMatches = matchRepository
            .findAwayMatchesByTeamSeasonBeforeDateLimited(
                  awayTeam, season, beforeDate, SEASON_WINDOW);

      // All matches for each team (season-filtered, limited)
      List<Match> homeTeamAllMatches = matchRepository
            .findByTeamSeasonBeforeDateLimited(
                  homeTeam, season, beforeDate, SEASON_WINDOW);

      List<Match> awayTeamAllMatches = matchRepository
            .findByTeamSeasonBeforeDateLimited(
                  awayTeam, season, beforeDate, SEASON_WINDOW);

      // H2H matches (cross-season, limited to 5)
      List<Match> h2hMatches = matchRepository
            .findH2HBeforeDateLimited(homeTeam, awayTeam, beforeDate, H2H_WINDOW);

      // Shots data with null filtering at DB level
      List<Match> homeMatchesWithShots = matchRepository
            .findHomeMatchesWithShotsData(homeTeam, season, beforeDate, MEDIUM_TERM_WINDOW);
      List<Match> awayMatchesWithShots = matchRepository
            .findAwayMatchesWithShotsData(awayTeam, season, beforeDate, MEDIUM_TERM_WINDOW);

      // Corners data with null filtering at DB level
      List<Match> homeMatchesWithCorners = matchRepository
            .findHomeMatchesWithCornersData(homeTeam, season, beforeDate, MEDIUM_TERM_WINDOW);
      List<Match> awayMatchesWithCorners = matchRepository
            .findAwayMatchesWithCornersData(awayTeam, season, beforeDate, MEDIUM_TERM_WINDOW);

      return MatchFeatures.builder()
            .homeTeam(homeTeam)
            .awayTeam(awayTeam)

            // Form: points per game (already limited at DB level)
            .homeFormPoints(calcFormPoints(homeTeamHomeMatches, homeTeam, formWindow))
            .awayFormPoints(calcFormPoints(awayTeamAwayMatches, awayTeam, formWindow))

            // Goals: avg scored and conceded (season-scoped)
            .homeGoalsScoredAvg(calcGoalsScoredAvg(homeTeamHomeMatches, homeTeam))
            .homeGoalsConcededAvg(calcGoalsConcededAvg(homeTeamHomeMatches, homeTeam))
            .awayGoalsScoredAvg(calcGoalsScoredAvg(awayTeamAwayMatches, awayTeam))
            .awayGoalsConcededAvg(calcGoalsConcededAvg(awayTeamAwayMatches, awayTeam))

            // Total goals per game
            .homeTotalGoalsAvg(calcTotalGoalsAvg(homeTeamAllMatches, formWindow))
            .awayTotalGoalsAvg(calcTotalGoalsAvg(awayTeamAllMatches, formWindow))

            // Head-to-head rates (cross-season, limited to 5)
            .h2hHomeWinRate(calcH2HWinRate(h2hMatches, homeTeam, true))
            .h2hDrawRate(calcH2HDrawRate(h2hMatches))
            .h2hAwayWinRate(calcH2HWinRate(h2hMatches, awayTeam, false))

            // Shots on target (using pre-filtered data)
            .homeShotsOnTargetAvg(calcShotsOnTargetAvgFromFiltered(homeMatchesWithShots, true))
            .awayShotsOnTargetAvg(calcShotsOnTargetAvgFromFiltered(awayMatchesWithShots, false))

            // Corners (using pre-filtered data)
            .homeCornersAvg(calcCornersAvgFromFiltered(homeMatchesWithCorners, true))
            .awayCornersAvg(calcCornersAvgFromFiltered(awayMatchesWithCorners, false))

            // Goal difference (season-scoped)
            .homeGoalDifference(calcGoalDifference(homeTeamAllMatches, homeTeam, RECENT_FORM_WINDOW))
            .awayGoalDifference(calcGoalDifference(awayTeamAllMatches, awayTeam, RECENT_FORM_WINDOW))

            // Overall form (all matches, season-scoped)
            .homeOverallFormPoints(calcFormPoints(homeTeamAllMatches, homeTeam, formWindow))
            .awayOverallFormPoints(calcFormPoints(awayTeamAllMatches, awayTeam, formWindow))

            // Streaks (stop at first non-qualifying result)
            .homeWinStreak(calcWinStreak(homeTeamAllMatches, homeTeam))
            .awayWinStreak(calcWinStreak(awayTeamAllMatches, awayTeam))
            .homeUnbeatenStreak(calcUnbeatenStreak(homeTeamAllMatches, homeTeam))
            .awayUnbeatenStreak(calcUnbeatenStreak(awayTeamAllMatches, awayTeam))

            // Days since last match (season-scoped, ignores cross-season)
            .homeDaysSinceLastMatch(calcDaysSinceLastMatch(homeTeam, season, beforeDate))
            .awayDaysSinceLastMatch(calcDaysSinceLastMatch(awayTeam, season, beforeDate))

            // Half-time features (season-scoped)
            .homeHalfTimeLeadRate(calcHalfTimeLeadRate(homeTeamHomeMatches, homeTeam))
            .awayHalfTimeLeadRate(calcHalfTimeLeadRate(awayTeamAwayMatches, awayTeam))
            .homeComebackRate(calcComebackRate(homeTeamHomeMatches, homeTeam))
            .awayComebackRate(calcComebackRate(awayTeamAwayMatches, awayTeam))

            // League positions (season-scoped, calculated before date)
            .homeLeaguePosition(calcLeaguePosition(homeTeam, season, beforeDate))
            .awayLeaguePosition(calcLeaguePosition(awayTeam, season, beforeDate))

            .build();
   }

   // ── Season Determination ──────────────────────────────────────────────

   /**
    * Determine the current season from database.
    */
   private String determineCurrentSeason(LocalDate date) {
      String season = matchRepository.findSeasonForDate(date);
      if (season != null && !season.isBlank()) {
         return season;
      }
      // Fallback to derived season
      return deriveSeason(date);
   }

   /**
    * Derive season string from a date.
    * Football season runs Aug-May, so:
    * - Jan-Jul dates belong to the season that started previous August
    * - Aug-Dec dates belong to the season starting that August
    */
   private String deriveSeason(LocalDate date) {
      int year = date.getYear();
      int month = date.getMonthValue();

      int startYear;
      if (month >= 8) {
         // Aug-Dec: season started this year
         startYear = year;
      } else {
         // Jan-Jul: season started previous year
         startYear = year - 1;
      }

      int endYear = startYear + 1;
      return String.format("%d-%02d", startYear, endYear % 100);
   }

   // ── Feature calculators ───────────────────────────────────────────────

   /**
    * Average points per game across matches.
    * Data is already limited at DB level.
    */
   private double calcFormPoints(List<Match> matches, String teamName, int window) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .limit(window)  // Secondary limit for safety
            .mapToInt(m -> m.getPointsForTeam(teamName))
            .average()
            .orElse(0.0);
   }

   /**
    * Average goals scored (season-scoped, no additional limit needed).
    */
   private double calcGoalsScoredAvg(List<Match> matches, String teamName) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .mapToInt(m -> m.getGoalsScoredByTeam(teamName))
            .average()
            .orElse(0.0);
   }

   /**
    * Average goals conceded (season-scoped).
    */
   private double calcGoalsConcededAvg(List<Match> matches, String teamName) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .mapToInt(m -> m.getGoalsConcededByTeam(teamName))
            .average()
            .orElse(0.0);
   }

   /**
    * Average total goals per game.
    */
   private double calcTotalGoalsAvg(List<Match> matches, int window) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .limit(window)
            .filter(m -> m.getFullTimeHomeGoals() != null && m.getFullTimeAwayGoals() != null)
            .mapToInt(m -> m.getFullTimeHomeGoals() + m.getFullTimeAwayGoals())
            .average()
            .orElse(0.0);
   }

   /**
    * H2H win rate with historical priors.
    * Uses cross-season data (last 5 meetings regardless of season).
    */
   private double calcH2HWinRate(List<Match> h2hMatches, String teamName, boolean isHomeTeam) {
      if (h2hMatches.isEmpty()) {
         return isHomeTeam ? PRIOR_HOME_WIN : PRIOR_AWAY_WIN;
      }

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
    * H2H draw rate with historical prior.
    */
   private double calcH2HDrawRate(List<Match> h2hMatches) {
      if (h2hMatches.isEmpty()) return PRIOR_DRAW;

      long draws = h2hMatches.stream()
            .filter(m -> "D".equals(m.getFullTimeResult()))
            .count();

      return (double) draws / h2hMatches.size();
   }

   /**
    * Shots on target average from pre-filtered data.
    * Data is already filtered for non-null shots at DB level.
    */
   private double calcShotsOnTargetAvgFromFiltered(List<Match> matches, boolean isHome) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .mapToInt(m -> isHome ? m.getHomeShotsOnTarget() : m.getAwayShotsOnTarget())
            .average()
            .orElse(0.0);
   }

   /**
    * Corners average from pre-filtered data.
    * Data is already filtered for non-null corners at DB level.
    */
   private double calcCornersAvgFromFiltered(List<Match> matches, boolean isHome) {
      if (matches.isEmpty()) return 0.0;

      return matches.stream()
            .mapToInt(m -> isHome ? m.getHomeCorners() : m.getAwayCorners())
            .average()
            .orElse(0.0);
   }

   /**
    * Goal difference over recent matches.
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
    * Win streak — consecutive wins from most recent match.
    * Stops at first non-win (draw or loss).
    *
    * <p>Matches are already in DESC order from DB.</p>
    */
   private int calcWinStreak(List<Match> matches, String teamName) {
      if (matches.isEmpty()) return 0;

      int streak = 0;
      for (Match m : matches) {
         if (m.getPointsForTeam(teamName) == 3) {
            streak++;
         } else {
            break; // Stop at first non-win
         }
      }
      return streak;
   }

   /**
    * Unbeaten streak — consecutive matches without a loss.
    * Stops at first loss.
    */
   private int calcUnbeatenStreak(List<Match> matches, String teamName) {
      if (matches.isEmpty()) return 0;

      int streak = 0;
      for (Match m : matches) {
         int points = m.getPointsForTeam(teamName);
         if (points >= 1) { // Win (3) or Draw (1)
            streak++;
         } else {
            break; // Stop at first loss
         }
      }
      return streak;
   }

   /**
    * Days since last match within the SAME SEASON.
    * Cross-season gaps are ignored - returns default rest value.
    *
    * <p>This prevents artificially high rest values at season start.</p>
    *
    * @param teamName   Team name
    * @param season     Current season
    * @param beforeDate Reference date
    * @return Days since last match (capped at MAX_REST_DAYS, DEFAULT_REST_DAYS if no same-season match)
    */
   private int calcDaysSinceLastMatch(String teamName, String season, LocalDate beforeDate) {
      // Query for most recent match in same season
      List<Match> lastMatches = matchRepository
            .findLastMatchByTeamAndSeasonBeforeDate(teamName, season, beforeDate);

      if (lastMatches.isEmpty()) {
         // No same-season matches - likely start of season
         log.debug("No same-season matches for {} before {} (season={}), using default rest",
                   teamName, beforeDate, season);
         return DEFAULT_REST_DAYS;
      }

      Match lastMatch = lastMatches.get(0);
      LocalDate lastMatchDate = lastMatch.getMatchDate();

      if (lastMatchDate == null) {
         return DEFAULT_REST_DAYS;
      }

      long days = ChronoUnit.DAYS.between(lastMatchDate, beforeDate);

      // Cap at reasonable maximum
      return (int) Math.max(0, Math.min(days, MAX_REST_DAYS));
   }

   // ── Half-Time Features ────────────────────────────────────────────────

   /**
    * Rate at which team leads at half-time.
    */
   private double calcHalfTimeLeadRate(List<Match> matches, String teamName) {
      if (matches.isEmpty()) return 0.0;

      long matchesWithHT = 0;
      long leading = 0;

      for (Match m : matches) {
         if (m.getHalfTimeHomeGoals() == null || m.getHalfTimeAwayGoals() == null) {
            continue;
         }

         matchesWithHT++;
         int htHome = m.getHalfTimeHomeGoals();
         int htAway = m.getHalfTimeAwayGoals();

         boolean isHome = teamName.equalsIgnoreCase(m.getHomeTeam());
         if (isHome && htHome > htAway) {
            leading++;
         } else if (!isHome && htAway > htHome) {
            leading++;
         }
      }

      return matchesWithHT > 0 ? (double) leading / matchesWithHT : 0.0;
   }

   /**
    * Rate of comebacks from trailing at half-time.
    */
   private double calcComebackRate(List<Match> matches, String teamName) {
      if (matches.isEmpty()) return 0.0;

      long trailing = 0;
      long comebacks = 0;

      for (Match m : matches) {
         if (m.getHalfTimeHomeGoals() == null || m.getHalfTimeAwayGoals() == null
               || m.getFullTimeResult() == null) {
            continue;
         }

         int htHome = m.getHalfTimeHomeGoals();
         int htAway = m.getHalfTimeAwayGoals();

         boolean isHome = teamName.equalsIgnoreCase(m.getHomeTeam());
         boolean wasTrailing = isHome ? htAway > htHome : htHome > htAway;

         if (wasTrailing) {
            trailing++;
            int points = m.getPointsForTeam(teamName);
            if (points >= 1) {
               comebacks++;
            }
         }
      }

      return trailing > 0 ? (double) comebacks / trailing : 0.0;
   }

   // ── League Position ───────────────────────────────────────────────────

   /**
    * Calculate league position within the season as of the given date.
    * Uses season-filtered data only.
    */
   private int calcLeaguePosition(String teamName, String season, LocalDate asOfDate) {
      if (leaguePositionService == null) {
         log.debug("LeaguePositionService not available, using default position");
         return 10;  // Mid-table default
      }

      return leaguePositionService.getTeamPositionAsOfDate(teamName, season, asOfDate);
   }
}
