package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.CornerPredictionDTO;
import com.app.footballprediction.dto.CornerStatsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for calculating corner kick statistics and predictions.
 *
 * <p>Provides comprehensive corner analytics including:</p>
 * <ul>
 *   <li>Historical corner statistics per team (home/away split)</li>
 *   <li>Corner dominance calculations</li>
 *   <li>Match corner predictions with probability distributions</li>
 *   <li>Over/under probability calculations</li>
 * </ul>
 *
 * <p><strong>Data Sources:</strong></p>
 * <ul>
 *   <li>HC (Home Corners) - corners won by home team</li>
 *   <li>AC (Away Corners) - corners won by away team</li>
 * </ul>
 *
 * <p><strong>Calculation Methodology:</strong></p>
 * <ul>
 *   <li>Uses full season matches for the current season</li>
 *   <li>Matches sorted by date descending</li>
 *   <li>Applies exponential decay weighting for recency</li>
 *   <li>Uses normal distribution for probability estimation</li>
 * </ul>
 *
 * @author Football Forecaster Team
 * @version 1.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CornerStatsService {

    private final MatchRepository matchRepository;
    private final TeamValidationService teamValidationService;

    /**
     * Minimum matches required for confident statistics.
     * Below this threshold, results should be treated with caution.
     */
    private static final int MIN_MATCHES_FOR_CONFIDENCE = 5;

    /**
     * High confidence threshold for predictions.
     * Teams with this many matches get maximum confidence.
     */
    private static final int HIGH_CONFIDENCE_MATCHES = 20;

    /**
     * Decay factor for weighted average calculation.
     * Higher values give more weight to recent matches.
     * 0.1 = gradual decay, 0.2 = moderate decay
     */
    private static final double RECENCY_DECAY_FACTOR = 0.15;

    /**
     * Standard deviation factor for corner distribution.
     * Based on empirical Premier League data.
     */
    private static final double CORNER_STD_DEV_FACTOR = 2.5;

    /**
     * Home advantage factor for corner prediction.
     * Home teams typically win 10-15% more corners.
     */
    private static final double HOME_ADVANTAGE_FACTOR = 1.10;

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Calculate corner statistics for a specific team.
     *
     * <p>Retrieves all matches from the current season and calculates:</p>
     * <ul>
     *   <li>Average corners won/conceded</li>
     *   <li>Corner dominance ratio</li>
     *   <li>Success rate (win rate when having more corners)</li>
     *   <li>Weighted average with recency factor</li>
     * </ul>
     *
     * @param teamName Name of the team (case-insensitive matching supported)
     * @param isHome   True for home matches only, false for away matches only, null for all
     * @return CornerStatsDTO containing corner statistics
     * @throws IllegalArgumentException if team name is empty or no matches found
     */
    @Cacheable(value = "cornerStats", key = "#teamName + '-' + #isHome")
    public CornerStatsDTO calculateCornerStats(String teamName, Boolean isHome) {
        log.info("Calculating corner stats for team: '{}', isHome: {}", teamName, isHome);

        // Validate input
        validateTeamName(teamName);

        LocalDate beforeDate = LocalDate.now().plusDays(1);
        String currentSeason = matchRepository.findCurrentSeason();

        if (currentSeason == null) {
            log.warn("No current season found in database");
            throw new IllegalStateException("No season data available. Please ingest match data first.");
        }

        log.debug("Using current season: {}", currentSeason);

        String resolvedTeam = resolveTeamName(teamName, beforeDate, currentSeason);

        // Fetch full season matches based on home/away filter
        List<Match> matches = fetchSeasonMatchesForTeam(resolvedTeam, isHome, currentSeason, beforeDate);

        if (matches.isEmpty()) {
            log.warn("No matches found for team: {} in season: {}", resolvedTeam, currentSeason);
            throw new IllegalArgumentException(
                    "No corner data available for '" + resolvedTeam + "' in season " + currentSeason + ". " +
                    "Corner statistics require historical match data with corner information.");
        }

        log.debug("Processing {} full season matches for corner statistics", matches.size());

        // Calculate statistics using all season matches
        return buildCornerStats(resolvedTeam, matches, isHome, currentSeason);
    }

    /**
     * Predict corner statistics for an upcoming match.
     *
     * <p>Uses both teams' historical corner data to predict:</p>
     * <ul>
     *   <li>Expected corners for each team</li>
     *   <li>Expected total corners</li>
     *   <li>Over/under probabilities (9.5, 10.5, 11.5)</li>
     * </ul>
     *
     * @param homeTeam Name of the home team
     * @param awayTeam Name of the away team
     * @return CornerPredictionDTO containing match predictions
     * @throws IllegalArgumentException if team names are invalid or same
     */
    @Cacheable(value = "cornerPrediction", key = "#homeTeam + '-vs-' + #awayTeam")
    public CornerPredictionDTO predictMatchCorners(String homeTeam, String awayTeam) {
        log.info("Predicting corners for match: {} vs {}", homeTeam, awayTeam);

        // Validate inputs
        validateTeamName(homeTeam);
        validateTeamName(awayTeam);

        if (homeTeam.trim().equalsIgnoreCase(awayTeam.trim())) {
            throw new IllegalArgumentException("Home and away teams cannot be the same");
        }

        // Get corner stats for both teams (uses current season internally)
        CornerStatsDTO homeStats = calculateCornerStats(homeTeam.trim(), true);
        CornerStatsDTO awayStats = calculateCornerStats(awayTeam.trim(), false);

        // Build prediction using resolved team names from stats
        return buildCornerPrediction(homeStats.getTeamName(), awayStats.getTeamName(), homeStats, awayStats);
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRIVATE CALCULATION METHODS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Build corner statistics DTO from match data.
     *
     * @param teamName       Resolved team name
     * @param matches        List of season matches (sorted by date DESC)
     * @param isHome         Home/away filter
     * @param season         Season identifier for logging
     * @return CornerStatsDTO with calculated statistics
     */
    private CornerStatsDTO buildCornerStats(String teamName, List<Match> matches, Boolean isHome, String season) {
        int totalCornersWon = 0;
        int totalCornersAgainst = 0;
        int winsWithCornerDominance = 0;
        int matchesWithCornerDominance = 0;
        int matchesWithCornerData = 0;

        double weightedCornerSum = 0.0;
        double weightSum = 0.0;

        for (int i = 0; i < matches.size(); i++) {
            Match match = matches.get(i);

            // Get corner data based on team's role in the match
            Integer cornersWon = getCornersWon(match, teamName);
            Integer cornersAgainst = getCornersAgainst(match, teamName);

            // Skip matches without corner data
            if (cornersWon == null || cornersAgainst == null) {
                log.debug("Skipping match {} - missing corner data", match.getId());
                continue;
            }

            matchesWithCornerData++;

            // Accumulate totals
            totalCornersWon += cornersWon;
            totalCornersAgainst += cornersAgainst;

            // Calculate weighted average with exponential decay
            double weight = calculateRecencyWeight(i);
            weightedCornerSum += cornersWon * weight;
            weightSum += weight;

            // Track success rate (wins when having more corners)
            if (cornersWon > cornersAgainst) {
                matchesWithCornerDominance++;
                if (isWinForTeam(match, teamName)) {
                    winsWithCornerDominance++;
                }
            }
        }

        // Handle case with no valid corner data
        if (matchesWithCornerData == 0) {
            log.warn("No corner data available for team: {} in season: {}", teamName, season);
            return buildEmptyCornerStats(teamName, isHome);
        }

        // Calculate averages with null-safety
        double avgCornersWon = safeAverage(totalCornersWon, matchesWithCornerData);
        double avgCornersAgainst = safeAverage(totalCornersAgainst, matchesWithCornerData);
        double cornerDominance = safeDivide(totalCornersWon, totalCornersWon + totalCornersAgainst);
        double successRate = safeDivide(winsWithCornerDominance, matchesWithCornerDominance);
        double weightedAvg = safeDivide(weightedCornerSum, weightSum);

        log.debug("Corner stats for {} (season {}): matches={}, avgWon={}, avgAgainst={}, dominance={}",
                  teamName, season, matchesWithCornerData, avgCornersWon, avgCornersAgainst, cornerDominance);

        // Validate results
        validateCornerStats(avgCornersWon, avgCornersAgainst, cornerDominance, successRate, teamName);

        return CornerStatsDTO.builder()
                .teamName(teamName)
                .isHome(isHome)
                .avgCornersWon(roundToTwoDecimals(avgCornersWon))
                .avgCornersAgainst(roundToTwoDecimals(avgCornersAgainst))
                .cornerDominance(roundToThreeDecimals(cornerDominance))
                .successRate(roundToThreeDecimals(successRate))
                .matchesAnalyzed(matchesWithCornerData)
                .totalCornersWon(totalCornersWon)
                .totalCornersAgainst(totalCornersAgainst)
                .weightedAvgCorners(roundToTwoDecimals(weightedAvg))
                .build();
    }

    /**
     * Build corner prediction DTO from team statistics.
     *
     * @param homeTeam  Home team name
     * @param awayTeam  Away team name
     * @param homeStats Home team corner stats
     * @param awayStats Away team corner stats
     * @return CornerPredictionDTO with match predictions
     */
    private CornerPredictionDTO buildCornerPrediction(String homeTeam, String awayTeam,
                                                       CornerStatsDTO homeStats, CornerStatsDTO awayStats) {
        // Calculate expected corners with home advantage
        double expectedHomeCorners = calculateExpectedCorners(
                homeStats.getWeightedAvgCorners(),
                awayStats.getAvgCornersAgainst(),
                true
        );

        double expectedAwayCorners = calculateExpectedCorners(
                awayStats.getWeightedAvgCorners(),
                homeStats.getAvgCornersAgainst(),
                false
        );

        // Total corners MUST equal home + away
        double expectedTotalCorners = expectedHomeCorners + expectedAwayCorners;

        // Calculate standard deviation for the match
        double stdDev = calculateMatchStdDev(homeStats.getMatchesAnalyzed(), awayStats.getMatchesAnalyzed());

        // Calculate over/under probabilities using normal distribution
        double probOver9_5 = calculateOverProbability(expectedTotalCorners, 9.5, stdDev);
        double probOver10_5 = calculateOverProbability(expectedTotalCorners, 10.5, stdDev);
        double probOver11_5 = calculateOverProbability(expectedTotalCorners, 11.5, stdDev);

        // Calculate confidence based on sample sizes
        double confidence = calculatePredictionConfidence(
                homeStats.getMatchesAnalyzed(),
                awayStats.getMatchesAnalyzed()
        );

        log.info("Corner prediction: {} vs {} - Expected total: {}, Confidence: {}",
                 homeTeam, awayTeam, expectedTotalCorners, confidence);

        // Validate prediction
        validatePrediction(expectedTotalCorners, expectedHomeCorners, expectedAwayCorners,
                           probOver9_5, probOver10_5, probOver11_5);

        return CornerPredictionDTO.builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .expectedTotalCorners(roundToTwoDecimals(expectedTotalCorners))
                .expectedHomeCorners(roundToTwoDecimals(expectedHomeCorners))
                .expectedAwayCorners(roundToTwoDecimals(expectedAwayCorners))
                .probOver9_5(roundToThreeDecimals(probOver9_5))
                .probOver10_5(roundToThreeDecimals(probOver10_5))
                .probOver11_5(roundToThreeDecimals(probOver11_5))
                .confidence(roundToThreeDecimals(confidence))
                .homeWeightedCorners(homeStats.getWeightedAvgCorners())
                .awayWeightedCorners(awayStats.getWeightedAvgCorners())
                .homeMatchesAnalyzed(homeStats.getMatchesAnalyzed())
                .awayMatchesAnalyzed(awayStats.getMatchesAnalyzed())
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPER METHODS - Corner Data Extraction
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Get corners won by the specified team in a match.
     *
     * @param match    Match entity
     * @param teamName Team name to check
     * @return Corners won, or null if data unavailable
     */
    private Integer getCornersWon(Match match, String teamName) {
        if (match == null || teamName == null) return null;

        if (isHomeTeam(match, teamName)) {
            return match.getHomeCorners(); // HC
        } else if (isAwayTeam(match, teamName)) {
            return match.getAwayCorners(); // AC
        }
        return null;
    }

    /**
     * Get corners conceded by the specified team in a match.
     *
     * @param match    Match entity
     * @param teamName Team name to check
     * @return Corners conceded, or null if data unavailable
     */
    private Integer getCornersAgainst(Match match, String teamName) {
        if (match == null || teamName == null) return null;

        if (isHomeTeam(match, teamName)) {
            return match.getAwayCorners(); // AC = corners against home team
        } else if (isAwayTeam(match, teamName)) {
            return match.getHomeCorners(); // HC = corners against away team
        }
        return null;
    }

    /**
     * Check if the specified team won the match.
     *
     * @param match    Match entity
     * @param teamName Team name to check
     * @return true if team won, false otherwise
     */
    private boolean isWinForTeam(Match match, String teamName) {
        if (match == null || match.getFullTimeResult() == null) return false;

        String result = match.getFullTimeResult();
        if (isHomeTeam(match, teamName)) {
            return "H".equals(result);
        } else if (isAwayTeam(match, teamName)) {
            return "A".equals(result);
        }
        return false;
    }

    /**
     * Check if team played as home team.
     */
    private boolean isHomeTeam(Match match, String teamName) {
        return match.getHomeTeam() != null &&
               match.getHomeTeam().trim().equalsIgnoreCase(teamName.trim());
    }

    /**
     * Check if team played as away team.
     */
    private boolean isAwayTeam(Match match, String teamName) {
        return match.getAwayTeam() != null &&
               match.getAwayTeam().trim().equalsIgnoreCase(teamName.trim());
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPER METHODS - Statistical Calculations
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Calculate recency weight using exponential decay.
     * More recent matches get higher weight.
     *
     * @param index Match index (0 = most recent)
     * @return Weight value (0 to 1)
     */
    private double calculateRecencyWeight(int index) {
        return Math.exp(-RECENCY_DECAY_FACTOR * index);
    }

    /**
     * Calculate expected corners for a team in a match.
     *
     * @param teamCornerAvg      Team's weighted corner average
     * @param opponentConceded   Opponent's average corners conceded
     * @param isHome             True if team is playing at home
     * @return Expected corners
     */
    private double calculateExpectedCorners(double teamCornerAvg, double opponentConceded, boolean isHome) {
        // Base expectation: average of team's scoring and opponent's conceding
        double baseExpected = (teamCornerAvg + opponentConceded) / 2.0;

        // Apply home advantage
        if (isHome) {
            baseExpected *= HOME_ADVANTAGE_FACTOR;
        }

        // Ensure reasonable bounds (1 to 12 corners)
        return Math.max(1.0, Math.min(12.0, baseExpected));
    }

    /**
     * Calculate standard deviation for match corner prediction.
     * Based on sample sizes and empirical data.
     *
     * @param homeSampleSize Home team's sample size
     * @param awaySampleSize Away team's sample size
     * @return Standard deviation
     */
    private double calculateMatchStdDev(int homeSampleSize, int awaySampleSize) {
        // Base std dev adjusted by confidence from sample sizes
        double avgSampleSize = (homeSampleSize + awaySampleSize) / 2.0;

        // Higher sample size = lower std dev (more confidence)
        double confidenceMultiplier = Math.max(0.7, 1.0 - (avgSampleSize / 50.0));

        return CORNER_STD_DEV_FACTOR * confidenceMultiplier;
    }

    /**
     * Calculate probability of total corners being over a threshold.
     * Uses cumulative normal distribution approximation.
     *
     * @param expected Expected total corners
     * @param threshold Over threshold (e.g., 9.5, 10.5)
     * @param stdDev   Standard deviation
     * @return Probability (0 to 1)
     */
    private double calculateOverProbability(double expected, double threshold, double stdDev) {
        if (stdDev <= 0) {
            return expected > threshold ? 1.0 : 0.0;
        }

        // Z-score calculation
        double zScore = (threshold - expected) / stdDev;

        // Cumulative distribution function approximation
        // P(X > threshold) = 1 - CDF(threshold)
        double probability = 1.0 - normalCDF(zScore);

        // Ensure bounds [0, 1]
        return Math.max(0.0, Math.min(1.0, probability));
    }

    /**
     * Approximation of the standard normal cumulative distribution function.
     * Uses Abramowitz and Stegun approximation.
     *
     * @param z Z-score
     * @return CDF value
     */
    private double normalCDF(double z) {
        if (z < -8.0) return 0.0;
        if (z > 8.0) return 1.0;

        double sign = 1.0;
        if (z < 0) {
            sign = -1.0;
            z = -z;
        }

        // Coefficients for the approximation
        double t = 1.0 / (1.0 + 0.2316419 * z);
        double d = 0.3989423 * Math.exp(-z * z / 2.0);

        double p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));

        if (sign > 0) {
            return 1.0 - p;
        }
        return p;
    }

    /**
     * Calculate prediction confidence based on sample sizes.
     *
     * @param homeSamples Home team sample count
     * @param awaySamples Away team sample count
     * @return Confidence level (0 to 1)
     */
    private double calculatePredictionConfidence(int homeSamples, int awaySamples) {
        int minSamples = Math.min(homeSamples, awaySamples);

        if (minSamples < MIN_MATCHES_FOR_CONFIDENCE) {
            // Low confidence for small samples
            return minSamples / (double) MIN_MATCHES_FOR_CONFIDENCE * 0.5;
        }

        // Confidence increases with sample size, caps at 1.0
        // Uses HIGH_CONFIDENCE_MATCHES as the threshold for max confidence
        return Math.min(1.0, 0.5 + (minSamples / (double) HIGH_CONFIDENCE_MATCHES) * 0.5);
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPER METHODS - Math Utilities (Null & Divide-by-Zero Safe)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Calculate safe average (prevents divide by zero).
     */
    private double safeAverage(double sum, int count) {
        if (count <= 0) return 0.0;
        return sum / count;
    }

    /**
     * Safe division (prevents divide by zero and NaN).
     */
    private double safeDivide(double numerator, double denominator) {
        if (denominator == 0.0) return 0.0;
        double result = numerator / denominator;
        return Double.isNaN(result) || Double.isInfinite(result) ? 0.0 : result;
    }

    /**
     * Safe division for integers.
     */
    private double safeDivide(int numerator, int denominator) {
        if (denominator == 0) return 0.0;
        return (double) numerator / denominator;
    }

    /**
     * Round to two decimal places.
     */
    private double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Round to three decimal places.
     */
    private double roundToThreeDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.round(value * 1000.0) / 1000.0;
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPER METHODS - Data Fetching
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Fetch full season matches for a team based on home/away filter.
     *
     * @param teamName   Team name
     * @param isHome     Home/away filter (null = all matches)
     * @param season     Season identifier (e.g., "2025-26")
     * @param beforeDate Cutoff date to prevent future data leakage
     * @return List of matches for the season
     */
    private List<Match> fetchSeasonMatchesForTeam(String teamName, Boolean isHome, String season, LocalDate beforeDate) {
        List<Match> seasonMatches = matchRepository.findByTeamAndSeasonBeforeDate(teamName, season, beforeDate);

        if (isHome == null) {
            // Return all season matches
            return seasonMatches;
        }

        // Filter by home/away
        return seasonMatches.stream()
                .filter(match -> {
                    if (isHome) {
                        return match.getHomeTeam() != null &&
                               match.getHomeTeam().equalsIgnoreCase(teamName);
                    } else {
                        return match.getAwayTeam() != null &&
                               match.getAwayTeam().equalsIgnoreCase(teamName);
                    }
                })
                .toList();
    }

    // ══════════════════════════════════════════════════════════════════════
    // VALIDATION METHODS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Validate team name input.
     */
    private void validateTeamName(String teamName) {
        if (teamName == null || teamName.trim().isEmpty()) {
            throw new IllegalArgumentException("Team name cannot be null or empty");
        }
    }

    /**
     * Resolve team name with fuzzy matching using season-filtered data.
     * Falls back to case-insensitive matching if exact match fails.
     *
     * @param teamName   Team name to resolve
     * @param beforeDate Cutoff date
     * @param season     Current season
     * @return Resolved team name
     */
    /**
     * Resolve team name via centralized validation service.
     */
    private String resolveTeamName(String teamName, LocalDate beforeDate, String season) {
        return teamValidationService.resolveTeamName(teamName);
    }

    /**
     * Validate corner statistics for anomalies.
     */
    private void validateCornerStats(double avgWon, double avgAgainst, double dominance,
                                      double successRate, String teamName) {
        // Log warnings for unusual values
        if (avgWon > 10.0) {
            log.warn("Unusually high corner average ({}) for team: {}", avgWon, teamName);
        }
        if (avgWon < 1.0 && avgWon > 0) {
            log.warn("Unusually low corner average ({}) for team: {}", avgWon, teamName);
        }
        if (dominance < 0.0 || dominance > 1.0) {
            log.error("Invalid corner dominance ({}) for team: {}", dominance, teamName);
        }
        if (successRate < 0.0 || successRate > 1.0) {
            log.error("Invalid success rate ({}) for team: {}", successRate, teamName);
        }
    }

    /**
     * Validate prediction values.
     */
    private void validatePrediction(double total, double home, double away,
                                     double prob9_5, double prob10_5, double prob11_5) {
        // Verify total equals home + away
        double diff = Math.abs(total - (home + away));
        if (diff > 0.01) {
            log.error("Prediction validation failed: total ({}) != home ({}) + away ({})",
                      total, home, away);
        }

        // Verify probabilities are valid
        if (prob9_5 < 0 || prob9_5 > 1 || prob10_5 < 0 || prob10_5 > 1 || prob11_5 < 0 || prob11_5 > 1) {
            log.error("Invalid probability values: {}, {}, {}", prob9_5, prob10_5, prob11_5);
        }

        // Verify probability ordering (higher threshold = lower probability)
        if (prob9_5 < prob10_5 || prob10_5 < prob11_5) {
            log.warn("Unexpected probability ordering: over9.5={}, over10.5={}, over11.5={}",
                     prob9_5, prob10_5, prob11_5);
        }
    }

    /**
     * Build empty corner stats DTO when no data available.
     */
    private CornerStatsDTO buildEmptyCornerStats(String teamName, Boolean isHome) {
        return CornerStatsDTO.builder()
                .teamName(teamName)
                .isHome(isHome)
                .avgCornersWon(0.0)
                .avgCornersAgainst(0.0)
                .cornerDominance(0.0)
                .successRate(0.0)
                .matchesAnalyzed(0)
                .totalCornersWon(0)
                .totalCornersAgainst(0)
                .weightedAvgCorners(0.0)
                .build();
    }
}

