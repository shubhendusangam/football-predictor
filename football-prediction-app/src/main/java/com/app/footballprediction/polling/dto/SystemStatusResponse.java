package com.app.footballprediction.polling.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO for system status response used by UI dashboard.
 * Provides comprehensive view of sync and model training status.
 */
@Data
@Builder
public class SystemStatusResponse {

    // Sync Status
    private LocalDateTime lastSyncTime;
    private int matchesFetchedToday;
    private int matchesInsertedToday;
    private int matchesUpdatedToday;
    private String syncStatus; // SUCCESS, FAILED, IN_PROGRESS, PENDING
    private String syncErrorMessage;

    // Model Status
    private LocalDateTime lastModelTrainingTime;
    private String modelVersion;
    private boolean trainingRunning;
    private boolean retrainTriggeredToday;
    private String retrainDecisionReason;
    private Long lastTrainingDurationMs;

    // Data Freshness
    private String latestCompletedMatchDate;
    private int daysSinceLastMatch;

    // System Health
    private boolean pollingEnabled;
    private boolean autoRetrainEnabled;
    private int newMatchesSinceLastTraining;

    // Match Day Status (for smart refresh)
    private boolean matchDay;
    private int totalMatchesToday;
    private int completedMatchesToday;
    private boolean allMatchesCompleted;
    private LocalDateTime lastMatchCompletionTimestamp;

    /**
     * Create a status response indicating system is healthy.
     */
    public static SystemStatusResponse healthy() {
        return SystemStatusResponse.builder()
            .syncStatus("PENDING")
            .pollingEnabled(true)
            .autoRetrainEnabled(true)
            .build();
    }
}

