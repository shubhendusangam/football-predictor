package com.app.footballprediction.modeltraining;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for model training result classes.
 */
@DisplayName("Model Training Result Classes Tests")
class ModelTrainingResultsTest {

    @Nested
    @DisplayName("CrossValidationResult Tests")
    class CrossValidationResultTests {

        @Test
        @DisplayName("builds correctly with all fields")
        void buildsCorrectlyWithAllFields() {
            double[][] confusionMatrix = {
                    {100, 20, 10},
                    {15, 50, 25},
                    {12, 18, 60}
            };

            CrossValidationResult result = CrossValidationResult.builder()
                    .accuracy(75.5)
                    .kappa(0.55)
                    .meanAbsoluteError(0.25)
                    .rootMeanSquaredError(0.35)
                    .fMeasure(0.72)
                    .precision(0.74)
                    .recall(0.71)
                    .areaUnderROC(0.85)
                    .confusionMatrix(confusionMatrix)
                    .folds(10)
                    .classDetails("  H: 0.75\n  D: 0.65\n  A: 0.78")
                    .build();

            assertThat(result.getAccuracy()).isEqualTo(75.5);
            assertThat(result.getKappa()).isEqualTo(0.55);
            assertThat(result.getFolds()).isEqualTo(10);
            assertThat(result.getConfusionMatrix()).isEqualTo(confusionMatrix);
        }

        @Test
        @DisplayName("generates formatted report")
        void generatesFormattedReport() {
            double[][] confusionMatrix = {
                    {100, 20, 10},
                    {15, 50, 25},
                    {12, 18, 60}
            };

            CrossValidationResult result = CrossValidationResult.builder()
                    .accuracy(75.5)
                    .kappa(0.55)
                    .meanAbsoluteError(0.25)
                    .rootMeanSquaredError(0.35)
                    .fMeasure(0.72)
                    .precision(0.74)
                    .recall(0.71)
                    .areaUnderROC(0.85)
                    .confusionMatrix(confusionMatrix)
                    .folds(10)
                    .classDetails("  H: 0.75\n  D: 0.65\n  A: 0.78")
                    .build();

            String report = result.toReport();

            assertThat(report).contains("CROSS-VALIDATION RESULTS (10-Fold)");
            assertThat(report).contains("Accuracy");
            assertThat(report).contains("75.5");
            assertThat(report).contains("Kappa");
            assertThat(report).contains("0.5500");
            assertThat(report).contains("Confusion Matrix");
        }

        @Test
        @DisplayName("handles null confusion matrix in report")
        void handlesNullConfusionMatrixInReport() {
            CrossValidationResult result = CrossValidationResult.builder()
                    .accuracy(50.0)
                    .kappa(0.25)
                    .folds(5)
                    .classDetails("")
                    .build();

            String report = result.toReport();

            assertThat(report).contains("N/A");
        }
    }

    @Nested
    @DisplayName("GridSearchResult Tests")
    class GridSearchResultTests {

        @Test
        @DisplayName("builds correctly with all fields")
        void buildsCorrectlyWithAllFields() {
            Map<String, Object> params = Map.of(
                    "numTrees", 100,
                    "numFeatures", 5,
                    "maxDepth", 0
            );

            GridSearchResult result = GridSearchResult.builder()
                    .classifierName("RandomForest")
                    .bestParams(params)
                    .accuracy(56.5)
                    .kappa(0.34)
                    .fMeasure(0.55)
                    .build();

            assertThat(result.getClassifierName()).isEqualTo("RandomForest");
            assertThat(result.getBestParams()).containsEntry("numTrees", 100);
            assertThat(result.getAccuracy()).isEqualTo(56.5);
        }

        @Test
        @DisplayName("generates formatted report")
        void generatesFormattedReport() {
            Map<String, Object> params = Map.of(
                    "numTrees", 100,
                    "numFeatures", 5
            );

            GridSearchResult result = GridSearchResult.builder()
                    .classifierName("RandomForest")
                    .bestParams(params)
                    .accuracy(56.5)
                    .kappa(0.34)
                    .fMeasure(0.55)
                    .build();

            String report = result.toReport();

            assertThat(report).contains("GRID SEARCH RESULTS");
            assertThat(report).contains("RandomForest");
            assertThat(report).contains("56.5");
            assertThat(report).contains("Best Parameters");
            assertThat(report).contains("numTrees");
        }

        @Test
        @DisplayName("handles null params in report")
        void handlesNullParamsInReport() {
            GridSearchResult result = GridSearchResult.builder()
                    .classifierName("TestClassifier")
                    .accuracy(50.0)
                    .build();

            String report = result.toReport();

            assertThat(report).contains("TestClassifier");
            assertThat(report).doesNotContain("null");
        }
    }

    @Nested
    @DisplayName("ModelComparisonResult Tests")
    class ModelComparisonResultTests {

        @Test
        @DisplayName("builds correctly with all fields")
        void buildsCorrectlyWithAllFields() {
            ModelComparisonResult result = ModelComparisonResult.builder()
                    .modelName("RandomForest")
                    .accuracy(55.5)
                    .kappa(0.33)
                    .fMeasure(0.54)
                    .precision(0.55)
                    .recall(0.54)
                    .areaUnderROC(0.72)
                    .trainingTimeMs(1500)
                    .build();

            assertThat(result.getModelName()).isEqualTo("RandomForest");
            assertThat(result.getAccuracy()).isEqualTo(55.5);
            assertThat(result.getTrainingTimeMs()).isEqualTo(1500);
        }

        @Test
        @DisplayName("generates formatted row")
        void generatesFormattedRow() {
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
            assertThat(row).contains("0.3300");
            assertThat(row).contains("1500ms");
        }
    }
}

