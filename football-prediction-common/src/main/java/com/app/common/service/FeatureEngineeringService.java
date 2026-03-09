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
import com.app.common.model.SeasonTeamStats;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.SeasonTeamStatsRepository;

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

   @Autowired(required = false)
   private SeasonTeamStatsRepository seasonTeamStatsRepository;

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

    /**
     * For HISTORICAL PREDICTION — uses the match date as the cutoff
     * so that features only include data available before the match was played.
     * This prevents data leakage when generating retrospective predictions.
     *
     * @param homeTeam  Home team name
     * @param awayTeam  Away team name
     * @param matchDate Date of the match (used as cutoff)
     * @param season    Season identifier
     * @return MatchFeatures computed from data available before matchDate
     */
    public MatchFeatures buildFeaturesForHistoricalPrediction(String homeTeam, String awayTeam,
                                                               LocalDate matchDate, String season) {
       return buildFeatures(homeTeam, awayTeam, matchDate, season);
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

      MatchFeatures features = MatchFeatures.builder()
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

            // Possession proxy (estimated from shots + corners)
            .homePossessionProxy(estimatePossession(homeTeamHomeMatches, homeTeam, true))
            .awayPossessionProxy(estimatePossession(awayTeamAwayMatches, awayTeam, false))

            // Elo ratings (from SeasonTeamStats)
            .homeEloRating(getEloRating(homeTeam, season))
            .awayEloRating(getEloRating(awayTeam, season))

            // Recency-weighted form (exponential decay)
            .homeWeightedForm(calcWeightedFormPoints(homeTeamHomeMatches, homeTeam, formWindow))
            .awayWeightedForm(calcWeightedFormPoints(awayTeamAwayMatches, awayTeam, formWindow))

            .build();

      // ── Compute derived interaction features ──────────────────
      features.setFormDifference(features.getHomeFormPoints() - features.getAwayFormPoints());
      features.setGoalDiffDifference(features.getHomeGoalDifference() - features.getAwayGoalDifference());
      features.setH2hDominance(features.getH2hHomeWinRate() - features.getH2hAwayWinRate());
      features.setRestAdvantage(features.getHomeDaysSinceLastMatch() - features.getAwayDaysSinceLastMatch());
      features.setEloDifference(features.getHomeEloRating() - features.getAwayEloRating());

      return features;
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
    * Estimate possession percentage using shots and corners as proxies.
    *
    * Formula: possession = (shotRatio × 0.6) + (cornerRatio × 0.4)
    * Where:
    * - shotRatio = teamShots / (teamShots + opponentShots)
    * - cornerRatio = teamCorners / (teamCorners + opponentCorners)
    *
    * @param matches List of matches to analyze
    * @param teamName The team to calculate possession for
    * @param isHome Whether the team is playing at home in these matches
    * @return Estimated possession as double (0.0 to 1.0, representing 0% to 100%)
    */
   public double estimatePossession(List<Match> matches, String teamName, boolean isHome) {
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

   // ── Elo Rating ────────────────────────────────────────────────────────

   /**
    * Get the current Elo rating for a team in a given season.
    * Returns default 1500.0 if SeasonTeamStats is not available.
    */
   private double getEloRating(String teamName, String season) {
      if (seasonTeamStatsRepository == null) {
         return SeasonTeamStats.DEFAULT_ELO_RATING;
      }
      try {
         return seasonTeamStatsRepository
               .findBySeasonIdAndTeamNameIgnoreCase(season, teamName)
               .map(SeasonTeamStats::getEloRating)
               .orElse(SeasonTeamStats.DEFAULT_ELO_RATING);
      } catch (Exception e) {
         log.debug("Could not fetch Elo rating for {} in season {}: {}", teamName, season, e.getMessage());
         return SeasonTeamStats.DEFAULT_ELO_RATING;
      }
   }

   // ── Recency-Weighted Form ─────────────────────────────────────────────

   /**
    * Calculate recency-weighted form points using exponential decay.
    * Most recent match has the highest weight, decaying by factor 0.7 per match.
    *
    * <p>This gives more importance to the most recent results while still
    * considering slightly older ones, unlike equal-weight form which treats
    * all 5 matches identically.</p>
    *
    * @param matches List of matches (newest first, as per DB ordering)
    * @param teamName Team to calculate for
    * @param window Number of matches to consider
    * @return Weighted form score (0.0 to 3.0 scale, matching points per game)
    */
   private double calcWeightedFormPoints(List<Match> matches, String teamName, int window) {
      if (matches.isEmpty()) return 0.0;

      double decayFactor = 0.7;
      double weightedSum = 0.0;
      double totalWeight = 0.0;

      int limit = Math.min(window, matches.size());
      for (int i = 0; i < limit; i++) {
         double weight = Math.pow(decayFactor, i); // 1.0, 0.7, 0.49, 0.343, 0.24
         int points = matches.get(i).getPointsForTeam(teamName);
         weightedSum += points * weight;
         totalWeight += weight;
      }

      return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
   }
}
