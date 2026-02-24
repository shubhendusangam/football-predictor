package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.common.util.PredictionUtils;
import com.app.common.util.TeamNameNormalizer;
import com.app.footballprediction.dto.H2HInsightsResponse;
import com.app.footballprediction.dto.H2HInsightsResponse.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating enhanced Head-to-Head (H2H) insights between two teams.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class H2HInsightsService {

    private final MatchRepository matchRepository;
    private final InsightsValidationService validationService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int RECENT_MATCHES_COUNT = 5;
    private static final String DATA_SCOPE_ALL_H2H = "All Historical Meetings";
    private static final String DATA_SCOPE_H2H_ONLY = "H2H Only (All Meetings)";

    /**
     * Get comprehensive H2H insights for two teams.
     * Automatically normalizes team names from API format to database format.
     *
     * @param homeTeam The home team for the upcoming/hypothetical match
     * @param awayTeam The away team for the upcoming/hypothetical match
     * @return H2HInsightsResponse with all H2H statistics
     */
    @Cacheable(value = "h2hInsights", key = "#homeTeam + '_vs_' + #awayTeam")
    public H2HInsightsResponse getH2HInsights(String homeTeam, String awayTeam) {
        log.info("Calculating H2H insights: {} vs {}", homeTeam, awayTeam);

        // Normalize team names to match database format
        String normalizedHome = TeamNameNormalizer.normalize(homeTeam);
        String normalizedAway = TeamNameNormalizer.normalize(awayTeam);

        if (!normalizedHome.equals(homeTeam) || !normalizedAway.equals(awayTeam)) {
            log.info("Normalized team names: '{}' -> '{}', '{}' -> '{}'",
                     homeTeam, normalizedHome, awayTeam, normalizedAway);
        }

        LocalDate beforeDate = LocalDate.now().plusDays(1);

        // Fetch all H2H matches between these two teams
        List<Match> h2hMatches = matchRepository.findH2HBeforeDate(normalizedHome, normalizedAway, beforeDate);

        // If no matches found, try case-insensitive search
        if (h2hMatches.isEmpty()) {
            h2hMatches = tryFuzzyH2HSearch(normalizedHome, normalizedAway, beforeDate);
        }

        if (h2hMatches.isEmpty()) {
            log.info("No H2H history found between {} and {} (searched as {} vs {})",
                     homeTeam, awayTeam, normalizedHome, normalizedAway);
            return buildEmptyResponse(normalizedHome, normalizedAway);
        }

        log.debug("Found {} H2H matches between {} and {}", h2hMatches.size(), normalizedHome, normalizedAway);

        H2HInsightsResponse response = H2HInsightsResponse.builder()
                .homeTeam(normalizedHome)
                .awayTeam(normalizedAway)
                .historicalRecord(calculateHistoricalRecord(h2hMatches, normalizedHome, normalizedAway))
                .recentMeetings(getRecentMeetings(h2hMatches, normalizedHome, normalizedAway))
                .goalStats(calculateGoalStats(h2hMatches, normalizedHome, normalizedAway))
                .commonResults(calculateCommonResults(h2hMatches, normalizedHome, normalizedAway))
                .venueAdvantage(calculateVenueAdvantage(h2hMatches, normalizedHome, normalizedAway))
                .build();

        // Validate H2H insights for consistency
        List<String> validationErrors = validationService.validateH2HInsights(response);
        if (!validationErrors.isEmpty()) {
            log.error("H2H validation errors for {} vs {}: {}", normalizedHome, normalizedAway, validationErrors);
        }

        return response;
    }

    /**
     * Try fuzzy H2H search when exact match fails.
     * Searches with case-insensitive matching.
     */
    private List<Match> tryFuzzyH2HSearch(String homeTeam, String awayTeam, LocalDate beforeDate) {
        // Get all matches for home team
        List<Match> homeTeamMatches = matchRepository.findByTeamBeforeDateIgnoreCase(homeTeam, beforeDate);

        if (homeTeamMatches.isEmpty()) {
            return Collections.emptyList();
        }

        // Filter for matches against away team (case-insensitive)
        String awayLower = awayTeam.toLowerCase();
        return homeTeamMatches.stream()
                .filter(m -> {
                    String opponent = m.getHomeTeam().equalsIgnoreCase(homeTeam)
                            ? m.getAwayTeam()
                            : m.getHomeTeam();
                    return opponent.toLowerCase().contains(awayLower) ||
                           awayLower.contains(opponent.toLowerCase());
                })
                .collect(Collectors.toList());
    }

    /**
     * Build an empty response when no H2H history exists.
     */
    private H2HInsightsResponse buildEmptyResponse(String homeTeam, String awayTeam) {
        return H2HInsightsResponse.builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .historicalRecord(HistoricalRecord.builder()
                        .totalMatches(0)
                        .homeTeamWins(0)
                        .draws(0)
                        .awayTeamWins(0)
                        .summary("No previous meetings between " + homeTeam + " and " + awayTeam)
                        .dominantTeam("EVEN")
                        .homeTeamWinPercentage(0)
                        .awayTeamWinPercentage(0)
                        .drawPercentage(0)
                        .dataScope(DATA_SCOPE_ALL_H2H)
                        .isConsistent(true)
                        .build())
                .recentMeetings(Collections.emptyList())
                .goalStats(H2HGoalStats.builder()
                        .avgTotalGoals(0)
                        .avgHomeTeamGoals(0)
                        .avgAwayTeamGoals(0)
                        .totalGoalsAllTime(0)
                        .highestScoringMatch(0)
                        .highestScoringMatchDetails("N/A")
                        .cleanSheetsHomeTeam(0)
                        .cleanSheetsAwayTeam(0)
                        .dataScope(DATA_SCOPE_H2H_ONLY)
                        .build())
                .commonResults(CommonResultStats.builder()
                        .mostCommonResult("N/A")
                        .mostCommonResultCount(0)
                        .mostCommonOutcome("N/A")
                        .homeWinCount(0)
                        .drawCount(0)
                        .awayWinCount(0)
                        .topScorelines(Collections.emptyList())
                        .dataScope(DATA_SCOPE_H2H_ONLY)
                        .build())
                .venueAdvantage(VenueAdvantageStats.builder()
                        .homeTeamHomeMatches(0)
                        .homeTeamHomeWins(0)
                        .homeTeamHomeDraws(0)
                        .homeTeamHomeLosses(0)
                        .homeTeamHomeWinPercentage(0)
                        .awayTeamHomeMatches(0)
                        .awayTeamHomeWins(0)
                        .awayTeamHomeDraws(0)
                        .awayTeamHomeLosses(0)
                        .awayTeamHomeWinPercentage(0)
                        .homeAdvantageDescription("No H2H history available")
                        .build())
                .build();
    }

    /**
     * Calculate historical record: "Arsenal leads 15-8-7 vs Chelsea" format.
     * Scope: All historical H2H meetings.
     */
    private HistoricalRecord calculateHistoricalRecord(List<Match> h2hMatches, String homeTeam, String awayTeam) {
        int homeTeamWins = 0;
        int draws = 0;
        int awayTeamWins = 0;

        for (Match match : h2hMatches) {
            String winner = getWinnerTeam(match);
            if (winner == null) {
                draws++;
            } else if (winner.equalsIgnoreCase(homeTeam)) {
                homeTeamWins++;
            } else if (winner.equalsIgnoreCase(awayTeam)) {
                awayTeamWins++;
            }
        }

        int totalMatches = h2hMatches.size();
        double homeTeamWinPct = totalMatches > 0 ? PredictionUtils.round((double) homeTeamWins / totalMatches * 100) : 0;
        double awayTeamWinPct = totalMatches > 0 ? PredictionUtils.round((double) awayTeamWins / totalMatches * 100) : 0;
        double drawPct = totalMatches > 0 ? PredictionUtils.round((double) draws / totalMatches * 100) : 0;

        // Determine dominant team and summary
        String dominantTeam;
        String summary;
        if (homeTeamWins > awayTeamWins) {
            dominantTeam = "HOME";
            summary = String.format("%s leads %d-%d-%d vs %s", homeTeam, homeTeamWins, draws, awayTeamWins, awayTeam);
        } else if (awayTeamWins > homeTeamWins) {
            dominantTeam = "AWAY";
            summary = String.format("%s leads %d-%d-%d vs %s", awayTeam, awayTeamWins, draws, homeTeamWins, homeTeam);
        } else {
            dominantTeam = "EVEN";
            summary = String.format("Series tied %d-%d-%d between %s and %s", homeTeamWins, draws, awayTeamWins, homeTeam, awayTeam);
        }

        // Validate consistency: wins + draws + losses = totalMatches
        boolean isConsistent = (homeTeamWins + draws + awayTeamWins) == totalMatches;

        return HistoricalRecord.builder()
                .totalMatches(totalMatches)
                .homeTeamWins(homeTeamWins)
                .draws(draws)
                .awayTeamWins(awayTeamWins)
                .summary(summary)
                .dominantTeam(dominantTeam)
                .homeTeamWinPercentage(homeTeamWinPct)
                .awayTeamWinPercentage(awayTeamWinPct)
                .drawPercentage(drawPct)
                .dataScope(DATA_SCOPE_ALL_H2H)
                .isConsistent(isConsistent)
                .build();
    }

    /**
     * Get the last 5 meetings with results and scorelines.
     */
    private List<H2HMatch> getRecentMeetings(List<Match> h2hMatches, String homeTeam, String awayTeam) {
        return h2hMatches.stream()
                .limit(RECENT_MATCHES_COUNT)
                .map(match -> {
                    String winner = getWinnerTeam(match);
                    String winnerDisplay = winner != null ? winner : "Draw";

                    return H2HMatch.builder()
                            .date(match.getMatchDate().format(DATE_FORMATTER))
                            .homeTeamInMatch(match.getHomeTeam())
                            .awayTeamInMatch(match.getAwayTeam())
                            .homeGoals(match.getFullTimeHomeGoals())
                            .awayGoals(match.getFullTimeAwayGoals())
                            .score(match.getFullTimeHomeGoals() + "-" + match.getFullTimeAwayGoals())
                            .result(match.getFullTimeResult())
                            .winner(winnerDisplay)
                            .season(extractSeason(match.getMatchDate()))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Calculate H2H goal statistics.
     */
    private H2HGoalStats calculateGoalStats(List<Match> h2hMatches, String homeTeam, String awayTeam) {
        int totalGoals = 0;
        int homeTeamGoals = 0;
        int awayTeamGoals = 0;
        int cleanSheetsHome = 0;
        int cleanSheetsAway = 0;
        int bttsCount = 0;
        int highestTotal = 0;
        Match highestMatch = null;

        for (Match match : h2hMatches) {
            int homeGoals = match.getFullTimeHomeGoals();
            int awayGoals = match.getFullTimeAwayGoals();
            int matchTotal = homeGoals + awayGoals;

            totalGoals += matchTotal;

            // Track goals for each team
            if (match.getHomeTeam().equalsIgnoreCase(homeTeam)) {
                homeTeamGoals += homeGoals;
                awayTeamGoals += awayGoals;
                if (awayGoals == 0) cleanSheetsHome++;
                if (homeGoals == 0) cleanSheetsAway++;
            } else {
                homeTeamGoals += awayGoals;
                awayTeamGoals += homeGoals;
                if (homeGoals == 0) cleanSheetsHome++;
                if (awayGoals == 0) cleanSheetsAway++;
            }

            // Both teams to score
            if (homeGoals > 0 && awayGoals > 0) {
                bttsCount++;
            }

            // Track highest scoring match
            if (matchTotal > highestTotal) {
                highestTotal = matchTotal;
                highestMatch = match;
            }
        }

        int totalMatches = h2hMatches.size();
        String highestDetails = "N/A";
        if (highestMatch != null) {
            highestDetails = String.format("%d-%d (%s vs %s, %s)",
                    highestMatch.getFullTimeHomeGoals(),
                    highestMatch.getFullTimeAwayGoals(),
                    highestMatch.getHomeTeam(),
                    highestMatch.getAwayTeam(),
                    highestMatch.getMatchDate().format(DATE_FORMATTER));
        }

        return H2HGoalStats.builder()
                .avgTotalGoals(totalMatches > 0 ? PredictionUtils.round((double) totalGoals / totalMatches) : 0)
                .avgHomeTeamGoals(totalMatches > 0 ? PredictionUtils.round((double) homeTeamGoals / totalMatches) : 0)
                .avgAwayTeamGoals(totalMatches > 0 ? PredictionUtils.round((double) awayTeamGoals / totalMatches) : 0)
                .totalGoalsAllTime(totalGoals)
                .highestScoringMatch(highestTotal)
                .highestScoringMatchDetails(highestDetails)
                .cleanSheetsHomeTeam(cleanSheetsHome)
                .cleanSheetsAwayTeam(cleanSheetsAway)
                .dataScope(DATA_SCOPE_H2H_ONLY)
                .build();
    }

    /**
     * Calculate most common results and outcomes.
     */
    private CommonResultStats calculateCommonResults(List<Match> h2hMatches, String homeTeam, String awayTeam) {
        int homeWinCount = 0;
        int drawCount = 0;
        int awayWinCount = 0;

        // Track scoreline frequencies (normalized so homeTeam goals always first)
        Map<String, Integer> scoreFrequencies = new HashMap<>();

        for (Match match : h2hMatches) {
            String winner = getWinnerTeam(match);
            if (winner == null) {
                drawCount++;
            } else if (winner.equalsIgnoreCase(homeTeam)) {
                homeWinCount++;
            } else {
                awayWinCount++;
            }

            // Normalize scoreline (always show homeTeam's perspective)
            String normalizedScore;
            if (match.getHomeTeam().equalsIgnoreCase(homeTeam)) {
                normalizedScore = match.getFullTimeHomeGoals() + "-" + match.getFullTimeAwayGoals();
            } else {
                normalizedScore = match.getFullTimeAwayGoals() + "-" + match.getFullTimeHomeGoals();
            }
            scoreFrequencies.merge(normalizedScore, 1, Integer::sum);
        }

        // Find most common scoreline
        String mostCommonResult = "N/A";
        int mostCommonCount = 0;
        for (Map.Entry<String, Integer> entry : scoreFrequencies.entrySet()) {
            if (entry.getValue() > mostCommonCount) {
                mostCommonCount = entry.getValue();
                mostCommonResult = entry.getKey();
            }
        }

        // Determine most common outcome
        String mostCommonOutcome;
        if (homeWinCount >= drawCount && homeWinCount >= awayWinCount) {
            mostCommonOutcome = "HOME_WIN";
        } else if (awayWinCount >= homeWinCount && awayWinCount >= drawCount) {
            mostCommonOutcome = "AWAY_WIN";
        } else {
            mostCommonOutcome = "DRAW";
        }

        // Top 5 scorelines
        int totalMatches = h2hMatches.size();
        List<ScoreFrequency> topScorelines = scoreFrequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> ScoreFrequency.builder()
                        .scoreline(entry.getKey())
                        .count(entry.getValue())
                        .percentage(totalMatches > 0 ? PredictionUtils.round((double) entry.getValue() / totalMatches * 100) : 0)
                        .build())
                .collect(Collectors.toList());

        return CommonResultStats.builder()
                .mostCommonResult(mostCommonResult)
                .mostCommonResultCount(mostCommonCount)
                .mostCommonOutcome(mostCommonOutcome)
                .homeWinCount(homeWinCount)
                .drawCount(drawCount)
                .awayWinCount(awayWinCount)
                .topScorelines(topScorelines)
                .dataScope(DATA_SCOPE_H2H_ONLY)
                .build();
    }

    /**
     * Calculate venue-based advantage statistics.
     */
    private VenueAdvantageStats calculateVenueAdvantage(List<Match> h2hMatches, String homeTeam, String awayTeam) {
        // Matches where homeTeam was at home
        int homeTeamHomeMatches = 0;
        int homeTeamHomeWins = 0;
        int homeTeamHomeDraws = 0;
        int homeTeamHomeLosses = 0;

        // Matches where awayTeam was at home
        int awayTeamHomeMatches = 0;
        int awayTeamHomeWins = 0;
        int awayTeamHomeDraws = 0;
        int awayTeamHomeLosses = 0;

        for (Match match : h2hMatches) {
            if (match.getHomeTeam().equalsIgnoreCase(homeTeam)) {
                // homeTeam was playing at home
                homeTeamHomeMatches++;
                switch (match.getFullTimeResult()) {
                    case "H" -> homeTeamHomeWins++;
                    case "D" -> homeTeamHomeDraws++;
                    case "A" -> homeTeamHomeLosses++;
                }
            } else {
                // awayTeam was playing at home (homeTeam was away)
                awayTeamHomeMatches++;
                switch (match.getFullTimeResult()) {
                    case "H" -> awayTeamHomeWins++;
                    case "D" -> awayTeamHomeDraws++;
                    case "A" -> awayTeamHomeLosses++;
                }
            }
        }

        double homeTeamHomeWinPct = homeTeamHomeMatches > 0
                ? PredictionUtils.round((double) homeTeamHomeWins / homeTeamHomeMatches * 100) : 0;
        double awayTeamHomeWinPct = awayTeamHomeMatches > 0
                ? PredictionUtils.round((double) awayTeamHomeWins / awayTeamHomeMatches * 100) : 0;

        // Build description
        String description;
        if (homeTeamHomeWinPct > 60) {
            description = String.format("%s have strong home advantage (%.0f%% win rate at home vs %s)",
                    homeTeam, homeTeamHomeWinPct, awayTeam);
        } else if (awayTeamHomeWinPct > 60) {
            description = String.format("%s have strong home advantage (%.0f%% win rate at home vs %s)",
                    awayTeam, awayTeamHomeWinPct, homeTeam);
        } else if (Math.abs(homeTeamHomeWinPct - awayTeamHomeWinPct) < 10) {
            description = "Balanced rivalry - neither team has significant home advantage";
        } else if (homeTeamHomeWinPct > awayTeamHomeWinPct) {
            description = String.format("%s have slight home advantage (%.0f%% vs %.0f%%)",
                    homeTeam, homeTeamHomeWinPct, awayTeamHomeWinPct);
        } else {
            description = String.format("%s have slight home advantage (%.0f%% vs %.0f%%)",
                    awayTeam, awayTeamHomeWinPct, homeTeamHomeWinPct);
        }

        return VenueAdvantageStats.builder()
                .homeTeamHomeMatches(homeTeamHomeMatches)
                .homeTeamHomeWins(homeTeamHomeWins)
                .homeTeamHomeDraws(homeTeamHomeDraws)
                .homeTeamHomeLosses(homeTeamHomeLosses)
                .homeTeamHomeWinPercentage(homeTeamHomeWinPct)
                .awayTeamHomeMatches(awayTeamHomeMatches)
                .awayTeamHomeWins(awayTeamHomeWins)
                .awayTeamHomeDraws(awayTeamHomeDraws)
                .awayTeamHomeLosses(awayTeamHomeLosses)
                .awayTeamHomeWinPercentage(awayTeamHomeWinPct)
                .homeAdvantageDescription(description)
                .build();
    }

    /**
     * Get the winning team name from a match, or null for draws.
     */
    private String getWinnerTeam(Match match) {
        return switch (match.getFullTimeResult()) {
            case "H" -> match.getHomeTeam();
            case "A" -> match.getAwayTeam();
            default -> null;  // Draw
        };
    }

    /**
     * Extract season string from match date (e.g., "2024-25" for matches from Aug 2024 - May 2025).
     */
    private String extractSeason(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();

        // Season starts in August (month 8)
        if (month >= 8) {
            return year + "-" + String.format("%02d", (year + 1) % 100);
        } else {
            return (year - 1) + "-" + String.format("%02d", year % 100);
        }
    }
}

