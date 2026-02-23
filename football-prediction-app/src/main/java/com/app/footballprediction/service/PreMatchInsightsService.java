package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.model.SeasonTeamStats;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.SeasonTeamStatsRepository;
import com.app.common.util.PredictionUtils;
import com.app.common.util.TeamNameNormalizer;
import com.app.footballprediction.dto.PreMatchInsightsResponse;
import com.app.footballprediction.dto.PreMatchInsightsResponse.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating pre-match insights and predictions.
 * Provides form comparison, streak indicators, rest analysis, and goal predictions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PreMatchInsightsService {

    private final MatchRepository matchRepository;
    private final SeasonTeamStatsRepository seasonTeamStatsRepository;
    private final TeamStatsService teamStatsService;
    private final H2HInsightsService h2hInsightsService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int FORM_WINDOW = 5;
    private static final int STREAK_THRESHOLD = 3;
    private static final int FATIGUE_DAYS_THRESHOLD = 4;

    /**
     * Get comprehensive pre-match insights for a fixture.
     * Automatically normalizes team names from API format to database format.
     */
    @Cacheable(value = "preMatchInsights", key = "#homeTeam + '_vs_' + #awayTeam")
    public PreMatchInsightsResponse getPreMatchInsights(String homeTeam, String awayTeam) {
        log.info("Generating pre-match insights for {} vs {}", homeTeam, awayTeam);

        // Normalize team names to match database format
        String normalizedHome = TeamNameNormalizer.normalize(homeTeam);
        String normalizedAway = TeamNameNormalizer.normalize(awayTeam);

        if (!normalizedHome.equals(homeTeam) || !normalizedAway.equals(awayTeam)) {
            log.info("Normalized team names: '{}' -> '{}', '{}' -> '{}'",
                     homeTeam, normalizedHome, awayTeam, normalizedAway);
        }

        LocalDate beforeDate = LocalDate.now().plusDays(1);

        // Get recent matches for both teams
        List<Match> homeTeamMatches = matchRepository.findByTeamBeforeDate(normalizedHome, beforeDate);
        List<Match> awayTeamMatches = matchRepository.findByTeamBeforeDate(normalizedAway, beforeDate);

        // Try case-insensitive search if no matches found
        if (homeTeamMatches.isEmpty()) {
            homeTeamMatches = matchRepository.findByTeamBeforeDateIgnoreCase(normalizedHome, beforeDate);
        }
        if (awayTeamMatches.isEmpty()) {
            awayTeamMatches = matchRepository.findByTeamBeforeDateIgnoreCase(normalizedAway, beforeDate);
        }

        if (homeTeamMatches.isEmpty() || awayTeamMatches.isEmpty()) {
            log.warn("Insufficient data for {} vs {}", normalizedHome, normalizedAway);
            return buildEmptyResponse(normalizedHome, normalizedAway);
        }

        // Calculate all insights
        FormComparison formComparison = calculateFormComparison(homeTeamMatches, awayTeamMatches, normalizedHome, normalizedAway);
        List<StreakIndicator> streakIndicators = calculateStreakIndicators(homeTeamMatches, awayTeamMatches, normalizedHome, normalizedAway);
        RestAnalysis restAnalysis = calculateRestAnalysis(homeTeamMatches, awayTeamMatches, normalizedHome, normalizedAway);
        GoalThreatMeter goalThreatMeter = calculateGoalThreatMeter(homeTeamMatches, awayTeamMatches, normalizedHome, normalizedAway);
        MarketPredictions marketPredictions = calculateMarketPredictions(homeTeamMatches, awayTeamMatches, normalizedHome, normalizedAway);

        return PreMatchInsightsResponse.builder()
                .homeTeam(normalizedHome)
                .awayTeam(normalizedAway)
                .formComparison(formComparison)
                .streakIndicators(streakIndicators)
                .restAnalysis(restAnalysis)
                .goalThreatMeter(goalThreatMeter)
                .marketPredictions(marketPredictions)
                .keyInsights(generateKeyInsights(formComparison, streakIndicators, restAnalysis, goalThreatMeter))
                .generatedAt(LocalDate.now().format(DATE_FORMATTER))
                .build();
    }


    /**
     * Calculate form comparison between two teams.
     */
    private FormComparison calculateFormComparison(List<Match> homeMatches, List<Match> awayMatches,
                                                    String homeTeam, String awayTeam) {
        // Home team form (last 5)
        List<Match> homeLast5 = homeMatches.stream().limit(FORM_WINDOW).toList();
        int homeFormPoints = calculateFormPoints(homeLast5, homeTeam);
        String homeFormString = buildFormString(homeLast5, homeTeam);
        double homeFormRating = calculateFormRating(homeLast5, homeTeam);

        // Away team form (last 5)
        List<Match> awayLast5 = awayMatches.stream().limit(FORM_WINDOW).toList();
        int awayFormPoints = calculateFormPoints(awayLast5, awayTeam);
        String awayFormString = buildFormString(awayLast5, awayTeam);
        double awayFormRating = calculateFormRating(awayLast5, awayTeam);

        // Determine form advantage
        String formAdvantage;
        if (homeFormPoints > awayFormPoints + 3) {
            formAdvantage = homeTeam + " (Strong)";
        } else if (awayFormPoints > homeFormPoints + 3) {
            formAdvantage = awayTeam + " (Strong)";
        } else if (homeFormPoints > awayFormPoints) {
            formAdvantage = homeTeam + " (Slight)";
        } else if (awayFormPoints > homeFormPoints) {
            formAdvantage = awayTeam + " (Slight)";
        } else {
            formAdvantage = "Even";
        }

        return FormComparison.builder()
                .homeFormPoints(homeFormPoints)
                .homeFormString(homeFormString)
                .homeFormRating(homeFormRating)
                .homeMaxPoints(FORM_WINDOW * 3)
                .awayFormPoints(awayFormPoints)
                .awayFormString(awayFormString)
                .awayFormRating(awayFormRating)
                .awayMaxPoints(FORM_WINDOW * 3)
                .formAdvantage(formAdvantage)
                .pointsDifference(homeFormPoints - awayFormPoints)
                .build();
    }

    /**
     * Calculate streak indicators for both teams.
     */
    private List<StreakIndicator> calculateStreakIndicators(List<Match> homeMatches, List<Match> awayMatches,
                                                             String homeTeam, String awayTeam) {
        List<StreakIndicator> indicators = new ArrayList<>();

        // Home team streaks
        addTeamStreaks(indicators, homeMatches, homeTeam, true);

        // Away team streaks
        addTeamStreaks(indicators, awayMatches, awayTeam, false);

        // Sort by significance
        indicators.sort(Comparator.comparingInt(StreakIndicator::getStreakLength).reversed());

        return indicators;
    }

    private void addTeamStreaks(List<StreakIndicator> indicators, List<Match> matches,
                                String teamName, boolean isHomeTeam) {
        if (matches.isEmpty()) return;

        // Win streak
        int winStreak = 0;
        for (Match m : matches) {
            if (m.getPointsForTeam(teamName) == 3) {
                winStreak++;
            } else {
                break;
            }
        }
        if (winStreak >= STREAK_THRESHOLD) {
            indicators.add(StreakIndicator.builder()
                    .team(teamName)
                    .streakType("WIN")
                    .streakLength(winStreak)
                    .emoji("🔥")
                    .description(teamName + " on " + winStreak + "-match winning streak")
                    .impact("POSITIVE")
                    .isHomeTeam(isHomeTeam)
                    .build());
        }

        // Unbeaten streak
        int unbeatenStreak = 0;
        for (Match m : matches) {
            if (m.getPointsForTeam(teamName) >= 1) {
                unbeatenStreak++;
            } else {
                break;
            }
        }
        if (unbeatenStreak >= STREAK_THRESHOLD + 2 && unbeatenStreak > winStreak) {
            indicators.add(StreakIndicator.builder()
                    .team(teamName)
                    .streakType("UNBEATEN")
                    .streakLength(unbeatenStreak)
                    .emoji("💪")
                    .description(teamName + " unbeaten in last " + unbeatenStreak + " matches")
                    .impact("POSITIVE")
                    .isHomeTeam(isHomeTeam)
                    .build());
        }

        // Losing streak
        int lossStreak = 0;
        for (Match m : matches) {
            if (m.getPointsForTeam(teamName) == 0) {
                lossStreak++;
            } else {
                break;
            }
        }
        if (lossStreak >= STREAK_THRESHOLD) {
            indicators.add(StreakIndicator.builder()
                    .team(teamName)
                    .streakType("LOSS")
                    .streakLength(lossStreak)
                    .emoji("❄️")
                    .description(teamName + " lost last " + lossStreak + " matches")
                    .impact("NEGATIVE")
                    .isHomeTeam(isHomeTeam)
                    .build());
        }

        // Winless streak
        int winlessStreak = 0;
        for (Match m : matches) {
            if (m.getPointsForTeam(teamName) < 3) {
                winlessStreak++;
            } else {
                break;
            }
        }
        if (winlessStreak >= STREAK_THRESHOLD + 2) {
            indicators.add(StreakIndicator.builder()
                    .team(teamName)
                    .streakType("WINLESS")
                    .streakLength(winlessStreak)
                    .emoji("⚠️")
                    .description(teamName + " without a win in " + winlessStreak + " matches")
                    .impact("NEGATIVE")
                    .isHomeTeam(isHomeTeam)
                    .build());
        }

        // Scoring streak
        int scoringStreak = 0;
        for (Match m : matches) {
            if (m.getGoalsScoredByTeam(teamName) > 0) {
                scoringStreak++;
            } else {
                break;
            }
        }
        if (scoringStreak >= STREAK_THRESHOLD + 3) {
            indicators.add(StreakIndicator.builder()
                    .team(teamName)
                    .streakType("SCORING")
                    .streakLength(scoringStreak)
                    .emoji("⚽")
                    .description(teamName + " scored in last " + scoringStreak + " matches")
                    .impact("POSITIVE")
                    .isHomeTeam(isHomeTeam)
                    .build());
        }

        // Clean sheet streak
        int cleanSheetStreak = 0;
        for (Match m : matches) {
            if (m.getGoalsConcededByTeam(teamName) == 0) {
                cleanSheetStreak++;
            } else {
                break;
            }
        }
        if (cleanSheetStreak >= 2) {
            indicators.add(StreakIndicator.builder()
                    .team(teamName)
                    .streakType("CLEAN_SHEET")
                    .streakLength(cleanSheetStreak)
                    .emoji("🧤")
                    .description(teamName + " kept " + cleanSheetStreak + " consecutive clean sheets")
                    .impact("POSITIVE")
                    .isHomeTeam(isHomeTeam)
                    .build());
        }
    }

    /**
     * Calculate rest analysis and fatigue warnings.
     */
    private RestAnalysis calculateRestAnalysis(List<Match> homeMatches, List<Match> awayMatches,
                                                String homeTeam, String awayTeam) {
        // Get last match dates
        LocalDate homeLastMatch = homeMatches.isEmpty() ? null :
                homeMatches.get(0).getMatchDate();
        LocalDate awayLastMatch = awayMatches.isEmpty() ? null :
                awayMatches.get(0).getMatchDate();

        LocalDate today = LocalDate.now();

        int homeRestDays = homeLastMatch != null ?
                (int) ChronoUnit.DAYS.between(homeLastMatch, today) : 7;
        int awayRestDays = awayLastMatch != null ?
                (int) ChronoUnit.DAYS.between(awayLastMatch, today) : 7;

        // Determine fatigue warnings
        List<String> fatigueWarnings = new ArrayList<>();

        if (homeRestDays < FATIGUE_DAYS_THRESHOLD) {
            fatigueWarnings.add("⚠️ " + homeTeam + " played " + homeRestDays + " days ago");
        }
        if (awayRestDays < FATIGUE_DAYS_THRESHOLD) {
            fatigueWarnings.add("⚠️ " + awayTeam + " played " + awayRestDays + " days ago");
        }

        // Rest advantage
        String restAdvantage;
        int restDifference = homeRestDays - awayRestDays;
        if (Math.abs(restDifference) < 2) {
            restAdvantage = "Even";
        } else if (restDifference > 0) {
            restAdvantage = homeTeam + " (" + restDifference + " extra days)";
        } else {
            restAdvantage = awayTeam + " (" + (-restDifference) + " extra days)";
        }

        return RestAnalysis.builder()
                .homeTeamLastMatch(homeLastMatch != null ? homeLastMatch.format(DATE_FORMATTER) : "N/A")
                .awayTeamLastMatch(awayLastMatch != null ? awayLastMatch.format(DATE_FORMATTER) : "N/A")
                .homeTeamRestDays(homeRestDays)
                .awayTeamRestDays(awayRestDays)
                .restDifference(restDifference)
                .restAdvantage(restAdvantage)
                .fatigueWarnings(fatigueWarnings)
                .homeFatigueRisk(homeRestDays < FATIGUE_DAYS_THRESHOLD)
                .awayFatigueRisk(awayRestDays < FATIGUE_DAYS_THRESHOLD)
                .build();
    }

    /**
     * Calculate goal threat meter based on scoring/conceding averages.
     * Uses SEASON stats for accurate averages (not just last 10 matches).
     */
    private GoalThreatMeter calculateGoalThreatMeter(List<Match> homeMatches, List<Match> awayMatches,
                                                      String homeTeam, String awayTeam) {
        // Get current season
        String currentSeason = getCurrentSeason();

        // Try to get season stats for accurate averages
        Optional<SeasonTeamStats> homeSeasonStats = seasonTeamStatsRepository
                .findBySeasonIdAndTeamNameIgnoreCase(currentSeason, homeTeam);
        Optional<SeasonTeamStats> awaySeasonStats = seasonTeamStatsRepository
                .findBySeasonIdAndTeamNameIgnoreCase(currentSeason, awayTeam);

        double homeAvgScored, homeAvgConceded, awayAvgScored, awayAvgConceded;

        // Use season stats if available for accurate full-season averages
        if (homeSeasonStats.isPresent() && homeSeasonStats.get().getMatchesPlayed() > 0) {
            SeasonTeamStats stats = homeSeasonStats.get();
            homeAvgScored = (double) stats.getGoalsScored() / stats.getMatchesPlayed();
            homeAvgConceded = (double) stats.getGoalsConceded() / stats.getMatchesPlayed();
            log.debug("Using season stats for {}: {} matches, scored={}, conceded={}",
                    homeTeam, stats.getMatchesPlayed(), stats.getGoalsScored(), stats.getGoalsConceded());
        } else {
            // Fallback to calculating from ALL available matches (not just last 10)
            homeAvgScored = homeMatches.stream()
                    .mapToInt(m -> m.getGoalsScoredByTeam(homeTeam))
                    .average().orElse(0);
            homeAvgConceded = homeMatches.stream()
                    .mapToInt(m -> m.getGoalsConcededByTeam(homeTeam))
                    .average().orElse(0);
            log.debug("Using match-based stats for {}: {} matches", homeTeam, homeMatches.size());
        }

        if (awaySeasonStats.isPresent() && awaySeasonStats.get().getMatchesPlayed() > 0) {
            SeasonTeamStats stats = awaySeasonStats.get();
            awayAvgScored = (double) stats.getGoalsScored() / stats.getMatchesPlayed();
            awayAvgConceded = (double) stats.getGoalsConceded() / stats.getMatchesPlayed();
            log.debug("Using season stats for {}: {} matches, scored={}, conceded={}",
                    awayTeam, stats.getMatchesPlayed(), stats.getGoalsScored(), stats.getGoalsConceded());
        } else {
            // Fallback to calculating from ALL available matches (not just last 10)
            awayAvgScored = awayMatches.stream()
                    .mapToInt(m -> m.getGoalsScoredByTeam(awayTeam))
                    .average().orElse(0);
            awayAvgConceded = awayMatches.stream()
                    .mapToInt(m -> m.getGoalsConcededByTeam(awayTeam))
                    .average().orElse(0);
            log.debug("Using match-based stats for {}: {} matches", awayTeam, awayMatches.size());
        }

        // Expected goals in this match
        double homeExpectedGoals = (homeAvgScored + awayAvgConceded) / 2;
        double awayExpectedGoals = (awayAvgScored + homeAvgConceded) / 2;
        double totalExpectedGoals = homeExpectedGoals + awayExpectedGoals;

        // Threat ratings (0-100) - based on expected goals relative to league average (~1.5 goals per team)
        double homeThreatRating = Math.min(100, (homeExpectedGoals / 2.0) * 100);
        double awayThreatRating = Math.min(100, (awayExpectedGoals / 2.0) * 100);

        return GoalThreatMeter.builder()
                .homeTeamAvgScored(PredictionUtils.round(homeAvgScored))
                .homeTeamAvgConceded(PredictionUtils.round(homeAvgConceded))
                .awayTeamAvgScored(PredictionUtils.round(awayAvgScored))
                .awayTeamAvgConceded(PredictionUtils.round(awayAvgConceded))
                .homeExpectedGoals(PredictionUtils.round(homeExpectedGoals))
                .awayExpectedGoals(PredictionUtils.round(awayExpectedGoals))
                .totalExpectedGoals(PredictionUtils.round(totalExpectedGoals))
                .homeThreatRating(PredictionUtils.round(homeThreatRating))
                .awayThreatRating(PredictionUtils.round(awayThreatRating))
                .build();
    }

    /**
     * Get current football season string.
     */
    private String getCurrentSeason() {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        if (month >= 8) {
            return year + "-" + String.format("%02d", (year + 1) % 100);
        } else {
            return (year - 1) + "-" + String.format("%02d", year % 100);
        }
    }

    /**
     * Calculate market predictions based on goal expectations.
     * Uses SEASON stats for accurate expected goals.
     */
    private MarketPredictions calculateMarketPredictions(List<Match> homeMatches, List<Match> awayMatches,
                                                          String homeTeam, String awayTeam) {
        // Get current season
        String currentSeason = getCurrentSeason();

        // Try to get season stats for accurate expected goals
        Optional<SeasonTeamStats> homeSeasonStats = seasonTeamStatsRepository
                .findBySeasonIdAndTeamNameIgnoreCase(currentSeason, homeTeam);
        Optional<SeasonTeamStats> awaySeasonStats = seasonTeamStatsRepository
                .findBySeasonIdAndTeamNameIgnoreCase(currentSeason, awayTeam);

        double homeAvgScored, awayAvgScored;

        if (homeSeasonStats.isPresent() && homeSeasonStats.get().getMatchesPlayed() > 0) {
            SeasonTeamStats stats = homeSeasonStats.get();
            homeAvgScored = (double) stats.getGoalsScored() / stats.getMatchesPlayed();
        } else {
            // Fallback to ALL matches (not limited)
            homeAvgScored = homeMatches.stream()
                    .mapToDouble(m -> safeGetGoalsScored(m, homeTeam))
                    .average()
                    .orElse(1.0);
        }

        if (awaySeasonStats.isPresent() && awaySeasonStats.get().getMatchesPlayed() > 0) {
            SeasonTeamStats stats = awaySeasonStats.get();
            awayAvgScored = (double) stats.getGoalsScored() / stats.getMatchesPlayed();
        } else {
            // Fallback to ALL matches (not limited)
            awayAvgScored = awayMatches.stream()
                    .mapToDouble(m -> safeGetGoalsScored(m, awayTeam))
                    .average()
                    .orElse(1.0);
        }

        double expectedHomeGoals = PredictionUtils.round(homeAvgScored);
        double expectedAwayGoals = PredictionUtils.round(awayAvgScored);
        double expectedTotalGoals = PredictionUtils.round(homeAvgScored + awayAvgScored);

        String recommendation = generateGoalRecommendation(expectedTotalGoals);

        return MarketPredictions.builder()
                .expectedHomeGoals(expectedHomeGoals)
                .expectedAwayGoals(expectedAwayGoals)
                .expectedTotalGoals(expectedTotalGoals)
                .recommendation(recommendation)
                .build();
    }

    /**
     * Generate key insights summary.
     */
    private List<String> generateKeyInsights(FormComparison form, List<StreakIndicator> streaks,
                                              RestAnalysis rest, GoalThreatMeter threat) {
        List<String> insights = new ArrayList<>();

        // Form insight
        if (form.getPointsDifference() >= 6) {
            insights.add("📊 " + form.getFormAdvantage().split(" ")[0] + " in significantly better form (" +
                    form.getHomeFormPoints() + " vs " + form.getAwayFormPoints() + " pts)");
        } else if (form.getPointsDifference() <= -6) {
            insights.add("📊 " + form.getFormAdvantage().split(" ")[0] + " in significantly better form (" +
                    form.getAwayFormPoints() + " vs " + form.getHomeFormPoints() + " pts)");
        }

        // Streak insights (top 2)
        streaks.stream().limit(2).forEach(s -> insights.add(s.getEmoji() + " " + s.getDescription()));

        // Rest insight
        if (!rest.getFatigueWarnings().isEmpty()) {
            insights.addAll(rest.getFatigueWarnings());
        }

        // Goal threat insight
        if (threat.getTotalExpectedGoals() >= 3.0) {
            insights.add("⚽ High-scoring game expected (" +
                    PredictionUtils.round(threat.getTotalExpectedGoals()) + " goals)");
        } else if (threat.getTotalExpectedGoals() < 2.0) {
            insights.add("🔒 Low-scoring encounter expected (" +
                    PredictionUtils.round(threat.getTotalExpectedGoals()) + " goals)");
        }

        return insights;
    }

    // Helper methods
    private int calculateFormPoints(List<Match> matches, String team) {
        return matches.stream()
                .mapToInt(m -> m.getPointsForTeam(team))
                .sum();
    }

    private String buildFormString(List<Match> matches, String team) {
        return matches.stream()
                .limit(5)
                .map(m -> {
                    int points = m.getPointsForTeam(team);
                    return points == 3 ? "W" : points == 1 ? "D" : "L";
                })
                .collect(Collectors.joining());
    }

    private double calculateFormRating(List<Match> matches, String team) {
        if (matches.isEmpty()) return 50.0;
        int points = calculateFormPoints(matches, team);
        int maxPoints = matches.size() * 3;
        return PredictionUtils.round((double) points / maxPoints * 100);
    }

    private String generateGoalRecommendation(double expectedTotalGoals) {
        if (expectedTotalGoals >= 3.0) {
            return "High-scoring match expected (" + expectedTotalGoals + " goals)";
        } else if (expectedTotalGoals >= 2.5) {
            return "Moderate goal expectation (" + expectedTotalGoals + " goals)";
        } else if (expectedTotalGoals >= 2.0) {
            return "Typical goal count expected (" + expectedTotalGoals + " goals)";
        } else {
            return "Low-scoring match expected (" + expectedTotalGoals + " goals)";
        }
    }

    /**
     * Safely get goals scored by a team, handling null values.
     * This method determines if the team was home or away and returns the appropriate goal count.
     */
    private int safeGetGoalsScored(Match match, String teamName) {
        if (teamName == null || match == null) return 0;
        String normalizedName = teamName.trim();

        if (match.getHomeTeam() != null && match.getHomeTeam().trim().equalsIgnoreCase(normalizedName)) {
            return match.getFullTimeHomeGoals() != null ? match.getFullTimeHomeGoals() : 0;
        } else if (match.getAwayTeam() != null && match.getAwayTeam().trim().equalsIgnoreCase(normalizedName)) {
            return match.getFullTimeAwayGoals() != null ? match.getFullTimeAwayGoals() : 0;
        }
        return 0;
    }

    /**
     * Safely get goals conceded by a team, handling null values.
     */
    private int safeGetGoalsConceded(Match match, String teamName) {
        if (teamName == null || match == null) return 0;
        String normalizedName = teamName.trim();

        if (match.getHomeTeam() != null && match.getHomeTeam().trim().equalsIgnoreCase(normalizedName)) {
            return match.getFullTimeAwayGoals() != null ? match.getFullTimeAwayGoals() : 0;
        } else if (match.getAwayTeam() != null && match.getAwayTeam().trim().equalsIgnoreCase(normalizedName)) {
            return match.getFullTimeHomeGoals() != null ? match.getFullTimeHomeGoals() : 0;
        }
        return 0;
    }

    private PreMatchInsightsResponse buildEmptyResponse(String homeTeam, String awayTeam) {
        return PreMatchInsightsResponse.builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .formComparison(FormComparison.builder().build())
                .streakIndicators(Collections.emptyList())
                .restAnalysis(RestAnalysis.builder()
                        .fatigueWarnings(Collections.emptyList())
                        .build())
                .goalThreatMeter(GoalThreatMeter.builder().build())
                .marketPredictions(MarketPredictions.builder().build())
                .keyInsights(List.of("Insufficient data available"))
                .generatedAt(LocalDate.now().format(DATE_FORMATTER))
                .build();
    }
}

