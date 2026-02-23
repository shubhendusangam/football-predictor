package com.app.footballprediction.service;

import com.app.common.model.MatchFeatures;
import com.app.common.model.SeasonTeamStats;
import com.app.common.repository.SeasonTeamStatsRepository;
import com.app.footballprediction.dto.PredictionExplanation;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for Elo-based prediction adjustments and explainability.
 *
 * Integrates Elo ratings into match outcome predictions:
 * - Fetches team Elo ratings from season_team_stats
 * - Calculates Elo difference impact on probabilities
 * - Detects potential upsets
 * - Generates human-readable explanations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EloPredictionService {

    private final SeasonTeamStatsRepository seasonTeamStatsRepository;

    // Elo adjustment thresholds
    private static final int ELO_HIGH_THRESHOLD = 100;
    private static final int ELO_MEDIUM_THRESHOLD = 50;
    private static final double ELO_HIGH_ADJUSTMENT = 0.08; // 8%
    private static final double ELO_MEDIUM_ADJUSTMENT = 0.04; // 4%

    // Upset detection threshold
    private static final double UPSET_PROBABILITY_THRESHOLD = 0.40; // 40%

    // Default Elo rating for teams without stats
    private static final double DEFAULT_ELO = 1500.0;

    // Home advantage base impact
    private static final double HOME_ADVANTAGE_IMPACT = 0.03; // 3%

    /**
     * Result of Elo-adjusted prediction calculation.
     */
    @Data
    @Builder
    public static class EloPredictionResult {
        private double homeWinProbability;
        private double drawProbability;
        private double awayWinProbability;
        private double homeElo;
        private double awayElo;
        private double eloDifference;
        private boolean upsetAlert;
        private String upsetTeam;
        private PredictionExplanation explanation;
    }

    /**
     * Calculate Elo-adjusted predictions with explainability.
     *
     * @param homeTeam Home team name
     * @param awayTeam Away team name
     * @param season Season ID
     * @param baseProbabilities Base probabilities [homeWin, draw, awayWin] from ML model
     * @param features Match features for form/trend analysis
     * @return EloPredictionResult with adjusted probabilities and explanation
     */
    public EloPredictionResult calculateEloPrediction(
            String homeTeam,
            String awayTeam,
            String season,
            double[] baseProbabilities,
            MatchFeatures features) {

        log.debug("Calculating Elo prediction for {} vs {} in season {}", homeTeam, awayTeam, season);

        // Fetch Elo ratings using batch query (single DB call)
        double[] elos = getTeamElosBatch(homeTeam, awayTeam, season);
        double homeElo = elos[0];
        double awayElo = elos[1];
        double eloDifference = homeElo - awayElo;

        log.debug("Elo ratings - Home: {}, Away: {}, Difference: {}", homeElo, awayElo, eloDifference);

        // Initialize adjusted probabilities
        double homeWinProb = baseProbabilities[0];
        double drawProb = baseProbabilities[1];
        double awayWinProb = baseProbabilities[2];

        // Calculate Elo impact
        double eloImpact = calculateEloImpact(eloDifference);

        // Apply Elo adjustment
        if (eloDifference > 0) {
            // Home team has higher Elo
            homeWinProb += eloImpact;
            drawProb -= eloImpact * 0.4;
            awayWinProb -= eloImpact * 0.6;
        } else if (eloDifference < 0) {
            // Away team has higher Elo
            awayWinProb += Math.abs(eloImpact);
            drawProb -= Math.abs(eloImpact) * 0.4;
            homeWinProb -= Math.abs(eloImpact) * 0.6;
        }

        // Calculate and apply form impact
        double formImpact = calculateFormImpact(features);
        if (formImpact > 0) {
            // Home team has better form
            homeWinProb += formImpact;
            drawProb -= formImpact * 0.3;
            awayWinProb -= formImpact * 0.7;
        } else if (formImpact < 0) {
            // Away team has better form
            awayWinProb += Math.abs(formImpact);
            drawProb -= Math.abs(formImpact) * 0.3;
            homeWinProb -= Math.abs(formImpact) * 0.7;
        }

        // Calculate and apply goal trend impact
        double goalTrendImpact = calculateGoalTrendImpact(features);
        if (goalTrendImpact > 0) {
            // Home team has goal trend advantage
            homeWinProb += goalTrendImpact;
            drawProb -= goalTrendImpact * 0.3;
            awayWinProb -= goalTrendImpact * 0.7;
        } else if (goalTrendImpact < 0) {
            // Away team has goal trend advantage
            awayWinProb += Math.abs(goalTrendImpact);
            drawProb -= Math.abs(goalTrendImpact) * 0.3;
            homeWinProb -= Math.abs(goalTrendImpact) * 0.7;
        }

        // Normalize probabilities to ensure they sum to 1.0
        double[] normalized = normalizeProbabilities(homeWinProb, drawProb, awayWinProb);
        homeWinProb = normalized[0];
        drawProb = normalized[1];
        awayWinProb = normalized[2];

        // Detect upset potential
        boolean upsetAlert = false;
        String upsetTeam = null;

        // Upset = Lower Elo team has > 40% win probability
        if (eloDifference > ELO_MEDIUM_THRESHOLD && awayWinProb > UPSET_PROBABILITY_THRESHOLD) {
            upsetAlert = true;
            upsetTeam = awayTeam;
            log.info("Upset alert: {} (lower Elo) has {}% win chance vs {}",
                    awayTeam, String.format("%.1f", awayWinProb * 100), homeTeam);
        } else if (eloDifference < -ELO_MEDIUM_THRESHOLD && homeWinProb > UPSET_PROBABILITY_THRESHOLD) {
            upsetAlert = true;
            upsetTeam = homeTeam;
            log.info("Upset alert: {} (lower Elo) has {}% win chance vs {}",
                    homeTeam, String.format("%.1f", homeWinProb * 100), awayTeam);
        }

        // Build explanation
        PredictionExplanation explanation = buildExplanation(
                eloImpact, formImpact, goalTrendImpact, eloDifference, homeTeam, awayTeam);

        return EloPredictionResult.builder()
                .homeWinProbability(homeWinProb)
                .drawProbability(drawProb)
                .awayWinProbability(awayWinProb)
                .homeElo(homeElo)
                .awayElo(awayElo)
                .eloDifference(eloDifference)
                .upsetAlert(upsetAlert)
                .upsetTeam(upsetTeam)
                .explanation(explanation)
                .build();
    }

    /**
     * Get Elo ratings for both teams in a single batch query (performance optimization).
     * Returns array [homeElo, awayElo].
     */
    private double[] getTeamElosBatch(String homeTeam, String awayTeam, String season) {
        if (season == null || season.isEmpty()) {
            log.warn("No season provided for Elo lookup, using defaults");
            return new double[]{DEFAULT_ELO, DEFAULT_ELO};
        }

        // Batch query for both teams
        List<String> teamNames = List.of(homeTeam.toLowerCase(), awayTeam.toLowerCase());
        List<SeasonTeamStats> stats = seasonTeamStatsRepository
                .findBySeasonIdAndTeamNames(season, teamNames);

        double homeElo = DEFAULT_ELO;
        double awayElo = DEFAULT_ELO;

        for (SeasonTeamStats stat : stats) {
            if (stat.getTeamName().equalsIgnoreCase(homeTeam) && stat.getEloRating() != null) {
                homeElo = stat.getEloRating();
            } else if (stat.getTeamName().equalsIgnoreCase(awayTeam) && stat.getEloRating() != null) {
                awayElo = stat.getEloRating();
            }
        }

        return new double[]{homeElo, awayElo};
    }

    /**
     * Get team Elo rating from season stats.
     */
    private double getTeamElo(String teamName, String season) {
        if (season == null || season.isEmpty()) {
            log.warn("No season provided for Elo lookup, using default");
            return DEFAULT_ELO;
        }

        Optional<SeasonTeamStats> stats = seasonTeamStatsRepository
                .findBySeasonIdAndTeamNameIgnoreCase(season, teamName);

        if (stats.isPresent() && stats.get().getEloRating() != null) {
            return stats.get().getEloRating();
        }

        log.debug("No Elo rating found for {} in season {}, using default", teamName, season);
        return DEFAULT_ELO;
    }

    /**
     * Calculate Elo impact based on rating difference.
     */
    private double calculateEloImpact(double eloDifference) {
        double absEloDiff = Math.abs(eloDifference);

        if (absEloDiff > ELO_HIGH_THRESHOLD) {
            return ELO_HIGH_ADJUSTMENT;
        } else if (absEloDiff > ELO_MEDIUM_THRESHOLD) {
            return ELO_MEDIUM_ADJUSTMENT;
        }

        // Linear scaling for smaller differences
        return (absEloDiff / ELO_HIGH_THRESHOLD) * ELO_MEDIUM_ADJUSTMENT;
    }

    /**
     * Calculate form impact from recent performance.
     */
    private double calculateFormImpact(MatchFeatures features) {
        if (features == null) return 0.0;

        double homeForm = features.getHomeFormPoints();
        double awayForm = features.getAwayFormPoints();

        // Form is normalized 0-1, convert to impact
        double formDiff = homeForm - awayForm;

        // Max impact: ±5%
        return formDiff * 0.05;
    }

    /**
     * Calculate goal trend impact from scoring/conceding patterns.
     */
    private double calculateGoalTrendImpact(MatchFeatures features) {
        if (features == null) return 0.0;

        // Home attacking strength vs Away defensive weakness
        double homeAttack = features.getHomeGoalsScoredAvg();
        double awayDefense = features.getAwayGoalsConcededAvg();

        // Away attacking strength vs Home defensive weakness
        double awayAttack = features.getAwayGoalsScoredAvg();
        double homeDefense = features.getHomeGoalsConcededAvg();

        // Calculate net advantage
        double homeAdvantage = (homeAttack + awayDefense) - (awayAttack + homeDefense);

        // Max impact: ±4%
        return Math.max(-0.04, Math.min(0.04, homeAdvantage * 0.02));
    }

    /**
     * Normalize probabilities to sum to exactly 1.0.
     * Ensures no negative probabilities.
     */
    private double[] normalizeProbabilities(double home, double draw, double away) {
        // Ensure no negative values
        home = Math.max(0.01, home);
        draw = Math.max(0.01, draw);
        away = Math.max(0.01, away);

        // Normalize to sum to 1.0
        double total = home + draw + away;

        return new double[] {
            home / total,
            draw / total,
            away / total
        };
    }

    /**
     * Build human-readable prediction explanation.
     */
    private PredictionExplanation buildExplanation(
            double eloImpact,
            double formImpact,
            double goalTrendImpact,
            double eloDifference,
            String homeTeam,
            String awayTeam) {

        // Format impacts as percentage strings
        String eloImpactStr = PredictionExplanation.formatImpact(eloImpact * 100);
        String formImpactStr = PredictionExplanation.formatImpact(formImpact * 100);
        String goalTrendStr = PredictionExplanation.formatImpact(goalTrendImpact * 100);
        String homeAdvStr = PredictionExplanation.formatImpact(HOME_ADVANTAGE_IMPACT * 100);

        // Build summary
        StringBuilder summary = new StringBuilder();

        if (Math.abs(eloDifference) > ELO_HIGH_THRESHOLD) {
            String strongerTeam = eloDifference > 0 ? homeTeam : awayTeam;
            summary.append(strongerTeam)
                   .append(" has a significant Elo advantage (")
                   .append(String.format("%.0f", Math.abs(eloDifference)))
                   .append(" points). ");
        } else if (Math.abs(eloDifference) > ELO_MEDIUM_THRESHOLD) {
            String strongerTeam = eloDifference > 0 ? homeTeam : awayTeam;
            summary.append(strongerTeam)
                   .append(" has a moderate Elo advantage. ");
        } else {
            summary.append("Teams are evenly matched by Elo rating. ");
        }

        if (Math.abs(formImpact) > 0.02) {
            String betterFormTeam = formImpact > 0 ? homeTeam : awayTeam;
            summary.append(betterFormTeam).append(" is in better recent form. ");
        }

        return PredictionExplanation.builder()
                .eloImpact(eloImpactStr)
                .formImpact(formImpactStr)
                .goalTrendImpact(goalTrendStr)
                .homeAdvantageImpact(homeAdvStr)
                .summary(summary.toString().trim())
                .build();
    }

    /**
     * Get Elo rating for a team in the current season.
     * Returns default if not found.
     */
    public double getTeamEloRating(String teamName, String season) {
        return getTeamElo(teamName, season);
    }

    /**
     * Check if a match has upset potential based on Elo and predicted probabilities.
     */
    public boolean isUpsetPotential(double homeElo, double awayElo,
                                     double homeWinProb, double awayWinProb) {
        double eloDiff = homeElo - awayElo;

        // Higher Elo team should have higher win probability
        // If lower Elo team has > 40% win chance, it's upset potential
        if (eloDiff > ELO_MEDIUM_THRESHOLD && awayWinProb > UPSET_PROBABILITY_THRESHOLD) {
            return true;
        }
        if (eloDiff < -ELO_MEDIUM_THRESHOLD && homeWinProb > UPSET_PROBABILITY_THRESHOLD) {
            return true;
        }

        return false;
    }
}

