package com.app.footballprediction.service;

import com.app.common.model.MatchFeatures;
import com.app.common.model.SeasonTeamStats;
import com.app.common.repository.SeasonTeamStatsRepository;
import com.app.footballprediction.dto.PredictionExplanation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EloPredictionService.
 * Tests Elo-based prediction adjustments and explainability.
 */
@ExtendWith(MockitoExtension.class)
class EloPredictionServiceTest {

    @Mock
    private SeasonTeamStatsRepository seasonTeamStatsRepository;

    @InjectMocks
    private EloPredictionService eloPredictionService;

    private static final String SEASON = "2025-26";
    private static final String HOME_TEAM = "Arsenal";
    private static final String AWAY_TEAM = "Southampton";

    private MatchFeatures createDefaultFeatures() {
        return MatchFeatures.builder()
                .homeFormPoints(0.7)
                .awayFormPoints(0.5)
                .homeGoalsScoredAvg(2.0)
                .awayGoalsScoredAvg(1.5)
                .homeGoalsConcededAvg(0.8)
                .awayGoalsConcededAvg(1.5)
                .build();
    }

    private SeasonTeamStats createTeamStats(String teamName, double eloRating) {
        return SeasonTeamStats.builder()
                .seasonId(SEASON)
                .teamName(teamName)
                .eloRating(eloRating)
                .formPointsLast5(10)
                .goalsScored(25)
                .goalsConceded(15)
                .build();
    }

    /**
     * Helper to mock batch query for two teams.
     */
    private void mockBatchEloQuery(double homeElo, double awayElo) {
        List<SeasonTeamStats> stats = List.of(
                createTeamStats(HOME_TEAM, homeElo),
                createTeamStats(AWAY_TEAM, awayElo)
        );
        when(seasonTeamStatsRepository.findBySeasonIdAndTeamNames(eq(SEASON), anyList()))
                .thenReturn(stats);
    }

    /**
     * Helper to mock batch query with only home team.
     */
    private void mockBatchEloQueryHomeOnly(double homeElo) {
        List<SeasonTeamStats> stats = List.of(createTeamStats(HOME_TEAM, homeElo));
        when(seasonTeamStatsRepository.findBySeasonIdAndTeamNames(eq(SEASON), anyList()))
                .thenReturn(stats);
    }

    /**
     * Helper to mock batch query returning empty (no stats found).
     */
    private void mockBatchEloQueryEmpty() {
        when(seasonTeamStatsRepository.findBySeasonIdAndTeamNames(anyString(), anyList()))
                .thenReturn(Collections.emptyList());
    }

    @Nested
    @DisplayName("Equal Elo Tests")
    class EqualEloTests {

        @Test
        @DisplayName("Should return similar probabilities when Elo ratings are equal")
        void equalElo_ShouldReturnSimilarProbabilities() {
            // Given
            mockBatchEloQuery(1500.0, 1500.0);

            double[] baseProbabilities = {0.40, 0.30, 0.30};
            MatchFeatures features = createDefaultFeatures();

            // When
            EloPredictionService.EloPredictionResult result = eloPredictionService
                    .calculateEloPrediction(HOME_TEAM, AWAY_TEAM, SEASON, baseProbabilities, features);

            // Then
            assertThat(result.getEloDifference()).isEqualTo(0.0);
            assertThat(result.isUpsetAlert()).isFalse();
            // Probabilities should sum to 1.0
            double total = result.getHomeWinProbability() + result.getDrawProbability() + result.getAwayWinProbability();
            assertThat(total).isCloseTo(1.0, org.assertj.core.api.Assertions.within(0.01));
        }

