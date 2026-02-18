package com.app.footballprediction.modeltraining;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import weka.classifiers.Classifier;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.meta.Vote;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for EnsembleModelService.
 * Tests k-fold cross-validation, gradient boosting, ensemble methods, and grid search.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EnsembleModelService Unit Tests")
class EnsembleModelServiceTest {

    private EnsembleModelService ensembleModelService;
    private Instances testDataset;

    @BeforeEach
    void setUp() {
        ensembleModelService = new EnsembleModelService();
        ReflectionTestUtils.setField(ensembleModelService, "ensembleModelPath",
                "./target/test-data/test_ensemble.model");
        ReflectionTestUtils.setField(ensembleModelService, "kFolds", 5);

        // Create a test dataset
        testDataset = createTestDataset(200);
    }

    @Nested
    @DisplayName("Cross-Validation Tests")
    class CrossValidationTests {

        @Test
        @DisplayName("performs cross-validation and returns valid metrics")
        void performsCrossValidationSuccessfully() throws Exception {
            RandomForest rf = new RandomForest();
            rf.setNumIterations(10);
            rf.setSeed(42);

            CrossValidationResult result = ensembleModelService.performCrossValidation(
                    testDataset, rf, 5);

            assertThat(result).isNotNull();
            assertThat(result.getAccuracy()).isBetween(0.0, 100.0);
            assertThat(result.getKappa()).isBetween(-1.0, 1.0);
            assertThat(result.getFMeasure()).isBetween(0.0, 1.0);
            assertThat(result.getPrecision()).isBetween(0.0, 1.0);
            assertThat(result.getRecall()).isBetween(0.0, 1.0);
            assertThat(result.getFolds()).isEqualTo(5);
        }

        @Test
        @DisplayName("returns valid confusion matrix")
        void returnsValidConfusionMatrix() throws Exception {
            RandomForest rf = new RandomForest();
            rf.setNumIterations(10);
            rf.setSeed(42);

            CrossValidationResult result = ensembleModelService.performCrossValidation(
                    testDataset, rf, 3);

            assertThat(result.getConfusionMatrix()).isNotNull();
            assertThat(result.getConfusionMatrix().length).isEqualTo(3); // 3 classes: H, D, A
        }

        @Test
        @DisplayName("generates formatted report")
        void generatesFormattedReport() throws Exception {
            RandomForest rf = new RandomForest();
            rf.setNumIterations(10);
            rf.setSeed(42);

            CrossValidationResult result = ensembleModelService.performCrossValidation(
                    testDataset, rf, 3);

            String report = result.toReport();

            assertThat(report).contains("CROSS-VALIDATION RESULTS");
            assertThat(report).contains("Accuracy");
            assertThat(report).contains("Kappa");
            assertThat(report).contains("F-Measure");
        }
    }

    @Nested
    @DisplayName("Gradient Boosting Tests")
    class GradientBoostingTests {

        @Test
        @DisplayName("trains AdaBoost classifier successfully")
        void trainsAdaBoostSuccessfully() throws Exception {
            AdaBoostM1 adaBoost = ensembleModelService.trainGradientBoosting(testDataset);

            assertThat(adaBoost).isNotNull();
        }

        @Test
        @DisplayName("trains AdaBoost with custom parameters")
        void trainsAdaBoostWithCustomParameters() throws Exception {
            J48 customBase = new J48();
            customBase.setConfidenceFactor(0.1f);

            AdaBoostM1 adaBoost = ensembleModelService.trainGradientBoosting(
                    testDataset, 50, customBase);

            assertThat(adaBoost).isNotNull();
            assertThat(adaBoost.getNumIterations()).isEqualTo(50);
        }

        @Test
        @DisplayName("AdaBoost can make predictions")
        void adaBoostCanMakePredictions() throws Exception {
            AdaBoostM1 adaBoost = ensembleModelService.trainGradientBoosting(testDataset);

            DenseInstance testInstance = createTestInstance(testDataset);
            double[] distribution = adaBoost.distributionForInstance(testInstance);

            assertThat(distribution).hasSize(3); // 3 classes
            assertThat(distribution[0] + distribution[1] + distribution[2])
                    .isCloseTo(1.0, within(0.01));
        }
    }

    @Nested
    @DisplayName("Ensemble Model Tests")
    class EnsembleModelTests {

        @Test
        @DisplayName("trains voting ensemble successfully")
        void trainsVotingEnsembleSuccessfully() throws Exception {
            RandomForest rf = new RandomForest();
            rf.setNumIterations(10);
            J48 j48 = new J48();

            Vote vote = ensembleModelService.trainVotingEnsemble(
                    testDataset, new Classifier[]{rf, j48});

            assertThat(vote).isNotNull();
        }

        @Test
        @DisplayName("trains default ensemble successfully")
        void trainsDefaultEnsembleSuccessfully() throws Exception {
            Vote ensemble = ensembleModelService.trainDefaultEnsemble(testDataset);

            assertThat(ensemble).isNotNull();
        }

