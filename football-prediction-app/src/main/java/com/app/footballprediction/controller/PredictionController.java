package com.app.footballprediction.controller;

import com.app.footballprediction.dto.PredictRequest;
import com.app.footballprediction.dto.PredictResponse;
import com.app.footballprediction.dto.ScorePredictionDTO;
import com.app.footballprediction.dto.H2HInsightsResponse;
import com.app.footballprediction.dto.TrendingInsightsResponse;
import com.app.footballprediction.service.FootballDataApiService;
import com.app.footballprediction.service.H2HInsightsService;
import com.app.footballprediction.service.InjuryAdjustmentService;
import com.app.footballprediction.service.InjuryDataService;
import com.app.footballprediction.service.PredictionOrchestrationService;
import com.app.footballprediction.service.PredictionTrackingService;
import com.app.footballprediction.service.ScorePredictionService;
import com.app.footballprediction.service.TeamValidationService;
import com.app.footballprediction.service.TrendingInsightsService;
import com.app.common.dto.MatchInjuryContextDTO;
import com.app.footballprediction.scheduler.DailyPredictionScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST controller for match predictions, H2H insights, and trending insights.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Predictions", description = "Match outcome predictions, H2H insights, and trending analytics")
public class PredictionController {

    private final PredictionOrchestrationService predictionOrchestrationService;
    private final TeamValidationService teamValidationService;
    private final H2HInsightsService h2hInsightsService;
    private final TrendingInsightsService trendingInsightsService;
    private final PredictionTrackingService predictionTrackingService;
    private final FootballDataApiService footballDataApiService;
    private final DailyPredictionScheduler dailyPredictionScheduler;
    private final ScorePredictionService scorePredictionService;
    private final InjuryDataService injuryDataService;
    private final InjuryAdjustmentService injuryAdjustmentService;

    // ── Prediction ────────────────────────────────────────────────────────

