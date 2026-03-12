package com.app.footballprediction.controller;

import com.app.common.service.FeatureEngineeringService;
import com.app.footballprediction.dto.LeagueStandingsResponse;
import com.app.footballprediction.dto.dashboard.ModelAccuracyResponse;
import com.app.footballprediction.dto.dashboard.TodaysPredictionsResponse;
import com.app.footballprediction.dto.dashboard.TopTeamsResponse;
import com.app.footballprediction.dto.dashboard.UpcomingMatchesResponse;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import com.app.footballprediction.service.CsvIngestionService;
import com.app.footballprediction.service.DashboardService;
import com.app.footballprediction.service.LeagueStandingService;
import com.app.footballprediction.service.ModelAccuracyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for dashboard-specific endpoints.
 * All endpoints are optimized for &lt;300ms response times.
 *
 * <p>Includes endpoints previously hosted in PredictionController:
 * stats, accuracy, activity, available-leagues, available-seasons, refresh.</p>
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;
    private final LeagueStandingService leagueStandingService;
    private final ModelTrainingService modelTrainingService;
    private final FeatureEngineeringService featureEngineeringService;
    private final CsvIngestionService csvIngestionService;
    private final ModelAccuracyService modelAccuracyService;

    /**
     * Get upcoming matches for dashboard.
     * GET /api/dashboard/upcoming-matches
     */
    @GetMapping("/upcoming-matches")
    public ResponseEntity<UpcomingMatchesResponse> getUpcomingMatches() {
        log.debug("GET /api/dashboard/upcoming-matches");
        long startTime = System.currentTimeMillis();

        UpcomingMatchesResponse response = dashboardService.getUpcomingMatches();

        log.debug("Upcoming matches response time: {}ms", System.currentTimeMillis() - startTime);
        return ResponseEntity.ok(response);
    }

    /**
     * Get league standings for dashboard.
     * GET /api/dashboard/league-standings?leagueId=1&season=2025/26
     */
    @GetMapping("/league-standings")
    public ResponseEntity<LeagueStandingsResponse> getLeagueStandings(
            @RequestParam(required = false, defaultValue = "1") Long leagueId,
            @RequestParam(required = false) String season) {
        log.debug("GET /api/dashboard/league-standings leagueId={} season={}", leagueId, season);
        long startTime = System.currentTimeMillis();

        LeagueStandingsResponse response;
        if (season != null && !season.isEmpty()) {
            response = leagueStandingService.getLeagueTableForSeason(leagueId, season);
        } else {
            response = leagueStandingService.getCurrentLeagueTable(leagueId);
        }

        log.debug("League standings response time: {}ms", System.currentTimeMillis() - startTime);
        return ResponseEntity.ok(response);
    }

    /**
     * Get today's predictions for dashboard.
     * GET /api/dashboard/todays-predictions
     */
    @GetMapping("/todays-predictions")
    public ResponseEntity<TodaysPredictionsResponse> getTodaysPredictions() {
        log.debug("GET /api/dashboard/todays-predictions");
        long startTime = System.currentTimeMillis();

        TodaysPredictionsResponse response = dashboardService.getTodaysPredictions();

        log.debug("Today's predictions response time: {}ms", System.currentTimeMillis() - startTime);
        return ResponseEntity.ok(response);
    }

    /**
     * Get top teams for dashboard.
     * GET /api/dashboard/top-teams
     */
    @GetMapping("/top-teams")
    public ResponseEntity<TopTeamsResponse> getTopTeams() {
        log.debug("GET /api/dashboard/top-teams");
        long startTime = System.currentTimeMillis();

        TopTeamsResponse response = dashboardService.getTopTeams();

        log.debug("Top teams response time: {}ms", System.currentTimeMillis() - startTime);
        return ResponseEntity.ok(response);
    }

    /**
     * Get model accuracy stats for dashboard.
     * GET /api/dashboard/model-accuracy
     */
    @GetMapping("/model-accuracy")
    public ResponseEntity<ModelAccuracyResponse> getModelAccuracy() {
        log.debug("GET /api/dashboard/model-accuracy");
        long startTime = System.currentTimeMillis();

        ModelAccuracyResponse response = dashboardService.getModelAccuracy();

        log.debug("Model accuracy response time: {}ms", System.currentTimeMillis() - startTime);
        return ResponseEntity.ok(response);
    }

    // ── Endpoints migrated from PredictionController ──────────────────────

    /**
     * Get dashboard statistics overview.
     * GET /api/dashboard/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        long totalMatches = csvIngestionService.getMatchCount();
        int totalTeams = featureEngineeringService.getAllTeams().size();
        boolean modelLoaded = modelTrainingService.isModelLoaded();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMatches", totalMatches);
        stats.put("totalTeams", totalTeams);
        stats.put("modelLoaded", modelLoaded);
        stats.put("totalFeatures", 25);
        stats.put("modelType", "Stacked Ensemble (RF + GB + LR)");
        stats.put("lastUpdated", modelTrainingService.getModelLastUpdated());
        return ResponseEntity.ok(stats);
    }

    /**
     * Get model accuracy metrics for dashboard.
     * Uses real data from ModelAccuracyService when available, falls back to defaults.
     * GET /api/dashboard/accuracy
     */
    @GetMapping("/accuracy")
    public ResponseEntity<Map<String, Object>> getDashboardAccuracy() {
        boolean modelLoaded = modelTrainingService.isModelLoaded();
        if (!modelLoaded) {
            return ResponseEntity.ok(Map.of(
                    "modelLoaded", false,
                    "overall", 0.0,
                    "totalPredictions", 0,
                    "correctPredictions", 0,
                    "message", "Model not loaded yet"
            ));
        }

        // Attempt to use real accuracy data
        var globalAccuracy = modelAccuracyService.recalculateGlobalAccuracy();

        Map<String, Object> accuracy = new HashMap<>();
        accuracy.put("modelLoaded", true);

        if (globalAccuracy != null) {
            accuracy.put("overall", Math.round(globalAccuracy.getWinnerAccuracy() * 1000.0) / 10.0);
            accuracy.put("totalPredictions", globalAccuracy.getTotalPredictions());
            accuracy.put("correctPredictions", globalAccuracy.getCorrectWinnerPredictions());
            accuracy.put("f1Score", Math.round(globalAccuracy.getWinnerAccuracy() * 100.0) / 100.0);
        } else {
            long totalMatches = csvIngestionService.getMatchCount();
            accuracy.put("overall", 0.0);
            accuracy.put("totalPredictions", totalMatches);
            accuracy.put("correctPredictions", 0);
            accuracy.put("f1Score", 0.0);
            accuracy.put("message", "No evaluated predictions yet — accuracy will populate as predictions are resolved");
        }

        return ResponseEntity.ok(accuracy);
    }

    /**
     * Get recent activity/predictions.
     * GET /api/dashboard/activity
     */
    @GetMapping("/activity")
    public ResponseEntity<Map<String, Object>> getRecentActivity() {
        return ResponseEntity.ok(Map.of(
                "activities", List.of(),
                "message", "No recent activity tracked yet",
                "hint", "Prediction history tracking coming soon"
        ));
    }

    /**
     * Get list of available leagues for the standings dropdown.
     * GET /api/dashboard/available-leagues
     */
    @GetMapping("/available-leagues")
    public ResponseEntity<Map<String, Object>> getAvailableLeagues() {
        var leagues = leagueStandingService.getAvailableLeagues();
        return ResponseEntity.ok(Map.of(
                "leagues", leagues,
                "count", leagues.size()
        ));
    }

    /**
     * Get available seasons for a league.
     * GET /api/dashboard/available-seasons?leagueId=1
     */
    @GetMapping("/available-seasons")
    public ResponseEntity<Map<String, Object>> getAvailableSeasons(
            @RequestParam(required = false) Long leagueId) {
        Long effectiveLeagueId = leagueId != null ? leagueId : leagueStandingService.getDefaultLeagueId();
        var seasons = leagueStandingService.getAvailableSeasons(effectiveLeagueId);
        return ResponseEntity.ok(Map.of(
                "leagueId", effectiveLeagueId,
                "seasons", seasons,
                "count", seasons.size()
        ));
    }

    /**
     * Refresh league standings (recalculate from matches).
     * POST /api/dashboard/league-standings/refresh?leagueId=1
     */
    @PostMapping("/league-standings/refresh")
    public ResponseEntity<Map<String, Object>> refreshLeagueStandings(
            @RequestParam(required = false) Long leagueId) {
        Long effectiveLeagueId = leagueId != null ? leagueId : leagueStandingService.getDefaultLeagueId();
        leagueStandingService.refreshStandings(effectiveLeagueId);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "League standings refreshed successfully",
                "leagueId", effectiveLeagueId
        ));
    }
}

