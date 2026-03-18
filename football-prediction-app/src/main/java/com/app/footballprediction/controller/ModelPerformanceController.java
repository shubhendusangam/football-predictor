package com.app.footballprediction.controller;

import com.app.common.model.ModelAccuracy;
import com.app.common.model.ModelTrainingHistory;
import com.app.common.model.PredictionEvaluation;
import com.app.common.repository.PredictionEvaluationRepository;
import com.app.footballprediction.modeltraining.CrossValidationResult;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import com.app.footballprediction.scheduler.PredictionRecalculationScheduler;
import com.app.footballprediction.service.HistoricalPredictionGenerator;
import com.app.footballprediction.service.MatchResultProcessor;
import com.app.footballprediction.service.ModelAccuracyService;
import com.app.footballprediction.service.ModelSelfTrainingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for model performance, accuracy, error analysis, and retraining history.
 */
@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Model", description = "ML model training, evaluation, grid search, and readiness status")
public class ModelPerformanceController {

    private final ModelAccuracyService modelAccuracyService;
    private final ModelSelfTrainingService modelSelfTrainingService;
    private final ModelTrainingService modelTrainingService;
    private final PredictionEvaluationRepository evaluationRepository;
    private final PredictionRecalculationScheduler recalculationScheduler;
    private final MatchResultProcessor matchResultProcessor;
    private final HistoricalPredictionGenerator historicalPredictionGenerator;
    /**
     * GET /api/model/accuracy
     *
     * Returns the latest model accuracy metrics (global, per-team, per-league).
     */
    @GetMapping("/accuracy")
    public ResponseEntity<?> getModelAccuracy() {
        Map<String, Object> response = new LinkedHashMap<>();

        // Global accuracy
        Optional<ModelAccuracy> globalAccuracy = modelAccuracyService.getLatestGlobalAccuracy();
        if (globalAccuracy.isPresent()) {
            Map<String, Object> global = getStringObjectMap(globalAccuracy);
            response.put("global", global);
        } else {
            response.put("global", Map.of("message", "No accuracy data available yet. Evaluations will be created when finished matches have corresponding predictions."));
        }

        // Team accuracies
        List<ModelAccuracy> teamAccuracies = modelAccuracyService.getAllTeamAccuracies();
        if (!teamAccuracies.isEmpty()) {
            List<Map<String, Object>> teams = getMapList(teamAccuracies);
            response.put("perTeam", teams);
        }

        // League/season accuracies
        List<ModelAccuracy> leagueAccuracies = modelAccuracyService.getAllLeagueAccuracies();
        if (!leagueAccuracies.isEmpty()) {
            List<Map<String, Object>> leagues = new ArrayList<>();
            for (ModelAccuracy a : leagueAccuracies) {
                Map<String, Object> league = new LinkedHashMap<>();
                league.put("season", a.getScopeKey());
                league.put("totalPredictions", a.getTotalPredictions());
                league.put("winnerAccuracy", String.format("%.1f%%", a.getWinnerAccuracy() * 100));
                league.put("goalErrorAverage", String.format("%.2f", a.getGoalErrorAverage()));
                leagues.add(league);
            }
            response.put("perLeague", leagues);
        }

        return ResponseEntity.ok(response);
    }

    private static @NonNull List<Map<String, Object>> getMapList(List<ModelAccuracy> teamAccuracies) {
        List<Map<String, Object>> teams = new ArrayList<>();
        for (ModelAccuracy a : teamAccuracies) {
            Map<String, Object> team = new LinkedHashMap<>();
            team.put("team", a.getScopeKey());
            team.put("totalPredictions", a.getTotalPredictions());
            team.put("winnerAccuracy", String.format("%.1f%%", a.getWinnerAccuracy() * 100));
            team.put("goalErrorAverage", String.format("%.2f", a.getGoalErrorAverage()));
            teams.add(team);
        }
        return teams;
    }

    private static @NonNull Map<String, Object> getStringObjectMap(Optional<ModelAccuracy> globalAccuracy) {
        ModelAccuracy a = globalAccuracy.get();
        Map<String, Object> global = new LinkedHashMap<>();
        global.put("totalPredictions", a.getTotalPredictions());
        global.put("correctWinnerPredictions", a.getCorrectWinnerPredictions());
        global.put("exactScorePredictions", a.getExactScorePredictions());
        global.put("winnerAccuracy", String.format("%.1f%%", a.getWinnerAccuracy() * 100));
        global.put("scoreAccuracy", String.format("%.1f%%", a.getScoreAccuracy() * 100));
        global.put("goalErrorAverage", String.format("%.2f", a.getGoalErrorAverage()));
        global.put("cardErrorAverage", String.format("%.2f", a.getCardErrorAverage()));
        global.put("cornerErrorAverage", String.format("%.2f", a.getCornerErrorAverage()));
        global.put("calculatedAt", a.getCalculatedAt().toString());
        return global;
    }

