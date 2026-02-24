package com.app.footballprediction.polling.service;

import com.app.common.ingestion.dto.InternalMatchDto;
import com.app.common.ingestion.event.CacheInvalidationEvent;
import com.app.common.ingestion.event.MatchUpdatedEvent;
import com.app.common.ingestion.event.StatsRefreshEvent;
import com.app.common.model.Match;
import com.app.common.model.SystemSettings;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.SystemSettingsRepository;
import com.app.footballprediction.ingestion.model.UpsertResult;
import com.app.footballprediction.ingestion.orchestrator.IngestionRouter;
import com.app.footballprediction.ingestion.service.IdempotentUpsertService;
import com.app.footballprediction.polling.model.PollingResult;
import com.app.footballprediction.polling.model.SyncStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Service for polling external APIs and syncing completed matches.
 *
 * <p>Core responsibilities:
 * <ul>
 *   <li>Fetch matches from configured API</li>
 *   <li>Filter for COMPLETED matches only</li>
 *   <li>Perform idempotent upsert to database</li>
 *   <li>Publish events for downstream processing</li>
 *   <li>Track sync status for observability</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchPollingService {

    private final IngestionRouter ingestionRouter;
    private final IdempotentUpsertService upsertService;
    private final MatchRepository matchRepository;
    private final SystemSettingsRepository systemSettingsRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${polling.competition:PL}")
    private String competition;

    @Value("${polling.days.lookback:7}")
    private int daysLookback;

    // Tracking state
    private final AtomicReference<LocalDateTime> lastSyncTime = new AtomicReference<>();
    private final AtomicInteger matchesInsertedToday = new AtomicInteger(0);
    private final AtomicInteger matchesUpdatedToday = new AtomicInteger(0);
    private final AtomicLong lastPollDurationMs = new AtomicLong(0);
    private final AtomicBoolean lastSyncSuccessful = new AtomicBoolean(true);
    private final AtomicReference<String> lastErrorMessage = new AtomicReference<>();
    private final AtomicBoolean retrainTriggeredToday = new AtomicBoolean(false);
    private final AtomicReference<String> retrainDecisionReason = new AtomicReference<>();

    /**
     * Poll for completed matches and sync to database.
     * This is the main entry point called by the scheduler.
     *
     * @return Result of the polling operation
     */
    @Transactional
    public PollingResult pollAndSyncMatches() {
        LocalDateTime startTime = LocalDateTime.now();
        long startMs = System.currentTimeMillis();

        log.info("🔄 Starting daily match polling for competition: {}", competition);

        try {
            // Reset daily counters if new day
            resetDailyCountersIfNeeded();

            // Step 1: Fetch recent completed matches from API
            List<InternalMatchDto> allMatches = ingestionRouter.fetchRecentMatches(competition, 50);

            if (allMatches.isEmpty()) {
                log.info("No matches returned from API");
                return recordResult(PollingResult.success(0, 0, 0, 0, 0,
                    System.currentTimeMillis() - startMs));
            }

            log.info("Fetched {} matches from API", allMatches.size());

            // Step 2: Filter for COMPLETED matches only
            List<InternalMatchDto> completedMatches = allMatches.stream()
                .filter(InternalMatchDto::isCompleted)
                .filter(m -> m.getFullTimeResult() != null)
                .collect(Collectors.toList());

            log.info("Found {} completed matches", completedMatches.size());

            if (completedMatches.isEmpty()) {
                log.info("No completed matches to process");
                return recordResult(PollingResult.success(allMatches.size(), 0, 0, 0, 0,
                    System.currentTimeMillis() - startMs));
            }

            // Step 3: Perform idempotent upsert
            UpsertResult upsertResult = upsertService.upsertMatches(completedMatches);

            // Step 4: Update tracking counters
            matchesInsertedToday.addAndGet(upsertResult.getInserted());
            matchesUpdatedToday.addAndGet(upsertResult.getUpdated());

            // Step 5: Publish events for downstream processing
            if (upsertResult.hasChanges()) {
                publishUpdateEvents(upsertResult);
            }

            // Step 6: Update last sync time in SystemSettings
            updateLastSyncTime();

            long duration = System.currentTimeMillis() - startMs;
            lastPollDurationMs.set(duration);
            lastSyncSuccessful.set(true);
            lastSyncTime.set(LocalDateTime.now());

            log.info("✅ Polling complete: {} fetched, {} completed, {} inserted, {} updated, {} skipped in {}ms",
                allMatches.size(), completedMatches.size(),
                upsertResult.getInserted(), upsertResult.getUpdated(),
                upsertResult.getSkipped(), duration);

            PollingResult result = PollingResult.builder()
                .success(true)
                .matchesFetched(allMatches.size())
                .completedMatchesFound(completedMatches.size())
                .matchesInserted(upsertResult.getInserted())
                .matchesUpdated(upsertResult.getUpdated())
                .matchesSkipped(upsertResult.getSkipped())
                .durationMs(duration)
                .startedAt(startTime)
                .completedAt(LocalDateTime.now())
                .build();

            return recordResult(result);

        } catch (Exception e) {
            log.error("❌ Polling failed: {}", e.getMessage(), e);
            lastSyncSuccessful.set(false);
            lastErrorMessage.set(e.getMessage());
            return recordResult(PollingResult.failure(e.getMessage()));
        }
    }

    /**
     * Publish events for downstream processing.
     * Events trigger:
     * - Standings recalculation
     * - Insights refresh (hot/cold teams, scorers, etc.)
     * - Cache invalidation
     */
    private void publishUpdateEvents(UpsertResult upsertResult) {
        log.debug("Publishing update events for {} affected matches",
            upsertResult.getAffectedMatches().size());

        // Publish individual match events
        for (Match match : upsertResult.getAffectedMatches()) {
            MatchUpdatedEvent.UpdateType updateType =
                upsertResult.getInsertedMatches().contains(match)
                    ? MatchUpdatedEvent.UpdateType.INSERTED
                    : MatchUpdatedEvent.UpdateType.RESULT_UPDATED;

            eventPublisher.publishEvent(new MatchUpdatedEvent(this, match, updateType));
        }

        // Publish stats refresh event
        if (!upsertResult.getAffectedSeasons().isEmpty()) {
            eventPublisher.publishEvent(new StatsRefreshEvent(
                this,
                upsertResult.getAffectedSeasons(),
                upsertResult.getAffectedTeams(),
                StatsRefreshEvent.RefreshScope.PARTIAL
            ));
        }

        // Publish cache invalidation event
        Set<String> cacheNames = Set.of(
            "trendingInsights",
            "teamStats",
            "matches",
            "predictions",
            "standings",
            "seasonStats"
        );
        eventPublisher.publishEvent(new CacheInvalidationEvent(
            this,
            cacheNames,
            upsertResult.getAffectedCacheKeys(),
            CacheInvalidationEvent.InvalidationType.SELECTIVE
        ));

        log.info("Published update events for {} seasons, {} teams",
            upsertResult.getAffectedSeasons().size(),
            upsertResult.getAffectedTeams().size());
    }

    /**
     * Check if there are new completed matches since last model training.
     * Used for smart retrain decision.
     *
     * @return Number of new completed matches since last training
     */
    public int countNewMatchesSinceLastTraining() {
        LocalDateTime lastTrainingTime = getLastModelTrainingTime();

        if (lastTrainingTime == null) {
            // Never trained - return total completed match count
            return (int) matchRepository.count();
        }

        // Count matches with results that were updated after last training
        // Using matchDate as proxy since we don't have updated_at on Match
        LocalDate lastTrainingDate = lastTrainingTime.toLocalDate();

        List<Match> recentMatches = matchRepository.findAllByOrderByMatchDateDesc()
            .stream()
            .filter(m -> m.getFullTimeResult() != null)
            .filter(m -> m.getMatchDate() != null && m.getMatchDate().isAfter(lastTrainingDate.minusDays(7)))
            .collect(Collectors.toList());

        log.debug("Found {} completed matches since last training on {}",
            recentMatches.size(), lastTrainingDate);

        return recentMatches.size();
    }

    /**
     * Get the current sync status for observability.
     */
    public SyncStatus getSyncStatus() {
        LocalDateTime lastTrainingTime = getLastModelTrainingTime();
        int newMatchesSinceTraining = countNewMatchesSinceLastTraining();

        return SyncStatus.builder()
            .lastSyncTime(lastSyncTime.get())
            .matchesInsertedToday(matchesInsertedToday.get())
            .matchesUpdatedToday(matchesUpdatedToday.get())
            .lastModelTrainingTime(lastTrainingTime)
            .modelVersion(getModelVersion())
            .retrainTriggeredToday(retrainTriggeredToday.get())
            .lastPollDurationMs(lastPollDurationMs.get())
            .recordsProcessed(matchesInsertedToday.get() + matchesUpdatedToday.get())
            .lastSyncSuccessful(lastSyncSuccessful.get())
            .errorMessage(lastErrorMessage.get())
            .newMatchesSinceLastTraining(newMatchesSinceTraining)
            .pollingEnabled(true)
            .retrainDecisionReason(retrainDecisionReason.get())
            .build();
    }

    /**
     * Mark that retraining was triggered.
     */
    public void markRetrainTriggered(String reason) {
        retrainTriggeredToday.set(true);
        retrainDecisionReason.set(reason);
    }

    /**
     * Reset daily counters at the start of a new day.
     */
    private void resetDailyCountersIfNeeded() {
        LocalDateTime lastSync = lastSyncTime.get();
        if (lastSync != null && !lastSync.toLocalDate().equals(LocalDate.now())) {
            log.info("New day detected, resetting daily counters");
            matchesInsertedToday.set(0);
            matchesUpdatedToday.set(0);
            retrainTriggeredToday.set(false);
            retrainDecisionReason.set(null);
        }
    }

    /**
     * Update last sync time in SystemSettings.
     */
    private void updateLastSyncTime() {
        try {
            SystemSettings settings = systemSettingsRepository.getSettings()
                .orElse(SystemSettings.builder().build());
            settings.setLastDataFetch(LocalDateTime.now());
            systemSettingsRepository.save(settings);
        } catch (Exception e) {
            log.warn("Failed to update last sync time in SystemSettings: {}", e.getMessage());
        }
    }

    /**
     * Get last model training time from SystemSettings.
     */
    private LocalDateTime getLastModelTrainingTime() {
        return systemSettingsRepository.getSettings()
            .map(SystemSettings::getLastModelTraining)
            .orElse(null);
    }

    /**
     * Get current model version.
     */
    private String getModelVersion() {
        // Could be enhanced to track actual model version
        LocalDateTime trainingTime = getLastModelTrainingTime();
        if (trainingTime != null) {
            return "v" + trainingTime.toLocalDate().toString();
        }
        return "unknown";
    }

    /**
     * Record result and return it.
     */
    private PollingResult recordResult(PollingResult result) {
        if (result.isSuccess()) {
            lastSyncSuccessful.set(true);
            lastErrorMessage.set(null);
        } else {
            lastSyncSuccessful.set(false);
            lastErrorMessage.set(result.getErrorMessage());
        }
        return result;
    }
}

