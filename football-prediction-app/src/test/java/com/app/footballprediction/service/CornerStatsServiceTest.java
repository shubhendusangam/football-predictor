package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.CornerPredictionDTO;
import com.app.footballprediction.dto.CornerStatsDTO;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CornerStatsService.
 *
 * Tests cover:
 * - Corner statistics calculation (full season)
 * - Corner prediction logic
 * - Edge cases (empty data, null values, divide-by-zero)
 * - Validation rules
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CornerStatsService Unit Tests")
class CornerStatsServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private CornerStatsService cornerStatsService;

    private List<Match> sampleMatches;
    private static final String TEAM_NAME = "Arsenal";
    private static final String TEST_SEASON = "2025-26";
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 2, 27);

    @BeforeEach
    void setUp() {
        sampleMatches = createSampleMatches();
    }

    /**
     * Helper to setup common mocks for season-based queries
     */
    private void setupSeasonMocks(String teamName, List<Match> matches) {
        when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
        when(matchRepository.findByTeamAndSeasonBeforeDate(eq(teamName), eq(TEST_SEASON), any()))
                .thenReturn(matches);
    }

    // ══════════════════════════════════════════════════════════════════════
    // TEST: calculateCornerStats
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("calculateCornerStats Tests")
    class CalculateCornerStatsTests {

        @Test
        @DisplayName("should calculate correct corner averages for home matches")
        void calculateCornerStats_homeMatches_calculatesCorrectAverages() {
            // Given
            List<Match> homeMatches = sampleMatches.stream()
                    .filter(m -> m.getHomeTeam().equals(TEAM_NAME))
                    .toList();

            setupSeasonMocks(TEAM_NAME, homeMatches);

            // When
            CornerStatsDTO stats = cornerStatsService.calculateCornerStats(TEAM_NAME, true);

            // Then
            assertThat(stats).isNotNull();
            assertThat(stats.getTeamName()).isEqualTo(TEAM_NAME);
            assertThat(stats.getIsHome()).isTrue();
            assertThat(stats.getAvgCornersWon()).isGreaterThan(0);
            assertThat(stats.getAvgCornersAgainst()).isGreaterThan(0);
            assertThat(stats.getCornerDominance()).isBetween(0.0, 1.0);
            assertThat(stats.getSuccessRate()).isBetween(0.0, 1.0);
            assertThat(stats.getMatchesAnalyzed()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should calculate correct corner averages for away matches")
        void calculateCornerStats_awayMatches_calculatesCorrectAverages() {
            // Given
            List<Match> awayMatches = sampleMatches.stream()
                    .filter(m -> m.getAwayTeam().equals(TEAM_NAME))
                    .toList();

            setupSeasonMocks(TEAM_NAME, awayMatches);

            // When
            CornerStatsDTO stats = cornerStatsService.calculateCornerStats(TEAM_NAME, false);

            // Then
            assertThat(stats).isNotNull();
            assertThat(stats.getIsHome()).isFalse();
            assertThat(stats.getAvgCornersWon()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("should calculate overall stats when isHome is null")
        void calculateCornerStats_allMatches_calculatesOverallStats() {
            // Given
            setupSeasonMocks(TEAM_NAME, sampleMatches);

            // When
            CornerStatsDTO stats = cornerStatsService.calculateCornerStats(TEAM_NAME, null);

            // Then
            assertThat(stats).isNotNull();
            assertThat(stats.getIsHome()).isNull();
            assertThat(stats.getMatchesAnalyzed()).isEqualTo(sampleMatches.size());
        }

        @Test
        @DisplayName("should throw exception for empty team name")
        void calculateCornerStats_emptyTeamName_throwsException() {
            assertThatThrownBy(() -> cornerStatsService.calculateCornerStats("", true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be null or empty");
        }

        @Test
        @DisplayName("should throw exception for null team name")
        void calculateCornerStats_nullTeamName_throwsException() {
            assertThatThrownBy(() -> cornerStatsService.calculateCornerStats(null, true))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw exception when no matches found")
        void calculateCornerStats_noMatches_throwsException() {
            // Given
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findByTeamAndSeasonBeforeDate(any(), eq(TEST_SEASON), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByTeamAndSeasonBeforeDateIgnoreCase(any(), eq(TEST_SEASON), any()))
                    .thenReturn(Collections.emptyList());

            // When/Then
            assertThatThrownBy(() -> cornerStatsService.calculateCornerStats("UnknownTeam", true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No corner data available");
        }

        @Test
        @DisplayName("should use all season matches (no 20 match limit)")
        void calculateCornerStats_manyMatches_usesAllSeasonMatches() {
            // Given
            List<Match> manyMatches = createManyMatches(30);
            setupSeasonMocks(TEAM_NAME, manyMatches);

            // When
            CornerStatsDTO stats = cornerStatsService.calculateCornerStats(TEAM_NAME, null);

            // Then - should use all 30 matches (full season)
            assertThat(stats.getMatchesAnalyzed()).isEqualTo(30);
        }

        @Test
        @DisplayName("should handle matches with missing corner data")
        void calculateCornerStats_missingCornerData_skipsMatches() {
            // Given
            List<Match> matchesWithNulls = new ArrayList<>();
            matchesWithNulls.add(createMatch(TEAM_NAME, "Chelsea", 6, 4, "H", TEST_DATE));
            matchesWithNulls.add(createMatchWithNullCorners(TEAM_NAME, "Liverpool", TEST_DATE.minusDays(1)));
            matchesWithNulls.add(createMatch(TEAM_NAME, "Tottenham", 5, 3, "H", TEST_DATE.minusDays(2)));

            setupSeasonMocks(TEAM_NAME, matchesWithNulls);

            // When
            CornerStatsDTO stats = cornerStatsService.calculateCornerStats(TEAM_NAME, null);

            // Then
            assertThat(stats.getMatchesAnalyzed()).isEqualTo(2); // Skips the one with nulls
        }

        @Test
        @DisplayName("should calculate corner dominance correctly")
        void calculateCornerStats_cornerDominance_calculatedCorrectly() {
            // Given
            List<Match> matches = List.of(
                    createMatch(TEAM_NAME, "TeamA", 8, 4, "H", TEST_DATE),  // 8 won, 4 against
                    createMatch(TEAM_NAME, "TeamB", 6, 4, "H", TEST_DATE.minusDays(1)) // 6 won, 4 against
            );
            // Total: 14 won, 8 against = 14/22 = 0.636 dominance

            setupSeasonMocks(TEAM_NAME, matches);

            // When
            CornerStatsDTO stats = cornerStatsService.calculateCornerStats(TEAM_NAME, null);

            // Then
            double expectedDominance = 14.0 / 22.0;
            assertThat(stats.getCornerDominance()).isCloseTo(expectedDominance, within(0.01));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // TEST: predictMatchCorners
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("predictMatchCorners Tests")
    class PredictMatchCornersTests {

        @Test
        @DisplayName("should predict corners for valid match")
        void predictMatchCorners_validMatch_returnsPrediction() {
            // Given
            String homeTeam = "Arsenal";
            String awayTeam = "Chelsea";

            List<Match> arsenalHomeMatches = createSampleMatches().stream()
                    .filter(m -> m.getHomeTeam().equals(homeTeam))
                    .toList();
            List<Match> chelseaAwayMatches = createSampleMatchesForTeam("Chelsea", false);

            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq(homeTeam), eq(TEST_SEASON), any()))
                    .thenReturn(arsenalHomeMatches);
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq(awayTeam), eq(TEST_SEASON), any()))
                    .thenReturn(chelseaAwayMatches);

            // When
            CornerPredictionDTO prediction = cornerStatsService.predictMatchCorners(homeTeam, awayTeam);

            // Then
            assertThat(prediction).isNotNull();
            assertThat(prediction.getHomeTeam()).isEqualTo(homeTeam);
            assertThat(prediction.getAwayTeam()).isEqualTo(awayTeam);
            assertThat(prediction.getExpectedTotalCorners()).isGreaterThan(0);

            // Verify total = home + away
            double calculatedTotal = prediction.getExpectedHomeCorners() + prediction.getExpectedAwayCorners();
            assertThat(prediction.getExpectedTotalCorners()).isCloseTo(calculatedTotal, within(0.01));
        }

        @Test
        @DisplayName("should calculate valid probabilities between 0 and 1")
        void predictMatchCorners_probabilities_areBetween0And1() {
            // Given
            String homeTeam = "Arsenal";
            String awayTeam = "Chelsea";

            setupMocksForPrediction(homeTeam, awayTeam);

            // When
            CornerPredictionDTO prediction = cornerStatsService.predictMatchCorners(homeTeam, awayTeam);

            // Then
            assertThat(prediction.getProbOver9_5()).isBetween(0.0, 1.0);
            assertThat(prediction.getProbOver10_5()).isBetween(0.0, 1.0);
            assertThat(prediction.getProbOver11_5()).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("should have decreasing probabilities for increasing thresholds")
        void predictMatchCorners_probabilities_decreaseWithThreshold() {
            // Given
            String homeTeam = "Arsenal";
            String awayTeam = "Chelsea";

            setupMocksForPrediction(homeTeam, awayTeam);

            // When
            CornerPredictionDTO prediction = cornerStatsService.predictMatchCorners(homeTeam, awayTeam);

            // Then - Higher threshold should have lower probability
            assertThat(prediction.getProbOver9_5()).isGreaterThanOrEqualTo(prediction.getProbOver10_5());
            assertThat(prediction.getProbOver10_5()).isGreaterThanOrEqualTo(prediction.getProbOver11_5());
        }

        @Test
        @DisplayName("should throw exception when home and away teams are the same")
        void predictMatchCorners_sameTeam_throwsException() {
            assertThatThrownBy(() -> cornerStatsService.predictMatchCorners("Arsenal", "Arsenal"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be the same");
        }

        @Test
        @DisplayName("should throw exception for empty home team")
        void predictMatchCorners_emptyHomeTeam_throwsException() {
            assertThatThrownBy(() -> cornerStatsService.predictMatchCorners("", "Chelsea"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should calculate confidence based on sample size")
        void predictMatchCorners_confidence_basedOnSampleSize() {
            // Given
            String homeTeam = "Arsenal";
            String awayTeam = "Chelsea";

            setupMocksForPrediction(homeTeam, awayTeam);

            // When
            CornerPredictionDTO prediction = cornerStatsService.predictMatchCorners(homeTeam, awayTeam);

            // Then
            assertThat(prediction.getConfidence()).isBetween(0.0, 1.0);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // TEST: Edge Cases
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle zero corners in match")
        void calculateCornerStats_zeroCorners_handlesCorrectly() {
            // Given
            List<Match> zeroCornerMatches = List.of(
                    createMatch(TEAM_NAME, "TeamA", 0, 0, "D", TEST_DATE)
            );

            setupSeasonMocks(TEAM_NAME, zeroCornerMatches);

            // When
            CornerStatsDTO stats = cornerStatsService.calculateCornerStats(TEAM_NAME, null);

            // Then
            assertThat(stats.getAvgCornersWon()).isEqualTo(0.0);
            assertThat(stats.getAvgCornersAgainst()).isEqualTo(0.0);
            assertThat(stats.getCornerDominance()).isEqualTo(0.0); // 0 / 0 handled
        }

        @Test
        @DisplayName("should handle case-insensitive team name matching")
        void calculateCornerStats_caseInsensitive_matchesCorrectly() {
            // Given - lowercase "arsenal" not found, but case-insensitive finds it
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("arsenal"), eq(TEST_SEASON), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByTeamAndSeasonBeforeDateIgnoreCase(eq("arsenal"), eq(TEST_SEASON), any()))
                    .thenReturn(sampleMatches);
            // After resolving to "Arsenal", the service fetches matches with proper casing
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq(TEAM_NAME), eq(TEST_SEASON), any()))
                    .thenReturn(sampleMatches);

            // When
            CornerStatsDTO stats = cornerStatsService.calculateCornerStats("arsenal", null);

            // Then
            assertThat(stats).isNotNull();
            assertThat(stats.getTeamName()).isEqualTo(TEAM_NAME);
        }

        @Test
        @DisplayName("should return no negative values")
        void calculateCornerStats_noNegativeValues() {
            // Given
            setupSeasonMocks(TEAM_NAME, sampleMatches);

            // When
            CornerStatsDTO stats = cornerStatsService.calculateCornerStats(TEAM_NAME, null);

            // Then
            assertThat(stats.getAvgCornersWon()).isGreaterThanOrEqualTo(0);
            assertThat(stats.getAvgCornersAgainst()).isGreaterThanOrEqualTo(0);
            assertThat(stats.getCornerDominance()).isGreaterThanOrEqualTo(0);
            assertThat(stats.getSuccessRate()).isGreaterThanOrEqualTo(0);
            assertThat(stats.getWeightedAvgCorners()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("should return no NaN values")
        void calculateCornerStats_noNaNValues() {
            // Given
            setupSeasonMocks(TEAM_NAME, sampleMatches);

            // When
            CornerStatsDTO stats = cornerStatsService.calculateCornerStats(TEAM_NAME, null);

            // Then
            assertThat(stats.getAvgCornersWon()).isNotNaN();
            assertThat(stats.getAvgCornersAgainst()).isNotNaN();
            assertThat(stats.getCornerDominance()).isNotNaN();
            assertThat(stats.getSuccessRate()).isNotNaN();
            assertThat(stats.getWeightedAvgCorners()).isNotNaN();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ══════════════════════════════════════════════════════════════════════

    private List<Match> createSampleMatches() {
        List<Match> matches = new ArrayList<>();

        // Home matches for Arsenal
        matches.add(createMatch(TEAM_NAME, "Chelsea", 7, 4, "H", TEST_DATE));
        matches.add(createMatch(TEAM_NAME, "Liverpool", 6, 5, "H", TEST_DATE.minusDays(7)));
        matches.add(createMatch(TEAM_NAME, "Tottenham", 8, 3, "H", TEST_DATE.minusDays(14)));

        // Away matches for Arsenal
        matches.add(createAwayMatch("Man City", TEAM_NAME, 5, 4, "H", TEST_DATE.minusDays(21)));
        matches.add(createAwayMatch("Newcastle", TEAM_NAME, 4, 6, "A", TEST_DATE.minusDays(28)));

        return matches;
    }

    private List<Match> createSampleMatchesForTeam(String teamName, boolean asHome) {
        List<Match> matches = new ArrayList<>();
        if (asHome) {
            matches.add(createMatch(teamName, "TeamA", 5, 4, "H", TEST_DATE));
            matches.add(createMatch(teamName, "TeamB", 6, 3, "H", TEST_DATE.minusDays(7)));
        } else {
            matches.add(createAwayMatch("TeamC", teamName, 6, 5, "H", TEST_DATE));
            matches.add(createAwayMatch("TeamD", teamName, 4, 4, "D", TEST_DATE.minusDays(7)));
        }
        return matches;
    }

    private List<Match> createManyMatches(int count) {
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            matches.add(createMatch(TEAM_NAME, "Team" + i, 5 + (i % 4), 3 + (i % 3),
                    i % 2 == 0 ? "H" : "D", TEST_DATE.minusDays(i)));
        }
        return matches;
    }

    private Match createMatch(String home, String away, int homeCorners, int awayCorners,
                              String result, LocalDate date) {
        return Match.builder()
                .homeTeam(home)
                .awayTeam(away)
                .homeCorners(homeCorners)
                .awayCorners(awayCorners)
                .fullTimeResult(result)
                .fullTimeHomeGoals(result.equals("H") ? 2 : 1)
                .fullTimeAwayGoals(result.equals("A") ? 2 : 1)
                .matchDate(date)
                .build();
    }

    private Match createAwayMatch(String home, String away, int homeCorners, int awayCorners,
                                  String result, LocalDate date) {
        return createMatch(home, away, homeCorners, awayCorners, result, date);
    }

    private Match createMatchWithNullCorners(String home, String away, LocalDate date) {
        return Match.builder()
                .homeTeam(home)
                .awayTeam(away)
                .homeCorners(null)
                .awayCorners(null)
                .fullTimeResult("D")
                .fullTimeHomeGoals(1)
                .fullTimeAwayGoals(1)
                .matchDate(date)
                .build();
    }

    private void setupMocksForPrediction(String homeTeam, String awayTeam) {
        List<Match> homeTeamMatches = createSampleMatches().stream()
                .filter(m -> m.getHomeTeam().equals(homeTeam))
                .toList();
        List<Match> awayTeamMatches = createSampleMatchesForTeam(awayTeam, false);

        when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
        when(matchRepository.findByTeamAndSeasonBeforeDate(eq(homeTeam), eq(TEST_SEASON), any()))
                .thenReturn(homeTeamMatches);
        when(matchRepository.findByTeamAndSeasonBeforeDate(eq(awayTeam), eq(TEST_SEASON), any()))
                .thenReturn(awayTeamMatches);
    }
}