    /**
     * Predict match outcome.
     * POST /api/predict
     */
    @Operation(summary = "Predict match outcome",
            description = "Predicts the result of a match between two teams using ML model",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Prediction generated successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid team name or request"),
                    @ApiResponse(responseCode = "503", description = "Model not trained yet")
            })
    @PostMapping("/predict")
    public ResponseEntity<?> predict(
            @RequestBody PredictRequest request,
            @RequestParam(required = false) Long fixtureId) {

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

        // ── Injury adjustment (when fixtureId is provided) ──────
        if (fixtureId != null) {
            try {
                // Use teamId=0 as placeholder — the API resolves by fixtureId
                MatchInjuryContextDTO injuryContext = injuryDataService
                        .getMatchInjuryContext(fixtureId, 0, 0);

                if (injuryContext.isProbabilitiesAdjusted()) {
                    double[] adjusted = injuryAdjustmentService.adjustProbabilities(
                            response.getProbHomeWin(), response.getProbDraw(), response.getProbAwayWin(),
                            injuryContext.getHomeAvailability(), injuryContext.getAwayAvailability());

                    String note = injuryAdjustmentService.buildAdjustmentNote(
                            injuryContext.getHomeAvailability(), injuryContext.getAwayAvailability());

                    // Rebuild response with injury data overlaid
                    response = PredictResponse.builder()
                            .homeTeam(response.getHomeTeam())
                            .awayTeam(response.getAwayTeam())
                            .prediction(response.getPrediction())
                            .predictionCode(response.getPredictionCode())
                            .probHomeWin(adjusted[0])
                            .probDraw(adjusted[1])
                            .probAwayWin(adjusted[2])
                            .confidence(response.getConfidence())
                            .features(response.getFeatures())
                            .h2hInsights(response.getH2hInsights())
                            .homeElo(response.getHomeElo())
                            .awayElo(response.getAwayElo())
                            .eloDifference(response.getEloDifference())
                            .upsetAlert(response.getUpsetAlert())
                            .upsetTeam(response.getUpsetTeam())
                            .explanation(response.getExplanation())
                            .scorePrediction(response.getScorePrediction())
                            .homeAvailability(response.getHomeAvailability())
                            .awayAvailability(response.getAwayAvailability())
                            .availabilityNote(response.getAvailabilityNote())
                            .injuryContext(injuryContext)
                            .injuryAdjustmentNote(note)
                            .build();
                }
            } catch (Exception e) {
                log.warn("Injury adjustment failed for fixtureId {}: {}", fixtureId, e.getMessage());
            }
        }

        return ResponseEntity.ok(response);
    }

    // ── Score Prediction ────────────────────────────────────────────────

    /**
     * Predict exact match scoreline using Poisson regression (Dixon-Coles model).
     * GET /api/predict/score?home=Arsenal&away=Chelsea
     */
    @Operation(summary = "Predict match scoreline",
            description = "Predicts the exact scoreline of a match using a Dixon-Coles Poisson model. " +
                    "Returns most likely score, top-3 scores, over/under probabilities, BTTS, " +
                    "and a full 0-0 to 5-5 score probability matrix.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Score prediction generated successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid team name or request"),
                    @ApiResponse(responseCode = "503", description = "Poisson model not trained yet")
            })
    @GetMapping("/predict/score")
    public ResponseEntity<?> predictScore(
            @Parameter(description = "Home team name", example = "Arsenal") @RequestParam String home,
            @Parameter(description = "Away team name", example = "Chelsea") @RequestParam String away,
            @Parameter(description = "Season (optional, for future use)", example = "2025") @RequestParam(required = false) String season) {

        if (home == null || home.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "home is required",
                    "hint", "Use GET /api/teams to see valid team names"
            ));
        }
        if (away == null || away.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "away is required",
                    "hint", "Use GET /api/teams to see valid team names"
            ));
        }
        if (home.equalsIgnoreCase(away)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "home and away cannot be the same team"
            ));
        }

        // Validate and normalize team names
        TeamValidationService.ValidationResult homeValidation = teamValidationService.validateTeam(home);
        if (!homeValidation.isValid()) {
            return ResponseEntity.badRequest().body(homeValidation.toErrorResponse());
        }
        TeamValidationService.ValidationResult awayValidation = teamValidationService.validateTeam(away);
        if (!awayValidation.isValid()) {
            return ResponseEntity.badRequest().body(awayValidation.toErrorResponse());
        }

        String homeTeam = homeValidation.getNormalizedName();
        String awayTeam = awayValidation.getNormalizedName();

        if (!scorePredictionService.isModelAvailable()) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Poisson score model not available",
                    "hint", "Train the model first via POST /api/training/train-poisson"
            ));
        }

        try {
            ScorePredictionDTO prediction = scorePredictionService.predictScore(homeTeam, awayTeam);
            return ResponseEntity.ok(prediction);
        } catch (Exception e) {
            log.error("Score prediction failed for {} vs {}: {}", homeTeam, awayTeam, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Score prediction failed: " + e.getMessage()
            ));
        }
    }

    // ── Head-to-Head Insights ──────────────────────────────────────────────

    /**
     * Get enhanced H2H insights between two teams.
     * GET /api/h2h?homeTeam=Arsenal&awayTeam=Chelsea
     */
    @Operation(summary = "Get head-to-head insights", description = "Returns historical H2H record, recent meetings, and venue advantage between two teams")
    @GetMapping("/h2h")
    public ResponseEntity<?> getH2HInsights(
            @Parameter(description = "Home team name", example = "Arsenal") @RequestParam String homeTeam,
            @Parameter(description = "Away team name", example = "Chelsea") @RequestParam String awayTeam) {

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
    @Operation(summary = "Get trending insights", description = "Hot/cold teams, upset alerts, and trending stats for a season")
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
    @Operation(summary = "Get available seasons for insights")
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
    @Operation(summary = "Update unresolved predictions with actual results")
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
    @Operation(summary = "Get all predictions", description = "Returns upcoming match list from external data source")
    @GetMapping("/predictions")
    public ResponseEntity<Map<String, Object>> getAllPredictions() {
        try {
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
        } catch (Exception e) {
            log.warn("Failed to fetch scheduled matches from external API: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "predictions", List.of(),
                    "count", 0,
                    "source", "upcoming_matches",
                    "hint", "External API unavailable – use POST /api/predict to make custom predictions"
            ));
        }
    }

    /**
     * Get today's predictions from upcoming matches.
     * GET /api/predictions/today
     */
    @Operation(summary = "Get today's predictions from upcoming matches")
    @GetMapping("/predictions/today")
    public ResponseEntity<Map<String, Object>> getTodaysPredictions() {
        String today = LocalDate.now().toString();
        try {
            var upcomingMatches = footballDataApiService.getScheduledMatches("PL");

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
        } catch (Exception e) {
            log.warn("Failed to fetch today's matches from external API: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "date", today,
                    "predictions", List.of(),
                    "count", 0,
                    "hint", "External API unavailable – use GET /api/external/predict for ML predictions"
            ));
        }
    }

    /**
     * Manually trigger prediction generation for all upcoming scheduled matches.
     * POST /api/predictions/generate
     */
    @Operation(summary = "Generate predictions for all upcoming matches",
            description = "Triggers the prediction engine to generate and store predictions for all upcoming scheduled matches")
    @PostMapping("/predictions/generate")
    public ResponseEntity<Map<String, Object>> generatePredictions() {
        log.info("Manual prediction generation triggered via API");
        int generated = dailyPredictionScheduler.executePredictionGeneration("MANUAL_API");
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "predictionsGenerated", generated,
                "message", generated > 0
                        ? "Generated " + generated + " predictions for upcoming matches"
                        : "No predictions generated — check if model is loaded and matches are available"
        ));
    }
}
