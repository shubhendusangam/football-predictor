package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.model.MatchFeatures;
import com.app.common.repository.MatchRepository;
import com.app.common.service.FeatureEngineeringService;
import com.app.common.util.PredictionUtils;
import com.app.footballprediction.dto.TrendingInsightsResponse;
import com.app.footballprediction.dto.TrendingInsightsResponse.*;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating Live/Trending Insights.
 * Analyzes team performance trends and generates actionable predictions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrendingInsightsService {

    private final MatchRepository matchRepository;
    private final FeatureEngineeringService featureEngineeringService;
    private final ModelTrainingService modelTrainingService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int RECENT_MATCHES_WINDOW = 10;
    private static final int HOT_STREAK_THRESHOLD = 3;          // Current consecutive wins
    private static final int HOT_FORM_THRESHOLD = 4;            // Wins in last 5 matches (alternative)
    private static final int HOT_FORM_WINDOW = 5;               // Window for alternative hot form check
    private static final int COLD_STREAK_THRESHOLD = 5;
    private static final int TOP_N_RESULTS = 5;

    /**
     * Get all trending insights.
     */
    @Cacheable(value = "trendingInsights", key = "'all'")
    public TrendingInsightsResponse getTrendingInsights() {
        log.info("Calculating trending insights...");

        LocalDate beforeDate = LocalDate.now().plusDays(1);
        Set<String> allTeams = featureEngineeringService.getAllTeams();

        log.debug("Analyzing {} teams for trends", allTeams.size());

        // Calculate all insights
        List<HotTeam> hotTeams = calculateHotTeams(allTeams, beforeDate);
        List<ColdTeam> coldTeams = calculateColdTeams(allTeams, beforeDate);
        List<TopScorer> topScorers = calculateTopScorers(allTeams, beforeDate);
        List<DefensiveWall> defensiveWalls = calculateDefensiveWalls(allTeams, beforeDate);
        List<UpsetAlert> upsetAlerts = calculateUpsetAlerts(allTeams);
        List<GoalFestMatch> goalFestMatches = calculateGoalFestMatches(allTeams);

        log.info("Insights calculated: {} hot teams, {} cold teams, {} top scorers, {} defensive walls, {} upset alerts, {} goal fest matches",
                hotTeams.size(), coldTeams.size(), topScorers.size(),
                defensiveWalls.size(), upsetAlerts.size(), goalFestMatches.size());

        return TrendingInsightsResponse.builder()
                .hotTeams(hotTeams)
                .coldTeams(coldTeams)
                .topScorers(topScorers)
                .defensiveWalls(defensiveWalls)
                .upsetAlerts(upsetAlerts)
                .goalFestMatches(goalFestMatches)
                .generatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .totalTeamsAnalyzed(allTeams.size())
                .build();
    }

    /**
     * 🔥 Hot Teams: Teams on 3+ match winning streaks OR teams with 4+ wins in last 5 matches.
     * Prioritizes consecutive winning streaks, but also includes hot form teams.
     */
    private List<HotTeam> calculateHotTeams(Set<String> teams, LocalDate beforeDate) {
        List<HotTeam> hotTeams = new ArrayList<>();
        Set<String> teamsWithStreak = new HashSet<>();

        // First pass: Find teams with consecutive winning streaks
        for (String team : teams) {
            List<Match> matches = matchRepository.findByTeamBeforeDate(team, beforeDate);
            if (matches.isEmpty()) continue;

            int winStreak = calcWinStreak(matches, team);

            if (winStreak >= HOT_STREAK_THRESHOLD) {
                teamsWithStreak.add(team);
                List<Match> streakMatches = matches.stream().limit(winStreak).toList();

                int goalsScored = streakMatches.stream()
                        .mapToInt(m -> m.getGoalsScoredByTeam(team))
                        .sum();
                int goalsConceded = streakMatches.stream()
                        .mapToInt(m -> m.getGoalsConcededByTeam(team))
                        .sum();

                List<String> opponents = streakMatches.stream()
                        .limit(3)
                        .map(m -> m.getHomeTeam().equalsIgnoreCase(team) ? m.getAwayTeam() : m.getHomeTeam())
                        .toList();

                hotTeams.add(HotTeam.builder()
                        .teamName(team)
                        .winStreak(winStreak)
                        .goalsScored(goalsScored)
                        .goalsConceded(goalsConceded)
                        .recentForm(buildFormString(matches, team, HOT_FORM_WINDOW))
                        .lastOpponents(opponents)
                        .streakStartDate(streakMatches.getLast().getMatchDate().format(DATE_FORMATTER))
                        .build());
            }
        }

        // Second pass: If we don't have enough teams, include teams with excellent recent form
        // (4+ wins in last 5 matches, even if not consecutive)
        if (hotTeams.size() < TOP_N_RESULTS) {
            for (String team : teams) {
                if (teamsWithStreak.contains(team)) continue;

                List<Match> matches = matchRepository.findByTeamBeforeDate(team, beforeDate);
                if (matches.size() < HOT_FORM_WINDOW) continue;

                List<Match> recentMatches = matches.stream().limit(HOT_FORM_WINDOW).toList();
                int winsInWindow = (int) recentMatches.stream()
                        .filter(m -> m.getPointsForTeam(team) == 3)
                        .count();

                if (winsInWindow >= HOT_FORM_THRESHOLD) {
                    int goalsScored = recentMatches.stream()
                            .mapToInt(m -> m.getGoalsScoredByTeam(team))
                            .sum();
                    int goalsConceded = recentMatches.stream()
                            .mapToInt(m -> m.getGoalsConcededByTeam(team))
                            .sum();

                    List<String> opponents = recentMatches.stream()
                            .filter(m -> m.getPointsForTeam(team) == 3)
                            .limit(3)
                            .map(m -> m.getHomeTeam().equalsIgnoreCase(team) ? m.getAwayTeam() : m.getHomeTeam())
                            .toList();


                    hotTeams.add(HotTeam.builder()
                            .teamName(team)
                            .winStreak(winsInWindow)  // Use wins in window as "streak" for hot form teams
                            .goalsScored(goalsScored)
                            .goalsConceded(goalsConceded)
                            .recentForm(buildFormString(matches, team, HOT_FORM_WINDOW))
                            .lastOpponents(opponents)
                            .streakStartDate(recentMatches.getLast().getMatchDate().format(DATE_FORMATTER))
                            .build());
                }
            }
        }

        // Sort by wins/streak, then by goals scored
        hotTeams.sort((a, b) -> {
            int streakCompare = Integer.compare(b.getWinStreak(), a.getWinStreak());
            return streakCompare != 0 ? streakCompare : Integer.compare(b.getGoalsScored(), a.getGoalsScored());
        });

        return hotTeams.stream().limit(TOP_N_RESULTS).toList();
    }

    /**
     * ❄️ Cold Teams: Teams without a win in 5+ matches.
     */
    private List<ColdTeam> calculateColdTeams(Set<String> teams, LocalDate beforeDate) {
        List<ColdTeam> coldTeams = new ArrayList<>();

        for (String team : teams) {
            List<Match> matches = matchRepository.findByTeamBeforeDate(team, beforeDate);
            if (matches.isEmpty()) continue;

            int winlessStreak = calcWinlessStreak(matches, team);

            if (winlessStreak >= COLD_STREAK_THRESHOLD) {
                List<Match> streakMatches = matches.stream().limit(winlessStreak).toList();

                int draws = (int) streakMatches.stream()
                        .filter(m -> m.getPointsForTeam(team) == 1)
                        .count();
                int losses = (int) streakMatches.stream()
                        .filter(m -> m.getPointsForTeam(team) == 0)
                        .count();
                int goalsScored = streakMatches.stream()
                        .mapToInt(m -> m.getGoalsScoredByTeam(team))
                        .sum();
                int goalsConceded = streakMatches.stream()
                        .mapToInt(m -> m.getGoalsConcededByTeam(team))
                        .sum();

                // Find last win
                String lastWinDate = "N/A";
                String lastWinOpponent = "N/A";
                for (int i = winlessStreak; i < matches.size(); i++) {
                    if (matches.get(i).getPointsForTeam(team) == 3) {
                        lastWinDate = matches.get(i).getMatchDate().format(DATE_FORMATTER);
                        Match winMatch = matches.get(i);
                        lastWinOpponent = winMatch.getHomeTeam().equalsIgnoreCase(team)
                                ? winMatch.getAwayTeam() : winMatch.getHomeTeam();
                        break;
                    }
                }

                coldTeams.add(ColdTeam.builder()
                        .teamName(team)
                        .matchesWithoutWin(winlessStreak)
                        .draws(draws)
                        .losses(losses)
                        .goalsScored(goalsScored)
                        .goalsConceded(goalsConceded)
                        .recentForm(buildFormString(matches, team, winlessStreak))
                        .lastWinDate(lastWinDate)
                        .lastWinOpponent(lastWinOpponent)
                        .build());
            }
        }

        // Sort by longest winless streak
        coldTeams.sort((a, b) -> Integer.compare(b.getMatchesWithoutWin(), a.getMatchesWithoutWin()));

        return coldTeams.stream().limit(TOP_N_RESULTS).toList();
    }

    /**
     * ⚽ Top Scorers: Teams scoring most goals in recent matches.
     */
    private List<TopScorer> calculateTopScorers(Set<String> teams, LocalDate beforeDate) {
        List<TopScorer> topScorers = new ArrayList<>();

        for (String team : teams) {
            List<Match> matches = matchRepository.findByTeamBeforeDate(team, beforeDate);
            if (matches.isEmpty()) continue;

            List<Match> recentMatches = matches.stream().limit(RECENT_MATCHES_WINDOW).toList();

            int totalGoals = recentMatches.stream()
                    .mapToInt(m -> m.getGoalsScoredByTeam(team))
                    .sum();

            // Find highest scoring match
            int highestScore = 0;
            String highestOpponent = "N/A";
            for (Match m : recentMatches) {
                int goals = m.getGoalsScoredByTeam(team);
                if (goals > highestScore) {
                    highestScore = goals;
                    highestOpponent = m.getHomeTeam().equalsIgnoreCase(team)
                            ? m.getAwayTeam() : m.getHomeTeam();
                }
            }

            topScorers.add(TopScorer.builder()
                    .teamName(team)
                    .goalsScored(totalGoals)
                    .matchesAnalyzed(recentMatches.size())
                    .avgGoalsPerMatch(PredictionUtils.round((double) totalGoals / recentMatches.size()))
                    .highestScoringMatch(highestScore)
                    .highestScoringOpponent(highestOpponent)
                    .recentForm(buildFormString(matches, team, 5))
                    .build());
        }

        // Sort by total goals, then avg goals per match
        topScorers.sort((a, b) -> {
            int goalsCompare = Integer.compare(b.getGoalsScored(), a.getGoalsScored());
            return goalsCompare != 0 ? goalsCompare : Double.compare(b.getAvgGoalsPerMatch(), a.getAvgGoalsPerMatch());
        });

        return topScorers.stream().limit(TOP_N_RESULTS).toList();
    }

    /**
     * 🧱 Defensive Walls: Teams with most clean sheets recently.
     */
    private List<DefensiveWall> calculateDefensiveWalls(Set<String> teams, LocalDate beforeDate) {
        List<DefensiveWall> defensiveWalls = new ArrayList<>();

        for (String team : teams) {
            List<Match> matches = matchRepository.findByTeamBeforeDate(team, beforeDate);
            if (matches.isEmpty()) continue;

            List<Match> recentMatches = matches.stream().limit(RECENT_MATCHES_WINDOW).toList();

            int cleanSheets = (int) recentMatches.stream()
                    .filter(m -> m.getGoalsConcededByTeam(team) == 0)
                    .count();

            int goalsConceded = recentMatches.stream()
                    .mapToInt(m -> m.getGoalsConcededByTeam(team))
                    .sum();

            // Calculate current clean sheet streak
            int cleanSheetStreak = 0;
            for (Match m : recentMatches) {
                if (m.getGoalsConcededByTeam(team) == 0) {
                    cleanSheetStreak++;
                } else {
                    break;
                }
            }

            defensiveWalls.add(DefensiveWall.builder()
                    .teamName(team)
                    .cleanSheets(cleanSheets)
                    .matchesAnalyzed(recentMatches.size())
                    .cleanSheetPercentage(PredictionUtils.round((double) cleanSheets / recentMatches.size() * 100))
                    .goalsConceded(goalsConceded)
                    .avgGoalsConceded(PredictionUtils.round((double) goalsConceded / recentMatches.size()))
                    .currentCleanSheetStreak(cleanSheetStreak)
                    .build());
        }

        // Sort by clean sheets, then by clean sheet percentage
        defensiveWalls.sort((a, b) -> {
            int csCompare = Integer.compare(b.getCleanSheets(), a.getCleanSheets());
            return csCompare != 0 ? csCompare : Double.compare(b.getCleanSheetPercentage(), a.getCleanSheetPercentage());
        });

        return defensiveWalls.stream().limit(TOP_N_RESULTS).toList();
    }

    /**
     * 🎯 Upset Alerts: Matches where away team has >50% win probability.
     */
    private List<UpsetAlert> calculateUpsetAlerts(Set<String> teams) {
        List<UpsetAlert> upsetAlerts = new ArrayList<>();

        // We need upcoming matches - generate potential matchups from current teams
        // In production, this would use actual scheduled fixtures
        // For now, we'll analyze all possible Premier League matchups and identify upsets

        try {
            // Check if model is ready
            if (!modelTrainingService.isModelLoaded()) {
                log.warn("Model not ready - skipping upset alerts");
                return upsetAlerts;
            }

            // Get Premier League teams (top 20 most frequent in recent data)
            LocalDate beforeDate = LocalDate.now().plusDays(1);
            Map<String, Long> teamMatchCounts = new HashMap<>();
            for (String team : teams) {
                List<Match> matches = matchRepository.findByTeamBeforeDate(team, beforeDate);
                // Filter to last 2 seasons approximately
                long recentCount = matches.stream()
                        .filter(m -> m.getMatchDate().isAfter(LocalDate.now().minusYears(2)))
                        .count();
                if (recentCount >= 30) { // At least ~1 season worth
                    teamMatchCounts.put(team, recentCount);
                }
            }

            List<String> activeTeams = teamMatchCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(20)
                    .map(Map.Entry::getKey)
                    .toList();

            // Analyze matchups where away team is stronger
            for (String homeTeam : activeTeams) {
                for (String awayTeam : activeTeams) {
                    if (homeTeam.equals(awayTeam)) continue;

                    try {
                        MatchFeatures features = featureEngineeringService.buildFeaturesForPrediction(homeTeam, awayTeam);
                        double[] probs = modelTrainingService.predict(features);

                        // Upset = away win probability > 50%
                        if (probs[2] > 0.50) {
                            String reason = generateUpsetReason(features, homeTeam, awayTeam, probs);

                            upsetAlerts.add(UpsetAlert.builder()
                                    .homeTeam(homeTeam)
                                    .awayTeam(awayTeam)
                                    .matchDate("Hypothetical")
                                    .awayWinProbability(PredictionUtils.round(probs[2] * 100))
                                    .homeWinProbability(PredictionUtils.round(probs[0] * 100))
                                    .drawProbability(PredictionUtils.round(probs[1] * 100))
                                    .confidence(PredictionUtils.getConfidence(probs))
                                    .reason(reason)
                                    .homeTeamFormPoints((int) Math.round(features.getHomeFormPoints() * 5))
                                    .awayTeamFormPoints((int) Math.round(features.getAwayFormPoints() * 5))
                                    .build());
                        }
                    } catch (Exception e) {
                        // Skip this matchup
                    }
                }
            }

            // Sort by away win probability
            upsetAlerts.sort((a, b) -> Double.compare(b.getAwayWinProbability(), a.getAwayWinProbability()));

        } catch (Exception e) {
            log.error("Error calculating upset alerts: {}", e.getMessage());
        }

        return upsetAlerts.stream().limit(TOP_N_RESULTS).toList();
    }

    /**
     * 🎉 Goal Fest Predictions: Matches with highest expected total goals.
     */
    private List<GoalFestMatch> calculateGoalFestMatches(Set<String> teams) {
        List<GoalFestMatch> goalFestMatches = new ArrayList<>();

        try {
            LocalDate beforeDate = LocalDate.now().plusDays(1);

            // Get active teams
            Map<String, Double> teamAvgGoals = new HashMap<>();
            Map<String, Double> teamAvgConceded = new HashMap<>();

            for (String team : teams) {
                List<Match> matches = matchRepository.findByTeamBeforeDate(team, beforeDate);
                List<Match> recentMatches = matches.stream().limit(RECENT_MATCHES_WINDOW).toList();

                if (recentMatches.size() >= 5) {
                    double avgScored = recentMatches.stream()
                            .mapToInt(m -> m.getGoalsScoredByTeam(team))
                            .average()
                            .orElse(0);
                    double avgConceded = recentMatches.stream()
                            .mapToInt(m -> m.getGoalsConcededByTeam(team))
                            .average()
                            .orElse(0);

                    teamAvgGoals.put(team, avgScored);
                    teamAvgConceded.put(team, avgConceded);
                }
            }

            // Find high-scoring matchups
            List<String> activeTeams = new ArrayList<>(teamAvgGoals.keySet());

            for (String homeTeam : activeTeams) {
                for (String awayTeam : activeTeams) {
                    if (homeTeam.equals(awayTeam)) continue;

                    double homeScoring = teamAvgGoals.getOrDefault(homeTeam, 0.0);
                    double awayConceding = teamAvgConceded.getOrDefault(awayTeam, 0.0);
                    double awayScoring = teamAvgGoals.getOrDefault(awayTeam, 0.0);
                    double homeConceding = teamAvgConceded.getOrDefault(homeTeam, 0.0);

                    // Expected goals = (home attack + away defense) / 2 + (away attack + home defense) / 2
                    double expectedHomeGoals = (homeScoring + awayConceding) / 2;
                    double expectedAwayGoals = (awayScoring + homeConceding) / 2;
                    double expectedTotalGoals = expectedHomeGoals + expectedAwayGoals;

                    // Only include matches expected to have 3+ goals
                    if (expectedTotalGoals >= 3.0) {
                        // Estimate over 2.5 probability (simplified)
                        double over25Prob = Math.min(95, expectedTotalGoals * 25);

                        // BTTS probability (if both teams score regularly)
                        double bttsPct = Math.min(90, (homeScoring + awayScoring) * 20);

                        goalFestMatches.add(GoalFestMatch.builder()
                                .homeTeam(homeTeam)
                                .awayTeam(awayTeam)
                                .matchDate("Hypothetical")
                                .expectedTotalGoals(PredictionUtils.round(expectedTotalGoals))
                                .homeTeamAvgScoring(PredictionUtils.round(homeScoring))
                                .awayTeamAvgScoring(PredictionUtils.round(awayScoring))
                                .homeTeamAvgConceding(PredictionUtils.round(homeConceding))
                                .awayTeamAvgConceding(PredictionUtils.round(awayConceding))
                                .over25Probability(PredictionUtils.round(over25Prob))
                                .bttsPercentage(PredictionUtils.round(bttsPct))
                                .build());
                    }
                }
            }

            // Sort by expected total goals
            goalFestMatches.sort((a, b) -> Double.compare(b.getExpectedTotalGoals(), a.getExpectedTotalGoals()));

        } catch (Exception e) {
            log.error("Error calculating goal fest matches: {}", e.getMessage());
        }

        return goalFestMatches.stream().limit(TOP_N_RESULTS).toList();
    }

    // ── Helper Methods ──────────────────────────────────────────────────────

    private int calcWinStreak(List<Match> matches, String teamName) {
        int streak = 0;
        for (Match m : matches) {
            if (m.getPointsForTeam(teamName) == 3) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    private int calcWinlessStreak(List<Match> matches, String teamName) {
        int streak = 0;
        for (Match m : matches) {
            if (m.getPointsForTeam(teamName) < 3) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    private String buildFormString(List<Match> matches, String teamName, int limit) {
        return matches.stream()
                .limit(limit)
                .map(m -> {
                    int points = m.getPointsForTeam(teamName);
                    if (points == 3) return "W";
                    if (points == 1) return "D";
                    return "L";
                })
                .collect(Collectors.joining());
    }

    private String generateUpsetReason(MatchFeatures features, String homeTeam, String awayTeam, double[] probs) {
        StringBuilder reason = new StringBuilder();

        if (features.getAwayFormPoints() > features.getHomeFormPoints() + 0.5) {
            reason.append(awayTeam).append(" in better form. ");
        }
        if (features.getAwayWinStreak() >= 3) {
            reason.append(awayTeam).append(" on ").append(features.getAwayWinStreak()).append("-match win streak. ");
        }
        if (features.getHomeWinStreak() == 0 && features.getHomeUnbeatenStreak() < 3) {
            reason.append(homeTeam).append(" struggling at home. ");
        }
        if (features.getAwayGoalsScoredAvg() > features.getHomeGoalsConcededAvg()) {
            reason.append(awayTeam).append(" attack > ").append(homeTeam).append(" defense. ");
        }

        if (reason.isEmpty()) {
            reason.append("Statistical model predicts ").append(awayTeam).append(" advantage.");
        }

        return reason.toString().trim();
    }
}

