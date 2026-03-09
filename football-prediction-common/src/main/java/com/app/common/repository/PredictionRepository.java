package com.app.common.repository;

import com.app.common.model.Prediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository for Prediction entity operations.
 * Provides queries for team analytics and model accuracy tracking.
 */
@Repository
public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    // ═══════════════════════════════════════════════════════════════════
    // Basic CRUD and lookup queries
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Find prediction by match ID and team name.
     */
    Optional<Prediction> findByMatchIdAndTeamName(Long matchId, String teamName);

    /**
     * Find all predictions for a team.
     */
    List<Prediction> findByTeamNameIgnoreCaseOrderByMatchDateDesc(String teamName);

    /**
     * Find all predictions for a team in a specific season.
     */
    List<Prediction> findByTeamNameIgnoreCaseAndSeasonOrderByMatchDateDesc(String teamName, String season);

    /**
     * Check if prediction exists for a match and team.
     */
    boolean existsByMatchIdAndTeamName(Long matchId, String teamName);

    // ═══════════════════════════════════════════════════════════════════
    // Upcoming/Unresolved predictions
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Find upcoming (unresolved) predictions for a team.
     */
    @Query("SELECT p FROM Prediction p WHERE LOWER(p.teamName) = LOWER(:teamName) " +
           "AND p.actualResult IS NULL ORDER BY p.matchDate ASC")
    List<Prediction> findUpcomingPredictions(@Param("teamName") String teamName);

    /**
     * Find upcoming predictions for a team with limit.
     */
    @Query("SELECT p FROM Prediction p WHERE LOWER(p.teamName) = LOWER(:teamName) " +
           "AND p.actualResult IS NULL AND p.matchDate >= :fromDate ORDER BY p.matchDate ASC")
    List<Prediction> findUpcomingPredictionsFromDate(@Param("teamName") String teamName,
                                                      @Param("fromDate") LocalDate fromDate);

    /**
     * Find all unresolved predictions where match date has passed.
     * Used to update predictions with actual results.
     */
    @Query("SELECT p FROM Prediction p WHERE p.actualResult IS NULL AND p.matchDate <= :beforeDate ORDER BY p.matchDate ASC")
    List<Prediction> findAllUnresolvedPredictionsBeforeDate(@Param("beforeDate") LocalDate beforeDate);

    // ═══════════════════════════════════════════════════════════════════
    // Model accuracy queries
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Count total resolved predictions for a team.
     */
    @Query("SELECT COUNT(p) FROM Prediction p WHERE LOWER(p.teamName) = LOWER(:teamName) " +
           "AND p.actualResult IS NOT NULL")
    long countResolvedPredictions(@Param("teamName") String teamName);

    /**
     * Count correct predictions for a team.
     */
    @Query("SELECT COUNT(p) FROM Prediction p WHERE LOWER(p.teamName) = LOWER(:teamName) " +
           "AND p.isCorrect = true")
    long countCorrectPredictions(@Param("teamName") String teamName);

    /**
     * Count high confidence predictions for a team.
     */
    @Query("SELECT COUNT(p) FROM Prediction p WHERE LOWER(p.teamName) = LOWER(:teamName) " +
           "AND p.actualResult IS NOT NULL AND p.confidence >= 0.6")
    long countHighConfidencePredictions(@Param("teamName") String teamName);

    /**
     * Count correct high confidence predictions for a team.
     */
    @Query("SELECT COUNT(p) FROM Prediction p WHERE LOWER(p.teamName) = LOWER(:teamName) " +
           "AND p.isCorrect = true AND p.confidence >= 0.6")
    long countCorrectHighConfidencePredictions(@Param("teamName") String teamName);

    /**
     * Count home predictions for a team.
     */
    @Query("SELECT COUNT(p) FROM Prediction p WHERE LOWER(p.teamName) = LOWER(:teamName) " +
           "AND p.actualResult IS NOT NULL AND p.isHome = true")
    long countHomePredictions(@Param("teamName") String teamName);

    /**
     * Count correct home predictions for a team.
     */
    @Query("SELECT COUNT(p) FROM Prediction p WHERE LOWER(p.teamName) = LOWER(:teamName) " +
           "AND p.isCorrect = true AND p.isHome = true")
    long countCorrectHomePredictions(@Param("teamName") String teamName);

    /**
     * Count away predictions for a team.
     */
    @Query("SELECT COUNT(p) FROM Prediction p WHERE LOWER(p.teamName) = LOWER(:teamName) " +
           "AND p.actualResult IS NOT NULL AND p.isHome = false")
    long countAwayPredictions(@Param("teamName") String teamName);

    /**
     * Count correct away predictions for a team.
     */
    @Query("SELECT COUNT(p) FROM Prediction p WHERE LOWER(p.teamName) = LOWER(:teamName) " +
           "AND p.isCorrect = true AND p.isHome = false")
    long countCorrectAwayPredictions(@Param("teamName") String teamName);

    // ═══════════════════════════════════════════════════════════════════
    // Season-wise aggregation
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get distinct seasons for a team's predictions.
     */
    @Query("SELECT DISTINCT p.season FROM Prediction p WHERE LOWER(p.teamName) = LOWER(:teamName) " +
           "AND p.season IS NOT NULL ORDER BY p.season DESC")
    List<String> findDistinctSeasonsByTeam(@Param("teamName") String teamName);

    /**
     * Get resolved predictions for a team in a season.
     */
    @Query("SELECT p FROM Prediction p WHERE LOWER(p.teamName) = LOWER(:teamName) " +
           "AND p.season = :season AND p.actualResult IS NOT NULL ORDER BY p.matchDate ASC")
    List<Prediction> findResolvedPredictionsBySeason(@Param("teamName") String teamName,
                                                      @Param("season") String season);

    // ═══════════════════════════════════════════════════════════════════
    // Home vs Away trends
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get home predictions for a team (resolved).
     */
    @Query("SELECT p FROM Prediction p WHERE LOWER(p.teamName) = LOWER(:teamName) " +
           "AND p.isHome = true AND p.actualResult IS NOT NULL ORDER BY p.matchDate DESC")
    List<Prediction> findHomePredictions(@Param("teamName") String teamName);

    /**
     * Get away predictions for a team (resolved).
     */
    @Query("SELECT p FROM Prediction p WHERE LOWER(p.teamName) = LOWER(:teamName) " +
           "AND p.isHome = false AND p.actualResult IS NOT NULL ORDER BY p.matchDate DESC")
    List<Prediction> findAwayPredictions(@Param("teamName") String teamName);

    // ═══════════════════════════════════════════════════════════════════
    // Statistics aggregation helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get average confidence for a team's predictions.
     */
    @Query("SELECT AVG(p.confidence) FROM Prediction p WHERE LOWER(p.teamName) = LOWER(:teamName) " +
           "AND p.actualResult IS NOT NULL")
    Double getAverageConfidence(@Param("teamName") String teamName);

    /**
     * Get all predictions for global model accuracy calculation.
     */
    @Query("SELECT p FROM Prediction p WHERE p.actualResult IS NOT NULL")
    List<Prediction> findAllResolvedPredictions();

    /**
     * Count all resolved predictions.
     */
    @Query("SELECT COUNT(p) FROM Prediction p WHERE p.actualResult IS NOT NULL")
    long countAllResolvedPredictions();

    /**
     * Count all correct predictions.
     */
    @Query("SELECT COUNT(p) FROM Prediction p WHERE p.isCorrect = true")
    long countAllCorrectPredictions();

    // ═══════════════════════════════════════════════════════════════════
    // Backfill optimization queries
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get all match IDs that already have at least one prediction.
     * Used to efficiently skip already-processed matches during backfill
     * instead of loading all predictions per team.
     */
    @Query("SELECT DISTINCT p.matchId FROM Prediction p")
    Set<Long> findAllDistinctMatchIds();
}

