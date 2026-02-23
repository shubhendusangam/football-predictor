package com.app.common.repository;

import com.app.common.model.LeagueStanding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for LeagueStanding entity operations.
 * Provides methods for fetching league tables with proper sorting.
 */
@Repository
public interface LeagueStandingRepository extends JpaRepository<LeagueStanding, Long> {

    /**
     * Find standings by league and season, sorted by points, goal difference, and goals for.
     * This is the primary method for displaying the league table.
     */
    @Query("SELECT ls FROM LeagueStanding ls WHERE ls.leagueId = :leagueId AND ls.season = :season " +
           "ORDER BY ls.points DESC, ls.goalDifference DESC, ls.goalsFor DESC")
    List<LeagueStanding> findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(
            @Param("leagueId") Long leagueId,
            @Param("season") String season);

    /**
     * Find standings by league ID only (all seasons).
     */
    List<LeagueStanding> findByLeagueIdOrderBySeasonDescPointsDesc(Long leagueId);

    /**
     * Find a specific team's standing in a league and season.
     */
    Optional<LeagueStanding> findByLeagueIdAndSeasonAndTeamName(Long leagueId, String season, String teamName);

    /**
     * Find a specific team's standing by team ID.
     */
    Optional<LeagueStanding> findByLeagueIdAndSeasonAndTeamId(Long leagueId, String season, Long teamId);

    /**
     * Get all distinct seasons for a league.
     */
    @Query("SELECT DISTINCT ls.season FROM LeagueStanding ls WHERE ls.leagueId = :leagueId ORDER BY ls.season DESC")
    List<String> findDistinctSeasonsByLeagueId(@Param("leagueId") Long leagueId);

    /**
     * Get the latest season for a league.
     */
    @Query("SELECT ls.season FROM LeagueStanding ls WHERE ls.leagueId = :leagueId ORDER BY ls.season DESC LIMIT 1")
    Optional<String> findLatestSeasonByLeagueId(@Param("leagueId") Long leagueId);

    /**
     * Check if standings exist for a league and season.
     */
    boolean existsByLeagueIdAndSeason(Long leagueId, String season);

    /**
     * Count teams in a league for a season.
     */
    long countByLeagueIdAndSeason(Long leagueId, String season);

    /**
     * Delete all standings for a league and season (for recalculation).
     */
    @Modifying
    @Query("DELETE FROM LeagueStanding ls WHERE ls.leagueId = :leagueId AND ls.season = :season")
    void deleteByLeagueIdAndSeason(@Param("leagueId") Long leagueId, @Param("season") String season);

    /**
     * Update positions for all teams in a league/season.
     * Call this after any standings update to recalculate positions.
     */
    @Query("SELECT ls FROM LeagueStanding ls WHERE ls.leagueId = :leagueId AND ls.season = :season " +
           "ORDER BY ls.points DESC, ls.goalDifference DESC, ls.goalsFor DESC, ls.teamName ASC")
    List<LeagueStanding> findForPositionUpdate(@Param("leagueId") Long leagueId, @Param("season") String season);

    /**
     * Find top N teams in a league for a season.
     */
    @Query("SELECT ls FROM LeagueStanding ls WHERE ls.leagueId = :leagueId AND ls.season = :season " +
           "ORDER BY ls.points DESC, ls.goalDifference DESC, ls.goalsFor DESC LIMIT :limit")
    List<LeagueStanding> findTopTeams(@Param("leagueId") Long leagueId,
                                       @Param("season") String season,
                                       @Param("limit") int limit);

    /**
     * Find bottom N teams in a league for a season (relegation zone).
     */
    @Query("SELECT ls FROM LeagueStanding ls WHERE ls.leagueId = :leagueId AND ls.season = :season " +
           "ORDER BY ls.points ASC, ls.goalDifference ASC, ls.goalsFor ASC LIMIT :limit")
    List<LeagueStanding> findBottomTeams(@Param("leagueId") Long leagueId,
                                          @Param("season") String season,
                                          @Param("limit") int limit);

    /**
     * Find all standings by team name across all leagues and seasons.
     */
    List<LeagueStanding> findByTeamNameIgnoreCaseOrderBySeasonDesc(String teamName);
}