        @Test
        @DisplayName("ensemble can make predictions")
        void ensembleCanMakePredictions() throws Exception {
            Vote ensemble = ensembleModelService.trainDefaultEnsemble(testDataset);

            DenseInstance testInstance = createTestInstance(testDataset);
            double[] distribution = ensemble.distributionForInstance(testInstance);

            assertThat(distribution).hasSize(3);
            assertThat(distribution[0] + distribution[1] + distribution[2])
                    .isCloseTo(1.0, within(0.01));
        }
    }

    @Nested
    @DisplayName("Grid Search Tests")
    class GridSearchTests {

        @Test
        @DisplayName("performs Random Forest grid search")
        void performsRandomForestGridSearch() throws Exception {
            // Use smaller dataset for faster testing
            Instances smallDataset = createTestDataset(100);

            GridSearchResult result = ensembleModelService.gridSearchRandomForest(smallDataset);

            assertThat(result).isNotNull();
            assertThat(result.getClassifierName()).isEqualTo("RandomForest");
            assertThat(result.getBestParams()).containsKeys("numTrees", "numFeatures", "maxDepth");
            assertThat(result.getAccuracy()).isBetween(0.0, 100.0);
        }

        @Test
        @DisplayName("performs AdaBoost grid search")
        void performsAdaBoostGridSearch() throws Exception {
            // Use smaller dataset for faster testing
            Instances smallDataset = createTestDataset(100);

            GridSearchResult result = ensembleModelService.gridSearchAdaBoost(smallDataset);

            assertThat(result).isNotNull();
            assertThat(result.getClassifierName()).isEqualTo("AdaBoostM1");
            assertThat(result.getBestParams()).containsKeys("numIterations", "confidenceFactor", "minNumObj");
            assertThat(result.getAccuracy()).isBetween(0.0, 100.0);
        }

        @Test
        @DisplayName("builds classifier from grid search result")
        void buildsClassifierFromGridSearchResult() throws Exception {
            GridSearchResult rfResult = GridSearchResult.builder()
                    .classifierName("RandomForest")
                    .bestParams(Map.of("numTrees", 100, "numFeatures", 5, "maxDepth", 10))
                    .accuracy(55.0)
                    .build();

            Classifier classifier = ensembleModelService.buildBestClassifier(rfResult);

            assertThat(classifier).isInstanceOf(RandomForest.class);
            RandomForest rf = (RandomForest) classifier;
            assertThat(rf.getNumIterations()).isEqualTo(100);
        }

        @Test
        @DisplayName("grid search generates formatted report")
        void gridSearchGeneratesReport() {
            GridSearchResult result = GridSearchResult.builder()
                    .classifierName("RandomForest")
                    .bestParams(Map.of("numTrees", 100, "numFeatures", 5, "maxDepth", 0))
                    .accuracy(56.5)
                    .kappa(0.34)
                    .fMeasure(0.55)
                    .build();

            String report = result.toReport();

            assertThat(report).contains("GRID SEARCH RESULTS");
            assertThat(report).contains("RandomForest");
            assertThat(report).contains("56.5");
        }
    }

    @Nested
    @DisplayName("Model Comparison Tests")
    class ModelComparisonTests {

