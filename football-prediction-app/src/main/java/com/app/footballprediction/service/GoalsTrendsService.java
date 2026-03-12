package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.common.util.PredictionUtils;
import com.app.footballprediction.dto.GoalsTrendsDTO;
import com.app.footballprediction.dto.SeasonGoalsStatsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating league-wide goals trends across multiple seasons.
 * Tracks how goal-scoring patterns change over time, including averages,
 * clean sheets, high/low-scoring game percentages, and overall trend direction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoalsTrendsService {

    private final MatchRepository matchRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Threshold above which the per-season avg-change is considered "Increasing". */
    private static final double TREND_THRESHOLD = 0.03;

    /**
     * Calculate goals trends for the given seasons.
     *
     * @param seasons list of season identifiers (e.g., ["2020-21", "2021-22", ...]).
     *                If null or empty, the last 6 seasons in the database are used.
     * @return GoalsTrendsDTO containing per-season stats and overall trend
     */
    @Cacheable(value = "goalsTrends", key = "#seasons != null ? #seasons.toString() : 'default'")
    public GoalsTrendsDTO calculateGoalsTrends(List<String> seasons) {
        log.info("Calculating goals trends for seasons: {}", seasons);

        List<String> effectiveSeasons = resolveSeasons(seasons);
        if (effectiveSeasons.isEmpty()) {
            log.warn("No seasons available for goals trends analysis");
            return buildEmptyResponse();
        }

        List<SeasonGoalsStatsDTO> seasonStats = new ArrayList<>();
        for (String season : effectiveSeasons) {
            List<Match> matches = matchRepository.findBySeasonOrderByMatchDateDesc(season);
            // Only consider completed matches with goal data
            List<Match> completedMatches = matches.stream()
                    .filter(m -> m.getFullTimeResult() != null
                            && m.getFullTimeHomeGoals() != null
                            && m.getFullTimeAwayGoals() != null)
                    .collect(Collectors.toList());

            if (completedMatches.isEmpty()) {
                log.debug("No completed matches for season {}, skipping", season);
                continue;
            }

            seasonStats.add(buildSeasonStats(season, completedMatches));
        }

        if (seasonStats.isEmpty()) {
            return buildEmptyResponse();
        }

        // Sort chronologically by season
        seasonStats.sort(Comparator.comparing(SeasonGoalsStatsDTO::getSeason));

        // Calculate trend direction and average change
        double avgChange = calculateAverageChange(seasonStats);
        String trendDirection = determineTrendDirection(avgChange);

        log.info("Goals trends: {} seasons analyzed, trend={}, avgChange={}",
                seasonStats.size(), trendDirection, avgChange);

        return GoalsTrendsDTO.builder()
                .seasonStats(seasonStats)
                .trendDirection(trendDirection)
                .avgChange(PredictionUtils.round(avgChange))
                .seasonsAnalyzed(seasonStats.size())
                .generatedAt(LocalDate.now().format(DATE_FORMATTER))
                .build();
    }

    // ── Private helpers ────────────────────────────────────────────────

    /**
     * Resolve effective seasons: use provided list, or fall back to last 6 DB seasons.
     */
    private List<String> resolveSeasons(List<String> seasons) {
        if (seasons != null && !seasons.isEmpty()) {
            return seasons.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .sorted()
                    .collect(Collectors.toList());
        }
        // Fall back to last 6 seasons
        List<String> allSeasons = matchRepository.findAllSeasons();
        return allSeasons.stream()
                .limit(6)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Build per-season goals statistics from completed matches.
     */
    private SeasonGoalsStatsDTO buildSeasonStats(String season, List<Match> matches) {
        int totalMatches = matches.size();
        int totalGoals = 0;
        int homeGoals = 0;
        int awayGoals = 0;
        int cleanSheets = 0;
        int highScoring = 0;   // > 4 goals
        int lowScoring = 0;    // < 2 goals (i.e. 0 or 1)
        int mediumScoring = 0; // 2 or 3 goals

        for (Match m : matches) {
            int hg = m.getFullTimeHomeGoals();
            int ag = m.getFullTimeAwayGoals();
            int total = hg + ag;

            totalGoals += total;
            homeGoals += hg;
            awayGoals += ag;

            // Clean sheet: at least one side scored 0
            if (hg == 0 || ag == 0) {
                cleanSheets++;
            }

            if (total > 4) {
                highScoring++;
            } else if (total < 2) {
                lowScoring++;
            } else {
                mediumScoring++;
            }
        }

        return SeasonGoalsStatsDTO.builder()
                .season(season)
                .totalGoals(totalGoals)
                .totalMatches(totalMatches)
                .avgGoalsPerGame(PredictionUtils.round((double) totalGoals / totalMatches))
                .homeGoalsAvg(PredictionUtils.round((double) homeGoals / totalMatches))
                .awayGoalsAvg(PredictionUtils.round((double) awayGoals / totalMatches))
                .cleanSheetPercentage(PredictionUtils.round((double) cleanSheets / totalMatches * 100))
                .highScoringPercentage(PredictionUtils.round((double) highScoring / totalMatches * 100))
                .lowScoringPercentage(PredictionUtils.round((double) lowScoring / totalMatches * 100))
                .mediumScoringPercentage(PredictionUtils.round((double) mediumScoring / totalMatches * 100))
                .build();
    }

    /**
     * Calculate the average per-season change in goals per game.
     */
    private double calculateAverageChange(List<SeasonGoalsStatsDTO> stats) {
        if (stats.size() < 2) {
            return 0.0;
        }
        double totalChange = 0;
        for (int i = 1; i < stats.size(); i++) {
            totalChange += stats.get(i).getAvgGoalsPerGame() - stats.get(i - 1).getAvgGoalsPerGame();
        }
        return totalChange / (stats.size() - 1);
    }

    /**
     * Determine trend direction from average change.
     */
    private String determineTrendDirection(double avgChange) {
        if (avgChange > TREND_THRESHOLD) {
            return "Increasing";
        } else if (avgChange < -TREND_THRESHOLD) {
            return "Decreasing";
        }
        return "Stable";
    }

    private GoalsTrendsDTO buildEmptyResponse() {
        return GoalsTrendsDTO.builder()
                .seasonStats(Collections.emptyList())
                .trendDirection("Stable")
                .avgChange(0.0)
                .seasonsAnalyzed(0)
                .generatedAt(LocalDate.now().format(DATE_FORMATTER))
                .build();
    }
}

