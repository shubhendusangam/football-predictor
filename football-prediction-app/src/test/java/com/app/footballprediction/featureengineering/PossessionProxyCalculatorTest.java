package com.app.footballprediction.featureengineering;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.common.service.FeatureEngineeringService;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Possession Proxy Calculator feature.
 * Tests verify the estimatePossession method in FeatureEngineeringService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Possession Proxy Calculator Tests")
class PossessionProxyCalculatorTest {

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private FeatureEngineeringService featureEngineeringService;

    /**
     * Helper to create a match with specific shots and corners data.
     */
    private Match createMatchWithStats(String homeTeam, String awayTeam,
                                       int homeShots, int awayShots,
                                       int homeCorners, int awayCorners) {
        return Match.builder()
                .id(1L)
                .matchDate(LocalDate.now().minusDays(7))
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .fullTimeHomeGoals(2)
                .fullTimeAwayGoals(1)
                .fullTimeResult("H")
                .season("2025-26")
                .homeShots(homeShots)
                .awayShots(awayShots)
                .homeCorners(homeCorners)
                .awayCorners(awayCorners)
                .build();
    }

    @Nested
    @DisplayName("estimatePossession()")
    class EstimatePossessionTests {

        @Test
        @DisplayName("Should return 0.5 for empty match list")
        void shouldReturnDefaultForEmptyList() {
            double possession = featureEngineeringService.estimatePossession(
                    Collections.emptyList(), "Arsenal", true);

            assertThat(possession).isEqualTo(0.5);
        }

        @Test
        @DisplayName("Should return 0.5 for null match list")
        void shouldReturnDefaultForNullList() {
            double possession = featureEngineeringService.estimatePossession(
                    null, "Arsenal", true);

            assertThat(possession).isEqualTo(0.5);
        }

        @Test
        @DisplayName("Should calculate high possession for dominant team (Arsenal)")
        void shouldCalculateHighPossessionForDominantTeam() {
            // Arsenal: High shots (18) and corners (8) vs opponent (6 shots, 2 corners)
            // Expected: shotRatio = 18/24 = 0.75, cornerRatio = 8/10 = 0.80
            // Possession = 0.75 * 0.6 + 0.80 * 0.4 = 0.45 + 0.32 = 0.77
            List<Match> matches = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                matches.add(createMatchWithStats("Arsenal", "Opponent" + i, 18, 6, 8, 2));
            }

            double possession = featureEngineeringService.estimatePossession(
                    matches, "Arsenal", true);

            // Arsenal should have 60-65%+ possession proxy
            assertThat(possession).isBetween(0.60, 0.85);
        }

        @Test
        @DisplayName("Should calculate low possession for defensive team (Southampton)")
        void shouldCalculateLowPossessionForDefensiveTeam() {
            // Southampton: Low shots (7) and corners (3) vs opponent (15 shots, 7 corners)
            // Expected: shotRatio = 7/22 = 0.318, cornerRatio = 3/10 = 0.30
            // Possession = 0.318 * 0.6 + 0.30 * 0.4 = 0.19 + 0.12 = 0.31
            List<Match> matches = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                matches.add(createMatchWithStats("Opponent" + i, "Southampton", 15, 7, 7, 3));
            }

            double possession = featureEngineeringService.estimatePossession(
                    matches, "Southampton", false);

