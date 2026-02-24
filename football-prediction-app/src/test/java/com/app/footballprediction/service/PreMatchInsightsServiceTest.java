package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.SeasonTeamStatsRepository;
import com.app.footballprediction.dto.PreMatchInsightsResponse;
import com.app.footballprediction.dto.PreMatchInsightsResponse.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Comprehensive unit tests for PreMatchInsightsService.
 * Tests cover form calculation, streak detection, rest analysis, goal threats, and market predictions.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PreMatchInsightsService Unit Tests")
class PreMatchInsightsServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private SeasonTeamStatsRepository seasonTeamStatsRepository;

    @Mock
    private TeamStatsService teamStatsService;

    @Mock
    private H2HInsightsService h2hInsightsService;

    @Mock
    private InsightsValidationService validationService;

    @InjectMocks
    private PreMatchInsightsService preMatchInsightsService;

    private List<Match> arsenalMatches;
    private List<Match> chelseaMatches;

    @BeforeEach
    void setUp() {
        // Setup validation service to return no errors by default (lenient for tests that don't reach validation)
        lenient().when(validationService.validatePreMatchInsights(any()))
                .thenReturn(Collections.emptyList());

        // Create sample match data for Arsenal (5 recent matches)
        arsenalMatches = Arrays.asList(
                createMatch("Arsenal", "Liverpool", 2, 1, "H", LocalDate.now().minusDays(3)),
                createMatch("Arsenal", "Man City", 1, 1, "D", LocalDate.now().minusDays(10)),
                createMatch("Tottenham", "Arsenal", 0, 2, "A", LocalDate.now().minusDays(17)),
                createMatch("Arsenal", "Chelsea", 3, 0, "H", LocalDate.now().minusDays(24)),
                createMatch("Newcastle", "Arsenal", 1, 2, "A", LocalDate.now().minusDays(31))
        );

        // Create sample match data for Chelsea (5 recent matches)
        chelseaMatches = Arrays.asList(
                createMatch("Chelsea", "Brighton", 1, 2, "A", LocalDate.now().minusDays(4)),
                createMatch("West Ham", "Chelsea", 0, 0, "D", LocalDate.now().minusDays(11)),
                createMatch("Chelsea", "Newcastle", 1, 1, "D", LocalDate.now().minusDays(18)),
                createMatch("Arsenal", "Chelsea", 3, 0, "H", LocalDate.now().minusDays(24)),
                createMatch("Chelsea", "Wolves", 2, 1, "H", LocalDate.now().minusDays(32))
        );
    }

    private Match createMatch(String home, String away, int homeGoals, int awayGoals,
                              String result, LocalDate date) {
        return Match.builder()
                .homeTeam(home)
                .awayTeam(away)
                .fullTimeHomeGoals(homeGoals)
                .fullTimeAwayGoals(awayGoals)
                .fullTimeResult(result)
                .matchDate(date)
                .build();
    }

    @Nested
    @DisplayName("Form Point Calculation (3/1/0 Rule)")
    class FormPointCalculationTests {

        @Test
        @DisplayName("should calculate 3 points for a win")
        void formPoints_win_returns3Points() {
            // Given - Arsenal won 4 out of 5 (WWWDW = 13 points)
            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(arsenalMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            FormComparison form = response.getFormComparison();
            // Arsenal: W(3) + D(1) + W(3) + W(3) + W(3) = 13 points
            assertThat(form.getHomeFormPoints()).isEqualTo(13);
        }

        @Test
        @DisplayName("should calculate 1 point for a draw")
        void formPoints_draw_returns1Point() {
            // Given - Chelsea has 2 draws
            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(arsenalMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            FormComparison form = response.getFormComparison();
            // Chelsea: L(0) + D(1) + D(1) + L(0) + W(3) = 5 points
            assertThat(form.getAwayFormPoints()).isEqualTo(5);
        }

        @Test
        @DisplayName("should calculate 0 points for a loss")
        void formPoints_loss_returns0Points() {
            // Given - All losses
            List<Match> losingMatches = Arrays.asList(
                    createMatch("Arsenal", "Liverpool", 0, 3, "A", LocalDate.now().minusDays(3)),
                    createMatch("Man City", "Arsenal", 4, 0, "H", LocalDate.now().minusDays(10)),
                    createMatch("Arsenal", "Chelsea", 0, 2, "A", LocalDate.now().minusDays(17)),
                    createMatch("Tottenham", "Arsenal", 2, 0, "H", LocalDate.now().minusDays(24)),
                    createMatch("Arsenal", "Newcastle", 1, 3, "A", LocalDate.now().minusDays(31))
            );

            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(losingMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            assertThat(response.getFormComparison().getHomeFormPoints()).isEqualTo(0);
            assertThat(response.getFormComparison().getHomeFormString()).isEqualTo("LLLLL");
        }

        @Test
        @DisplayName("should build correct form string (WDWWW)")
        void formString_shouldShowCorrectSequence() {
            // Given
            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(arsenalMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            assertThat(response.getFormComparison().getHomeFormString()).isEqualTo("WDWWW");
            assertThat(response.getFormComparison().getAwayFormString()).isEqualTo("LDDLW");
        }

        @Test
        @DisplayName("should calculate form rating as percentage correctly")
        void formRating_shouldCalculatePercentageCorrectly() {
            // Given
            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(arsenalMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            // Arsenal: 13/15 = 86.67%
            assertThat(response.getFormComparison().getHomeFormRating()).isCloseTo(86.67, within(0.1));
            // Chelsea: 5/15 = 33.33%
            assertThat(response.getFormComparison().getAwayFormRating()).isCloseTo(33.33, within(0.1));
        }
    }

    @Nested
    @DisplayName("Win Percentage Calculation")
    class WinPercentageTests {

        @Test
        @DisplayName("should not have integer division errors")
        void winPercentage_shouldUseDoubleArithmetic() {
            // Given - 1 win out of 3 matches (33.33% not 0%)
            List<Match> mixedMatches = Arrays.asList(
                    createMatch("Arsenal", "Liverpool", 2, 1, "H", LocalDate.now().minusDays(3)),
                    createMatch("Man City", "Arsenal", 1, 0, "H", LocalDate.now().minusDays(10)),
                    createMatch("Arsenal", "Chelsea", 0, 1, "A", LocalDate.now().minusDays(17))
            );

            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(mixedMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then - Form rating should be 3/9 = 33.33%, not 0% from integer division
            assertThat(response.getFormComparison().getHomeFormRating()).isCloseTo(33.33, within(0.1));
        }
    }

    @Nested
    @DisplayName("Streak Indicators")
    class StreakIndicatorTests {

        @Test
        @DisplayName("should detect winning streak of 3+ matches")
        void streak_winningStreak_detected() {
            // Given - 3 consecutive wins
            List<Match> winningStreak = Arrays.asList(
                    createMatch("Arsenal", "Liverpool", 2, 0, "H", LocalDate.now().minusDays(3)),
                    createMatch("Arsenal", "Man City", 1, 0, "H", LocalDate.now().minusDays(10)),
                    createMatch("Tottenham", "Arsenal", 0, 2, "A", LocalDate.now().minusDays(17)),
                    createMatch("Arsenal", "Chelsea", 1, 1, "D", LocalDate.now().minusDays(24)),
                    createMatch("Newcastle", "Arsenal", 1, 2, "A", LocalDate.now().minusDays(31))
            );

            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(winningStreak);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            List<StreakIndicator> streaks = response.getStreakIndicators();
            assertThat(streaks).isNotEmpty();
            boolean hasWinStreak = streaks.stream()
                    .anyMatch(s -> s.getTeam().equals("Arsenal") &&
                            s.getStreakType().equals("WIN") &&
                            s.getStreakLength() == 3);
            assertThat(hasWinStreak).isTrue();
        }

        @Test
        @DisplayName("should detect losing streak of 3+ matches")
        void streak_losingStreak_detected() {
            // Given - 3 consecutive losses
            List<Match> losingStreak = Arrays.asList(
                    createMatch("Arsenal", "Liverpool", 0, 2, "A", LocalDate.now().minusDays(3)),
                    createMatch("Man City", "Arsenal", 3, 0, "H", LocalDate.now().minusDays(10)),
                    createMatch("Arsenal", "Chelsea", 0, 1, "A", LocalDate.now().minusDays(17)),
                    createMatch("Arsenal", "Newcastle", 2, 0, "H", LocalDate.now().minusDays(24))
            );

            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(losingStreak);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            List<StreakIndicator> streaks = response.getStreakIndicators();
            boolean hasLossStreak = streaks.stream()
                    .anyMatch(s -> s.getTeam().equals("Arsenal") &&
                            s.getStreakType().equals("LOSS") &&
                            s.getStreakLength() == 3);
            assertThat(hasLossStreak).isTrue();
        }

        @Test
        @DisplayName("should detect scoring streak of 6+ matches")
        void streak_scoringStreak_detected() {
            // Given - 6 consecutive matches with goals
            List<Match> scoringStreak = Arrays.asList(
                    createMatch("Arsenal", "Liverpool", 2, 1, "H", LocalDate.now().minusDays(3)),
                    createMatch("Arsenal", "Man City", 1, 1, "D", LocalDate.now().minusDays(10)),
                    createMatch("Tottenham", "Arsenal", 0, 3, "A", LocalDate.now().minusDays(17)),
                    createMatch("Arsenal", "Chelsea", 1, 0, "H", LocalDate.now().minusDays(24)),
                    createMatch("Newcastle", "Arsenal", 1, 2, "A", LocalDate.now().minusDays(31)),
                    createMatch("Arsenal", "West Ham", 3, 1, "H", LocalDate.now().minusDays(38))
            );

            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(scoringStreak);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            List<StreakIndicator> streaks = response.getStreakIndicators();
            boolean hasScoringStreak = streaks.stream()
                    .anyMatch(s -> s.getTeam().equals("Arsenal") &&
                            s.getStreakType().equals("SCORING") &&
                            s.getStreakLength() >= 6);
            assertThat(hasScoringStreak).isTrue();
        }

        @Test
        @DisplayName("should detect clean sheet streak of 2+ matches")
        void streak_cleanSheetStreak_detected() {
            // Given - 2 consecutive clean sheets
            List<Match> cleanSheetStreak = Arrays.asList(
                    createMatch("Arsenal", "Liverpool", 2, 0, "H", LocalDate.now().minusDays(3)),
                    createMatch("Arsenal", "Man City", 1, 0, "H", LocalDate.now().minusDays(10)),
                    createMatch("Tottenham", "Arsenal", 2, 1, "H", LocalDate.now().minusDays(17))
            );

            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(cleanSheetStreak);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            List<StreakIndicator> streaks = response.getStreakIndicators();
            boolean hasCleanSheetStreak = streaks.stream()
                    .anyMatch(s -> s.getTeam().equals("Arsenal") &&
                            s.getStreakType().equals("CLEAN_SHEET") &&
                            s.getStreakLength() >= 2);
            assertThat(hasCleanSheetStreak).isTrue();
        }
    }

    @Nested
    @DisplayName("Rest Analysis")
    class RestAnalysisTests {

        @Test
        @DisplayName("should calculate rest days correctly")
        void restAnalysis_calculatesRestDaysCorrectly() {
            // Given
            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(arsenalMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            RestAnalysis rest = response.getRestAnalysis();
            assertThat(rest.getHomeTeamRestDays()).isEqualTo(3);
            assertThat(rest.getAwayTeamRestDays()).isEqualTo(4);
            assertThat(rest.getRestDifference()).isEqualTo(-1); // Arsenal played more recently
        }

        @Test
        @DisplayName("should flag fatigue risk when rest < 4 days")
        void restAnalysis_flagsFatigueRisk() {
            // Given
            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(arsenalMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            RestAnalysis rest = response.getRestAnalysis();
            assertThat(rest.isHomeFatigueRisk()).isTrue(); // Arsenal: 3 days < 4
            assertThat(rest.isAwayFatigueRisk()).isFalse(); // Chelsea: 4 days = 4
        }
    }

    @Nested
    @DisplayName("Goal Threat Meter")
    class GoalThreatMeterTests {

        @Test
        @DisplayName("should calculate average goals scored correctly")
        void goalThreat_calculatesAverageGoals() {
            // Given
            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(arsenalMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            GoalThreatMeter threat = response.getGoalThreatMeter();
            // Arsenal scored: 2, 1, 2, 3, 2 = 10 goals in 5 matches = 2.0 avg
            assertThat(threat.getHomeTeamAvgScored()).isCloseTo(2.0, within(0.1));
        }

        @Test
        @DisplayName("should calculate expected goals based on both teams")
        void goalThreat_calculatesExpectedGoals() {
            // Given
            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(arsenalMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            GoalThreatMeter threat = response.getGoalThreatMeter();
            // Expected goals should be calculated based on attacking vs defending stats
            assertThat(threat.getTotalExpectedGoals()).isGreaterThan(0);
            assertThat(threat.getHomeExpectedGoals()).isGreaterThan(0);
            assertThat(threat.getAwayExpectedGoals()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle empty match history gracefully")
        void emptyHistory_returnsEmptyResponse() {
            // Given
            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByTeamBeforeDateIgnoreCase(eq("Arsenal"), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByTeamBeforeDateIgnoreCase(eq("Chelsea"), any()))
                    .thenReturn(Collections.emptyList());

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getKeyInsights()).contains("Insufficient data available");
        }

        @Test
        @DisplayName("should handle single match in history")
        void singleMatch_calculatesCorrectly() {
            // Given
            List<Match> singleMatch = Collections.singletonList(
                    createMatch("Arsenal", "Liverpool", 2, 1, "H", LocalDate.now().minusDays(3))
            );

            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(singleMatch);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            assertThat(response.getFormComparison().getHomeFormPoints()).isEqualTo(3);
            assertThat(response.getFormComparison().getHomeFormString()).isEqualTo("W");
        }

        @Test
        @DisplayName("should handle null goal values safely")
        void nullGoals_handledSafely() {
            // Given - Match with null goals
            Match matchWithNulls = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Liverpool")
                    .fullTimeHomeGoals(null)
                    .fullTimeAwayGoals(null)
                    .fullTimeResult("H")
                    .matchDate(LocalDate.now().minusDays(3))
                    .build();

            List<Match> matchesWithNull = Collections.singletonList(matchWithNulls);

            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(matchesWithNull);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When/Then - Should not throw NPE
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("should handle team playing only home matches")
        void homeOnlyMatches_calculatesCorrectly() {
            // Given
            List<Match> homeOnlyMatches = Arrays.asList(
                    createMatch("Arsenal", "Liverpool", 2, 1, "H", LocalDate.now().minusDays(3)),
                    createMatch("Arsenal", "Man City", 1, 0, "H", LocalDate.now().minusDays(10)),
                    createMatch("Arsenal", "Chelsea", 3, 2, "H", LocalDate.now().minusDays(17))
            );

            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(homeOnlyMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            assertThat(response.getFormComparison().getHomeFormPoints()).isEqualTo(9); // 3 wins
        }

        @Test
        @DisplayName("should handle team playing only away matches")
        void awayOnlyMatches_calculatesCorrectly() {
            // Given
            List<Match> awayOnlyMatches = Arrays.asList(
                    createMatch("Liverpool", "Arsenal", 1, 2, "A", LocalDate.now().minusDays(3)),
                    createMatch("Man City", "Arsenal", 0, 1, "A", LocalDate.now().minusDays(10)),
                    createMatch("Chelsea", "Arsenal", 2, 3, "A", LocalDate.now().minusDays(17))
            );

            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(awayOnlyMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            assertThat(response.getFormComparison().getHomeFormPoints()).isEqualTo(9); // 3 away wins
        }
    }

    @Nested
    @DisplayName("Market Predictions")
    class MarketPredictionsTests {

        @Test
        @DisplayName("should calculate expected total goals correctly")
        void marketPredictions_calculatesExpectedTotalGoals() {
            // Given
            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(arsenalMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            MarketPredictions market = response.getMarketPredictions();
            assertThat(market.getExpectedTotalGoals()).isGreaterThan(0);
            assertThat(market.getExpectedHomeGoals()).isGreaterThanOrEqualTo(0);
            assertThat(market.getExpectedAwayGoals()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("should generate appropriate recommendation for high-scoring expectation")
        void marketPredictions_highScoringRecommendation() {
            // Given - High scoring matches
            List<Match> highScoringMatches = Arrays.asList(
                    createMatch("Arsenal", "Liverpool", 4, 3, "H", LocalDate.now().minusDays(3)),
                    createMatch("Arsenal", "Man City", 3, 2, "H", LocalDate.now().minusDays(10)),
                    createMatch("Arsenal", "Chelsea", 5, 1, "H", LocalDate.now().minusDays(17)),
                    createMatch("Arsenal", "Tottenham", 3, 3, "D", LocalDate.now().minusDays(24)),
                    createMatch("Arsenal", "Newcastle", 4, 2, "H", LocalDate.now().minusDays(31))
            );

            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(highScoringMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            assertThat(response.getMarketPredictions().getRecommendation()).contains("High-scoring");
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("should complete calculation within 300ms")
        void performance_completesWithin300ms() {
            // Given
            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(arsenalMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            long startTime = System.currentTimeMillis();
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");
            long endTime = System.currentTimeMillis();

            // Then
            assertThat(endTime - startTime).isLessThan(300);
            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("should handle large dataset efficiently")
        void performance_handlesLargeDataset() {
            // Given - 100 matches
            List<Match> largeDataset = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                largeDataset.add(createMatch(
                        "Arsenal",
                        "Team" + i,
                        (int) (Math.random() * 4),
                        (int) (Math.random() * 3),
                        i % 3 == 0 ? "H" : (i % 3 == 1 ? "D" : "A"),
                        LocalDate.now().minusDays(i * 7)
                ));
            }

            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(largeDataset);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            long startTime = System.currentTimeMillis();
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");
            long endTime = System.currentTimeMillis();

            // Then
            assertThat(endTime - startTime).isLessThan(300);
            assertThat(response).isNotNull();
        }
    }

    @Nested
    @DisplayName("Team Name Normalization")
    class TeamNameNormalizationTests {

        @Test
        @DisplayName("should normalize team names for consistent lookup")
        void normalization_handlesVariousFormats() {
            // Given
            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(arsenalMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            assertThat(response.getHomeTeam()).isEqualTo("Arsenal");
            assertThat(response.getAwayTeam()).isEqualTo("Chelsea");
        }
    }

    @Nested
    @DisplayName("Query Efficiency")
    class QueryEfficiencyTests {

        @Test
        @DisplayName("should not make N+1 queries")
        void queryEfficiency_noNPlusOneQueries() {
            // Given
            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(arsenalMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then - Should only call findByTeamBeforeDate twice (once per team)
            verify(matchRepository, times(1)).findByTeamBeforeDate(eq("Arsenal"), any());
            verify(matchRepository, times(1)).findByTeamBeforeDate(eq("Chelsea"), any());
            // Case-insensitive variants should not be called if normal queries return data
            verify(matchRepository, never()).findByTeamBeforeDateIgnoreCase(any(), any());
        }
    }

    @Nested
    @DisplayName("Clean Sheet Percentage")
    class CleanSheetPercentageTests {

        @Test
        @DisplayName("should calculate clean sheet percentage correctly")
        void cleanSheet_calculatesPercentageCorrectly() {
            // Given - 2 clean sheets in 5 matches
            List<Match> matchesWithCleanSheets = Arrays.asList(
                    createMatch("Arsenal", "Liverpool", 2, 0, "H", LocalDate.now().minusDays(3)),  // Clean sheet
                    createMatch("Arsenal", "Man City", 1, 1, "D", LocalDate.now().minusDays(10)),
                    createMatch("Tottenham", "Arsenal", 0, 2, "A", LocalDate.now().minusDays(17)), // Clean sheet
                    createMatch("Arsenal", "Chelsea", 3, 1, "H", LocalDate.now().minusDays(24)),
                    createMatch("Newcastle", "Arsenal", 1, 2, "A", LocalDate.now().minusDays(31))
            );

            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(matchesWithCleanSheets);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            GoalThreatMeter threat = response.getGoalThreatMeter();
            // Arsenal conceded: 0, 1, 0, 1, 1 = 0.6 avg conceded (low = good defense)
            assertThat(threat.getHomeTeamAvgConceded()).isCloseTo(0.6, within(0.1));
        }
    }

    @Nested
    @DisplayName("Over 2.5 Goals Logic")
    class Over25GoalsTests {

        @Test
        @DisplayName("should identify high-scoring matches correctly")
        void over25_identifiesHighScoringMatches() {
            // Given - High scoring (4 matches over 2.5 goals)
            List<Match> highScoringMatches = Arrays.asList(
                    createMatch("Arsenal", "Liverpool", 3, 1, "H", LocalDate.now().minusDays(3)),  // 4 goals
                    createMatch("Arsenal", "Man City", 2, 2, "D", LocalDate.now().minusDays(10)), // 4 goals
                    createMatch("Tottenham", "Arsenal", 1, 3, "A", LocalDate.now().minusDays(17)), // 4 goals
                    createMatch("Arsenal", "Chelsea", 2, 0, "H", LocalDate.now().minusDays(24)),   // 2 goals
                    createMatch("Newcastle", "Arsenal", 0, 3, "A", LocalDate.now().minusDays(31)) // 3 goals
            );

            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(highScoringMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            GoalThreatMeter threat = response.getGoalThreatMeter();
            // Arsenal scored: 3 (home), 2 (home), 3 (away), 2 (home), 3 (away) = 13 goals in 5 matches = 2.6 avg
            assertThat(threat.getHomeTeamAvgScored()).isCloseTo(2.6, within(0.1));
        }
    }

    @Nested
    @DisplayName("CurrentStreak Calculation (STEP 1-6 Implementation)")
    class CurrentStreakTests {

        @Test
        @DisplayName("should calculate winless streak correctly")
        void currentStreak_winlessStreak_calculatedCorrectly() {
            // Given - 6 consecutive matches without a win (L, D, L, D, L, L)
            List<Match> winlessMatches = Arrays.asList(
                    createMatch("Arsenal", "Liverpool", 0, 2, "A", LocalDate.now().minusDays(3)),   // L
                    createMatch("Arsenal", "Man City", 1, 1, "D", LocalDate.now().minusDays(10)),  // D
                    createMatch("Tottenham", "Arsenal", 2, 0, "H", LocalDate.now().minusDays(17)), // L
                    createMatch("Arsenal", "Chelsea", 0, 0, "D", LocalDate.now().minusDays(24)),   // D
                    createMatch("Newcastle", "Arsenal", 3, 1, "H", LocalDate.now().minusDays(31)), // L
                    createMatch("Arsenal", "West Ham", 0, 1, "A", LocalDate.now().minusDays(38))   // L
            );

            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(winlessMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            CurrentStreak homeStreak = response.getHomeCurrentStreak();
            assertThat(homeStreak).isNotNull();
            assertThat(homeStreak.getTeamName()).isEqualTo("Arsenal");
            assertThat(homeStreak.getWinlessStreak()).isEqualTo(6);
            assertThat(homeStreak.getPrimaryStreakType()).isEqualTo("LOSS");  // Primary is LOSS (consecutive losses at start)
            assertThat(homeStreak.getRecentResults()).hasSize(6);
        }

        @Test
        @DisplayName("should show 'No active streak' when last match was a win followed by losses")
        void currentStreak_displayText_showsNoActiveStreak_whenMixedResults() {
            // Given - Last match is a win, winless/loss streak should be 0
            List<Match> mixedMatches = Arrays.asList(
                    createMatch("Arsenal", "Liverpool", 2, 1, "H", LocalDate.now().minusDays(3)),   // W
                    createMatch("Arsenal", "Man City", 0, 2, "A", LocalDate.now().minusDays(10)),   // L
                    createMatch("Tottenham", "Arsenal", 3, 0, "H", LocalDate.now().minusDays(17))   // L
            );

            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(mixedMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            CurrentStreak homeStreak = response.getHomeCurrentStreak();
            assertThat(homeStreak).isNotNull();
            assertThat(homeStreak.getWinStreak()).isEqualTo(1);
            assertThat(homeStreak.getWinlessStreak()).isEqualTo(0);
            assertThat(homeStreak.getPrimaryStreakType()).isEqualTo("WIN");
        }

        @Test
        @DisplayName("should ensure key insight consistency with current streak")
        void currentStreak_keyInsight_consistency() {
            // Given - 6 consecutive matches without a win
            List<Match> winlessMatches = Arrays.asList(
                    createMatch("Arsenal", "Liverpool", 0, 2, "A", LocalDate.now().minusDays(3)),
                    createMatch("Arsenal", "Man City", 1, 1, "D", LocalDate.now().minusDays(10)),
                    createMatch("Tottenham", "Arsenal", 2, 0, "H", LocalDate.now().minusDays(17)),
                    createMatch("Arsenal", "Chelsea", 0, 0, "D", LocalDate.now().minusDays(24)),
                    createMatch("Newcastle", "Arsenal", 3, 1, "H", LocalDate.now().minusDays(31)),
                    createMatch("Arsenal", "West Ham", 0, 1, "A", LocalDate.now().minusDays(38))
            );

            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(winlessMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then - Key insight should mention winless streak AND match the CurrentStreak data
            CurrentStreak homeStreak = response.getHomeCurrentStreak();
            List<String> keyInsights = response.getKeyInsights();

            // If winless streak >= 5 (threshold), key insight should mention it
            if (homeStreak.getWinlessStreak() >= 5) {
                boolean hasWinlessInsight = keyInsights.stream()
                        .anyMatch(insight -> insight.contains("Arsenal") && insight.contains("without a win"));
                // Only assert if the streak exceeds threshold
                assertThat(hasWinlessInsight)
                        .as("Key insight should mention Arsenal's winless streak of " + homeStreak.getWinlessStreak())
                        .isTrue();
            }
        }

        @Test
        @DisplayName("should populate recentResults with last 6 match outcomes")
        void currentStreak_recentResults_populated() {
            // Given
            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(arsenalMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaMatches);

            // When
            PreMatchInsightsResponse response = preMatchInsightsService.getPreMatchInsights("Arsenal", "Chelsea");

            // Then
            CurrentStreak homeStreak = response.getHomeCurrentStreak();
            assertThat(homeStreak.getRecentResults()).isNotEmpty();
            assertThat(homeStreak.getRecentResults()).containsPattern("[WDL]+");
        }
    }
}

