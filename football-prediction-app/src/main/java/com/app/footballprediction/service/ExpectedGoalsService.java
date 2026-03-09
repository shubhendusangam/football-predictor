package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.ExpectedGoalsDTO;
import com.app.footballprediction.dto.MatchXGPredictionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for calculating Expected Goals (xG) statistics and predictions.
 *
 * <p>Provides comprehensive xG analytics including:</p>
 * <ul>
 *   <li>Team xG based on shots on target and league conversion rates</li>
 *   <li>Over/underperformance relative to xG</li>
 *   <li>Match xG predictions with over/under goal probabilities</li>
 * </ul>
 *
 * <p><strong>xG Model:</strong></p>
 * <p>Uses a shots-on-target proxy model:</p>
 * <ul>
 *   <li>xG = avgShotsOnTarget × leagueConversionRate</li>
 *   <li>League conversion rate: ~0.28 (empirical PL average)</li>
 *   <li>Team-specific conversion rate calculated for comparison</li>
 * </ul>
 *
 * <p><strong>Data Sources:</strong></p>
 * <ul>
 *   <li>HST (Home Shots on Target) / AST (Away Shots on Target)</li>
 *   <li>FTHG (Full Time Home Goals) / FTAG (Full Time Away Goals)</li>
 * </ul>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpectedGoalsService {

    private final MatchRepository matchRepository;

    /**
     * Default Premier League shot-on-target to goal conversion rate.
     * Empirically: about 28% of shots on target result in goals.
     */
    private static final double DEFAULT_LEAGUE_CONVERSION_RATE = 0.28;

    /**
     * Minimum matches required for confident statistics.
     */
    private static final int MIN_MATCHES_FOR_CONFIDENCE = 5;

    /**
     * High confidence threshold for predictions.
     */
    private static final int HIGH_CONFIDENCE_MATCHES = 20;

    /**
     * Decay factor for weighted average calculation.
     * Higher values give more weight to recent matches.
     */
    private static final double RECENCY_DECAY_FACTOR = 0.15;

    /**
     * Home advantage factor for xG prediction.
     * Home teams typically score ~10% more.
     */
    private static final double HOME_ADVANTAGE_FACTOR = 1.10;

    /**
     * Minimum realistic xG per team (floor).
     */
    private static final double MIN_XG = 0.5;

    /**
     * Maximum realistic xG per team (ceiling).
     */
    private static final double MAX_XG = 3.5;

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Calculate expected goals (xG) statistics for a specific team.
     *
     * <p>Retrieves all matches from the current season and calculates:</p>
     * <ul>
     *   <li>Average shots on target per match</li>
     *   <li>xG per game (avgSOT × league conversion rate)</li>
     *   <li>Actual goals per game</li>
     *   <li>Over/underperformance relative to xG</li>
     *   <li>Team-specific conversion rate</li>
     * </ul>
     *
     * @param teamName Name of the team (case-insensitive matching supported)
     * @param isHome   True for home matches only, false for away only, null for all
     * @return ExpectedGoalsDTO containing xG statistics
     * @throws IllegalArgumentException if team name is empty or no matches found
     */
    @Cacheable(value = "expectedGoals", key = "#teamName + '-' + #isHome")
    public ExpectedGoalsDTO calculateXG(String teamName, Boolean isHome) {
        log.info("Calculating xG for team: '{}', isHome: {}", teamName, isHome);

        validateTeamName(teamName);

        LocalDate beforeDate = LocalDate.now().plusDays(1);
        String currentSeason = matchRepository.findCurrentSeason();

        if (currentSeason == null) {
            log.warn("No current season found in database");
            throw new IllegalStateException("No season data available. Please ingest match data first.");
        }

        log.debug("Using current season: {}", currentSeason);

        String resolvedTeam = resolveTeamName(teamName, beforeDate, currentSeason);

        // Calculate league conversion rate from all season data
        double leagueConversionRate = calculateLeagueConversionRate(currentSeason, beforeDate);

        // Fetch full season matches based on home/away filter
        List<Match> matches = fetchSeasonMatchesForTeam(resolvedTeam, isHome, currentSeason, beforeDate);

        if (matches.isEmpty()) {
            log.warn("No matches found for team: {} in season: {}", resolvedTeam, currentSeason);
            throw new IllegalArgumentException(
                    "No xG data available for '" + resolvedTeam + "' in season " + currentSeason + ". " +
                    "Expected goals statistics require historical match data with shots on target information.");
        }

        log.debug("Processing {} full season matches for xG statistics", matches.size());

        return buildExpectedGoalsStats(resolvedTeam, matches, isHome, currentSeason, leagueConversionRate);
    }

    /**
     * Predict expected goals (xG) for an upcoming match.
     *
     * <p>Uses both teams' historical xG data to predict:</p>
     * <ul>
     *   <li>Expected goals for each team</li>
     *   <li>Total expected goals</li>
     *   <li>Over/under goal probabilities (1.5, 2.5, 3.5)</li>
     * </ul>
     *
     * @param homeTeam Name of the home team
     * @param awayTeam Name of the away team
     * @return MatchXGPredictionDTO containing match xG predictions
     * @throws IllegalArgumentException if team names are invalid or same
     */
    @Cacheable(value = "xgPrediction", key = "#homeTeam + '-vs-' + #awayTeam")
    public MatchXGPredictionDTO predictMatchXG(String homeTeam, String awayTeam) {
        log.info("Predicting xG for match: {} vs {}", homeTeam, awayTeam);

        validateTeamName(homeTeam);
        validateTeamName(awayTeam);

        if (homeTeam.trim().equalsIgnoreCase(awayTeam.trim())) {
            throw new IllegalArgumentException("Home and away teams cannot be the same");
        }

        // Get xG stats for both teams (home stats for home team, away stats for away team)
        ExpectedGoalsDTO homeStats = calculateXG(homeTeam.trim(), true);
        ExpectedGoalsDTO awayStats = calculateXG(awayTeam.trim(), false);

        return buildMatchXGPrediction(homeStats, awayStats);
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRIVATE CALCULATION METHODS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Calculate the league-wide conversion rate from all season matches.
     * Conversion rate = total goals / total shots on target.
     *
     * @param season     Current season
     * @param beforeDate Cutoff date
     * @return League conversion rate (falls back to DEFAULT_LEAGUE_CONVERSION_RATE if insufficient data)
     */
    private double calculateLeagueConversionRate(String season, LocalDate beforeDate) {
        List<Match> allMatches = matchRepository.findBySeasonOrderByMatchDateDesc(season);

        int totalShotsOnTarget = 0;
        int totalGoals = 0;
        int validMatches = 0;

        for (Match match : allMatches) {
            if (match.getMatchDate() != null && match.getMatchDate().isBefore(beforeDate)) {
                Integer hst = match.getHomeShotsOnTarget();
                Integer ast = match.getAwayShotsOnTarget();
                Integer hg = match.getFullTimeHomeGoals();
                Integer ag = match.getFullTimeAwayGoals();

                if (hst != null && ast != null && hg != null && ag != null) {
                    totalShotsOnTarget += hst + ast;
                    totalGoals += hg + ag;
                    validMatches++;
                }
            }
        }

        if (validMatches < 10 || totalShotsOnTarget == 0) {
            log.info("Insufficient data for league conversion rate ({} matches), using default: {}",
                     validMatches, DEFAULT_LEAGUE_CONVERSION_RATE);
            return DEFAULT_LEAGUE_CONVERSION_RATE;
        }

        double rate = (double) totalGoals / totalShotsOnTarget;
        log.debug("Calculated league conversion rate: {} from {} matches ({} goals / {} SOT)",
                  roundToThreeDecimals(rate), validMatches, totalGoals, totalShotsOnTarget);

        // Sanity check: rate should be between 0.15 and 0.45
        if (rate < 0.15 || rate > 0.45) {
            log.warn("League conversion rate {} outside expected range, using default", rate);
            return DEFAULT_LEAGUE_CONVERSION_RATE;
        }

        return rate;
    }

    /**
     * Build expected goals statistics DTO from match data.
     *
     * @param teamName            Resolved team name
     * @param matches             List of season matches (sorted by date DESC)
     * @param isHome              Home/away filter
     * @param season              Season identifier
     * @param leagueConversionRate League-wide conversion rate
     * @return ExpectedGoalsDTO with calculated statistics
     */
    private ExpectedGoalsDTO buildExpectedGoalsStats(String teamName, List<Match> matches, Boolean isHome,
                                                      String season, double leagueConversionRate) {
        int totalShotsOnTarget = 0;
        int totalShotsOnTargetAgainst = 0;
        int totalGoals = 0;
        int matchesWithShotData = 0;

        double weightedXGSum = 0.0;
        double weightSum = 0.0;

        for (int i = 0; i < matches.size(); i++) {
            Match match = matches.get(i);

            Integer shotsOnTarget = getShotsOnTarget(match, teamName);
            Integer shotsOnTargetAgainst = getShotsOnTargetAgainst(match, teamName);
            Integer goals = getGoalsScored(match, teamName);

            // Skip matches without shot data
            if (shotsOnTarget == null || goals == null) {
                log.debug("Skipping match {} - missing shot/goal data", match.getId());
                continue;
            }

            matchesWithShotData++;

            totalShotsOnTarget += shotsOnTarget;
            totalShotsOnTargetAgainst += (shotsOnTargetAgainst != null ? shotsOnTargetAgainst : 0);
            totalGoals += goals;

            // Calculate per-match xG and apply recency weighting
            double matchXG = shotsOnTarget * leagueConversionRate;
            double weight = calculateRecencyWeight(i);
            weightedXGSum += matchXG * weight;
            weightSum += weight;
        }

        // Handle case with no valid data
        if (matchesWithShotData == 0) {
            log.warn("No shot data available for team: {} in season: {}", teamName, season);
            return buildEmptyExpectedGoals(teamName, isHome, leagueConversionRate);
        }

        // Calculate averages
        double avgShotsOnTarget = safeAverage(totalShotsOnTarget, matchesWithShotData);
        double avgShotsOnTargetAgainst = safeAverage(totalShotsOnTargetAgainst, matchesWithShotData);
        double actualGoalsPerGame = safeAverage(totalGoals, matchesWithShotData);
        double expectedGoalsPerGame = avgShotsOnTarget * leagueConversionRate;
        double expectedGoalsAgainst = avgShotsOnTargetAgainst * leagueConversionRate;
        double xgDifference = actualGoalsPerGame - expectedGoalsPerGame;
        double teamConversionRate = totalShotsOnTarget > 0
                ? (double) totalGoals / totalShotsOnTarget : 0.0;
        double weightedXG = safeDivide(weightedXGSum, weightSum);

        // Build performance string
        String performance = buildPerformanceString(xgDifference);

        log.debug("xG stats for {} (season {}): matches={}, avgSOT={}, xG={}, actual={}, diff={}",
                  teamName, season, matchesWithShotData, avgShotsOnTarget, expectedGoalsPerGame,
                  actualGoalsPerGame, xgDifference);

        return ExpectedGoalsDTO.builder()
                .teamName(teamName)
                .isHome(isHome)
                .avgShotsOnTarget(roundToTwoDecimals(avgShotsOnTarget))
                .expectedGoals(roundToTwoDecimals(expectedGoalsPerGame))
                .actualGoals(roundToTwoDecimals(actualGoalsPerGame))
                .xGDifference(roundToTwoDecimals(xgDifference))
                .conversionRate(roundToThreeDecimals(teamConversionRate))
                .leagueConversionRate(roundToThreeDecimals(leagueConversionRate))
                .performance(performance)
                .matchesAnalyzed(matchesWithShotData)
                .totalShotsOnTarget(totalShotsOnTarget)
                .totalGoals(totalGoals)
                .weightedXG(roundToTwoDecimals(weightedXG))
                .avgShotsOnTargetAgainst(roundToTwoDecimals(avgShotsOnTargetAgainst))
                .expectedGoalsAgainst(roundToTwoDecimals(expectedGoalsAgainst))
                .build();
    }

    /**
     * Build match xG prediction from team statistics.
     *
     * @param homeStats Home team xG stats
     * @param awayStats Away team xG stats
     * @return MatchXGPredictionDTO with match predictions
     */
    private MatchXGPredictionDTO buildMatchXGPrediction(ExpectedGoalsDTO homeStats, ExpectedGoalsDTO awayStats) {
        // Calculate expected goals considering attacking strength and defensive weakness
        // Home xG = average of (home team's attacking xG, away team's defensive xG conceded)
        double homeAttackXG = homeStats.getWeightedXG() > 0 ? homeStats.getWeightedXG() : homeStats.getExpectedGoals();
        double awayDefenseXG = awayStats.getExpectedGoalsAgainst();
        double homeXG = (homeAttackXG + awayDefenseXG) / 2.0;

        // Apply home advantage
        homeXG *= HOME_ADVANTAGE_FACTOR;

        // Away xG = average of (away team's attacking xG, home team's defensive xG conceded)
        double awayAttackXG = awayStats.getWeightedXG() > 0 ? awayStats.getWeightedXG() : awayStats.getExpectedGoals();
        double homeDefenseXG = homeStats.getExpectedGoalsAgainst();
        double awayXG = (awayAttackXG + homeDefenseXG) / 2.0;

        // Clamp to realistic range
        homeXG = Math.max(MIN_XG, Math.min(MAX_XG, homeXG));
        awayXG = Math.max(MIN_XG, Math.min(MAX_XG, awayXG));

        // Use unrounded values for probability calculations (better precision)
        double totalXGRaw = homeXG + awayXG;

        // Calculate over/under probabilities using Poisson approximation
        double probOver1_5 = calculatePoissonOverProbability(totalXGRaw, 1.5);
        double probOver2_5 = calculatePoissonOverProbability(totalXGRaw, 2.5);
        double probOver3_5 = calculatePoissonOverProbability(totalXGRaw, 3.5);

        // Round individual values first, then derive total from rounded values
        // This ensures totalXG == homeXG + awayXG in the DTO
        double roundedHomeXG = roundToTwoDecimals(homeXG);
        double roundedAwayXG = roundToTwoDecimals(awayXG);
        double totalXG = roundToTwoDecimals(roundedHomeXG + roundedAwayXG);

        // Build prediction string
        String prediction = buildPredictionString(totalXG);

        // Build recommendation
        String recommendation = buildRecommendation(totalXG);

        // Calculate confidence
        double confidence = calculatePredictionConfidence(
                homeStats.getMatchesAnalyzed(), awayStats.getMatchesAnalyzed());

        log.info("xG prediction: {} vs {} - homeXG={}, awayXG={}, totalXG={}, confidence={}",
                 homeStats.getTeamName(), awayStats.getTeamName(), roundedHomeXG, roundedAwayXG, totalXG, confidence);

        return MatchXGPredictionDTO.builder()
                .homeTeam(homeStats.getTeamName())
                .awayTeam(awayStats.getTeamName())
                .homeXG(roundedHomeXG)
                .awayXG(roundedAwayXG)
                .totalXG(totalXG)
                .prediction(prediction)
                .probOver1_5(roundToThreeDecimals(probOver1_5))
                .probOver2_5(roundToThreeDecimals(probOver2_5))
                .probOver3_5(roundToThreeDecimals(probOver3_5))
                .confidence(roundToThreeDecimals(confidence))
                .homeShotsOnTarget(homeStats.getAvgShotsOnTarget())
                .awayShotsOnTarget(awayStats.getAvgShotsOnTarget())
                .homeMatchesAnalyzed(homeStats.getMatchesAnalyzed())
                .awayMatchesAnalyzed(awayStats.getMatchesAnalyzed())
                .recommendation(recommendation)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPER METHODS - Match Data Extraction
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Get shots on target for the specified team in a match.
     */
    private Integer getShotsOnTarget(Match match, String teamName) {
        if (match == null || teamName == null) return null;

        if (isHomeTeam(match, teamName)) {
            return match.getHomeShotsOnTarget(); // HST
        } else if (isAwayTeam(match, teamName)) {
            return match.getAwayShotsOnTarget(); // AST
        }
        return null;
    }

    /**
     * Get shots on target against (conceded) for the specified team in a match.
     */
    private Integer getShotsOnTargetAgainst(Match match, String teamName) {
        if (match == null || teamName == null) return null;

        if (isHomeTeam(match, teamName)) {
            return match.getAwayShotsOnTarget(); // AST = shots against home team
        } else if (isAwayTeam(match, teamName)) {
            return match.getHomeShotsOnTarget(); // HST = shots against away team
        }
        return null;
    }

    /**
     * Get goals scored by the specified team in a match.
     */
    private Integer getGoalsScored(Match match, String teamName) {
        if (match == null || teamName == null) return null;

        if (isHomeTeam(match, teamName)) {
            return match.getFullTimeHomeGoals(); // FTHG
        } else if (isAwayTeam(match, teamName)) {
            return match.getFullTimeAwayGoals(); // FTAG
        }
        return null;
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
     * Calculate probability of total goals being over a threshold using Poisson distribution.
     * P(X > k) = 1 - P(X <= floor(k))
     *
     * @param lambda Expected total goals (mean of Poisson distribution)
     * @param threshold Over threshold (e.g., 2.5)
     * @return Probability (0 to 1)
     */
    private double calculatePoissonOverProbability(double lambda, double threshold) {
        if (lambda <= 0) return 0.0;

        int k = (int) Math.floor(threshold);
        double cumulativeProb = 0.0;

        // P(X <= k) = sum of P(X = i) for i = 0 to k
        for (int i = 0; i <= k; i++) {
            cumulativeProb += poissonPMF(lambda, i);
        }

        double prob = 1.0 - cumulativeProb;
        return Math.max(0.0, Math.min(1.0, prob));
    }

    /**
     * Poisson probability mass function.
     * P(X = k) = (λ^k * e^-λ) / k!
     */
    private double poissonPMF(double lambda, int k) {
        if (k < 0) return 0.0;
        double logProb = k * Math.log(lambda) - lambda - logFactorial(k);
        return Math.exp(logProb);
    }

    /**
     * Logarithm of factorial (for numerical stability).
     */
    private double logFactorial(int n) {
        if (n <= 1) return 0.0;
        double result = 0.0;
        for (int i = 2; i <= n; i++) {
            result += Math.log(i);
        }
        return result;
    }

    /**
     * Calculate prediction confidence based on sample sizes.
     */
    private double calculatePredictionConfidence(int homeSamples, int awaySamples) {
        int minSamples = Math.min(homeSamples, awaySamples);

        if (minSamples < MIN_MATCHES_FOR_CONFIDENCE) {
            return minSamples / (double) MIN_MATCHES_FOR_CONFIDENCE * 0.5;
        }

        return Math.min(1.0, 0.5 + (minSamples / (double) HIGH_CONFIDENCE_MATCHES) * 0.5);
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPER METHODS - String Builders
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Build performance string from xG difference.
     */
    private String buildPerformanceString(double xgDifference) {
        if (Math.abs(xgDifference) < 0.05) {
            return "Performing as expected";
        } else if (xgDifference > 0) {
            return String.format("Overperforming +%.1f", xgDifference);
        } else {
            return String.format("Underperforming %.1f", xgDifference);
        }
    }

    /**
     * Build prediction string from total xG.
     */
    private String buildPredictionString(double totalXG) {
        if (totalXG >= 2.5) {
            return String.format("Expect Over 2.5 goals (totalXG: %.1f)", totalXG);
        } else if (totalXG >= 1.5) {
            return String.format("Expect Over 1.5 goals (totalXG: %.1f)", totalXG);
        } else {
            return String.format("Low-scoring match expected (totalXG: %.1f)", totalXG);
        }
    }

    /**
     * Build recommendation text for the match.
     */
    private String buildRecommendation(double totalXG) {
        if (totalXG >= 3.5) {
            return String.format("High-scoring match expected (%.1f xG)", totalXG);
        } else if (totalXG >= 2.5) {
            return String.format("Goals expected - moderate attacking match (%.1f xG)", totalXG);
        } else if (totalXG >= 1.5) {
            return String.format("Tight match expected - fewer goals likely (%.1f xG)", totalXG);
        } else {
            return String.format("Low-scoring match expected (%.1f xG)", totalXG);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPER METHODS - Math Utilities
    // ══════════════════════════════════════════════════════════════════════

    private double safeAverage(double sum, int count) {
        if (count <= 0) return 0.0;
        return sum / count;
    }

    private double safeDivide(double numerator, double denominator) {
        if (denominator == 0.0) return 0.0;
        double result = numerator / denominator;
        return Double.isNaN(result) || Double.isInfinite(result) ? 0.0 : result;
    }

    private double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.round(value * 100.0) / 100.0;
    }

    private double roundToThreeDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.round(value * 1000.0) / 1000.0;
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPER METHODS - Data Fetching
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Fetch full season matches for a team based on home/away filter.
     */
    private List<Match> fetchSeasonMatchesForTeam(String teamName, Boolean isHome,
                                                   String season, LocalDate beforeDate) {
        List<Match> seasonMatches = matchRepository.findByTeamAndSeasonBeforeDate(teamName, season, beforeDate);

        if (isHome == null) {
            return seasonMatches;
        }

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

    private void validateTeamName(String teamName) {
        if (teamName == null || teamName.trim().isEmpty()) {
            throw new IllegalArgumentException("Team name cannot be null or empty");
        }
    }

    /**
     * Resolve team name with fuzzy matching using season-filtered data.
     */
    private String resolveTeamName(String teamName, LocalDate beforeDate, String season) {
        String trimmed = teamName.trim();

        // Try exact match first within season
        List<Match> exactMatches = matchRepository.findByTeamAndSeasonBeforeDate(trimmed, season, beforeDate);
        if (!exactMatches.isEmpty()) {
            return trimmed;
        }

        // Try case-insensitive match within season
        List<Match> caseInsensitiveMatches = matchRepository.findByTeamAndSeasonBeforeDateIgnoreCase(
                trimmed, season, beforeDate);
        if (!caseInsensitiveMatches.isEmpty()) {
            Match first = caseInsensitiveMatches.get(0);
            String actual = first.getHomeTeam().equalsIgnoreCase(trimmed)
                    ? first.getHomeTeam()
                    : first.getAwayTeam();
            log.debug("Resolved '{}' to '{}' (case-insensitive) in season {}", trimmed, actual, season);
            return actual;
        }

        log.warn("Could not resolve team name '{}' in season {}", trimmed, season);
        return trimmed;
    }

    /**
     * Build empty expected goals DTO when no data available.
     */
    private ExpectedGoalsDTO buildEmptyExpectedGoals(String teamName, Boolean isHome, double leagueConversionRate) {
        return ExpectedGoalsDTO.builder()
                .teamName(teamName)
                .isHome(isHome)
                .avgShotsOnTarget(0.0)
                .expectedGoals(0.0)
                .actualGoals(0.0)
                .xGDifference(0.0)
                .conversionRate(0.0)
                .leagueConversionRate(roundToThreeDecimals(leagueConversionRate))
                .performance("Insufficient data")
                .matchesAnalyzed(0)
                .totalShotsOnTarget(0)
                .totalGoals(0)
                .weightedXG(0.0)
                .avgShotsOnTargetAgainst(0.0)
                .expectedGoalsAgainst(0.0)
                .build();
    }
}

