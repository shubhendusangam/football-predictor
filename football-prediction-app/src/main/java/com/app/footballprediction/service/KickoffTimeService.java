package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.KickoffTimeAnalysisDTO;
import com.app.footballprediction.dto.KickoffTimeStatsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Service for analyzing team performance by kick-off time slot.
 *
 * <p>Groups matches into time-of-day slots and calculates win/draw/loss
 * breakdown, goal averages, and performance classification per slot.</p>
 *
 * <p><strong>Time Slots:</strong></p>
 * <ul>
 *   <li>Early: 12:00 – 13:30</li>
 *   <li>Afternoon: 14:00 – 16:00</li>
 *   <li>Late: 16:30 – 18:30</li>
 *   <li>Evening: 19:00 – 21:00</li>
 * </ul>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KickoffTimeService {

    private final MatchRepository matchRepository;

    // ══════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Maximum recent matches to analyze.
     */
    private static final int MAX_MATCHES = 100;

    /**
     * Minimum matches in a slot for confident classification.
     */
    private static final int MIN_MATCHES_FOR_CONFIDENCE = 3;

    /**
     * Threshold above overall win rate for "Strong" classification (percentage points).
     */
    private static final double STRONG_THRESHOLD = 5.0;

    /**
     * Threshold below overall win rate for "Weak" classification (percentage points).
     */
    private static final double WEAK_THRESHOLD = 5.0;

    /**
     * High confidence threshold (total matches with time data).
     */
    private static final int HIGH_CONFIDENCE_MATCHES = 30;

    /**
     * Medium confidence threshold (total matches with time data).
     */
    private static final int MEDIUM_CONFIDENCE_MATCHES = 15;

    /**
     * Time slot definitions with boundaries.
     */
    private static final List<TimeSlotDef> TIME_SLOTS = List.of(
            new TimeSlotDef("early", "Early (12:00–13:30)", "12:00 – 13:30",
                    LocalTime.of(12, 0), LocalTime.of(13, 30)),
            new TimeSlotDef("afternoon", "Afternoon (14:00–16:00)", "14:00 – 16:00",
                    LocalTime.of(14, 0), LocalTime.of(16, 0)),
            new TimeSlotDef("late", "Late (16:30–18:30)", "16:30 – 18:30",
                    LocalTime.of(16, 30), LocalTime.of(18, 30)),
            new TimeSlotDef("evening", "Evening (19:00–21:00)", "19:00 – 21:00",
                    LocalTime.of(19, 0), LocalTime.of(21, 0))
    );

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Analyze team performance grouped by kick-off time slots.
     *
     * @param teamName Team name to analyze
     * @return KickoffTimeAnalysisDTO with per-slot performance breakdown
     * @throws IllegalArgumentException if team name is invalid
     */
    @Cacheable(value = "kickoffTimeAnalysis", key = "#teamName")
    public KickoffTimeAnalysisDTO analyzeByKickoffTime(String teamName) {
        log.info("Analyzing kick-off time performance for team: {}", teamName);

        validateTeamName(teamName);

        LocalDate beforeDate = LocalDate.now().plusDays(1);
        String resolvedTeam = resolveTeamName(teamName.trim(), beforeDate);

        // Fetch recent matches
        List<Match> allMatches = matchRepository.findByTeamBeforeDate(resolvedTeam, beforeDate);

        if (allMatches.isEmpty()) {
            log.warn("No matches found for team: {}", resolvedTeam);
            return KickoffTimeAnalysisDTO.empty(resolvedTeam);
        }

        // Limit to recent matches
        List<Match> recentMatches = allMatches.stream()
                .limit(MAX_MATCHES)
                .toList();

        // Filter to matches with valid kick-off time
        List<Match> matchesWithTime = recentMatches.stream()
                .filter(m -> m.getKickoffTime() != null && !m.getKickoffTime().isBlank())
                .filter(m -> m.getFullTimeResult() != null)
                .toList();

        if (matchesWithTime.isEmpty()) {
            log.warn("No matches with kick-off time data found for team: {}", resolvedTeam);
            return KickoffTimeAnalysisDTO.empty(resolvedTeam);
        }

        log.debug("Analyzing {} matches with time data (out of {} total) for {}",
                matchesWithTime.size(), recentMatches.size(), resolvedTeam);

        return buildKickoffTimeAnalysis(resolvedTeam, recentMatches, matchesWithTime);
    }

    // ══════════════════════════════════════════════════════════════════════
    // ANALYSIS CALCULATION
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Build the kick-off time analysis DTO from match data.
     */
    private KickoffTimeAnalysisDTO buildKickoffTimeAnalysis(
            String teamName, List<Match> allMatches, List<Match> matchesWithTime) {

        // Calculate overall win rate for baseline comparison
        int totalWins = 0;
        int totalGoalsScored = 0;
        int totalCompleted = 0;

        for (Match match : allMatches) {
            if (match.getFullTimeResult() == null) continue;
            totalCompleted++;
            int points = match.getPointsForTeam(teamName);
            if (points == 3) totalWins++;
            totalGoalsScored += match.getGoalsScoredByTeam(teamName);
        }

        double overallWinRate = totalCompleted > 0
                ? roundToTwoDecimals((double) totalWins / totalCompleted * 100.0)
                : 0.0;
        double overallAvgGoals = totalCompleted > 0
                ? roundToTwoDecimals((double) totalGoalsScored / totalCompleted)
                : 0.0;

        // Group matches by time slot
        Map<String, List<Match>> slotMatches = new LinkedHashMap<>();
        for (TimeSlotDef slot : TIME_SLOTS) {
            slotMatches.put(slot.key, new ArrayList<>());
        }

        for (Match match : matchesWithTime) {
            LocalTime kickoff = parseKickoffTime(match.getKickoffTime());
            if (kickoff == null) continue;

            for (TimeSlotDef slot : TIME_SLOTS) {
                if (!kickoff.isBefore(slot.start) && !kickoff.isAfter(slot.end)) {
                    slotMatches.get(slot.key).add(match);
                    break;
                }
            }
        }

        // Build per-slot stats
        List<KickoffTimeStatsDTO> slotStats = new ArrayList<>();
        String bestTime = null;
        String worstTime = null;
        double bestWinPct = -1;
        double worstWinPct = 101;

        for (TimeSlotDef slotDef : TIME_SLOTS) {
            List<Match> matches = slotMatches.get(slotDef.key);
            KickoffTimeStatsDTO stats = calculateSlotStats(teamName, slotDef, matches, overallWinRate);
            slotStats.add(stats);

            // Track best/worst (only for slots with enough data)
            if (matches.size() >= MIN_MATCHES_FOR_CONFIDENCE) {
                if (stats.getWinPercentage() > bestWinPct) {
                    bestWinPct = stats.getWinPercentage();
                    bestTime = slotDef.label;
                }
                if (stats.getWinPercentage() < worstWinPct) {
                    worstWinPct = stats.getWinPercentage();
                    worstTime = slotDef.label;
                }
            }
        }

        // Determine confidence level
        String confidence;
        if (matchesWithTime.size() >= HIGH_CONFIDENCE_MATCHES) {
            confidence = "High";
        } else if (matchesWithTime.size() >= MEDIUM_CONFIDENCE_MATCHES) {
            confidence = "Medium";
        } else {
            confidence = "Low";
        }

        log.info("Kick-off analysis for {}: {} matches with time data, overallWin={}%, best={}, worst={}",
                teamName, matchesWithTime.size(), overallWinRate, bestTime, worstTime);

        return KickoffTimeAnalysisDTO.builder()
                .teamName(teamName)
                .dataScope("Last " + allMatches.stream().filter(m -> m.getFullTimeResult() != null).count() + " Matches")
                .matchesAnalyzed(totalCompleted)
                .matchesWithTimeData(matchesWithTime.size())
                .timeSlots(slotStats)
                .bestTime(bestTime != null ? bestTime : "N/A")
                .worstTime(worstTime != null ? worstTime : "N/A")
                .overallWinRate(overallWinRate)
                .overallAvgGoalsScored(overallAvgGoals)
                .confidence(confidence)
                .build();
    }

    /**
     * Calculate statistics for a single time slot.
     */
    private KickoffTimeStatsDTO calculateSlotStats(
            String teamName, TimeSlotDef slotDef, List<Match> matches, double overallWinRate) {

        int wins = 0, draws = 0, losses = 0;
        int goalsScored = 0, goalsConceded = 0;

        for (Match match : matches) {
            int points = match.getPointsForTeam(teamName);
            if (points == 3) wins++;
            else if (points == 1) draws++;
            else losses++;

            goalsScored += match.getGoalsScoredByTeam(teamName);
            goalsConceded += match.getGoalsConcededByTeam(teamName);
        }

        int played = matches.size();
        double winPct = played > 0 ? roundToTwoDecimals((double) wins / played * 100.0) : 0.0;
        double avgScored = played > 0 ? roundToTwoDecimals((double) goalsScored / played) : 0.0;
        double avgConceded = played > 0 ? roundToTwoDecimals((double) goalsConceded / played) : 0.0;

        // Classify performance relative to overall win rate
        String performance;
        if (played < MIN_MATCHES_FOR_CONFIDENCE) {
            performance = "Insufficient Data";
        } else if (winPct >= overallWinRate + STRONG_THRESHOLD) {
            performance = "Strong";
        } else if (winPct <= overallWinRate - WEAK_THRESHOLD) {
            performance = "Weak";
        } else {
            performance = "Average";
        }

        return KickoffTimeStatsDTO.builder()
                .timeSlot(slotDef.label)
                .slotKey(slotDef.key)
                .matchesPlayed(played)
                .wins(wins)
                .draws(draws)
                .losses(losses)
                .winPercentage(winPct)
                .avgGoalsScored(avgScored)
                .avgGoalsConceded(avgConceded)
                .performance(performance)
                .timeRange(slotDef.range)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Parse a kick-off time string (e.g., "15:00", "20:00") into LocalTime.
     * Handles common CSV formats.
     */
    private LocalTime parseKickoffTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) return null;

        String cleaned = timeStr.trim();

        try {
            // Handle "HH:mm" format
            return LocalTime.parse(cleaned);
        } catch (DateTimeParseException e) {
            // Try "H:mm" format (e.g., "3:00")
            try {
                if (cleaned.length() <= 4 && cleaned.contains(":")) {
                    return LocalTime.parse("0" + cleaned);
                }
            } catch (DateTimeParseException e2) {
                // ignore
            }
            log.trace("Cannot parse kick-off time: '{}'", timeStr);
            return null;
        }
    }

    /**
     * Resolve team name with case-insensitive matching.
     */
    private String resolveTeamName(String teamName, LocalDate beforeDate) {
        String trimmed = teamName.trim();

        // Try exact match
        List<Match> exactMatches = matchRepository.findByTeamBeforeDate(trimmed, beforeDate);
        if (!exactMatches.isEmpty()) {
            return trimmed;
        }

        // Try case-insensitive
        List<Match> caseInsensitive = matchRepository.findByTeamBeforeDateIgnoreCase(trimmed, beforeDate);
        if (!caseInsensitive.isEmpty()) {
            Match first = caseInsensitive.getFirst();
            String actual = first.getHomeTeam().equalsIgnoreCase(trimmed)
                    ? first.getHomeTeam()
                    : first.getAwayTeam();
            log.debug("Resolved '{}' to '{}'", trimmed, actual);
            return actual;
        }

        return trimmed;
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

    // ══════════════════════════════════════════════════════════════════════
    // INNER CLASS: Time Slot Definition
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Defines a time slot with key, label, display range, and time boundaries.
     */
    private record TimeSlotDef(String key, String label, String range, LocalTime start, LocalTime end) {
    }
}


