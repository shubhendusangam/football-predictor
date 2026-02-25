package com.app.footballprediction.service;

import com.app.common.dto.ShotQualityDTO;
import com.app.common.dto.ShotQualityDTO.LeagueComparison;
import com.app.common.dto.ShotQualityDTO.ShotTrendPoint;
import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for calculating shot quality metrics and analysis.
 * Provides comprehensive shot efficiency statistics for teams.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShotQualityService {

    private final MatchRepository matchRepository;

    // League average constants (Premier League typical values)
    private static final double LEAGUE_AVG_SHOT_ACCURACY = 0.32; // 32%
    private static final double LEAGUE_AVG_CONVERSION_RATE = 0.28;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int TREND_MATCHES_LIMIT = 10;

    /**
     * Calculate shot quality metrics for a team.
     *
     * @param teamName The team name to analyze
     * @param isHome   true for home matches only, false for away matches only
     * @return ShotQualityDTO with comprehensive shot metrics
     */
    @Cacheable(value = "shotQuality", key = "#teamName.toLowerCase() + '_' + #isHome")
    public ShotQualityDTO calculateShotQuality(String teamName, boolean isHome) {
        log.info("Calculating shot quality for {} (isHome={})", teamName, isHome);
        long startTime = System.currentTimeMillis();

        try {
            // Resolve team name
            String resolvedTeamName = resolveTeamName(teamName);
            LocalDate beforeDate = LocalDate.now().plusDays(1);

            // Fetch matches based on home/away
            List<Match> matches = isHome
                    ? matchRepository.findHomeMatchesByTeamBeforeDate(resolvedTeamName, beforeDate)
                    : matchRepository.findAwayMatchesByTeamBeforeDate(resolvedTeamName, beforeDate);

            // Filter matches that have shot data
            List<Match> matchesWithShotData = matches.stream()
                    .filter(m -> hasValidShotData(m, isHome))
                    .collect(Collectors.toList());

            if (matchesWithShotData.isEmpty()) {
                log.warn("No matches with shot data found for {} (isHome={})", resolvedTeamName, isHome);
                return buildEmptyResponse(resolvedTeamName, isHome);
            }

            // Calculate totals
            int totalShots = 0;
            int totalShotsOnTarget = 0;
            int totalGoals = 0;

            for (Match match : matchesWithShotData) {
                if (isHome) {
                    totalShots += safeInt(match.getHomeShots());
                    totalShotsOnTarget += safeInt(match.getHomeShotsOnTarget());
                    totalGoals += safeInt(match.getFullTimeHomeGoals());
                } else {
                    totalShots += safeInt(match.getAwayShots());
                    totalShotsOnTarget += safeInt(match.getAwayShotsOnTarget());
                    totalGoals += safeInt(match.getFullTimeAwayGoals());
                }
            }

            int matchCount = matchesWithShotData.size();

            // Calculate averages
            double avgShots = (double) totalShots / matchCount;
            double avgShotsOnTarget = (double) totalShotsOnTarget / matchCount;

            // Calculate efficiency metrics
            double shotAccuracy = totalShots > 0 ? (double) totalShotsOnTarget / totalShots : 0.0;
            double conversionRate = totalShotsOnTarget > 0 ? (double) totalGoals / totalShotsOnTarget : 0.0;

            // Calculate quality score: (shotAccuracy × 10 × 0.4) + (conversionRate × 10 × 0.6)
            // Cap shotAccuracy at 1.0 and conversionRate at 1.0 to keep score in 0-10 range
            double cappedShotAccuracy = Math.min(shotAccuracy, 1.0);
            double cappedConversionRate = Math.min(conversionRate, 1.0);
            double qualityScore = (cappedShotAccuracy * 10 * 0.4) + (cappedConversionRate * 10 * 0.6);

            // Build trend data (last 10 matches)
            List<ShotTrendPoint> shotsTrend = buildShotsTrend(matchesWithShotData, isHome);

            // Build league comparison
            LeagueComparison leagueComparison = buildLeagueComparison(shotAccuracy, conversionRate);

            // Determine rating
            String rating = ShotQualityDTO.calculateRating(qualityScore);

            ShotQualityDTO result = ShotQualityDTO.builder()
                    .teamName(resolvedTeamName)
                    .avgShots(round(avgShots, 2))
                    .avgShotsOnTarget(round(avgShotsOnTarget, 2))
                    .shotAccuracy(round(shotAccuracy * 100, 2)) // Convert to percentage
                    .conversionRate(round(conversionRate, 3))
                    .qualityScore(round(qualityScore, 2))
                    .rating(rating)
                    .isHome(isHome)
                    .matchesAnalyzed(matchCount)
                    .totalGoals(totalGoals)
                    .totalShots(totalShots)
                    .totalShotsOnTarget(totalShotsOnTarget)
                    .shotsTrend(shotsTrend)
                    .leagueComparison(leagueComparison)
                    .build();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Calculated shot quality for {} (isHome={}) in {}ms: score={}, rating={}",
                    resolvedTeamName, isHome, duration, qualityScore, rating);

            return result;

        } catch (IllegalArgumentException e) {
            log.warn("Team not found for shot quality: {}", teamName);
            throw e;
        } catch (Exception e) {
            log.error("Failed to calculate shot quality for {}: {}", teamName, e.getMessage(), e);
            throw new RuntimeException("Failed to calculate shot quality", e);
        }
    }

    /**
     * Calculate combined shot quality for a team (both home and away).
     *
     * @param teamName The team name to analyze
     * @return ShotQualityDTO with combined home and away metrics
     */
    @Cacheable(value = "shotQuality", key = "#teamName.toLowerCase() + '_combined'")
    public ShotQualityDTO calculateCombinedShotQuality(String teamName) {
        log.info("Calculating combined shot quality for {}", teamName);
        long startTime = System.currentTimeMillis();

        try {
            String resolvedTeamName = resolveTeamName(teamName);
            LocalDate beforeDate = LocalDate.now().plusDays(1);

            // Fetch all matches
            List<Match> allMatches = matchRepository.findByTeamBeforeDate(resolvedTeamName, beforeDate);

            // Filter matches that have shot data and separate by home/away
            List<Match> matchesWithShotData = new ArrayList<>();
            int totalShots = 0;
            int totalShotsOnTarget = 0;
            int totalGoals = 0;

            for (Match match : allMatches) {
                boolean isHome = match.getHomeTeam().equalsIgnoreCase(resolvedTeamName);
                if (hasValidShotData(match, isHome)) {
                    matchesWithShotData.add(match);
                    if (isHome) {
                        totalShots += safeInt(match.getHomeShots());
                        totalShotsOnTarget += safeInt(match.getHomeShotsOnTarget());
                        totalGoals += safeInt(match.getFullTimeHomeGoals());
                    } else {
                        totalShots += safeInt(match.getAwayShots());
                        totalShotsOnTarget += safeInt(match.getAwayShotsOnTarget());
                        totalGoals += safeInt(match.getFullTimeAwayGoals());
                    }
                }
            }

            if (matchesWithShotData.isEmpty()) {
                log.warn("No matches with shot data found for {}", resolvedTeamName);
                return buildEmptyResponse(resolvedTeamName, null);
            }

            int matchCount = matchesWithShotData.size();

            // Calculate averages
            double avgShots = (double) totalShots / matchCount;
            double avgShotsOnTarget = (double) totalShotsOnTarget / matchCount;

            // Calculate efficiency metrics
            double shotAccuracy = totalShots > 0 ? (double) totalShotsOnTarget / totalShots : 0.0;
            double conversionRate = totalShotsOnTarget > 0 ? (double) totalGoals / totalShotsOnTarget : 0.0;

            // Calculate quality score
            double cappedShotAccuracy = Math.min(shotAccuracy, 1.0);
            double cappedConversionRate = Math.min(conversionRate, 1.0);
            double qualityScore = (cappedShotAccuracy * 10 * 0.4) + (cappedConversionRate * 10 * 0.6);

            // Build trend data
            List<ShotTrendPoint> shotsTrend = buildCombinedShotsTrend(matchesWithShotData, resolvedTeamName);

            // Build league comparison
            LeagueComparison leagueComparison = buildLeagueComparison(shotAccuracy, conversionRate);

            String rating = ShotQualityDTO.calculateRating(qualityScore);

            ShotQualityDTO result = ShotQualityDTO.builder()
                    .teamName(resolvedTeamName)
                    .avgShots(round(avgShots, 2))
                    .avgShotsOnTarget(round(avgShotsOnTarget, 2))
                    .shotAccuracy(round(shotAccuracy * 100, 2))
                    .conversionRate(round(conversionRate, 3))
                    .qualityScore(round(qualityScore, 2))
                    .rating(rating)
                    .isHome(null) // Combined stats
                    .matchesAnalyzed(matchCount)
                    .totalGoals(totalGoals)
                    .totalShots(totalShots)
                    .totalShotsOnTarget(totalShotsOnTarget)
                    .shotsTrend(shotsTrend)
                    .leagueComparison(leagueComparison)
                    .build();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Calculated combined shot quality for {} in {}ms: score={}, rating={}",
                    resolvedTeamName, duration, qualityScore, rating);

            return result;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to calculate combined shot quality for {}: {}", teamName, e.getMessage(), e);
            throw new RuntimeException("Failed to calculate shot quality", e);
        }
    }

    /**
     * Resolve team name using case-insensitive matching.
     */
    private String resolveTeamName(String teamName) {
        if (teamName == null || teamName.trim().isEmpty()) {
            throw new IllegalArgumentException("Team name cannot be empty");
        }

        String trimmedName = teamName.trim();
        LocalDate beforeDate = LocalDate.now().plusDays(1);

        // Try exact match first
        List<Match> exactMatches = matchRepository.findByTeamBeforeDate(trimmedName, beforeDate);
        if (!exactMatches.isEmpty()) {
            return trimmedName;
        }

        // Try case-insensitive match
        List<Match> caseInsensitiveMatches = matchRepository.findByTeamBeforeDateIgnoreCase(trimmedName, beforeDate);
        if (!caseInsensitiveMatches.isEmpty()) {
            Match firstMatch = caseInsensitiveMatches.get(0);
            return firstMatch.getHomeTeam().equalsIgnoreCase(trimmedName)
                    ? firstMatch.getHomeTeam()
                    : firstMatch.getAwayTeam();
        }

        // Try fuzzy match
        List<String> similarTeams = matchRepository.findTeamNamesContaining(trimmedName);
        if (!similarTeams.isEmpty()) {
            log.info("Resolved '{}' to '{}' (fuzzy match)", trimmedName, similarTeams.get(0));
            return similarTeams.get(0);
        }

        throw new IllegalArgumentException("Team not found: " + trimmedName +
                ". Use GET /api/teams to see available teams.");
    }

    /**
     * Check if a match has valid shot data.
     */
    private boolean hasValidShotData(Match match, boolean isHome) {
        if (match.getFullTimeResult() == null) {
            return false;
        }
        if (isHome) {
            return match.getHomeShots() != null && match.getHomeShotsOnTarget() != null;
        } else {
            return match.getAwayShots() != null && match.getAwayShotsOnTarget() != null;
        }
    }

    /**
     * Build shots trend data for the last N matches.
     */
    private List<ShotTrendPoint> buildShotsTrend(List<Match> matches, boolean isHome) {
        return matches.stream()
                .limit(TREND_MATCHES_LIMIT)
                .map(match -> {
                    int shots = isHome ? safeInt(match.getHomeShots()) : safeInt(match.getAwayShots());
                    int shotsOnTarget = isHome ? safeInt(match.getHomeShotsOnTarget()) : safeInt(match.getAwayShotsOnTarget());
                    int goals = isHome ? safeInt(match.getFullTimeHomeGoals()) : safeInt(match.getFullTimeAwayGoals());
                    String opponent = isHome ? match.getAwayTeam() : match.getHomeTeam();
                    double accuracy = shots > 0 ? (double) shotsOnTarget / shots * 100 : 0.0;

                    return ShotTrendPoint.builder()
                            .matchDate(match.getMatchDate() != null ? match.getMatchDate().format(DATE_FORMATTER) : null)
                            .opponent(opponent)
                            .shots(shots)
                            .shotsOnTarget(shotsOnTarget)
                            .goals(goals)
                            .shotAccuracy(round(accuracy, 1))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Build combined shots trend data for the last N matches.
     */
    private List<ShotTrendPoint> buildCombinedShotsTrend(List<Match> matches, String teamName) {
        return matches.stream()
                .limit(TREND_MATCHES_LIMIT)
                .map(match -> {
                    boolean isHome = match.getHomeTeam().equalsIgnoreCase(teamName);
                    int shots = isHome ? safeInt(match.getHomeShots()) : safeInt(match.getAwayShots());
                    int shotsOnTarget = isHome ? safeInt(match.getHomeShotsOnTarget()) : safeInt(match.getAwayShotsOnTarget());
                    int goals = isHome ? safeInt(match.getFullTimeHomeGoals()) : safeInt(match.getFullTimeAwayGoals());
                    String opponent = isHome ? match.getAwayTeam() : match.getHomeTeam();
                    double accuracy = shots > 0 ? (double) shotsOnTarget / shots * 100 : 0.0;

                    return ShotTrendPoint.builder()
                            .matchDate(match.getMatchDate() != null ? match.getMatchDate().format(DATE_FORMATTER) : null)
                            .opponent(opponent)
                            .shots(shots)
                            .shotsOnTarget(shotsOnTarget)
                            .goals(goals)
                            .shotAccuracy(round(accuracy, 1))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Build league comparison data.
     */
    private LeagueComparison buildLeagueComparison(double shotAccuracy, double conversionRate) {
        double shotAccuracyDiff = shotAccuracy - LEAGUE_AVG_SHOT_ACCURACY;
        double conversionRateDiff = conversionRate - LEAGUE_AVG_CONVERSION_RATE;

        return LeagueComparison.builder()
                .leagueAvgShotAccuracy(round(LEAGUE_AVG_SHOT_ACCURACY * 100, 1))
                .leagueAvgConversionRate(round(LEAGUE_AVG_CONVERSION_RATE, 2))
                .shotAccuracyDiff(round(shotAccuracyDiff * 100, 2))
                .conversionRateDiff(round(conversionRateDiff, 3))
                .aboveAvgShotAccuracy(shotAccuracyDiff > 0)
                .aboveAvgConversion(conversionRateDiff > 0)
                .build();
    }

    /**
     * Build empty response when no data is available.
     */
    private ShotQualityDTO buildEmptyResponse(String teamName, Boolean isHome) {
        return ShotQualityDTO.builder()
                .teamName(teamName)
                .avgShots(0.0)
                .avgShotsOnTarget(0.0)
                .shotAccuracy(0.0)
                .conversionRate(0.0)
                .qualityScore(0.0)
                .rating("N/A")
                .isHome(isHome)
                .matchesAnalyzed(0)
                .totalGoals(0)
                .totalShots(0)
                .totalShotsOnTarget(0)
                .shotsTrend(List.of())
                .leagueComparison(buildLeagueComparison(0.0, 0.0))
                .build();
    }

    /**
     * Safe integer extraction from nullable Integer.
     */
    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    /**
     * Round a double to specified decimal places.
     */
    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}

