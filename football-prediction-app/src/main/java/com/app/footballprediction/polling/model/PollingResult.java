package com.app.footballprediction.polling.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Result of a daily polling operation.
 * Contains details about what was fetched and processed.
 */
@Data
@Builder
public class PollingResult {

    /**
     * Whether the polling was successful.
     */
    private boolean success;

    /**
     * Number of matches fetched from API.
     */
    private int matchesFetched;

    /**
     * Number of completed matches found.
     */
    private int completedMatchesFound;

    /**
     * Number of new matches inserted.
     */
    private int matchesInserted;

    /**
     * Number of existing matches updated.
     */
    private int matchesUpdated;

    /**
     * Number of matches skipped (no changes).
     */
    private int matchesSkipped;

    /**
     * Duration of the operation in milliseconds.
     */
    private long durationMs;

    /**
     * Timestamp when polling started.
     */
    private LocalDateTime startedAt;

    /**
     * Timestamp when polling completed.
     */
    private LocalDateTime completedAt;

    /**
     * Error message if polling failed.
     */
    private String errorMessage;

    /**
     * Whether model retraining was triggered.
     */
    private boolean retrainTriggered;

    /**
     * Reason for retrain decision.
     */
    private String retrainDecisionReason;

    /**
     * Check if any data was changed.
     */
    public boolean hasChanges() {
        return matchesInserted > 0 || matchesUpdated > 0;
    }

    /**
     * Create a successful result.
     */
    public static PollingResult success(int fetched, int completed, int inserted,
                                        int updated, int skipped, long durationMs) {
        return PollingResult.builder()
            .success(true)
            .matchesFetched(fetched)
            .completedMatchesFound(completed)
            .matchesInserted(inserted)
            .matchesUpdated(updated)
            .matchesSkipped(skipped)
            .durationMs(durationMs)
            .completedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Create a failure result.
     */
    public static PollingResult failure(String errorMessage) {
        return PollingResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .completedAt(LocalDateTime.now())
            .build();
    }
}

