package com.app.footballprediction.controller;

import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import com.app.footballprediction.dto.PredictRequest;
import com.app.footballprediction.dto.PredictResponse;
import com.app.footballprediction.featureengineering.FeatureEngineeringService;
import com.app.footballprediction.model.MatchFeatures;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import com.app.footballprediction.scheduler.DataUpdateScheduler;
import com.app.footballprediction.service.CsvIngestionService;
import com.app.footballprediction.service.FootballDataApiService;
import com.app.footballprediction.service.NewsService;

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
               round(features.getHomeFormPoints()),
               round(features.getAwayFormPoints()),
               round(features.getH2hHomeWinRate()),
               round(features.getH2hDrawRate()),
               round(features.getH2hAwayWinRate()));

         // ── Run model ──────────────────────────────────────────
         double[] probes = modelTrainingService.predict(features);
         String label   = modelTrainingService.getPredictedLabel(probes);

         // ── Build response ─────────────────────────────────────
         PredictResponse response = PredictResponse.builder()
               .homeTeam(request.getHomeTeam())
               .awayTeam(request.getAwayTeam())
               .prediction(labelToText(label))
               .predictionCode(label)
               .probHomeWin(round(probes[0]))
               .probDraw(round(probes[1]))
               .probAwayWin(round(probes[2]))
               .confidence(getConfidence(probes))
               .features(PredictResponse.FeatureSummary.builder()
                     .homeFormPoints(round(features.getHomeFormPoints()))
                     .awayFormPoints(round(features.getAwayFormPoints()))
                     .homeGoalsScoredAvg(round(features.getHomeGoalsScoredAvg()))
                     .awayGoalsScoredAvg(round(features.getAwayGoalsScoredAvg()))
                     .h2hHomeWinRate(round(features.getH2hHomeWinRate()))
                     .h2hDrawRate(round(features.getH2hDrawRate()))
                     .h2hAwayWinRate(round(features.getH2hAwayWinRate()))
                     .build())
               .build();

         log.info("Predicted: {} vs {} → {} (H:{} D:{} A:{})",
               request.getHomeTeam(),
               request.getAwayTeam(),
               response.getPrediction(),
               round(probes[0]),
               round(probes[1]),
               round(probes[2]));

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
    * Check whether the model is loaded and ready.
    * <p>
    * GET /api/model/status
    */
   @GetMapping("/model/status")
   public ResponseEntity<?> modelStatus() {
      boolean loaded = modelTrainingService.isModelLoaded();
      return ResponseEntity.ok(Map.of(
            "modelLoaded", loaded,
            "hint", loaded
                  ? "Ready to predict. Call POST /api/predict"
                  : "Model not loaded. Call POST /api/model/train"
      ));
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

   // ── Teams ─────────────────────────────────────────────────────────────

   /**
    * Get all available team names.
    * <p>
    * GET /api/teams
    */
   @GetMapping("/teams")
   public ResponseEntity<?> getTeams() {
      try {
         var teams = featureEngineeringService.getAllTeams();
         return ResponseEntity.ok(teams);
      } catch (Exception e) {
         log.error("Failed to fetch teams: {}", e.getMessage());
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

   private String labelToText(String label) {
      return switch (label) {
         case "H" -> "HOME_WIN";
         case "D" -> "DRAW";
         case "A" -> "AWAY_WIN";
         default  -> "UNKNOWN";
      };
   }

   /**
    * Confidence based on the highest probability in the distribution.
    * HIGH   → model is fairly sure (>= 0.55)
    * MEDIUM → model leans one way but not strongly (>= 0.45)
    * LOW    → all three outcomes nearly equal — don't trust this one
    */
   private String getConfidence(double[] probes) {
      double max = Math.max(probes[0], Math.max(probes[1], probes[2]));
      if (max >= 0.55) return "HIGH";
      if (max >= 0.45) return "MEDIUM";
      return "LOW";
   }

   /**
    * Round to 2 decimal places for clean JSON output.
    */
   private double round(double val) {
      return Math.round(val * 100.0) / 100.0;
   }
}
