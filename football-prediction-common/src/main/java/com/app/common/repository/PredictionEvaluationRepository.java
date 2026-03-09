package com.app.common.repository;

import com.app.common.model.PredictionEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for PredictionEvaluation entity operations.
 * Provides queries for evaluation metrics and accuracy analysis.
 */
@Repository
public interface PredictionEvaluationRepository extends JpaRepository<PredictionEvaluation, Long> {

    /**
     * Find evaluation by match ID.
     */
    Optional<PredictionEvaluation> findByMatchId(Long matchId);

    /**
     * Check if evaluation exists for a match.
     */
    boolean existsByMatchId(Long matchId);

    /**
     * Find all evaluations where winner was predicted correctly.
     */
    List<PredictionEvaluation> findByWinnerCorrectTrue();

    /**
     * Find all evaluations where exact score was predicted.
     */
    List<PredictionEvaluation> findByScoreExactTrue();

    /**
     * Count total evaluations.
     */
    @Query("SELECT COUNT(e) FROM PredictionEvaluation e")
    long countAllEvaluations();

    /**
     * Count evaluations where winner was correct.
     */
    @Query("SELECT COUNT(e) FROM PredictionEvaluation e WHERE e.winnerCorrect = true")
    long countCorrectWinnerPredictions();

    /**
     * Count evaluations where exact score was correct.
     */
    @Query("SELECT COUNT(e) FROM PredictionEvaluation e WHERE e.scoreExact = true")
    long countExactScorePredictions();

    /**
     * Get average goal difference error.
     */
    @Query("SELECT AVG(e.goalDifferenceError) FROM PredictionEvaluation e WHERE e.goalDifferenceError IS NOT NULL")
    Double getAverageGoalDifferenceError();

    /**
     * Get average card prediction error.
     */
    @Query("SELECT AVG(e.cardPredictionError) FROM PredictionEvaluation e WHERE e.cardPredictionError IS NOT NULL")
    Double getAverageCardPredictionError();

    /**
     * Get average corner prediction error.
     */
    @Query("SELECT AVG(e.cornerPredictionError) FROM PredictionEvaluation e WHERE e.cornerPredictionError IS NOT NULL")
    Double getAverageCornerPredictionError();

    // ═══════════════════════════════════════════════════════════════════
    // Per-team queries
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Find evaluations for a specific team (home or away).
     */
    @Query("SELECT e FROM PredictionEvaluation e WHERE LOWER(e.homeTeam) = LOWER(:team) OR LOWER(e.awayTeam) = LOWER(:team)")
    List<PredictionEvaluation> findByTeam(@Param("team") String team);

    /**
     * Count evaluations for a team.
     */
    @Query("SELECT COUNT(e) FROM PredictionEvaluation e WHERE LOWER(e.homeTeam) = LOWER(:team) OR LOWER(e.awayTeam) = LOWER(:team)")
    long countByTeam(@Param("team") String team);

    /**
     * Count correct winner predictions for a team.
     */
    @Query("SELECT COUNT(e) FROM PredictionEvaluation e WHERE (LOWER(e.homeTeam) = LOWER(:team) OR LOWER(e.awayTeam) = LOWER(:team)) AND e.winnerCorrect = true")
    long countCorrectWinnerByTeam(@Param("team") String team);

    // ═══════════════════════════════════════════════════════════════════
    // Per-season queries
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Find evaluations by season.
     */
    List<PredictionEvaluation> findBySeasonOrderByEvaluationTimeDesc(String season);

    /**
     * Count evaluations by season.
     */
    long countBySeason(String season);

    /**
     * Count correct winner predictions by season.
     */
    @Query("SELECT COUNT(e) FROM PredictionEvaluation e WHERE e.season = :season AND e.winnerCorrect = true")
    long countCorrectWinnerBySeason(@Param("season") String season);

    /**
     * Get average goal error by season.
     */
    @Query("SELECT AVG(e.goalDifferenceError) FROM PredictionEvaluation e WHERE e.season = :season AND e.goalDifferenceError IS NOT NULL")
    Double getAverageGoalErrorBySeason(@Param("season") String season);

    /**
     * Get distinct seasons with evaluations.
     */
    @Query("SELECT DISTINCT e.season FROM PredictionEvaluation e WHERE e.season IS NOT NULL ORDER BY e.season DESC")
    List<String> findDistinctSeasons();

    /**
     * Find recent evaluations ordered by evaluation time.
     */
    @Query("SELECT e FROM PredictionEvaluation e ORDER BY e.evaluationTime DESC")
    List<PredictionEvaluation> findRecentEvaluations();

    /**
     * Find evaluations within a time window (for sliding window accuracy).
     */
    List<PredictionEvaluation> findByEvaluationTimeAfterOrderByEvaluationTimeDesc(LocalDateTime cutoff);
}