        @Test
        @DisplayName("Should have no upset alert when Elo ratings are equal")
        void equalElo_ShouldHaveNoUpsetAlert() {
            // Given
            mockBatchEloQuery(1500.0, 1500.0);

            double[] baseProbabilities = {0.35, 0.30, 0.35};
            MatchFeatures features = createDefaultFeatures();

            // When
            EloPredictionService.EloPredictionResult result = eloPredictionService
                    .calculateEloPrediction(HOME_TEAM, AWAY_TEAM, SEASON, baseProbabilities, features);

            // Then
            assertThat(result.isUpsetAlert()).isFalse();
            assertThat(result.getUpsetTeam()).isNull();
        }
    }

    @Nested
    @DisplayName("Large Positive Elo Difference Tests")
    class LargePositiveEloDifferenceTests {

        @Test
        @DisplayName("Should increase home win probability when home team has 100+ Elo advantage")
        void largePositiveEloDiff_ShouldIncreaseHomeWinProbability() {
            // Given - Home team has 150 point Elo advantage
            mockBatchEloQuery(1650.0, 1500.0);

            double[] baseProbabilities = {0.40, 0.30, 0.30};
            MatchFeatures features = createDefaultFeatures();

            // When
            EloPredictionService.EloPredictionResult result = eloPredictionService
                    .calculateEloPrediction(HOME_TEAM, AWAY_TEAM, SEASON, baseProbabilities, features);

            // Then
            assertThat(result.getEloDifference()).isEqualTo(150.0);
            assertThat(result.getHomeWinProbability()).isGreaterThan(0.40);
            assertThat(result.getAwayWinProbability()).isLessThan(0.30);
        }

        @Test
        @DisplayName("Should have no upset alert when higher Elo team is favored")
        void largePositiveEloDiff_ShouldHaveNoUpsetWhenHigherEloFavored() {
            // Given
            mockBatchEloQuery(1700.0, 1500.0);

            double[] baseProbabilities = {0.50, 0.25, 0.25};
            MatchFeatures features = createDefaultFeatures();

            // When
            EloPredictionService.EloPredictionResult result = eloPredictionService
                    .calculateEloPrediction(HOME_TEAM, AWAY_TEAM, SEASON, baseProbabilities, features);

            // Then
            assertThat(result.isUpsetAlert()).isFalse();
        }
    }

    @Nested
    @DisplayName("Large Negative Elo Difference Tests")
    class LargeNegativeEloDifferenceTests {

        @Test
        @DisplayName("Should increase away win probability when away team has 100+ Elo advantage")
        void largeNegativeEloDiff_ShouldIncreaseAwayWinProbability() {
            // Given - Away team has 150 point Elo advantage
            mockBatchEloQuery(1400.0, 1550.0);

            double[] baseProbabilities = {0.35, 0.30, 0.35};
            MatchFeatures features = createDefaultFeatures();

            // When
            EloPredictionService.EloPredictionResult result = eloPredictionService
                    .calculateEloPrediction(HOME_TEAM, AWAY_TEAM, SEASON, baseProbabilities, features);

            // Then
            assertThat(result.getEloDifference()).isEqualTo(-150.0);
            assertThat(result.getAwayWinProbability()).isGreaterThan(0.35);
            assertThat(result.getHomeWinProbability()).isLessThan(0.35);
        }

        @Test
        @DisplayName("Should detect upset when home team has high win probability despite lower Elo")
        void largeNegativeEloDiff_ShouldDetectUpsetForHomeTeam() {
            // Given - Away team has higher Elo but home team predicted to win
            mockBatchEloQuery(1400.0, 1600.0);

            // High home win probability despite lower Elo
            double[] baseProbabilities = {0.55, 0.25, 0.20};
            MatchFeatures features = createDefaultFeatures();

            // When
            EloPredictionService.EloPredictionResult result = eloPredictionService
                    .calculateEloPrediction(HOME_TEAM, AWAY_TEAM, SEASON, baseProbabilities, features);

            // Then
            assertThat(result.isUpsetAlert()).isTrue();
            assertThat(result.getUpsetTeam()).isEqualTo(HOME_TEAM);
        }
    }

    @Nested
    @DisplayName("Draw Scenario Tests")
    class DrawScenarioTests {

