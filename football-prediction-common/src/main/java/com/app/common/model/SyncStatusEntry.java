package com.app.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Persisted entity that records every sync operation (fixtures + results).
 *
 * <p>Unlike the in-memory {@code SyncStatus} POJO in the polling package,
 * this entity survives application restarts and provides a full audit trail.</p>
 */
@Entity
@Table(name = "sync_status", indexes = {
    @Index(name = "idx_sync_status_type", columnList = "sync_type"),
    @Index(name = "idx_sync_status_started", columnList = "started_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncStatusEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Type of sync operation: FIXTURES, RESULTS, STANDINGS, FULL.
     */
    @Column(name = "sync_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private SyncType syncType;

    /**
     * Competition code being synced (e.g. "PL").
     */
    @Column(name = "competition", length = 10)
    private String competition;

    /**
     * When this sync started.
     */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /**
     * When this sync finished (null if still running).
     */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /**
     * Duration in milliseconds.
     */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * Number of records fetched from the external API.
     */
    @Column(name = "records_fetched")
    @Builder.Default
    private Integer recordsFetched = 0;

    /**
     * Number of new records inserted.
     */
    @Column(name = "records_inserted")
    @Builder.Default
    private Integer recordsInserted = 0;

    /**
     * Number of existing records updated.
     */
    @Column(name = "records_updated")
    @Builder.Default
    private Integer recordsUpdated = 0;

    /**
     * Number of records skipped (already up to date).
     */
    @Column(name = "records_skipped")
    @Builder.Default
    private Integer recordsSkipped = 0;

    /**
     * Whether the sync completed successfully.
     */
    @Column(name = "success", nullable = false)
    @Builder.Default
    private Boolean success = false;

    /**
     * Error message if sync failed.
     */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /**
     * Who/what triggered the sync.
     */
    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    /**
     * Number of retries performed.
     */
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    public enum SyncType {
        FIXTURES,
        RESULTS,
        STANDINGS,
        FULL
    }
}

