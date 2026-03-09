package com.app.footballprediction.listener;

import com.app.footballprediction.service.HistoricalPredictionGenerator;
import com.app.footballprediction.service.MatchResultProcessor;
import com.app.footballprediction.service.ModelAccuracyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs prediction backfill on application startup <strong>asynchronously</strong>.
 *
 * <p>On first startup (or after code deploy) there may be many finished matches
 * with no Prediction records at all (historical CSV data, past API syncs).
 * This listener first generates predictions for those matches using the trained
 * model, then resolves them against actual results and recalculates accuracy
 * so the UI immediately shows real numbers instead of "No Prediction Data".</p>
 *
 * <p>The backfill is idempotent — matches that already have predictions
 * and already-resolved predictions are skipped.</p>
 *
 * <p><strong>Non-blocking:</strong> The backfill runs in a background thread via
 * {@code @Async} so the application is ready to serve requests immediately.
 * Check {@link #getStatus()} to monitor progress.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PredictionBackfillListener {

    private final HistoricalPredictionGenerator historicalPredictionGenerator;
    private final MatchResultProcessor matchResultProcessor;
    private final ModelAccuracyService modelAccuracyService;

    /** Backfill status for monitoring via admin API */
    private final AtomicReference<BackfillStatus> status = new AtomicReference<>(BackfillStatus.NOT_STARTED);

    public enum BackfillStatus {
        NOT_STARTED, GENERATING_PREDICTIONS, RESOLVING_PREDICTIONS, RECALCULATING_ACCURACY, COMPLETED, FAILED
    }

    public BackfillStatus getStatus() {
        return status.get();
    }

    /**
     * Run after all other startup listeners (data sync, model training, etc.)
     * have completed. Order 200 ensures this runs late.
     * Runs asynchronously to avoid blocking app startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(200)
    @Async
    public void onApplicationReady() {
        log.info("📊 Starting prediction backfill & accuracy bootstrap (async)...");
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Generate predictions for historical matches that have none
            int generated = 0;
            try {
                status.set(BackfillStatus.GENERATING_PREDICTIONS);
                generated = historicalPredictionGenerator.generateAll();
                if (generated > 0) {
                    log.info("Generated {} historical predictions", generated);
                }
            } catch (Exception e) {
                log.warn("Historical prediction generation failed (non-fatal): {}", e.getMessage());
            }

            // Step 2: Resolve all unresolved predictions against finished matches
            status.set(BackfillStatus.RESOLVING_PREDICTIONS);
            int resolved = matchResultProcessor.processAllUnresolvedPredictions();

            if (resolved > 0 || generated > 0) {
                log.info("Backfill: generated={}, resolved={} — recalculating accuracy...", generated, resolved);

                // Step 3: Recalculate global + per-team + per-league accuracy
                status.set(BackfillStatus.RECALCULATING_ACCURACY);
                modelAccuracyService.recalculateAllAccuracy();
                log.info("✅ Model accuracy metrics updated");
            } else {
                log.debug("All predictions already resolved (or no predictions exist yet)");
            }

            status.set(BackfillStatus.COMPLETED);
            long duration = System.currentTimeMillis() - startTime;
            log.info("📊 Backfill pipeline completed in {}ms (generated={}, resolved={})",
                    duration, generated, resolved);

        } catch (Exception e) {
            status.set(BackfillStatus.FAILED);
            log.error("❌ Prediction backfill failed: {}", e.getMessage(), e);
            // Non-fatal — the app continues running; scheduler will pick it up later
        }
    }
}