        @Test
        @DisplayName("compares multiple models")
        void comparesMultipleModels() throws Exception {
            Instances smallDataset = createTestDataset(100);

            Map<String, Classifier> classifiers = new LinkedHashMap<>();
            RandomForest rf = new RandomForest();
            rf.setNumIterations(10);
            rf.setSeed(42);
            classifiers.put("RandomForest", rf);
            classifiers.put("J48", new J48());

            List<ModelComparisonResult> results = ensembleModelService.compareModels(
                    smallDataset, classifiers);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).getAccuracy())
                    .isGreaterThanOrEqualTo(results.get(1).getAccuracy()); // Sorted by accuracy
        }

        @Test
        @DisplayName("model comparison result generates row")
        void modelComparisonResultGeneratesRow() {
            ModelComparisonResult result = ModelComparisonResult.builder()
                    .modelName("RandomForest")
                    .accuracy(55.5)
                    .kappa(0.33)
                    .fMeasure(0.54)
                    .precision(0.55)
                    .recall(0.54)
                    .trainingTimeMs(1500)
                    .build();

            String row = result.toRow();

            assertThat(row).contains("RandomForest");
            assertThat(row).contains("55.50%");
        }
    }

    @Nested
    @DisplayName("Model Persistence Tests")
    class ModelPersistenceTests {

        @Test
        @DisplayName("saves and loads model successfully")
        void savesAndLoadsModelSuccessfully() throws Exception {
            Vote ensemble = ensembleModelService.trainDefaultEnsemble(testDataset);

            ensembleModelService.saveModel(ensemble, testDataset);

            assertThat(ensembleModelService.isModelLoaded()).isTrue();
            assertThat(ensembleModelService.getEnsembleModel()).isNotNull();
            assertThat(ensembleModelService.getTrainingHeader()).isNotNull();

            // Clean up
            new File("./target/test-data/test_ensemble.model").delete();
        }

        @Test
        @DisplayName("isModelLoaded returns false when not loaded")
        void isModelLoadedReturnsFalseWhenNotLoaded() {
            ReflectionTestUtils.setField(ensembleModelService, "ensembleModel", null);
            ReflectionTestUtils.setField(ensembleModelService, "trainingHeader", null);

            assertThat(ensembleModelService.isModelLoaded()).isFalse();
        }
    }

    @Nested
    @DisplayName("Prediction Tests")
    class PredictionTests {

        @Test
        @DisplayName("throws exception when model not loaded")
        void throwsExceptionWhenModelNotLoaded() {
            ReflectionTestUtils.setField(ensembleModelService, "ensembleModel", null);

            DenseInstance testInstance = createTestInstance(testDataset);

            assertThatThrownBy(() -> ensembleModelService.predict(testInstance))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Ensemble model not loaded");
        }

        @Test
        @DisplayName("makes prediction when model is loaded")
        void makesPredictionWhenModelIsLoaded() throws Exception {
            Vote ensemble = ensembleModelService.trainDefaultEnsemble(testDataset);
            ensembleModelService.setEnsembleModel(ensemble);
            ensembleModelService.setTrainingHeader(testDataset);

            DenseInstance testInstance = createTestInstance(testDataset);
            double[] prediction = ensembleModelService.predict(testInstance);

            assertThat(prediction).hasSize(3);
            assertThat(prediction[0] + prediction[1] + prediction[2])
                    .isCloseTo(1.0, within(0.01));
        }
    }

    // ── Helper Methods ────────────────────────────────────────────────────

    /**
     * Creates a test dataset with synthetic football match data.
     */
    private Instances createTestDataset(int numInstances) {
        ArrayList<Attribute> attributes = new ArrayList<>();

        // Numeric features (simplified)
        attributes.add(new Attribute("homeFormPoints"));
        attributes.add(new Attribute("awayFormPoints"));
        attributes.add(new Attribute("homeGoalsScoredAvg"));
        attributes.add(new Attribute("homeGoalsConcededAvg"));
        attributes.add(new Attribute("awayGoalsScoredAvg"));
        attributes.add(new Attribute("awayGoalsConcededAvg"));

        // Class label
        ArrayList<String> labels = new ArrayList<>(List.of("H", "D", "A"));
        attributes.add(new Attribute("result", labels));

        Instances dataset = new Instances("TestFootball", attributes, numInstances);
        dataset.setClassIndex(attributes.size() - 1);

        // Generate synthetic data
        java.util.Random random = new java.util.Random(42);
        for (int i = 0; i < numInstances; i++) {
            DenseInstance instance = new DenseInstance(attributes.size());
            instance.setDataset(dataset);

            // Generate features
            double homeForm = 0.5 + random.nextDouble() * 2.5; // 0.5 - 3.0
            double awayForm = 0.5 + random.nextDouble() * 2.5;
            double homeGoalsScored = 0.5 + random.nextDouble() * 2.0;
            double homeGoalsConceded = 0.5 + random.nextDouble() * 2.0;
            double awayGoalsScored = 0.5 + random.nextDouble() * 2.0;
            double awayGoalsConceded = 0.5 + random.nextDouble() * 2.0;

            instance.setValue(0, homeForm);
            instance.setValue(1, awayForm);
            instance.setValue(2, homeGoalsScored);
            instance.setValue(3, homeGoalsConceded);
            instance.setValue(4, awayGoalsScored);
            instance.setValue(5, awayGoalsConceded);

            // Determine outcome based on features (with some randomness)
            double homeStrength = homeForm + homeGoalsScored - awayGoalsConceded;
            double awayStrength = awayForm + awayGoalsScored - homeGoalsConceded;
            double diff = homeStrength - awayStrength + (random.nextDouble() - 0.5) * 2;

            String outcome;
            if (diff > 0.5) {
                outcome = "H";
            } else if (diff < -0.5) {
                outcome = "A";
            } else {
                outcome = "D";
            }
            instance.setValue(6, outcome);

            dataset.add(instance);
        }

        return dataset;
    }

    /**
     * Creates a test instance for prediction.
     */
    private DenseInstance createTestInstance(Instances dataset) {
        DenseInstance instance = new DenseInstance(dataset.numAttributes());
        instance.setDataset(dataset);

        instance.setValue(0, 2.0);  // homeFormPoints
        instance.setValue(1, 1.5);  // awayFormPoints
        instance.setValue(2, 1.8);  // homeGoalsScoredAvg
        instance.setValue(3, 1.0);  // homeGoalsConcededAvg
        instance.setValue(4, 1.2);  // awayGoalsScoredAvg
        instance.setValue(5, 1.5);  // awayGoalsConcededAvg

        return instance;
    }
}

