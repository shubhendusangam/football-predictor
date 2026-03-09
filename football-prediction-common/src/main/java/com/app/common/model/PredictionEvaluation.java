package com.app.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing the evaluation of a prediction against actual match results.
 * Stores detailed accuracy metrics for each prediction after the match is completed.
 */
@Entity
@Table(name = "prediction_evaluations", indexes = {
    @Index(name = "idx_eval_match", columnList = "matchId"),
    @Index(name = "idx_eval_time", columnList = "evaluationTime"),
    @Index(name = "idx_eval_winner_correct", columnList = "winnerCorrect")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reference to the match this evaluation is for */
    @Column(nullable = false, unique = true)
    private Long matchId;

    /** Predicted home goals from the model */
    private Integer predictedHomeGoals;

    /** Predicted away goals from the model */
    private Integer predictedAwayGoals;

    /** Actual home goals from the finished match */
    private Integer actualHomeGoals;

    /** Actual away goals from the finished match */
    private Integer actualAwayGoals;

    /**
     * Predicted winner: H (home), D (draw), A (away)
     */
    @Column(length = 10)
    private String predictedWinner;

    /**
     * Actual winner: H (home), D (draw), A (away)
     */
    @Column(length = 10)
    private String actualWinner;

    /**
     * Absolute difference between predicted and actual goal difference.
     * goalDifferenceError = abs((predictedHomeGoals - predictedAwayGoals) - (actualHomeGoals - actualAwayGoals))
     */
    private Integer goalDifferenceError;

    /** Whether the predicted winner matched the actual winner */
    @Column(nullable = false)
    private Boolean winnerCorrect;

    /** Whether the exact score was predicted correctly */
    @Column(nullable = false)
    private Boolean scoreExact;

    /**
     * Absolute error in card predictions.
     * cardPredictionError = abs(predictedCards - actualCards)
     */
    private Integer cardPredictionError;

    /**
     * Absolute error in corner predictions.
     * cornerPredictionError = abs(predictedCorners - actualCorners)
     */
    private Integer cornerPredictionError;

    /** Home team name for easier querying */
    private String homeTeam;

    /** Away team name for easier querying */
    private String awayTeam;

    /** Season identifier */
    private String season;

    /** Model confidence at the time of prediction */
    private Double predictionConfidence;

    /** Timestamp when the evaluation was performed */
    @Column(nullable = false)
    private LocalDateTime evaluationTime;
}

