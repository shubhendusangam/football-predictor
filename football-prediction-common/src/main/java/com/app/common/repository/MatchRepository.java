package com.app.common.repository;

import com.app.common.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

   /**
    * All matches involving a team (home or away), ordered by date descending.
    * Used for general form calculation.
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
    * Used to compute home-specific form and goal averages.
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
    * Used to compute away-specific form and goal averages.
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
    * Get all distinct team names from the database.
    * Used for team name suggestions and validation.
    */
   @Query("SELECT DISTINCT m.homeTeam FROM Match m ORDER BY m.homeTeam")
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
    * Get all distinct team names from a specific season.
    */
   @Query("SELECT DISTINCT m.homeTeam FROM Match m WHERE m.season = :season AND m.fullTimeResult IS NOT NULL")
   List<String> findAllDistinctTeamNamesBySeason(@Param("season") String season);

   /**
    * Get all completed matches for a specific season ordered by date descending.
    */
   @Query("SELECT m FROM Match m WHERE m.season = :season AND m.fullTimeResult IS NOT NULL " +
         "ORDER BY m.matchDate DESC")
   List<Match> findBySeasonOrderByMatchDateDesc(@Param("season") String season);

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
}
