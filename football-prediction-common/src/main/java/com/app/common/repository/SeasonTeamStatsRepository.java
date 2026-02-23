package com.app.common.repository;

import com.app.common.model.SeasonTeamStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for SeasonTeamStats entity operations.
 * Provides queries for team statistics within seasons including Elo ratings.
 */
@Repository
public interface SeasonTeamStatsRepository extends JpaRepository<SeasonTeamStats, Long> {

    /**
     * Find stats for a specific team in a specific season.
     */
    Optional<SeasonTeamStats> findBySeasonIdAndTeamId(String seasonId, Long teamId);

    /**
     * Find stats for a specific team by name in a specific season.
     */
    Optional<SeasonTeamStats> findBySeasonIdAndTeamNameIgnoreCase(String seasonId, String teamName);

    /**
     * Find all team stats for a season ordered by Elo rating descending.
     */
    List<SeasonTeamStats> findBySeasonIdOrderByEloRatingDesc(String seasonId);

    /**
     * Find all team stats for a season ordered by total points.
     */
    @Query("SELECT s FROM SeasonTeamStats s WHERE s.seasonId = :seasonId " +
           "ORDER BY (s.wins * 3 + s.draws) DESC, (s.goalsScored - s.goalsConceded) DESC")
    List<SeasonTeamStats> findBySeasonIdOrderByPointsDesc(@Param("seasonId") String seasonId);

    /**
     * Find all stats for a specific team across all seasons.
     */
    List<SeasonTeamStats> findByTeamIdOrderBySeasonIdDesc(Long teamId);

    /**
     * Find all stats for a team by name across all seasons.
     */
    List<SeasonTeamStats> findByTeamNameIgnoreCaseOrderBySeasonIdDesc(String teamName);

    /**
     * Check if stats exist for a team in a season.
     */
    boolean existsBySeasonIdAndTeamId(String seasonId, Long teamId);

    /**
     * Get top N teams by Elo rating in a season.
     */
    @Query("SELECT s FROM SeasonTeamStats s WHERE s.seasonId = :seasonId " +
           "ORDER BY s.eloRating DESC")
    List<SeasonTeamStats> findTopByEloRating(@Param("seasonId") String seasonId);

    /**
     * Get teams with highest form in last 5 matches.
     */
    @Query("SELECT s FROM SeasonTeamStats s WHERE s.seasonId = :seasonId " +
           "ORDER BY s.formPointsLast5 DESC")
    List<SeasonTeamStats> findTopByForm(@Param("seasonId") String seasonId);

    /**
     * Get average Elo rating for a season.
     */
    @Query("SELECT AVG(s.eloRating) FROM SeasonTeamStats s WHERE s.seasonId = :seasonId")
    Double getAverageEloRating(@Param("seasonId") String seasonId);

    /**
     * Find teams on winning streaks.
     */
    @Query("SELECT s FROM SeasonTeamStats s WHERE s.seasonId = :seasonId " +
           "AND s.currentStreak LIKE 'W%' ORDER BY s.currentStreak DESC")
    List<SeasonTeamStats> findTeamsOnWinningStreak(@Param("seasonId") String seasonId);

    /**
     * Find teams on losing streaks.
     */
    @Query("SELECT s FROM SeasonTeamStats s WHERE s.seasonId = :seasonId " +
           "AND s.currentStreak LIKE 'L%' ORDER BY s.currentStreak DESC")
    List<SeasonTeamStats> findTeamsOnLosingStreak(@Param("seasonId") String seasonId);

    /**
     * Find stats for multiple teams in a season (batch query to avoid N+1).
     */
    @Query("SELECT s FROM SeasonTeamStats s WHERE s.seasonId = :seasonId " +
           "AND LOWER(s.teamName) IN :teamNames")
    List<SeasonTeamStats> findBySeasonIdAndTeamNames(
            @Param("seasonId") String seasonId,
            @Param("teamNames") List<String> teamNames);

    /**
     * Delete all stats for a season.
     */
    void deleteBySeasonId(String seasonId);

    /**
     * Count teams in a season.
     */
    long countBySeasonId(String seasonId);
}

