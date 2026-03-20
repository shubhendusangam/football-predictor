package com.app.modeltraining.service;

import com.app.common.model.Match;
import com.app.common.model.PoissonParameters;
import com.app.common.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dixon-Coles inspired Poisson regression model for predicting exact match scorelines.
 *
 * <p>Estimates per-team attack strength and defence weakness parameters from
 * historical match data, then uses independent Poisson distributions to
 * predict goal probabilities for each team.</p>
 *
 * <h3>Model Parameters (serialised)</h3>
 * <ul>
 *   <li>{@code attack[team]}   — team's attacking strength (goals scored relative to league average)</li>
 *   <li>{@code defence[team]}  — team's defensive weakness (goals conceded relative to league average)</li>
 *   <li>{@code homeAdvantage}  — multiplicative home-field advantage factor</li>
 *   <li>{@code leagueAvgGoals} — average goals per team per match in the dataset</li>
 * </ul>
 *
 * <h3>Prediction Formula</h3>
 * <pre>
 *   λ_home = attack[home] × defence[away] × leagueAvgGoals × homeAdvantage
 *   λ_away = attack[away] × defence[home] × leagueAvgGoals
 * </pre>
 *
 * <h3>Season Weighting</h3>
 * Uses the last 3 seasons with exponential decay weighting (most recent = 1.0,
 * previous = 0.6, oldest = 0.3) to capture current form while retaining
 * historical signal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PoissonModelTrainingService {

    private final MatchRepository matchRepository;

    @Value("${model.poisson.output.path:../data/poisson_score.model}")
    private String poissonModelPath;

    @Value("${model.poisson.max-goals:5}")
    private int maxGoals;

    @Value("${model.poisson.home-advantage-default:1.36}")
    private double defaultHomeAdvantage;

    /** Minimum matches a team must have played to receive its own parameters. */
    private static final int MIN_TEAM_MATCHES = 5;

    /** Season weights: most-recent → oldest (index 0 = current). */
    private static final double[] SEASON_WEIGHTS = {1.0, 0.6, 0.3};

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Train the Poisson score model from historical match data.
     *
     * @return human-readable training report
     */
    public String trainPoissonModel() {
        long start = System.currentTimeMillis();
        log.info("Starting Poisson (Dixon-Coles) model training...");

        List<String> seasons = matchRepository.findAllSeasons();
        if (seasons.isEmpty()) {
            throw new IllegalStateException("No seasons found in database");
        }

        // Take last 3 seasons (already DESC sorted)
        List<String> recentSeasons = seasons.subList(0, Math.min(3, seasons.size()));
        log.info("Training on seasons: {}", recentSeasons);

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
            log.info("  Season {} → {} matches (weight {})", season, seasonMatches.size(), weight);
        }

        if (weightedMatches.size() < 100) {
            throw new IllegalStateException("Insufficient data for Poisson model. Need ≥ 100 matches, found: " + weightedMatches.size());
        }

        // ── Compute parameters ──────────────────────────────────────
        PoissonParameters params = estimateParameters(weightedMatches);

        // ── Save model ──────────────────────────────────────────────
        saveModel(params);

        // ── Evaluate on most-recent season ──────────────────────────
        List<Match> testMatches = matchRepository.findBySeasonOrderByMatchDateDesc(recentSeasons.get(0))
                .stream()
                .filter(m -> m.getFullTimeHomeGoals() != null && m.getFullTimeAwayGoals() != null)
                .toList();
        String evalReport = evaluate(params, testMatches);

        long duration = System.currentTimeMillis() - start;
        String report = buildTrainingReport(params, weightedMatches.size(), duration, evalReport);
        log.info("\n{}", report);
        return report;
    }

    /**
     * Test the trained Poisson model against the most-recent season.
     *
     * @return human-readable test report
     */
    public String testPoissonModel() {
        PoissonParameters params = loadModel();
        String currentSeason = matchRepository.findCurrentSeason();
        if (currentSeason == null) {
            throw new IllegalStateException("No current season found");
        }

        List<Match> testMatches = matchRepository.findBySeasonOrderByMatchDateDesc(currentSeason)
                .stream()
                .filter(m -> m.getFullTimeHomeGoals() != null && m.getFullTimeAwayGoals() != null)
                .toList();

        if (testMatches.isEmpty()) {
            throw new IllegalStateException("No completed matches in current season " + currentSeason);
        }

        return evaluate(params, testMatches);
    }

    /**
     * Get model information.
     */
    public Map<String, Object> getModelInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        File file = new File(poissonModelPath);
        info.put("modelExists", file.exists());
        info.put("modelPath", poissonModelPath);
        if (file.exists()) {
            info.put("modelSize", file.length());
            info.put("lastModified", new Date(file.lastModified()));
            try {
                PoissonParameters params = loadModel();
                info.put("teamsCount", params.getAttack().size());
                info.put("homeAdvantage", String.format("%.4f", params.getHomeAdvantage()));
                info.put("leagueAvgGoals", String.format("%.4f", params.getLeagueAvgGoals()));
            } catch (Exception e) {
                info.put("loadError", e.getMessage());
            }
        }
        return info;
    }

    /**
     * Predict scoreline probabilities for a fixture.
     *
     * @return prediction result with probability matrix
     */
    public ScorePredictionResult predictScore(String homeTeam, String awayTeam) {
        PoissonParameters params = loadModel();
        return predictScore(params, homeTeam, awayTeam);
    }

    // ══════════════════════════════════════════════════════════════════════
    // PARAMETER ESTIMATION (Dixon-Coles simplified)
    // ══════════════════════════════════════════════════════════════════════

    PoissonParameters estimateParameters(List<WeightedMatch> matches) {
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

        log.info("League stats: avgHomeGoals={}, avgAwayGoals={}, homeAdvantage={}",
                fmt(avgHomeGoals), fmt(avgAwayGoals), fmt(homeAdvantage));

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
                // attack = goals scored per match / league average goals
                attack.put(team, Math.max(0.2, avgScored / leagueAvgGoals));
                // defence = goals conceded per match / league average goals
                defence.put(team, Math.max(0.2, avgConceded / leagueAvgGoals));
            } else {
                // Fallback: average team
                attack.put(team, 1.0);
                defence.put(team, 1.0);
            }
        }

        // Normalise attack and defence so the league average stays at 1.0
        normalise(attack);
        normalise(defence);

        PoissonParameters params = new PoissonParameters();
        params.setAttack(attack);
        params.setDefence(defence);
        params.setHomeAdvantage(homeAdvantage);
        params.setLeagueAvgGoals(leagueAvgGoals);
        params.setTrainedAt(new Date());
        params.setMaxGoals(maxGoals);

        log.info("Poisson model trained: {} teams, homeAdvantage={}, leagueAvg={}",
                attack.size(), fmt(homeAdvantage), fmt(leagueAvgGoals));

        return params;
    }

    private void normalise(Map<String, Double> map) {
        double sum = map.values().stream().mapToDouble(Double::doubleValue).sum();
        double avg = sum / map.size();
        if (avg > 0) {
            map.replaceAll((k, v) -> v / avg);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PREDICTION
    // ══════════════════════════════════════════════════════════════════════

    ScorePredictionResult predictScore(PoissonParameters params, String homeTeam, String awayTeam) {
        double attackHome = params.getAttack().getOrDefault(homeTeam, 1.0);
        double defenceHome = params.getDefence().getOrDefault(homeTeam, 1.0);
        double attackAway = params.getAttack().getOrDefault(awayTeam, 1.0);
        double defenceAway = params.getDefence().getOrDefault(awayTeam, 1.0);

        double lambdaHome = attackHome * defenceAway * params.getLeagueAvgGoals() * params.getHomeAdvantage();
        double lambdaAway = attackAway * defenceHome * params.getLeagueAvgGoals();

        // Clamp lambdas to reasonable range
        lambdaHome = Math.max(0.3, Math.min(5.0, lambdaHome));
        lambdaAway = Math.max(0.3, Math.min(5.0, lambdaAway));

        int mg = params.getMaxGoals();

        // Build score probability matrix
        double[][] matrix = new double[mg + 1][mg + 1];
        for (int i = 0; i <= mg; i++) {
            for (int j = 0; j <= mg; j++) {
                matrix[i][j] = poissonPMF(lambdaHome, i) * poissonPMF(lambdaAway, j);
            }
        }

        // Apply Dixon-Coles low-score correction (rho parameter)
        double rho = computeRho(lambdaHome, lambdaAway);
        applyDixonColesCorrection(matrix, lambdaHome, lambdaAway, rho);

        // Re-normalise matrix
        double totalProb = 0;
        for (int i = 0; i <= mg; i++)
            for (int j = 0; j <= mg; j++)
                totalProb += matrix[i][j];
        if (totalProb > 0) {
            for (int i = 0; i <= mg; i++)
                for (int j = 0; j <= mg; j++)
                    matrix[i][j] /= totalProb;
        }

        // Find most likely score and build sorted list
        List<ScoreProb> allScores = new ArrayList<>();
        for (int i = 0; i <= mg; i++) {
            for (int j = 0; j <= mg; j++) {
                allScores.add(new ScoreProb(i, j, matrix[i][j]));
            }
        }
        allScores.sort(Comparator.comparingDouble(ScoreProb::prob).reversed());

        String mostLikely = allScores.get(0).toScoreString();
        double mostLikelyProb = allScores.get(0).prob();

        List<Map<String, Double>> top3 = allScores.stream()
                .limit(3)
                .map(sp -> Map.of(sp.toScoreString(), round(sp.prob())))
                .collect(Collectors.toList());

        // Market probabilities
        double homeWinProb = 0, drawProb = 0, awayWinProb = 0;
        double over15 = 0, over25 = 0, over35 = 0;
        double btts = 0, csHome = 0, csAway = 0;

        for (int i = 0; i <= mg; i++) {
            for (int j = 0; j <= mg; j++) {
                double p = matrix[i][j];
                if (i > j) homeWinProb += p;
                else if (i == j) drawProb += p;
                else awayWinProb += p;

                if (i + j > 1) over15 += p;
                if (i + j > 2) over25 += p;
                if (i + j > 3) over35 += p;

                if (i > 0 && j > 0) btts += p;
                if (j == 0) csHome += p;
                if (i == 0) csAway += p;
            }
        }

        ScorePredictionResult result = new ScorePredictionResult();
        result.homeTeam = homeTeam;
        result.awayTeam = awayTeam;
        result.lambdaHome = round(lambdaHome);
        result.lambdaAway = round(lambdaAway);
        result.mostLikelyScore = mostLikely;
        result.mostLikelyScoreProb = round(mostLikelyProb);
        result.top3Scores = top3;
        result.scoreMatrix = buildMatrixMap(matrix, mg);
        result.probHomeWin = round(homeWinProb);
        result.probDraw = round(drawProb);
        result.probAwayWin = round(awayWinProb);
        result.over15Prob = round(over15);
        result.over25Prob = round(over25);
        result.over35Prob = round(over35);
        result.bttsProb = round(btts);
        result.cleanSheetHome = round(csHome);
        result.cleanSheetAway = round(csAway);
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════
    // POISSON MATH
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Poisson probability mass function: P(X=k) = (λ^k × e^-λ) / k!
     */
    static double poissonPMF(double lambda, int k) {
        if (k < 0) return 0.0;
        // Use log-space to avoid overflow for large k
        double logP = k * Math.log(lambda) - lambda - logFactorial(k);
        return Math.exp(logP);
    }

    private static double logFactorial(int n) {
        double result = 0;
        for (int i = 2; i <= n; i++) {
            result += Math.log(i);
        }
        return result;
    }

    /**
     * Dixon-Coles rho correction for low-scoring matches.
     * Rho accounts for the correlation between home/away goals at low scores.
     * Simplified estimation: rho ≈ -0.13 (empirical PL average).
     */
    private double computeRho(double lambdaHome, double lambdaAway) {
        // Empirical rho for Premier League data
        return -0.13;
    }

    /**
     * Apply Dixon-Coles correction to 0-0, 1-0, 0-1, and 1-1 cells.
     */
    private void applyDixonColesCorrection(double[][] matrix, double lambdaHome, double lambdaAway, double rho) {
        if (matrix.length < 2 || matrix[0].length < 2) return;

        double p00 = poissonPMF(lambdaHome, 0) * poissonPMF(lambdaAway, 0);
        double p10 = poissonPMF(lambdaHome, 1) * poissonPMF(lambdaAway, 0);
        double p01 = poissonPMF(lambdaHome, 0) * poissonPMF(lambdaAway, 1);
        double p11 = poissonPMF(lambdaHome, 1) * poissonPMF(lambdaAway, 1);

        // tau functions from Dixon-Coles
        matrix[0][0] = p00 * (1 - lambdaHome * lambdaAway * rho);
        matrix[1][0] = p10 * (1 + lambdaAway * rho);
        matrix[0][1] = p01 * (1 + lambdaHome * rho);
        matrix[1][1] = p11 * (1 - rho);

        // Floor at 0
        for (int i = 0; i < 2; i++)
            for (int j = 0; j < 2; j++)
                matrix[i][j] = Math.max(0, matrix[i][j]);
    }

    // ══════════════════════════════════════════════════════════════════════
    // EVALUATION
    // ══════════════════════════════════════════════════════════════════════

    private String evaluate(PoissonParameters params, List<Match> testMatches) {
        int exactScoreCorrect = 0;
        int winnerCorrect = 0;
        double totalGoalError = 0;
        int evaluated = 0;

        for (Match m : testMatches) {
            if (m.getFullTimeHomeGoals() == null || m.getFullTimeAwayGoals() == null) continue;

            ScorePredictionResult pred = predictScore(params, m.getHomeTeam(), m.getAwayTeam());

            int actualH = m.getFullTimeHomeGoals();
            int actualA = m.getFullTimeAwayGoals();
            String actualScore = actualH + "-" + actualA;

            // Exact score check
            if (pred.mostLikelyScore.equals(actualScore)) {
                exactScoreCorrect++;
            }

            // Winner check
            String predWinner = pred.probHomeWin > pred.probAwayWin
                    ? (pred.probHomeWin > pred.probDraw ? "H" : "D")
                    : (pred.probAwayWin > pred.probDraw ? "A" : "D");
            String actualWinner = actualH > actualA ? "H" : (actualH < actualA ? "A" : "D");
            if (predWinner.equals(actualWinner)) {
                winnerCorrect++;
            }

            // Goal error (MAE)
            totalGoalError += Math.abs(pred.lambdaHome - actualH) + Math.abs(pred.lambdaAway - actualA);
            evaluated++;
        }

        double exactScorePct = evaluated > 0 ? (100.0 * exactScoreCorrect / evaluated) : 0;
        double winnerPct = evaluated > 0 ? (100.0 * winnerCorrect / evaluated) : 0;
        double mae = evaluated > 0 ? (totalGoalError / evaluated) : 0;

        return String.format(
                "\n══════════════════════════════════════════%n" +
                "   POISSON SCORE MODEL — EVALUATION%n" +
                "══════════════════════════════════════════%n" +
                "  Matches evaluated : %d%n" +
                "  Exact Score %%     : %.1f%% (%d/%d)%n" +
                "  Winner Correct %%  : %.1f%% (%d/%d)%n" +
                "  Mean Absolute Err : %.3f goals%n" +
                "══════════════════════════════════════════%n",
                evaluated, exactScorePct, exactScoreCorrect, evaluated,
                winnerPct, winnerCorrect, evaluated, mae);
    }

    // ══════════════════════════════════════════════════════════════════════
    // SERIALISATION
    // ══════════════════════════════════════════════════════════════════════

    private void saveModel(PoissonParameters params) {
        File file = new File(poissonModelPath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            log.warn("Failed to create directory: {}", parent.getAbsolutePath());
        }
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(params);
            log.info("Poisson model saved to {}", poissonModelPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save Poisson model: " + e.getMessage(), e);
        }
    }

    PoissonParameters loadModel() {
        File file = new File(poissonModelPath);
        if (!file.exists()) {
            throw new IllegalStateException("Poisson model not found at " + poissonModelPath + ". Train it first.");
        }
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (PoissonParameters) ois.readObject();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Poisson model: " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private Map<String, Double> buildMatrixMap(double[][] matrix, int mg) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (int i = 0; i <= mg; i++) {
            for (int j = 0; j <= mg; j++) {
                map.put(i + "-" + j, round(matrix[i][j]));
            }
        }
        return map;
    }

    private String buildTrainingReport(PoissonParameters params, int matchCount, long durationMs, String evalReport) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n══════════════════════════════════════════\n");
        sb.append("   POISSON SCORE MODEL — TRAINING\n");
        sb.append("══════════════════════════════════════════\n");
        sb.append(String.format("  Matches used      : %d%n", matchCount));
        sb.append(String.format("  Teams estimated   : %d%n", params.getAttack().size()));
        sb.append(String.format("  Home advantage    : %.4f%n", params.getHomeAdvantage()));
        sb.append(String.format("  League avg goals  : %.4f%n", params.getLeagueAvgGoals()));
        sb.append(String.format("  Training time     : %d ms%n", durationMs));

        // Top 5 attack + defence
        sb.append("\n  Top 5 Attack Strength:\n");
        params.getAttack().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> sb.append(String.format("    %-20s %.4f%n", e.getKey(), e.getValue())));


        sb.append("\n  Top 5 Defensive Weakness (lower = better):\n");
        params.getDefence().entrySet().stream()
                .limit(5)
                .forEach(e -> sb.append(String.format("    %-20s %.4f%n", e.getKey(), e.getValue())));

        sb.append("══════════════════════════════════════════\n");
        sb.append(evalReport);
        return sb.toString();
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static String fmt(double value) {
        return String.format("%.4f", value);
    }

    // ══════════════════════════════════════════════════════════════════════
    // INNER DATA CLASSES
    // ══════════════════════════════════════════════════════════════════════

    /** Weighted match wrapper for multi-season training. */
    record WeightedMatch(Match match, double weight) {}


    /** Score probability tuple. */
    record ScoreProb(int homeGoals, int awayGoals, double prob) {
        String toScoreString() { return homeGoals + "-" + awayGoals; }
    }

    /** Result DTO for score prediction. */
    public static class ScorePredictionResult implements Serializable {
        private static final long serialVersionUID = 1L;
        public String homeTeam;
        public String awayTeam;
        public double lambdaHome;
        public double lambdaAway;
        public String mostLikelyScore;
        public double mostLikelyScoreProb;
        public List<Map<String, Double>> top3Scores;
        public Map<String, Double> scoreMatrix;
        public double probHomeWin;
        public double probDraw;
        public double probAwayWin;
        public double over15Prob;
        public double over25Prob;
        public double over35Prob;
        public double bttsProb;
        public double cleanSheetHome;
        public double cleanSheetAway;
    }
}

