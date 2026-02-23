package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.model.MatchFeatures;
import com.app.common.model.SeasonTeamStats;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.SeasonTeamStatsRepository;
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
 *
 * <p><b>IMPORTANT:</b> All insights are calculated strictly within the selected season.
 * This ensures:
 * <ul>
 *   <li>No cross-season data leakage</li>
 *   <li>Streak logic resets at season boundaries</li>
 *   <li>Rankings are per-season</li>
 *   <li>Goal aggregates are per-season</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrendingInsightsService {

    private final MatchRepository matchRepository;
    private final SeasonTeamStatsRepository seasonTeamStatsRepository;
    private final FeatureEngineeringService featureEngineeringService;
    private final ModelTrainingService modelTrainingService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int RECENT_MATCHES_WINDOW = 10;
    private static final int HOT_STREAK_THRESHOLD = 3;          // Current consecutive wins
    private static final int HOT_FORM_THRESHOLD = 4;            // Wins in last 5 matches (alternative)
    private static final int HOT_FORM_WINDOW = 5;               // Window for alternative hot form check
    private static final int COLD_STREAK_THRESHOLD = 5;
    private static final int TOP_N_RESULTS = 5;
    private static final double UPSET_PROBABILITY_THRESHOLD = 0.40; // 40% threshold for upset alerts
    private static final double ELO_UPSET_THRESHOLD = 50.0;     // Minimum Elo difference for upset consideration

    /**
     * Get the current season identifier.
     * Returns the season of the most recent completed match.
     */
    public String getCurrentSeason() {
        String season = matchRepository.findCurrentSeason();
        if (season == null || season.isEmpty()) {
            log.warn("No current season found in database");
            return null;
        }
        return season;
    }

    /**
     * Get all available seasons.
     */
    public List<String> getAvailableSeasons() {
        return matchRepository.findAllSeasons();
    }

    /**
     * Get all trending insights for the current season.
     * Delegates to the season-specific method using the current season.
     */
    @Cacheable(value = "trendingInsights", key = "'all'")
    public TrendingInsightsResponse getTrendingInsights() {
        String currentSeason = getCurrentSeason();
        if (currentSeason == null) {
            log.warn("No current season available - returning empty insights");
            return buildEmptyResponse();
        }
        return getTrendingInsightsBySeason(currentSeason);
    }

    /**
     * Get all trending insights for a specific season.
     * All insights are calculated strictly within the selected season:
     * - No cross-season data leakage
     * - Streak logic resets at season boundaries
     * - Rankings are per-season
     * - Goal aggregates are per-season
     */
    @Cacheable(value = "trendingInsights", key = "#season")
    public TrendingInsightsResponse getTrendingInsightsBySeason(String season) {
        log.info("Calculating trending insights for season: {}", season);

        // Validate season exists
        List<String> availableSeasons = getAvailableSeasons();
        if (!availableSeasons.contains(season)) {
            log.warn("Season {} not found in database. Available: {}", season, availableSeasons);
            return buildEmptyResponse();
        }

        LocalDate beforeDate = LocalDate.now().plusDays(1);

        // Get teams that played in the specified season only
        Set<String> seasonTeams = getTeamsForSeason(season);

        log.debug("Analyzing {} teams for trends in season {}", seasonTeams.size(), season);

        // Calculate all insights using season-filtered data
        List<HotTeam> hotTeams = calculateHotTeams(seasonTeams, beforeDate, season);
        List<ColdTeam> coldTeams = calculateColdTeams(seasonTeams, beforeDate, season);
        List<TopScorer> topScorers = calculateTopScorers(seasonTeams, beforeDate, season);
        List<DefensiveWall> defensiveWalls = calculateDefensiveWalls(seasonTeams, beforeDate, season);
        List<UpsetAlert> upsetAlerts = calculateUpsetAlerts(seasonTeams, season);
        List<GoalFestMatch> goalFestMatches = calculateGoalFestMatches(seasonTeams, season);

        log.info("Insights calculated for season {}: {} hot teams, {} cold teams, {} top scorers, {} defensive walls, {} upset alerts, {} goal fest matches",
                season, hotTeams.size(), coldTeams.size(), topScorers.size(),
                defensiveWalls.size(), upsetAlerts.size(), goalFestMatches.size());

        return TrendingInsightsResponse.builder()
                .hotTeams(hotTeams)
                .coldTeams(coldTeams)
                .topScorers(topScorers)
                .defensiveWalls(defensiveWalls)
                .upsetAlerts(upsetAlerts)
                .goalFestMatches(goalFestMatches)
                .generatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .totalTeamsAnalyzed(seasonTeams.size())
                .season(season)
                .build();
    }

    /**
     * Build an empty response when no data is available.
     */
    private TrendingInsightsResponse buildEmptyResponse() {
        return TrendingInsightsResponse.builder()
                .hotTeams(Collections.emptyList())
                .coldTeams(Collections.emptyList())
                .topScorers(Collections.emptyList())
                .defensiveWalls(Collections.emptyList())
                .upsetAlerts(Collections.emptyList())
                .goalFestMatches(Collections.emptyList())
                .generatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .totalTeamsAnalyzed(0)
                .season(null)
                .build();
    }

    /**
     * Get all teams that played in a specific season.
     * Only includes teams with completed matches (fullTimeResult IS NOT NULL).
     */
    private Set<String> getTeamsForSeason(String season) {
        List<String> teamNames = matchRepository.findAllDistinctTeamNamesBySeason(season);
        Set<String> teams = new TreeSet<>(teamNames);

        // Also add away teams
        List<Match> seasonMatches = matchRepository.findBySeasonOrderByMatchDateDesc(season);
        for (Match match : seasonMatches) {
            teams.add(match.getHomeTeam());
            teams.add(match.getAwayTeam());
        }

        return teams;
    }

    /**
     * 🔥 Hot Teams: Teams on 3+ match winning streaks OR teams with 4+ wins in last 5 matches.
     * Prioritizes consecutive winning streaks, but also includes hot form teams.
     * Requires minimum of HOT_FORM_WINDOW matches to qualify.
     *
     * <p>All data is filtered to the specified season only - streaks do not carry over
     * from previous seasons.
     */
    private List<HotTeam> calculateHotTeams(Set<String> teams, LocalDate beforeDate, String season) {
        List<HotTeam> hotTeams = new ArrayList<>();
        Set<String> teamsWithStreak = new HashSet<>();

        // Cache matches to avoid N+1 queries in second pass
        Map<String, List<Match>> matchCache = new HashMap<>();

        // First pass: Find teams with consecutive winning streaks
        for (String team : teams) {
            // Use season-filtered query - ensures no previous season data leaks
            List<Match> matches = matchRepository.findByTeamAndSeasonBeforeDate(team, season, beforeDate);
            matchCache.put(team, matches); // Cache for potential second pass

            // Minimum match requirement to prevent false positives for new teams
            if (matches.size() < HOT_FORM_WINDOW) continue;

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

                // Use cached matches instead of querying again
                List<Match> matches = matchCache.get(team);
                if (matches == null || matches.size() < HOT_FORM_WINDOW) continue;

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
     * Requires minimum of COLD_STREAK_THRESHOLD matches to qualify (prevents false positives for new teams).
     *
     * <p>All data is filtered to the specified season only - streaks do not carry over
     * from previous seasons.
     */
    private List<ColdTeam> calculateColdTeams(Set<String> teams, LocalDate beforeDate, String season) {
        List<ColdTeam> coldTeams = new ArrayList<>();

        for (String team : teams) {
            // Use season-filtered query - ensures no previous season data leaks
            List<Match> matches = matchRepository.findByTeamAndSeasonBeforeDate(team, season, beforeDate);

            // Minimum match requirement to prevent false positives for new teams
            if (matches.size() < COLD_STREAK_THRESHOLD) continue;

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
     * ⚽ Top Scorers: Teams scoring most goals in recent matches within the season.
     *
     * <p>All goal aggregates are calculated within the specified season only.
     */
    private List<TopScorer> calculateTopScorers(Set<String> teams, LocalDate beforeDate, String season) {
        List<TopScorer> topScorers = new ArrayList<>();

        for (String team : teams) {
            // Use season-filtered query
            List<Match> matches = matchRepository.findByTeamAndSeasonBeforeDate(team, season, beforeDate);
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
     * 🧱 Defensive Walls: Teams with most clean sheets recently within the season.
     *
     * <p>Clean sheet streaks are calculated within the specified season only.
     */
    private List<DefensiveWall> calculateDefensiveWalls(Set<String> teams, LocalDate beforeDate, String season) {
        List<DefensiveWall> defensiveWalls = new ArrayList<>();

        for (String team : teams) {
            // Use season-filtered query
            List<Match> matches = matchRepository.findByTeamAndSeasonBeforeDate(team, season, beforeDate);
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
     * 🎯 Upset Alerts: Matches where lower-Elo team has significant win probability.
     *
     * <p>Analysis uses both ML predictions and Elo ratings to identify potential upsets.
     * An upset is flagged when:
     * <ul>
     *   <li>Away team has >40% win probability despite lower Elo rating, OR</li>
     *   <li>Home team has >40% win probability despite significantly lower Elo</li>
     * </ul>
     *
     * <p>Analysis is based on teams from the specified season only.
     */
    private List<UpsetAlert> calculateUpsetAlerts(Set<String> teams, String season) {
        List<UpsetAlert> upsetAlerts = new ArrayList<>();

        try {
            // Check if model is ready
            if (!modelTrainingService.isModelLoaded()) {
                log.warn("Model not ready - using Elo-only upset detection");
            }

            // Get teams that have played in the specified season only
            LocalDate beforeDate = LocalDate.now().plusDays(1);
            Map<String, Long> teamMatchCounts = new HashMap<>();
            Map<String, Double> teamEloRatings = new HashMap<>();

            for (String team : teams) {
                // Use season-filtered query to get match counts within this season only
                List<Match> matches = matchRepository.findByTeamAndSeasonBeforeDate(team, season, beforeDate);
                long matchCount = matches.size();
                // Require at least 3 matches in this season to qualify (lowered from 5)
                if (matchCount >= 3) {
                    teamMatchCounts.put(team, matchCount);

                    // Get Elo rating from season stats
                    Optional<SeasonTeamStats> stats = seasonTeamStatsRepository
                            .findBySeasonIdAndTeamNameIgnoreCase(season, team);
                    double eloRating = stats.map(SeasonTeamStats::getEloRating)
                            .orElse(SeasonTeamStats.DEFAULT_ELO_RATING);
                    teamEloRatings.put(team, eloRating);
                }
            }

            List<String> activeTeams = teamMatchCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(20)
                    .map(Map.Entry::getKey)
                    .toList();

            log.debug("Analyzing {} active teams for upset potential in season {}", activeTeams.size(), season);

            // Analyze matchups for upset potential
            for (String homeTeam : activeTeams) {
                for (String awayTeam : activeTeams) {
                    if (homeTeam.equals(awayTeam)) continue;

                    try {
                        double homeElo = teamEloRatings.getOrDefault(homeTeam, 1500.0);
                        double awayElo = teamEloRatings.getOrDefault(awayTeam, 1500.0);
                        double eloDiff = homeElo - awayElo;

                        // Get ML predictions if model is loaded
                        double[] probs = {0.33, 0.34, 0.33}; // Default
                        MatchFeatures features = null;

                        if (modelTrainingService.isModelLoaded()) {
                            features = featureEngineeringService.buildFeaturesForPrediction(homeTeam, awayTeam);
                            probs = modelTrainingService.predict(features);
                        }

                        // Upset detection logic:
                        // 1. Home team has higher Elo but away team has >40% win probability
                        // 2. Away team has higher Elo but home team has >40% win probability
                        boolean isUpset = false;
                        String upsetType = "";

                        if (eloDiff > ELO_UPSET_THRESHOLD && probs[2] > UPSET_PROBABILITY_THRESHOLD) {
                            // Home team stronger but away might win
                            isUpset = true;
                            upsetType = "Away upset vs stronger home team";
                        } else if (eloDiff < -ELO_UPSET_THRESHOLD && probs[0] > UPSET_PROBABILITY_THRESHOLD) {
                            // Away team stronger but home might win
                            isUpset = true;
                            upsetType = "Home upset vs stronger away team";
                        }

                        if (isUpset) {
                            String reason = generateUpsetReasonWithElo(features, homeTeam, awayTeam, probs, homeElo, awayElo, upsetType);

                            upsetAlerts.add(UpsetAlert.builder()
                                    .homeTeam(homeTeam)
                                    .awayTeam(awayTeam)
                                    .matchDate("Potential")
                                    .awayWinProbability(PredictionUtils.round(probs[2] * 100))
                                    .homeWinProbability(PredictionUtils.round(probs[0] * 100))
                                    .drawProbability(PredictionUtils.round(probs[1] * 100))
                                    .confidence(PredictionUtils.getConfidence(probs))
                                    .reason(reason)
                                    .homeTeamFormPoints(features != null ? (int) Math.round(features.getHomeFormPoints() * 5) : 0)
                                    .awayTeamFormPoints(features != null ? (int) Math.round(features.getAwayFormPoints() * 5) : 0)
                                    .build());
                        }
                    } catch (Exception e) {
                        // Skip this matchup - log at debug level to avoid spam
                        log.debug("Skipping matchup {} vs {}: {}", homeTeam, awayTeam, e.getMessage());
                    }
                }
            }

            // Sort by highest upset probability (away win for away upsets, home win for home upsets)
            upsetAlerts.sort((a, b) -> {
                double aMax = Math.max(a.getAwayWinProbability(), a.getHomeWinProbability());
                double bMax = Math.max(b.getAwayWinProbability(), b.getHomeWinProbability());
                return Double.compare(bMax, aMax);
            });

            log.info("Found {} potential upset alerts for season {}", upsetAlerts.size(), season);

        } catch (Exception e) {
            log.error("Error calculating upset alerts: {}", e.getMessage(), e);
        }

        return upsetAlerts.stream().limit(TOP_N_RESULTS).toList();
    }

    /**
     * Generate upset reason including Elo information.
     */
    private String generateUpsetReasonWithElo(MatchFeatures features, String homeTeam, String awayTeam,
                                               double[] probs, double homeElo, double awayElo, String upsetType) {
        StringBuilder reason = new StringBuilder();
        reason.append(upsetType).append(". ");

        double eloDiff = Math.abs(homeElo - awayElo);
        reason.append(String.format("Elo difference: %.0f points. ", eloDiff));

        if (features != null) {
            if (features.getAwayFormPoints() > features.getHomeFormPoints() + 0.1) {
                reason.append(awayTeam).append(" in better form. ");
            } else if (features.getHomeFormPoints() > features.getAwayFormPoints() + 0.1) {
                reason.append(homeTeam).append(" in better form. ");
            }

            if (features.getH2hAwayWinRate() > 0.4) {
                reason.append("Strong H2H record for ").append(awayTeam).append(". ");
            } else if (features.getH2hHomeWinRate() > 0.6) {
                reason.append("Strong H2H record for ").append(homeTeam).append(". ");
            }
        }

        return reason.toString().trim();
    }


    /**
     * 🎉 Goal Fest Predictions: Matches with highest expected total goals.
     *
     * <p>Goal averages are calculated within the specified season only.
     */
    private List<GoalFestMatch> calculateGoalFestMatches(Set<String> teams, String season) {
        List<GoalFestMatch> goalFestMatches = new ArrayList<>();

        try {
            LocalDate beforeDate = LocalDate.now().plusDays(1);

            // Get active teams with goal averages from this season only
            Map<String, Double> teamAvgGoals = new HashMap<>();
            Map<String, Double> teamAvgConceded = new HashMap<>();

            for (String team : teams) {
                // Use season-filtered query
                List<Match> matches = matchRepository.findByTeamAndSeasonBeforeDate(team, season, beforeDate);
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
                        goalFestMatches.add(GoalFestMatch.builder()
                                .homeTeam(homeTeam)
                                .awayTeam(awayTeam)
                                .matchDate("Hypothetical")
                                .expectedTotalGoals(PredictionUtils.round(expectedTotalGoals))
                                .homeTeamAvgScoring(PredictionUtils.round(homeScoring))
                                .awayTeamAvgScoring(PredictionUtils.round(awayScoring))
                                .homeTeamAvgConceding(PredictionUtils.round(homeConceding))
                                .awayTeamAvgConceding(PredictionUtils.round(awayConceding))
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

