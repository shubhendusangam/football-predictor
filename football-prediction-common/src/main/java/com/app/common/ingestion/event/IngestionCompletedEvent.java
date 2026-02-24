package com.app.common.ingestion.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.Set;

/**
 * Domain event published when an ingestion batch completes.
 *
 * <p>This event provides summary information about the ingestion
 * and can be used for:
 * <ul>
 *   <li>Logging and auditing</li>
 *   <li>Triggering downstream processes</li>
 *   <li>Metrics collection</li>
 *   <li>Alerting on anomalies</li>
 * </ul>
 */
public class IngestionCompletedEvent extends ApplicationEvent {

    private final String competition;
    private final String season;
    private final int matchesProcessed;
    private final int matchesInserted;
    private final int matchesUpdated;
    private final int matchesSkipped;
    private final Set<String> errors;
    private final long durationMs;
    private final Instant completedAt;
    private final IngestionStatus status;

    public IngestionCompletedEvent(Object source, String competition, String season,
                                   int matchesProcessed, int matchesInserted,
                                   int matchesUpdated, int matchesSkipped,
                                   Set<String> errors, long durationMs,
                                   IngestionStatus status) {
        super(source);
        this.competition = competition;
        this.season = season;
        this.matchesProcessed = matchesProcessed;
        this.matchesInserted = matchesInserted;
        this.matchesUpdated = matchesUpdated;
        this.matchesSkipped = matchesSkipped;
        this.errors = errors;
        this.durationMs = durationMs;
        this.completedAt = Instant.now();
        this.status = status;
    }

    public String getCompetition() {
        return competition;
    }

    public String getSeason() {
        return season;
    }

    public int getMatchesProcessed() {
        return matchesProcessed;
    }

    public int getMatchesInserted() {
        return matchesInserted;
    }

    public int getMatchesUpdated() {
        return matchesUpdated;
    }

    public int getMatchesSkipped() {
        return matchesSkipped;
    }

    public Set<String> getErrors() {
        return errors;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public IngestionStatus getStatus() {
        return status;
    }

    /**
     * Check if ingestion was successful.
     */
    public boolean isSuccess() {
        return status == IngestionStatus.SUCCESS;
    }

    /**
     * Check if any matches were affected.
     */
    public boolean hasChanges() {
        return matchesInserted > 0 || matchesUpdated > 0;
    }

    /**
     * Ingestion status values.
     */
    public enum IngestionStatus {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILED,
        SHADOW_VALIDATION_FAILED,
        NO_DATA
    }
}