            // Southampton should have 35-45% possession proxy
            assertThat(possession).isBetween(0.25, 0.45);
        }

        @Test
        @DisplayName("Should have home + away possession approximately equal to 1.0")
        void shouldHavePossessionSumApproximatelyOne() {
            // Create matches with specific stats
            List<Match> homeMatches = new ArrayList<>();
            List<Match> awayMatches = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                // Home team stats: 14 shots, 6 corners
                homeMatches.add(createMatchWithStats("HomeTeam", "Opponent" + i, 14, 10, 6, 4));
                // Away team stats: 10 shots, 4 corners (same match from away perspective)
                awayMatches.add(createMatchWithStats("Opponent" + i, "AwayTeam", 14, 10, 6, 4));
            }

            double homePossession = featureEngineeringService.estimatePossession(
                    homeMatches, "HomeTeam", true);
            double awayPossession = featureEngineeringService.estimatePossession(
                    awayMatches, "AwayTeam", false);

            // For the same match scenario, possession should sum close to 1.0
            // Note: Different match lists so won't be exactly 1.0, but should be reasonable
            assertThat(homePossession + awayPossession).isBetween(0.85, 1.15);
        }

        @Test
        @DisplayName("Should handle matches with null shots data")
        void shouldHandleNullShotsData() {
            Match matchWithNullShots = Match.builder()
                    .id(1L)
                    .matchDate(LocalDate.now().minusDays(7))
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeResult("H")
                    .homeShots(null)
                    .awayShots(null)
                    .homeCorners(8)
                    .awayCorners(4)
                    .build();

            List<Match> matches = List.of(matchWithNullShots);

            double possession = featureEngineeringService.estimatePossession(
                    matches, "Arsenal", true);

            // Should use default 0.5 for shots and calculate corners only
            // cornerRatio = 8/12 = 0.667
            // possession = 0.5 * 0.6 + 0.667 * 0.4 = 0.3 + 0.267 = 0.567
            assertThat(possession).isBetween(0.4, 0.7);
        }

        @Test
        @DisplayName("Should handle matches with null corners data")
        void shouldHandleNullCornersData() {
            Match matchWithNullCorners = Match.builder()
                    .id(1L)
                    .matchDate(LocalDate.now().minusDays(7))
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeResult("H")
                    .homeShots(15)
                    .awayShots(8)
                    .homeCorners(null)
                    .awayCorners(null)
                    .build();

            List<Match> matches = List.of(matchWithNullCorners);

            double possession = featureEngineeringService.estimatePossession(
                    matches, "Arsenal", true);

            // Should use shots ratio only and default for corners
            // shotRatio = 15/23 = 0.652
            // possession = 0.652 * 0.6 + 0.5 * 0.4 = 0.391 + 0.2 = 0.591
            assertThat(possession).isBetween(0.5, 0.7);
        }

        @Test
        @DisplayName("Should handle zero total shots")
        void shouldHandleZeroTotalShots() {
            Match matchWithZeroShots = Match.builder()
                    .id(1L)
                    .matchDate(LocalDate.now().minusDays(7))
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeResult("D")
                    .homeShots(0)
                    .awayShots(0)
                    .homeCorners(5)
                    .awayCorners(5)
                    .build();

            List<Match> matches = List.of(matchWithZeroShots);

            double possession = featureEngineeringService.estimatePossession(
                    matches, "Arsenal", true);

            // Should use default 0.5 for shots, corners = 5/10 = 0.5
            assertThat(possession).isEqualTo(0.5);
        }

        @Test
        @DisplayName("Should calculate possession for away team correctly")
        void shouldCalculatePossessionForAwayTeam() {
            // Away team perspective: swaps home/away stats
            List<Match> matches = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                // AwayTeam plays away: opponent gets 8 shots, 4 corners; AwayTeam gets 14, 7
                matches.add(createMatchWithStats("Opponent" + i, "AwayTeam", 8, 14, 4, 7));
            }

            double possession = featureEngineeringService.estimatePossession(
                    matches, "AwayTeam", false);

            // AwayTeam: shotRatio = 14/22 = 0.636, cornerRatio = 7/11 = 0.636
            // possession = 0.636 * 0.6 + 0.636 * 0.4 = 0.382 + 0.255 = 0.636
            assertThat(possession).isBetween(0.55, 0.75);
        }

        @Test
        @DisplayName("Should clamp possession between 0 and 1")
        void shouldClampPossessionBetweenZeroAndOne() {
            // Extreme case: very dominant team
            List<Match> matches = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                matches.add(createMatchWithStats("DominantTeam", "Opponent", 30, 1, 15, 0));
            }

            double possession = featureEngineeringService.estimatePossession(
                    matches, "DominantTeam", true);

            // Should be clamped to 1.0 max
            assertThat(possession).isLessThanOrEqualTo(1.0);
            assertThat(possession).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Formula Validation")
    class FormulaValidationTests {

        @Test
        @DisplayName("Should weight shots at 60% and corners at 40%")
        void shouldApplyCorrectWeighting() {
            // Known values for exact calculation
            // Shot ratio = 0.6 (60% of shots)
            // Corner ratio = 0.7 (70% of corners)
            // Expected: 0.6 * 0.6 + 0.7 * 0.4 = 0.36 + 0.28 = 0.64

            Match match = Match.builder()
                    .id(1L)
                    .matchDate(LocalDate.now().minusDays(7))
                    .homeTeam("TestTeam")
                    .awayTeam("Opponent")
                    .fullTimeResult("H")
                    .homeShots(12) // 60% of 20 total
                    .awayShots(8)
                    .homeCorners(7) // 70% of 10 total
                    .awayCorners(3)
                    .build();

            double possession = featureEngineeringService.estimatePossession(
                    List.of(match), "TestTeam", true);

            // Expected: 0.6 * 0.6 + 0.7 * 0.4 = 0.64
            assertThat(possession).isCloseTo(0.64, org.assertj.core.api.Assertions.within(0.01));
        }
    }
}

