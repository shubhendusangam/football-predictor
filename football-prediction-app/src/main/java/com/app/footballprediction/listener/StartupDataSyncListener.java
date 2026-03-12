package com.app.footballprediction.listener;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.SeasonTeamStatsRepository;
import com.app.footballprediction.service.ApiDataSyncService;
import com.app.footballprediction.service.CsvIngestionService;
import com.app.footballprediction.service.MatchCompletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Startup listener that automatically checks data freshness and triggers sync if needed.
 *
 * This ensures the application always has up-to-date data when it starts,
 * even if it was offline for several days.
 *
 * Uses ApplicationReadyEvent instead of @PostConstruct to ensure all beans are fully initialized.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StartupDataSyncListener {

    private final MatchRepository matchRepository;
    private final ApiDataSyncService apiDataSyncService;
    private final CsvIngestionService csvIngestionService;
    private final MatchCompletionService matchCompletionService;
    private final SeasonTeamStatsRepository seasonTeamStatsRepository;

    @Value("${startup.sync.enabled:true}")
    private boolean startupSyncEnabled;

    @Value("${startup.sync.threshold-days:1}")
    private int thresholdDays;

    @Value("${startup.sync.competition:PL}")
    private String competition;

    @Value("${startup.sync.mode:smart}")
    private String syncMode;

    /**
     * Runs after the application is fully started and all beans are ready.
     * Checks data freshness and triggers sync if data is stale.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!startupSyncEnabled) {
            log.info("ℹ️ Startup sync is DISABLED in configuration");
            return;
        }

        try {
            logStartupBanner();

            // Check data freshness
            DataFreshnessStatus status = checkDataFreshness();

            // Log data status
            logDataStatus(status);

            // Perform sync if needed
            if (status.syncNeeded) {
                log.warn("⚠️ Data is stale. Starting automatic sync...");
                performStartupSync();
            } else {
                log.info("✅ Data is fresh. No sync needed.");
            }

            // Process season stats for any unprocessed matches (from API sync)
            processUncomputedSeasonStats();

            logCompletionBanner();

        } catch (Exception e) {
            log.error("❌ Startup data check failed: {}", e.getMessage(), e);
            log.warn("⚠️ Application will continue with existing data");
            log.info("💡 Try manual sync: POST /api/admin/sync/all?competition={}", competition);
        }
    }

    /**
     * Log startup banner with configuration details.
     */
    private void logStartupBanner() {
        log.info("🚀 Checking data freshness (threshold: {} days, mode: {}, competition: {})",
                thresholdDays, syncMode, competition);
    }

    /**
     * Log completion banner.
     */
    private void logCompletionBanner() {
        log.debug("🏁 Startup data check completed");
    }

    /**
     * Check if the database data is fresh or stale.
     *
     * @return DataFreshnessStatus with details about data state
     */
    private DataFreshnessStatus checkDataFreshness() {
        DataFreshnessStatus status = new DataFreshnessStatus();

        // Query latest match from database
        List<Match> latestMatches = matchRepository.findAll(
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "matchDate"))
        ).getContent();

        if (latestMatches.isEmpty()) {
            // No matches in database - initial setup
            status.latestMatchDate = null;
            status.daysSinceLastMatch = Integer.MAX_VALUE;
            status.totalMatches = 0;
            status.syncNeeded = true;
            status.reason = "No matches in database (initial setup)";
            return status;
        }

        // Get latest match details
        Match latestMatch = latestMatches.get(0);
        LocalDate latestDate = latestMatch.getMatchDate();
        LocalDate today = LocalDate.now();

        status.latestMatchDate = latestDate;
        status.totalMatches = matchRepository.count();

        // Calculate days since last match
        long daysBetween = ChronoUnit.DAYS.between(latestDate, today);
        status.daysSinceLastMatch = (int) daysBetween;

        // Determine if sync is needed
        if (daysBetween > thresholdDays) {
            status.syncNeeded = true;
            status.reason = String.format("Latest match is %d days old (threshold: %d days)",
                    daysBetween, thresholdDays);
        } else if (daysBetween < 0) {
            // Latest match is in the future (scheduled fixture)
            status.syncNeeded = false;
            status.reason = "Latest match is a future fixture - data is current";
        } else {
            status.syncNeeded = false;
            status.reason = "Data is up to date";
        }

        return status;
    }

    /**
     * Log data freshness status.
     */
    private void logDataStatus(DataFreshnessStatus status) {
        log.info("📊 Data status: latest={}, age={} days, total={}, syncNeeded={} ({})",
                status.latestMatchDate != null ? status.latestMatchDate : "N/A",
                status.daysSinceLastMatch == Integer.MAX_VALUE ? "N/A" : status.daysSinceLastMatch,
                status.totalMatches,
                status.syncNeeded,
                status.reason);
    }

    /**
     * Perform the startup sync based on configured mode.
     */
    private void performStartupSync() {
        long startTime = System.currentTimeMillis();

        try {
            log.info("🔄 Starting {} synchronization...", syncMode);

            if ("smart".equalsIgnoreCase(syncMode)) {
                apiDataSyncService.smartSync(competition);
            } else {
                apiDataSyncService.syncAll(competition);
            }

            // Normalize season data to ensure consistent format (YYYY-YY with dash)
            log.debug("🔧 Normalizing season data format...");
            java.util.Map<String, Integer> normResult = apiDataSyncService.normalizeAllSeasonData();
            log.debug("   Normalized: {} matches, {} standings, {} forms calculated",
                    normResult.get("matchesNormalized"),
                    normResult.get("standingsNormalized"),
                    normResult.get("formsCalculated"));

            // Enrich API-synced matches with detailed statistics from CSV
            // The external API only returns scores, not stats (shots, corners, cards, fouls)
            try {
                int enriched = csvIngestionService.enrichMissingStats();
                if (enriched > 0) {
                    log.debug("📊 Enriched {} matches with detailed statistics from CSV", enriched);
                }
            } catch (Exception e2) {
                log.warn("⚠️ Stats enrichment from CSV failed (non-fatal): {}", e2.getMessage());
            }

            long duration = System.currentTimeMillis() - startTime;

            // Re-check data status after sync
            DataFreshnessStatus newStatus = checkDataFreshness();

            log.info("✅ Startup sync completed in {}s — latest match: {}, total: {}",
                    duration / 1000,
                    newStatus.latestMatchDate != null ? newStatus.latestMatchDate : "N/A",
                    newStatus.totalMatches);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("❌ Startup sync FAILED after {}ms: {}", duration, e.getMessage(), e);
            log.warn("⚠️ Application will continue with stale data");
            log.info("💡 Try manual sync: POST /api/admin/sync/all?competition={}", competition);
        }
    }

    /**
     * Process season stats for completed matches that haven't been processed yet.
     * <p>
     * Matches inserted via API polling lack season team stats (Elo, form, streaks).
     * This finds affected seasons and recalculates stats for them.
     * Uses incremental processing: only recalculates seasons that have unprocessed matches.
     */
    private void processUncomputedSeasonStats() {
        try {
            // Find completed matches that haven't had their stats processed
            List<Match> unprocessed = matchRepository.findAllByOrderByMatchDateAsc().stream()
                    .filter(m -> m.getFullTimeResult() != null)
                    .filter(m -> !Boolean.TRUE.equals(m.getStatsProcessed()))
                    .toList();

            if (unprocessed.isEmpty()) {
                log.debug("All completed matches already have season stats computed.");
                return;
            }

            // Get distinct seasons affected
            List<String> affectedSeasons = unprocessed.stream()
                    .map(Match::getSeason)
                    .distinct()
                    .sorted()
                    .toList();

            log.info("📊 Computing season stats for {} unprocessed matches across {} seasons...",
                    unprocessed.size(), affectedSeasons.size());
            long start = System.currentTimeMillis();

            int processed = 0;
            for (String season : affectedSeasons) {
                try {
                    matchCompletionService.recalculateSeasonStats(season);
                    processed++;
                    log.debug("   Processed season: {}", season);
                } catch (Exception e) {
                    log.error("Failed to compute stats for season {}: {}", season, e.getMessage());
                }
            }

            long duration = System.currentTimeMillis() - start;
            long totalStats = seasonTeamStatsRepository.count();
            log.info("✅ Season stats updated: {} records across {} seasons in {}ms",
                    totalStats, processed, duration);

        } catch (Exception e) {
            log.warn("⚠️ Season stats computation failed (non-fatal): {}", e.getMessage());
        }
    }

    /**
     * Inner class to hold data freshness status.
     */
    private static class DataFreshnessStatus {
        LocalDate latestMatchDate;
        int daysSinceLastMatch;
        long totalMatches;
        boolean syncNeeded;
        String reason;
    }
}

