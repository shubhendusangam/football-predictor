package com.app.footballprediction.polling.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Tracks the status of the daily match polling and sync operations.
 * Provides observability into the automated data pipeline.
 */
@Data
@Builder
public class SyncStatus {

    /**
     * Timestamp of the last successful sync.
     */
    private LocalDateTime lastSyncTime;

    /**
     * Number of matches inserted during the last sync.
     */
    private int matchesInsertedToday;

    /**
     * Number of matches updated during the last sync.
     */
    private int matchesUpdatedToday;

    /**
     * Timestamp of the last model training.
     */
    private LocalDateTime lastModelTrainingTime;

    /**
     * Current model version/identifier.
     */
    private String modelVersion;

    /**
     * Whether model retraining was triggered today.
     */
    private boolean retrainTriggeredToday;

    /**
     * Duration of the last poll operation in milliseconds.
     */
    private long lastPollDurationMs;

    /**
     * Total records processed in last sync.
     */
    private int recordsProcessed;

    /**
     * Status message from last operation.
     */
    private String statusMessage;

    /**
     * Whether the last sync was successful.
     */
    private boolean lastSyncSuccessful;

    /**
     * Error message if last sync failed.
     */
    private String errorMessage;

    /**
     * Number of new completed matches since last model training.
     */
    private int newMatchesSinceLastTraining;

    /**
     * Next scheduled sync time.
     */
    private LocalDateTime nextScheduledSync;

    /**
     * Whether the polling service is enabled.
     */
    private boolean pollingEnabled;

    /**
     * Current retrain decision reason.
     */
    private String retrainDecisionReason;
}

