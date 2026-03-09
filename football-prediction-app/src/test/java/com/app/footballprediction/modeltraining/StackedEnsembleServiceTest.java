package com.app.footballprediction.modeltraining;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for StackedEnsembleService.
 * Tests the OOF-based stacking pipeline, prediction, and model persistence.
 */
@DisplayName("StackedEnsembleService Unit Tests")
class StackedEnsembleServiceTest {

    private StackedEnsembleService stackedEnsembleService;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() {
        stackedEnsembleService = new StackedEnsembleService();
    }

    @Nested
    @DisplayName("isModelTrained()")
    class IsModelTrainedTests {

        @Test
        @DisplayName("returns false before training")
        void returnsFalseBeforeTraining() {
            assertThat(stackedEnsembleService.isModelTrained()).isFalse();
        }
    }

    @Nested
    @DisplayName("predictProbabilities()")
    class PredictProbabilitiesTests {

        @Test
        @DisplayName("throws when model not trained")
        void throwsWhenModelNotTrained() {
            Instances dataset = createSyntheticDataset(10);
            Instance instance = dataset.instance(0);

            assertThatThrownBy(() -> stackedEnsembleService.predictProbabilities(instance))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Model not trained");
        }
    }

    @Nested
    @DisplayName("trainStackedEnsemble()")
    class TrainStackedEnsembleTests {

        @Test
        @DisplayName("trains successfully with synthetic data")
        void trainsSuccessfully() throws Exception {
            Instances trainData = createSyntheticDataset(200);
            Instances validationData = createSyntheticDataset(50);

            stackedEnsembleService.trainStackedEnsemble(trainData, validationData);

            assertThat(stackedEnsembleService.isModelTrained()).isTrue();
            assertThat(stackedEnsembleService.getRandomForest()).isNotNull();
            assertThat(stackedEnsembleService.getGradientBoosting()).isNotNull();
            assertThat(stackedEnsembleService.getLogisticRegression()).isNotNull();
        }

        @Test
        @DisplayName("predictions return valid probability distributions")
        void predictionsAreValid() throws Exception {
            Instances trainData = createSyntheticDataset(200);
            Instances validationData = createSyntheticDataset(50);

            stackedEnsembleService.trainStackedEnsemble(trainData, validationData);

            Instance testInstance = createSyntheticDataset(1).instance(0);
            double[] probs = stackedEnsembleService.predictProbabilities(testInstance);

            assertThat(probs).hasSize(3);
            assertThat(probs[0]).isBetween(0.0, 1.0);
            assertThat(probs[1]).isBetween(0.0, 1.0);
            assertThat(probs[2]).isBetween(0.0, 1.0);

            double sum = probs[0] + probs[1] + probs[2];
            assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("predictClass returns valid label")
        void predictClassReturnsValidLabel() throws Exception {
            Instances trainData = createSyntheticDataset(200);
            Instances validationData = createSyntheticDataset(50);

            stackedEnsembleService.trainStackedEnsemble(trainData, validationData);

            Instance testInstance = createSyntheticDataset(1).instance(0);
            String label = stackedEnsembleService.predictClass(testInstance);

            assertThat(label).isIn("H", "D", "A");
        }
    }

    @Nested
    @DisplayName("save/load round-trip")
    class PersistenceTests {

        @Test
        @DisplayName("model survives save and load cycle")
        void saveThenLoad() throws Exception {
            Instances trainData = createSyntheticDataset(200);
            Instances validationData = createSyntheticDataset(50);

            stackedEnsembleService.trainStackedEnsemble(trainData, validationData);

            // Get a prediction before save
            Instance testInstance = createSyntheticDataset(1).instance(0);
            double[] probsBefore = stackedEnsembleService.predictProbabilities(testInstance);

            // Save
            String modelPath = new File(tempDir, "test_model.model").getAbsolutePath();
            stackedEnsembleService.saveModel(modelPath);

            // Load into a new instance
            StackedEnsembleService loaded = StackedEnsembleService.loadModel(modelPath);

            assertThat(loaded.isModelTrained()).isTrue();

            // Predictions should be the same
            double[] probsAfter = loaded.predictProbabilities(testInstance);
            assertThat(probsAfter[0]).isCloseTo(probsBefore[0], org.assertj.core.data.Offset.offset(0.001));
            assertThat(probsAfter[1]).isCloseTo(probsBefore[1], org.assertj.core.data.Offset.offset(0.001));
            assertThat(probsAfter[2]).isCloseTo(probsBefore[2], org.assertj.core.data.Offset.offset(0.001));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    /**
     * Create a synthetic dataset with 6 numeric features + class label.
     * Simulates a simplified version of the real feature set.
     */
    private Instances createSyntheticDataset(int numInstances) {
        ArrayList<Attribute> attrs = new ArrayList<>();
        attrs.add(new Attribute("homeFormPoints"));
        attrs.add(new Attribute("awayFormPoints"));
        attrs.add(new Attribute("homeGoalsScoredAvg"));
        attrs.add(new Attribute("awayGoalsScoredAvg"));
        attrs.add(new Attribute("h2hHomeWinRate"));
        attrs.add(new Attribute("homeShotsOnTargetAvg"));

        ArrayList<String> labels = new ArrayList<>(List.of("H", "D", "A"));
        attrs.add(new Attribute("result", labels));

        Instances dataset = new Instances("SyntheticTest", attrs, numInstances);
        dataset.setClassIndex(attrs.size() - 1);

        Random rng = new Random(42);
        for (int i = 0; i < numInstances; i++) {
            double[] values = new double[7];
            values[0] = rng.nextDouble() * 3;       // homeFormPoints
            values[1] = rng.nextDouble() * 3;       // awayFormPoints
            values[2] = rng.nextDouble() * 3;       // homeGoalsScoredAvg
            values[3] = rng.nextDouble() * 3;       // awayGoalsScoredAvg
            values[4] = rng.nextDouble();            // h2hHomeWinRate
            values[5] = rng.nextDouble() * 6;       // homeShotsOnTargetAvg

            // Label: simple rule for determinism
            if (values[0] > values[1] + 0.5) {
                values[6] = 0; // H
            } else if (values[1] > values[0] + 0.5) {
                values[6] = 2; // A
            } else {
                values[6] = 1; // D
            }

            Instance inst = new DenseInstance(1.0, values);
            dataset.add(inst);
        }

        return dataset;
    }
}

