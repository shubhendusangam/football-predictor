package com.app.footballprediction.controller;

import com.app.footballprediction.dto.PredictRequest;
import com.app.footballprediction.dto.PredictResponse;
import com.app.footballprediction.dto.H2HInsightsResponse;
import com.app.footballprediction.dto.TrendingInsightsResponse;
import com.app.footballprediction.service.FootballDataApiService;
import com.app.footballprediction.service.H2HInsightsService;
import com.app.footballprediction.service.PredictionOrchestrationService;
import com.app.footballprediction.service.PredictionTrackingService;
import com.app.footballprediction.service.TeamValidationService;
import com.app.footballprediction.service.TrendingInsightsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST controller for match predictions, H2H insights, and trending insights.
 *
 * <p>Model training, cache management, data lifecycle, match history, and dashboard
 * endpoints have been extracted into dedicated controllers:
 * {@link ModelTrainingController}, {@link CacheManagementController},
 * {@link DataManagementController}, {@link MatchHistoryController},
 * {@link DashboardController}.</p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class PredictionController {

    private final PredictionOrchestrationService predictionOrchestrationService;
    private final TeamValidationService teamValidationService;
    private final H2HInsightsService h2hInsightsService;
    private final TrendingInsightsService trendingInsightsService;
    private final PredictionTrackingService predictionTrackingService;
    private final FootballDataApiService footballDataApiService;

    // ── Prediction ────────────────────────────────────────────────────────

    /**
     * Predict match outcome.
     * POST /api/predict
     */
    @PostMapping("/predict")
    public ResponseEntity<?> predict(@RequestBody PredictRequest request) {

        if (request.getHomeTeam() == null || request.getHomeTeam().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "homeTeam is required",
                    "hint", "Use GET /api/teams to see valid team names"
            ));
        }
        if (request.getAwayTeam() == null || request.getAwayTeam().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "awayTeam is required",
                    "hint", "Use GET /api/teams to see valid team names"
            ));
        }
        if (request.getHomeTeam().equalsIgnoreCase(request.getAwayTeam())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "homeTeam and awayTeam cannot be the same"
            ));
        }

        // Validate and normalize team names
        TeamValidationService.ValidationResult homeValidation =
                teamValidationService.validateTeam(request.getHomeTeam());
        if (!homeValidation.isValid()) {
            return ResponseEntity.badRequest().body(homeValidation.toErrorResponse());
        }

        TeamValidationService.ValidationResult awayValidation =
                teamValidationService.validateTeam(request.getAwayTeam());
        if (!awayValidation.isValid()) {
            return ResponseEntity.badRequest().body(awayValidation.toErrorResponse());
        }

        String homeTeam = homeValidation.getNormalizedName();
        String awayTeam = awayValidation.getNormalizedName();

        if (!homeTeam.equals(request.getHomeTeam()) || !awayTeam.equals(request.getAwayTeam())) {
            log.info("Normalized team names: '{}' -> '{}', '{}' -> '{}'",
                    request.getHomeTeam(), homeTeam, request.getAwayTeam(), awayTeam);
        }

        PredictResponse response = predictionOrchestrationService.predict(homeTeam, awayTeam);
        return ResponseEntity.ok(response);
    }

    // ── Head-to-Head Insights ──────────────────────────────────────────────

    /**
     * Get enhanced H2H insights between two teams.
     * GET /api/h2h?homeTeam=Arsenal&awayTeam=Chelsea
     */
    @GetMapping("/h2h")
    public ResponseEntity<?> getH2HInsights(
            @RequestParam String homeTeam,
            @RequestParam String awayTeam) {

        if (homeTeam == null || homeTeam.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "homeTeam is required",
                    "hint", "Use GET /api/teams to see valid team names"
            ));
        }
        if (awayTeam == null || awayTeam.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "awayTeam is required",
                    "hint", "Use GET /api/teams to see valid team names"
            ));
        }
        if (homeTeam.equalsIgnoreCase(awayTeam)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "homeTeam and awayTeam cannot be the same"
            ));
        }

        log.info("Fetching H2H insights: {} vs {}", homeTeam, awayTeam);
        H2HInsightsResponse insights = h2hInsightsService.getH2HInsights(homeTeam, awayTeam);
        return ResponseEntity.ok(insights);
    }

    // ── Trending Insights ─────────────────────────────────────────────────

    /**
     * Get live/trending insights across all teams for a specific season.
     * GET /api/insights/trending
     * GET /api/insights/trending?season=2024-25
     */
    @GetMapping("/insights/trending")
    public ResponseEntity<TrendingInsightsResponse> getTrendingInsights(
            @RequestParam(required = false) String season) {

        TrendingInsightsResponse insights;
        if (season != null && !season.isBlank()) {
            log.info("Fetching trending insights for season: {}", season);
            insights = trendingInsightsService.getTrendingInsightsBySeason(season);
        } else {
            log.info("Fetching trending insights for current season...");
            insights = trendingInsightsService.getTrendingInsights();
        }
        return ResponseEntity.ok(insights);
    }

    /**
     * Get list of available seasons for insights.
     * GET /api/insights/seasons
     */
    @GetMapping("/insights/seasons")
    public ResponseEntity<Map<String, Object>> getAvailableSeasons() {
        log.info("Fetching available seasons for insights...");
        var seasons = trendingInsightsService.getAvailableSeasons();
        String currentSeason = trendingInsightsService.getCurrentSeason();
        return ResponseEntity.ok(Map.of(
                "seasons", seasons,
                "currentSeason", currentSeason != null ? currentSeason : ""
        ));
    }

    // ── Predictions Listing ───────────────────────────────────────────────

    /**
     * Update unresolved predictions with actual results.
     * POST /api/predictions/update-results
     */
    @PostMapping("/predictions/update-results")
    public ResponseEntity<Map<String, Object>> updatePredictionResults() {
        log.info("Manual prediction results update triggered via API");
        predictionTrackingService.updateUnresolvedPredictions();
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Prediction results updated successfully"
        ));
    }

    /**
     * Get all predictions.
     * GET /api/predictions
     */
    @GetMapping("/predictions")
    public ResponseEntity<Map<String, Object>> getAllPredictions() {
        var upcomingMatches = footballDataApiService.getScheduledMatches("PL");
        return ResponseEntity.ok(Map.of(
                "predictions", upcomingMatches.getMatches() != null ?
                        upcomingMatches.getMatches().stream().limit(10).toList() :
                        List.of(),
                "count", upcomingMatches.getMatches() != null ?
                        Math.min(upcomingMatches.getMatches().size(), 10) : 0,
                "source", "upcoming_matches",
                "hint", "Use POST /api/predict to make custom predictions"
        ));
    }

    /**
     * Get today's predictions from upcoming matches.
     * GET /api/predictions/today
     */
    @GetMapping("/predictions/today")
    public ResponseEntity<Map<String, Object>> getTodaysPredictions() {
        var upcomingMatches = footballDataApiService.getScheduledMatches("PL");
        String today = LocalDate.now().toString();

        var todaysMatches = upcomingMatches.getMatches() != null ?
                upcomingMatches.getMatches().stream()
                        .filter(m -> m.getUtcDate() != null && m.getUtcDate().startsWith(today))
                        .toList() :
                List.of();

        return ResponseEntity.ok(Map.of(
                "date", today,
                "predictions", todaysMatches,
                "count", todaysMatches.size(),
                "hint", "Use GET /api/external/predict for ML predictions"
        ));
    }
}
