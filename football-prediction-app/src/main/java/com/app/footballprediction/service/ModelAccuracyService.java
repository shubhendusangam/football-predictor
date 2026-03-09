package com.app.footballprediction.service;

import com.app.common.model.ModelAccuracy;
import com.app.common.model.PredictionEvaluation;
import com.app.common.repository.ModelAccuracyRepository;
import com.app.common.repository.PredictionEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for recalculating and tracking model accuracy metrics.
 *
 * Responsibilities:
 * - Recalculate global model accuracy
 * - Recalculate per-league accuracy
 * - Recalculate per-team accuracy
 *
 * Metrics computed:
 * - winnerAccuracy = correctWinnerPredictions / totalPredictions
 * - scoreAccuracy = exactScorePredictions / totalPredictions
 * - goalErrorAverage = average(goalDifferenceError)
 * - cardErrorAverage = average(cardPredictionError)
 * - cornerErrorAverage = average(cornerPredictionError)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelAccuracyService {

    private final PredictionEvaluationRepository evaluationRepository;
    private final ModelAccuracyRepository accuracyRepository;

    /**
     * Recalculate all accuracy metrics: global, per-league, per-team, and sliding window.
     */
    @Transactional
    public void recalculateAllAccuracy() {
        log.debug("Starting model accuracy recalculation...");

        recalculateGlobalAccuracy();
        recalculatePerTeamAccuracy();
        recalculatePerLeagueAccuracy();
        recalculateSlidingWindowAccuracy();

        log.debug("Model accuracy recalculation complete");
    }

    /**
     * Recalculate global model accuracy across all evaluations.
     */
    @Transactional
    public ModelAccuracy recalculateGlobalAccuracy() {
        long total = evaluationRepository.countAllEvaluations();
        if (total == 0) {
            log.debug("No evaluations found, skipping global accuracy calculation");
            return null;
        }

        long correctWinner = evaluationRepository.countCorrectWinnerPredictions();
        long exactScore = evaluationRepository.countExactScorePredictions();
        Double avgGoalError = evaluationRepository.getAverageGoalDifferenceError();
        Double avgCardError = evaluationRepository.getAverageCardPredictionError();
        Double avgCornerError = evaluationRepository.getAverageCornerPredictionError();

        ModelAccuracy accuracy = ModelAccuracy.builder()
                .scope("GLOBAL")
                .scopeKey(null)
                .totalPredictions(total)
                .correctWinnerPredictions(correctWinner)
                .exactScorePredictions(exactScore)
                .winnerAccuracy((double) correctWinner / total)
                .scoreAccuracy((double) exactScore / total)
                .goalErrorAverage(avgGoalError != null ? avgGoalError : 0.0)
                .cardErrorAverage(avgCardError != null ? avgCardError : 0.0)
                .cornerErrorAverage(avgCornerError != null ? avgCornerError : 0.0)
                .calculatedAt(LocalDateTime.now())
                .build();

        accuracy = accuracyRepository.save(accuracy);

        log.debug("Global accuracy: winnerAccuracy={}%, scoreAccuracy={}%, goalErrorAvg={}",
                String.format("%.1f", accuracy.getWinnerAccuracy() * 100),
                String.format("%.1f", accuracy.getScoreAccuracy() * 100),
                String.format("%.2f", accuracy.getGoalErrorAverage()));

        return accuracy;
    }

    /**
     * Recalculate accuracy per team.
     */
    @Transactional
    public List<ModelAccuracy> recalculatePerTeamAccuracy() {
        List<PredictionEvaluation> allEvaluations = evaluationRepository.findAll();

        // Group by team (both home and away)
        Map<String, List<PredictionEvaluation>> teamEvaluations = new HashMap<>();
        for (PredictionEvaluation eval : allEvaluations) {
            teamEvaluations.computeIfAbsent(eval.getHomeTeam(), k -> new ArrayList<>()).add(eval);
            teamEvaluations.computeIfAbsent(eval.getAwayTeam(), k -> new ArrayList<>()).add(eval);
        }

        List<ModelAccuracy> results = new ArrayList<>();
        for (Map.Entry<String, List<PredictionEvaluation>> entry : teamEvaluations.entrySet()) {
            String team = entry.getKey();
            List<PredictionEvaluation> evals = entry.getValue();

            if (team == null || evals.isEmpty()) continue;

            ModelAccuracy accuracy = computeAccuracyFromEvaluations("TEAM", team, evals);
            results.add(accuracyRepository.save(accuracy));
        }

        log.debug("Recalculated accuracy for {} teams", results.size());
        return results;
    }

    /**
     * Recalculate accuracy per league/season.
     */
    @Transactional
    public List<ModelAccuracy> recalculatePerLeagueAccuracy() {
        List<String> seasons = evaluationRepository.findDistinctSeasons();
        List<ModelAccuracy> results = new ArrayList<>();

        for (String season : seasons) {
            if (season == null) continue;

            List<PredictionEvaluation> evals = evaluationRepository
                    .findBySeasonOrderByEvaluationTimeDesc(season);

            if (evals.isEmpty()) continue;

            ModelAccuracy accuracy = computeAccuracyFromEvaluations("LEAGUE", season, evals);
            accuracy.setSeason(season);
            results.add(accuracyRepository.save(accuracy));
        }

        log.debug("Recalculated accuracy for {} leagues/seasons", results.size());
        return results;
    }

    /**
     * Compute accuracy metrics from a list of evaluations.
     */
    private ModelAccuracy computeAccuracyFromEvaluations(String scope, String scopeKey,
                                                          List<PredictionEvaluation> evaluations) {
        long total = evaluations.size();
        long correctWinner = evaluations.stream().filter(PredictionEvaluation::getWinnerCorrect).count();
        long exactScore = evaluations.stream().filter(PredictionEvaluation::getScoreExact).count();

        double avgGoalError = evaluations.stream()
                .filter(e -> e.getGoalDifferenceError() != null)
                .mapToInt(PredictionEvaluation::getGoalDifferenceError)
                .average()
                .orElse(0.0);

        double avgCardError = evaluations.stream()
                .filter(e -> e.getCardPredictionError() != null)
                .mapToInt(PredictionEvaluation::getCardPredictionError)
                .average()
                .orElse(0.0);

        double avgCornerError = evaluations.stream()
                .filter(e -> e.getCornerPredictionError() != null)
                .mapToInt(PredictionEvaluation::getCornerPredictionError)
                .average()
                .orElse(0.0);

        return ModelAccuracy.builder()
                .scope(scope)
                .scopeKey(scopeKey)
                .totalPredictions(total)
                .correctWinnerPredictions(correctWinner)
                .exactScorePredictions(exactScore)
                .winnerAccuracy(total > 0 ? (double) correctWinner / total : 0.0)
                .scoreAccuracy(total > 0 ? (double) exactScore / total : 0.0)
                .goalErrorAverage(avgGoalError)
                .cardErrorAverage(avgCardError)
                .cornerErrorAverage(avgCornerError)
                .calculatedAt(LocalDateTime.now())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Query Methods for REST API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get the latest global accuracy metrics.
     */
    public Optional<ModelAccuracy> getLatestGlobalAccuracy() {
        return accuracyRepository.findTopByScopeAndScopeKeyOrderByCalculatedAtDesc("GLOBAL", null);
    }

    /**
     * Get the latest accuracy for a specific team.
     */
    public Optional<ModelAccuracy> getTeamAccuracy(String teamName) {
        return accuracyRepository.findTopByScopeAndScopeKeyOrderByCalculatedAtDesc("TEAM", teamName);
    }

    /**
     * Get all team accuracies (latest per team).
     */
    public List<ModelAccuracy> getAllTeamAccuracies() {
        return accuracyRepository.findLatestTeamAccuracies();
    }

    /**
     * Get all league/season accuracies.
     */
    public List<ModelAccuracy> getAllLeagueAccuracies() {
        return accuracyRepository.findLatestLeagueAccuracies();
    }

    /**
     * Get accuracy history for the global scope.
     */
    public List<ModelAccuracy> getGlobalAccuracyHistory() {
        return accuracyRepository.findByScopeAndScopeKeyOrderByCalculatedAtDesc("GLOBAL", null);
    }

    /**
     * Get error analysis summary.
     */
    public Map<String, Object> getErrorAnalysis() {
        Map<String, Object> analysis = new LinkedHashMap<>();

        long total = evaluationRepository.countAllEvaluations();
        analysis.put("totalEvaluations", total);

        if (total == 0) {
            analysis.put("message", "No evaluations available yet");
            return analysis;
        }

        long correctWinner = evaluationRepository.countCorrectWinnerPredictions();
        long exactScore = evaluationRepository.countExactScorePredictions();

        analysis.put("winnerAccuracy", String.format("%.1f%%", (double) correctWinner / total * 100));
        analysis.put("scoreAccuracy", String.format("%.1f%%", (double) exactScore / total * 100));

        Double avgGoalError = evaluationRepository.getAverageGoalDifferenceError();
        Double avgCardError = evaluationRepository.getAverageCardPredictionError();
        Double avgCornerError = evaluationRepository.getAverageCornerPredictionError();

        analysis.put("goalErrorAverage", avgGoalError != null ? String.format("%.2f", avgGoalError) : "N/A");
        analysis.put("cardErrorAverage", avgCardError != null ? String.format("%.2f", avgCardError) : "N/A");
        analysis.put("cornerErrorAverage", avgCornerError != null ? String.format("%.2f", avgCornerError) : "N/A");

        // Per-season breakdown
        List<String> seasons = evaluationRepository.findDistinctSeasons();
        List<Map<String, Object>> seasonBreakdown = new ArrayList<>();
        for (String season : seasons) {
            if (season == null) continue;
            long seasonTotal = evaluationRepository.countBySeason(season);
            long seasonCorrect = evaluationRepository.countCorrectWinnerBySeason(season);
            Double seasonGoalError = evaluationRepository.getAverageGoalErrorBySeason(season);

            Map<String, Object> seasonData = new LinkedHashMap<>();
            seasonData.put("season", season);
            seasonData.put("total", seasonTotal);
            seasonData.put("winnerAccuracy", seasonTotal > 0
                    ? String.format("%.1f%%", (double) seasonCorrect / seasonTotal * 100) : "N/A");
            seasonData.put("goalErrorAverage", seasonGoalError != null
                    ? String.format("%.2f", seasonGoalError) : "N/A");
            seasonBreakdown.add(seasonData);
        }
        analysis.put("seasonBreakdown", seasonBreakdown);

        // Per-outcome accuracy breakdown (H/D/A)
        List<PredictionEvaluation> allEvals = evaluationRepository.findAll();
        if (!allEvals.isEmpty()) {
            Map<String, Object> outcomeBreakdown = new LinkedHashMap<>();
            for (String outcome : List.of("H", "D", "A")) {
                long predicted = allEvals.stream()
                        .filter(e -> outcome.equals(e.getPredictedWinner())).count();
                long actual = allEvals.stream()
                        .filter(e -> outcome.equals(e.getActualWinner())).count();
                long correctForOutcome = allEvals.stream()
                        .filter(e -> outcome.equals(e.getActualWinner()) && e.getWinnerCorrect()).count();
                double precision = predicted > 0 ? (double) allEvals.stream()
                        .filter(e -> outcome.equals(e.getPredictedWinner()) && e.getWinnerCorrect()).count() / predicted : 0;
                double recall = actual > 0 ? (double) correctForOutcome / actual : 0;
                double f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0;

                Map<String, Object> outcomeData = new LinkedHashMap<>();
                outcomeData.put("predicted", predicted);
                outcomeData.put("actual", actual);
                outcomeData.put("precision", String.format("%.1f%%", precision * 100));
                outcomeData.put("recall", String.format("%.1f%%", recall * 100));
                outcomeData.put("f1Score", String.format("%.1f%%", f1 * 100));
                outcomeBreakdown.put(outcome, outcomeData);
            }
            analysis.put("perOutcomeBreakdown", outcomeBreakdown);
        }

        return analysis;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Sliding Window Accuracy
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Recalculate accuracy over sliding time windows (30, 60, 90 days).
     * This detects model drift much faster than all-time averages.
     */
    @Transactional
    public void recalculateSlidingWindowAccuracy() {
        int[] windowDays = {30, 60, 90};

        for (int days : windowDays) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
            List<PredictionEvaluation> windowEvals = evaluationRepository
                    .findByEvaluationTimeAfterOrderByEvaluationTimeDesc(cutoff);

            if (windowEvals.isEmpty()) {
                log.debug("No evaluations in last {} days, skipping window accuracy", days);
                continue;
            }

            String windowKey = "WINDOW_" + days;
            ModelAccuracy accuracy = computeAccuracyFromEvaluations("WINDOW", windowKey, windowEvals);
            accuracyRepository.save(accuracy);

            log.debug("Window {} days accuracy: winnerAccuracy={}%, evaluations={}",
                    days,
                    String.format("%.1f", accuracy.getWinnerAccuracy() * 100),
                    windowEvals.size());
        }
    }

    /**
     * Get sliding window accuracy for all windows.
     */
    public Map<String, Object> getSlidingWindowAccuracy() {
        Map<String, Object> windows = new LinkedHashMap<>();
        for (String windowKey : List.of("WINDOW_30", "WINDOW_60", "WINDOW_90")) {
            Optional<ModelAccuracy> windowAccuracy = accuracyRepository
                    .findTopByScopeAndScopeKeyOrderByCalculatedAtDesc("WINDOW", windowKey);
            if (windowAccuracy.isPresent()) {
                ModelAccuracy a = windowAccuracy.get();
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("totalPredictions", a.getTotalPredictions());
                data.put("winnerAccuracy", String.format("%.1f%%", a.getWinnerAccuracy() * 100));
                data.put("scoreAccuracy", String.format("%.1f%%", a.getScoreAccuracy() * 100));
                data.put("goalErrorAverage", String.format("%.2f", a.getGoalErrorAverage()));
                data.put("calculatedAt", a.getCalculatedAt().toString());
                windows.put(windowKey, data);
            }
        }
        return windows;
    }
}