    /**
     * GET /api/model/performance
     *
     * Returns a comprehensive performance summary including accuracy,
     * training info, and retraining thresholds.
     */
    @GetMapping("/performance")
    public ResponseEntity<?> getModelPerformance() {
        Map<String, Object> performance = modelSelfTrainingService.getPerformanceSummary();
        return ResponseEntity.ok(performance);
    }

    /**
     * GET /api/model/error-analysis
     *
     * Returns detailed error analysis with per-season breakdowns.
     */
    @GetMapping("/error-analysis")
    public ResponseEntity<?> getErrorAnalysis() {
        Map<String, Object> analysis = modelAccuracyService.getErrorAnalysis();
        return ResponseEntity.ok(analysis);
    }

    /**
     * GET /api/model/retraining-history
     *
     * Returns the history of all model retraining events.
     */
    @GetMapping("/retraining-history")
    public ResponseEntity<?> getRetrainingHistory() {
        List<ModelTrainingHistory> history = modelSelfTrainingService.getTrainingHistory();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalTrainings", history.size());

        List<Map<String, Object>> entries = new ArrayList<>();
        for (ModelTrainingHistory h : history) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", h.getId());
            entry.put("trainingTime", h.getTrainingTime().toString());
            entry.put("triggerReason", h.getTriggerReason());
            entry.put("success", h.getSuccess());
            entry.put("durationMs", h.getTrainingDurationMs());
            entry.put("modelVersion", h.getModelVersion());

            if (h.getPreviousWinnerAccuracy() != null) {
                entry.put("previousWinnerAccuracy",
                        String.format("%.1f%%", h.getPreviousWinnerAccuracy() * 100));
            }
            if (h.getNewWinnerAccuracy() != null) {
                entry.put("newWinnerAccuracy",
                        String.format("%.1f%%", h.getNewWinnerAccuracy() * 100));
            }
            if (h.getWeightAdjustments() != null) {
                entry.put("weightAdjustments", h.getWeightAdjustments());
            }
            if (h.getErrorMessage() != null) {
                entry.put("errorMessage", h.getErrorMessage());
            }
            entries.add(entry);
        }
        response.put("history", entries);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/model/evaluations
     *
     * Returns recent prediction evaluations (for debugging/inspection).
     */
    @GetMapping("/evaluations")
    public ResponseEntity<?> getRecentEvaluations() {
        List<PredictionEvaluation> evaluations = evaluationRepository.findRecentEvaluations();

        List<Map<String, Object>> results = new ArrayList<>();
        for (PredictionEvaluation eval : evaluations) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("matchId", eval.getMatchId());
            e.put("homeTeam", eval.getHomeTeam());
            e.put("awayTeam", eval.getAwayTeam());
            e.put("predicted", eval.getPredictedHomeGoals() + "-" + eval.getPredictedAwayGoals());
            e.put("actual", eval.getActualHomeGoals() + "-" + eval.getActualAwayGoals());
            e.put("predictedWinner", eval.getPredictedWinner());
            e.put("actualWinner", eval.getActualWinner());
            e.put("winnerCorrect", eval.getWinnerCorrect());
            e.put("scoreExact", eval.getScoreExact());
            e.put("goalDifferenceError", eval.getGoalDifferenceError());
            e.put("cardPredictionError", eval.getCardPredictionError());
            e.put("cornerPredictionError", eval.getCornerPredictionError());
            e.put("confidence", eval.getPredictionConfidence());
            e.put("evaluationTime", eval.getEvaluationTime().toString());
            results.add(e);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalEvaluations", results.size());
        response.put("evaluations", results);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/model/retrain
     *
     * Manually trigger model retraining (admin action).
     */
    @PostMapping("/retrain")
    public ResponseEntity<?> triggerRetrain() {
        log.info("Manual model retraining requested via API");
        ModelTrainingHistory result = modelSelfTrainingService.forceRetrain();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", result.getSuccess() ? "SUCCESS" : "FAILED");
        response.put("trainingTime", result.getTrainingTime().toString());
        response.put("durationMs", result.getTrainingDurationMs());
        response.put("modelVersion", result.getModelVersion());
        if (result.getErrorMessage() != null) {
            response.put("error", result.getErrorMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/model/recalculate
     *
     * Manually trigger the full recalculation pipeline (admin action).
     */
    @PostMapping("/recalculate")
    public ResponseEntity<?> triggerRecalculation() {
        log.info("Manual recalculation pipeline triggered via API");
        recalculationScheduler.triggerManualRecalculation();

        return ResponseEntity.ok(Map.of(
                "status", "TRIGGERED",
                "message", "Recalculation pipeline has been triggered. Check logs for progress."
        ));
    }

    /**
     * POST /api/model/backfill-predictions
     *
     * Manually generate predictions for historical matches, resolve them
     * against actual results, and recalculate accuracy metrics.
     * Use this when the Model Accuracy tab shows empty data.
     */
    @PostMapping("/backfill-predictions")
    public ResponseEntity<?> backfillPredictions() {
        log.info("Manual prediction backfill triggered via API");
        try {
            // Step 1: Generate predictions for matches that don't have any
            int generated = historicalPredictionGenerator.generateAll();

            // Step 2: Resolve all unresolved predictions against finished matches
            int resolved = matchResultProcessor.processAllUnresolvedPredictions();

            // Step 3: Recalculate accuracy
            if (generated > 0 || resolved > 0) {
                modelAccuracyService.recalculateAllAccuracy();
            }

            log.info("Backfill complete: generated={}, resolved={}", generated, resolved);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "predictionsGenerated", generated,
                    "predictionsResolved", resolved,
                    "message", String.format("Generated %d predictions, resolved %d. Accuracy metrics updated.", generated, resolved)
            ));
        } catch (Exception e) {
            log.error("Manual prediction backfill failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "message", "Backfill failed: " + e.getMessage()
            ));
        }
    }

    /**
     * GET /api/model/feature-importance
     *
     * Returns feature importance analysis using the trained RandomForest.
     * Shows which features contribute most to prediction accuracy.
     */
    @GetMapping("/feature-importance")
    public ResponseEntity<?> getFeatureImportance() {
        try {
            Map<String, Double> importance = modelTrainingService.getFeatureImportance();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("totalFeatures", importance.size());
            response.put("features", importance);

            // Top 10 most important
            List<Map<String, Object>> top10 = new ArrayList<>();
            int rank = 1;
            for (Map.Entry<String, Double> entry : importance.entrySet()) {
                if (rank > 10) break;
                Map<String, Object> feature = new LinkedHashMap<>();
                feature.put("rank", rank++);
                feature.put("feature", entry.getKey());
                feature.put("importance", String.format("%.4f", entry.getValue()));
                top10.add(feature);
            }
            response.put("top10", top10);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Feature importance analysis failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Feature importance analysis failed",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/model/temporal-cv
     *
     * Performs time-series aware cross-validation (expanding window).
     * More realistic than standard k-fold CV for temporal prediction tasks.
     */
    @GetMapping("/temporal-cv")
    public ResponseEntity<?> performTemporalCrossValidation() {
        try {
            CrossValidationResult result = modelTrainingService.performTemporalCrossValidation();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("type", "Temporal Cross-Validation (Expanding Window)");
            response.put("folds", result.getFolds());
            response.put("accuracy", String.format("%.2f%%", result.getAccuracy()));
            response.put("kappa", String.format("%.4f", result.getKappa()));
            response.put("fMeasure", String.format("%.4f", result.getFMeasure()));
            response.put("precision", String.format("%.4f", result.getPrecision()));
            response.put("recall", String.format("%.4f", result.getRecall()));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Temporal CV failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Temporal cross-validation failed",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/model/sliding-accuracy
     *
     * Returns accuracy metrics over sliding time windows (30, 60, 90 days).
     * Helps detect model drift faster than all-time averages.
     */
    @GetMapping("/sliding-accuracy")
    public ResponseEntity<?> getSlidingWindowAccuracy() {
        Map<String, Object> windows = modelAccuracyService.getSlidingWindowAccuracy();

        if (windows.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "message", "No sliding window accuracy data available yet. Evaluations are needed."
            ));
        }

        return ResponseEntity.ok(windows);
    }

    /**
     * POST /api/model/train-smote
     *
     * Train the model with SMOTE class balancing applied to the Draw class.
     * This helps improve Draw prediction accuracy.
     */
    @PostMapping("/train-smote")
    public ResponseEntity<?> trainWithSMOTE() {
        try {
            log.info("SMOTE training requested via API");
            String report = modelTrainingService.trainWithSMOTE();

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "report", report
            ));
        } catch (Exception e) {
            log.error("SMOTE training failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            ));
        }
    }
}

