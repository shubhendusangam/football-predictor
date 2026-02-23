package com.app.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Single-row configuration entity for system settings.
 * Stores global application settings that can be modified by admins.
 */
@Entity
@Table(name = "system_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Whether the prediction engine is enabled.
     */
    @Column(name = "prediction_engine_enabled")
    @Builder.Default
    private Boolean predictionEngineEnabled = true;

    /**
     * Whether automatic model retraining is enabled.
     */
    @Column(name = "auto_retrain_enabled")
    @Builder.Default
    private Boolean autoRetrainEnabled = true;

    /**
     * Whether automatic data fetching is enabled.
     */
    @Column(name = "auto_fetch_enabled")
    @Builder.Default
    private Boolean autoFetchEnabled = true;

    /**
     * Minimum confidence threshold for predictions (0-100).
     */
    @Column(name = "min_confidence_threshold")
    @Builder.Default
    private Integer minConfidenceThreshold = 60;

    /**
     * Number of recent matches to consider for form calculation.
     */
    @Column(name = "form_window_size")
    @Builder.Default
    private Integer formWindowSize = 5;


    /**
     * Default league for predictions (e.g., "PL", "PD", "BL1").
     */
    @Column(name = "default_league")
    @Builder.Default
    private String defaultLeague = "PL";

    /**
     * Maintenance mode - disables public API access when true.
     */
    @Column(name = "maintenance_mode")
    @Builder.Default
    private Boolean maintenanceMode = false;

    /**
     * Cache TTL in minutes.
     */
    @Column(name = "cache_ttl_minutes")
    @Builder.Default
    private Integer cacheTtlMinutes = 60;

    /**
     * Last model training timestamp.
     */
    @Column(name = "last_model_training")
    private LocalDateTime lastModelTraining;

    /**
     * Last data fetch timestamp.
     */
    @Column(name = "last_data_fetch")
    private LocalDateTime lastDataFetch;

    /**
     * Current model accuracy percentage.
     */
    @Column(name = "model_accuracy")
    private Double modelAccuracy;

    /**
     * Total number of predictions made.
     */
    @Column(name = "total_predictions")
    @Builder.Default
    private Long totalPredictions = 0L;

    /**
     * Settings last updated timestamp.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Username of admin who last updated settings.
     */
    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }
}

