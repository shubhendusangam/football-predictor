package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.common.util.PredictionUtils;
import com.app.footballprediction.dto.LeagueStatsResponse;
import com.app.footballprediction.dto.LeagueStatsResponse.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating comprehensive league-wide statistics.
 * Provides Season Overview, Goals Trends, Home Advantage Analysis, and Record Matches.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeagueStatsService {

    private final MatchRepository matchRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int SEASONS_TO_ANALYZE = 21;

    /**
     * Get comprehensive league statistics.
     */
    @Cacheable(value = "leagueStats", key = "'overall'")
    public LeagueStatsResponse getLeagueStats() {
        log.info("Calculating league statistics...");

        List<Match> allMatches = matchRepository.findAllByOrderByMatchDateDesc();

        if (allMatches.isEmpty()) {
            log.warn("No matches found in database");
            return buildEmptyResponse();
        }

        log.info("Analyzing {} total matches", allMatches.size());

        return LeagueStatsResponse.builder()
                .seasonOverview(calculateSeasonOverview(allMatches))
                .goalsTrends(calculateGoalsTrends(allMatches))
                .homeAdvantage(calculateHomeAdvantage(allMatches))
                .recordMatches(calculateRecordMatches(allMatches))
                .commonScorelines(calculateCommonScorelines(allMatches))
                .halfTimeStats(calculateHalfTimeStats(allMatches))
                .refereeStats(calculateRefereeStats(allMatches))
                .totalMatchesAnalyzed(allMatches.size())
                .generatedAt(LocalDate.now().format(DATE_FORMATTER))
                .build();
    }

    /**
     * Get current season statistics only.
     */
    @Cacheable(value = "leagueStats", key = "'currentSeason'")
    public LeagueStatsResponse getCurrentSeasonStats() {
        log.info("Calculating current season statistics...");

        LocalDate seasonStart = getSeasonStartDate();
        List<Match> seasonMatches = matchRepository.findAllByOrderByMatchDateDesc().stream()
                .filter(m -> m.getMatchDate() != null && m.getMatchDate().isAfter(seasonStart))
                .collect(Collectors.toList());

        if (seasonMatches.isEmpty()) {
            log.warn("No matches found for current season");
            return buildEmptyResponse();
        }

        log.info("Analyzing {} matches for current season", seasonMatches.size());

        return LeagueStatsResponse.builder()
                .seasonOverview(calculateSeasonOverview(seasonMatches))
                .goalsTrends(calculateGoalsTrends(seasonMatches))
                .homeAdvantage(calculateHomeAdvantage(seasonMatches))
                .recordMatches(calculateRecordMatches(seasonMatches))
                .commonScorelines(calculateCommonScorelines(seasonMatches))
                .halfTimeStats(calculateHalfTimeStats(seasonMatches))
                .refereeStats(calculateRefereeStats(seasonMatches))
                .totalMatchesAnalyzed(seasonMatches.size())
                .generatedAt(LocalDate.now().format(DATE_FORMATTER))
                .build();
    }

    /**
     * Calculate Season Overview: goals, averages, win percentages.
     */
    private SeasonOverview calculateSeasonOverview(List<Match> matches) {
        int totalMatches = matches.size();
        int totalGoals = 0;
        int homeGoals = 0;
        int awayGoals = 0;
        int homeWins = 0;
        int draws = 0;
        int awayWins = 0;

        for (Match m : matches) {
            int hg = m.getFullTimeHomeGoals() != null ? m.getFullTimeHomeGoals() : 0;
            int ag = m.getFullTimeAwayGoals() != null ? m.getFullTimeAwayGoals() : 0;

            totalGoals += hg + ag;
            homeGoals += hg;
            awayGoals += ag;

            if (hg > ag) homeWins++;
            else if (hg == ag) draws++;
            else awayWins++;
        }

        return SeasonOverview.builder()
                .totalMatches(totalMatches)
                .totalGoals(totalGoals)
                .homeGoals(homeGoals)
                .awayGoals(awayGoals)
                .avgGoalsPerMatch(totalMatches > 0 ? PredictionUtils.round((double) totalGoals / totalMatches) : 0)
                .avgHomeGoals(totalMatches > 0 ? PredictionUtils.round((double) homeGoals / totalMatches) : 0)
                .avgAwayGoals(totalMatches > 0 ? PredictionUtils.round((double) awayGoals / totalMatches) : 0)
                .homeWinPercentage(totalMatches > 0 ? PredictionUtils.round((double) homeWins / totalMatches * 100) : 0)
                .drawPercentage(totalMatches > 0 ? PredictionUtils.round((double) draws / totalMatches * 100) : 0)
                .awayWinPercentage(totalMatches > 0 ? PredictionUtils.round((double) awayWins / totalMatches * 100) : 0)
                .cleanSheetPercentage(calculateCleanSheetPercentage(matches))
                .build();
    }

    /**
     * Calculate Goals Trends by season.
     */
    private List<GoalsTrend> calculateGoalsTrends(List<Match> matches) {
        // Group matches by season
        Map<String, List<Match>> matchesBySeason = matches.stream()
                .filter(m -> m.getSeason() != null)
                .collect(Collectors.groupingBy(Match::getSeason));

        List<GoalsTrend> trends = new ArrayList<>();

        for (Map.Entry<String, List<Match>> entry : matchesBySeason.entrySet()) {
            String season = entry.getKey();
            List<Match> seasonMatches = entry.getValue();

            int totalGoals = seasonMatches.stream()
                    .mapToInt(m -> (m.getFullTimeHomeGoals() != null ? m.getFullTimeHomeGoals() : 0) +
                            (m.getFullTimeAwayGoals() != null ? m.getFullTimeAwayGoals() : 0))
                    .sum();

            int homeGoals = seasonMatches.stream()
                    .mapToInt(m -> m.getFullTimeHomeGoals() != null ? m.getFullTimeHomeGoals() : 0)
                    .sum();

            int awayGoals = seasonMatches.stream()
                    .mapToInt(m -> m.getFullTimeAwayGoals() != null ? m.getFullTimeAwayGoals() : 0)
                    .sum();

            int matchCount = seasonMatches.size();

            trends.add(GoalsTrend.builder()
                    .season(season)
                    .totalGoals(totalGoals)
                    .avgGoalsPerMatch(matchCount > 0 ? PredictionUtils.round((double) totalGoals / matchCount) : 0)
                    .homeGoals(homeGoals)
                    .awayGoals(awayGoals)
                    .matchesPlayed(matchCount)
                    .homeWinRate(calculateHomeWinRate(seasonMatches))
                    .build());
        }

        // Sort by season
        trends.sort(Comparator.comparing(GoalsTrend::getSeason));

        return trends;
    }

    /**
     * Calculate Home Advantage trends over seasons.
     */
    private HomeAdvantageStats calculateHomeAdvantage(List<Match> matches) {
        // Overall home advantage
        long homeWins = matches.stream()
                .filter(m -> {
                    int hg = m.getFullTimeHomeGoals() != null ? m.getFullTimeHomeGoals() : 0;
                    int ag = m.getFullTimeAwayGoals() != null ? m.getFullTimeAwayGoals() : 0;
                    return hg > ag;
                })
                .count();

        long awayWins = matches.stream()
                .filter(m -> {
                    int hg = m.getFullTimeHomeGoals() != null ? m.getFullTimeHomeGoals() : 0;
                    int ag = m.getFullTimeAwayGoals() != null ? m.getFullTimeAwayGoals() : 0;
                    return ag > hg;
                })
                .count();

        int totalMatches = matches.size();

        // Calculate by season
        Map<String, List<Match>> matchesBySeason = matches.stream()
                .filter(m -> m.getSeason() != null)
                .collect(Collectors.groupingBy(Match::getSeason));

        List<SeasonHomeAdvantage> seasonTrends = new ArrayList<>();

        for (Map.Entry<String, List<Match>> entry : matchesBySeason.entrySet()) {
            List<Match> seasonMatches = entry.getValue();
            double homeWinRate = calculateHomeWinRate(seasonMatches);

            seasonTrends.add(SeasonHomeAdvantage.builder()
                    .season(entry.getKey())
                    .homeWinRate(homeWinRate)
                    .awayWinRate(calculateAwayWinRate(seasonMatches))
                    .homeGoalsAvg(calculateAvgHomeGoals(seasonMatches))
                    .awayGoalsAvg(calculateAvgAwayGoals(seasonMatches))
                    .build());
        }

        seasonTrends.sort(Comparator.comparing(SeasonHomeAdvantage::getSeason));

        return HomeAdvantageStats.builder()
                .overallHomeWinRate(totalMatches > 0 ? PredictionUtils.round((double) homeWins / totalMatches * 100) : 0)
                .overallAwayWinRate(totalMatches > 0 ? PredictionUtils.round((double) awayWins / totalMatches * 100) : 0)
                .homeWinCount((int) homeWins)
                .awayWinCount((int) awayWins)
                .seasonTrends(seasonTrends)
                .build();
    }

    /**
     * Calculate Record Matches: biggest wins, highest scoring, etc.
     */
    private RecordMatches calculateRecordMatches(List<Match> matches) {
        // Biggest wins (highest goal difference)
        List<RecordMatch> biggestWins = matches.stream()
                .filter(m -> m.getFullTimeHomeGoals() != null && m.getFullTimeAwayGoals() != null)
                .sorted((a, b) -> {
                    int diffA = Math.abs(a.getFullTimeHomeGoals() - a.getFullTimeAwayGoals());
                    int diffB = Math.abs(b.getFullTimeHomeGoals() - b.getFullTimeAwayGoals());
                    return Integer.compare(diffB, diffA);
                })
                .limit(5)
                .map(this::toRecordMatch)
                .collect(Collectors.toList());

        // Highest scoring games
        List<RecordMatch> highestScoring = matches.stream()
                .filter(m -> m.getFullTimeHomeGoals() != null && m.getFullTimeAwayGoals() != null)
                .sorted((a, b) -> {
                    int totalA = a.getFullTimeHomeGoals() + a.getFullTimeAwayGoals();
                    int totalB = b.getFullTimeHomeGoals() + b.getFullTimeAwayGoals();
                    return Integer.compare(totalB, totalA);
                })
                .limit(5)
                .map(this::toRecordMatch)
                .collect(Collectors.toList());

        return RecordMatches.builder()
                .biggestWins(biggestWins)
                .highestScoringGames(highestScoring)
                .build();
    }

    /**
     * Calculate common scorelines.
     */
    private List<ScorelineStats> calculateCommonScorelines(List<Match> matches) {
        Map<String, Integer> scorelineCounts = new HashMap<>();

        for (Match m : matches) {
            if (m.getFullTimeHomeGoals() == null || m.getFullTimeAwayGoals() == null) continue;

            String scoreline = m.getFullTimeHomeGoals() + "-" + m.getFullTimeAwayGoals();
            scorelineCounts.merge(scoreline, 1, Integer::sum);
        }

        int totalMatches = matches.size();

        return scorelineCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(e -> ScorelineStats.builder()
                        .scoreline(e.getKey())
                        .count(e.getValue())
                        .percentage(totalMatches > 0 ? PredictionUtils.round((double) e.getValue() / totalMatches * 100) : 0)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Calculate half-time statistics (First Half vs Second Half goals).
     */
    private HalfTimeStats calculateHalfTimeStats(List<Match> matches) {
        int firstHalfGoals = 0;
        int secondHalfGoals = 0;
        int matchesWithHtData = 0;

        for (Match m : matches) {
            if (m.getHalfTimeHomeGoals() == null || m.getHalfTimeAwayGoals() == null) continue;
            if (m.getFullTimeHomeGoals() == null || m.getFullTimeAwayGoals() == null) continue;

            matchesWithHtData++;

            int htGoals = m.getHalfTimeHomeGoals() + m.getHalfTimeAwayGoals();
            int ftGoals = m.getFullTimeHomeGoals() + m.getFullTimeAwayGoals();

            firstHalfGoals += htGoals;
            secondHalfGoals += ftGoals - htGoals;
        }

        return HalfTimeStats.builder()
                .totalFirstHalfGoals(firstHalfGoals)
                .totalSecondHalfGoals(secondHalfGoals)
                .avgFirstHalfGoals(matchesWithHtData > 0 ? PredictionUtils.round((double) firstHalfGoals / matchesWithHtData) : 0)
                .avgSecondHalfGoals(matchesWithHtData > 0 ? PredictionUtils.round((double) secondHalfGoals / matchesWithHtData) : 0)
                .firstHalfPercentage(firstHalfGoals + secondHalfGoals > 0 ?
                        PredictionUtils.round((double) firstHalfGoals / (firstHalfGoals + secondHalfGoals) * 100) : 0)
                .secondHalfPercentage(firstHalfGoals + secondHalfGoals > 0 ?
                        PredictionUtils.round((double) secondHalfGoals / (firstHalfGoals + secondHalfGoals) * 100) : 0)
                .matchesAnalyzed(matchesWithHtData)
                .build();
    }

    /**
     * Calculate referee statistics.
     */
    private List<RefereeStats> calculateRefereeStats(List<Match> matches) {
        Map<String, List<Match>> matchesByReferee = matches.stream()
                .filter(m -> m.getReferee() != null && !m.getReferee().isEmpty())
                .collect(Collectors.groupingBy(Match::getReferee));

        return matchesByReferee.entrySet().stream()
                .filter(e -> e.getValue().size() >= 5) // Only referees with 5+ matches
                .map(e -> {
                    String referee = e.getKey();
                    List<Match> refMatches = e.getValue();

                    int totalCards = refMatches.stream()
                            .mapToInt(m -> {
                                int hy = m.getHomeYellowCards() != null ? m.getHomeYellowCards() : 0;
                                int ay = m.getAwayYellowCards() != null ? m.getAwayYellowCards() : 0;
                                int hr = m.getHomeRedCards() != null ? m.getHomeRedCards() : 0;
                                int ar = m.getAwayRedCards() != null ? m.getAwayRedCards() : 0;
                                return hy + ay + hr + ar;
                            })
                            .sum();

                    int totalReds = refMatches.stream()
                            .mapToInt(m -> {
                                int hr = m.getHomeRedCards() != null ? m.getHomeRedCards() : 0;
                                int ar = m.getAwayRedCards() != null ? m.getAwayRedCards() : 0;
                                return hr + ar;
                            })
                            .sum();

                    double homeWinRate = calculateHomeWinRate(refMatches);

                    return RefereeStats.builder()
                            .refereeName(referee)
                            .matchesOfficiatedAsReferee(refMatches.size())
                            .totalCards(totalCards)
                            .avgCardsPerMatch(PredictionUtils.round((double) totalCards / refMatches.size()))
                            .totalRedCards(totalReds)
                            .homeWinRate(homeWinRate)
                            .build();
                })
                .sorted(Comparator.comparingDouble(RefereeStats::getAvgCardsPerMatch).reversed())
                .limit(10)
                .collect(Collectors.toList());
    }

    // Helper methods

    private double calculateCleanSheetPercentage(List<Match> matches) {
        long cleanSheets = matches.stream()
                .filter(m -> {
                    int hg = m.getFullTimeHomeGoals() != null ? m.getFullTimeHomeGoals() : 0;
                    int ag = m.getFullTimeAwayGoals() != null ? m.getFullTimeAwayGoals() : 0;
                    return hg == 0 || ag == 0;
                })
                .count();
        return matches.size() > 0 ? PredictionUtils.round((double) cleanSheets / matches.size() * 100) : 0;
    }

    private double calculateHomeWinRate(List<Match> matches) {
        long homeWins = matches.stream()
                .filter(m -> {
                    int hg = m.getFullTimeHomeGoals() != null ? m.getFullTimeHomeGoals() : 0;
                    int ag = m.getFullTimeAwayGoals() != null ? m.getFullTimeAwayGoals() : 0;
                    return hg > ag;
                })
                .count();
        return matches.size() > 0 ? PredictionUtils.round((double) homeWins / matches.size() * 100) : 0;
    }

    private double calculateAwayWinRate(List<Match> matches) {
        long awayWins = matches.stream()
                .filter(m -> {
                    int hg = m.getFullTimeHomeGoals() != null ? m.getFullTimeHomeGoals() : 0;
                    int ag = m.getFullTimeAwayGoals() != null ? m.getFullTimeAwayGoals() : 0;
                    return ag > hg;
                })
                .count();
        return matches.size() > 0 ? PredictionUtils.round((double) awayWins / matches.size() * 100) : 0;
    }

    private double calculateAvgHomeGoals(List<Match> matches) {
        int total = matches.stream()
                .mapToInt(m -> m.getFullTimeHomeGoals() != null ? m.getFullTimeHomeGoals() : 0)
                .sum();
        return matches.size() > 0 ? PredictionUtils.round((double) total / matches.size()) : 0;
    }

    private double calculateAvgAwayGoals(List<Match> matches) {
        int total = matches.stream()
                .mapToInt(m -> m.getFullTimeAwayGoals() != null ? m.getFullTimeAwayGoals() : 0)
                .sum();
        return matches.size() > 0 ? PredictionUtils.round((double) total / matches.size()) : 0;
    }

    private RecordMatch toRecordMatch(Match m) {
        return RecordMatch.builder()
                .homeTeam(m.getHomeTeam())
                .awayTeam(m.getAwayTeam())
                .homeGoals(m.getFullTimeHomeGoals())
                .awayGoals(m.getFullTimeAwayGoals())
                .date(m.getMatchDate() != null ? m.getMatchDate().format(DATE_FORMATTER) : "N/A")
                .season(m.getSeason())
                .totalGoals(m.getFullTimeHomeGoals() + m.getFullTimeAwayGoals())
                .goalDifference(Math.abs(m.getFullTimeHomeGoals() - m.getFullTimeAwayGoals()))
                .build();
    }

    private LocalDate getSeasonStartDate() {
        // Premier League typically starts in August
        LocalDate now = LocalDate.now();
        int year = now.getMonthValue() >= 8 ? now.getYear() : now.getYear() - 1;
        return LocalDate.of(year, 8, 1);
    }

    private LeagueStatsResponse buildEmptyResponse() {
        return LeagueStatsResponse.builder()
                .seasonOverview(SeasonOverview.builder().build())
                .goalsTrends(Collections.emptyList())
                .homeAdvantage(HomeAdvantageStats.builder().seasonTrends(Collections.emptyList()).build())
                .recordMatches(RecordMatches.builder()
                        .biggestWins(Collections.emptyList())
                        .highestScoringGames(Collections.emptyList())
                        .build())
                .commonScorelines(Collections.emptyList())
                .halfTimeStats(HalfTimeStats.builder().build())
                .refereeStats(Collections.emptyList())
                .totalMatchesAnalyzed(0)
                .generatedAt(LocalDate.now().format(DATE_FORMATTER))
                .build();
    }
}

