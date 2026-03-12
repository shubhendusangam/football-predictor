package com.app.footballprediction.service;

import com.app.common.model.ModelAccuracy;
import com.app.common.model.ModelTrainingHistory;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.ModelAccuracyRepository;
import com.app.common.repository.ModelTrainingHistoryRepository;
import com.app.common.repository.PredictionEvaluationRepository;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for self-training model parameter adjustments based on prediction errors.
 *
 * Responsibilities:
 * - Analyze prediction errors to determine if retraining is needed
 * - Adjust model weights based on error patterns
 * - Trigger full model retraining when thresholds are exceeded
 * - Record training history for audit trail
 *
 * Training triggers:
 * - winnerAccuracy < 65%  → increase weight of teamFormScore
 * - goalErrorAverage > 1.2 → increase attackingStrengthWeight
 * - cardErrorAverage > 2   → increase refereeImpactWeight
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelSelfTrainingService {

    private final ModelTrainingService modelTrainingService;
    private final ModelAccuracyRepository accuracyRepository;
    private final ModelTrainingHistoryRepository trainingHistoryRepository;
    private final PredictionEvaluationRepository evaluationRepository;
    private final MatchRepository matchRepository;

    @Value("${retrain.cooldown.hours:24}")
    private int cooldownHours;

    @Value("${retrain.min.new.matches:5}")
    private int minNewMatchesForRetrain;

    // Thresholds for triggering weight adjustments
    private static final double WINNER_ACCURACY_THRESHOLD = 0.65;
    private static final double GOAL_ERROR_THRESHOLD = 1.2;
    private static final double CARD_ERROR_THRESHOLD = 2.0;
    private static final double CORNER_ERROR_THRESHOLD = 3.0;
    private static final double DRAW_RECALL_THRESHOLD = 0.30; // Draw recall is typically very low

    /**
     * Analyze prediction errors and determine if retraining is needed.
     * If thresholds are exceeded, trigger model retraining.
     *
     * @return training history record, or null if no retraining was needed
     */
    @Transactional
    public ModelTrainingHistory analyzeAndRetrain() {
        log.info("Analyzing prediction errors for potential model retraining...");

        // Check cooldown period
        if (isInCooldown()) {
            log.info("Model retraining skipped - still within cooldown period ({} hours)", cooldownHours);
            return null;
        }

        // Get latest global accuracy
        Optional<ModelAccuracy> latestAccuracyOpt = accuracyRepository
                .findTopByScopeAndScopeKeyOrderByCalculatedAtDesc("GLOBAL", null);

        if (latestAccuracyOpt.isEmpty()) {
            log.info("No accuracy metrics available yet, skipping retraining analysis");
            return null;
        }

        ModelAccuracy accuracy = latestAccuracyOpt.get();

        // Analyze errors and build adjustment recommendations
        Map<String, String> adjustments = analyzeErrors(accuracy);

        if (adjustments.isEmpty()) {
            log.info("Model performance within acceptable thresholds, no retraining needed");
            log.info("  Winner accuracy: {}% (threshold: {}%)",
                    String.format("%.1f", accuracy.getWinnerAccuracy() * 100),
                    String.format("%.1f", WINNER_ACCURACY_THRESHOLD * 100));
            log.info("  Goal error avg: {} (threshold: {})",
                    String.format("%.2f", accuracy.getGoalErrorAverage()),
                    String.format("%.2f", GOAL_ERROR_THRESHOLD));
            return null;
        }

        // Trigger retraining
        return executeRetraining(accuracy, adjustments, "AUTOMATED");
    }

    /**
     * Analyze error patterns and determine which weights need adjustment.
     */
    private Map<String, String> analyzeErrors(ModelAccuracy accuracy) {
        Map<String, String> adjustments = new LinkedHashMap<>();

        if (accuracy.getWinnerAccuracy() < WINNER_ACCURACY_THRESHOLD) {
            adjustments.put("teamFormScoreWeight",
                    String.format("INCREASE (winnerAccuracy=%.1f%% < %.1f%%)",
                            accuracy.getWinnerAccuracy() * 100, WINNER_ACCURACY_THRESHOLD * 100));
            log.warn("⚠ Winner accuracy {}% is below threshold {}% - recommending teamFormScore weight increase",
                    String.format("%.1f", accuracy.getWinnerAccuracy() * 100),
                    String.format("%.1f", WINNER_ACCURACY_THRESHOLD * 100));
        }

        if (accuracy.getGoalErrorAverage() > GOAL_ERROR_THRESHOLD) {
            adjustments.put("attackingStrengthWeight",
                    String.format("INCREASE (goalErrorAvg=%.2f > %.2f)",
                            accuracy.getGoalErrorAverage(), GOAL_ERROR_THRESHOLD));
            log.warn("⚠ Goal error average {} exceeds threshold {} - recommending attackingStrength weight increase",
                    String.format("%.2f", accuracy.getGoalErrorAverage()),
                    String.format("%.2f", GOAL_ERROR_THRESHOLD));
        }

        if (accuracy.getCardErrorAverage() != null && accuracy.getCardErrorAverage() > CARD_ERROR_THRESHOLD) {
            adjustments.put("refereeImpactWeight",
                    String.format("INCREASE (cardErrorAvg=%.2f > %.2f)",
                            accuracy.getCardErrorAverage(), CARD_ERROR_THRESHOLD));
            log.warn("⚠ Card error average {} exceeds threshold {} - recommending refereeImpact weight increase",
                    String.format("%.2f", accuracy.getCardErrorAverage()),
                    String.format("%.2f", CARD_ERROR_THRESHOLD));
        }

        if (accuracy.getCornerErrorAverage() != null && accuracy.getCornerErrorAverage() > CORNER_ERROR_THRESHOLD) {
            adjustments.put("cornerStrategyWeight",
                    String.format("INCREASE (cornerErrorAvg=%.2f > %.2f)",
                            accuracy.getCornerErrorAverage(), CORNER_ERROR_THRESHOLD));
            log.warn("⚠ Corner error average {} exceeds threshold {} - recommending cornerStrategy weight increase",
                    String.format("%.2f", accuracy.getCornerErrorAverage()),
                    String.format("%.2f", CORNER_ERROR_THRESHOLD));
        }

        // Per-class analysis: Check Draw prediction quality
        analyzeDrawPredictionQuality(adjustments);

        return adjustments;
    }

    /**
     * Analyze Draw prediction quality specifically.
     * Draws are typically the hardest to predict (~25% of outcomes but often <15% predicted correctly).
     */
    private void analyzeDrawPredictionQuality(Map<String, String> adjustments) {
        try {
            List<com.app.common.model.PredictionEvaluation> allEvals = evaluationRepository.findAll();
            if (allEvals.size() < 20) return; // Need sufficient data

            long actualDraws = allEvals.stream()
                    .filter(e -> "D".equals(e.getActualWinner())).count();
            long correctDrawPredictions = allEvals.stream()
                    .filter(e -> "D".equals(e.getActualWinner()) && e.getWinnerCorrect()).count();

            if (actualDraws > 0) {
                double drawRecall = (double) correctDrawPredictions / actualDraws;
                if (drawRecall < DRAW_RECALL_THRESHOLD) {
                    adjustments.put("drawPredictionWeight",
                            String.format("INCREASE_SMOTE (drawRecall=%.1f%% < %.1f%%, correctDraws=%d/%d)",
                                    drawRecall * 100, DRAW_RECALL_THRESHOLD * 100,
                                    correctDrawPredictions, actualDraws));
                    log.warn("⚠ Draw recall {}% is below threshold {}% - recommending SMOTE or cost-sensitive training",
                            String.format("%.1f", drawRecall * 100), String.format("%.1f", DRAW_RECALL_THRESHOLD * 100));
                }
            }
        } catch (Exception e) {
            log.debug("Could not analyze Draw prediction quality: {}", e.getMessage());
        }
    }

    /**
     * Execute model retraining and record the result.
     */
    private ModelTrainingHistory executeRetraining(ModelAccuracy previousAccuracy,
                                                    Map<String, String> adjustments,
                                                    String triggerReason) {
        long startTime = System.currentTimeMillis();
        log.info("🧠 Starting model retraining (trigger: {})", triggerReason);
        log.info("Weight adjustments to apply: {}", adjustments);

        ModelTrainingHistory history = ModelTrainingHistory.builder()
                .trainingTime(LocalDateTime.now())
                .triggerReason(triggerReason)
                .previousWinnerAccuracy(previousAccuracy.getWinnerAccuracy())
                .previousGoalError(previousAccuracy.getGoalErrorAverage())
                .previousCardError(previousAccuracy.getCardErrorAverage())
                .previousCornerError(previousAccuracy.getCornerErrorAverage())
                .weightAdjustments(adjustments.toString())
                .build();

        try {
            // Trigger full model retraining
            String report = modelTrainingService.trainAndEvaluate();
            long duration = System.currentTimeMillis() - startTime;

            history.setTrainingDurationMs(duration);
            history.setSuccess(true);
            history.setModelVersion("v" + System.currentTimeMillis());

            log.info("✅ Model retraining completed in {}ms", duration);
            log.info("Training report:\n{}", report);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            history.setTrainingDurationMs(duration);
            history.setSuccess(false);
            history.setErrorMessage(e.getMessage());

            log.error("❌ Model retraining failed after {}ms: {}", duration, e.getMessage(), e);
        }

        return trainingHistoryRepository.save(history);
    }

    /**
     * Force a manual retraining regardless of thresholds.
     */
    @Transactional
    public ModelTrainingHistory forceRetrain() {
        log.info("Manual model retraining triggered");

        Optional<ModelAccuracy> latestAccuracyOpt = accuracyRepository
                .findTopByScopeAndScopeKeyOrderByCalculatedAtDesc("GLOBAL", null);

        ModelAccuracy previousAccuracy = latestAccuracyOpt.orElse(
                ModelAccuracy.builder()
                        .winnerAccuracy(0.0)
                        .goalErrorAverage(0.0)
                        .cardErrorAverage(0.0)
                        .cornerErrorAverage(0.0)
                        .build());

        Map<String, String> adjustments = new LinkedHashMap<>();
        adjustments.put("reason", "MANUAL_TRIGGER");

        return executeRetraining(previousAccuracy, adjustments, "MANUAL");
    }

    /**
     * Check if we are within the cooldown period since the last successful training.
     */
    private boolean isInCooldown() {
        LocalDateTime cooldownSince = LocalDateTime.now().minusHours(cooldownHours);
        return trainingHistoryRepository.hasRecentSuccessfulTraining(cooldownSince);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Centralized Training + History Recording
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Train the model and record a {@link ModelTrainingHistory} entry.
     * <p>
     * This is the single entry-point that <strong>all</strong> callsites should use
     * (startup, scheduled CSV update, smart retrain, etc.) so that every training
     * event is audited in the {@code model_training_history} table.
     *
     * @param triggerReason free-form label stored in the history row
     *                      (e.g. STARTUP_INITIAL, SCHEDULED_CSV_UPDATE, SMART_RETRAIN)
     * @return the training report produced by {@link ModelTrainingService#trainAndEvaluate()}
     * @throws Exception re-thrown after recording a failed history entry
     */
    @Transactional
    public String trainWithHistory(String triggerReason) throws Exception {
        long startTime = System.currentTimeMillis();

        ModelTrainingHistory history = ModelTrainingHistory.builder()
                .trainingTime(LocalDateTime.now())
                .triggerReason(triggerReason)
                .matchesUsed((int) matchRepository.count())
                .build();

        // Capture previous accuracy if available
        accuracyRepository.findTopByScopeAndScopeKeyOrderByCalculatedAtDesc("GLOBAL", null)
                .ifPresent(acc -> history.setPreviousWinnerAccuracy(acc.getWinnerAccuracy()));

        try {
            String report = modelTrainingService.trainAndEvaluate();
            long duration = System.currentTimeMillis() - startTime;

            history.setTrainingDurationMs(duration);
            history.setSuccess(true);
            history.setModelVersion("v" + System.currentTimeMillis());

            // Capture new accuracy after training
            accuracyRepository.findTopByScopeAndScopeKeyOrderByCalculatedAtDesc("GLOBAL", null)
                    .ifPresent(acc -> history.setNewWinnerAccuracy(acc.getWinnerAccuracy()));

            trainingHistoryRepository.save(history);
            log.info("Model training recorded: trigger={}, duration={}ms", triggerReason, duration);

            return report;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            history.setTrainingDurationMs(duration);
            history.setSuccess(false);
            history.setErrorMessage(e.getMessage());
            trainingHistoryRepository.save(history);
            throw e; // re-throw so callers know it failed
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Query Methods for REST API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get all training history records.
     */
    public List<ModelTrainingHistory> getTrainingHistory() {
        return trainingHistoryRepository.findAllByOrderByTrainingTimeDesc();
    }

    /**
     * Get the latest successful training.
     */
    public Optional<ModelTrainingHistory> getLatestSuccessfulTraining() {
        return trainingHistoryRepository.findTopBySuccessTrueOrderByTrainingTimeDesc();
    }

    /**
     * Get model performance summary combining accuracy and training info.
     */
    public Map<String, Object> getPerformanceSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();

        // Latest global accuracy
        Optional<ModelAccuracy> accuracy = accuracyRepository
                .findTopByScopeAndScopeKeyOrderByCalculatedAtDesc("GLOBAL", null);
        if (accuracy.isPresent()) {
            ModelAccuracy a = accuracy.get();
            summary.put("winnerAccuracy", String.format("%.1f%%", a.getWinnerAccuracy() * 100));
            summary.put("scoreAccuracy", String.format("%.1f%%", a.getScoreAccuracy() * 100));
            summary.put("goalErrorAverage", String.format("%.2f", a.getGoalErrorAverage()));
            summary.put("cardErrorAverage", String.format("%.2f", a.getCardErrorAverage()));
            summary.put("cornerErrorAverage", String.format("%.2f", a.getCornerErrorAverage()));
            summary.put("totalEvaluations", a.getTotalPredictions());
            summary.put("lastCalculated", a.getCalculatedAt().toString());
        } else {
            summary.put("message", "No accuracy metrics available yet");
        }

        // Training info
        Optional<ModelTrainingHistory> lastTraining = trainingHistoryRepository
                .findTopBySuccessTrueOrderByTrainingTimeDesc();
        if (lastTraining.isPresent()) {
            ModelTrainingHistory t = lastTraining.get();
            summary.put("lastTrainingTime", t.getTrainingTime().toString());
            summary.put("lastTrainingDuration", t.getTrainingDurationMs() + "ms");
            summary.put("modelVersion", t.getModelVersion());
        }

        summary.put("totalTrainings", trainingHistoryRepository.countBySuccessTrue());
        summary.put("failedTrainings", trainingHistoryRepository.countBySuccessFalse());

        // Retraining thresholds
        Map<String, Object> thresholds = new LinkedHashMap<>();
        thresholds.put("winnerAccuracyThreshold", String.format("%.0f%%", WINNER_ACCURACY_THRESHOLD * 100));
        thresholds.put("goalErrorThreshold", GOAL_ERROR_THRESHOLD);
        thresholds.put("cardErrorThreshold", CARD_ERROR_THRESHOLD);
        thresholds.put("cornerErrorThreshold", CORNER_ERROR_THRESHOLD);
        thresholds.put("cooldownHours", cooldownHours);
        summary.put("retrainingThresholds", thresholds);

        return summary;
    }
}

