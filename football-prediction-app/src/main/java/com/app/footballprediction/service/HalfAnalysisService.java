package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.HalfAnalysisDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for analyzing team performance by half (first half vs second half).
 *
 * <p>Provides comprehensive half-time analysis including:</p>
 * <ul>
 *   <li>Goal distribution between halves</li>
 *   <li>Win rates based on half-time position</li>
 *   <li>Comeback statistics</li>
 *   <li>Pattern classification (Fast Starter, Strong Finisher, Balanced)</li>
 * </ul>
 *
 * <p><strong>Data Sources:</strong></p>
 * <ul>
 *   <li>HTHG (Half-Time Home Goals)</li>
 *   <li>HTAG (Half-Time Away Goals)</li>
 *   <li>FTHG (Full-Time Home Goals)</li>
 *   <li>FTAG (Full-Time Away Goals)</li>
 * </ul>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HalfAnalysisService {

    private final MatchRepository matchRepository;
    private final TeamValidationService teamValidationService;

    // ══════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Maximum recent matches to analyze.
     */
    private static final int MAX_MATCHES = 25;

    /**
     * Minimum matches required for confident analysis.
     */
    private static final int MIN_MATCHES_FOR_CONFIDENCE = 5;

    /**
     * High confidence threshold (number of matches).
     */
    private static final int HIGH_CONFIDENCE_MATCHES = 20;

    /**
     * Threshold for determining balanced performance (percentage difference).
     */
    private static final double BALANCED_THRESHOLD = 5.0;

    /**
     * Threshold for "Fast Starter" or "Strong Finisher" pattern.
     */
    private static final double PATTERN_THRESHOLD = 60.0;

    /**
     * Anomaly threshold for comeback rate.
     */
    private static final double ANOMALY_COMEBACK_RATE = 40.0;

    /**
     * Anomaly threshold for second half dominance.
     */
    private static final double ANOMALY_SECOND_HALF_PERCENTAGE = 80.0;

    /**
     * Maximum percentage value.
     */
    private static final double MAX_PERCENTAGE = 100.0;

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Analyze team performance by half (first half vs second half).
     *
     * <p>Calculates:</p>
     * <ul>
     *   <li>Goal distribution between halves</li>
     *   <li>Win rates based on HT position (leading, drawing, trailing)</li>
     *   <li>Comeback rate</li>
     *   <li>Pattern classification</li>
     * </ul>
     *
     * @param teamName Team name to analyze
     * @return HalfAnalysisDTO with comprehensive half analysis
     * @throws IllegalArgumentException if team name is invalid
     */
    @Cacheable(value = "halfAnalysis", key = "#teamName")
    public HalfAnalysisDTO analyzeByHalf(String teamName) {
        log.info("Analyzing half performance for team: {}", teamName);

        validateTeamName(teamName);

        LocalDate beforeDate = LocalDate.now().plusDays(1);
        String resolvedTeam = resolveTeamName(teamName.trim(), beforeDate);

        // Fetch recent matches
        List<Match> allMatches = matchRepository.findByTeamBeforeDate(resolvedTeam, beforeDate);

        if (allMatches.isEmpty()) {
            log.warn("No matches found for team: {}", resolvedTeam);
            return HalfAnalysisDTO.empty(resolvedTeam);
        }

        // Limit to recent matches
        List<Match> recentMatches = allMatches.stream()
                .limit(MAX_MATCHES)
                .toList();

        log.debug("Analyzing {} matches for half performance", recentMatches.size());

        return buildHalfAnalysis(resolvedTeam, recentMatches);
    }

    // ══════════════════════════════════════════════════════════════════════
    // ANALYSIS CALCULATION
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Build half analysis DTO from match data.
     */
    private HalfAnalysisDTO buildHalfAnalysis(String teamName, List<Match> matches) {
        // Goal counters
        int totalFirstHalfGoals = 0;
        int totalSecondHalfGoals = 0;
        int totalFirstHalfConceded = 0;
        int totalSecondHalfConceded = 0;

        // HT position counters
        int matchesLeadingHT = 0;
        int matchesDrawingHT = 0;
        int matchesTrailingHT = 0;

        // Win counters based on HT position
        int winsWhenLeadingHT = 0;
        int winsWhenDrawingHT = 0;
        int winsWhenTrailingHT = 0;

        int validMatches = 0;

        for (Match match : matches) {
            // Skip matches without required data
            if (!hasValidHalfTimeData(match)) {
                continue;
            }

            boolean isHome = match.getHomeTeam() != null &&
                             match.getHomeTeam().equalsIgnoreCase(teamName);

            // Extract goals for this team
            int teamHTGoals = isHome ? match.getHalfTimeHomeGoals() : match.getHalfTimeAwayGoals();
            int opponentHTGoals = isHome ? match.getHalfTimeAwayGoals() : match.getHalfTimeHomeGoals();
            int teamFTGoals = isHome ? match.getFullTimeHomeGoals() : match.getFullTimeAwayGoals();
            int opponentFTGoals = isHome ? match.getFullTimeAwayGoals() : match.getFullTimeHomeGoals();

            // Calculate second half goals
            int teamSecondHalfGoals = teamFTGoals - teamHTGoals;
            int opponentSecondHalfGoals = opponentFTGoals - opponentHTGoals;

            // Defensive: ensure non-negative
            teamSecondHalfGoals = Math.max(0, teamSecondHalfGoals);
            opponentSecondHalfGoals = Math.max(0, opponentSecondHalfGoals);

            // Accumulate goals
            totalFirstHalfGoals += teamHTGoals;
            totalSecondHalfGoals += teamSecondHalfGoals;
            totalFirstHalfConceded += opponentHTGoals;
            totalSecondHalfConceded += opponentSecondHalfGoals;

            // Determine HT position and final result
            boolean wonMatch = teamFTGoals > opponentFTGoals;

            if (teamHTGoals > opponentHTGoals) {
                // Leading at HT
                matchesLeadingHT++;
                if (wonMatch) winsWhenLeadingHT++;
            } else if (teamHTGoals < opponentHTGoals) {
                // Trailing at HT
                matchesTrailingHT++;
                if (wonMatch) winsWhenTrailingHT++;
            } else {
                // Drawing at HT
                matchesDrawingHT++;
                if (wonMatch) winsWhenDrawingHT++;
            }

            validMatches++;
        }

        if (validMatches == 0) {
            log.warn("No valid matches with half-time data for team: {}", teamName);
            return HalfAnalysisDTO.empty(teamName);
        }

        // Calculate averages
        double firstHalfAvg = safeDivide(totalFirstHalfGoals, validMatches);
        double secondHalfAvg = safeDivide(totalSecondHalfGoals, validMatches);
        double firstHalfConcededAvg = safeDivide(totalFirstHalfConceded, validMatches);
        double secondHalfConcededAvg = safeDivide(totalSecondHalfConceded, validMatches);

        // Calculate percentages
        int totalGoals = totalFirstHalfGoals + totalSecondHalfGoals;
        double firstHalfPct;
        double secondHalfPct;

        if (totalGoals == 0) {
            // Edge case: no goals scored
            firstHalfPct = 50.0;
            secondHalfPct = 50.0;
        } else {
            firstHalfPct = roundToTwoDecimals((double) totalFirstHalfGoals / totalGoals * 100.0);
            secondHalfPct = roundToTwoDecimals((double) totalSecondHalfGoals / totalGoals * 100.0);

            // Ensure percentages sum to 100 (handle rounding)
            double total = firstHalfPct + secondHalfPct;
            if (Math.abs(total - 100.0) > 0.01) {
                secondHalfPct = roundToTwoDecimals(100.0 - firstHalfPct);
            }
        }

        // Calculate win rates
        double winRateLeading = calculatePercentage(winsWhenLeadingHT, matchesLeadingHT);
        double winRateDrawing = calculatePercentage(winsWhenDrawingHT, matchesDrawingHT);
        double winRateLosing = calculatePercentage(winsWhenTrailingHT, matchesTrailingHT);
        double comebackRate = calculatePercentage(winsWhenTrailingHT, matchesTrailingHT);

        // Determine stronger half
        String strongerHalf = determineStrongerHalf(firstHalfPct, secondHalfPct);

        // Determine pattern
        String pattern = determinePattern(firstHalfPct, secondHalfPct);

        // Calculate confidence
        double confidence = calculateConfidence(validMatches);

        // Log anomalies and build anomaly description
        StringBuilder anomalyBuilder = new StringBuilder();
        boolean hasAnomaly = false;

        if (comebackRate > ANOMALY_COMEBACK_RATE) {
            log.warn("ANOMALY: {} has unusually high comeback rate: {}%", teamName, String.format("%.1f", comebackRate));
            anomalyBuilder.append("High comeback rate (").append(String.format("%.1f", comebackRate)).append("%). ");
            hasAnomaly = true;
        }

        if (secondHalfPct > ANOMALY_SECOND_HALF_PERCENTAGE) {
            log.warn("ANOMALY: {} has extreme second half dominance: {}%", teamName, String.format("%.1f", secondHalfPct));
            anomalyBuilder.append("Extreme 2H dominance (").append(String.format("%.1f", secondHalfPct)).append("%). ");
            hasAnomaly = true;
        }

        if (validMatches < MIN_MATCHES_FOR_CONFIDENCE) {
            log.warn("LOW_SAMPLE: {} analysis based on only {} matches", teamName, validMatches);
            anomalyBuilder.append("Low sample size (").append(validMatches).append(" matches). ");
            hasAnomaly = true;
        }

        // Calculate goal differentials
        double firstHalfDiff = roundToTwoDecimals(firstHalfAvg - firstHalfConcededAvg);
        double secondHalfDiff = roundToTwoDecimals(secondHalfAvg - secondHalfConcededAvg);

        log.info("Half analysis for {}: 1H={}%, 2H={}%, pattern={}, comebackRate={}%",
                teamName, String.format("%.1f", firstHalfPct), String.format("%.1f", secondHalfPct),
                pattern, String.format("%.1f", comebackRate));

        return HalfAnalysisDTO.builder()
                .teamName(teamName)
                .dataScope("Last " + validMatches + " Matches")
                .firstHalfGoalsAvg(roundToTwoDecimals(firstHalfAvg))
                .secondHalfGoalsAvg(roundToTwoDecimals(secondHalfAvg))
                .totalFirstHalfGoals(totalFirstHalfGoals)
                .totalSecondHalfGoals(totalSecondHalfGoals)
                .totalGoals(totalGoals)
                .firstHalfPercentage(firstHalfPct)
                .secondHalfPercentage(secondHalfPct)
                .strongerHalf(strongerHalf)
                .winRateWhenLeadingHT(winRateLeading)
                .winRateWhenDrawingHT(winRateDrawing)
                .winRateWhenLosingHT(winRateLosing)
                .comebackRate(comebackRate)
                .pattern(pattern)
                .matchesAnalyzed(validMatches)
                .matchesLeadingHT(matchesLeadingHT)
                .matchesDrawingHT(matchesDrawingHT)
                .matchesTrailingHT(matchesTrailingHT)
                .firstHalfConcededAvg(roundToTwoDecimals(firstHalfConcededAvg))
                .secondHalfConcededAvg(roundToTwoDecimals(secondHalfConcededAvg))
                .confidence(roundToTwoDecimals(confidence))
                .firstHalfGoalDifferential(firstHalfDiff)
                .secondHalfGoalDifferential(secondHalfDiff)
                .anomalyDetected(hasAnomaly)
                .anomalyDescription(hasAnomaly ? anomalyBuilder.toString().trim() : null)
                .build();
    }

    /**
     * Check if match has valid half-time data.
     */
    private boolean hasValidHalfTimeData(Match match) {
        return match.getHalfTimeHomeGoals() != null &&
               match.getHalfTimeAwayGoals() != null &&
               match.getFullTimeHomeGoals() != null &&
               match.getFullTimeAwayGoals() != null;
    }

    /**
     * Determine which half the team is stronger in.
     */
    private String determineStrongerHalf(double firstHalfPct, double secondHalfPct) {
        double difference = Math.abs(firstHalfPct - secondHalfPct);

        if (difference < BALANCED_THRESHOLD) {
            return "Balanced";
        } else if (firstHalfPct > secondHalfPct) {
            return "First Half";
        } else {
            return "Second Half";
        }
    }

    /**
     * Determine pattern classification based on goal distribution.
     */
    private String determinePattern(double firstHalfPct, double secondHalfPct) {
        if (firstHalfPct >= PATTERN_THRESHOLD) {
            return "Fast Starter";
        } else if (secondHalfPct >= PATTERN_THRESHOLD) {
            return "Strong Finisher";
        } else {
            return "Balanced";
        }
    }


    // ══════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Calculate percentage with bounds checking.
     */
    private double calculatePercentage(int numerator, int denominator) {
        if (denominator == 0) return 0.0;
        double pct = (double) numerator / denominator * 100.0;
        return Math.min(MAX_PERCENTAGE, Math.max(0.0, roundToTwoDecimals(pct)));
    }

    /**
     * Calculate confidence based on sample size.
     */
    private double calculateConfidence(int matchCount) {
        if (matchCount < MIN_MATCHES_FOR_CONFIDENCE) {
            return (double) matchCount / MIN_MATCHES_FOR_CONFIDENCE * 0.5;
        }
        return Math.min(1.0, 0.5 + ((double) matchCount / HIGH_CONFIDENCE_MATCHES) * 0.5);
    }

    /**
     * Safe division to prevent divide-by-zero.
     */
    private double safeDivide(int numerator, int denominator) {
        if (denominator == 0) return 0.0;
        return (double) numerator / denominator;
    }

    /**
     * Resolve team name via centralized validation service.
     */
    private String resolveTeamName(String teamName, LocalDate beforeDate) {
        return teamValidationService.resolveTeamName(teamName);
    }

    /**
     * Validate team name.
     */
    private void validateTeamName(String teamName) {
        if (teamName == null || teamName.trim().isEmpty()) {
            throw new IllegalArgumentException("Team name cannot be null or empty");
        }
    }

    /**
     * Round to two decimal places.
     */
    private double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.round(value * 100.0) / 100.0;
    }
}

