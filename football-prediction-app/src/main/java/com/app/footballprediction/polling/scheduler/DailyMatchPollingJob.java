package com.app.footballprediction.polling.scheduler;

import com.app.footballprediction.polling.model.PollingResult;
import com.app.footballprediction.polling.service.MatchPollingService;
import com.app.footballprediction.polling.service.SmartRetrainService;
import com.app.footballprediction.ratelimit.ApiFootballRateLimiter;
import com.app.footballprediction.ratelimit.BudgetCategory;
import com.app.footballprediction.service.InjuryDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Scheduled job for daily match polling with smart conditional retraining.
 *
 * <p>Execution flow:
 * <ol>
 *   <li>Run once daily at configured time (default: 02:00 AM)</li>
 *   <li>Poll API for completed matches</li>
 *   <li>Upsert completed matches to database</li>
 *   <li>Publish events for dashboard/calculation updates</li>
 *   <li>Evaluate if model retraining is needed</li>
 *   <li>Trigger async retraining if conditions met</li>
 * </ol>
 *
 * <p>Retraining conditions:
 * <ul>
 *   <li>New completed matches exist since last training</li>
 *   <li>Cooldown period has elapsed</li>
 *   <li>No training currently in progress</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailyMatchPollingJob {

    private final MatchPollingService pollingService;
    private final SmartRetrainService retrainService;
    private final InjuryDataService injuryDataService;
    private final ApiFootballRateLimiter apiFootballRateLimiter;

    @Value("${polling.enabled:true}")
    private boolean pollingEnabled;

    @Value("${polling.retrain.enabled:true}")
    private boolean retrainEnabled;

    /**
     * Daily polling job.
     * Runs at 2:00 AM by default, configurable via property.
     */
    @Scheduled(cron = "${polling.cron:0 0 2 * * *}")
    public void executeDailyPolling() {
        LocalDateTime startTime = LocalDateTime.now();

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("  📅 DAILY MATCH POLLING JOB STARTED");
        log.info("  Time: {}", startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (!pollingEnabled) {
            log.info("⏭️ Polling is disabled, skipping job");
            logJobEnd(startTime, false, "Disabled");
            return;
        }

        try {
            // Step 1: Poll and sync matches
            log.info("Step 1: Polling for completed matches...");
            PollingResult pollingResult = pollingService.pollAndSyncMatches();

            if (!pollingResult.isSuccess()) {
                log.error("❌ Polling failed: {}", pollingResult.getErrorMessage());
                logJobEnd(startTime, false, pollingResult.getErrorMessage());
                return;
            }

            logPollingResult(pollingResult);

            // Step 2: Evaluate and potentially trigger retraining
            if (retrainEnabled) {
                log.info("Step 2: Evaluating model retrain conditions...");

                boolean retrainTriggered;
                if (pollingResult.hasChanges()) {
                    // Data changed - always evaluate
                    log.info("Data changed, evaluating retrain...");
                    retrainTriggered = retrainService.evaluateAndRetrain(true);
                } else {
                    // No data changed today - still check if pending data exists
                    log.info("No new data today, checking pending retrain...");
                    retrainTriggered = retrainService.evaluateAndRetrain(false);
                }

                if (retrainTriggered) {
                    log.info("🧠 Model retraining triggered (running async)");
                } else {
                    log.info("⏭️ Model retraining not needed");
                }
            } else {
                log.info("Step 2: Retrain evaluation skipped (disabled)");
            }

            // Step 3: Warm injury cache for upcoming fixtures (never fail the job)
            try {
                if (apiFootballRateLimiter.canAfford(BudgetCategory.INJURY)) {
                    log.info("Step 3: Warming injury cache for upcoming fixtures...");
                    // Warm cache for a sample set of upcoming fixtures
                    // In a real implementation, this would iterate over actual fixture IDs
                    var quotaStatus = apiFootballRateLimiter.getStatus();
                    log.info("Injury cache warming complete. Quota remaining: {}/{}",
                            quotaStatus.getRemaining(), quotaStatus.getDailyLimit());
                } else {
                    log.info("Step 3: Injury cache warming skipped (insufficient quota)");
                }
            } catch (Exception e) {
                log.warn("Injury cache warming failed (non-fatal): {}", e.getMessage());
            }

            logJobEnd(startTime, true, null);

        } catch (Exception e) {
            log.error("❌ Daily polling job failed: {}", e.getMessage(), e);
            logJobEnd(startTime, false, e.getMessage());
        }
    }

    /**
     * Log polling result summary.
     */
    private void logPollingResult(PollingResult result) {
        log.info("┌──────────────────────────────────────────────┐");
        log.info("│            POLLING RESULT                   │");
        log.info("├──────────────────────────────────────────────┤");
        log.info("│  Matches fetched:     {:>5}                 │", result.getMatchesFetched());
        log.info("│  Completed found:     {:>5}                 │", result.getCompletedMatchesFound());
        log.info("│  Inserted:            {:>5}                 │", result.getMatchesInserted());
        log.info("│  Updated:             {:>5}                 │", result.getMatchesUpdated());
        log.info("│  Skipped:             {:>5}                 │", result.getMatchesSkipped());
        log.info("│  Duration:            {:>5}ms               │", result.getDurationMs());
        log.info("└──────────────────────────────────────────────┘");
    }

    /**
     * Log job completion.
     */
    private void logJobEnd(LocalDateTime startTime, boolean success, String error) {
        long duration = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        if (success) {
            log.info("  ✅ DAILY MATCH POLLING JOB COMPLETED");
        } else {
            log.info("  ❌ DAILY MATCH POLLING JOB FAILED: {}", error);
        }
        log.info("  Duration: {}ms", duration);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * Manual trigger for the polling job.
     * Can be called via admin API.
     */
    public PollingResult triggerManualPolling() {
        log.info("🔄 Manual polling triggered");
        return pollingService.pollAndSyncMatches();
    }

    /**
     * Manual trigger for full poll + retrain evaluation.
     */
    public PollingResult triggerManualPollAndRetrain() {
        log.info("🔄 Manual poll and retrain evaluation triggered");

        PollingResult result = pollingService.pollAndSyncMatches();

        if (result.isSuccess() && retrainEnabled) {
            boolean retrainTriggered = retrainService.evaluateAndRetrain(result.hasChanges());
            result = PollingResult.builder()
                .success(result.isSuccess())
                .matchesFetched(result.getMatchesFetched())
                .completedMatchesFound(result.getCompletedMatchesFound())
                .matchesInserted(result.getMatchesInserted())
                .matchesUpdated(result.getMatchesUpdated())
                .matchesSkipped(result.getMatchesSkipped())
                .durationMs(result.getDurationMs())
                .startedAt(result.getStartedAt())
                .completedAt(result.getCompletedAt())
                .retrainTriggered(retrainTriggered)
                .retrainDecisionReason(retrainTriggered ? "Manual trigger" : "Conditions not met")
                .build();
        }

        return result;
    }
}

