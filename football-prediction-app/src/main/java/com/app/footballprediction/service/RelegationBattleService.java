package com.app.footballprediction.service;

import com.app.common.model.LeagueStanding;
import com.app.common.repository.LeagueStandingRepository;
import com.app.footballprediction.config.CacheConfig;
import com.app.footballprediction.dto.RelegationBattleAnalysisDTO;
import com.app.footballprediction.dto.RelegationBattleAnalysisDTO.RelegationSummary;
import com.app.footballprediction.dto.RelegationBattleDTO;
import com.app.footballprediction.util.SeasonUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for analyzing the relegation battle (fight to avoid drop).
 * Provides survival probability calculations, gap analysis, and status indicators.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RelegationBattleService {

    private final LeagueStandingRepository standingRepository;
    private final LeagueStandingService leagueStandingService;
    private final TeamService teamService;

    /**
     * Total matches in a typical Premier League season.
     */
    private static final int TOTAL_SEASON_MATCHES = 38;

    /**
     * Safety line position (17th is last safe position).
     */
    private static final int SAFETY_LINE_POSITION = 17;

    /**
     * First relegation position.
     */
    private static final int RELEGATION_START_POSITION = 18;

    /**
     * Historical average points needed for survival.
     */
    private static final int DEFAULT_SURVIVAL_TARGET = 38;

    /**
     * Minimum points typically needed to avoid relegation.
     */
    private static final int MIN_SURVIVAL_THRESHOLD = 35;

    /**
     * Safe points threshold (teams above this are considered safe).
     */
    private static final int SAFE_POINTS_THRESHOLD = 40;

    /**
     * Number of teams to analyze (positions 14-20).
     */
    private static final int FIRST_BATTLE_POSITION = 14;

    /**
     * Analyze the relegation battle for a given season.
     *
     * @param season   Season identifier (e.g., "2025-26")
     * @param asOfDate Date to calculate analysis as of (defaults to today)
     * @return Complete relegation battle analysis
     */
    @Cacheable(value = CacheConfig.CACHE_RELEGATION_BATTLE, key = "'battle_' + #season + '_' + #asOfDate")
    @Transactional(readOnly = true)
    public RelegationBattleAnalysisDTO analyzeRelegationBattle(String season, LocalDate asOfDate) {
        String normalizedSeason = SeasonUtils.normalizeSeason(season);
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();

        log.info("Analyzing relegation battle for season {} as of {}", normalizedSeason, effectiveDate);

        // Get league standings - use default league ID (1 = Premier League)
        Long leagueId = leagueStandingService.getDefaultLeagueId();
        List<LeagueStanding> standings = standingRepository
                .findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(leagueId, normalizedSeason);

        // Try alternate format if no standings found
        if (standings.isEmpty()) {
            String altSeason = normalizedSeason.replace("-", "/");
            standings = standingRepository
                    .findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(leagueId, altSeason);
        }

        // If still empty, try to calculate from matches
        if (standings.isEmpty()) {
            log.info("No standings found, calculating from matches...");
            standings = leagueStandingService.calculateStandingsFromMatches(leagueId, normalizedSeason);
        }

        if (standings.isEmpty()) {
            log.warn("No standings available for season {}", normalizedSeason);
            return buildEmptyResponse(normalizedSeason, effectiveDate);
        }

        // Sort by points (descending) to ensure correct positions
        standings.sort(Comparator
                .comparingInt((LeagueStanding s) -> s.getPoints() != null ? s.getPoints() : 0).reversed()
                .thenComparingInt(s -> s.getGoalDifference() != null ? s.getGoalDifference() : 0).reversed()
                .thenComparingInt(s -> s.getGoalsFor() != null ? s.getGoalsFor() : 0).reversed());

        // Assign positions if not set
        for (int i = 0; i < standings.size(); i++) {
            standings.get(i).setPosition(i + 1);
        }

        int totalTeams = standings.size();
        if (totalTeams < 18) {
            log.warn("Not enough teams for relegation analysis (need at least 18, got {})", totalTeams);
            return buildEmptyResponse(normalizedSeason, effectiveDate);
        }

        // Get key position points
        int safetyLinePoints = standings.get(SAFETY_LINE_POSITION - 1).getPoints() != null
                ? standings.get(SAFETY_LINE_POSITION - 1).getPoints() : 0;
        int relegationLinePoints = standings.get(RELEGATION_START_POSITION - 1).getPoints() != null
                ? standings.get(RELEGATION_START_POSITION - 1).getPoints() : 0;

        // Calculate matchdays completed and remaining
        int maxPlayed = standings.stream()
                .mapToInt(s -> s.getPlayed() != null ? s.getPlayed() : 0)
                .max()
                .orElse(0);
        int remainingMatches = TOTAL_SEASON_MATCHES - maxPlayed;
        double seasonProgress = (double) maxPlayed / TOTAL_SEASON_MATCHES * 100;

        // Estimate survival points target based on current 17th place pace
        int survivalTarget = estimateSurvivalTarget(safetyLinePoints, remainingMatches, effectiveDate);

        // Get teams in relegation battle (positions 14-20)
        List<LeagueStanding> battleTeams = standings.stream()
                .filter(s -> s.getPosition() != null && s.getPosition() >= FIRST_BATTLE_POSITION && s.getPosition() <= totalTeams)
                .collect(Collectors.toList());

        // Build team DTOs
        List<RelegationBattleDTO> teamsInBattle = new ArrayList<>();
        for (LeagueStanding standing : battleTeams) {
            RelegationBattleDTO teamBattle = buildTeamBattleDTO(
                    standing, safetyLinePoints, relegationLinePoints,
                    remainingMatches, survivalTarget, effectiveDate
            );
            teamsInBattle.add(teamBattle);
        }

        // Build summary
        RelegationSummary summary = buildRelegationSummary(teamsInBattle, safetyLinePoints,
                relegationLinePoints, remainingMatches);

        return RelegationBattleAnalysisDTO.builder()
                .season(normalizedSeason)
                .asOfDate(effectiveDate)
                .teamsInBattle(teamsInBattle)
                .survivalPointsTarget(survivalTarget)
                .totalMatchesInSeason(TOTAL_SEASON_MATCHES)
                .matchdaysCompleted(maxPlayed)
                .seasonProgressPercent(Math.round(seasonProgress * 10) / 10.0)
                .summary(summary)
                .lastUpdated(effectiveDate.atStartOfDay().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    /**
     * Analyze relegation battle using current date (convenience method).
     */
    public RelegationBattleAnalysisDTO analyzeRelegationBattle(String season) {
        return analyzeRelegationBattle(season, LocalDate.now());
    }

    /**
     * Build a Team Battle DTO with all calculations.
     */
    private RelegationBattleDTO buildTeamBattleDTO(
            LeagueStanding standing,
            int safetyLinePoints,
            int relegationLinePoints,
            int remainingMatches,
            int survivalTarget,
            LocalDate asOfDate
    ) {
        int points = standing.getPoints() != null ? standing.getPoints() : 0;
        int played = standing.getPlayed() != null ? standing.getPlayed() : 0;
        int won = standing.getWon() != null ? standing.getWon() : 0;
        int position = standing.getPosition() != null ? standing.getPosition() : 0;

        // Calculate gaps
        int gapToSafety = points - safetyLinePoints; // Positive if above 17th
        int gapToRelegation = points - relegationLinePoints; // Positive if above 18th

        // Calculate points needed for survival
        int pointsNeeded = Math.max(0, survivalTarget - points);

        // Calculate win rate
        double winRate = played > 0 ? (double) won / played * 100 : 0;

        // Calculate points per game
        double ppg = played > 0 ? (double) points / played : 0;

        // Calculate survival probability
        double probability = calculateSurvivalProbability(
                position, points, remainingMatches, survivalTarget, winRate, gapToSafety, asOfDate
        );

        // Determine status
        String status = determineStatus(position, probability, points, gapToSafety, remainingMatches);

        // Determine desperation level
        String desperation = determineDesperationLevel(position, probability, gapToSafety, remainingMatches, asOfDate);

        // Get team logo
        String teamLogo = teamService.getTeamLogoUrl(standing.getTeamName());

        return RelegationBattleDTO.builder()
                .teamName(standing.getTeamName())
                .teamLogo(teamLogo)
                .currentPosition(position)
                .points(points)
                .gapToSafety(gapToSafety)
                .gapToRelegation(gapToRelegation)
                .remainingMatches(remainingMatches)
                .pointsNeededForSafety(pointsNeeded)
                .survivalProbability(Math.round(probability * 10) / 10.0)
                .status(status)
                .desperationLevel(desperation)
                .winRate(Math.round(winRate * 10) / 10.0)
                .goalDifference(standing.getGoalDifference() != null ? standing.getGoalDifference() : 0)
                .form(standing.getForm() != null ? standing.getForm() : "")
                .pointsPerGame(Math.round(ppg * 100) / 100.0)
                .played(played)
                .build();
    }

    /**
     * Calculate probability of survival (avoiding relegation).
     * Uses multiple factors:
     * - Current position relative to relegation zone
     * - Points gap to safety
     * - Remaining matches and points potential
     * - Historical escape rates
     * - Time of season (late season escapes are harder)
     */
    private double calculateSurvivalProbability(
            int position, int points, int remainingMatches,
            int survivalTarget, double winRate, int gapToSafety, LocalDate asOfDate
    ) {
        // Teams with 40+ points are essentially safe
        if (points >= SAFE_POINTS_THRESHOLD) {
            return 100.0;
        }

        // Base probability from position
        double positionProbability;
        if (position <= 14) {
            positionProbability = 95; // Very safe
        } else if (position == 15) {
            positionProbability = 85;
        } else if (position == 16) {
            positionProbability = 70;
        } else if (position == 17) {
            positionProbability = 55; // On the edge
        } else if (position == 18) {
            positionProbability = 35;
        } else if (position == 19) {
            positionProbability = 20;
        } else {
            positionProbability = 10; // 20th place
        }

        // Maximum points still achievable
        int maxPossiblePoints = points + (remainingMatches * 3);
        int pointsNeeded = Math.max(0, survivalTarget - points);

        // Gap factor - how far above/below safety line
        double gapFactor;
        if (gapToSafety >= 8) {
            gapFactor = 1.3; // Comfortable margin
        } else if (gapToSafety >= 4) {
            gapFactor = 1.15;
        } else if (gapToSafety >= 1) {
            gapFactor = 1.05;
        } else if (gapToSafety >= 0) {
            gapFactor = 1.0; // On the line
        } else if (gapToSafety >= -3) {
            gapFactor = 0.85;
        } else if (gapToSafety >= -6) {
            gapFactor = 0.65;
        } else if (gapToSafety >= -9) {
            gapFactor = 0.45;
        } else {
            gapFactor = Math.max(0.1, 0.35 - (Math.abs(gapToSafety) - 9) * 0.03);
        }

        // Points achievability factor
        double achievabilityFactor;
        if (maxPossiblePoints < survivalTarget - 5) {
            // Very difficult to reach safety
            achievabilityFactor = 0.3 * (double) maxPossiblePoints / survivalTarget;
        } else if (pointsNeeded == 0) {
            // Already at or above survival target
            achievabilityFactor = 1.2;
        } else {
            // Calculate based on wins needed vs remaining matches
            double winsNeeded = (double) pointsNeeded / 3;
            achievabilityFactor = Math.min(1.0, (double) remainingMatches / (winsNeeded * 1.5));
        }

        // Season timing factor - harder to escape late in season
        double timingFactor = 1.0;
        if (asOfDate != null) {
            Month month = asOfDate.getMonth();
            if (month == Month.APRIL || month == Month.MAY) {
                // Late season - escapes are harder, pressure is higher
                timingFactor = 0.85;
                if (remainingMatches <= 5) {
                    timingFactor = 0.7; // Crunch time
                }
            }

            // March check for teams with <20 points (validation requirement)
            if (month == Month.MARCH && points < 20) {
                // Should have <10% survival chance
                return Math.min(10.0, positionProbability * 0.1);
            }
        }

        // Win rate projection factor
        double projectionFactor = 0.6 + (winRate / 100) * 0.8; // Range: 0.6 to 1.4

        // Combine factors
        double probability = positionProbability * gapFactor * achievabilityFactor * timingFactor;
        probability = probability * 0.8 + (probability * projectionFactor * 0.2);

        // Apply bounds
        if (points >= SAFE_POINTS_THRESHOLD) {
            probability = 100;
        }

        // Clamp to 0-100 range
        probability = Math.max(0, Math.min(100, probability));

        return probability;
    }

    /**
     * Determine team's current status in the relegation battle.
     */
    private String determineStatus(int position, double probability, int points, int gapToSafety, int remainingMatches) {
        int maxCatchUp = remainingMatches * 3;

        // Teams with 40+ points are safe
        if (points >= SAFE_POINTS_THRESHOLD || probability >= 95) {
            return "Safe";
        }

        // Mathematically relegated (can't catch safety line)
        if (position >= RELEGATION_START_POSITION && gapToSafety < 0 && Math.abs(gapToSafety) > maxCatchUp) {
            return "Relegated";
        }

        // In danger zone with low probability
        if (position >= RELEGATION_START_POSITION || probability < 30) {
            return "Danger";
        }

        // Fighting but not in immediate danger
        if (probability < 70 || position >= 15) {
            return "Fighting";
        }

        return "Safe";
    }

    /**
     * Determine team's desperation level.
     */
    private String determineDesperationLevel(int position, double probability, int gapToSafety, int remainingMatches, LocalDate asOfDate) {
        // Season timing matters
        boolean isLateseason = false;
        if (asOfDate != null) {
            Month month = asOfDate.getMonth();
            isLateseason = month == Month.APRIL || month == Month.MAY;
        }

        // Extreme: In relegation zone with very low probability
        if (position >= RELEGATION_START_POSITION && probability < 20) {
            return "Extreme";
        }

        // High: Below safety or just above with decreasing probability
        if ((position >= 17 && probability < 50) ||
            (gapToSafety < 0 && isLateseason) ||
            (position >= 16 && remainingMatches <= 5 && probability < 60)) {
            return "High";
        }

        // Medium: Fighting positions with uncertain outcome
        if (position >= 15 && probability < 70) {
            return "Medium";
        }

        // Low: Relatively safe but still monitoring
        return "Low";
    }

    /**
     * Estimate the survival target based on current standings pace.
     */
    private int estimateSurvivalTarget(int currentSafetyPoints, int remainingMatches, LocalDate asOfDate) {
        if (remainingMatches <= 0) {
            // Season over, survival target is actual 17th place points
            return currentSafetyPoints;
        }

        // Historical average is around 35-40 points for 17th place
        int historicalTarget = DEFAULT_SURVIVAL_TARGET;

        // Project 17th place finish based on current pace
        int matchesPlayed = TOTAL_SEASON_MATCHES - remainingMatches;
        if (matchesPlayed > 0) {
            double ppgSafety = (double) currentSafetyPoints / matchesPlayed;
            int projectedSafety = (int) Math.round(currentSafetyPoints + (ppgSafety * remainingMatches));

            // Take weighted average of projection and historical
            // Weight projection more heavily as season progresses
            double projectionWeight = Math.min(0.7, (double) matchesPlayed / TOTAL_SEASON_MATCHES);
            return (int) Math.round(projectedSafety * projectionWeight + historicalTarget * (1 - projectionWeight));
        }

        return historicalTarget;
    }

    /**
     * Build the relegation battle summary.
     */
    private RelegationSummary buildRelegationSummary(List<RelegationBattleDTO> teams, int safetyLinePoints,
                                                      int relegationLinePoints, int remainingMatches) {
        if (teams.isEmpty()) {
            return RelegationSummary.builder()
                    .teamsInDanger(0)
                    .teamsRelegated(0)
                    .teamsSafe(0)
                    .safetyLinePoints(safetyLinePoints)
                    .relegationLinePoints(relegationLinePoints)
                    .gapAtRelegationLine(safetyLinePoints - relegationLinePoints)
                    .intensity("Unknown")
                    .mostLikelyToGoDown("")
                    .bestEscapeChance("")
                    .build();
        }

        // Count teams by status
        int teamsInDanger = (int) teams.stream()
                .filter(t -> "Danger".equals(t.getStatus()) || "Relegated".equals(t.getStatus()))
                .count();
        int teamsRelegated = (int) teams.stream()
                .filter(t -> "Relegated".equals(t.getStatus()))
                .count();
        int teamsSafe = (int) teams.stream()
                .filter(t -> "Safe".equals(t.getStatus()))
                .count();

        int gap = safetyLinePoints - relegationLinePoints;

        // Find most likely to be relegated (lowest survival probability in bottom 3)
        String mostLikely = teams.stream()
                .filter(t -> t.getCurrentPosition() >= RELEGATION_START_POSITION)
                .min(Comparator.comparingDouble(RelegationBattleDTO::getSurvivalProbability))
                .map(RelegationBattleDTO::getTeamName)
                .orElse("");

        // Find best escape chance (highest survival probability in relegation zone)
        String bestEscape = teams.stream()
                .filter(t -> t.getCurrentPosition() >= RELEGATION_START_POSITION)
                .max(Comparator.comparingDouble(RelegationBattleDTO::getSurvivalProbability))
                .map(RelegationBattleDTO::getTeamName)
                .orElse("");

        // Determine intensity
        String intensity;
        if (teamsRelegated >= 2) {
            intensity = "Calm"; // Most spots decided
        } else if (gap >= 6 && teamsInDanger <= 3) {
            intensity = "Calm";
        } else if (gap >= 3 || teamsInDanger <= 4) {
            intensity = "Tense";
        } else if (gap <= 2 && teamsInDanger >= 5) {
            intensity = "Dramatic";
        } else {
            intensity = "Critical";
        }

        return RelegationSummary.builder()
                .teamsInDanger(teamsInDanger)
                .teamsRelegated(teamsRelegated)
                .teamsSafe(teamsSafe)
                .safetyLinePoints(safetyLinePoints)
                .relegationLinePoints(relegationLinePoints)
                .gapAtRelegationLine(gap)
                .intensity(intensity)
                .mostLikelyToGoDown(mostLikely)
                .bestEscapeChance(bestEscape)
                .build();
    }

    /**
     * Build empty response when no data available.
     */
    private RelegationBattleAnalysisDTO buildEmptyResponse(String season, LocalDate asOfDate) {
        return RelegationBattleAnalysisDTO.builder()
                .season(season)
                .asOfDate(asOfDate)
                .teamsInBattle(new ArrayList<>())
                .survivalPointsTarget(DEFAULT_SURVIVAL_TARGET)
                .totalMatchesInSeason(TOTAL_SEASON_MATCHES)
                .matchdaysCompleted(0)
                .seasonProgressPercent(0)
                .summary(RelegationSummary.builder()
                        .teamsInDanger(0)
                        .teamsRelegated(0)
                        .teamsSafe(0)
                        .safetyLinePoints(0)
                        .relegationLinePoints(0)
                        .gapAtRelegationLine(0)
                        .intensity("Not Started")
                        .mostLikelyToGoDown("")
                        .bestEscapeChance("")
                        .build())
                .lastUpdated(asOfDate.atStartOfDay().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }
}

