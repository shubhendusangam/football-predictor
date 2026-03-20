package com.app.modeltraining.startup;

import com.app.common.repository.MatchRepository;
import com.app.modeltraining.service.PoissonModelTrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Trains the Poisson score model automatically on first startup
 * when the model file does not yet exist.
 *
 * <p>In Docker deployments the model-training-service starts <em>before</em>
 * the main app (via {@code depends_on}).  The main app is responsible for
 * CSV ingestion, so the database is usually empty when this service boots.
 * To handle this, the trainer runs asynchronously and retries with
 * exponential back-off until match data appears.</p>
 *
 * <p>If the model file already exists, the trainer is a no-op.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PoissonModelStartupTrainer {

    private final PoissonModelTrainingService poissonModelTrainingService;
    private final MatchRepository matchRepository;

    @Value("${model.poisson.output.path:../data/poisson_score.model}")
    private String poissonModelPath;

    /** Maximum number of retry attempts before giving up. */
    private static final int MAX_RETRIES = 10;

    /** Initial delay between retries (seconds). Doubles each attempt. */
    private static final int INITIAL_DELAY_SECONDS = 15;

    /** Maximum delay cap (seconds). */
    private static final int MAX_DELAY_SECONDS = 120;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void trainIfMissing() {
        File modelFile = new File(poissonModelPath);
        if (modelFile.exists()) {
            log.info("✓ Poisson score model already exists at {}", modelFile.getAbsolutePath());
            return;
        }

        log.info("⏳ Poisson score model not found — waiting for match data before training...");

        int delay = INITIAL_DELAY_SECONDS;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {

            // Re-check: another process (e.g. main app) may have trained it
            if (new File(poissonModelPath).exists()) {
                log.info("✓ Poisson model appeared on disk (trained by another process)");
                return;
            }

            // Check whether the database has enough data yet
            long matchCount = matchRepository.count();
            if (matchCount < 100) {
                log.info("   Attempt {}/{}: {} matches in DB (need ≥ 100). Retrying in {}s...",
                        attempt, MAX_RETRIES, matchCount, delay);
                try {
                    Thread.sleep(delay * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Poisson startup trainer interrupted");
                    return;
                }
                delay = Math.min(delay * 2, MAX_DELAY_SECONDS);
                continue;
            }

            // Data is available — attempt training
            long start = System.currentTimeMillis();
            try {
                String report = poissonModelTrainingService.trainPoissonModel();
                long duration = System.currentTimeMillis() - start;
                log.info("✓ Poisson score model trained in {}ms\n{}", duration, report);
                return;
            } catch (IllegalStateException e) {
                log.warn("⚠ Poisson training attempt {}/{} failed (insufficient data): {}",
                        attempt, MAX_RETRIES, e.getMessage());
            } catch (Exception e) {
                log.error("✗ Poisson training attempt {}/{} failed", attempt, MAX_RETRIES, e);
            }

            try {
                Thread.sleep(delay * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            delay = Math.min(delay * 2, MAX_DELAY_SECONDS);
        }

        log.warn("⚠ Poisson model startup training gave up after {} attempts. "
                + "Train manually via POST /api/training/train-poisson", MAX_RETRIES);
    }
}

