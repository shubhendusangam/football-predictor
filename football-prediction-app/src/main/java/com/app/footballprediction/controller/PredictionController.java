package com.app.footballprediction.controller;

import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import com.app.footballprediction.dto.PredictRequest;
import com.app.footballprediction.dto.PredictResponse;
import com.app.footballprediction.dto.H2HInsightsResponse;
import com.app.footballprediction.dto.TrendingInsightsResponse;
import com.app.common.service.FeatureEngineeringService;
import com.app.common.model.MatchFeatures;
import com.app.common.util.PredictionUtils;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import com.app.footballprediction.scheduler.DataUpdateScheduler;
import com.app.footballprediction.service.CsvIngestionService;
import com.app.footballprediction.service.FootballDataApiService;
import com.app.footballprediction.service.H2HInsightsService;
import com.app.footballprediction.service.NewsService;
import com.app.footballprediction.service.TrendingInsightsService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class PredictionController {

   private final FeatureEngineeringService featureEngineeringService;
   private final ModelTrainingService modelTrainingService;
   private final CsvIngestionService csvIngestionService;
   private final DataUpdateScheduler dataUpdateScheduler;
   private final FootballDataApiService footballDataApiService;
   private final NewsService newsService;
   private final H2HInsightsService h2hInsightsService;
   private final TrendingInsightsService trendingInsightsService;
   private final CacheManager cacheManager;

   // ── Prediction ────────────────────────────────────────────────────────

   /**
    * Predict match outcome.
    * <p>
    * POST /api/predict
    * {
    *   "homeTeam": "Arsenal",
    *   "awayTeam": "Chelsea"
    * }
    * <p>
    * Team names must match exactly what's in your CSV.
    * Use GET /api/teams to get valid names.
    */
   @PostMapping("/predict")
   public ResponseEntity<?> predict(@RequestBody PredictRequest request) {

      // ── Input validation ───────────────────────────────────────
      if (request.getHomeTeam() == null || request.getHomeTeam().isBlank()) {
         return ResponseEntity.badRequest().body(Map.of(
               "error", "homeTeam is required",
               "hint",  "Use GET /api/teams to see valid team names"
         ));
      }

      if (request.getAwayTeam() == null || request.getAwayTeam().isBlank()) {
         return ResponseEntity.badRequest().body(Map.of(
               "error", "awayTeam is required",
               "hint",  "Use GET /api/teams to see valid team names"
         ));
      }

      if (request.getHomeTeam().equalsIgnoreCase(request.getAwayTeam())) {
         return ResponseEntity.badRequest().body(Map.of(
               "error", "homeTeam and awayTeam cannot be the same"
         ));
      }

      try {
         // ── Build features from DB history ─────────────────────
         MatchFeatures features = featureEngineeringService
               .buildFeaturesForPrediction(
                     request.getHomeTeam(),
                     request.getAwayTeam()
               );

         log.debug("Features built → homeForm:{} awayForm:{} h2h:{}/{}/{}",
               PredictionUtils.round(features.getHomeFormPoints()),
               PredictionUtils.round(features.getAwayFormPoints()),
               PredictionUtils.round(features.getH2hHomeWinRate()),
               PredictionUtils.round(features.getH2hDrawRate()),
               PredictionUtils.round(features.getH2hAwayWinRate()));

         // ── Run model ──────────────────────────────────────────
         double[] probes = modelTrainingService.predict(features);
         String label   = modelTrainingService.getPredictedLabel(probes);

         // ── Get enhanced H2H insights ──────────────────────────
         H2HInsightsResponse h2hFull = h2hInsightsService.getH2HInsights(
               request.getHomeTeam(), request.getAwayTeam());
         PredictResponse.H2HSummary h2hSummary = buildH2HSummary(h2hFull);

         // ── Build response ─────────────────────────────────────
         PredictResponse response = PredictResponse.builder()
               .homeTeam(request.getHomeTeam())
               .awayTeam(request.getAwayTeam())
               .prediction(PredictionUtils.labelToText(label))
               .predictionCode(label)
               .probHomeWin(PredictionUtils.round(probes[0]))
               .probDraw(PredictionUtils.round(probes[1]))
               .probAwayWin(PredictionUtils.round(probes[2]))
               .confidence(PredictionUtils.getConfidence(probes))
               .features(PredictResponse.FeatureSummary.builder()
                     .homeFormPoints(PredictionUtils.round(features.getHomeFormPoints()))
                     .awayFormPoints(PredictionUtils.round(features.getAwayFormPoints()))
                     .homeGoalsScoredAvg(PredictionUtils.round(features.getHomeGoalsScoredAvg()))
                     .awayGoalsScoredAvg(PredictionUtils.round(features.getAwayGoalsScoredAvg()))
                     .h2hHomeWinRate(PredictionUtils.round(features.getH2hHomeWinRate()))
                     .h2hDrawRate(PredictionUtils.round(features.getH2hDrawRate()))
                     .h2hAwayWinRate(PredictionUtils.round(features.getH2hAwayWinRate()))
                     .build())
               .h2hInsights(h2hSummary)
               .build();

         log.info("Predicted: {} vs {} → {} (H:{} D:{} A:{})",
               request.getHomeTeam(),
               request.getAwayTeam(),
               response.getPrediction(),
               PredictionUtils.round(probes[0]),
               PredictionUtils.round(probes[1]),
               PredictionUtils.round(probes[2]));

         return ResponseEntity.ok(response);

      } catch (IllegalStateException e) {
         // Model not loaded yet
         return ResponseEntity.badRequest().body(Map.of(
               "error", e.getMessage(),
               "hint",  "Call POST /api/model/train first"
         ));
      } catch (Exception e) {
         log.error("Prediction failed for {} vs {}: {}",
               request.getHomeTeam(),
               request.getAwayTeam(),
               e.getMessage());
         return ResponseEntity.internalServerError().body(Map.of(
               "error", e.getMessage()
         ));
      }
   }

   // ── Model management ──────────────────────────────────────────────────

   /**
    * Train or retrain model from all data in DB.
    * Takes 30–60 seconds. Overwrites existing model on disk.
    * <p>
    * POST /api/model/train
    */
   @PostMapping("/model/train")
   public ResponseEntity<?> trainModel() {
      try {
         log.info("Manual retrain requested via API...");
         String report = modelTrainingService.trainAndEvaluate();
         return ResponseEntity.ok(Map.of(
               "status", "success",
               "report", report
         ));
      } catch (IllegalStateException e) {
         return ResponseEntity.badRequest().body(Map.of(
               "error", e.getMessage()
         ));
      } catch (Exception e) {
         log.error("Training failed: {}", e.getMessage());
         return ResponseEntity.internalServerError().body(Map.of(
               "error", e.getMessage()
         ));
      }
   }

   /**
    * Advanced training with cross-validation, grid search, and ensemble.
    * This is the recommended training method for best accuracy.
    * <p>
    * POST /api/model/train/advanced
    */
   @PostMapping("/model/train/advanced")
   public ResponseEntity<?> trainAdvanced() {
      try {
         log.info("Advanced training requested via API...");
         String report = modelTrainingService.trainAdvanced();
         return ResponseEntity.ok(Map.of(
               "status", "success",
               "report", report
         ));
      } catch (IllegalStateException e) {
         return ResponseEntity.badRequest().body(Map.of(
               "error", e.getMessage()
         ));
      } catch (Exception e) {
         log.error("Advanced training failed: {}", e.getMessage());
         return ResponseEntity.internalServerError().body(Map.of(
               "error", e.getMessage()
         ));
      }
   }

   /**
    * Train using cross-validation for better evaluation.
    * <p>
    * POST /api/model/train/cv
    */
   @PostMapping("/model/train/cv")
   public ResponseEntity<?> trainWithCrossValidation() {
      try {
         log.info("Cross-validation training requested via API...");
         String report = modelTrainingService.trainWithCrossValidation();
         return ResponseEntity.ok(Map.of(
               "status", "success",
               "report", report
         ));
      } catch (IllegalStateException e) {
         return ResponseEntity.badRequest().body(Map.of(
               "error", e.getMessage()
         ));
      } catch (Exception e) {
         log.error("Cross-validation training failed: {}", e.getMessage());
         return ResponseEntity.internalServerError().body(Map.of(
               "error", e.getMessage()
         ));
      }
   }

   /**
    * Train using Gradient Boosting (AdaBoost).
    * <p>
    * POST /api/model/train/boosting
    */
   @PostMapping("/model/train/boosting")
   public ResponseEntity<?> trainGradientBoosting() {
      try {
         log.info("Gradient Boosting training requested via API...");
         String report = modelTrainingService.trainGradientBoosting();
         return ResponseEntity.ok(Map.of(
               "status", "success",
               "report", report
         ));
      } catch (IllegalStateException e) {
         return ResponseEntity.badRequest().body(Map.of(
               "error", e.getMessage()
         ));
      } catch (Exception e) {
         log.error("Gradient Boosting training failed: {}", e.getMessage());
         return ResponseEntity.internalServerError().body(Map.of(
               "error", e.getMessage()
         ));
      }
   }

   /**
    * Train ensemble model combining multiple classifiers.
    * <p>
    * POST /api/model/train/ensemble
    */
   @PostMapping("/model/train/ensemble")
   public ResponseEntity<?> trainEnsemble() {
      try {
         log.info("Ensemble training requested via API...");
         String report = modelTrainingService.trainEnsemble();
         return ResponseEntity.ok(Map.of(
               "status", "success",
               "report", report
         ));
      } catch (IllegalStateException e) {
         return ResponseEntity.badRequest().body(Map.of(
               "error", e.getMessage()
         ));
      } catch (Exception e) {
         log.error("Ensemble training failed: {}", e.getMessage());
         return ResponseEntity.internalServerError().body(Map.of(
               "error", e.getMessage()
         ));
      }
   }

   /**
    * Train Stacked Ensemble model: RandomForest + Gradient Boosting + Logistic Regression meta-model.
    * This is the most advanced ensemble approach using model stacking.
    * <p>
    * Architecture:
    * - Base Model 1: RandomForest (100 trees)
    * - Base Model 2: Gradient Boosting (AdaBoostM1, 100 iterations)
    * - Meta Model: Logistic Regression (combines base model predictions)
    * <p>
    * POST /api/model/train/stacked
    */
   @PostMapping("/model/train/stacked")
   public ResponseEntity<?> trainStackedEnsemble() {
      try {
         log.info("Stacked Ensemble training requested via API...");
         log.info("Architecture: RandomForest + Gradient Boosting + Logistic Regression meta-model");
         String report = modelTrainingService.trainAndEvaluate();
         return ResponseEntity.ok(Map.of(
               "status", "success",
               "modelType", "STACKED_ENSEMBLE",
               "architecture", Map.of(
                     "baseModel1", "RandomForest (100 trees)",
                     "baseModel2", "Gradient Boosting (AdaBoostM1, 100 iterations)",
                     "metaModel", "Logistic Regression"
               ),
               "report", report
         ));
      } catch (IllegalStateException e) {
         return ResponseEntity.badRequest().body(Map.of(
               "error", e.getMessage()
         ));
      } catch (Exception e) {
         log.error("Stacked Ensemble training failed: {}", e.getMessage());
         return ResponseEntity.internalServerError().body(Map.of(
               "error", e.getMessage()
         ));
      }
   }

   /**
    * Perform hyperparameter grid search.
    * <p>
    * POST /api/model/grid-search
    */
   @PostMapping("/model/grid-search")
   public ResponseEntity<?> gridSearch() {
      try {
         log.info("Grid search requested via API...");
         String report = modelTrainingService.performGridSearch();
         return ResponseEntity.ok(Map.of(
               "status", "success",
               "report", report
         ));
      } catch (IllegalStateException e) {
         return ResponseEntity.badRequest().body(Map.of(
               "error", e.getMessage()
         ));
      } catch (Exception e) {
         log.error("Grid search failed: {}", e.getMessage());
         return ResponseEntity.internalServerError().body(Map.of(
               "error", e.getMessage()
         ));
      }
   }

   /**
    * Compare all available models using cross-validation.
    * <p>
    * GET /api/model/compare
    */
   @GetMapping("/model/compare")
   public ResponseEntity<?> compareModels() {
      try {
         log.info("Model comparison requested via API...");
         String report = modelTrainingService.compareModels();
         return ResponseEntity.ok(Map.of(
               "status", "success",
               "report", report
         ));
      } catch (IllegalStateException e) {
         return ResponseEntity.badRequest().body(Map.of(
               "error", e.getMessage()
         ));
      } catch (Exception e) {
         log.error("Model comparison failed: {}", e.getMessage());
         return ResponseEntity.internalServerError().body(Map.of(
               "error", e.getMessage()
         ));
      }
   }

   /**
    * Check whether the model is loaded and ready.
   /**
    * GET /api/model/status
    */
   @GetMapping("/model/status")
   public ResponseEntity<?> modelStatus() {
      boolean loaded = modelTrainingService.isModelLoaded();
      String lastUpdated = modelTrainingService.getModelLastUpdated();

      // Get actual stats
      long totalMatches = featureEngineeringService.getAllTeams().isEmpty() ? 0 :
              csvIngestionService.getMatchCount();
      int totalTeams = featureEngineeringService.getAllTeams().size();

      Map<String, Object> response = new HashMap<>();
      response.put("modelLoaded", loaded);
      response.put("totalMatches", totalMatches);
      response.put("totalTeams", totalTeams);
      response.put("totalFeatures", 22); // Number of ML features used
      response.put("hint", loaded
            ? "Ready to predict. Call POST /api/predict"
            : "Model not loaded. Call POST /api/model/train");
      if (lastUpdated != null) {
         response.put("lastUpdated", lastUpdated);
      }
      return ResponseEntity.ok(response);
   }

   // ── Data management ───────────────────────────────────────────────────

   /**
    * Re-ingest CSV files after adding new seasons.
    * Skips already-loaded matches automatically.
    * <p>
    * POST /api/data/reload
    */
   @PostMapping("/data/reload")
   public ResponseEntity<?> reloadData() {
      try {
         log.info("CSV reload requested via API...");
         csvIngestionService.ingestAll();
         return ResponseEntity.ok(Map.of(
               "status", "CSV data reloaded successfully"
         ));
      } catch (Exception e) {
         log.error("CSV reload failed: {}", e.getMessage());
         return ResponseEntity.internalServerError().body(Map.of(
               "error", e.getMessage()
         ));
      }
   }

   /**
    * Download latest data from football-data.co.uk and retrain model.
    * <p>
    * POST /api/data/update
    */
   @PostMapping("/data/update")
   public ResponseEntity<?> updateData() {
      try {
         log.info("Data update requested via API...");
         String result = dataUpdateScheduler.triggerManualUpdate();
         return ResponseEntity.ok(Map.of(
               "status", "success",
               "result", result
         ));
      } catch (Exception e) {
         log.error("Data update failed: {}", e.getMessage());
         return ResponseEntity.internalServerError().body(Map.of(
               "error", e.getMessage()
         ));
      }
   }

   // ── Head-to-Head Insights ──────────────────────────────────────────────

   /**
    * Get enhanced Head-to-Head (H2H) insights between two teams.
    * <p>
    * GET /api/h2h?homeTeam=Arsenal&awayTeam=Chelsea
    * <p>
    * Returns comprehensive H2H statistics including:
    * - Historical Record Display: "Arsenal leads 15-8-7 vs Chelsea" format
    * - Recent H2H Timeline: Last 5 meetings with results and scorelines
    * - H2H Goal Stats: Average goals when these teams meet
    * - Common Results: Most frequent outcome in H2H matchups
    * - Venue Advantage: H2H win % based on home/away
    */
   @GetMapping("/h2h")
   public ResponseEntity<?> getH2HInsights(
         @RequestParam String homeTeam,
         @RequestParam String awayTeam) {

      // ── Input validation ───────────────────────────────────────
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

      try {
         log.info("Fetching H2H insights: {} vs {}", homeTeam, awayTeam);
         H2HInsightsResponse insights = h2hInsightsService.getH2HInsights(homeTeam, awayTeam);
         return ResponseEntity.ok(insights);
      } catch (Exception e) {
         log.error("Failed to fetch H2H insights for {} vs {}: {}",
               homeTeam, awayTeam, e.getMessage());
         return ResponseEntity.internalServerError().body(Map.of(
               "error", e.getMessage()
         ));
      }
   }

   // ── Trending Insights ─────────────────────────────────────────────────

   /**
    * Get live/trending insights across all teams.
    * <p>
    * GET /api/insights/trending
    * <p>
    * Returns:
    * - 🔥 Hot Teams: Teams on 3+ match winning streaks
    * - ❄️ Cold Teams: Teams without a win in 5+ matches
    * - ⚽ Top Scorers: Teams scoring most goals recently
    * - 🧱 Defensive Walls: Teams with most clean sheets
    * - 🎯 Upset Alerts: Matches where away team has >50% win probability
    * - 🎉 Goal Fest: Matches with highest expected total goals
    */
   @GetMapping("/insights/trending")
   public ResponseEntity<?> getTrendingInsights() {
      try {
         log.info("Fetching trending insights...");
         TrendingInsightsResponse insights = trendingInsightsService.getTrendingInsights();
         return ResponseEntity.ok(insights);
      } catch (Exception e) {
         log.error("Failed to fetch trending insights: {}", e.getMessage());
         return ResponseEntity.internalServerError().body(Map.of(
               "error", e.getMessage()
         ));
      }
   }


   // ── Helpers ───────────────────────────────────────────────────────────

   /**
    * Clear all caches.
    * <p>
    * POST /api/cache/clear
    */
   @PostMapping("/cache/clear")
   public ResponseEntity<?> clearAllCaches() {
      try {
         log.info("Cache clear requested via API...");

         // Clear Spring caches
         cacheManager.getCacheNames().forEach(cacheName -> {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
               cache.clear();
            }
         });

         // Clear service-level caches
         footballDataApiService.clearCache();
         newsService.clearCache();

         return ResponseEntity.ok(Map.of(
               "status", "All caches cleared successfully",
               "caches", cacheManager.getCacheNames()
         ));
      } catch (Exception e) {
         log.error("Cache clear failed: {}", e.getMessage());
         return ResponseEntity.internalServerError().body(Map.of(
               "error", e.getMessage()
         ));
      }
   }

   /**
    * Get cache status.
    * <p>
    * GET /api/cache/status
    */
   @GetMapping("/cache/status")
   public ResponseEntity<?> getCacheStatus() {
      try {
         return ResponseEntity.ok(Map.of(
               "caches", cacheManager.getCacheNames(),
               "status", "active"
         ));
      } catch (Exception e) {
         return ResponseEntity.internalServerError().body(Map.of(
               "error", e.getMessage()
         ));
      }
   }

   /**
    * Build H2H summary from full H2H insights response.
    */
   private PredictResponse.H2HSummary buildH2HSummary(H2HInsightsResponse h2h) {
      if (h2h == null || h2h.getHistoricalRecord() == null) {
         return null;
      }

      var historical = h2h.getHistoricalRecord();
      var goalStats = h2h.getGoalStats();
      var commonResults = h2h.getCommonResults();
      var venueAdvantage = h2h.getVenueAdvantage();

      // Convert recent meetings to summary format
      var recentMeetings = h2h.getRecentMeetings() != null
            ? h2h.getRecentMeetings().stream()
                  .map(m -> PredictResponse.RecentH2HMatch.builder()
                        .date(m.getDate())
                        .homeTeamInMatch(m.getHomeTeamInMatch())
                        .awayTeamInMatch(m.getAwayTeamInMatch())
                        .score(m.getScore())
                        .winner(m.getWinner())
                        .season(m.getSeason())
                        .build())
                  .toList()
            : java.util.Collections.<PredictResponse.RecentH2HMatch>emptyList();

      return PredictResponse.H2HSummary.builder()
            .historicalRecord(historical.getSummary())
            .totalMeetings(historical.getTotalMatches())
            .homeTeamWins(historical.getHomeTeamWins())
            .draws(historical.getDraws())
            .awayTeamWins(historical.getAwayTeamWins())
            .dominantTeam(historical.getDominantTeam())
            .recentMeetings(recentMeetings)
            .avgGoalsPerMatch(goalStats != null ? goalStats.getAvgTotalGoals() : 0)
            .avgHomeTeamGoals(goalStats != null ? goalStats.getAvgHomeTeamGoals() : 0)
            .avgAwayTeamGoals(goalStats != null ? goalStats.getAvgAwayTeamGoals() : 0)
            .bttsPercentage(goalStats != null ? goalStats.getBttsPercentage() : 0)
            .mostCommonScore(commonResults != null ? commonResults.getMostCommonResult() : "N/A")
            .mostCommonOutcome(commonResults != null ? commonResults.getMostCommonOutcome() : "N/A")
            .homeTeamHomeWinPct(venueAdvantage != null ? venueAdvantage.getHomeTeamHomeWinPercentage() : 0)
            .awayTeamHomeWinPct(venueAdvantage != null ? venueAdvantage.getAwayTeamHomeWinPercentage() : 0)
            .venueAdvantageNote(venueAdvantage != null ? venueAdvantage.getHomeAdvantageDescription() : "N/A")
            .build();
   }
}
