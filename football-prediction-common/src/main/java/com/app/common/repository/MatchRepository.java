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
    * Home matches only for a team before a given date.
    * Used to compute home-specific form and goal averages.
    */
   @Query("SELECT m FROM Match m WHERE m.homeTeam = :team " +
         "AND m.matchDate < :before ORDER BY m.matchDate DESC")
   List<Match> findHomeMatchesByTeamBeforeDate(@Param("team") String team,
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
    * Duplicate check used by CsvIngestionService.
    * Prevents the same match being inserted twice on app restart.
    */
   boolean existsByMatchDateAndHomeTeamAndAwayTeam(LocalDate date,
                                                   String homeTeam,
                                                   String awayTeam);

   /** Total match count — used for startup logging. */
   long count();
}

