package com.app.common.repository;

import com.app.common.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for Match entities.
 *
 * <p><strong>IMPORTANT: Ordering Convention</strong></p>
 * <p>All queries that return matches for form/streak calculation are ordered by
 * {@code matchDate DESC} (newest first). This is required for:</p>
 * <ul>
 *   <li>Streak calculations (must start from most recent match)</li>
 *   <li>Form calculations (recent matches weighted correctly)</li>
 *   <li>Days since last match (first element is most recent)</li>
 * </ul>
 */
@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

   /**
    * All matches involving a team (home or away), ordered by date descending.
    * Matches are returned in DESCENDING order (newest first). Required for streak and form logic.
    *
    * @param team   Team name to search for
    * @param before Cutoff date (exclusive)
    * @return List of matches sorted by date DESC
    */
   @Query("SELECT m FROM Match m WHERE (m.homeTeam = :team OR m.awayTeam = :team) " +
         "AND m.matchDate < :before ORDER BY m.matchDate DESC")
   List<Match> findByTeamBeforeDate(@Param("team") String team,
                                    @Param("before") LocalDate before);

   /**
    * Case-insensitive search for matches involving a team.
    * Used when exact match fails.
    */
   @Query("SELECT m FROM Match m WHERE (LOWER(m.homeTeam) = LOWER(:team) OR LOWER(m.awayTeam) = LOWER(:team)) " +
         "AND m.matchDate < :before ORDER BY m.matchDate DESC")
   List<Match> findByTeamBeforeDateIgnoreCase(@Param("team") String team,
                                               @Param("before") LocalDate before);

   /**
    * Home matches only for a team before a given date.
    * Matches are returned in DESCENDING order (newest first). Required for streak and form logic.
    * Used to compute home-specific form and goal averages.
    *
    * @param team   Team name to search for
    * @param before Cutoff date (exclusive)
    * @return List of home matches sorted by date DESC
    */
   @Query("SELECT m FROM Match m WHERE m.homeTeam = :team " +
         "AND m.matchDate < :before ORDER BY m.matchDate DESC")
   List<Match> findHomeMatchesByTeamBeforeDate(@Param("team") String team,
                                               @Param("before") LocalDate before);

   /**
    * Case-insensitive home matches search.
    */
   @Query("SELECT m FROM Match m WHERE LOWER(m.homeTeam) = LOWER(:team) " +
         "AND m.matchDate < :before ORDER BY m.matchDate DESC")
   List<Match> findHomeMatchesByTeamBeforeDateIgnoreCase(@Param("team") String team,
                                                          @Param("before") LocalDate before);

   /**
    * Away matches only for a team before a given date.
    * Matches are returned in DESCENDING order (newest first). Required for streak and form logic.
    * Used to compute away-specific form and goal averages.
    *
    * @param team   Team name to search for
    * @param before Cutoff date (exclusive)
    * @return List of away matches sorted by date DESC
    */
   @Query("SELECT m FROM Match m WHERE m.awayTeam = :team " +
         "AND m.matchDate < :before ORDER BY m.matchDate DESC")
   List<Match> findAwayMatchesByTeamBeforeDate(@Param("team") String team,
                                               @Param("before") LocalDate before);

   /**
    * Case-insensitive away matches search.
    */
   @Query("SELECT m FROM Match m WHERE LOWER(m.awayTeam) = LOWER(:team) " +
         "AND m.matchDate < :before ORDER BY m.matchDate DESC")
   List<Match> findAwayMatchesByTeamBeforeDateIgnoreCase(@Param("team") String team,
                                                          @Param("before") LocalDate before);

   /**
    * Get all distinct team names from the database (both home and away).
    * Used for team name suggestions and validation.
    */
   @Query("SELECT DISTINCT m.homeTeam FROM Match m " +
         "UNION SELECT DISTINCT m.awayTeam FROM Match m")
   List<String> findAllDistinctTeamNames();

   /**
    * Find team names that contain a given search string (case-insensitive).
    * Used for fuzzy team name matching and suggestions.
    */
   @Query("SELECT DISTINCT m.homeTeam FROM Match m WHERE LOWER(m.homeTeam) LIKE LOWER(CONCAT('%', :search, '%')) " +
         "UNION SELECT DISTINCT m.awayTeam FROM Match m WHERE LOWER(m.awayTeam) LIKE LOWER(CONCAT('%', :search, '%'))")
   List<String> findTeamNamesContaining(@Param("search") String search);

   /**
    * Head-to-head history between two specific teams before a given date.
    * Covers both home and away permutations.
    * Matches are returned in DESCENDING order (newest first). Required for streak and form logic.
    *
    * @param homeTeam First team (or home team in upcoming match context)
    * @param awayTeam Second team (or away team in upcoming match context)
    * @param before   Cutoff date (exclusive)
    * @return List of H2H matches sorted by date DESC
    */
   @Query("SELECT m FROM Match m WHERE " +
         "((m.homeTeam = :home AND m.awayTeam = :away) OR " +
         " (m.homeTeam = :away AND m.awayTeam = :home)) " +
         "AND m.matchDate < :before ORDER BY m.matchDate DESC")
   List<Match> findH2HBeforeDate(@Param("home") String homeTeam,
                                 @Param("away") String awayTeam,
                                 @Param("before") LocalDate before);

   /**
    * All matches ordered by date ascending.
    * Used by ModelTrainingService to build the full training dataset
    * in chronological order (critical for temporal split).
    */
   List<Match> findAllByOrderByMatchDateAsc();

   /**
    * All matches ordered by date descending.
    * Used by LeagueStatsService to get most recent matches first.
    */
   List<Match> findAllByOrderByMatchDateDesc();

   /**
    * Duplicate check used by CsvIngestionService.
    * Prevents the same match being inserted twice on app restart.
    */
   boolean existsByMatchDateAndHomeTeamAndAwayTeam(LocalDate date,
                                                   String homeTeam,
                                                   String awayTeam);

   /**
    * Find a specific match by date and teams.
    * Used for updating existing matches with additional data (e.g., fouls).
    */
   Match findByMatchDateAndHomeTeamAndAwayTeam(LocalDate date,
                                               String homeTeam,
                                               String awayTeam);

   /**
    * Case-insensitive search for a match by date and teams.
    * Used by MatchResultProcessor for robust prediction resolution.
    */
   Match findByMatchDateAndHomeTeamIgnoreCaseAndAwayTeamIgnoreCase(LocalDate date,
                                                                    String homeTeam,
                                                                    String awayTeam);

   /** Total match count — used for startup logging. */
   long count();

   // ── Season-Filtered Queries for Insights Engine ──────────────────────

   /**
    * All completed matches involving a team within a specific season.
    * Used for season-scoped form calculation in insights.
    */
   @Query("SELECT m FROM Match m WHERE (m.homeTeam = :team OR m.awayTeam = :team) " +
         "AND m.season = :season AND m.fullTimeResult IS NOT NULL " +
         "AND m.matchDate < :before ORDER BY m.matchDate DESC")
   List<Match> findByTeamAndSeasonBeforeDate(@Param("team") String team,
                                              @Param("season") String season,
                                              @Param("before") LocalDate before);

   /**
    * Case-insensitive search for completed matches involving a team within a season.
    */
   @Query("SELECT m FROM Match m WHERE (LOWER(m.homeTeam) = LOWER(:team) OR LOWER(m.awayTeam) = LOWER(:team)) " +
         "AND m.season = :season AND m.fullTimeResult IS NOT NULL " +
         "AND m.matchDate < :before ORDER BY m.matchDate DESC")
   List<Match> findByTeamAndSeasonBeforeDateIgnoreCase(@Param("team") String team,
                                                        @Param("season") String season,
                                                        @Param("before") LocalDate before);

   /**
    * Get all distinct team names from a specific season (both home and away).
    */
   @Query("SELECT DISTINCT m.homeTeam FROM Match m WHERE m.season = :season AND m.fullTimeResult IS NOT NULL " +
         "UNION SELECT DISTINCT m.awayTeam FROM Match m WHERE m.season = :season AND m.fullTimeResult IS NOT NULL")
   List<String> findAllDistinctTeamNamesBySeason(@Param("season") String season);

   /**
    * Get all completed matches for a specific season ordered by date descending.
    */
   @Query("SELECT m FROM Match m WHERE m.season = :season AND m.fullTimeResult IS NOT NULL " +
         "ORDER BY m.matchDate DESC")
   List<Match> findBySeasonOrderByMatchDateDesc(@Param("season") String season);

   /**
    * Get all matches for a specific season (for fixture generation).
    */
   @Query("SELECT m FROM Match m WHERE m.season = :season ORDER BY m.matchDate DESC")
   List<Match> findBySeason(@Param("season") String season);

   /**
    * Get all completed matches involving a team within a specific season.
    * Used for generating simulated upcoming fixtures for non-PL teams.
    */
   @Query("SELECT m FROM Match m WHERE (LOWER(m.homeTeam) = LOWER(:team) OR LOWER(m.awayTeam) = LOWER(:team)) " +
         "AND m.season = :season AND m.fullTimeResult IS NOT NULL " +
         "ORDER BY m.matchDate DESC")
   List<Match> findByTeamAndSeason(@Param("team") String team, @Param("season") String season);

   /**
    * Get the most recent season (based on latest match date).
    */
   @Query("SELECT m.season FROM Match m WHERE m.fullTimeResult IS NOT NULL " +
         "ORDER BY m.matchDate DESC LIMIT 1")
   String findCurrentSeason();

   /**
    * Get all distinct seasons in the database.
    */
   @Query("SELECT DISTINCT m.season FROM Match m WHERE m.season IS NOT NULL ORDER BY m.season DESC")
   List<String> findAllSeasons();

   // ══════════════════════════════════════════════════════════════════════
   // STRICT SEASON-FILTERED QUERIES WITH DB-LEVEL LIMITS
   // All queries enforce: season + matchDate < beforeDate + ORDER BY DESC + LIMIT
   // ══════════════════════════════════════════════════════════════════════

   /**
    * Home matches for a team within a season, before a date, with DB-level limit.
    * Used for form calculation. Only returns completed matches.
    *
    * @param team   Team name
    * @param season Season identifier (e.g., "2024-25")
    * @param before Cutoff date (exclusive)
    * @param limit  Maximum number of matches to return
    * @return List of home matches sorted by date DESC, limited at DB level
    */
   @Query(value = "SELECT * FROM matches m WHERE m.home_team = :team " +
         "AND m.season = :season AND m.match_date < :before " +
         "AND m.full_time_result IS NOT NULL " +
         "ORDER BY m.match_date DESC LIMIT :limit", nativeQuery = true)
   List<Match> findHomeMatchesByTeamSeasonBeforeDateLimited(
         @Param("team") String team,
         @Param("season") String season,
         @Param("before") LocalDate before,
         @Param("limit") int limit);

   /**
    * Away matches for a team within a season, before a date, with DB-level limit.
    * Used for form calculation. Only returns completed matches.
    */
   @Query(value = "SELECT * FROM matches m WHERE m.away_team = :team " +
         "AND m.season = :season AND m.match_date < :before " +
         "AND m.full_time_result IS NOT NULL " +
         "ORDER BY m.match_date DESC LIMIT :limit", nativeQuery = true)
   List<Match> findAwayMatchesByTeamSeasonBeforeDateLimited(
         @Param("team") String team,
         @Param("season") String season,
         @Param("before") LocalDate before,
         @Param("limit") int limit);

   /**
    * All matches (home or away) for a team within a season, before a date, with DB-level limit.
    * Used for overall form, streaks, and goal difference calculations.
    */
   @Query(value = "SELECT * FROM matches m WHERE (m.home_team = :team OR m.away_team = :team) " +
         "AND m.season = :season AND m.match_date < :before " +
         "AND m.full_time_result IS NOT NULL " +
         "ORDER BY m.match_date DESC LIMIT :limit", nativeQuery = true)
   List<Match> findByTeamSeasonBeforeDateLimited(
         @Param("team") String team,
         @Param("season") String season,
         @Param("before") LocalDate before,
         @Param("limit") int limit);

   /**
    * Head-to-head matches between two teams, before a date, limited to last N.
    * No season filter - H2H history spans multiple seasons.
    * Limited to 5 matches as per Premier League convention.
    */
   @Query(value = "SELECT * FROM matches m WHERE " +
         "((m.home_team = :home AND m.away_team = :away) OR " +
         " (m.home_team = :away AND m.away_team = :home)) " +
         "AND m.match_date < :before " +
         "AND m.full_time_result IS NOT NULL " +
         "ORDER BY m.match_date DESC LIMIT :limit", nativeQuery = true)
   List<Match> findH2HBeforeDateLimited(
         @Param("home") String homeTeam,
         @Param("away") String awayTeam,
         @Param("before") LocalDate before,
         @Param("limit") int limit);

   /**
    * Most recent match for a team within the same season.
    * Used for days-since-last-match calculation (ignores cross-season gaps).
    */
   @Query(value = "SELECT * FROM matches m WHERE (m.home_team = :team OR m.away_team = :team) " +
         "AND m.season = :season AND m.match_date < :before " +
         "AND m.full_time_result IS NOT NULL " +
         "ORDER BY m.match_date DESC LIMIT 1", nativeQuery = true)
   List<Match> findLastMatchByTeamAndSeasonBeforeDate(
         @Param("team") String team,
         @Param("season") String season,
         @Param("before") LocalDate before);

   /**
    * All completed matches in a season before a given date.
    * Used for league table calculation at a specific point in time.
    */
   @Query("SELECT m FROM Match m WHERE m.season = :season " +
         "AND m.matchDate < :before AND m.fullTimeResult IS NOT NULL " +
         "ORDER BY m.matchDate ASC")
   List<Match> findBySeasonBeforeDateForTable(
         @Param("season") String season,
         @Param("before") LocalDate before);

   /**
    * Home matches with shots data for a team within a season.
    * Excludes matches where shot data is null.
    */
   @Query(value = "SELECT * FROM matches m WHERE m.home_team = :team " +
         "AND m.season = :season AND m.match_date < :before " +
         "AND m.full_time_result IS NOT NULL " +
         "AND m.home_shots_on_target IS NOT NULL " +
         "ORDER BY m.match_date DESC LIMIT :limit", nativeQuery = true)
   List<Match> findHomeMatchesWithShotsData(
         @Param("team") String team,
         @Param("season") String season,
         @Param("before") LocalDate before,
         @Param("limit") int limit);

   /**
    * Away matches with shots data for a team within a season.
    * Excludes matches where shot data is null.
    */
   @Query(value = "SELECT * FROM matches m WHERE m.away_team = :team " +
         "AND m.season = :season AND m.match_date < :before " +
         "AND m.full_time_result IS NOT NULL " +
         "AND m.away_shots_on_target IS NOT NULL " +
         "ORDER BY m.match_date DESC LIMIT :limit", nativeQuery = true)
   List<Match> findAwayMatchesWithShotsData(
         @Param("team") String team,
         @Param("season") String season,
         @Param("before") LocalDate before,
         @Param("limit") int limit);

   /**
    * Home matches with corners data for a team within a season.
    */
   @Query(value = "SELECT * FROM matches m WHERE m.home_team = :team " +
         "AND m.season = :season AND m.match_date < :before " +
         "AND m.full_time_result IS NOT NULL " +
         "AND m.home_corners IS NOT NULL " +
         "ORDER BY m.match_date DESC LIMIT :limit", nativeQuery = true)
   List<Match> findHomeMatchesWithCornersData(
         @Param("team") String team,
         @Param("season") String season,
         @Param("before") LocalDate before,
         @Param("limit") int limit);

   /**
    * Away matches with corners data for a team within a season.
    */
   @Query(value = "SELECT * FROM matches m WHERE m.away_team = :team " +
         "AND m.season = :season AND m.match_date < :before " +
         "AND m.full_time_result IS NOT NULL " +
         "AND m.away_corners IS NOT NULL " +
         "ORDER BY m.match_date DESC LIMIT :limit", nativeQuery = true)
   List<Match> findAwayMatchesWithCornersData(
         @Param("team") String team,
         @Param("season") String season,
         @Param("before") LocalDate before,
         @Param("limit") int limit);

   /**
    * Get season for a specific date.
    * Returns the season that contains matches closest to the given date.
    */
   @Query(value = "SELECT m.season FROM matches m WHERE m.match_date <= :date " +
         "AND m.full_time_result IS NOT NULL " +
         "ORDER BY m.match_date DESC LIMIT 1", nativeQuery = true)
   String findSeasonForDate(@Param("date") LocalDate date);

   /**
    * Find all finished matches on a specific date.
    * Used by PredictionRecalculationScheduler to detect today's completed matches.
    */
   @Query("SELECT m FROM Match m WHERE m.matchDate = :date AND m.fullTimeResult IS NOT NULL " +
         "ORDER BY m.matchDate DESC")
   List<Match> findFinishedMatchesByDate(@Param("date") LocalDate date);

   /**
    * Count finished matches on a specific date.
    * Quick check to know if there are any completed matches today.
    */
   @Query("SELECT COUNT(m) FROM Match m WHERE m.matchDate = :date AND m.fullTimeResult IS NOT NULL")
   long countFinishedMatchesByDate(@Param("date") LocalDate date);

   /**
    * Count scheduled (not yet finished) matches on a specific date.
    * Used to determine if matches are still in progress or pending today.
    */
   @Query("SELECT COUNT(m) FROM Match m WHERE m.matchDate = :date AND m.fullTimeResult IS NULL")
   long countUnfinishedMatchesByDate(@Param("date") LocalDate date);

   // ══════════════════════════════════════════════════════════════════════
   // BACKFILL OPTIMIZATION QUERIES
   // ══════════════════════════════════════════════════════════════════════

   /**
    * Find all finished matches whose ID is NOT in the given set of match IDs.
    * Used by HistoricalPredictionGenerator to skip matches that already have predictions.
    * Much more efficient than checking per-match in Java.
    */
   @Query("SELECT m FROM Match m WHERE m.fullTimeResult IS NOT NULL " +
         "AND m.id NOT IN :excludeIds ORDER BY m.matchDate ASC")
   List<Match> findFinishedMatchesExcludingIds(@Param("excludeIds") java.util.Collection<Long> excludeIds);

   /**
    * Find all finished matches when no IDs to exclude (first-time backfill).
    */
   @Query("SELECT m FROM Match m WHERE m.fullTimeResult IS NOT NULL ORDER BY m.matchDate ASC")
   List<Match> findAllFinishedMatchesAsc();

    /**
     * Find all finished matches before a date, keyed for map lookup.
     * Used by MatchResultProcessor for bulk pre-loading instead of per-prediction queries.
     */
    @Query("SELECT m FROM Match m WHERE m.fullTimeResult IS NOT NULL AND m.matchDate <= :beforeDate")
    List<Match> findAllFinishedMatchesBeforeDate(@Param("beforeDate") LocalDate beforeDate);

    /**
     * Lightweight projection returning only (matchDate, homeTeam, awayTeam) for all matches.
     * Used by CsvIngestionService to pre-load existing match keys for O(1) duplicate checks
     * instead of per-row existence queries.
     */
    @Query("SELECT m.matchDate, m.homeTeam, m.awayTeam FROM Match m")
    List<Object[]> findAllMatchKeyProjections();
}
