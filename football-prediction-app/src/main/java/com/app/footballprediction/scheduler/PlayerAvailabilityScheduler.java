package com.app.footballprediction.scheduler;

import com.app.footballprediction.service.PlayerAvailabilityApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Scheduled job that syncs player injury/suspension data daily.
 *
 * <p>Runs at 10:00 AM by default (configurable) and fetches the latest
 * squad availability data for all Premier League teams from football-data.org.</p>
 *
 * <p>Also runs once at startup (with 90-second delay) to ensure data is
 * populated if the app was offline.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlayerAvailabilityScheduler {

    private final PlayerAvailabilityApiService playerAvailabilityApiService;

    @Value("${player.availability.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    /**
     * Sync player availability every morning at 10:00 AM.
     * This runs after the daily match sync (6 AM) and prediction generation (7 AM).
     */
    @Scheduled(cron = "${player.availability.sync.cron:0 0 10 * * *}")
    public void syncPlayerAvailability() {
        if (!schedulerEnabled) {
            log.debug("Player availability scheduler disabled, skipping");
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        log.info("═══════════════════════════════════════════════════════════════");
        log.info(" PLAYER AVAILABILITY SYNC — {}", timestamp);
        log.info("═══════════════════════════════════════════════════════════════");

        try {
            playerAvailabilityApiService.syncAllTeams();
            log.info("Player availability sync completed successfully");
        } catch (Exception e) {
            log.error("Player availability sync FAILED: {}", e.getMessage(), e);
        }
    }

    /**
     * Run sync at startup with a 90-second delay.
     * This populates data immediately if the app was just started.
     */
    @Scheduled(initialDelay = 90_000, fixedDelay = Long.MAX_VALUE)
    public void syncOnStartup() {
        if (!schedulerEnabled) {
            log.debug("Player availability scheduler disabled, skipping startup sync");
            return;
        }

        log.info("Running startup player availability sync...");
        try {
            playerAvailabilityApiService.syncAllTeams();
            log.info("Startup player availability sync completed");
        } catch (Exception e) {
            log.error("Startup player availability sync failed: {}", e.getMessage(), e);
        }
    }
}

