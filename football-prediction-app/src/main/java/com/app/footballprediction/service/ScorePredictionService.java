package com.app.footballprediction.service;

import com.app.common.model.PoissonParameters;
import com.app.footballprediction.dto.ScorePredictionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for predicting exact match scorelines using a Poisson regression model.
 *
 * <p>Loads pre-trained Dixon-Coles parameters (attack/defence strengths per team,
 * home advantage factor, and league average goals) and computes a score probability
 * matrix for any given fixture.</p>
 *
 * <p>The model file is shared with the model-training-service via the {@code data/}
 * directory and is trained by {@code PoissonModelTrainingService}.</p>
 *
 * <p>Provides:</p>
 * <ul>
 *   <li>Most likely scoreline + probability</li>
 *   <li>Top-3 scorelines</li>
 *   <li>Over/Under 1.5/2.5/3.5 goals probabilities</li>
 *   <li>Both Teams To Score (BTTS) probability</li>
 *   <li>Clean sheet probabilities for each team</li>
 *   <li>Full 0-0 to 5-5 score probability matrix</li>
 * </ul>
 */
@Service
@Slf4j
public class ScorePredictionService {

    @Value("${model.poisson.output.path:./data/poisson_score.model}")
    private String poissonModelPath;

    @Value("${model.poisson.max-goals:5}")
    private int maxGoals;


    /** Cached model parameters — loaded lazily. */
    private volatile PoissonParameters cachedParams;
    private volatile long cachedParamsLoadedAt;

    /** Reload model if file is newer than cached version (check every 60s). */
    private static final long RELOAD_CHECK_INTERVAL_MS = 60_000;

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Predict scoreline for a match.
     *
     * @param homeTeam normalised home team name
     * @param awayTeam normalised away team name
     * @return ScorePredictionDTO with full score breakdown
     */
    @Cacheable(value = "scorePrediction", key = "#homeTeam + '-vs-' + #awayTeam")
    public ScorePredictionDTO predictScore(String homeTeam, String awayTeam) {
        log.info("Predicting score: {} vs {}", homeTeam, awayTeam);

        PoissonParameters params = getParameters();

        double attackHome = params.getAttack().getOrDefault(homeTeam, 1.0);
        double defenceHome = params.getDefence().getOrDefault(homeTeam, 1.0);
        double attackAway = params.getAttack().getOrDefault(awayTeam, 1.0);
        double defenceAway = params.getDefence().getOrDefault(awayTeam, 1.0);

        double lambdaHome = attackHome * defenceAway * params.getLeagueAvgGoals() * params.getHomeAdvantage();
        double lambdaAway = attackAway * defenceHome * params.getLeagueAvgGoals();

        // Clamp
        lambdaHome = Math.max(0.3, Math.min(5.0, lambdaHome));
        lambdaAway = Math.max(0.3, Math.min(5.0, lambdaAway));

        int mg = params.getMaxGoals() > 0 ? params.getMaxGoals() : maxGoals;

        // Build Poisson score matrix
        double[][] matrix = new double[mg + 1][mg + 1];
        for (int i = 0; i <= mg; i++) {
            for (int j = 0; j <= mg; j++) {
                matrix[i][j] = poissonPMF(lambdaHome, i) * poissonPMF(lambdaAway, j);
            }
        }

        // Apply Dixon-Coles low-score correction
        applyDixonColesCorrection(matrix, lambdaHome, lambdaAway);

        // Re-normalise
        double totalProb = 0;
        for (int i = 0; i <= mg; i++)
            for (int j = 0; j <= mg; j++)
                totalProb += matrix[i][j];
        if (totalProb > 0)
            for (int i = 0; i <= mg; i++)
                for (int j = 0; j <= mg; j++)
                    matrix[i][j] /= totalProb;

        // Collect all scores sorted by probability
        List<ScoreProb> allScores = new ArrayList<>();
        for (int i = 0; i <= mg; i++)
            for (int j = 0; j <= mg; j++)
                allScores.add(new ScoreProb(i, j, matrix[i][j]));
        allScores.sort(Comparator.comparingDouble(ScoreProb::prob).reversed());

        String mostLikely = allScores.get(0).toScoreString();
        double mostLikelyProb = allScores.get(0).prob();

        List<Map<String, Double>> top3 = allScores.stream()
                .limit(3)
                .map(sp -> Map.of(sp.toScoreString(), round(sp.prob())))
                .collect(Collectors.toList());

        // Aggregate market probabilities
        double homeWin = 0, draw = 0, awayWin = 0;
        double over15 = 0, over25 = 0, over35 = 0;
        double btts = 0, csHome = 0, csAway = 0;

        for (int i = 0; i <= mg; i++) {
            for (int j = 0; j <= mg; j++) {
                double p = matrix[i][j];
                if (i > j) homeWin += p;
                else if (i == j) draw += p;
                else awayWin += p;

                if (i + j > 1) over15 += p;
                if (i + j > 2) over25 += p;
                if (i + j > 3) over35 += p;

                if (i > 0 && j > 0) btts += p;
                if (j == 0) csHome += p;
                if (i == 0) csAway += p;
            }
        }

        // Build score matrix map
        Map<String, Double> scoreMatrix = new LinkedHashMap<>();
        for (int i = 0; i <= mg; i++)
            for (int j = 0; j <= mg; j++)
                scoreMatrix.put(i + "-" + j, round(matrix[i][j]));

        // Confidence based on whether teams are known
        boolean homeKnown = params.getAttack().containsKey(homeTeam);
        boolean awayKnown = params.getAttack().containsKey(awayTeam);
        String confidence = (homeKnown && awayKnown) ? "HIGH" : (homeKnown || awayKnown) ? "MEDIUM" : "LOW";

        ScorePredictionDTO dto = ScorePredictionDTO.builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .homeExpectedGoals(round(lambdaHome))
                .awayExpectedGoals(round(lambdaAway))
                .probHomeWin(round(homeWin))
                .probDraw(round(draw))
                .probAwayWin(round(awayWin))
                .scoreMatrix(scoreMatrix)
                .confidence(confidence)
                .scorePrediction(ScorePredictionDTO.ScorePrediction.builder()
                        .mostLikelyScore(mostLikely)
                        .probability(round(mostLikelyProb))
                        .top3Scores(top3)
                        .over15Prob(round(over15))
                        .over25Prob(round(over25))
                        .over35Prob(round(over35))
                        .bttsProb(round(btts))
                        .cleanSheetHome(round(csHome))
                        .cleanSheetAway(round(csAway))
                        .build())
                .build();

        log.info("Score prediction: {} vs {} → {} (p={}) λH={} λA={}",
                homeTeam, awayTeam, mostLikely, round(mostLikelyProb),
                round(lambdaHome), round(lambdaAway));

        return dto;
    }

