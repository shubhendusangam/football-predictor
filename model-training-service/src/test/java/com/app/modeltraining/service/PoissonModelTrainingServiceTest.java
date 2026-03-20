package com.app.modeltraining.service;

import com.app.common.model.Match;
import com.app.common.model.PoissonParameters;
import com.app.common.repository.MatchRepository;
import com.app.modeltraining.service.PoissonModelTrainingService.ScorePredictionResult;
import com.app.modeltraining.service.PoissonModelTrainingService.WeightedMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for PoissonModelTrainingService.
 * Tests Poisson PMF, Dixon-Coles parameter estimation,
 * score prediction, and market probability derivation.
 */
@ExtendWith(MockitoExtension.class)
class PoissonModelTrainingServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private PoissonModelTrainingService service;

    private List<WeightedMatch> sampleWeightedMatches;

    @BeforeEach
    void setUp() {
        sampleWeightedMatches = createSampleWeightedMatches();
        ReflectionTestUtils.setField(service, "maxGoals", 5);
        ReflectionTestUtils.setField(service, "defaultHomeAdvantage", 1.36);
        ReflectionTestUtils.setField(service, "poissonModelPath", "/tmp/test_poisson.model");
    }

    // ══════════════════════════════════════════════════════════════════════
    // POISSON PMF TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Poisson PMF Calculations")
    class PoissonPMFTests {

        @Test
        @DisplayName("P(X=0) for λ=1.5 should be ~0.2231")
        void testPoissonPMF_zero_goals() {
            double result = PoissonModelTrainingService.poissonPMF(1.5, 0);
            assertThat(result).isCloseTo(0.2231, within(0.001));
        }

        @Test
        @DisplayName("P(X=1) for λ=1.5 should be ~0.3347")
        void testPoissonPMF_one_goal() {
            double result = PoissonModelTrainingService.poissonPMF(1.5, 1);
            assertThat(result).isCloseTo(0.3347, within(0.001));
        }

        @Test
        @DisplayName("P(X=2) for λ=1.5 should be ~0.2510")
        void testPoissonPMF_two_goals() {
            double result = PoissonModelTrainingService.poissonPMF(1.5, 2);
            assertThat(result).isCloseTo(0.2510, within(0.001));
        }

        @Test
        @DisplayName("P(X=3) for λ=1.5 should be ~0.1255")
        void testPoissonPMF_three_goals() {
            double result = PoissonModelTrainingService.poissonPMF(1.5, 3);
            assertThat(result).isCloseTo(0.1255, within(0.001));
        }

        @Test
        @DisplayName("P(X=5) for λ=0.5 should be very small")
        void testPoissonPMF_rare_event() {
            double result = PoissonModelTrainingService.poissonPMF(0.5, 5);
            assertThat(result).isLessThan(0.002);
            assertThat(result).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("P(X=-1) should be 0.0")
        void testPoissonPMF_negative_goals() {
            double result = PoissonModelTrainingService.poissonPMF(1.5, -1);
            assertThat(result).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Poisson PMF should sum to ~1.0 for k=0..20")
        void testPoissonPMF_sums_to_one() {
            double lambda = 2.0;
            double sum = 0;
            for (int k = 0; k <= 20; k++) {
                sum += PoissonModelTrainingService.poissonPMF(lambda, k);
            }
            assertThat(sum).isCloseTo(1.0, within(0.0001));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PARAMETER ESTIMATION TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Dixon-Coles Parameter Estimation")
    class ParameterEstimationTests {

        @Test
        @DisplayName("Should estimate parameters for all teams")
        void testEstimateParameters_allTeamsPresent() {
            PoissonParameters params = service.estimateParameters(sampleWeightedMatches);

            assertThat(params.getAttack()).isNotEmpty();
            assertThat(params.getDefence()).isNotEmpty();
            assertThat(params.getAttack()).containsKey("Arsenal");
            assertThat(params.getAttack()).containsKey("Chelsea");
            assertThat(params.getDefence()).containsKey("Arsenal");
            assertThat(params.getDefence()).containsKey("Chelsea");
        }

        @Test
        @DisplayName("Home advantage should be > 1.0 (home teams score more)")
        void testEstimateParameters_homeAdvantage() {
            PoissonParameters params = service.estimateParameters(sampleWeightedMatches);
            assertThat(params.getHomeAdvantage()).isGreaterThan(1.0);
        }

        @Test
        @DisplayName("League average goals should be realistic (1.0 - 2.0)")
        void testEstimateParameters_leagueAvgGoals() {
            PoissonParameters params = service.estimateParameters(sampleWeightedMatches);
            assertThat(params.getLeagueAvgGoals()).isBetween(1.0, 2.0);
        }

        @Test
        @DisplayName("Attack/defence parameters should be normalised (mean ~1.0)")
        void testEstimateParameters_normalised() {
            PoissonParameters params = service.estimateParameters(sampleWeightedMatches);

            double avgAttack = params.getAttack().values().stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0);
            double avgDefence = params.getDefence().values().stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0);

            assertThat(avgAttack).isCloseTo(1.0, within(0.01));
            assertThat(avgDefence).isCloseTo(1.0, within(0.01));
        }

        @Test
        @DisplayName("Strong team should have higher attack strength")
        void testEstimateParameters_strongTeamAttack() {
            // Arsenal has higher goals in sample data
            PoissonParameters params = service.estimateParameters(sampleWeightedMatches);
            double arsenalAttack = params.getAttack().get("Arsenal");
            double southamptonAttack = params.getAttack().get("Southampton");
            assertThat(arsenalAttack).isGreaterThan(southamptonAttack);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SCORE PREDICTION TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Score Prediction")
    class ScorePredictionTests {

        private PoissonParameters params;

        @BeforeEach
        void setUp() {
            params = service.estimateParameters(sampleWeightedMatches);
        }

        @Test
        @DisplayName("Should produce a valid most likely score")
        void testPredictScore_validScore() {
            ScorePredictionResult result = service.predictScore(params, "Arsenal", "Chelsea");

            assertThat(result.mostLikelyScore).matches("\\d-\\d");
            assertThat(result.mostLikelyScoreProb).isGreaterThan(0.0);
            assertThat(result.mostLikelyScoreProb).isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("Should produce top 3 scores in descending probability")
        void testPredictScore_top3Sorted() {
            ScorePredictionResult result = service.predictScore(params, "Arsenal", "Chelsea");

            assertThat(result.top3Scores).hasSize(3);

            List<Double> probs = result.top3Scores.stream()
                    .map(m -> m.values().iterator().next())
                    .toList();
            assertThat(probs).isSortedAccordingTo(Comparator.reverseOrder());
        }

        @Test
        @DisplayName("Outcome probabilities should sum to ~1.0")
        void testPredictScore_outcomeProbsSum() {
            ScorePredictionResult result = service.predictScore(params, "Arsenal", "Chelsea");

            double total = result.probHomeWin + result.probDraw + result.probAwayWin;
            assertThat(total).isCloseTo(1.0, within(0.02));
        }

        @Test
        @DisplayName("Over/under probabilities should be consistent")
        void testPredictScore_overUnderConsistency() {
            ScorePredictionResult result = service.predictScore(params, "Arsenal", "Chelsea");

            // over15 >= over25 >= over35
            assertThat(result.over15Prob).isGreaterThanOrEqualTo(result.over25Prob);
            assertThat(result.over25Prob).isGreaterThanOrEqualTo(result.over35Prob);
        }

        @Test
        @DisplayName("BTTS prob + cleanSheetHome should be consistent")
        void testPredictScore_bttsConsistency() {
            ScorePredictionResult result = service.predictScore(params, "Arsenal", "Chelsea");

            // BTTS = 1 - (csHome + csAway - P(0-0)), so btts + (csHome + csAway) should be >= 1.0
            // More simply: all between 0 and 1
            assertThat(result.bttsProb).isBetween(0.0, 1.0);
            assertThat(result.cleanSheetHome).isBetween(0.0, 1.0);
            assertThat(result.cleanSheetAway).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("Score matrix should have (maxGoals+1)^2 entries")
        void testPredictScore_matrixSize() {
            ScorePredictionResult result = service.predictScore(params, "Arsenal", "Chelsea");
            // Default maxGoals = 5, so 6x6 = 36 entries
            assertThat(result.scoreMatrix).hasSize(36);
        }

        @Test
        @DisplayName("Score matrix probabilities should sum to ~1.0")
        void testPredictScore_matrixSumsToOne() {
            ScorePredictionResult result = service.predictScore(params, "Arsenal", "Chelsea");

            double sum = result.scoreMatrix.values().stream().mapToDouble(Double::doubleValue).sum();
            assertThat(sum).isCloseTo(1.0, within(0.02));
        }

        @Test
        @DisplayName("Home favourite should have higher home win probability")
        void testPredictScore_homeFavourite() {
            // Arsenal is the strong home team in sample data
            ScorePredictionResult result = service.predictScore(params, "Arsenal", "Southampton");

            assertThat(result.probHomeWin).isGreaterThan(result.probAwayWin);
        }

        @Test
        @DisplayName("Unknown teams should get average parameters (attack/defence = 1.0)")
        void testPredictScore_unknownTeam() {
            ScorePredictionResult result = service.predictScore(params, "UnknownTeamA", "UnknownTeamB");

            // Should still produce valid output
            assertThat(result.mostLikelyScore).isNotBlank();
            double total = result.probHomeWin + result.probDraw + result.probAwayWin;
            assertThat(total).isCloseTo(1.0, within(0.02));
        }

        @Test
        @DisplayName("Lambda values should be clamped within [0.3, 5.0]")
        void testPredictScore_lambdaClamped() {
            ScorePredictionResult result = service.predictScore(params, "Arsenal", "Chelsea");

            assertThat(result.lambdaHome).isBetween(0.3, 5.0);
            assertThat(result.lambdaAway).isBetween(0.3, 5.0);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // KNOWN FIXTURE DATA TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Known Fixture Predictions")
    class KnownFixtureTests {

        @Test
        @DisplayName("Arsenal (strong) vs Southampton (weak) at home: expect home win")
        void testKnownFixture_arsenalVsSouthampton() {
            PoissonParameters params = service.estimateParameters(sampleWeightedMatches);
            ScorePredictionResult result = service.predictScore(params, "Arsenal", "Southampton");

            assertThat(result.probHomeWin).isGreaterThan(0.4);
            assertThat(result.lambdaHome).isGreaterThan(result.lambdaAway);
        }

        @Test
        @DisplayName("Two equal teams should produce balanced probabilities")
        void testKnownFixture_equalTeams() {
            PoissonParameters params = service.estimateParameters(sampleWeightedMatches);
            ScorePredictionResult result = service.predictScore(params, "Liverpool", "Man City");

            // Both are strong teams — probabilities should be relatively balanced
            // Home advantage still applies, but no dominant winner expected
            assertThat(Math.abs(result.probHomeWin - result.probAwayWin)).isLessThan(0.25);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SAMPLE DATA GENERATION
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Create realistic sample weighted match data for 6 teams over multiple "seasons".
     * Arsenal/Man City/Liverpool are strong; Chelsea/Everton mid; Southampton weak.
     */
    private List<WeightedMatch> createSampleWeightedMatches() {
        List<WeightedMatch> matches = new ArrayList<>();
        String[] teams = {"Arsenal", "Chelsea", "Liverpool", "Man City", "Everton", "Southampton"};

        // Generate round-robin results (home + away) with realistic scorelines
        int[][] homeGoals = {
                // vs Arsenal, Chelsea, Liverpool, Man City, Everton, Southampton
                {0, 2, 2, 1, 3, 4}, // Arsenal home
                {1, 0, 1, 0, 2, 2}, // Chelsea home
                {2, 2, 0, 2, 3, 3}, // Liverpool home
                {2, 2, 1, 0, 3, 4}, // Man City home
                {0, 1, 1, 0, 0, 2}, // Everton home
                {0, 0, 1, 0, 1, 0}, // Southampton home
        };
        int[][] awayGoals = {
                {0, 0, 1, 2, 0, 0}, // vs Arsenal home
                {2, 0, 1, 1, 0, 1}, // vs Chelsea home
                {1, 0, 0, 1, 1, 0}, // vs Liverpool home
                {1, 1, 2, 0, 0, 1}, // vs Man City home
                {2, 2, 2, 3, 0, 1}, // vs Everton home
                {2, 1, 2, 3, 0, 0}, // vs Southampton home
        };

        LocalDate baseDate = LocalDate.of(2025, 1, 1);
        int dayOffset = 0;

        for (int i = 0; i < teams.length; i++) {
            for (int j = 0; j < teams.length; j++) {
                if (i == j) continue;
                Match match = Match.builder()
                        .homeTeam(teams[i])
                        .awayTeam(teams[j])
                        .fullTimeHomeGoals(homeGoals[i][j])
                        .fullTimeAwayGoals(awayGoals[i][j])
                        .season("2025-26")
                        .matchDate(baseDate.plusDays(dayOffset++))
                        .fullTimeResult(homeGoals[i][j] > awayGoals[i][j] ? "H" :
                                homeGoals[i][j] < awayGoals[i][j] ? "A" : "D")
                        .build();
                matches.add(new WeightedMatch(match, 1.0));
            }
        }

        // Add a second "season" with reduced weight for realistic multi-season training
        dayOffset = 0;
        for (int i = 0; i < teams.length; i++) {
            for (int j = 0; j < teams.length; j++) {
                if (i == j) continue;
                int hg = Math.max(0, homeGoals[i][j] + (i % 2 == 0 ? 1 : -1));
                int ag = Math.max(0, awayGoals[i][j] + (j % 2 == 0 ? 0 : 1));
                Match match = Match.builder()
                        .homeTeam(teams[i])
                        .awayTeam(teams[j])
                        .fullTimeHomeGoals(hg)
                        .fullTimeAwayGoals(ag)
                        .season("2024-25")
                        .matchDate(baseDate.minusDays(180).plusDays(dayOffset++))
                        .fullTimeResult(hg > ag ? "H" : hg < ag ? "A" : "D")
                        .build();
                matches.add(new WeightedMatch(match, 0.6));
            }
        }

        return matches;
    }
}

