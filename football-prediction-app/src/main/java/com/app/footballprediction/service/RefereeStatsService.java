package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.RefereeStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for computing referee statistics from match history.
 * Provides insights into referee tendencies for cards, fouls, and result distributions.
 *
 * <p>Statistics are aggregated from all matches officiated by each referee
 * and can be used for feature engineering or standalone analysis.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefereeStatsService {

    private final MatchRepository matchRepository;

    // League-wide averages for normalization (Premier League historical data)
    private static final double LEAGUE_AVG_YELLOW_CARDS = 3.5;  // Per match
    private static final double LEAGUE_AVG_RED_CARDS = 0.08;    // Per match
    private static final double LEAGUE_AVG_GOALS = 2.7;         // Per match

    /**
     * Get statistics for a specific referee.
     *
     * @param refereeName Referee name exactly as stored in database
     * @return RefereeStats with aggregated statistics
     */
    @Cacheable(value = "refereeStats", key = "#refereeName")
    public RefereeStats getRefereeStats(String refereeName) {
        if (refereeName == null || refereeName.isBlank()) {
            return RefereeStats.empty(refereeName);
        }

        // Get all matches for this referee
        List<Match> matches = getMatchesByReferee(refereeName);

        if (matches.isEmpty()) {
            log.info("No matches found for referee: {}", refereeName);
            return RefereeStats.empty(refereeName);
        }

        return calculateStats(refereeName, matches);
    }

    /**
     * Get all referee names in the database.
     */
    @Cacheable(value = "allReferees")
    public List<String> getAllReferees() {
        List<Match> allMatches = matchRepository.findAllByOrderByMatchDateDesc();
        return allMatches.stream()
                .map(Match::getReferee)
                .filter(Objects::nonNull)
                .filter(r -> !r.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Get statistics for all referees.
     */
    @Cacheable(value = "allRefereeStats")
    public List<RefereeStats> getAllRefereeStats() {
        List<String> referees = getAllReferees();
        return referees.stream()
                .map(this::getRefereeStats)
                .filter(s -> s.getMatchesOfficiated() >= 5)  // Min 5 matches for reliability
                .sorted(Comparator.comparingInt(RefereeStats::getMatchesOfficiated).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get top N strictest referees (by cards per match).
     */
    public List<RefereeStats> getStrictestReferees(int limit) {
        return getAllRefereeStats().stream()
                .sorted(Comparator.comparingDouble(RefereeStats::getStrictnessIndex).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get top N most lenient referees.
     */
    public List<RefereeStats> getMostLenientReferees(int limit) {
        return getAllRefereeStats().stream()
                .sorted(Comparator.comparingDouble(RefereeStats::getStrictnessIndex))
                .limit(limit)
                .collect(Collectors.toList());
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Get all matches officiated by a referee.
     */
    private List<Match> getMatchesByReferee(String refereeName) {
        List<Match> allMatches = matchRepository.findAllByOrderByMatchDateDesc();
        return allMatches.stream()
                .filter(m -> refereeName.equalsIgnoreCase(m.getReferee()))
                .collect(Collectors.toList());
    }

    /**
     * Calculate aggregated statistics for a referee.
     */
    private RefereeStats calculateStats(String refereeName, List<Match> matches) {
        int totalMatches = matches.size();

        // Card statistics
        double totalYellowCards = 0;
        double totalRedCards = 0;
        int matchesWithCardData = 0;

        // Goals statistics
        double totalGoals = 0;
        double totalHomeGoals = 0;
        double totalAwayGoals = 0;
        int matchesWithGoalData = 0;

        // Result statistics
        int homeWins = 0;
        int draws = 0;
        int awayWins = 0;
        int matchesWithResultData = 0;

        for (Match m : matches) {
            // Cards
            if (m.getHomeYellowCards() != null && m.getAwayYellowCards() != null) {
                totalYellowCards += m.getHomeYellowCards() + m.getAwayYellowCards();
                matchesWithCardData++;
            }
            if (m.getHomeRedCards() != null && m.getAwayRedCards() != null) {
                totalRedCards += m.getHomeRedCards() + m.getAwayRedCards();
            }

            // Goals
            if (m.getFullTimeHomeGoals() != null && m.getFullTimeAwayGoals() != null) {
                totalHomeGoals += m.getFullTimeHomeGoals();
                totalAwayGoals += m.getFullTimeAwayGoals();
                totalGoals += m.getFullTimeHomeGoals() + m.getFullTimeAwayGoals();
                matchesWithGoalData++;
            }

            // Results
            if (m.getFullTimeResult() != null) {
                matchesWithResultData++;
                switch (m.getFullTimeResult()) {
                    case "H" -> homeWins++;
                    case "D" -> draws++;
                    case "A" -> awayWins++;
                }
            }
        }

        // Calculate averages
        double avgYellowCards = matchesWithCardData > 0 ? totalYellowCards / matchesWithCardData : 0.0;
        double avgRedCards = matchesWithCardData > 0 ? totalRedCards / matchesWithCardData : 0.0;
        double avgGoalsPerMatch = matchesWithGoalData > 0 ? totalGoals / matchesWithGoalData : 0.0;
        double avgHomeGoals = matchesWithGoalData > 0 ? totalHomeGoals / matchesWithGoalData : 0.0;
        double avgAwayGoals = matchesWithGoalData > 0 ? totalAwayGoals / matchesWithGoalData : 0.0;

        double homeWinRate = matchesWithResultData > 0 ? (double) homeWins / matchesWithResultData : 0.462;
        double drawRate = matchesWithResultData > 0 ? (double) draws / matchesWithResultData : 0.268;
        double awayWinRate = matchesWithResultData > 0 ? (double) awayWins / matchesWithResultData : 0.270;

        // Calculate strictness index (normalized to 0-1 range)
        double strictnessIndex = calculateStrictnessIndex(avgYellowCards, avgRedCards);

        // Data completeness
        double dataCompleteness = (double) matchesWithCardData / totalMatches;

        log.debug("Calculated stats for referee {}: {} matches, {} avg yellow cards, {} strictness",
                refereeName, totalMatches, avgYellowCards, strictnessIndex);

        return RefereeStats.builder()
                .refereeName(refereeName)
                .matchesOfficiated(totalMatches)
                .avgYellowCards(round(avgYellowCards))
                .avgRedCards(round(avgRedCards))
                .avgFouls(0.0)  // Foul data not typically available
                .homeWinRate(round(homeWinRate))
                .drawRate(round(drawRate))
                .awayWinRate(round(awayWinRate))
                .avgGoalsPerMatch(round(avgGoalsPerMatch))
                .avgHomeGoals(round(avgHomeGoals))
                .avgAwayGoals(round(avgAwayGoals))
                .strictnessIndex(round(strictnessIndex))
                .dataCompleteness(round(dataCompleteness))
                .build();
    }

    /**
     * Calculate strictness index based on cards relative to league average.
     * Returns a value between 0.0 (lenient) and 1.0 (strict).
     */
    private double calculateStrictnessIndex(double avgYellowCards, double avgRedCards) {
        // Weight yellow cards more than red cards since red cards are rare
        double cardScore = (avgYellowCards / LEAGUE_AVG_YELLOW_CARDS) * 0.9
                         + (avgRedCards / LEAGUE_AVG_RED_CARDS) * 0.1;

        // Normalize to 0-1 range (assuming max 2x league average)
        return Math.max(0.0, Math.min(1.0, cardScore / 2.0));
    }

    /**
     * Round to 3 decimal places.
     */
    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}


