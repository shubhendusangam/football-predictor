package com.app.footballprediction.polling.service;

import com.app.common.model.SystemSettings;
import com.app.common.repository.SystemSettingsRepository;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    @Value("${model.output.path:./data/match_predictor.model}")
    private String modelOutputPath;

    @Value("${model.backup.path:./data/model_backups}")
    private String modelBackupPath;

    private static final int MAX_BACKUPS = 10;

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
            // Backup current model before retraining
            String modelVersion = backupCurrentModel();

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

    // ── Model Versioning & Backup ─────────────────────────────────────────

    /**
     * Backup the current model file before retraining.
     * Creates a timestamped copy in the backup directory.
     *
     * @return Model version string (timestamp-based)
     */
    private String backupCurrentModel() {
        String version = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());

        try {
            File modelFile = new File(modelOutputPath);
            if (!modelFile.exists()) {
                log.debug("No existing model to backup");
                return version;
            }

            File backupDir = new File(modelBackupPath);
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                log.warn("Failed to create backup directory: {}", modelBackupPath);
                return version;
            }

            String backupFileName = String.format("model_%s.model", version);
            Path backupPath = Path.of(modelBackupPath, backupFileName);
            Files.copy(modelFile.toPath(), backupPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("📦 Model backed up: {} → {}", modelFile.getName(), backupPath);

            // Clean old backups (keep last MAX_BACKUPS)
            cleanOldBackups(backupDir);

            return version;

        } catch (IOException e) {
            log.warn("Failed to backup model: {}", e.getMessage());
            return version;
        }
    }

    /**
     * Clean old model backups, keeping only the most recent MAX_BACKUPS.
     */
    private void cleanOldBackups(File backupDir) {
        File[] backups = backupDir.listFiles((dir, name) -> name.endsWith(".model"));
        if (backups == null || backups.length <= MAX_BACKUPS) return;

        // Sort by last modified (oldest first)
        java.util.Arrays.sort(backups, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));

        int toDelete = backups.length - MAX_BACKUPS;
        for (int i = 0; i < toDelete; i++) {
            if (backups[i].delete()) {
                log.debug("Deleted old model backup: {}", backups[i].getName());
            }
        }
    }

    /**
     * Rollback to a specific model backup version.
     *
     * @param version Version string (e.g., "20260309-140530")
     * @return true if rollback succeeded
     */
    public boolean rollbackToVersion(String version) {
        try {
            String backupFileName = String.format("model_%s.model", version);
            Path backupPath = Path.of(modelBackupPath, backupFileName);

            if (!Files.exists(backupPath)) {
                log.error("Backup not found: {}", backupPath);
                return false;
            }

            // Backup current model first
            backupCurrentModel();

            // Restore the specified backup
            Files.copy(backupPath, Path.of(modelOutputPath), StandardCopyOption.REPLACE_EXISTING);

            // Reload model
            modelTrainingService.loadModelFromDisk();

            log.info("✅ Model rolled back to version: {}", version);
            return true;

        } catch (Exception e) {
            log.error("❌ Rollback failed: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * List available model backup versions.
     */
    public java.util.List<String> listBackupVersions() {
        File backupDir = new File(modelBackupPath);
        File[] backups = backupDir.listFiles((dir, name) -> name.endsWith(".model"));
        if (backups == null) return java.util.List.of();

        return java.util.Arrays.stream(backups)
                .sorted((a, b) -> Long.compare(b.lastModified(), a.lastModified()))
                .map(f -> f.getName().replace("model_", "").replace(".model", ""))
                .toList();
    }
}

