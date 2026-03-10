package com.app.footballprediction.service;

import com.app.common.model.LeagueStanding;
import com.app.common.repository.LeagueStandingRepository;
import com.app.footballprediction.config.CacheConfig;
import com.app.footballprediction.dto.Top4RaceAnalysisDTO;
import com.app.footballprediction.dto.Top4RaceAnalysisDTO.TitleRaceSummary;
import com.app.footballprediction.dto.Top4RaceDTO;
import com.app.footballprediction.util.SeasonUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for analyzing the Champions League (Top 4) race and title battles.
 * Provides probability calculations, gap analysis, and status indicators.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Top4RaceService {

    private final LeagueStandingRepository standingRepository;
    private final LeagueStandingService leagueStandingService;
    private final TeamService teamService;

    /**
     * Total matches in a typical Premier League season.
     */
    private static final int TOTAL_SEASON_MATCHES = 38;

    /**
     * Target points for safe top 4 finish (historical average).
     */
    private static final int TOP_4_SAFETY_THRESHOLD = 72;

    /**
     * High probability threshold for top 4.
     */
    private static final int HIGH_POINTS_THRESHOLD = 70;

    /**
     * Points gap considered "mathematically safe" for top 4.
     */
    private static final int SAFE_GAP_THRESHOLD = 9;

    /**
     * Analyze the Top 4 race for a given season.
     *
     * @param season   Season identifier (e.g., "2025-26")
     * @param asOfDate Date to calculate analysis as of (defaults to today)
     * @return Complete Top 4 race analysis
     */
    @Cacheable(value = CacheConfig.CACHE_TOP4_RACE, key = "'race_' + #season + '_' + #asOfDate")
    @Transactional(readOnly = true)
    public Top4RaceAnalysisDTO analyzeTop4Race(String season, LocalDate asOfDate) {
        String normalizedSeason = SeasonUtils.normalizeSeason(season);
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();

        log.info("Analyzing Top 4 race for season {} as of {}", normalizedSeason, effectiveDate);

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

        // Get top 10 teams for the race analysis
        List<LeagueStanding> topTeams = standings.stream()
                .limit(10)
                .collect(Collectors.toList());

        // Calculate key position points
        int firstPlacePoints = topTeams.isEmpty() ? 0 : topTeams.get(0).getPoints();
        int fourthPlacePoints = topTeams.size() >= 4 ? topTeams.get(3).getPoints() : 0;
        int fifthPlacePoints = topTeams.size() >= 5 ? topTeams.get(4).getPoints() : 0;

        // Calculate matchdays completed and remaining
        int maxPlayed = topTeams.stream()
                .mapToInt(s -> s.getPlayed() != null ? s.getPlayed() : 0)
                .max()
                .orElse(0);
        int remainingMatches = TOTAL_SEASON_MATCHES - maxPlayed;
        double seasonProgress = (double) maxPlayed / TOTAL_SEASON_MATCHES * 100;

        // Estimate points needed for safety
        int pointsForSafety = estimateSafetyThreshold(fourthPlacePoints, remainingMatches);

        // Build team race DTOs
        List<Top4RaceDTO> teamsInRace = new ArrayList<>();
        for (int i = 0; i < topTeams.size() && i < 10; i++) {
            LeagueStanding standing = topTeams.get(i);
            Top4RaceDTO teamRace = buildTeamRaceDTO(
                    standing, i + 1, firstPlacePoints, fourthPlacePoints, fifthPlacePoints,
                    remainingMatches, pointsForSafety
            );
            teamsInRace.add(teamRace);
        }

        // Build title race summary
        TitleRaceSummary titleRace = buildTitleRaceSummary(topTeams, remainingMatches);

        return Top4RaceAnalysisDTO.builder()
                .season(normalizedSeason)
                .asOfDate(effectiveDate)
                .teamsInRace(teamsInRace)
                .pointsForSafety(pointsForSafety)
                .totalMatchesInSeason(TOTAL_SEASON_MATCHES)
                .matchdaysCompleted(maxPlayed)
                .seasonProgressPercent(Math.round(seasonProgress * 10) / 10.0)
                .titleRace(titleRace)
                .lastUpdated(effectiveDate.atStartOfDay().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    /**
     * Analyze Top 4 race using current season (convenience method).
     */
    public Top4RaceAnalysisDTO analyzeTop4Race(String season) {
        return analyzeTop4Race(season, LocalDate.now());
    }

    /**
     * Build a Team Race DTO with all calculations.
     */
    private Top4RaceDTO buildTeamRaceDTO(
            LeagueStanding standing,
            int position,
            int firstPlacePoints,
            int fourthPlacePoints,
            int fifthPlacePoints,
            int remainingMatches,
            int pointsForSafety
    ) {
        int points = standing.getPoints() != null ? standing.getPoints() : 0;
        int played = standing.getPlayed() != null ? standing.getPlayed() : 0;
        int won = standing.getWon() != null ? standing.getWon() : 0;

        // Calculate gaps
        int gapToFirst = firstPlacePoints - points;
        int gapToFourth = position <= 4 ? points - fourthPlacePoints : fourthPlacePoints - points;
        int gapToFifth = position <= 4 ? points - fifthPlacePoints : fifthPlacePoints - points;

        // Calculate points needed
        int pointsNeeded = Math.max(0, pointsForSafety - points);

        // Calculate win rate
        double winRate = played > 0 ? (double) won / played * 100 : 0;

        // Calculate points per game
        double ppg = played > 0 ? (double) points / played : 0;

        // Calculate probability
        double probability = calculateTop4Probability(
                position, points, remainingMatches, pointsForSafety, winRate, gapToFourth
        );

        // Determine status
        String status = determineStatus(position, probability, points, gapToFirst, remainingMatches);

        // Determine motivation
        String motivation = determineMotivation(position, probability, gapToFourth, gapToFirst, remainingMatches);

        // Get team logo
        String teamLogo = teamService.getTeamLogoUrl(standing.getTeamName());

        return Top4RaceDTO.builder()
                .teamName(standing.getTeamName())
                .teamLogo(teamLogo)
                .currentPosition(position)
                .points(points)
                .gapToFirst(gapToFirst)
                .gapToFourth(position <= 4 ? -gapToFourth : gapToFourth) // Negative for teams above 4th
                .gapToFifth(position <= 4 ? gapToFifth : -gapToFifth) // Positive safety margin
                .remainingMatches(remainingMatches)
                .pointsNeeded(pointsNeeded)
                .top4Probability(Math.round(probability * 10) / 10.0)
                .status(status)
                .motivation(motivation)
                .winRate(Math.round(winRate * 10) / 10.0)
                .goalDifference(standing.getGoalDifference() != null ? standing.getGoalDifference() : 0)
                .form(standing.getForm() != null ? standing.getForm() : "")
                .pointsPerGame(Math.round(ppg * 100) / 100.0)
                .build();
    }

    /**
     * Calculate probability of finishing in top 4.
     * Uses a combination of:
     * - Current position (weight: 30%)
     * - Points gap to 4th (weight: 35%)
     * - Remaining points potential vs needed (weight: 25%)
     * - Historical win rate projection (weight: 10%)
     */
    private double calculateTop4Probability(
            int position, int points, int remainingMatches,
            int safetyThreshold, double winRate, int gapToFourth
    ) {
        // Base probability from position
        double positionProbability;
        if (position <= 4) {
            positionProbability = 90 - (position - 1) * 10; // 1st: 90%, 2nd: 80%, 3rd: 70%, 4th: 60%
        } else if (position == 5) {
            positionProbability = 40;
        } else if (position == 6) {
            positionProbability = 25;
        } else if (position == 7) {
            positionProbability = 15;
        } else {
            positionProbability = Math.max(2, 20 - (position - 5) * 5);
        }

        // Maximum points still achievable
        int maxPossiblePoints = points + (remainingMatches * 3);
        int pointsNeeded = Math.max(0, safetyThreshold - points);

        // Points gap factor - adjust probability based on gap to 4th
        double gapFactor;
        if (position <= 4) {
            // In top 4 - gap to 5th is positive (safety margin)
            gapFactor = Math.min(1.2, 1 + (Math.abs(gapToFourth) * 0.02));
        } else {
            // Below top 4 - gap to 4th is a deficit
            if (gapToFourth <= 0) {
                gapFactor = 1.0; // Tied or ahead
            } else if (gapToFourth <= 3) {
                gapFactor = 0.95;
            } else if (gapToFourth <= 6) {
                gapFactor = 0.80;
            } else if (gapToFourth <= 9) {
                gapFactor = 0.60;
            } else if (gapToFourth <= 12) {
                gapFactor = 0.35;
            } else {
                gapFactor = Math.max(0.05, 0.20 - (gapToFourth - 12) * 0.02);
            }
        }

        // Points achievability factor
        double achievabilityFactor;
        if (maxPossiblePoints < safetyThreshold) {
            // Mathematically difficult
            achievabilityFactor = 0.5 * (double) maxPossiblePoints / safetyThreshold;
        } else if (pointsNeeded == 0) {
            // Already at safety threshold
            achievabilityFactor = 1.1;
        } else {
            // Calculate based on how many wins needed vs remaining matches
            double winsNeeded = (double) pointsNeeded / 3;
            double winsAvailable = (double) remainingMatches;
            achievabilityFactor = winsAvailable > 0 ? Math.min(1.0, winsAvailable / winsNeeded) : 0.5;
        }

        // Win rate projection factor
        double projectionFactor = 0.7 + (winRate / 100) * 0.6; // Range: 0.7 to 1.3

        // Combine factors
        double probability = positionProbability * gapFactor * achievabilityFactor;
        probability = probability * 0.85 + (probability * projectionFactor * 0.15);

        // Apply bounds and special cases
        if (points >= HIGH_POINTS_THRESHOLD) {
            probability = Math.max(probability, 95);
        }

        // Clamp to 0-100 range
        probability = Math.max(0, Math.min(100, probability));

        return probability;
    }

    /**
     * Determine team's current status in the race.
     */
    private String determineStatus(int position, double probability, int points, int gapToFirst, int remainingMatches) {
        int maxCatchUp = remainingMatches * 3;

        if (position == 1 && (gapToFirst < 0 || probability > 90)) {
            return "Champion";
        } else if (position == 1 && maxCatchUp < Math.abs(gapToFirst)) {
            return "Champion"; // Mathematically champions
        } else if (probability >= 85 && position <= 4) {
            return "UCL Safe";
        } else if (probability >= 50 || position <= 6) {
            return "Fighting";
        } else {
            return "Unlikely";
        }
    }

    /**
     * Determine team's motivation level for upcoming matches.
     */
    private String determineMotivation(int position, double probability, int gapToFourth, int gapToFirst, int remainingMatches) {
        // Title contenders: high motivation
        if (position <= 2 && gapToFirst <= 6) {
            return "High";
        }

        // Teams fighting for top 4 with close gaps
        if (position <= 6 && Math.abs(gapToFourth) <= 6) {
            return "High";
        }

        // Teams with realistic chance and points to gain
        if (probability >= 40 && probability < 80) {
            return "High";
        }

        // Teams with comfortable lead (might rotate)
        if (probability >= 90 && position <= 3) {
            return "Medium";
        }

        // Teams with little to play for
        if (probability < 20 && position > 6) {
            return "Low";
        }

        return "Medium";
    }

    /**
     * Estimate the safety threshold for top 4 finish.
     * Based on current 4th place points and projected finish.
     */
    private int estimateSafetyThreshold(int currentFourthPlacePoints, int remainingMatches) {
        // Historical average is around 70-75 points for 4th place
        int historicalAverage = TOP_4_SAFETY_THRESHOLD;

        if (remainingMatches <= 0) {
            return currentFourthPlacePoints;
        }

        // Project 4th place finish based on current pace
        double ppgFourth = currentFourthPlacePoints / (double) (TOTAL_SEASON_MATCHES - remainingMatches);
        int projectedFourth = (int) Math.round(currentFourthPlacePoints + (ppgFourth * remainingMatches));

        // Take average of projection and historical
        return (projectedFourth + historicalAverage) / 2;
    }

    /**
     * Build the title race summary.
     */
    private TitleRaceSummary buildTitleRaceSummary(List<LeagueStanding> topTeams, int remainingMatches) {
        if (topTeams.isEmpty()) {
            return TitleRaceSummary.builder()
                    .contenders(0)
                    .gapFirstToSecond(0)
                    .decided(false)
                    .leader("")
                    .intensity("Unknown")
                    .build();
        }

        int firstPoints = topTeams.get(0).getPoints() != null ? topTeams.get(0).getPoints() : 0;
        int secondPoints = topTeams.size() > 1 && topTeams.get(1).getPoints() != null
                ? topTeams.get(1).getPoints() : 0;
        int gap = firstPoints - secondPoints;
        int maxCatchUp = remainingMatches * 3;

        // Count contenders (within 9 points of leader)
        int contenders = (int) topTeams.stream()
                .filter(t -> t.getPoints() != null && (firstPoints - t.getPoints()) <= 9)
                .count();

        // Is title decided?
        boolean decided = gap > maxCatchUp;

        // Determine intensity
        String intensity;
        if (decided) {
            intensity = "Decided";
        } else if (gap >= 10) {
            intensity = "Comfortable Lead";
        } else if (gap >= 4) {
            intensity = "Close Race";
        } else {
            intensity = "Wide Open";
        }

        return TitleRaceSummary.builder()
                .contenders(contenders)
                .gapFirstToSecond(gap)
                .decided(decided)
                .leader(topTeams.get(0).getTeamName())
                .intensity(intensity)
                .build();
    }

    /**
     * Build empty response when no data available.
     */
    private Top4RaceAnalysisDTO buildEmptyResponse(String season, LocalDate asOfDate) {
        return Top4RaceAnalysisDTO.builder()
                .season(season)
                .asOfDate(asOfDate)
                .teamsInRace(new ArrayList<>())
                .pointsForSafety(TOP_4_SAFETY_THRESHOLD)
                .totalMatchesInSeason(TOTAL_SEASON_MATCHES)
                .matchdaysCompleted(0)
                .seasonProgressPercent(0)
                .titleRace(TitleRaceSummary.builder()
                        .contenders(0)
                        .gapFirstToSecond(0)
                        .decided(false)
                        .leader("")
                        .intensity("Not Started")
                        .build())
                .lastUpdated(asOfDate.atStartOfDay().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }
}



