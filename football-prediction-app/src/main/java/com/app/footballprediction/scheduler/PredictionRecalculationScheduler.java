package com.app.footballprediction.scheduler;

import com.app.common.model.Match;
import com.app.common.model.ModelTrainingHistory;
import com.app.footballprediction.service.FeatureRecalculationService;
import com.app.footballprediction.service.MatchResultProcessor;
import com.app.footballprediction.service.ModelAccuracyService;
import com.app.footballprediction.service.ModelSelfTrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Smart prediction recalculation scheduler.
 *
 * <p><strong>Behaviour (event-driven, NOT blind polling):</strong></p>
 * <ol>
 *   <li>Runs a lightweight DB check on a cron schedule (default: every match-day
 *       evening window 18:00-23:00 every 30 min, plus once at 02:00 for overnight catch-up).</li>
 *   <li>If new finished matches are found for today that have not yet been evaluated,
 *       the full recalculation pipeline is triggered <strong>asynchronously</strong> in a
 *       background thread so it does not block the scheduler.</li>
 *   <li>If no live/pending matches remain for today, the model is updated
 *       (accuracy recalculated + self-training evaluated) because the match-day
 *       data is now complete.</li>
 *   <li>If no new finished matches exist at all, the scheduler does nothing.</li>
 * </ol>
 *
 * <p>This avoids wasteful 15-minute polling on non-match days and ensures the
 * pipeline only fires when real data changes in the database.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PredictionRecalculationScheduler {

    private final MatchResultProcessor matchResultProcessor;
    private final ModelAccuracyService modelAccuracyService;
    private final ModelSelfTrainingService modelSelfTrainingService;
    private final FeatureRecalculationService featureRecalculationService;

    @Value("${recalculation.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    /** Guard to prevent overlapping background pipeline runs. */
    private final AtomicBoolean pipelineRunning = new AtomicBoolean(false);

    // ═══════════════════════════════════════════════════════════════════
    // Scheduled entry points
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Match-day evening window check.
     * Runs every 30 minutes between 18:00 and 23:59 every day.
     * Most Premier League matches finish in this window (15:00-22:00 GMT).
     */
    @Scheduled(cron = "${recalculation.matchday.cron:0 0/30 18-23 * * *}")
    public void matchDayCheck() {
        if (!schedulerEnabled) return;
        checkAndProcess("MATCH_DAY_WINDOW");
    }

    /**
     * Overnight catch-up check.
     * Runs once at 02:00 AM to pick up any matches that finished late or
     * results that were synced overnight.
     */
    @Scheduled(cron = "${recalculation.overnight.cron:0 0 2 * * *}")
    public void overnightCatchUp() {
        if (!schedulerEnabled) return;
        // Check today AND yesterday (in case late-night matches were recorded after midnight)
        checkAndProcess("OVERNIGHT_CATCHUP");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Core logic
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Lightweight DB check → triggers background pipeline only when new data exists.
     */
    private void checkAndProcess(String trigger) {
        LocalDate today = LocalDate.now();

        log.debug("[{}] Checking DB for new finished matches on {}", trigger, today);

        // Quick count — no heavy processing
        List<Match> newFinished = matchResultProcessor.findNewFinishedMatchesForDate(today);

        if (newFinished.isEmpty()) {
            log.debug("[{}] No new finished matches on {}. Nothing to do.", trigger, today);

            // If it's the overnight run and there are no live matches,
            // this is a good time to update the model with any pending evaluations
            if ("OVERNIGHT_CATCHUP".equals(trigger)) {
                runModelUpdateIfIdle(today);
            }
            return;
        }

        log.info("[{}] Found {} new finished matches on {} — triggering recalculation pipeline in background",
                trigger, newFinished.size(), today);

        boolean liveMatchesRemaining = matchResultProcessor.hasLiveOrPendingMatches(today);

        // Fire the heavy pipeline in a background thread
        runPipelineAsync(today, newFinished, liveMatchesRemaining, trigger);
    }

    /**
     * Run the recalculation pipeline asynchronously so the scheduler thread is not blocked.
     * Uses AtomicBoolean to prevent overlapping runs.
     */
    @Async
    public void runPipelineAsync(LocalDate date, List<Match> newFinishedMatches,
                                  boolean liveMatchesRemaining, String trigger) {
        if (!pipelineRunning.compareAndSet(false, true)) {
            log.info("Pipeline already running, skipping this trigger ({})", trigger);
            return;
        }

        try {
            runRecalculationPipeline(date, newFinishedMatches, liveMatchesRemaining, trigger);
        } finally {
            pipelineRunning.set(false);
        }
    }

    /**
     * Full recalculation pipeline. Only called when new finished matches exist.
     *
     * Steps:
     * 1. Evaluate all new finished matches for today (create PredictionEvaluation records)
     * 2. Recalculate derived features for teams involved
     * 3. Update accuracy metrics
     * 4. If no live matches remain → retrain model (match-day data is complete)
     */
    private void runRecalculationPipeline(LocalDate date, List<Match> newFinishedMatches,
                                           boolean liveMatchesRemaining, String trigger) {
        LocalDateTime startTime = LocalDateTime.now();

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("🔄 RECALCULATION PIPELINE STARTED");
        log.info("   Trigger     : {}", trigger);
        log.info("   Date        : {}", date);
        log.info("   New matches : {}", newFinishedMatches.size());
        log.info("   Live matches: {}", liveMatchesRemaining ? "YES — model update deferred" : "NO — will update model");
        log.info("═══════════════════════════════════════════════════════════════");

        try {
            // ── Step 1: Evaluate predictions for today's finished matches ─────
            log.info("Step 1/4: Evaluating predictions for {} finished matches...", newFinishedMatches.size());
            int evaluations = matchResultProcessor.processFinishedMatchesForDate(date);
            log.info("  → Created {} prediction evaluations", evaluations);

            if (evaluations == 0) {
                log.info("No evaluations created (no predictions existed for these matches). Done.");
                logPipelineEnd(startTime, trigger, 0, false);
                return;
            }

            // ── Step 2: Recalculate features for teams in today's matches ─────
            log.info("Step 2/4: Recalculating derived features for affected teams...");
            featureRecalculationService.recalculateForRecentMatches(newFinishedMatches);
            log.info("  → Feature recalculation complete");

            // ── Step 3: Update accuracy metrics ───────────────────────────────
            log.info("Step 3/4: Recalculating model accuracy metrics...");
            modelAccuracyService.recalculateAllAccuracy();
            log.info("  → Accuracy metrics updated");

            // ── Step 4: Retrain model ONLY when no live matches remain ────────
            boolean modelRetrained = false;
            if (!liveMatchesRemaining) {
                log.info("Step 4/4: No live matches remaining — analyzing errors and retraining model...");
                ModelTrainingHistory result = modelSelfTrainingService.analyzeAndRetrain();
                if (result != null) {
                    modelRetrained = true;
                    log.info("  → Model retrained: success={}, duration={}ms",
                            result.getSuccess(), result.getTrainingDurationMs());
                } else {
                    log.info("  → No retraining needed (within thresholds or cooldown)");
                }
            } else {
                log.info("Step 4/4: Live matches still in progress — model update deferred until all matches complete");
            }

            logPipelineEnd(startTime, trigger, evaluations, modelRetrained);

        } catch (Exception e) {
            log.error("❌ Recalculation pipeline failed: {}", e.getMessage(), e);
        }
    }

    /**
     * When no new matches exist but it's a quiet period (overnight),
     * update the model with any pending evaluations that haven't triggered retraining yet.
     * Also runs the full prediction backfill to catch anything missed.
     */
    private void runModelUpdateIfIdle(LocalDate today) {
        if (pipelineRunning.get()) {
            log.debug("Pipeline is running, skipping idle model update");
            return;
        }

        // Always try to resolve any stale unresolved predictions (backfill)
        try {
            int resolved = matchResultProcessor.processAllUnresolvedPredictions();
            if (resolved > 0) {
                log.info("Overnight backfill resolved {} predictions", resolved);
                modelAccuracyService.recalculateAllAccuracy();
            }
        } catch (Exception e) {
            log.error("❌ Overnight backfill failed: {}", e.getMessage(), e);
        }

        // Check if there are any finished matches today at all (already evaluated or not)
        boolean hadMatchesToday = matchResultProcessor.hasFinishedMatchesToday(today);
        if (!hadMatchesToday) {
            log.debug("No matches today. Skipping model update.");
            return;
        }

        // No live matches remaining + we had matches today = good time to ensure model is up to date
        boolean liveRemaining = matchResultProcessor.hasLiveOrPendingMatches(today);
        if (liveRemaining) {
            log.debug("Live matches still pending, deferring model update.");
            return;
        }

        log.info("🧠 Overnight idle model update — all matches finished, checking if retraining needed...");
        try {
            modelAccuracyService.recalculateAllAccuracy();
            ModelTrainingHistory result = modelSelfTrainingService.analyzeAndRetrain();
            if (result != null) {
                log.info("✅ Overnight model retrained: success={}", result.getSuccess());
            } else {
                log.info("Model within thresholds or in cooldown. No retraining needed.");
            }
        } catch (Exception e) {
            log.error("❌ Overnight model update failed: {}", e.getMessage(), e);
        }
    }

    private void logPipelineEnd(LocalDateTime startTime, String trigger,
                                 int evaluations, boolean modelRetrained) {
        long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("✅ RECALCULATION PIPELINE COMPLETED");
        log.info("   Trigger      : {}", trigger);
        log.info("   Evaluations  : {}", evaluations);
        log.info("   Model trained: {}", modelRetrained);
        log.info("   Duration     : {}ms", durationMs);
        log.info("═══════════════════════════════════════════════════════════════");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Manual triggers (admin / testing)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Manually trigger the recalculation pipeline for today.
     * Also resolves any historical backlog.
     * Runs synchronously on the calling thread.
     */
    public void triggerManualRecalculation() {
        log.info("Manual recalculation triggered");
        LocalDate today = LocalDate.now();

        // Always process the full backlog first
        int resolved = matchResultProcessor.processAllUnresolvedPredictions();
        log.info("Manual run: resolved {} predictions from backlog", resolved);

        // Then check today's matches
        List<Match> newFinished = matchResultProcessor.findNewFinishedMatchesForDate(today);
        boolean liveRemaining = matchResultProcessor.hasLiveOrPendingMatches(today);

        if (!newFinished.isEmpty()) {
            runRecalculationPipeline(today, newFinished, liveRemaining, "MANUAL");
        } else if (resolved > 0) {
            // Backlog was resolved but no new today — still update accuracy
            modelAccuracyService.recalculateAllAccuracy();
            if (!liveRemaining) {
                modelSelfTrainingService.analyzeAndRetrain();
            }
        } else {
            log.info("Manual run: nothing to process");
        }
    }
}

