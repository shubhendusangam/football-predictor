package com.app.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for EloRatingService.
 * Tests the Elo rating formula implementation:
 * - Expected Score = 1 / (1 + 10^((opponentElo - teamElo) / 400))
 * - New Rating = Old Rating + K * (Actual - Expected)
 * - K = 20
 */
@DisplayName("EloRatingService Unit Tests")
class EloRatingServiceTest {

    private EloRatingService eloRatingService;

    @BeforeEach
    void setUp() {
        eloRatingService = new EloRatingService();
    }

    @Nested
    @DisplayName("Expected Score Calculation")
    class ExpectedScoreTests {

        @Test
        @DisplayName("should return 0.5 for equal ratings")
        void equalRatings_returns50Percent() {
            // Given
            double teamElo = 1500.0;
            double opponentElo = 1500.0;

            // When
            double expectedScore = eloRatingService.calculateExpectedScore(teamElo, opponentElo);

            // Then
            assertThat(expectedScore).isEqualTo(0.5, within(0.0001));
        }

        @Test
        @DisplayName("should return ~0.64 when team is 100 points higher")
        void teamHigherBy100_returns64Percent() {
            // Given
            double teamElo = 1600.0;
            double opponentElo = 1500.0;

            // When
            double expectedScore = eloRatingService.calculateExpectedScore(teamElo, opponentElo);

            // Then
            // E = 1 / (1 + 10^(-100/400)) = 1 / (1 + 10^-0.25) ≈ 0.64
            assertThat(expectedScore).isCloseTo(0.64, within(0.01));
        }

        @Test
        @DisplayName("should return ~0.36 when team is 100 points lower")
        void teamLowerBy100_returns36Percent() {
            // Given
            double teamElo = 1400.0;
            double opponentElo = 1500.0;

            // When
            double expectedScore = eloRatingService.calculateExpectedScore(teamElo, opponentElo);

            // Then
            // E = 1 / (1 + 10^(100/400)) = 1 / (1 + 10^0.25) ≈ 0.36
            assertThat(expectedScore).isCloseTo(0.36, within(0.01));
        }

        @Test
        @DisplayName("should return ~0.76 when team is 200 points higher")
        void teamHigherBy200_returns76Percent() {
            // Given
            double teamElo = 1700.0;
            double opponentElo = 1500.0;

            // When
            double expectedScore = eloRatingService.calculateExpectedScore(teamElo, opponentElo);

            // Then
            // E = 1 / (1 + 10^(-200/400)) = 1 / (1 + 10^-0.5) ≈ 0.76
            assertThat(expectedScore).isCloseTo(0.76, within(0.01));
        }

        @Test
        @DisplayName("should return ~0.91 when team is 400 points higher")
        void teamHigherBy400_returns91Percent() {
            // Given
            double teamElo = 1900.0;
            double opponentElo = 1500.0;

            // When
            double expectedScore = eloRatingService.calculateExpectedScore(teamElo, opponentElo);

            // Then
            // E = 1 / (1 + 10^(-400/400)) = 1 / (1 + 0.1) ≈ 0.909
            assertThat(expectedScore).isCloseTo(0.909, within(0.01));
        }

        @Test
        @DisplayName("expected scores of two opponents should sum to 1")
        void twoOpponents_expectedScoresSumToOne() {
            // Given
            double teamAElo = 1600.0;
            double teamBElo = 1450.0;

            // When
            double expectedA = eloRatingService.calculateExpectedScore(teamAElo, teamBElo);
            double expectedB = eloRatingService.calculateExpectedScore(teamBElo, teamAElo);

            // Then
            assertThat(expectedA + expectedB).isEqualTo(1.0, within(0.0001));
        }
    }

    @Nested
    @DisplayName("New Rating Calculation")
    class NewRatingTests {

        @Test
        @DisplayName("should increase rating for win against equal opponent")
        void winAgainstEqual_increasesRating() {
            // Given
            double teamElo = 1500.0;
            double opponentElo = 1500.0;
            double actualScore = EloRatingService.RESULT_WIN; // 1.0

            // When
            double newRating = eloRatingService.calculateNewRatingFromMatch(teamElo, opponentElo, actualScore);

            // Then
            // New = 1500 + 20 * (1.0 - 0.5) = 1500 + 10 = 1510
            assertThat(newRating).isEqualTo(1510.0, within(0.01));
        }

        @Test
        @DisplayName("should decrease rating for loss against equal opponent")
        void lossAgainstEqual_decreasesRating() {
            // Given
            double teamElo = 1500.0;
            double opponentElo = 1500.0;
            double actualScore = EloRatingService.RESULT_LOSS; // 0.0

            // When
            double newRating = eloRatingService.calculateNewRatingFromMatch(teamElo, opponentElo, actualScore);

            // Then
            // New = 1500 + 20 * (0.0 - 0.5) = 1500 - 10 = 1490
            assertThat(newRating).isEqualTo(1490.0, within(0.01));
        }