        @Test
        @DisplayName("Should maintain draw probability within reasonable bounds")
        void drawScenario_ShouldMaintainReasonableDrawProbability() {
            // Given
            mockBatchEloQuery(1520.0, 1480.0);

            // High draw probability base
            double[] baseProbabilities = {0.30, 0.45, 0.25};
            MatchFeatures features = createDefaultFeatures();

            // When
            EloPredictionService.EloPredictionResult result = eloPredictionService
                    .calculateEloPrediction(HOME_TEAM, AWAY_TEAM, SEASON, baseProbabilities, features);

            // Then
            assertThat(result.getDrawProbability()).isGreaterThan(0.0);
            assertThat(result.getDrawProbability()).isLessThan(1.0);
        }
    }

    @Nested
    @DisplayName("Edge Case - Null Elo Tests")
    class NullEloTests {

        @Test
        @DisplayName("Should use default Elo when team stats not found")
        void nullElo_ShouldUseDefaultElo() {
            // Given - No stats found for either team
            mockBatchEloQueryEmpty();

            double[] baseProbabilities = {0.40, 0.30, 0.30};
            MatchFeatures features = createDefaultFeatures();

            // When
            EloPredictionService.EloPredictionResult result = eloPredictionService
                    .calculateEloPrediction(HOME_TEAM, AWAY_TEAM, SEASON, baseProbabilities, features);

            // Then
            assertThat(result.getHomeElo()).isEqualTo(1500.0); // Default Elo
            assertThat(result.getAwayElo()).isEqualTo(1500.0);
            assertThat(result.getEloDifference()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should use default Elo when season is null")
        void nullSeason_ShouldUseDefaultElo() {
            // Given
            double[] baseProbabilities = {0.40, 0.30, 0.30};
            MatchFeatures features = createDefaultFeatures();

            // When
            EloPredictionService.EloPredictionResult result = eloPredictionService
                    .calculateEloPrediction(HOME_TEAM, AWAY_TEAM, null, baseProbabilities, features);

            // Then
            assertThat(result.getHomeElo()).isEqualTo(1500.0);
            assertThat(result.getAwayElo()).isEqualTo(1500.0);
        }

        @Test
        @DisplayName("Should handle one team with stats and one without")
        void mixedEloStats_ShouldHandleGracefully() {
            // Given - Only home team has stats
            mockBatchEloQueryHomeOnly(1600.0);

            double[] baseProbabilities = {0.40, 0.30, 0.30};
            MatchFeatures features = createDefaultFeatures();

            // When
            EloPredictionService.EloPredictionResult result = eloPredictionService
                    .calculateEloPrediction(HOME_TEAM, AWAY_TEAM, SEASON, baseProbabilities, features);

            // Then
            assertThat(result.getHomeElo()).isEqualTo(1600.0);
            assertThat(result.getAwayElo()).isEqualTo(1500.0); // Default
            assertThat(result.getEloDifference()).isEqualTo(100.0);
        }
    }

    @Nested
    @DisplayName("Probability Normalization Tests")
    class ProbabilityNormalizationTests {

        @Test
        @DisplayName("Should always normalize probabilities to sum to 1.0")
        void shouldNormalizeProbabilities() {
            // Given
            mockBatchEloQuery(1800.0, 1400.0);

            double[] baseProbabilities = {0.60, 0.25, 0.15};
            MatchFeatures features = createDefaultFeatures();

            // When
            EloPredictionService.EloPredictionResult result = eloPredictionService
                    .calculateEloPrediction(HOME_TEAM, AWAY_TEAM, SEASON, baseProbabilities, features);

            // Then
            double total = result.getHomeWinProbability() +
                          result.getDrawProbability() +
                          result.getAwayWinProbability();
            assertThat(total).isCloseTo(1.0, org.assertj.core.api.Assertions.within(0.001));
        }

        @Test
        @DisplayName("Should ensure no probability is negative")
        void shouldEnsureNoNegativeProbabilities() {
            // Given - Extreme Elo difference
            mockBatchEloQuery(2000.0, 1200.0);

            double[] baseProbabilities = {0.70, 0.20, 0.10};
            MatchFeatures features = createDefaultFeatures();

            // When
            EloPredictionService.EloPredictionResult result = eloPredictionService
                    .calculateEloPrediction(HOME_TEAM, AWAY_TEAM, SEASON, baseProbabilities, features);

            // Then
            assertThat(result.getHomeWinProbability()).isGreaterThan(0.0);
            assertThat(result.getDrawProbability()).isGreaterThan(0.0);
            assertThat(result.getAwayWinProbability()).isGreaterThan(0.0);
        }
    }

    @Nested
    @DisplayName("Explanation Tests")
    class ExplanationTests {

        @Test
        @DisplayName("Should generate explanation with all impact fields")
        void shouldGenerateCompleteExplanation() {
            // Given
            mockBatchEloQuery(1600.0, 1450.0);

            double[] baseProbabilities = {0.40, 0.30, 0.30};
            MatchFeatures features = createDefaultFeatures();

            // When
            EloPredictionService.EloPredictionResult result = eloPredictionService
                    .calculateEloPrediction(HOME_TEAM, AWAY_TEAM, SEASON, baseProbabilities, features);

            // Then
            PredictionExplanation explanation = result.getExplanation();
            assertThat(explanation).isNotNull();
            assertThat(explanation.getEloImpact()).isNotNull();
            assertThat(explanation.getFormImpact()).isNotNull();
            assertThat(explanation.getGoalTrendImpact()).isNotNull();
            assertThat(explanation.getSummary()).isNotNull();
            assertThat(explanation.getSummary()).isNotEmpty();
        }

        @Test
        @DisplayName("Should mention significant Elo advantage in summary")
        void shouldMentionSignificantEloAdvantage() {
            // Given - Large Elo difference
            mockBatchEloQuery(1700.0, 1450.0);

            double[] baseProbabilities = {0.40, 0.30, 0.30};
            MatchFeatures features = createDefaultFeatures();

            // When
            EloPredictionService.EloPredictionResult result = eloPredictionService
                    .calculateEloPrediction(HOME_TEAM, AWAY_TEAM, SEASON, baseProbabilities, features);

            // Then
            assertThat(result.getExplanation().getSummary())
                    .contains(HOME_TEAM)
                    .containsIgnoringCase("elo");
        }
    }

    @Nested
    @DisplayName("Upset Detection Tests")
    class UpsetDetectionTests {

        @Test
        @DisplayName("Should detect upset when lower Elo away team has >40% win probability")
        void shouldDetectAwayTeamUpset() {
            // Given
            mockBatchEloQuery(1600.0, 1450.0);

            // Away team has high probability despite lower Elo
            double[] baseProbabilities = {0.25, 0.25, 0.50};
            MatchFeatures features = createDefaultFeatures();

            // When
            EloPredictionService.EloPredictionResult result = eloPredictionService
                    .calculateEloPrediction(HOME_TEAM, AWAY_TEAM, SEASON, baseProbabilities, features);

            // Then
            assertThat(result.isUpsetAlert()).isTrue();
            assertThat(result.getUpsetTeam()).isEqualTo(AWAY_TEAM);
        }

        @Test
        @DisplayName("Should not flag upset when probability is below threshold")
        void shouldNotFlagUpsetBelowThreshold() {
            // Given
            mockBatchEloQuery(1600.0, 1450.0);

            // Away team has low probability
            double[] baseProbabilities = {0.50, 0.30, 0.20};
            MatchFeatures features = createDefaultFeatures();

            // When
            EloPredictionService.EloPredictionResult result = eloPredictionService
                    .calculateEloPrediction(HOME_TEAM, AWAY_TEAM, SEASON, baseProbabilities, features);

            // Then
            assertThat(result.isUpsetAlert()).isFalse();
        }
    }
}

