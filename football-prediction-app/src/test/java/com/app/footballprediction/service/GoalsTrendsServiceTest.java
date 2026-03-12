package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.GoalsTrendsDTO;
import com.app.footballprediction.dto.SeasonGoalsStatsDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GoalsTrendsService.
 * Tests cover per-season calculation, trend direction, edge cases, and validation rules.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GoalsTrendsService Unit Tests")
class GoalsTrendsServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private GoalsTrendsService goalsTrendsService;

    // ── Test Helpers ──────────────────────────────────────────────────

    private Match buildMatch(String season, int homeGoals, int awayGoals) {
        return Match.builder()
                .season(season)
                .homeTeam("TeamA")
                .awayTeam("TeamB")
                .fullTimeHomeGoals(homeGoals)
                .fullTimeAwayGoals(awayGoals)
                .fullTimeResult(homeGoals > awayGoals ? "H" : homeGoals < awayGoals ? "A" : "D")
                .matchDate(LocalDate.of(2025, 1, 15))
                .build();
    }

    private List<Match> buildTypicalSeasonMatches(String season) {
        // Mix of results: 2-1, 0-0, 3-2, 1-0, 0-3 → totals: 3,0,5,1,3 = 12 goals in 5 matches
        return List.of(
                buildMatch(season, 2, 1),  // 3 goals
                buildMatch(season, 0, 0),  // 0 goals (clean sheet, low-scoring)
                buildMatch(season, 3, 2),  // 5 goals (high-scoring)
                buildMatch(season, 1, 0),  // 1 goal (clean sheet, low-scoring)
                buildMatch(season, 0, 3)   // 3 goals (clean sheet)
        );
    }

    // ── Per-Season Calculation Tests ─────────────────────────────────

    @Nested
    @DisplayName("Per-Season Statistics")
    class PerSeasonStatsTests {

        @Test
        @DisplayName("calculates correct average goals per game")
        void calculatesCorrectAvgGoals() {
            String season = "2023-24";
            List<Match> matches = buildTypicalSeasonMatches(season);
            when(matchRepository.findBySeasonOrderByMatchDateDesc(season)).thenReturn(matches);

            GoalsTrendsDTO result = goalsTrendsService.calculateGoalsTrends(List.of(season));

            assertThat(result.getSeasonStats()).hasSize(1);
            SeasonGoalsStatsDTO stats = result.getSeasonStats().get(0);
            assertThat(stats.getSeason()).isEqualTo(season);
            // 12 goals / 5 matches = 2.4
            assertThat(stats.getAvgGoalsPerGame()).isCloseTo(2.4, within(0.01));
            assertThat(stats.getTotalGoals()).isEqualTo(12);
            assertThat(stats.getTotalMatches()).isEqualTo(5);
        }

        @Test
        @DisplayName("calculates correct home and away averages")
        void calculatesHomeAwayAverages() {
            String season = "2023-24";
            List<Match> matches = buildTypicalSeasonMatches(season);
            when(matchRepository.findBySeasonOrderByMatchDateDesc(season)).thenReturn(matches);

            GoalsTrendsDTO result = goalsTrendsService.calculateGoalsTrends(List.of(season));

            SeasonGoalsStatsDTO stats = result.getSeasonStats().get(0);
            // Home goals: 2+0+3+1+0 = 6 / 5 = 1.2
            assertThat(stats.getHomeGoalsAvg()).isCloseTo(1.2, within(0.01));
            // Away goals: 1+0+2+0+3 = 6 / 5 = 1.2
            assertThat(stats.getAwayGoalsAvg()).isCloseTo(1.2, within(0.01));
        }

        @Test
        @DisplayName("calculates correct clean sheet percentage")
        void calculatesCleanSheetPercentage() {
            String season = "2023-24";
            List<Match> matches = buildTypicalSeasonMatches(season);
            when(matchRepository.findBySeasonOrderByMatchDateDesc(season)).thenReturn(matches);

            GoalsTrendsDTO result = goalsTrendsService.calculateGoalsTrends(List.of(season));

            SeasonGoalsStatsDTO stats = result.getSeasonStats().get(0);
            // Clean sheets: 0-0, 1-0, 0-3 → 3 out of 5 = 60%
            assertThat(stats.getCleanSheetPercentage()).isCloseTo(60.0, within(0.01));
        }

        @Test
        @DisplayName("calculates correct high-scoring and low-scoring percentages")
        void calculatesGoalDistributionPercentages() {
            String season = "2023-24";
            List<Match> matches = buildTypicalSeasonMatches(season);
            when(matchRepository.findBySeasonOrderByMatchDateDesc(season)).thenReturn(matches);

            GoalsTrendsDTO result = goalsTrendsService.calculateGoalsTrends(List.of(season));

            SeasonGoalsStatsDTO stats = result.getSeasonStats().get(0);
            // High-scoring (>4): 3-2=5 goals → 1 match → 20%
            assertThat(stats.getHighScoringPercentage()).isCloseTo(20.0, within(0.01));
            // Low-scoring (<2): 0-0=0, 1-0=1 → 2 matches → 40%
            assertThat(stats.getLowScoringPercentage()).isCloseTo(40.0, within(0.01));
            // Medium (2-3 goals): 2-1=3, 0-3=3 → 2 matches → 40%
            assertThat(stats.getMediumScoringPercentage()).isCloseTo(40.0, within(0.01));
        }
    }

    // ── Trend Direction Tests ────────────────────────────────────────

    @Nested
    @DisplayName("Trend Direction Calculation")
    class TrendDirectionTests {

        @Test
        @DisplayName("detects increasing goal trend")
        void detectsIncreasingTrend() {
            // Season 1: avg 2.0, Season 2: avg 2.5, Season 3: avg 3.0
            when(matchRepository.findBySeasonOrderByMatchDateDesc("2020-21"))
                    .thenReturn(List.of(buildMatch("2020-21", 1, 1)));
            when(matchRepository.findBySeasonOrderByMatchDateDesc("2021-22"))
                    .thenReturn(List.of(buildMatch("2021-22", 2, 1)));
            when(matchRepository.findBySeasonOrderByMatchDateDesc("2022-23"))
                    .thenReturn(List.of(buildMatch("2022-23", 2, 2)));

            GoalsTrendsDTO result = goalsTrendsService.calculateGoalsTrends(
                    List.of("2020-21", "2021-22", "2022-23"));

            assertThat(result.getTrendDirection()).isEqualTo("Increasing");
            assertThat(result.getAvgChange()).isGreaterThan(0);
        }

        @Test
        @DisplayName("detects decreasing goal trend")
        void detectsDecreasingTrend() {
            // Season 1: avg 4.0, Season 2: avg 3.0, Season 3: avg 2.0
            when(matchRepository.findBySeasonOrderByMatchDateDesc("2020-21"))
                    .thenReturn(List.of(buildMatch("2020-21", 2, 2)));
            when(matchRepository.findBySeasonOrderByMatchDateDesc("2021-22"))
                    .thenReturn(List.of(buildMatch("2021-22", 2, 1)));
            when(matchRepository.findBySeasonOrderByMatchDateDesc("2022-23"))
                    .thenReturn(List.of(buildMatch("2022-23", 1, 1)));

            GoalsTrendsDTO result = goalsTrendsService.calculateGoalsTrends(
                    List.of("2020-21", "2021-22", "2022-23"));

            assertThat(result.getTrendDirection()).isEqualTo("Decreasing");
            assertThat(result.getAvgChange()).isLessThan(0);
        }

        @Test
        @DisplayName("detects stable goal trend")
        void detectsStableTrend() {
            // All seasons ~same avg
            when(matchRepository.findBySeasonOrderByMatchDateDesc("2020-21"))
                    .thenReturn(List.of(buildMatch("2020-21", 1, 1)));
            when(matchRepository.findBySeasonOrderByMatchDateDesc("2021-22"))
                    .thenReturn(List.of(buildMatch("2021-22", 1, 1)));

            GoalsTrendsDTO result = goalsTrendsService.calculateGoalsTrends(
                    List.of("2020-21", "2021-22"));

            assertThat(result.getTrendDirection()).isEqualTo("Stable");
        }
    }

    // ── Edge Cases ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("returns empty response when no seasons provided and DB is empty")
        void returnsEmptyWhenNoData() {
            when(matchRepository.findAllSeasons()).thenReturn(Collections.emptyList());

            GoalsTrendsDTO result = goalsTrendsService.calculateGoalsTrends(null);

            assertThat(result.getSeasonStats()).isEmpty();
            assertThat(result.getTrendDirection()).isEqualTo("Stable");
            assertThat(result.getSeasonsAnalyzed()).isZero();
        }

        @Test
        @DisplayName("skips seasons with no completed matches")
        void skipsSeasonsWithNoCompletedMatches() {
            Match incompleteMatch = Match.builder()
                    .season("2022-23")
                    .homeTeam("TeamA")
                    .awayTeam("TeamB")
                    .fullTimeResult(null)
                    .matchDate(LocalDate.of(2023, 4, 1))
                    .build();

            when(matchRepository.findBySeasonOrderByMatchDateDesc("2022-23"))
                    .thenReturn(List.of(incompleteMatch));
            when(matchRepository.findBySeasonOrderByMatchDateDesc("2023-24"))
                    .thenReturn(buildTypicalSeasonMatches("2023-24"));

            GoalsTrendsDTO result = goalsTrendsService.calculateGoalsTrends(
                    List.of("2022-23", "2023-24"));

            assertThat(result.getSeasonStats()).hasSize(1);
            assertThat(result.getSeasonStats().get(0).getSeason()).isEqualTo("2023-24");
        }

        @Test
        @DisplayName("falls back to last 6 DB seasons when no seasons specified")
        void fallsBackToDbSeasons() {
            List<String> dbSeasons = List.of("2024-25", "2023-24", "2022-23",
                    "2021-22", "2020-21", "2019-20", "2018-19");
            when(matchRepository.findAllSeasons()).thenReturn(dbSeasons);

            // Expect queries for first 6 seasons only (limit 6)
            for (String s : dbSeasons.subList(0, 6)) {
                when(matchRepository.findBySeasonOrderByMatchDateDesc(s))
                        .thenReturn(List.of(buildMatch(s, 1, 1)));
            }

            GoalsTrendsDTO result = goalsTrendsService.calculateGoalsTrends(null);

            assertThat(result.getSeasonsAnalyzed()).isEqualTo(6);
            verify(matchRepository, never()).findBySeasonOrderByMatchDateDesc("2018-19");
        }

        @Test
        @DisplayName("single season returns Stable trend with zero avgChange")
        void singleSeasonIsStable() {
            String season = "2024-25";
            when(matchRepository.findBySeasonOrderByMatchDateDesc(season))
                    .thenReturn(List.of(buildMatch(season, 2, 1)));

            GoalsTrendsDTO result = goalsTrendsService.calculateGoalsTrends(List.of(season));

            assertThat(result.getTrendDirection()).isEqualTo("Stable");
            assertThat(result.getAvgChange()).isEqualTo(0.0);
            assertThat(result.getSeasonsAnalyzed()).isEqualTo(1);
        }
    }

    // ── Validation: Modern PL Expectations ───────────────────────────

    @Nested
    @DisplayName("Modern PL Validation")
    class ValidationTests {

        @Test
        @DisplayName("home goals average is typically higher than away goals average")
        void homeGoalsHigherThanAway() {
            String season = "2023-24";
            // Simulate typical PL match distribution: home advantage
            List<Match> matches = List.of(
                    buildMatch(season, 2, 1),
                    buildMatch(season, 3, 0),
                    buildMatch(season, 1, 1),
                    buildMatch(season, 2, 2),
                    buildMatch(season, 1, 0)
            );
            when(matchRepository.findBySeasonOrderByMatchDateDesc(season)).thenReturn(matches);

            GoalsTrendsDTO result = goalsTrendsService.calculateGoalsTrends(List.of(season));

            SeasonGoalsStatsDTO stats = result.getSeasonStats().get(0);
            // Home: 2+3+1+2+1 = 9/5 = 1.8, Away: 1+0+1+2+0 = 4/5 = 0.8
            assertThat(stats.getHomeGoalsAvg()).isGreaterThan(stats.getAwayGoalsAvg());
        }

        @Test
        @DisplayName("season stats are sorted chronologically")
        void seasonStatsSortedChronologically() {
            List<String> seasons = List.of("2022-23", "2020-21", "2021-22");
            for (String s : seasons) {
                when(matchRepository.findBySeasonOrderByMatchDateDesc(s))
                        .thenReturn(List.of(buildMatch(s, 1, 1)));
            }

            GoalsTrendsDTO result = goalsTrendsService.calculateGoalsTrends(seasons);

            List<String> resultSeasons = result.getSeasonStats().stream()
                    .map(SeasonGoalsStatsDTO::getSeason)
                    .toList();
            assertThat(resultSeasons).containsExactly("2020-21", "2021-22", "2022-23");
        }
    }
}