        @Test
        @DisplayName("should not change rating for draw against equal opponent")
        void drawAgainstEqual_noChange() {
            // Given
            double teamElo = 1500.0;
            double opponentElo = 1500.0;
            double actualScore = EloRatingService.RESULT_DRAW; // 0.5

            // When
            double newRating = eloRatingService.calculateNewRatingFromMatch(teamElo, opponentElo, actualScore);

            // Then
            // New = 1500 + 20 * (0.5 - 0.5) = 1500
            assertThat(newRating).isEqualTo(1500.0, within(0.01));
        }

        @Test
        @DisplayName("should gain less for beating weaker opponent")
        void winAgainstWeaker_smallerGain() {
            // Given
            double strongTeamElo = 1700.0;
            double weakTeamElo = 1400.0;
            double actualScore = EloRatingService.RESULT_WIN;

            // When
            double newRating = eloRatingService.calculateNewRatingFromMatch(strongTeamElo, weakTeamElo, actualScore);

            // Then
            // Expected ≈ 0.85, so gain ≈ 20 * (1 - 0.85) = 3
            double gain = newRating - strongTeamElo;
            assertThat(gain).isLessThan(5.0);
            assertThat(gain).isPositive();
        }

        @Test
        @DisplayName("should gain more for beating stronger opponent")
        void winAgainstStronger_largerGain() {
            // Given
            double weakTeamElo = 1400.0;
            double strongTeamElo = 1700.0;
            double actualScore = EloRatingService.RESULT_WIN;

            // When
            double newRating = eloRatingService.calculateNewRatingFromMatch(weakTeamElo, strongTeamElo, actualScore);

            // Then
            // Expected ≈ 0.15, so gain ≈ 20 * (1 - 0.15) = 17
            double gain = newRating - weakTeamElo;
            assertThat(gain).isGreaterThan(15.0);
        }

        @Test
        @DisplayName("should lose less when losing to stronger opponent")
        void lossAgainstStronger_smallerLoss() {
            // Given
            double weakTeamElo = 1400.0;
            double strongTeamElo = 1700.0;
            double actualScore = EloRatingService.RESULT_LOSS;

            // When
            double newRating = eloRatingService.calculateNewRatingFromMatch(weakTeamElo, strongTeamElo, actualScore);

            // Then
            // Expected ≈ 0.15, so loss ≈ 20 * (0 - 0.15) = -3
            double loss = weakTeamElo - newRating;
            assertThat(loss).isLessThan(5.0);
            assertThat(loss).isPositive();
        }

        @Test
        @DisplayName("should lose more when losing to weaker opponent")
        void lossAgainstWeaker_largerLoss() {
            // Given
            double strongTeamElo = 1700.0;
            double weakTeamElo = 1400.0;
            double actualScore = EloRatingService.RESULT_LOSS;

            // When
            double newRating = eloRatingService.calculateNewRatingFromMatch(strongTeamElo, weakTeamElo, actualScore);

            // Then
            // Expected ≈ 0.85, so loss ≈ 20 * (0 - 0.85) = -17
            double loss = strongTeamElo - newRating;
            assertThat(loss).isGreaterThan(15.0);
        }
    }

    @Nested
    @DisplayName("Match Rating Calculation")
    class MatchRatingTests {

        @Test
        @DisplayName("should correctly update both teams after home win")
        void homeWin_updatesCorrectly() {
            // Given
            double homeElo = 1500.0;
            double awayElo = 1500.0;
            int homeGoals = 2;
            int awayGoals = 1;

            // When
            double[] newRatings = eloRatingService.calculateMatchRatings(homeElo, awayElo, homeGoals, awayGoals);

            // Then
            assertThat(newRatings[0]).isEqualTo(1510.0, within(0.01)); // Home gained 10
            assertThat(newRatings[1]).isEqualTo(1490.0, within(0.01)); // Away lost 10
        }

        @Test
        @DisplayName("should correctly update both teams after away win")
        void awayWin_updatesCorrectly() {
            // Given
            double homeElo = 1500.0;
            double awayElo = 1500.0;
            int homeGoals = 0;
            int awayGoals = 2;

            // When
            double[] newRatings = eloRatingService.calculateMatchRatings(homeElo, awayElo, homeGoals, awayGoals);

            // Then
            assertThat(newRatings[0]).isEqualTo(1490.0, within(0.01)); // Home lost 10
            assertThat(newRatings[1]).isEqualTo(1510.0, within(0.01)); // Away gained 10
        }

        @Test
        @DisplayName("should correctly update both teams after draw")
        void draw_updatesCorrectly() {
            // Given
            double homeElo = 1500.0;
            double awayElo = 1500.0;
            int homeGoals = 1;
            int awayGoals = 1;

            // When
            double[] newRatings = eloRatingService.calculateMatchRatings(homeElo, awayElo, homeGoals, awayGoals);

            // Then
            assertThat(newRatings[0]).isEqualTo(1500.0, within(0.01)); // No change
            assertThat(newRatings[1]).isEqualTo(1500.0, within(0.01)); // No change
        }

