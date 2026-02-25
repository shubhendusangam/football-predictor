package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.FoulsAnalysisDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for analyzing team fouls and discipline metrics.
 *
 * Provides comprehensive fouls analysis including:
 * - Average fouls committed and drawn
 * - Fouls differential
 * - Discipline score (0-10, normalized)
 * - Win rates based on foul counts
 *
 * Uses only historical data to prevent future data leakage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FoulsAnalysisService {

    private final MatchRepository matchRepository;

    private static final int ANALYSIS_MATCH_COUNT = 20;
    private static final int LOW_FOULS_THRESHOLD = 10;
    private static final int HIGH_FOULS_THRESHOLD = 15;
    private static final int CONTROLLED_FOULS_THRESHOLD = 12;

    // League average fouls for normalization (Premier League average ~11-12)
    private static final double LEAGUE_AVG_FOULS = 11.5;
    private static final double FOULS_STD_DEV = 3.5;

    /**
     * Analyze fouls and discipline for a team.
     *
     * @param teamName The team name to analyze
     * @param isHome Whether to analyze home or away matches
     * @return FoulsAnalysisDTO with comprehensive fouls metrics
     * @throws IllegalArgumentException if team not found or insufficient data
     */
    @Cacheable(value = "foulsAnalysis", key = "#teamName.toLowerCase() + '_' + #isHome")
    public FoulsAnalysisDTO analyzeFouls(String teamName, boolean isHome) {
        log.info("Analyzing fouls for team: {} (isHome: {})", teamName, isHome);
        long startTime = System.currentTimeMillis();

        if (teamName == null || teamName.trim().isEmpty()) {
            throw new IllegalArgumentException("Team name cannot be empty");
        }

        String sanitizedTeamName = teamName.trim();
        LocalDate today = LocalDate.now();

        // Fetch matches (prevent future data leakage by using before today)
        List<Match> matches;
        if (isHome) {
            matches = matchRepository.findHomeMatchesByTeamBeforeDate(sanitizedTeamName, today);
        } else {
            matches = matchRepository.findAwayMatchesByTeamBeforeDate(sanitizedTeamName, today);
        }

        // Try case-insensitive match if no exact match found
        if (matches.isEmpty()) {
            log.debug("No exact match found for {}, trying case-insensitive search", sanitizedTeamName);
            if (isHome) {
                matches = matchRepository.findHomeMatchesByTeamBeforeDateIgnoreCase(sanitizedTeamName, today);
            } else {
                matches = matchRepository.findAwayMatchesByTeamBeforeDateIgnoreCase(sanitizedTeamName, today);
            }
        }

        if (matches.isEmpty()) {
            log.warn("No matches found for team: {}", sanitizedTeamName);
            throw new IllegalArgumentException("Team not found or no match data available: " + sanitizedTeamName);
        }

        // Filter matches with valid fouls data and limit to ANALYSIS_MATCH_COUNT
        // Matches are already sorted by date DESC (newest first)
        List<Match> validMatches = matches.stream()
                .filter(this::hasValidFoulsData)
                .limit(ANALYSIS_MATCH_COUNT)
                .collect(Collectors.toList());

        if (validMatches.isEmpty()) {
            log.warn("No matches with fouls data found for team: {}", sanitizedTeamName);
            // Return zero-filled DTO instead of throwing exception
            return buildEmptyAnalysis(sanitizedTeamName, isHome);
        }

        log.debug("Found {} matches with valid fouls data for {}", validMatches.size(), sanitizedTeamName);

        // Calculate statistics
        FoulsAnalysisDTO analysis = calculateFoulsAnalysis(validMatches, sanitizedTeamName, isHome);

        long duration = System.currentTimeMillis() - startTime;
        log.info("Fouls analysis for {} completed in {}ms. Discipline score: {}",
                sanitizedTeamName, duration, analysis.getDisciplineScore());

        return analysis;
    }

    /**
     * Check if a match has valid fouls data.
     */
    private boolean hasValidFoulsData(Match match) {
        if (match == null) return false;
        // Need both home and away fouls data
        return match.getHomeFouls() != null && match.getAwayFouls() != null;
    }

    /**
     * Calculate fouls analysis from valid matches.
     */
    private FoulsAnalysisDTO calculateFoulsAnalysis(List<Match> matches, String teamName, boolean isHome) {
        int matchCount = matches.size();

        int totalFoulsCommitted = 0;
        int totalFoulsDrawn = 0;
        int lowFoulsMatches = 0;
        int highFoulsMatches = 0;
        int controlledMatches = 0;
        int winsWhenLowFouls = 0;
        int winsWhenHighFouls = 0;
        int winsWhenControlled = 0;

        for (Match match : matches) {
            // Determine fouls committed and drawn based on home/away perspective
            int foulsCommitted;
            int foulsDrawn;

            if (isHome) {
                foulsCommitted = match.getHomeFouls();
                foulsDrawn = match.getAwayFouls();
            } else {
                foulsCommitted = match.getAwayFouls();
                foulsDrawn = match.getHomeFouls();
            }

            totalFoulsCommitted += foulsCommitted;
            totalFoulsDrawn += foulsDrawn;

            // Determine if match was a win
            boolean isWin = isMatchWin(match, teamName, isHome);

            // Low fouls analysis (<10)
            if (foulsCommitted < LOW_FOULS_THRESHOLD) {
                lowFoulsMatches++;
                if (isWin) winsWhenLowFouls++;
            }

            // High fouls analysis (>15)
            if (foulsCommitted > HIGH_FOULS_THRESHOLD) {
                highFoulsMatches++;
                if (isWin) winsWhenHighFouls++;
            }

            // Controlled aggression analysis (<12)
            if (foulsCommitted < CONTROLLED_FOULS_THRESHOLD) {
                controlledMatches++;
                if (isWin) winsWhenControlled++;
            }
        }

        // Calculate averages (with divide-by-zero prevention)
        double avgFoulsCommitted = matchCount > 0 ? (double) totalFoulsCommitted / matchCount : 0.0;
        double avgFoulsDrawn = matchCount > 0 ? (double) totalFoulsDrawn / matchCount : 0.0;
        double foulsDifferential = avgFoulsDrawn - avgFoulsCommitted;

        // Calculate win rates (with divide-by-zero prevention)
        double winRateLowFouls = lowFoulsMatches > 0
                ? (double) winsWhenLowFouls / lowFoulsMatches * 100 : 0.0;
        double winRateHighFouls = highFoulsMatches > 0
                ? (double) winsWhenHighFouls / highFoulsMatches * 100 : 0.0;
        double winRateControlled = controlledMatches > 0
                ? (double) winsWhenControlled / controlledMatches * 100 : 0.0;

        // Calculate discipline score (0-10, dynamically normalized)
        double disciplineScore = calculateDisciplineScore(avgFoulsCommitted);
        String disciplineRating = getDisciplineRating(disciplineScore);

        return FoulsAnalysisDTO.builder()
                .teamName(teamName)
                .isHome(isHome)
                .matchesAnalyzed(matchCount)
                .avgFoulsCommitted(round(avgFoulsCommitted, 2))
                .avgFoulsDrawn(round(avgFoulsDrawn, 2))
                .foulsDifferential(round(foulsDifferential, 2))
                .disciplineScore(round(disciplineScore, 1))
                .disciplineRating(disciplineRating)
                .winRateWhenLowFouls(round(winRateLowFouls, 1))
                .winRateWhenHighFouls(round(winRateHighFouls, 1))
                .winRateWhenControlled(round(winRateControlled, 1))
                .totalFoulsCommitted(totalFoulsCommitted)
                .totalFoulsDrawn(totalFoulsDrawn)
                .lowFoulsMatchCount(lowFoulsMatches)
                .highFoulsMatchCount(highFoulsMatches)
                .controlledMatchCount(controlledMatches)
                .dataScope("Last " + matchCount + " Matches")
                .build();
    }

    /**
     * Check if the match was a win for the team.
     */
    private boolean isMatchWin(Match match, String teamName, boolean isHome) {
        String result = match.getFullTimeResult();
        if (result == null) return false;

        // Home win
        if (isHome && "H".equals(result)) return true;
        // Away win
        if (!isHome && "A".equals(result)) return true;

        return false;
    }

    /**
     * Calculate discipline score (0-10) based on fouls committed.
     * Lower fouls = higher score.
     * Uses z-score normalization based on league averages.
     */
    private double calculateDisciplineScore(double avgFoulsCommitted) {
        // Calculate z-score (how many std devs from mean)
        // Positive z-score = more fouls than average (bad)
        // Negative z-score = fewer fouls than average (good)
        double zScore = (avgFoulsCommitted - LEAGUE_AVG_FOULS) / FOULS_STD_DEV;

        // Convert to 0-10 scale where lower fouls = higher score
        // Z-score of -2 (very low fouls) -> score of 10
        // Z-score of 0 (average) -> score of 5
        // Z-score of +2 (very high fouls) -> score of 0
        double score = 5 - (zScore * 2.5);

        // Clamp to 0-10 range
        return Math.max(0, Math.min(10, score));
    }

    /**
     * Get discipline rating text based on score.
     */
    private String getDisciplineRating(double score) {
        if (score >= 8) return "Excellent";
        if (score >= 6) return "Good";
        if (score >= 4) return "Average";
        return "Poor";
    }

    /**
     * Build empty analysis DTO when no fouls data is available.
     */
    private FoulsAnalysisDTO buildEmptyAnalysis(String teamName, boolean isHome) {
        return FoulsAnalysisDTO.builder()
                .teamName(teamName)
                .isHome(isHome)
                .matchesAnalyzed(0)
                .avgFoulsCommitted(0.0)
                .avgFoulsDrawn(0.0)
                .foulsDifferential(0.0)
                .disciplineScore(5.0) // Default to average
                .disciplineRating("N/A")
                .winRateWhenLowFouls(0.0)
                .winRateWhenHighFouls(0.0)
                .winRateWhenControlled(0.0)
                .totalFoulsCommitted(0)
                .totalFoulsDrawn(0)
                .lowFoulsMatchCount(0)
                .highFoulsMatchCount(0)
                .controlledMatchCount(0)
                .dataScope("No Data Available")
                .build();
    }

    /**
     * Round a double to specified decimal places.
     */
    private double round(double value, int places) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}

