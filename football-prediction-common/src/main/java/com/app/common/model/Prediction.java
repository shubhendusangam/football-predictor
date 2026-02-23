package com.app.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity representing a match prediction record.
 * Stores both the prediction made and the actual result for accuracy tracking.
 */
@Entity
@Table(name = "predictions", indexes = {
    @Index(name = "idx_prediction_team", columnList = "teamId"),
    @Index(name = "idx_prediction_season", columnList = "season"),
    @Index(name = "idx_prediction_match", columnList = "matchId"),
    @Index(name = "idx_prediction_date", columnList = "predictionDate")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the match this prediction was made for
     */
    @Column(nullable = false)
    private Long matchId;

    /**
     * The team this prediction relates to (for team-specific analytics)
     */
    private Long teamId;

    /**
     * Team name for easier querying
     */
    @Column(nullable = false)
    private String teamName;

    /**
     * Opponent team name
     */
    private String opponentName;

    /**
     * Whether the team was playing at home
     */
    @Column(nullable = false)
    private boolean isHome;

    /**
     * Season identifier (e.g., "2023-24")
     */
    private String season;

    /**
     * Match date
     */
    private LocalDate matchDate;

    /**
     * Predicted result for the team: WIN, DRAW, LOSS
     */
    @Column(nullable = false)
    private String predictedResult;

    /**
     * Actual result for the team: WIN, DRAW, LOSS (null if match not played yet)
     */
    private String actualResult;

    /**
     * Predicted home team goals
     */
    private Integer predictedHomeGoals;

    /**
     * Predicted away team goals
     */
    private Integer predictedAwayGoals;

    /**
     * Actual home team goals (null if match not played yet)
     */
    private Integer actualHomeGoals;

    /**
     * Actual away team goals (null if match not played yet)
     */
    private Integer actualAwayGoals;

    /**
     * Model's confidence in the prediction (0.0 - 1.0)
     */
    @Column(nullable = false)
    private Double confidence;

    /**
     * Home win probability from the model
     */
    private Double probHomeWin;

    /**
     * Draw probability from the model
     */
    private Double probDraw;

    /**
     * Away win probability from the model
     */
    private Double probAwayWin;

    /**
     * Whether the prediction was correct (null if match not played yet)
     */
    private Boolean isCorrect;

    /**
     * Timestamp when the prediction was made
     */
    @Column(nullable = false)
    private LocalDateTime predictionDate;

    /**
     * Timestamp when the actual result was recorded
     */
    private LocalDateTime resultRecordedDate;

    /**
     * Model version used for this prediction
     */
    private String modelVersion;

    /**
     * Additional metadata as JSON
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    /**
     * Check if this was a high confidence prediction (>= 0.6)
     */
    public boolean isHighConfidence() {
        return confidence != null && confidence >= 0.6;
    }

    /**
     * Check if the prediction has been resolved (match played)
     */
    public boolean isResolved() {
        return actualResult != null;
    }
}

