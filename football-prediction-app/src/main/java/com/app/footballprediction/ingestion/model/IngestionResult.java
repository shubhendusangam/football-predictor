package com.app.footballprediction.ingestion.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Result of an ingestion operation.
 * Provides detailed metrics and status for monitoring and alerting.
 */
@Data
@Builder
public class IngestionResult {

    private String competition;
    private String season;
    private String pipeline;  // LEGACY or NEW
    private IngestionStatus status;

    private Instant startedAt;
    private Instant completedAt;

    private int totalFetched;
    private int inserted;
    private int updated;
    private int skipped;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    @Builder.Default
    private Set<String> affectedSeasons = new HashSet<>();

    @Builder.Default
    private Set<String> affectedTeams = new HashSet<>();

    private ShadowValidationResult shadowValidation;

    /**
     * Check if any changes were made.
     */
    public boolean hasChanges() {
        return inserted > 0 || updated > 0;
    }

    /**
     * Check if ingestion was successful.
     */
    public boolean isSuccess() {
        return status == IngestionStatus.SUCCESS || status == IngestionStatus.PARTIAL_SUCCESS;
    }

    /**
     * Get duration in milliseconds.
     */
    public long getDurationMs() {
        if (startedAt == null || completedAt == null) {
            return 0;
        }
        return completedAt.toEpochMilli() - startedAt.toEpochMilli();
    }

    /**
     * Get error count.
     */
    public int getErrorCount() {
        return errors != null ? errors.size() : 0;
    }

    /**
     * Ingestion status values.
     */
    public enum IngestionStatus {
        /** All matches processed successfully */
        SUCCESS,

        /** Some matches processed, some errors */
        PARTIAL_SUCCESS,

        /** Ingestion failed completely */
        FAILED,

        /** Shadow validation found differences, no writes made */
        SHADOW_VALIDATION_FAILED,

        /** No data available from provider */
        NO_DATA,

        /** Ingestion in progress */
        IN_PROGRESS,

        /** Skipped (feature flag disabled) */
        SKIPPED
    }

    /**
     * Create a builder with defaults.
     */
    public static IngestionResultBuilder defaultBuilder() {
        return builder()
            .startedAt(Instant.now())
            .status(IngestionStatus.IN_PROGRESS)
            .errors(new ArrayList<>())
            .affectedSeasons(new HashSet<>())
            .affectedTeams(new HashSet<>());
    }
}

