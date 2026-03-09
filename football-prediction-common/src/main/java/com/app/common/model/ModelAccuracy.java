package com.app.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity storing aggregated model accuracy metrics.
 * Tracks global, per-league, and per-team accuracy over time.
 */
@Entity
@Table(name = "model_accuracy", indexes = {
    @Index(name = "idx_accuracy_scope", columnList = "scope"),
    @Index(name = "idx_accuracy_scope_key", columnList = "scopeKey"),
    @Index(name = "idx_accuracy_calculated", columnList = "calculatedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelAccuracy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Scope of the accuracy metric: GLOBAL, LEAGUE, TEAM
     */
    @Column(nullable = false, length = 20)
    private String scope;

    /**
     * Key for the scope (e.g., league name or team name).
     * NULL for GLOBAL scope.
     */
    private String scopeKey;

    /** Total number of predictions evaluated */
    @Column(nullable = false)
    private Long totalPredictions;

    /** Number of predictions where the winner was correct */
    @Column(nullable = false)
    private Long correctWinnerPredictions;

    /** Number of exact score predictions */
    @Column(nullable = false)
    private Long exactScorePredictions;

    /**
     * winnerAccuracy = correctWinnerPredictions / totalPredictions (0.0 - 1.0)
     */
    @Column(nullable = false)
    private Double winnerAccuracy;

    /**
     * scoreAccuracy = exactScorePredictions / totalPredictions (0.0 - 1.0)
     */
    @Column(nullable = false)
    private Double scoreAccuracy;

    /**
     * Average goal difference error across all evaluations.
     */
    @Column(nullable = false)
    private Double goalErrorAverage;

    /**
     * Average card prediction error across all evaluations.
     */
    @Builder.Default
    private Double cardErrorAverage = 0.0;

    /**
     * Average corner prediction error across all evaluations.
     */
    @Builder.Default
    private Double cornerErrorAverage = 0.0;

    /** Season identifier for tracking accuracy over time */
    private String season;

    /** Timestamp of this accuracy calculation */
    @Column(nullable = false)
    private LocalDateTime calculatedAt;
}

