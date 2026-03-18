package com.app.common.repository;

import com.app.common.model.SyncStatusEntry;
import com.app.common.model.SyncStatusEntry.SyncType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for persisted sync-status audit trail.
 */
@Repository
public interface SyncStatusEntryRepository extends JpaRepository<SyncStatusEntry, Long> {

    /**
     * Get the most recent sync entry for a given type.
     */
    Optional<SyncStatusEntry> findTopBySyncTypeOrderByStartedAtDesc(SyncType syncType);

    /**
     * Get the most recent successful sync for a given type.
     */
    Optional<SyncStatusEntry> findTopBySyncTypeAndSuccessTrueOrderByStartedAtDesc(SyncType syncType);

    /**
     * Get the most recent sync entry regardless of type.
     */
    Optional<SyncStatusEntry> findTopByOrderByStartedAtDesc();

    /**
     * Get recent sync history (last N entries).
     */
    List<SyncStatusEntry> findTop20ByOrderByStartedAtDesc();

    /**
     * Get sync entries in a date range.
     */
    List<SyncStatusEntry> findByStartedAtBetweenOrderByStartedAtDesc(
            LocalDateTime start, LocalDateTime end);

    /**
     * Count successful syncs since a given timestamp.
     */
    long countBySyncTypeAndSuccessTrueAndStartedAtAfter(SyncType syncType, LocalDateTime since);

    /**
     * Count failed syncs since a given timestamp.
     */
    long countBySyncTypeAndSuccessFalseAndStartedAtAfter(SyncType syncType, LocalDateTime since);

    /**
     * Get all failed syncs in the last N hours.
     */
    @Query("SELECT s FROM SyncStatusEntry s WHERE s.success = false AND s.startedAt > :since ORDER BY s.startedAt DESC")
    List<SyncStatusEntry> findRecentFailures(@Param("since") LocalDateTime since);
}

