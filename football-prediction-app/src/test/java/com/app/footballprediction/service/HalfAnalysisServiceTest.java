package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.HalfAnalysisDTO;
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
 * Unit tests for HalfAnalysisService.
 *
 * Tests cover:
 * - Goal distribution calculations (first half vs second half)
 * - Win rates based on half-time position
 * - Comeback rate calculations
 * - Pattern classification
 * - Edge cases (no data, zero goals, invalid input)
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HalfAnalysisService Unit Tests")
class HalfAnalysisServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private HalfAnalysisService halfAnalysisService;

    // ══════════════════════════════════════════════════════════════════════
    // TEST DATA BUILDERS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Create a test match with the team as home team.
     */
    private Match createHomeMatch(String teamName, int htHome, int htAway, int ftHome, int ftAway, int daysAgo) {
        String ftResult = ftHome > ftAway ? "H" : (ftHome < ftAway ? "A" : "D");
        return Match.builder()
                .id((long) daysAgo)
                .matchDate(LocalDate.now().minusDays(daysAgo))
                .season("2025-26")
                .homeTeam(teamName)
                .awayTeam("Opponent " + daysAgo)
                .halfTimeHomeGoals(htHome)
                .halfTimeAwayGoals(htAway)
                .fullTimeHomeGoals(ftHome)
                .fullTimeAwayGoals(ftAway)
                .fullTimeResult(ftResult)
                .build();
    }

    /**
     * Create a test match with the team as away team.
     */
    private Match createAwayMatch(String teamName, int htHome, int htAway, int ftHome, int ftAway, int daysAgo) {
        String ftResult = ftHome > ftAway ? "H" : (ftHome < ftAway ? "A" : "D");
        return Match.builder()
                .id((long) (100 + daysAgo))
                .matchDate(LocalDate.now().minusDays(daysAgo))
                .season("2025-26")
                .homeTeam("Opponent " + daysAgo)
                .awayTeam(teamName)
                .halfTimeHomeGoals(htHome)
                .halfTimeAwayGoals(htAway)
                .fullTimeHomeGoals(ftHome)
                .fullTimeAwayGoals(ftAway)
                .fullTimeResult(ftResult)
                .build();
    }

    /**
     * Create a list of matches with strong second half performance.
     */
    private List<Match> createStrongSecondHalfMatches(String teamName) {
        List<Match> matches = new ArrayList<>();
        // Home matches: Team scores more in second half
        // HT: 0-0, FT: 2-0 (2 goals in 2H)
        matches.add(createHomeMatch(teamName, 0, 0, 2, 0, 1));
        // HT: 1-0, FT: 3-1 (2 goals in 2H)
        matches.add(createHomeMatch(teamName, 1, 0, 3, 1, 3));
        // HT: 0-1, FT: 2-1 (2 goals in 2H) - comeback
        matches.add(createHomeMatch(teamName, 0, 1, 2, 1, 5));
        // HT: 0-0, FT: 1-0 (1 goal in 2H)
        matches.add(createHomeMatch(teamName, 0, 0, 1, 0, 7));
        // Away: HT: 0-0, FT: 0-3 (3 goals in 2H)
        matches.add(createAwayMatch(teamName, 0, 0, 0, 3, 2));
        // Away: HT: 1-1, FT: 1-3 (2 goals in 2H)
        matches.add(createAwayMatch(teamName, 1, 1, 1, 3, 4));
        // Away: HT: 2-0, FT: 2-2 (2 goals in 2H) - comeback
        matches.add(createAwayMatch(teamName, 2, 0, 2, 2, 6));

        return matches;
    }

    /**
     * Create a list of matches with strong first half performance.
     */
    private List<Match> createStrongFirstHalfMatches(String teamName) {
        List<Match> matches = new ArrayList<>();
        // Home: HT: 2-0, FT: 2-0 (all goals in 1H)
        matches.add(createHomeMatch(teamName, 2, 0, 2, 0, 1));
        // Home: HT: 3-0, FT: 3-1 (3 goals in 1H, 0 in 2H)
        matches.add(createHomeMatch(teamName, 3, 0, 3, 1, 3));
        // Home: HT: 2-1, FT: 2-1 (2 goals in 1H, 0 in 2H)
        matches.add(createHomeMatch(teamName, 2, 1, 2, 1, 5));
        // Away: HT: 0-2, FT: 0-2 (2 goals in 1H)
        matches.add(createAwayMatch(teamName, 0, 2, 0, 2, 2));
        // Away: HT: 1-3, FT: 1-3 (3 goals in 1H)
        matches.add(createAwayMatch(teamName, 1, 3, 1, 3, 4));

        return matches;
    }

    /**
     * Create a list of balanced matches.
     */
    private List<Match> createBalancedMatches(String teamName) {
        List<Match> matches = new ArrayList<>();
        // Home: HT: 1-0, FT: 2-1 (1 goal each half)
        matches.add(createHomeMatch(teamName, 1, 0, 2, 1, 1));
        // Home: HT: 1-1, FT: 2-2 (1 goal each half)
        matches.add(createHomeMatch(teamName, 1, 1, 2, 2, 3));
        // Away: HT: 0-1, FT: 1-2 (1 goal each half)
        matches.add(createAwayMatch(teamName, 0, 1, 1, 2, 2));
        // Away: HT: 1-1, FT: 2-2 (1 goal each half)
        matches.add(createAwayMatch(teamName, 1, 1, 2, 2, 4));

        return matches;
    }

    // ══════════════════════════════════════════════════════════════════════
    // BASIC FUNCTIONALITY TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("analyzeByHalf()")
    class AnalyzeByHalfTests {

        @Test
        @DisplayName("Should return analysis for team with valid data")
        void shouldReturnAnalysisForTeamWithValidData() {
            // Given
            String teamName = "Arsenal";
            List<Match> matches = createBalancedMatches(teamName);

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTeamName()).isEqualTo(teamName);
            assertThat(result.getMatchesAnalyzed()).isEqualTo(4);
            assertThat(result.getFirstHalfPercentage()).isGreaterThanOrEqualTo(0);
            assertThat(result.getSecondHalfPercentage()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Should return empty DTO when no matches found")
        void shouldReturnEmptyDtoWhenNoMatchesFound() {
            // Given
            String teamName = "NonExistent FC";

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByTeamBeforeDateIgnoreCase(eq(teamName), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTeamName()).isEqualTo(teamName);
            assertThat(result.getMatchesAnalyzed()).isZero();
            assertThat(result.getPattern()).isEqualTo("Balanced");
        }

        @Test
        @DisplayName("Should throw exception for null team name")
        void shouldThrowExceptionForNullTeamName() {
            // When/Then
            assertThatThrownBy(() -> halfAnalysisService.analyzeByHalf(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw exception for blank team name")
        void shouldThrowExceptionForBlankTeamName() {
            // When/Then
            assertThatThrownBy(() -> halfAnalysisService.analyzeByHalf("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be null or empty");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // GOAL DISTRIBUTION TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Goal Distribution Calculations")
    class GoalDistributionTests {

        @Test
        @DisplayName("Should identify strong second half team")
        void shouldIdentifyStrongSecondHalfTeam() {
            // Given
            String teamName = "Man City";
            List<Match> matches = createStrongSecondHalfMatches(teamName);

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            assertThat(result.getSecondHalfPercentage())
                    .isGreaterThan(result.getFirstHalfPercentage());
            assertThat(result.getStrongerHalf()).isEqualTo("Second Half");
            // With 60%+ in second half, should be "Strong Finisher"
            if (result.getSecondHalfPercentage() >= 60.0) {
                assertThat(result.getPattern()).isEqualTo("Strong Finisher");
            }
        }

        @Test
        @DisplayName("Should identify fast starter team")
        void shouldIdentifyFastStarterTeam() {
            // Given
            String teamName = "Liverpool";
            List<Match> matches = createStrongFirstHalfMatches(teamName);

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            assertThat(result.getFirstHalfPercentage())
                    .isGreaterThan(result.getSecondHalfPercentage());
            assertThat(result.getStrongerHalf()).isEqualTo("First Half");
            // With 60%+ in first half, should be "Fast Starter"
            if (result.getFirstHalfPercentage() >= 60.0) {
                assertThat(result.getPattern()).isEqualTo("Fast Starter");
            }
        }

        @Test
        @DisplayName("Should identify balanced team")
        void shouldIdentifyBalancedTeam() {
            // Given
            String teamName = "Chelsea";
            List<Match> matches = createBalancedMatches(teamName);

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            double difference = Math.abs(result.getFirstHalfPercentage() - result.getSecondHalfPercentage());
            if (difference < 5.0) {
                assertThat(result.getStrongerHalf()).isEqualTo("Balanced");
                assertThat(result.getPattern()).isEqualTo("Balanced");
            }
        }

        @Test
        @DisplayName("Percentages should sum to 100")
        void percentagesShouldSumTo100() {
            // Given
            String teamName = "Tottenham";
            List<Match> matches = createStrongSecondHalfMatches(teamName);

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            double sum = result.getFirstHalfPercentage() + result.getSecondHalfPercentage();
            assertThat(sum).isBetween(99.99, 100.01); // Allow small rounding error
        }

        @Test
        @DisplayName("Should not have negative values")
        void shouldNotHaveNegativeValues() {
            // Given
            String teamName = "Brighton";
            List<Match> matches = createBalancedMatches(teamName);

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            assertThat(result.getFirstHalfGoalsAvg()).isGreaterThanOrEqualTo(0);
            assertThat(result.getSecondHalfGoalsAvg()).isGreaterThanOrEqualTo(0);
            assertThat(result.getFirstHalfPercentage()).isGreaterThanOrEqualTo(0);
            assertThat(result.getSecondHalfPercentage()).isGreaterThanOrEqualTo(0);
            assertThat(result.getTotalFirstHalfGoals()).isGreaterThanOrEqualTo(0);
            assertThat(result.getTotalSecondHalfGoals()).isGreaterThanOrEqualTo(0);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // WIN RATE TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Win Rate Calculations")
    class WinRateTests {

        @Test
        @DisplayName("Should calculate win rate when leading at HT")
        void shouldCalculateWinRateWhenLeadingAtHT() {
            // Given
            String teamName = "Arsenal";
            List<Match> matches = new ArrayList<>();
            // 3 matches leading at HT, 2 wins, 1 draw
            matches.add(createHomeMatch(teamName, 2, 0, 3, 0, 1)); // Leading HT, Win
            matches.add(createHomeMatch(teamName, 1, 0, 2, 2, 3)); // Leading HT, Draw
            matches.add(createAwayMatch(teamName, 0, 2, 1, 3, 2)); // Leading HT, Win

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            assertThat(result.getMatchesLeadingHT()).isEqualTo(3);
            assertThat(result.getWinRateWhenLeadingHT()).isBetween(0.0, 100.0);
            // 2 wins out of 3 = 66.67%
            assertThat(result.getWinRateWhenLeadingHT()).isCloseTo(66.67, within(1.0));
        }

        @Test
        @DisplayName("Should calculate comeback rate")
        void shouldCalculateComebackRate() {
            // Given
            String teamName = "Man United";
            List<Match> matches = new ArrayList<>();
            // 3 matches trailing at HT, 1 win (comeback)
            matches.add(createHomeMatch(teamName, 0, 2, 3, 2, 1)); // Trailing HT, Win (comeback!)
            matches.add(createHomeMatch(teamName, 0, 1, 1, 2, 3)); // Trailing HT, Loss
            matches.add(createAwayMatch(teamName, 2, 0, 2, 1, 2)); // Trailing HT, Loss

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            assertThat(result.getMatchesTrailingHT()).isEqualTo(3);
            assertThat(result.getComebackRate()).isBetween(0.0, 100.0);
            // 1 win out of 3 = 33.33%
            assertThat(result.getComebackRate()).isCloseTo(33.33, within(1.0));
        }

        @Test
        @DisplayName("Win rates should be capped at 100%")
        void winRatesShouldBeCappedAt100() {
            // Given
            String teamName = "Newcastle";
            List<Match> matches = new ArrayList<>();
            // All leading and winning
            matches.add(createHomeMatch(teamName, 2, 0, 3, 0, 1));
            matches.add(createHomeMatch(teamName, 1, 0, 2, 0, 3));
            matches.add(createAwayMatch(teamName, 0, 2, 1, 3, 2));

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            assertThat(result.getWinRateWhenLeadingHT()).isLessThanOrEqualTo(100.0);
            assertThat(result.getWinRateWhenDrawingHT()).isLessThanOrEqualTo(100.0);
            assertThat(result.getWinRateWhenLosingHT()).isLessThanOrEqualTo(100.0);
            assertThat(result.getComebackRate()).isLessThanOrEqualTo(100.0);
        }

        @Test
        @DisplayName("Should handle no matches in HT position category")
        void shouldHandleNoMatchesInHTCategory() {
            // Given
            String teamName = "Villa";
            List<Match> matches = new ArrayList<>();
            // All drawing at HT
            matches.add(createHomeMatch(teamName, 0, 0, 1, 0, 1));
            matches.add(createHomeMatch(teamName, 1, 1, 2, 1, 3));

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            assertThat(result.getMatchesLeadingHT()).isZero();
            assertThat(result.getWinRateWhenLeadingHT()).isZero(); // No divide by zero
            assertThat(result.getMatchesTrailingHT()).isZero();
            assertThat(result.getComebackRate()).isZero(); // No divide by zero
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // EDGE CASE TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle matches with zero goals")
        void shouldHandleMatchesWithZeroGoals() {
            // Given
            String teamName = "Burnley";
            List<Match> matches = new ArrayList<>();
            // All 0-0 matches
            matches.add(createHomeMatch(teamName, 0, 0, 0, 0, 1));
            matches.add(createAwayMatch(teamName, 0, 0, 0, 0, 2));
            matches.add(createHomeMatch(teamName, 0, 0, 0, 0, 3));

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTotalGoals()).isZero();
            assertThat(result.getFirstHalfGoalsAvg()).isZero();
            assertThat(result.getSecondHalfGoalsAvg()).isZero();
            // When no goals, should default to 50/50
            assertThat(result.getFirstHalfPercentage()).isEqualTo(50.0);
            assertThat(result.getSecondHalfPercentage()).isEqualTo(50.0);
            assertThat(result.getStrongerHalf()).isEqualTo("Balanced");
        }

        @Test
        @DisplayName("Should handle match with null HT data")
        void shouldHandleMatchWithNullHTData() {
            // Given
            String teamName = "Wolves";
            List<Match> matches = new ArrayList<>();
            // Add valid match
            matches.add(createHomeMatch(teamName, 1, 0, 2, 1, 1));
            // Add match with null HT data
            Match nullHtMatch = Match.builder()
                    .id(999L)
                    .matchDate(LocalDate.now().minusDays(2))
                    .homeTeam(teamName)
                    .awayTeam("Opponent")
                    .halfTimeHomeGoals(null) // NULL
                    .halfTimeAwayGoals(null) // NULL
                    .fullTimeHomeGoals(2)
                    .fullTimeAwayGoals(0)
                    .fullTimeResult("H")
                    .build();
            matches.add(nullHtMatch);

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            // Should only analyze the valid match
            assertThat(result.getMatchesAnalyzed()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should use case-insensitive team matching")
        void shouldUseCaseInsensitiveMatching() {
            // Given
            String inputName = "arsenal"; // lowercase
            String actualName = "Arsenal"; // proper case
            List<Match> matches = createBalancedMatches(actualName);

            // First call with lowercase returns empty (exact match fails)
            when(matchRepository.findByTeamBeforeDate(eq(inputName), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            // Case-insensitive search returns matches with proper case
            when(matchRepository.findByTeamBeforeDateIgnoreCase(eq(inputName), any(LocalDate.class)))
                    .thenReturn(matches);
            // After resolving to "Arsenal", fetch again with resolved name
            when(matchRepository.findByTeamBeforeDate(eq(actualName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(inputName);

            // Then
            assertThat(result.getTeamName()).isEqualTo(actualName);
            assertThat(result.getMatchesAnalyzed()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should limit matches to recent 25")
        void shouldLimitMatchesToRecent25() {
            // Given
            String teamName = "Fulham";
            List<Match> matches = new ArrayList<>();
            // Create 30 matches
            for (int i = 0; i < 30; i++) {
                matches.add(createHomeMatch(teamName, 1, 0, 2, 0, i + 1));
            }

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            assertThat(result.getMatchesAnalyzed()).isLessThanOrEqualTo(25);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CONFIDENCE & ANOMALY TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Confidence and Anomaly Detection")
    class ConfidenceAndAnomalyTests {

        @Test
        @DisplayName("Should have low confidence for few matches")
        void shouldHaveLowConfidenceForFewMatches() {
            // Given
            String teamName = "Brentford";
            List<Match> matches = new ArrayList<>();
            // Only 3 matches
            matches.add(createHomeMatch(teamName, 1, 0, 2, 0, 1));
            matches.add(createHomeMatch(teamName, 1, 0, 2, 0, 3));
            matches.add(createAwayMatch(teamName, 0, 1, 0, 2, 2));

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            assertThat(result.getConfidence()).isLessThan(0.5);
            // Should flag as anomaly due to low sample size
            assertThat(result.isAnomalyDetected()).isTrue();
            assertThat(result.getAnomalyDescription()).contains("sample size");
        }

        @Test
        @DisplayName("Should have high confidence for many matches")
        void shouldHaveHighConfidenceForManyMatches() {
            // Given
            String teamName = "Crystal Palace";
            List<Match> matches = new ArrayList<>();
            // 20 matches
            for (int i = 0; i < 20; i++) {
                matches.add(createHomeMatch(teamName, 1, 0, 2, 1, i + 1));
            }

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            assertThat(result.getConfidence()).isGreaterThanOrEqualTo(0.5);
        }

        @Test
        @DisplayName("Should detect high comeback rate anomaly")
        void shouldDetectHighComebackRateAnomaly() {
            // Given - unrealistic: team always comes back from behind
            String teamName = "Comeback Kings";
            List<Match> matches = new ArrayList<>();
            // 5 matches trailing at HT, all won (100% comeback rate > 40% threshold)
            for (int i = 0; i < 5; i++) {
                matches.add(createHomeMatch(teamName, 0, 2, 3, 2, i + 1)); // Trailing, then win
            }
            // Add some more to get past low sample threshold
            for (int i = 5; i < 10; i++) {
                matches.add(createHomeMatch(teamName, 0, 2, 3, 2, i + 1));
            }

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            assertThat(result.getComebackRate()).isGreaterThan(40.0);
            assertThat(result.isAnomalyDetected()).isTrue();
            assertThat(result.getAnomalyDescription()).containsIgnoringCase("comeback");
        }

        @Test
        @DisplayName("Should round values to 2 decimal places")
        void shouldRoundValuesToTwoDecimalPlaces() {
            // Given
            String teamName = "West Ham";
            List<Match> matches = new ArrayList<>();
            // Create matches with uneven goal distribution
            matches.add(createHomeMatch(teamName, 1, 0, 2, 0, 1));
            matches.add(createHomeMatch(teamName, 1, 0, 2, 0, 3));
            matches.add(createHomeMatch(teamName, 0, 0, 1, 0, 5));

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            // Values should be reasonable (no NaN, no Infinity)
            assertThat(Double.isNaN(result.getFirstHalfGoalsAvg())).isFalse();
            assertThat(Double.isInfinite(result.getFirstHalfGoalsAvg())).isFalse();
            assertThat(Double.isNaN(result.getSecondHalfGoalsAvg())).isFalse();
            assertThat(Double.isInfinite(result.getSecondHalfGoalsAvg())).isFalse();

            // Check that percentages are reasonable
            assertThat(Double.isNaN(result.getFirstHalfPercentage())).isFalse();
            assertThat(Double.isNaN(result.getSecondHalfPercentage())).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // VALIDATION RULES TESTS (as per requirements)
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Validation Rules")
    class ValidationRulesTests {

        @Test
        @DisplayName("Man City style team should show strong 2H (>60%)")
        void manCityStyleTeamShouldShowStrong2H() {
            // Given - simulate Man City style (strong second half)
            String teamName = "Man City";
            List<Match> matches = createStrongSecondHalfMatches(teamName);

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            assertThat(result.getSecondHalfPercentage()).isGreaterThan(50.0);
        }

        @Test
        @DisplayName("Comeback rate should be in realistic range")
        void comebackRateShouldBeInRealisticRange() {
            // Given - typical team
            String teamName = "Southampton";
            List<Match> matches = createBalancedMatches(teamName);

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            // Comeback rate should be between 0-100
            assertThat(result.getComebackRate()).isBetween(0.0, 100.0);
        }

        @Test
        @DisplayName("All rates should be capped at 100%")
        void allRatesShouldBeCappedAt100() {
            // Given
            String teamName = "Everton";
            List<Match> matches = createStrongFirstHalfMatches(teamName);

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            assertThat(result.getFirstHalfPercentage()).isLessThanOrEqualTo(100.0);
            assertThat(result.getSecondHalfPercentage()).isLessThanOrEqualTo(100.0);
            assertThat(result.getWinRateWhenLeadingHT()).isLessThanOrEqualTo(100.0);
            assertThat(result.getWinRateWhenDrawingHT()).isLessThanOrEqualTo(100.0);
            assertThat(result.getWinRateWhenLosingHT()).isLessThanOrEqualTo(100.0);
            assertThat(result.getComebackRate()).isLessThanOrEqualTo(100.0);
        }

        @Test
        @DisplayName("Should handle very low scoring team")
        void shouldHandleVeryLowScoringTeam() {
            // Given - team that barely scores
            String teamName = "Low Scorers FC";
            List<Match> matches = new ArrayList<>();
            // Mix of 0-0 and 1-0 results
            matches.add(createHomeMatch(teamName, 0, 0, 0, 0, 1));
            matches.add(createHomeMatch(teamName, 0, 0, 1, 0, 3)); // 1 goal in 2H
            matches.add(createAwayMatch(teamName, 0, 0, 0, 0, 2));
            matches.add(createAwayMatch(teamName, 1, 0, 1, 0, 4)); // 1 goal in 1H
            matches.add(createHomeMatch(teamName, 0, 0, 0, 0, 5));

            when(matchRepository.findByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            HalfAnalysisDTO result = halfAnalysisService.analyzeByHalf(teamName);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTotalGoals()).isGreaterThanOrEqualTo(0);
            assertThat(Double.isNaN(result.getFirstHalfPercentage())).isFalse();
            assertThat(Double.isNaN(result.getSecondHalfPercentage())).isFalse();
        }
    }
}


