package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.common.util.PredictionUtils;
import com.app.footballprediction.dto.TeamFormResponse;
import com.app.footballprediction.dto.TeamStatsResponse;
import com.app.footballprediction.dto.TeamStatsResponse.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating comprehensive team statistics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeamStatsService {

    private final MatchRepository matchRepository;
    private final TeamValidationService teamValidationService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Get comprehensive statistics for a team.
     * Supports fuzzy team name matching for better UX.
     */
    @Cacheable(value = "teamStats", key = "#teamName")
    public TeamStatsResponse getTeamStats(String teamName) {
        log.info("Calculating stats for team: '{}'", teamName);

        LocalDate now = LocalDate.now();
        LocalDate beforeDate = now.plusDays(1);

        // Try to resolve the team name (handles case-insensitivity and fuzzy matching)
        String resolvedTeamName = resolveTeamName(teamName, beforeDate);

        log.debug("Query parameters: teamName='{}', resolvedTo='{}', beforeDate={}",
                  teamName, resolvedTeamName, beforeDate);

        // Fetch all matches for the resolved team name
        List<Match> allMatches = matchRepository.findByTeamBeforeDate(resolvedTeamName, beforeDate);
        List<Match> homeMatches = matchRepository.findHomeMatchesByTeamBeforeDate(resolvedTeamName, beforeDate);
        List<Match> awayMatches = matchRepository.findAwayMatchesByTeamBeforeDate(resolvedTeamName, beforeDate);

        log.debug("Found {} total matches for {}", allMatches.size(), resolvedTeamName);

        return TeamStatsResponse.builder()
                .teamName(resolvedTeamName)
                .overall(calculateOverallStats(allMatches, resolvedTeamName))
                .homeStats(calculateHomeAwayStats(homeMatches, resolvedTeamName, true))
                .awayStats(calculateHomeAwayStats(awayMatches, resolvedTeamName, false))
                .goalStats(calculateGoalStats(allMatches, resolvedTeamName))
                .formStats(calculateFormStats(allMatches, resolvedTeamName))
                .recentMatches(getRecentMatches(allMatches, resolvedTeamName, 10))
                .currentSeason(calculateCurrentSeasonStats(allMatches, resolvedTeamName))
                .topRivals(calculateTopRivals(allMatches, resolvedTeamName, 5))
                .build();
    }

    /**
     * Resolve team name via centralized validation service.
     */
    private String resolveTeamName(String teamName, LocalDate beforeDate) {
        return teamValidationService.resolveTeamName(teamName);
    }

    /**
     * Calculate overall statistics across all matches.
     */
    private OverallStats calculateOverallStats(List<Match> matches, String teamName) {
        int wins = 0, draws = 0, losses = 0;
        int goalsScored = 0, goalsConceded = 0;

        for (Match m : matches) {
            int points = m.getPointsForTeam(teamName);
            if (points == 3) wins++;
            else if (points == 1) draws++;
            else losses++;

            goalsScored += m.getGoalsScoredByTeam(teamName);
            goalsConceded += m.getGoalsConcededByTeam(teamName);
        }

        int totalMatches = matches.size();
        int totalPoints = wins * 3 + draws;

        return OverallStats.builder()
                .totalMatches(totalMatches)
                .wins(wins)
                .draws(draws)
                .losses(losses)
                .winPercentage(totalMatches > 0 ? PredictionUtils.round((double) wins / totalMatches * 100) : 0)
                .goalsScored(goalsScored)
                .goalsConceded(goalsConceded)
                .goalDifference(goalsScored - goalsConceded)
                .points(totalPoints)
                .pointsPerGame(totalMatches > 0 ? PredictionUtils.round((double) totalPoints / totalMatches) : 0)
                .build();
    }

    /**
     * Calculate home or away specific statistics.
     */
    private HomeAwayStats calculateHomeAwayStats(List<Match> matches, String teamName, boolean isHome) {
        int wins = 0, draws = 0, losses = 0;
        int goalsScored = 0, goalsConceded = 0, cleanSheets = 0;

        for (Match m : matches) {
            int points = m.getPointsForTeam(teamName);
            if (points == 3) wins++;
            else if (points == 1) draws++;
            else losses++;

            int scored = m.getGoalsScoredByTeam(teamName);
            int conceded = m.getGoalsConcededByTeam(teamName);
            goalsScored += scored;
            goalsConceded += conceded;

            if (conceded == 0) cleanSheets++;
        }

        int totalMatches = matches.size();

        return HomeAwayStats.builder()
                .matches(totalMatches)
                .wins(wins)
                .draws(draws)
                .losses(losses)
                .winPercentage(totalMatches > 0 ? PredictionUtils.round((double) wins / totalMatches * 100) : 0)
                .goalsScored(goalsScored)
                .goalsConceded(goalsConceded)
                .cleanSheets(cleanSheets)
                .avgGoalsScored(totalMatches > 0 ? PredictionUtils.round((double) goalsScored / totalMatches) : 0)
                .avgGoalsConceded(totalMatches > 0 ? PredictionUtils.round((double) goalsConceded / totalMatches) : 0)
                .build();
    }

    /**
     * Calculate goal scoring and conceding patterns.
     */
    private GoalStats calculateGoalStats(List<Match> matches, String teamName) {
        int totalGoalsScored = 0, totalGoalsConceded = 0;
        int firstHalfGoals = 0, secondHalfGoals = 0;
        int firstHalfConceded = 0, secondHalfConceded = 0;
        int cleanSheets = 0, failedToScore = 0;

        for (Match m : matches) {
            int scored = m.getGoalsScoredByTeam(teamName);
            int conceded = m.getGoalsConcededByTeam(teamName);
            totalGoalsScored += scored;
            totalGoalsConceded += conceded;

            if (conceded == 0) cleanSheets++;
            if (scored == 0) failedToScore++;

            // Half-time goal analysis
            if (m.getHalfTimeHomeGoals() != null && m.getHalfTimeAwayGoals() != null) {
                boolean isHome = m.getHomeTeam().equalsIgnoreCase(teamName);
                int htScored = isHome ? m.getHalfTimeHomeGoals() : m.getHalfTimeAwayGoals();
                int htConceded = isHome ? m.getHalfTimeAwayGoals() : m.getHalfTimeHomeGoals();

                firstHalfGoals += htScored;
                secondHalfGoals += (scored - htScored);
                firstHalfConceded += htConceded;
                secondHalfConceded += (conceded - htConceded);
            }
        }

        int totalMatches = matches.size();
        int totalGoalsForTiming = firstHalfGoals + secondHalfGoals;

        return GoalStats.builder()
                .avgGoalsScored(totalMatches > 0 ? PredictionUtils.round((double) totalGoalsScored / totalMatches) : 0)
                .avgGoalsConceded(totalMatches > 0 ? PredictionUtils.round((double) totalGoalsConceded / totalMatches) : 0)
                .avgTotalGoalsPerMatch(totalMatches > 0 ? PredictionUtils.round((double) (totalGoalsScored + totalGoalsConceded) / totalMatches) : 0)
                .firstHalfGoals(firstHalfGoals)
                .secondHalfGoals(secondHalfGoals)
                .firstHalfConceded(firstHalfConceded)
                .secondHalfConceded(secondHalfConceded)
                .firstHalfScoringRate(totalGoalsForTiming > 0 ? PredictionUtils.round((double) firstHalfGoals / totalGoalsForTiming * 100) : 0)
                .secondHalfScoringRate(totalGoalsForTiming > 0 ? PredictionUtils.round((double) secondHalfGoals / totalGoalsForTiming * 100) : 0)
                .cleanSheets(cleanSheets)
                .failedToScore(failedToScore)
                .cleanSheetPercentage(totalMatches > 0 ? PredictionUtils.round((double) cleanSheets / totalMatches * 100) : 0)
                .build();
    }

    /**
     * Calculate form and momentum statistics.
     */
    private FormStats calculateFormStats(List<Match> matches, String teamName) {
        // Last 5 and last 10 form
        double last5Points = calcFormPoints(matches, teamName, 5);
        double last10Points = calcFormPoints(matches, teamName, 10);
        String last5Form = buildFormString(matches, teamName, 5);
        String last10Form = buildFormString(matches, teamName, 10);

        // Streaks
        int currentWinStreak = calcWinStreak(matches, teamName);
        int currentUnbeatenStreak = calcUnbeatenStreak(matches, teamName);
        int currentWinlessStreak = calcWinlessStreak(matches, teamName);
        int longestWinStreak = calcLongestWinStreak(matches, teamName);
        int longestUnbeatenStreak = calcLongestUnbeatenStreak(matches, teamName);

        // Shots and corners (from recent matches)
        double avgShotsOnTarget = calcAvgShotsOnTarget(matches, teamName, 10);
        double avgCorners = calcAvgCorners(matches, teamName, 10);
        double shotConversionRate = calcShotConversionRate(matches, teamName, 10);

        return FormStats.builder()
                .last5FormPoints(last5Points)
                .last10FormPoints(last10Points)
                .last5Form(last5Form)
                .last10Form(last10Form)
                .currentWinStreak(currentWinStreak)
                .currentUnbeatenStreak(currentUnbeatenStreak)
                .currentWinlessStreak(currentWinlessStreak)
                .longestWinStreak(longestWinStreak)
                .longestUnbeatenStreak(longestUnbeatenStreak)
                .avgShotsOnTarget(avgShotsOnTarget)
                .avgCorners(avgCorners)
                .shotConversionRate(shotConversionRate)
                .build();
    }

    /**
     * Get recent match results for form visualization.
     */
    private List<RecentMatch> getRecentMatches(List<Match> matches, String teamName, int limit) {
        String normalizedTeamName = teamName.trim();
        return matches.stream()
                .limit(limit)
                .map(m -> {
                    boolean isHome = m.getHomeTeam().trim().equalsIgnoreCase(normalizedTeamName);
                    int goalsFor = m.getGoalsScoredByTeam(teamName);
                    int goalsAgainst = m.getGoalsConcededByTeam(teamName);
                    String result = getResultLetter(m.getPointsForTeam(teamName));

                    return RecentMatch.builder()
                            .date(m.getMatchDate().format(DATE_FORMATTER))
                            .opponent(isHome ? m.getAwayTeam() : m.getHomeTeam())
                            .isHome(isHome)
                            .goalsFor(goalsFor)
                            .goalsAgainst(goalsAgainst)
                            .result(result)
                            .score(goalsFor + "-" + goalsAgainst)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Calculate current season statistics.
     */
    private SeasonStats calculateCurrentSeasonStats(List<Match> matches, String teamName) {
        // Current season: matches from August of current/previous year
        LocalDate now = LocalDate.now();
        int seasonStartYear = now.getMonthValue() >= 8 ? now.getYear() : now.getYear() - 1;
        LocalDate seasonStart = LocalDate.of(seasonStartYear, 8, 1);

        List<Match> seasonMatches = matches.stream()
                .filter(m -> m.getMatchDate().isAfter(seasonStart.minusDays(1)))
                .collect(Collectors.toList());

        int wins = 0, draws = 0, losses = 0;
        int goalsScored = 0, goalsConceded = 0;

        for (Match m : seasonMatches) {
            int points = m.getPointsForTeam(teamName);
            if (points == 3) wins++;
            else if (points == 1) draws++;
            else losses++;

            goalsScored += m.getGoalsScoredByTeam(teamName);
            goalsConceded += m.getGoalsConcededByTeam(teamName);
        }

        int totalPoints = wins * 3 + draws;
        String season = seasonStartYear + "/" + String.valueOf(seasonStartYear + 1).substring(2);

        return SeasonStats.builder()
                .season(season)
                .matchesPlayed(seasonMatches.size())
                .wins(wins)
                .draws(draws)
                .losses(losses)
                .points(totalPoints)
                .goalsScored(goalsScored)
                .goalsConceded(goalsConceded)
                .goalDifference(goalsScored - goalsConceded)
                .position(0) // Would need full league data to calculate
                .build();
    }

    /**
     * Calculate head-to-head records against top rivals.
     */
    private List<H2HRecord> calculateTopRivals(List<Match> allMatches, String teamName, int limit) {
        // Group matches by opponent
        Map<String, List<Match>> matchesByOpponent = new HashMap<>();

        for (Match m : allMatches) {
            String opponent = m.getHomeTeam().equalsIgnoreCase(teamName)
                    ? m.getAwayTeam()
                    : m.getHomeTeam();

            matchesByOpponent.computeIfAbsent(opponent, k -> new ArrayList<>()).add(m);
        }

        // Calculate H2H stats and sort by number of matches
        return matchesByOpponent.entrySet().stream()
                .filter(e -> e.getValue().size() >= 3) // At least 3 matches
                .map(e -> calculateH2HRecord(e.getKey(), e.getValue(), teamName))
                .sorted((a, b) -> Integer.compare(b.getTotalMatches(), a.getTotalMatches()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private H2HRecord calculateH2HRecord(String opponent, List<Match> matches, String teamName) {
        int wins = 0, draws = 0, losses = 0;
        int goalsFor = 0, goalsAgainst = 0;

        for (Match m : matches) {
            int points = m.getPointsForTeam(teamName);
            if (points == 3) wins++;
            else if (points == 1) draws++;
            else losses++;

            goalsFor += m.getGoalsScoredByTeam(teamName);
            goalsAgainst += m.getGoalsConcededByTeam(teamName);
        }

        return H2HRecord.builder()
                .opponent(opponent)
                .totalMatches(matches.size())
                .wins(wins)
                .draws(draws)
                .losses(losses)
                .goalsFor(goalsFor)
                .goalsAgainst(goalsAgainst)
                .winPercentage(PredictionUtils.round((double) wins / matches.size() * 100))
                .build();
    }

    // ── Helper methods ───────────────────────────────────────────────────

    private double calcFormPoints(List<Match> matches, String teamName, int window) {
        return matches.stream()
                .limit(window)
                .mapToInt(m -> m.getPointsForTeam(teamName))
                .average()
                .orElse(0.0);
    }

    private String buildFormString(List<Match> matches, String teamName, int limit) {
        return matches.stream()
                .limit(limit)
                .map(m -> getResultLetter(m.getPointsForTeam(teamName)))
                .collect(Collectors.joining());
    }

    private String getResultLetter(int points) {
        return switch (points) {
            case 3 -> "W";
            case 1 -> "D";
            default -> "L";
        };
    }

    private int calcWinStreak(List<Match> matches, String teamName) {
        int streak = 0;
        for (Match m : matches) {
            if (m.getPointsForTeam(teamName) == 3) streak++;
            else break;
        }
        return streak;
    }

    private int calcUnbeatenStreak(List<Match> matches, String teamName) {
        int streak = 0;
        for (Match m : matches) {
            if (m.getPointsForTeam(teamName) >= 1) streak++;
            else break;
        }
        return streak;
    }

    private int calcWinlessStreak(List<Match> matches, String teamName) {
        int streak = 0;
        for (Match m : matches) {
            if (m.getPointsForTeam(teamName) < 3) streak++;
            else break;
        }
        return streak;
    }

    private int calcLongestWinStreak(List<Match> matches, String teamName) {
        int longest = 0, current = 0;
        // Process in chronological order for accurate streak calculation
        List<Match> chronological = new ArrayList<>(matches);
        Collections.reverse(chronological);

        for (Match m : chronological) {
            if (m.getPointsForTeam(teamName) == 3) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }
        return longest;
    }

    private int calcLongestUnbeatenStreak(List<Match> matches, String teamName) {
        int longest = 0, current = 0;
        List<Match> chronological = new ArrayList<>(matches);
        Collections.reverse(chronological);

        for (Match m : chronological) {
            if (m.getPointsForTeam(teamName) >= 1) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 0;
            }
        }
        return longest;
    }

    private double calcAvgShotsOnTarget(List<Match> matches, String teamName, int limit) {
        return matches.stream()
                .limit(limit)
                .filter(m -> m.getHomeShotsOnTarget() != null || m.getAwayShotsOnTarget() != null)
                .mapToInt(m -> {
                    boolean isHome = m.getHomeTeam().equalsIgnoreCase(teamName);
                    Integer shots = isHome ? m.getHomeShotsOnTarget() : m.getAwayShotsOnTarget();
                    return shots != null ? shots : 0;
                })
                .average()
                .orElse(0.0);
    }

    private double calcAvgCorners(List<Match> matches, String teamName, int limit) {
        return matches.stream()
                .limit(limit)
                .filter(m -> m.getHomeCorners() != null || m.getAwayCorners() != null)
                .mapToInt(m -> {
                    boolean isHome = m.getHomeTeam().equalsIgnoreCase(teamName);
                    Integer corners = isHome ? m.getHomeCorners() : m.getAwayCorners();
                    return corners != null ? corners : 0;
                })
                .average()
                .orElse(0.0);
    }

    private double calcShotConversionRate(List<Match> matches, String teamName, int limit) {
        int totalGoals = 0, totalShotsOnTarget = 0;

        for (Match m : matches.stream().limit(limit).toList()) {
            boolean isHome = m.getHomeTeam().equalsIgnoreCase(teamName);
            totalGoals += m.getGoalsScoredByTeam(teamName);

            Integer shots = isHome ? m.getHomeShotsOnTarget() : m.getAwayShotsOnTarget();
            if (shots != null) {
                totalShotsOnTarget += shots;
            }
        }

        return totalShotsOnTarget > 0 ? PredictionUtils.round((double) totalGoals / totalShotsOnTarget * 100) : 0;
    }

    /**
     * Get team form insights for the prediction view.
     * Returns statistics focused on recent form and scoring patterns.
     * Supports fuzzy team name matching for better UX.
     *
     * @param teamName The team name to get form insights for
     * @return TeamFormResponse with form insights
     */
    @Cacheable(value = "teamForm", key = "#teamName")
    public TeamFormResponse getTeamFormInsights(String teamName) {
        log.info("Calculating form insights for team: '{}'", teamName);

        LocalDate now = LocalDate.now();
        LocalDate beforeDate = now.plusDays(1);

        // Try to resolve the team name (handles case-insensitivity and fuzzy matching)
        String resolvedTeamName = resolveTeamName(teamName, beforeDate);

        List<Match> allMatches = matchRepository.findByTeamBeforeDate(resolvedTeamName, beforeDate);

        // Calculate last 5 matches statistics
        List<Match> last5Matches = allMatches.stream().limit(5).toList();

        // Goals averages
        double last5GoalsAvg = calcLast5GoalsAvg(last5Matches, resolvedTeamName);
        double last5ConcededAvg = calcLast5ConcededAvg(last5Matches, resolvedTeamName);

        // Clean sheet rate
        double cleanSheetRate = calcCleanSheetRate(last5Matches, resolvedTeamName);


        // Shot conversion
        double shotConversion = calcShotConversionRate(last5Matches, resolvedTeamName, 5) / 100.0;

        // Form trend based on comparing last 5 vs previous 5
        String formTrend = calcFormTrend(allMatches, resolvedTeamName);

        // Recent form string
        String recentForm = buildFormString(allMatches, resolvedTeamName, 5);

        // Goals timeline for sparkline
        List<Integer> goalsTimeline = last5Matches.stream()
                .map(m -> m.getGoalsScoredByTeam(resolvedTeamName))
                .toList();

        List<Integer> concededTimeline = last5Matches.stream()
                .map(m -> m.getGoalsConcededByTeam(resolvedTeamName))
                .toList();

        return TeamFormResponse.builder()
                .teamName(resolvedTeamName)
                .last5GoalsAvg(PredictionUtils.round(last5GoalsAvg))
                .last5ConcededAvg(PredictionUtils.round(last5ConcededAvg))
                .cleanSheetRate(PredictionUtils.round(cleanSheetRate))
                .shotConversion(PredictionUtils.round(shotConversion))
                .formTrend(formTrend)
                .recentForm(recentForm)
                .goalsTimeline(goalsTimeline)
                .concededTimeline(concededTimeline)
                .build();
    }

    private double calcLast5GoalsAvg(List<Match> matches, String teamName) {
        if (matches.isEmpty()) return 0.0;
        return matches.stream()
                .mapToInt(m -> m.getGoalsScoredByTeam(teamName))
                .average()
                .orElse(0.0);
    }

    private double calcLast5ConcededAvg(List<Match> matches, String teamName) {
        if (matches.isEmpty()) return 0.0;
        return matches.stream()
                .mapToInt(m -> m.getGoalsConcededByTeam(teamName))
                .average()
                .orElse(0.0);
    }

    private double calcCleanSheetRate(List<Match> matches, String teamName) {
        if (matches.isEmpty()) return 0.0;
        long cleanSheets = matches.stream()
                .filter(m -> m.getGoalsConcededByTeam(teamName) == 0)
                .count();
        return (double) cleanSheets / matches.size();
    }


    private String calcFormTrend(List<Match> allMatches, String teamName) {
        if (allMatches.size() < 10) return "stable";

        List<Match> last5 = allMatches.stream().limit(5).toList();
        List<Match> prev5 = allMatches.stream().skip(5).limit(5).toList();

        double last5Points = calcFormPoints(last5, teamName);
        double prev5Points = calcFormPoints(prev5, teamName);

        if (last5Points > prev5Points + 0.2) return "up";
        if (last5Points < prev5Points - 0.2) return "down";
        return "stable";
    }

    private double calcFormPoints(List<Match> matches, String teamName) {
        if (matches.isEmpty()) return 0.0;
        return matches.stream()
                .mapToInt(m -> m.getPointsForTeam(teamName))
                .average()
                .orElse(0.0);
    }
}

