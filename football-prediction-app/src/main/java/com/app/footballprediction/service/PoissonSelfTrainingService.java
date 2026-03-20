package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.model.PoissonParameters;
import com.app.common.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

/**
 * Lightweight Poisson (Dixon-Coles) model trainer that runs inside the main app.
 *
 * <p>This service mirrors the core parameter-estimation logic from
 * {@code model-training-service}'s {@code PoissonModelTrainingService}
 * so that the main application can bootstrap the Poisson score model at
 * startup without requiring the training service to be running.</p>
 *
 * <p><b>When is this used?</b></p>
 * <ul>
 *   <li>First-ever startup — no {@code poisson_score.model} file exists yet</li>
 *   <li>Local development — only the main app is running</li>
 *   <li>After a data reset — model file was deleted</li>
 * </ul>
 *
 * <p>If the model file already exists on disk, this service is a no-op.
 * Subsequent retraining is handled by the model-training-service's
 * scheduler or REST API.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PoissonSelfTrainingService {

    private final MatchRepository matchRepository;

    @Value("${model.poisson.output.path:./data/poisson_score.model}")
    private String poissonModelPath;

    @Value("${model.poisson.max-goals:5}")
    private int maxGoals;

    /** Minimum matches a team must have played to receive its own parameters. */
    private static final int MIN_TEAM_MATCHES = 5;

    /** Season weights: most-recent → oldest (index 0 = current). */
    private static final double[] SEASON_WEIGHTS = {1.0, 0.6, 0.3};

    /** Minimum total matches to attempt training. */
    private static final int MIN_TOTAL_MATCHES = 100;

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * @return {@code true} if the Poisson model file exists on disk.
     */
    public boolean isModelAvailable() {
        return new File(poissonModelPath).exists();
    }

    /**
     * Train the Poisson model if it does not already exist.
     *
     * @return a short status message for the startup log
     */
    public String trainIfMissing() {
        if (isModelAvailable()) {
            log.info("   ✓ Poisson score model already exists at {}", poissonModelPath);
            return "ALREADY_EXISTS";
        }

        log.info("   ⏳ Poisson score model not found — training initial model...");
        long start = System.currentTimeMillis();

        try {
            PoissonParameters params = estimateFromDatabase();
            saveModel(params);
            long duration = System.currentTimeMillis() - start;
            log.info("   ✓ Poisson score model trained and saved in {}ms ({} teams)",
                    duration, params.getAttack().size());
            return "TRAINED";
        } catch (IllegalStateException e) {
            log.warn("   ⚠ Poisson model training skipped: {}", e.getMessage());
            return "SKIPPED: " + e.getMessage();
        } catch (Exception e) {
            log.error("   ✗ Poisson model training failed", e);
            return "FAILED: " + e.getMessage();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PARAMETER ESTIMATION  (mirrors PoissonModelTrainingService logic)
    // ══════════════════════════════════════════════════════════════════════

    private PoissonParameters estimateFromDatabase() {
        List<String> seasons = matchRepository.findAllSeasons();
        if (seasons.isEmpty()) {
            throw new IllegalStateException("No seasons found in database");
        }

        // Take last 3 seasons (list is already DESC sorted)
        List<String> recentSeasons = seasons.subList(0, Math.min(3, seasons.size()));
        log.debug("Poisson training on seasons: {}", recentSeasons);

        // Collect weighted match data
        List<WeightedMatch> weightedMatches = new ArrayList<>();
        for (int i = 0; i < recentSeasons.size(); i++) {
            double weight = SEASON_WEIGHTS[i];
            String season = recentSeasons.get(i);
            List<Match> seasonMatches = matchRepository.findBySeasonOrderByMatchDateDesc(season)
                    .stream()
                    .filter(m -> m.getFullTimeHomeGoals() != null && m.getFullTimeAwayGoals() != null)
                    .toList();
            for (Match m : seasonMatches) {
                weightedMatches.add(new WeightedMatch(m, weight));
            }
            log.debug("  Season {} → {} matches (weight {})", season, seasonMatches.size(), weight);
        }

        if (weightedMatches.size() < MIN_TOTAL_MATCHES) {
            throw new IllegalStateException(
                    "Insufficient data for Poisson model. Need ≥ " + MIN_TOTAL_MATCHES
                            + " matches, found: " + weightedMatches.size());
        }

        return estimateParameters(weightedMatches);
    }

    private PoissonParameters estimateParameters(List<WeightedMatch> matches) {
        // Collect all teams
        Set<String> teamSet = new LinkedHashSet<>();
        for (WeightedMatch wm : matches) {
            teamSet.add(wm.match.getHomeTeam());
            teamSet.add(wm.match.getAwayTeam());
        }

        // Compute weighted league averages
        double totalWeightedHomeGoals = 0, totalWeightedAwayGoals = 0, totalWeight = 0;
        for (WeightedMatch wm : matches) {
            totalWeightedHomeGoals += wm.match.getFullTimeHomeGoals() * wm.weight;
            totalWeightedAwayGoals += wm.match.getFullTimeAwayGoals() * wm.weight;
            totalWeight += wm.weight;
        }

        double avgHomeGoals = totalWeightedHomeGoals / totalWeight;
        double avgAwayGoals = totalWeightedAwayGoals / totalWeight;
        double leagueAvgGoals = (avgHomeGoals + avgAwayGoals) / 2.0;
        double homeAdvantage = avgHomeGoals / avgAwayGoals;

        // Per-team weighted attack and defence rates
        Map<String, Double> attack = new HashMap<>();
        Map<String, Double> defence = new HashMap<>();

        for (String team : teamSet) {
            double weightedGoalsScored = 0, weightedGoalsConceded = 0;
            double teamWeight = 0;
            int matchCount = 0;

            for (WeightedMatch wm : matches) {
                Match m = wm.match;
                if (team.equals(m.getHomeTeam())) {
                    weightedGoalsScored += m.getFullTimeHomeGoals() * wm.weight;
                    weightedGoalsConceded += m.getFullTimeAwayGoals() * wm.weight;
                    teamWeight += wm.weight;
                    matchCount++;
                } else if (team.equals(m.getAwayTeam())) {
                    weightedGoalsScored += m.getFullTimeAwayGoals() * wm.weight;
                    weightedGoalsConceded += m.getFullTimeHomeGoals() * wm.weight;
                    teamWeight += wm.weight;
                    matchCount++;
                }
            }

            if (matchCount >= MIN_TEAM_MATCHES && teamWeight > 0) {
                double avgScored = weightedGoalsScored / teamWeight;
                double avgConceded = weightedGoalsConceded / teamWeight;
                attack.put(team, Math.max(0.2, avgScored / leagueAvgGoals));
                defence.put(team, Math.max(0.2, avgConceded / leagueAvgGoals));
            } else {
                attack.put(team, 1.0);
                defence.put(team, 1.0);
            }
        }

        // Normalise so league average stays at 1.0
        normalise(attack);
        normalise(defence);

        PoissonParameters params = new PoissonParameters();
        params.setAttack(attack);
        params.setDefence(defence);
        params.setHomeAdvantage(homeAdvantage);
        params.setLeagueAvgGoals(leagueAvgGoals);
        params.setTrainedAt(new Date());
        params.setMaxGoals(maxGoals);
        return params;
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private void normalise(Map<String, Double> map) {
        double sum = map.values().stream().mapToDouble(Double::doubleValue).sum();
        double avg = sum / map.size();
        if (avg > 0) {
            map.replaceAll((k, v) -> v / avg);
        }
    }

    private void saveModel(PoissonParameters params) {
        File file = new File(poissonModelPath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            log.warn("Failed to create directory: {}", parent.getAbsolutePath());
        }
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(params);
            log.info("Poisson model saved to {}", file.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save Poisson model: " + e.getMessage(), e);
        }
    }

    /** Weighted match wrapper. */
    private record WeightedMatch(Match match, double weight) {}
}

