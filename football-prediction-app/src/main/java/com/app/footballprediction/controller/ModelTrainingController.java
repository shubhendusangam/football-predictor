package com.app.footballprediction.controller;

import com.app.common.service.FeatureEngineeringService;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import com.app.footballprediction.service.CsvIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for ML model training and status.
 *
 * Endpoints:
 * - POST /api/model/train           — standard training
 * - POST /api/model/train/advanced   — full pipeline
 * - POST /api/model/train/cv         — cross-validation
 * - POST /api/model/train/boosting   — gradient boosting
 * - POST /api/model/train/ensemble   — ensemble
 * - POST /api/model/train/stacked    — stacked ensemble
 * - POST /api/model/grid-search      — hyperparameter search
 * - GET  /api/model/compare          — compare models
 * - GET  /api/model/status           — readiness check
 */
@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
@Slf4j
public class ModelTrainingController {

    private final ModelTrainingService modelTrainingService;
    private final FeatureEngineeringService featureEngineeringService;
    private final CsvIngestionService csvIngestionService;

    @PostMapping("/train")
    public ResponseEntity<Map<String, Object>> trainModel() throws Exception {
        log.info("Manual retrain requested via API...");
        String report = modelTrainingService.trainAndEvaluate();
        return ResponseEntity.ok(Map.of("status", "success", "report", report));
    }

    @PostMapping("/train/advanced")
    public ResponseEntity<Map<String, Object>> trainAdvanced() throws Exception {
        log.info("Advanced training requested via API...");
        String report = modelTrainingService.trainAdvanced();
        return ResponseEntity.ok(Map.of("status", "success", "report", report));
    }

    @PostMapping("/train/cv")
    public ResponseEntity<Map<String, Object>> trainWithCrossValidation() throws Exception {
        log.info("Cross-validation training requested via API...");
        String report = modelTrainingService.trainWithCrossValidation();
        return ResponseEntity.ok(Map.of("status", "success", "report", report));
    }

    @PostMapping("/train/boosting")
    public ResponseEntity<Map<String, Object>> trainGradientBoosting() throws Exception {
        log.info("Gradient Boosting training requested via API...");
        String report = modelTrainingService.trainGradientBoosting();
        return ResponseEntity.ok(Map.of("status", "success", "report", report));
    }

    @PostMapping("/train/ensemble")
    public ResponseEntity<Map<String, Object>> trainEnsemble() throws Exception {
        log.info("Ensemble training requested via API...");
        String report = modelTrainingService.trainEnsemble();
        return ResponseEntity.ok(Map.of("status", "success", "report", report));
    }

    @PostMapping("/train/stacked")
    public ResponseEntity<Map<String, Object>> trainStackedEnsemble() throws Exception {
        log.info("Stacked Ensemble training requested via API...");
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
    }

    @PostMapping("/grid-search")
    public ResponseEntity<Map<String, Object>> gridSearch() throws Exception {
        log.info("Grid search requested via API...");
        String report = modelTrainingService.performGridSearch();
        return ResponseEntity.ok(Map.of("status", "success", "report", report));
    }

    @GetMapping("/compare")
    public ResponseEntity<Map<String, Object>> compareModels() throws Exception {
        log.info("Model comparison requested via API...");
        String report = modelTrainingService.compareModels();
        return ResponseEntity.ok(Map.of("status", "success", "report", report));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> modelStatus() {
        boolean loaded = modelTrainingService.isModelLoaded();
        String lastUpdated = modelTrainingService.getModelLastUpdated();

        long totalMatches = featureEngineeringService.getAllTeams().isEmpty() ? 0 :
                csvIngestionService.getMatchCount();
        int totalTeams = featureEngineeringService.getAllTeams().size();

        Map<String, Object> response = new HashMap<>();
        response.put("modelLoaded", loaded);
        response.put("totalMatches", totalMatches);
        response.put("totalTeams", totalTeams);
        response.put("totalFeatures", 22);
        response.put("hint", loaded
                ? "Ready to predict. Call POST /api/predict"
                : "Model not loaded. Call POST /api/model/train");
        if (lastUpdated != null) {
            response.put("lastUpdated", lastUpdated);
        }
        return ResponseEntity.ok(response);
    }
}

