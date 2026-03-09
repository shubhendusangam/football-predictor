package com.app.common.repository;

import com.app.common.model.ModelTrainingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ModelTrainingHistory entity operations.
 * Provides queries for training history and cooldown checks.
 */
@Repository
public interface ModelTrainingHistoryRepository extends JpaRepository<ModelTrainingHistory, Long> {

    /**
     * Find all training events ordered by time (most recent first).
     */
    List<ModelTrainingHistory> findAllByOrderByTrainingTimeDesc();

    /**
     * Find the most recent successful training event.
     */
    Optional<ModelTrainingHistory> findTopBySuccessTrueOrderByTrainingTimeDesc();

    /**
     * Find training events by trigger reason.
     */
    List<ModelTrainingHistory> findByTriggerReasonOrderByTrainingTimeDesc(String triggerReason);

    /**
     * Check if retraining happened recently (cooldown check).
     */
    @Query("SELECT COUNT(h) > 0 FROM ModelTrainingHistory h WHERE h.success = true AND h.trainingTime > :since")
    boolean hasRecentSuccessfulTraining(LocalDateTime since);

    /**
     * Find recent training history (last N entries).
     */
    @Query("SELECT h FROM ModelTrainingHistory h ORDER BY h.trainingTime DESC")
    List<ModelTrainingHistory> findRecentHistory();

    /**
     * Count successful trainings.
     */
    long countBySuccessTrue();

    /**
     * Count failed trainings.
     */
    long countBySuccessFalse();
}

