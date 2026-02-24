package com.app.footballprediction.polling.controller;

import com.app.common.model.Match;
import com.app.common.model.SystemSettings;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.SystemSettingsRepository;
import com.app.footballprediction.polling.dto.MatchDayStatus;
import com.app.footballprediction.polling.dto.SystemStatusResponse;
import com.app.footballprediction.polling.model.PollingResult;
import com.app.footballprediction.polling.model.SyncStatus;
import com.app.footballprediction.polling.scheduler.DailyMatchPollingJob;
import com.app.footballprediction.polling.service.MatchDayService;
import com.app.footballprediction.polling.service.MatchPollingService;
import com.app.footballprediction.polling.service.SmartRetrainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Admin controller for sync status and manual polling operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /admin/sync-status - Get current sync status</li>
 *   <li>GET /admin/system-status - Get comprehensive system status for UI</li>
 *   <li>GET /admin/match-day-status - Get match day status for smart refresh</li>
 *   <li>POST /admin/poll/trigger - Manually trigger polling</li>
 *   <li>POST /admin/retrain/trigger - Manually trigger model retraining</li>
 * </ul>
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class SyncStatusController {

    private final MatchPollingService pollingService;
    private final DailyMatchPollingJob pollingJob;
    private final SmartRetrainService retrainService;
    private final MatchRepository matchRepository;
    private final SystemSettingsRepository systemSettingsRepository;
    private final MatchDayService matchDayService;

    /**
     * Get current sync status.
     *
     * <p>Returns:
     * <ul>
     *   <li>lastSyncTime - When last sync occurred</li>
     *   <li>matchesInsertedToday - Matches inserted today</li>
     *   <li>matchesUpdatedToday - Matches updated today</li>
     *   <li>lastModelTrainingTime - When model was last trained</li>
     *   <li>modelVersion - Current model version</li>
     *   <li>retrainTriggeredToday - Whether retrain happened today</li>
     * </ul>
     */
    @GetMapping("/sync-status")
    public ResponseEntity<SyncStatus> getSyncStatus() {
        log.debug("Getting sync status");
        SyncStatus status = pollingService.getSyncStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Get match day status for smart dashboard refresh.
     *
     * <p>Returns:
     * <ul>
     *   <li>isMatchDay - Whether today has matches</li>
     *   <li>totalMatchesToday - Total matches scheduled</li>
     *   <li>completedMatchesToday - Completed match count</li>
     *   <li>allMatchesCompleted - Whether all done</li>
     *   <li>lastMatchCompletionTimestamp - Last completion time</li>
     * </ul>
     *
     * <p>Frontend should use SSE (/api/events/match-completion) for real-time
     * notifications instead of polling this endpoint.
     */
    @GetMapping("/match-day-status")
    public ResponseEntity<MatchDayStatus> getMatchDayStatus() {
        log.debug("Getting match day status");
        MatchDayStatus status = matchDayService.getMatchDayStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Get comprehensive system status for UI dashboard.
     * This is the main endpoint used by the frontend status panel.
     */
    @GetMapping("/system-status")
    public ResponseEntity<SystemStatusResponse> getSystemStatus() {
        log.debug("Getting system status for UI dashboard");

        SyncStatus syncStatus = pollingService.getSyncStatus();
        SystemSettings settings = systemSettingsRepository.getSettings()
            .orElse(SystemSettings.builder().build());

        // Determine sync status string
        String syncStatusStr;
        if (retrainService.isTrainingInProgress()) {
            syncStatusStr = "IN_PROGRESS";
        } else if (syncStatus.isLastSyncSuccessful()) {
            syncStatusStr = syncStatus.getLastSyncTime() != null ? "SUCCESS" : "PENDING";
        } else {
            syncStatusStr = "FAILED";
        }

        // Get latest completed match info
        String latestMatchDate = null;
        int daysSinceLastMatch = 0;
        List<Match> recentMatches = matchRepository.findAllByOrderByMatchDateDesc();
        if (!recentMatches.isEmpty()) {
            Match latest = recentMatches.stream()
                .filter(m -> m.getFullTimeResult() != null)
                .findFirst()
                .orElse(null);
            if (latest != null && latest.getMatchDate() != null) {
                latestMatchDate = latest.getMatchDate().toString();
                daysSinceLastMatch = (int) ChronoUnit.DAYS.between(latest.getMatchDate(), LocalDate.now());
            }
        }

        SystemStatusResponse response = SystemStatusResponse.builder()
            // Sync Status
            .lastSyncTime(syncStatus.getLastSyncTime())
            .matchesFetchedToday(syncStatus.getRecordsProcessed())
            .matchesInsertedToday(syncStatus.getMatchesInsertedToday())
            .matchesUpdatedToday(syncStatus.getMatchesUpdatedToday())
            .syncStatus(syncStatusStr)
            .syncErrorMessage(syncStatus.getErrorMessage())
            // Model Status
            .lastModelTrainingTime(syncStatus.getLastModelTrainingTime())
            .modelVersion(syncStatus.getModelVersion())
            .trainingRunning(retrainService.isTrainingInProgress())
            .retrainTriggeredToday(syncStatus.isRetrainTriggeredToday())
            .retrainDecisionReason(syncStatus.getRetrainDecisionReason())
            .lastTrainingDurationMs(syncStatus.getLastPollDurationMs())
            // Data Freshness
            .latestCompletedMatchDate(latestMatchDate)
            .daysSinceLastMatch(daysSinceLastMatch)
            // System Health
            .pollingEnabled(syncStatus.isPollingEnabled())
            .autoRetrainEnabled(settings.getAutoRetrainEnabled() != null ? settings.getAutoRetrainEnabled() : true)
            .newMatchesSinceLastTraining(syncStatus.getNewMatchesSinceLastTraining())
            .build();

        // Add match day status
        MatchDayStatus matchDayStatus = matchDayService.getMatchDayStatus();
        response.setMatchDay(matchDayStatus.isMatchDay());
        response.setTotalMatchesToday(matchDayStatus.getTotalMatchesToday());
        response.setCompletedMatchesToday(matchDayStatus.getCompletedMatchesToday());
        response.setAllMatchesCompleted(matchDayStatus.isAllMatchesCompleted());
        response.setLastMatchCompletionTimestamp(matchDayStatus.getLastMatchCompletionTimestamp());

        return ResponseEntity.ok(response);
    }

    /**
     * Get detailed sync status as a map (for dashboard widgets).
     */
    @GetMapping("/sync-status/detailed")
    public ResponseEntity<Map<String, Object>> getDetailedSyncStatus() {
        SyncStatus status = pollingService.getSyncStatus();

        Map<String, Object> detailed = new HashMap<>();
        detailed.put("lastSyncTime", status.getLastSyncTime());
        detailed.put("matchesInsertedToday", status.getMatchesInsertedToday());
        detailed.put("matchesUpdatedToday", status.getMatchesUpdatedToday());
        detailed.put("totalChangesToday", status.getMatchesInsertedToday() + status.getMatchesUpdatedToday());
        detailed.put("lastModelTrainingTime", status.getLastModelTrainingTime());
        detailed.put("modelVersion", status.getModelVersion());
        detailed.put("retrainTriggeredToday", status.isRetrainTriggeredToday());
        detailed.put("retrainDecisionReason", status.getRetrainDecisionReason());
        detailed.put("lastPollDurationMs", status.getLastPollDurationMs());
        detailed.put("recordsProcessed", status.getRecordsProcessed());
        detailed.put("lastSyncSuccessful", status.isLastSyncSuccessful());
        detailed.put("errorMessage", status.getErrorMessage());
        detailed.put("newMatchesSinceLastTraining", status.getNewMatchesSinceLastTraining());
        detailed.put("pollingEnabled", status.isPollingEnabled());
        detailed.put("trainingInProgress", retrainService.isTrainingInProgress());

        return ResponseEntity.ok(detailed);
    }

    /**
     * Manually trigger polling job.
     */
    @PostMapping("/poll/trigger")
    public ResponseEntity<PollingResult> triggerPolling() {
        log.info("Manual polling triggered via admin API");
        PollingResult result = pollingJob.triggerManualPolling();
        return ResponseEntity.ok(result);
    }

    /**
     * Manually trigger polling with retrain evaluation.
     */
    @PostMapping("/poll/trigger-with-retrain")
    public ResponseEntity<PollingResult> triggerPollingWithRetrain() {
        log.info("Manual polling with retrain evaluation triggered via admin API");
        PollingResult result = pollingJob.triggerManualPollAndRetrain();
        return ResponseEntity.ok(result);
    }

    /**
     * Manually trigger model retraining.
     */
    @PostMapping("/retrain/trigger")
    public ResponseEntity<Map<String, Object>> triggerRetrain() {
        log.info("Manual model retrain triggered via admin API");

        if (retrainService.isTrainingInProgress()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Training already in progress"
            ));
        }

        CompletableFuture<String> future = retrainService.manualRetrain();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Model retraining started (running asynchronously)");
        response.put("trainingInProgress", true);
        response.put("startedAt", retrainService.getLastTrainingAttempt());

        return ResponseEntity.accepted().body(response);
    }

    /**
     * Get training status.
     */
    @GetMapping("/retrain/status")
    public ResponseEntity<Map<String, Object>> getRetrainStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("trainingInProgress", retrainService.isTrainingInProgress());
        status.put("lastTrainingAttempt", retrainService.getLastTrainingAttempt());
        status.put("lastTrainingReport", retrainService.getLastTrainingReport());

        SyncStatus syncStatus = pollingService.getSyncStatus();
        status.put("lastModelTrainingTime", syncStatus.getLastModelTrainingTime());
        status.put("modelVersion", syncStatus.getModelVersion());
        status.put("newMatchesSinceLastTraining", syncStatus.getNewMatchesSinceLastTraining());

        return ResponseEntity.ok(status);
    }

    /**
     * Check new matches since last training.
     */
    @GetMapping("/retrain/pending-data")
    public ResponseEntity<Map<String, Object>> getPendingRetrainData() {
        int newMatches = pollingService.countNewMatchesSinceLastTraining();
        SyncStatus status = pollingService.getSyncStatus();

        Map<String, Object> response = new HashMap<>();
        response.put("newMatchesSinceLastTraining", newMatches);
        response.put("lastModelTrainingTime", status.getLastModelTrainingTime());
        response.put("retrainRecommended", newMatches >= 5);
        response.put("reason", newMatches >= 5
            ? "Sufficient new data available"
            : "Not enough new data yet");

        return ResponseEntity.ok(response);
    }
}