    /**
     * Check whether the Poisson model is available.
     */
    public boolean isModelAvailable() {
        return new File(poissonModelPath).exists();
    }

    // ══════════════════════════════════════════════════════════════════════
    // POISSON MATH
    // ══════════════════════════════════════════════════════════════════════

    static double poissonPMF(double lambda, int k) {
        if (k < 0) return 0.0;
        double logP = k * Math.log(lambda) - lambda - logFactorial(k);
        return Math.exp(logP);
    }

    private static double logFactorial(int n) {
        double result = 0;
        for (int i = 2; i <= n; i++) result += Math.log(i);
        return result;
    }

    private void applyDixonColesCorrection(double[][] matrix, double lambdaHome, double lambdaAway) {
        if (matrix.length < 2 || matrix[0].length < 2) return;
        double rho = -0.13; // Empirical PL rho

        double p00 = poissonPMF(lambdaHome, 0) * poissonPMF(lambdaAway, 0);
        double p10 = poissonPMF(lambdaHome, 1) * poissonPMF(lambdaAway, 0);
        double p01 = poissonPMF(lambdaHome, 0) * poissonPMF(lambdaAway, 1);
        double p11 = poissonPMF(lambdaHome, 1) * poissonPMF(lambdaAway, 1);

        matrix[0][0] = Math.max(0, p00 * (1 - lambdaHome * lambdaAway * rho));
        matrix[1][0] = Math.max(0, p10 * (1 + lambdaAway * rho));
        matrix[0][1] = Math.max(0, p01 * (1 + lambdaHome * rho));
        matrix[1][1] = Math.max(0, p11 * (1 - rho));
    }

    // ══════════════════════════════════════════════════════════════════════
    // MODEL LOADING
    // ══════════════════════════════════════════════════════════════════════

    private PoissonParameters getParameters() {
        File file = new File(poissonModelPath);
        if (!file.exists()) {
            throw new IllegalStateException(
                    "Poisson score model not found. Train the model first via POST /api/training/train-poisson");
        }

        long now = System.currentTimeMillis();
        if (cachedParams != null && (now - cachedParamsLoadedAt) < RELOAD_CHECK_INTERVAL_MS) {
            return cachedParams;
        }

        // Check if file is newer
        if (cachedParams == null || file.lastModified() > cachedParamsLoadedAt) {
            synchronized (this) {
                if (cachedParams == null || file.lastModified() > cachedParamsLoadedAt) {
                    try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                        cachedParams = (PoissonParameters) ois.readObject();
                        cachedParamsLoadedAt = now;
                        log.info("Loaded Poisson model: {} teams, homeAdvantage={}",
                                cachedParams.getAttack().size(), cachedParams.getHomeAdvantage());
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to load Poisson model: " + e.getMessage(), e);
                    }
                }
            }
        }
        return cachedParams;
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private record ScoreProb(int homeGoals, int awayGoals, double prob) {
        String toScoreString() { return homeGoals + "-" + awayGoals; }
    }
}



