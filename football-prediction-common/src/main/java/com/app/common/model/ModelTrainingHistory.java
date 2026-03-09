package com.app.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity tracking model retraining history.
 * Records each self-training event including what adjustments were made
 * and the resulting performance changes.
 */
@Entity
@Table(name = "model_training_history", indexes = {
    @Index(name = "idx_training_time", columnList = "trainingTime"),
    @Index(name = "idx_training_trigger", columnList = "triggerReason")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Timestamp when training was initiated */
    @Column(nullable = false)
    private LocalDateTime trainingTime;

    /** Reason for retraining (e.g., SCHEDULED, LOW_ACCURACY, MANUAL) */
    @Column(nullable = false, length = 50)
    private String triggerReason;

    /** Winner accuracy before retraining */
    private Double previousWinnerAccuracy;

    /** Winner accuracy after retraining */
    private Double newWinnerAccuracy;

    /** Goal error average before retraining */
    private Double previousGoalError;

    /** Goal error average after retraining */
    private Double newGoalError;

    /** Card error average before retraining */
    private Double previousCardError;

    /** Card error average after retraining */
    private Double newCardError;

    /** Corner error average before retraining */
    private Double previousCornerError;

    /** Corner error average after retraining */
    private Double newCornerError;

    /** Number of matches used for this training */
    private Integer matchesUsed;

    /** Number of new evaluations since last training */
    private Integer newEvaluations;

    /**
     * JSON description of weight adjustments made.
     * Example: {"teamFormScoreWeight": 0.35 -> 0.45, "attackingStrengthWeight": 0.2 -> 0.3}
     */
    @Column(columnDefinition = "TEXT")
    private String weightAdjustments;

    /** Duration of training in milliseconds */
    private Long trainingDurationMs;

    /** Whether the training completed successfully */
    @Column(nullable = false)
    private Boolean success;

    /** Error message if training failed */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /** Model version after training */
    private String modelVersion;
}

