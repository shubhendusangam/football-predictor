package com.app.footballprediction.polling.service;

import com.app.common.model.SystemSettings;
import com.app.common.repository.SystemSettingsRepository;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service for smart conditional model retraining.
 *
 * <p>Retraining decision logic:
 * <ol>
 *   <li>Check if new completed matches exist since last training</li>
 *   <li>Check minimum time between trainings (cooldown)</li>
 *   <li>Trigger async training if conditions are met</li>
 *   <li>Keep previous model active if training fails</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmartRetrainService {

    private final MatchPollingService pollingService;
    private final ModelTrainingService modelTrainingService;
    private final SystemSettingsRepository systemSettingsRepository;

    @Value("${retrain.min.new.matches:5}")
    private int minNewMatchesForRetrain;

    @Value("${retrain.cooldown.hours:24}")
    private int cooldownHours;

    // Training state
    private final AtomicBoolean trainingInProgress = new AtomicBoolean(false);
    private final AtomicReference<String> lastTrainingReport = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastTrainingAttempt = new AtomicReference<>();

    /**
     * Evaluate whether model retraining should be triggered.
     * This is called after daily polling completes.
     *
     * @param dataChanged Whether any match data changed during polling
     * @return True if retraining was triggered
     */
    public boolean evaluateAndRetrain(boolean dataChanged) {
        log.info("📊 Evaluating model retrain conditions...");

        // If data changed today, we already have fresh results - good time to check

        // Get new matches since last training
        int newMatchesSinceTraining = pollingService.countNewMatchesSinceLastTraining();
        log.info("New completed matches since last training: {}", newMatchesSinceTraining);

        // Check if enough new matches
        if (newMatchesSinceTraining < minNewMatchesForRetrain) {
            String reason = String.format(
                "Not enough new matches (%d < %d required)",
                newMatchesSinceTraining, minNewMatchesForRetrain);
            log.info("⏭️ Skipping retrain: {}", reason);
            pollingService.markRetrainTriggered(false + ": " + reason);
            return false;
        }

        // Check cooldown period
        LocalDateTime lastTrainingTime = getLastModelTrainingTime();
        if (lastTrainingTime != null) {
            LocalDateTime cooldownEnd = lastTrainingTime.plusHours(cooldownHours);
            if (LocalDateTime.now().isBefore(cooldownEnd)) {
                String reason = String.format(
                    "Cooldown period not elapsed (last training: %s, cooldown ends: %s)",
                    lastTrainingTime, cooldownEnd);
                log.info("⏭️ Skipping retrain: {}", reason);
                pollingService.markRetrainTriggered(false + ": " + reason);
                return false;
            }
        }

        // Check if already training
        if (trainingInProgress.get()) {
            String reason = "Training already in progress";
            log.info("⏭️ Skipping retrain: {}", reason);
            pollingService.markRetrainTriggered(false + ": " + reason);
            return false;
        }

        // All conditions met - trigger async training
        String reason = String.format(
            "Triggered: %d new matches, cooldown elapsed",
            newMatchesSinceTraining);
        log.info("🚀 Triggering model retrain: {}", reason);
        pollingService.markRetrainTriggered(reason);

        // Start async training
        triggerAsyncTraining();

        return true;
    }

    /**
     * Trigger model training asynchronously.
     * Training runs in background and doesn't block the dashboard.
     */
    @Async
    public CompletableFuture<String> triggerAsyncTraining() {
        if (!trainingInProgress.compareAndSet(false, true)) {
            log.warn("Training already in progress, skipping");
            return CompletableFuture.completedFuture("Training already in progress");
        }

        lastTrainingAttempt.set(LocalDateTime.now());

        log.info("🧠 Starting asynchronous model training...");
        long startTime = System.currentTimeMillis();

        try {
            String report = modelTrainingService.trainAndEvaluate();

            long duration = System.currentTimeMillis() - startTime;
            log.info("✅ Model training completed in {}ms", duration);

            // Update training timestamp in SystemSettings
            updateTrainingTimestamp(report);

            lastTrainingReport.set(report);

            return CompletableFuture.completedFuture(report);

        } catch (Exception e) {
            log.error("❌ Model training failed: {}", e.getMessage(), e);
            log.warn("Previous model remains active");

            // Don't update timestamp on failure - keep previous model
            lastTrainingReport.set("Training failed: " + e.getMessage());

            return CompletableFuture.completedFuture("Training failed: " + e.getMessage());

        } finally {
            trainingInProgress.set(false);
        }
    }

    /**
     * Update training timestamp in SystemSettings after successful training.
     */
    private void updateTrainingTimestamp(String report) {
        try {
            SystemSettings settings = systemSettingsRepository.getSettings()
                .orElse(SystemSettings.builder().build());

            settings.setLastModelTraining(LocalDateTime.now());

            // Try to extract accuracy from report
            Double accuracy = extractAccuracyFromReport(report);
            if (accuracy != null) {
                settings.setModelAccuracy(accuracy);
            }

            systemSettingsRepository.save(settings);
            log.info("Updated training timestamp in SystemSettings");

        } catch (Exception e) {
            log.error("Failed to update training timestamp: {}", e.getMessage());
        }
    }

    /**
     * Extract accuracy percentage from training report.
     */
    private Double extractAccuracyFromReport(String report) {
        if (report == null) return null;

        try {
            // Look for "Accuracy  : XX.X%" pattern
            int idx = report.indexOf("Accuracy");
            if (idx >= 0) {
                int colonIdx = report.indexOf(":", idx);
                if (colonIdx >= 0) {
                    int endIdx = report.indexOf("%", colonIdx);
                    if (endIdx >= 0) {
                        String accuracyStr = report.substring(colonIdx + 1, endIdx).trim();
                        return Double.parseDouble(accuracyStr);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract accuracy from report");
        }
        return null;
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
     * Check if training is currently in progress.
     */
    public boolean isTrainingInProgress() {
        return trainingInProgress.get();
    }

    /**
     * Get the last training report.
     */
    public String getLastTrainingReport() {
        return lastTrainingReport.get();
    }

    /**
     * Get last training attempt time.
     */
    public LocalDateTime getLastTrainingAttempt() {
        return lastTrainingAttempt.get();
    }

    /**
     * Manually trigger model retraining (for admin use).
     */
    public CompletableFuture<String> manualRetrain() {
        log.info("Manual model retrain triggered");
        pollingService.markRetrainTriggered("Manual trigger by admin");
        return triggerAsyncTraining();
    }
}

