package com.app.footballprediction.service;

import com.app.common.model.SyncStatusEntry;
import com.app.common.model.SyncStatusEntry.SyncType;
import com.app.common.repository.SyncStatusEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Orchestrates live fixture and result synchronization from football-data.org.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Hourly fixture sync ({@link #syncFixtures()})</li>
 *   <li>Nightly result sync ({@link #syncResults()})</li>
 *   <li>Manual trigger ({@link #triggerFullSync(String)})</li>
 *   <li>Persists {@link SyncStatusEntry} for every operation</li>
 * </ul>
 *
 * <p>Delegates API calls to {@link ApiDataSyncService} and
 * records every sync attempt (success or failure) as an audit trail.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataSyncService {

    private final ApiDataSyncService apiDataSyncService;
    private final SyncStatusEntryRepository syncStatusRepo;

    @Value("${api.sync.competition:PL}")
    private String defaultCompetition;

    @Value("${scheduler.api-sync.enabled:true}")
    private boolean syncEnabled;

    // ══════════════════════════════════════════════════════════════
    // Scheduled Jobs
    // ══════════════════════════════════════════════════════════════

    /**
     * Hourly fixture sync — fetches upcoming/scheduled matches.
     * Cron: every hour at minute 0.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void syncFixtures() {
        if (!syncEnabled) {
            log.debug("Fixture sync disabled, skipping");
            return;
        }
        executeSyncWithAudit(SyncType.FIXTURES, defaultCompetition, "SCHEDULED_HOURLY");
    }

    /**
     * Nightly result sync — fetches finished match results.
     * Cron: 22:30 every day (after most matches finish).
     */
    @Scheduled(cron = "0 30 22 * * *")
    public void syncResults() {
        if (!syncEnabled) {
            log.debug("Result sync disabled, skipping");
            return;
        }
        executeSyncWithAudit(SyncType.RESULTS, defaultCompetition, "SCHEDULED_NIGHTLY");
    }

    // ══════════════════════════════════════════════════════════════
    // Manual Trigger
    // ══════════════════════════════════════════════════════════════

    /**
     * Trigger a full sync (standings + results + fixtures) on demand.
     *
     * @param triggeredBy who triggered (e.g. admin username)
     * @return the persisted {@link SyncStatusEntry}
     */
    public SyncStatusEntry triggerFullSync(String triggeredBy) {
        return executeSyncWithAudit(SyncType.FULL, defaultCompetition, triggeredBy);
    }

    /**
     * Trigger sync for a specific type.
     */
    public SyncStatusEntry triggerSync(SyncType type, String competition, String triggeredBy) {
        return executeSyncWithAudit(type, competition, triggeredBy);
    }

    // ══════════════════════════════════════════════════════════════
    // Status Queries
    // ══════════════════════════════════════════════════════════════

    /**
     * Get the most recent sync entry of any type.
     */
    public SyncStatusEntry getLatestSyncStatus() {
        return syncStatusRepo.findTopByOrderByStartedAtDesc().orElse(null);
    }

    /**
     * Get the most recent sync entry for a specific type.
     */
    public SyncStatusEntry getLatestSyncStatus(SyncType type) {
        return syncStatusRepo.findTopBySyncTypeOrderByStartedAtDesc(type).orElse(null);
    }

    /**
     * Get recent sync history (last 20 entries).
     */
    public List<SyncStatusEntry> getRecentHistory() {
        return syncStatusRepo.findTop20ByOrderByStartedAtDesc();
    }

    /**
     * Get recent failures (last 24 hours).
     */
    public List<SyncStatusEntry> getRecentFailures() {
        return syncStatusRepo.findRecentFailures(LocalDateTime.now().minusHours(24));
    }

    // ══════════════════════════════════════════════════════════════
    // Core Logic
    // ══════════════════════════════════════════════════════════════

    private SyncStatusEntry executeSyncWithAudit(SyncType type, String competition, String triggeredBy) {
        log.info("🔄 Starting {} sync for {} (triggered by: {})", type, competition, triggeredBy);

        SyncStatusEntry entry = SyncStatusEntry.builder()
                .syncType(type)
                .competition(competition)
                .startedAt(LocalDateTime.now())
                .triggeredBy(triggeredBy)
                .success(false)
                .build();

        // Persist immediately so we can track in-flight operations
        entry = syncStatusRepo.save(entry);

        long startTime = System.currentTimeMillis();
        int fetched = 0, inserted = 0, updated = 0, skipped = 0;

        try {
            switch (type) {
                case FIXTURES -> {
                    int count = apiDataSyncService.syncScheduledMatches(competition);
                    inserted = count;
                    fetched = count;
                }
                case RESULTS -> {
                    int[] result = apiDataSyncService.syncFinishedMatches(competition);
                    inserted = result[0];
                    updated = result[1];
                    fetched = inserted + updated;
                }
                case STANDINGS -> {
                    int count = apiDataSyncService.syncStandings(competition);
                    fetched = count;
                    inserted = count;
                }
                case FULL -> {
                    apiDataSyncService.syncAll(competition);
                    // syncAll doesn't return counts — mark as success
                    fetched = -1; // indicates "not counted individually"
                }
            }

            long durationMs = System.currentTimeMillis() - startTime;

            entry.setFinishedAt(LocalDateTime.now());
            entry.setDurationMs(durationMs);
            entry.setRecordsFetched(fetched);
            entry.setRecordsInserted(inserted);
            entry.setRecordsUpdated(updated);
            entry.setRecordsSkipped(skipped);
            entry.setSuccess(true);

            log.info("✅ {} sync completed for {} in {}ms: {} fetched, {} inserted, {} updated",
                    type, competition, durationMs, fetched, inserted, updated);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;

            entry.setFinishedAt(LocalDateTime.now());
            entry.setDurationMs(durationMs);
            entry.setSuccess(false);
            entry.setErrorMessage(truncate(e.getMessage()));

            log.error("❌ {} sync failed for {} after {}ms: {}",
                    type, competition, durationMs, e.getMessage(), e);
        }

        return syncStatusRepo.save(entry);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 2000 ? s : s.substring(0, 2000);
    }
}

