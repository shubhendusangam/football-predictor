package com.app.footballprediction.service;

import com.app.common.model.PoissonParameters;
import com.app.footballprediction.dto.ScorePredictionDTO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ScorePredictionService.
 * Tests Poisson lambda computation, score matrix generation,
 * market probabilities, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class ScorePredictionServiceTest {

    private ScorePredictionService service;

    private Path tempModelFile;

    @BeforeEach
    void setUp() throws Exception {
        service = new ScorePredictionService();
        ReflectionTestUtils.setField(service, "maxGoals", 5);

        // Create a temporary Poisson model file with known parameters
        tempModelFile = Files.createTempFile("poisson_test_model", ".model");
        ReflectionTestUtils.setField(service, "poissonModelPath", tempModelFile.toString());

        writeSampleModel(tempModelFile);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempModelFile != null) {
            Files.deleteIfExists(tempModelFile);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // POISSON PMF TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Poisson PMF Calculations")
    class PoissonPMFTests {

        @Test
        @DisplayName("P(X=0|λ=1.5) ≈ 0.2231")
        void testPMF_zero() {
            assertThat(ScorePredictionService.poissonPMF(1.5, 0)).isCloseTo(0.2231, within(0.001));
        }

        @Test
        @DisplayName("P(X=1|λ=1.5) ≈ 0.3347")
        void testPMF_one() {
            assertThat(ScorePredictionService.poissonPMF(1.5, 1)).isCloseTo(0.3347, within(0.001));
        }

        @Test
        @DisplayName("P(X=2|λ=2.0) ≈ 0.2707")
        void testPMF_two() {
            assertThat(ScorePredictionService.poissonPMF(2.0, 2)).isCloseTo(0.2707, within(0.001));
        }

        @Test
        @DisplayName("PMF should sum to ≈1.0 for k=0..20")
        void testPMF_sumsToOne() {
            double sum = 0;
            for (int k = 0; k <= 20; k++) {
                sum += ScorePredictionService.poissonPMF(1.8, k);
            }
            assertThat(sum).isCloseTo(1.0, within(0.0001));
        }

        @Test
        @DisplayName("P(X=-1) = 0.0")
        void testPMF_negative() {
            assertThat(ScorePredictionService.poissonPMF(1.5, -1)).isEqualTo(0.0);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SCORE PREDICTION TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Score Prediction")
    class ScorePredictionTests {

        @Test
        @DisplayName("Should return valid score prediction for known teams")
        void testPredictScore_validOutput() {
            ScorePredictionDTO result = service.predictScore("Arsenal", "Chelsea");

            assertThat(result).isNotNull();
            assertThat(result.getHomeTeam()).isEqualTo("Arsenal");
            assertThat(result.getAwayTeam()).isEqualTo("Chelsea");
            assertThat(result.getScorePrediction()).isNotNull();
            assertThat(result.getScorePrediction().getMostLikelyScore()).matches("\\d-\\d");
        }

        @Test
        @DisplayName("Score prediction probability should be between 0 and 1")
        void testPredictScore_probabilityRange() {
            ScorePredictionDTO result = service.predictScore("Arsenal", "Chelsea");

            assertThat(result.getScorePrediction().getProbability()).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("Top 3 scores should be in descending probability order")
        void testPredictScore_top3Order() {
            ScorePredictionDTO result = service.predictScore("Arsenal", "Chelsea");

            List<Map<String, Double>> top3 = result.getScorePrediction().getTop3Scores();
            assertThat(top3).hasSize(3);

            List<Double> probs = top3.stream()
                    .map(m -> m.values().iterator().next())
                    .toList();
            assertThat(probs).isSortedAccordingTo(Comparator.reverseOrder());
        }

        @Test
        @DisplayName("Outcome probabilities should sum to ≈1.0")
        void testPredictScore_outcomeProbsSum() {
            ScorePredictionDTO result = service.predictScore("Arsenal", "Chelsea");

            double total = result.getProbHomeWin() + result.getProbDraw() + result.getProbAwayWin();
            assertThat(total).isCloseTo(1.0, within(0.02));
        }

        @Test
        @DisplayName("Over/Under probabilities should be consistent (1.5 >= 2.5 >= 3.5)")
        void testPredictScore_overUnder() {
            ScorePredictionDTO result = service.predictScore("Arsenal", "Chelsea");
            ScorePredictionDTO.ScorePrediction sp = result.getScorePrediction();

            assertThat(sp.getOver15Prob()).isGreaterThanOrEqualTo(sp.getOver25Prob());
            assertThat(sp.getOver25Prob()).isGreaterThanOrEqualTo(sp.getOver35Prob());
        }

        @Test
        @DisplayName("BTTS and clean sheet probabilities should be in valid range")
        void testPredictScore_bttsAndCleanSheet() {
            ScorePredictionDTO result = service.predictScore("Arsenal", "Chelsea");
            ScorePredictionDTO.ScorePrediction sp = result.getScorePrediction();

            assertThat(sp.getBttsProb()).isBetween(0.0, 1.0);
            assertThat(sp.getCleanSheetHome()).isBetween(0.0, 1.0);
            assertThat(sp.getCleanSheetAway()).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("Score matrix should have 36 entries (6x6 for maxGoals=5)")
        void testPredictScore_matrixSize() {
            ScorePredictionDTO result = service.predictScore("Arsenal", "Chelsea");

            assertThat(result.getScoreMatrix()).hasSize(36);
        }

        @Test
        @DisplayName("Score matrix probabilities should sum to ≈1.0")
        void testPredictScore_matrixSum() {
            ScorePredictionDTO result = service.predictScore("Arsenal", "Chelsea");

            double sum = result.getScoreMatrix().values().stream().mapToDouble(Double::doubleValue).sum();
            assertThat(sum).isCloseTo(1.0, within(0.02));
        }

        @Test
        @DisplayName("Expected goals (lambda) should be in realistic range")
        void testPredictScore_expectedGoals() {
            ScorePredictionDTO result = service.predictScore("Arsenal", "Chelsea");

            assertThat(result.getHomeExpectedGoals()).isBetween(0.3, 5.0);
            assertThat(result.getAwayExpectedGoals()).isBetween(0.3, 5.0);
        }

        @Test
        @DisplayName("Strong home team vs weak away team should favour home win")
        void testPredictScore_homeFavourite() {
            ScorePredictionDTO result = service.predictScore("Arsenal", "Southampton");

            assertThat(result.getProbHomeWin()).isGreaterThan(result.getProbAwayWin());
            assertThat(result.getHomeExpectedGoals()).isGreaterThan(result.getAwayExpectedGoals());
        }

        @Test
        @DisplayName("Confidence should be HIGH for known teams")
        void testPredictScore_confidence() {
            ScorePredictionDTO result = service.predictScore("Arsenal", "Chelsea");
            assertThat(result.getConfidence()).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("Confidence should be LOW for unknown teams")
        void testPredictScore_unknownTeams() {
            ScorePredictionDTO result = service.predictScore("UnknownFC", "MysteryUTD");
            assertThat(result.getConfidence()).isEqualTo("LOW");
            // Should still produce valid results with default parameters
            assertThat(result.getScorePrediction().getMostLikelyScore()).isNotBlank();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // MODEL LOADING TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Model Loading")
    class ModelLoadingTests {

        @Test
        @DisplayName("isModelAvailable returns true when model file exists")
        void testModelAvailable() {
            assertThat(service.isModelAvailable()).isTrue();
        }

        @Test
        @DisplayName("isModelAvailable returns false when model file missing")
        void testModelNotAvailable() throws Exception {
            Files.deleteIfExists(tempModelFile);
            assertThat(service.isModelAvailable()).isFalse();
        }

        @Test
        @DisplayName("predictScore should throw when model file missing")
        void testPredictWithoutModel() throws Exception {
            Files.deleteIfExists(tempModelFile);
            assertThatThrownBy(() -> service.predictScore("Arsenal", "Chelsea"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Poisson score model not found");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // EDGE CASES
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Same team predictions should still work (defensive usage)")
        void testSameTeam() {
            // The controller prevents this, but the service should handle gracefully
            ScorePredictionDTO result = service.predictScore("Arsenal", "Arsenal");
            assertThat(result.getScorePrediction().getMostLikelyScore()).isNotBlank();
        }

        @Test
        @DisplayName("All matrix entries should be non-negative")
        void testNoNegativeProbabilities() {
            ScorePredictionDTO result = service.predictScore("Arsenal", "Chelsea");
            result.getScoreMatrix().values().forEach(p ->
                    assertThat(p).isGreaterThanOrEqualTo(0.0));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Write a sample Poisson model with known parameters for testing.
     * Parameters mimic a realistic Premier League scenario.
     */
    private void writeSampleModel(Path path) throws Exception {
        PoissonParameters params = new PoissonParameters();
        Map<String, Double> attack = new HashMap<>();
        Map<String, Double> defence = new HashMap<>();

        // Arsenal: strong attack, solid defence
        attack.put("Arsenal", 1.35);
        defence.put("Arsenal", 0.75);

        // Chelsea: above average
        attack.put("Chelsea", 1.10);
        defence.put("Chelsea", 0.90);

        // Liverpool: strong
        attack.put("Liverpool", 1.30);
        defence.put("Liverpool", 0.80);

        // Man City: very strong
        attack.put("Man City", 1.40);
        defence.put("Man City", 0.70);

        // Everton: average
        attack.put("Everton", 0.85);
        defence.put("Everton", 1.10);

        // Southampton: weak
        attack.put("Southampton", 0.70);
        defence.put("Southampton", 1.40);

        params.setAttack(attack);
        params.setDefence(defence);
        params.setHomeAdvantage(1.30);
        params.setLeagueAvgGoals(1.40);
        params.setMaxGoals(5);
        params.setTrainedAt(new Date());

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path.toFile()))) {
            oos.writeObject(params);
        }
    }
}

