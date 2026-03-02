package com.app.footballprediction.scheduler;

import com.app.footballprediction.service.ApiDataSyncService;
import com.app.footballprediction.service.CsvIngestionService;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URI;
import java.nio.file.*;

/**
 * Scheduled tasks for automated data updates and model retraining.
 *
 * - Downloads latest Premier League data weekly (CSV)
 * - Syncs data from football-data.org API hourly
 * - Retrains model after new data ingestion
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataUpdateScheduler {

    private final CsvIngestionService csvIngestionService;
    private final ModelTrainingService modelTrainingService;
    private final ApiDataSyncService apiDataSyncService;

    @Value("${scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${scheduler.auto-retrain:true}")
    private boolean autoRetrain;

    @Value("${scheduler.current-season-url:https://www.football-data.co.uk/mmz4281/2526/E0.csv}")
    private String currentSeasonUrl;

    @Value("${scheduler.current-season-file:data/PL_25_26.csv}")
    private String currentSeasonFile;

    @Value("${scheduler.api-sync.enabled:true}")
    private boolean apiSyncEnabled;

    @Value("${api.sync.competition:PL}")
    private String apiSyncCompetition;

    /**
     * Download latest data every Monday and Friday at 6 AM.
     * Football-data.co.uk updates their CSVs twice weekly.
     */
    @Scheduled(cron = "${scheduler.cron:0 0 6 * * MON,FRI}")
    public void updateCurrentSeasonData() {
        if (!schedulerEnabled) {
            log.debug("Scheduler disabled, skipping data update");
            return;
        }

        log.info("⏰ Scheduled task: Checking for Premier League data updates...");
        log.debug("Using CSV URL: {}", currentSeasonUrl);
        log.debug("Target file: {}", currentSeasonFile);

        try {
            // Download latest CSV
            boolean downloaded = downloadLatestCsv();

            if (downloaded) {
                // Re-ingest all CSV files (will skip duplicates)
                log.info("📥 Re-ingesting CSV data...");
                csvIngestionService.ingestAll();

                // Retrain model with new data
                if (autoRetrain) {
                    log.info("🧠 Retraining model with updated data...");
                    String report = modelTrainingService.trainAndEvaluate();
                    log.info("✅ Model retrained successfully:\n{}", report);
                }
            }

        } catch (Exception e) {
            log.error("❌ Scheduled data update failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Download the latest current season CSV from football-data.co.uk
     * @return true if new data was downloaded
     */
    private boolean downloadLatestCsv() {
        try {
            // Resolve path relative to classpath resources
            Path targetPath = Paths.get("src/main/resources/" + currentSeasonFile);

            // Get existing file size (if exists)
            long existingSize = Files.exists(targetPath) ? Files.size(targetPath) : 0;

            // Download to temp file first
            Path tempFile = Files.createTempFile("pl_current_", ".csv");

            log.info("📡 Downloading from: {}", currentSeasonUrl);

            try (InputStream in = URI.create(currentSeasonUrl).toURL().openStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            long newSize = Files.size(tempFile);

            // Check if file has grown (new matches added)
            if (newSize > existingSize) {
                // Create parent directories if needed
                Files.createDirectories(targetPath.getParent());

                // Move temp file to target
                Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);

                log.info("✅ Downloaded new data: {} bytes → {} bytes ({} bytes new)",
                        existingSize, newSize, newSize - existingSize);
                return true;

            } else {
                // No new data
                Files.deleteIfExists(tempFile);
                log.info("ℹ️ No new data available (file size unchanged: {} bytes)", existingSize);
                return false;
            }

        } catch (IOException e) {
            log.error("❌ Failed to download current season data", e);
            return false;
        }
    }

    /**
     * Manual trigger for data update (callable via API or testing)
     */
    public String triggerManualUpdate() {
        try {
            log.info("🔄 Manual data update triggered...");

            boolean downloaded = downloadLatestCsv();

            if (downloaded) {
                csvIngestionService.ingestAll();

                if (autoRetrain) {
                    return modelTrainingService.trainAndEvaluate();
                }
                return "Data updated successfully. Model retrain disabled.";
            }

            return "No new data available.";

        } catch (Exception e) {
            log.error("Manual update failed: {}", e.getMessage(), e);
            return "Update failed: " + e.getMessage();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // API Data Sync Scheduled Tasks
    // ══════════════════════════════════════════════════════════════

    /**
     * Sync data from football-data.org API every hour.
     * This ensures the database has fresh data from the external API.
     */
    @Scheduled(cron = "${scheduler.api-sync.cron:0 0 * * * *}")
    public void syncFromApi() {
        if (!schedulerEnabled || !apiSyncEnabled) {
            log.debug("API sync disabled, skipping");
            return;
        }

        log.info("⏰ Scheduled API sync starting for: {}", apiSyncCompetition);

        try {
            apiDataSyncService.syncAll(apiSyncCompetition);
            log.info("✅ Scheduled API sync completed for: {}", apiSyncCompetition);
        } catch (Exception e) {
            log.error("❌ Scheduled API sync failed for {}: {}", apiSyncCompetition, e.getMessage(), e);
        }
    }

    /**
     * Sync data after typical match times (18:00, 20:00, 22:00 GMT).
     * Most Premier League matches finish around these times.
     */
    @Scheduled(cron = "${scheduler.post-match.cron:0 0 18,20,22 * * *}")
    public void syncAfterMatches() {
        if (!schedulerEnabled || !apiSyncEnabled) {
            log.debug("Post-match sync disabled, skipping");
            return;
        }

        log.info("⏰ Post-match API sync starting for: {}", apiSyncCompetition);

        try {
            apiDataSyncService.syncAll(apiSyncCompetition);
            log.info("✅ Post-match API sync completed for: {}", apiSyncCompetition);
        } catch (Exception e) {
            log.error("❌ Post-match API sync failed for {}: {}", apiSyncCompetition, e.getMessage(), e);
        }
    }

    /**
     * Manual trigger for API data sync.
     */
    public String triggerManualApiSync() {
        try {
            log.info("🔄 Manual API sync triggered for: {}", apiSyncCompetition);
            apiDataSyncService.syncAll(apiSyncCompetition);
            return "API sync completed successfully for: " + apiSyncCompetition;
        } catch (Exception e) {
            log.error("Manual API sync failed: {}", e.getMessage(), e);
            return "API sync failed: " + e.getMessage();
        }
    }
}