        @Test
        @DisplayName("rating changes should be zero-sum")
        void ratingChanges_areZeroSum() {
            // Given
            double homeElo = 1600.0;
            double awayElo = 1450.0;
            int homeGoals = 2;
            int awayGoals = 0;

            // When
            double[] newRatings = eloRatingService.calculateMatchRatings(homeElo, awayElo, homeGoals, awayGoals);

            // Then
            double homeChange = newRatings[0] - homeElo;
            double awayChange = newRatings[1] - awayElo;
            assertThat(homeChange + awayChange).isEqualTo(0.0, within(0.0001));
        }
    }

    @Nested
    @DisplayName("Actual Score Calculation")
    class ActualScoreTests {

        @Test
        @DisplayName("should return 1.0 for win")
        void win_returns1() {
            double actual = eloRatingService.getActualScore(3, 1);
            assertThat(actual).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should return 0.5 for draw")
        void draw_returnsHalf() {
            double actual = eloRatingService.getActualScore(2, 2);
            assertThat(actual).isEqualTo(0.5);
        }

        @Test
        @DisplayName("should return 0.0 for loss")
        void loss_returns0() {
            double actual = eloRatingService.getActualScore(0, 2);
            assertThat(actual).isEqualTo(0.0);
        }

        @Test
        @DisplayName("should return 0.5 for 0-0 draw")
        void zeroZero_returnsHalf() {
            double actual = eloRatingService.getActualScore(0, 0);
            assertThat(actual).isEqualTo(0.5);
        }
    }

    @Nested
    @DisplayName("Rating Change Calculation")
    class RatingChangeTests {

        @Test
        @DisplayName("should return positive change for unexpected win")
        void unexpectedWin_positiveChange() {
            // Given - weak team beats strong team
            double weakElo = 1400.0;
            double strongElo = 1700.0;

            // When
            double change = eloRatingService.calculateRatingChange(weakElo, strongElo, 1.0);

            // Then
            assertThat(change).isGreaterThan(15.0); // Big gain for upset
        }

        @Test
        @DisplayName("should return negative change for unexpected loss")
        void unexpectedLoss_negativeChange() {
            // Given - strong team loses to weak team
            double strongElo = 1700.0;
            double weakElo = 1400.0;

            // When
            double change = eloRatingService.calculateRatingChange(strongElo, weakElo, 0.0);

            // Then
            assertThat(change).isLessThan(-15.0); // Big loss for upset
        }
    }

    @Nested
    @DisplayName("Win Probability Calculation")
    class WinProbabilityTests {

        @Test
        @DisplayName("should return 50% for equal teams")
        void equalTeams_50PercentChance() {
            double probability = eloRatingService.calculateWinProbability(1500, 1500);
            assertThat(probability).isEqualTo(0.5, within(0.0001));
        }

        @Test
        @DisplayName("should return higher probability for stronger team")
        void strongerTeam_higherChance() {
            double probability = eloRatingService.calculateWinProbability(1700, 1500);
            assertThat(probability).isGreaterThan(0.7);
        }
    }

    @Nested
    @DisplayName("Rating Tier Classification")
    class RatingTierTests {

        @Test
        @DisplayName("should classify 1750+ as Elite")
        void elite_tier() {
            assertThat(eloRatingService.getRatingTier(1850)).isEqualTo("Elite");
            assertThat(eloRatingService.getRatingTier(1750)).isEqualTo("Elite");
        }

        @Test
        @DisplayName("should classify 1600-1749 as Strong")
        void strong_tier() {
            assertThat(eloRatingService.getRatingTier(1749)).isEqualTo("Strong");
            assertThat(eloRatingService.getRatingTier(1600)).isEqualTo("Strong");
        }

        @Test
        @DisplayName("should classify 1450-1599 as Competitive")
        void competitive_tier() {
            assertThat(eloRatingService.getRatingTier(1599)).isEqualTo("Competitive");
            assertThat(eloRatingService.getRatingTier(1450)).isEqualTo("Competitive");
        }

        @Test
        @DisplayName("should classify below 1450 as Weak")
        void weak_tier() {
            assertThat(eloRatingService.getRatingTier(1449)).isEqualTo("Weak");
            assertThat(eloRatingService.getRatingTier(1300)).isEqualTo("Weak");
            assertThat(eloRatingService.getRatingTier(1200)).isEqualTo("Weak");
        }
    }

    @Nested
    @DisplayName("K-Factor Configuration")
    class KFactorTests {

        @Test
        @DisplayName("should use K=20")
        void kFactor_is20() {
            assertThat(EloRatingService.K_FACTOR).isEqualTo(20);
        }

        @Test
        @DisplayName("maximum rating change per match should be K")
        void maxChange_isK() {
            // When strong team loses to much weaker team (expected ~0, actual 0)
            double change = eloRatingService.calculateRatingChange(1900, 1100, 0.0);

            // Max loss is when expected is ~1.0 and actual is 0
            // Change = K * (0 - ~1) = ~-20
            assertThat(Math.abs(change)).isLessThanOrEqualTo(20.0);
        }
    }
}

