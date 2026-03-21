package com.app.footballprediction.modeltraining;

import com.app.common.service.FeatureEngineeringService;
import com.app.common.model.Match;
import com.app.common.model.MatchFeatures;
import com.app.common.repository.MatchRepository;
import com.app.common.weka.WekaSchemaBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.Instances;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ModelTrainingService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ModelTrainingService Unit Tests")
class ModelTrainingServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private FeatureEngineeringService featureEngineeringService;

    @InjectMocks
    private ModelTrainingService modelTrainingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(modelTrainingService, "modelOutputPath",
                "./data/test_model.model");
        ReflectionTestUtils.setField(modelTrainingService, "drawThreshold", 0.05);
    }

    @Nested
    @DisplayName("isModelLoaded()")
    class IsModelLoadedTests {

        @Test
        @DisplayName("returns false when model is null")
        void returnsFalseWhenModelNull() {
            ReflectionTestUtils.setField(modelTrainingService, "trainedModel", null);
            ReflectionTestUtils.setField(modelTrainingService, "trainingHeader", null);

            assertThat(modelTrainingService.isModelLoaded()).isFalse();
        }

        @Test
        @DisplayName("returns false when header is null")
        void returnsFalseWhenHeaderNull() {
            ReflectionTestUtils.setField(modelTrainingService, "trainedModel",
                    new RandomForest());
            ReflectionTestUtils.setField(modelTrainingService, "trainingHeader", null);

            assertThat(modelTrainingService.isModelLoaded()).isFalse();
        }

        @Test
        @DisplayName("returns true when both model and header are present")
        void returnsTrueWhenBothPresent() {
            ReflectionTestUtils.setField(modelTrainingService, "trainedModel",
                    new RandomForest());
            ReflectionTestUtils.setField(modelTrainingService, "trainingHeader",
                    createDummyInstances());

            assertThat(modelTrainingService.isModelLoaded()).isTrue();
        }

        @Test
        @DisplayName("returns false when header has wrong number of attributes (schema mismatch)")
        void returnsFalseWhenSchemaMismatch() {
            ReflectionTestUtils.setField(modelTrainingService, "trainedModel",
                    new RandomForest());

            // Create a header with wrong number of attributes (simulates old model)
            ArrayList<Attribute> oldAttrs = new ArrayList<>();
            oldAttrs.add(new Attribute("a"));
            oldAttrs.add(new Attribute("b"));
            ArrayList<String> labels = new ArrayList<>(List.of("H", "D", "A"));
            oldAttrs.add(new Attribute("result", labels));
            Instances oldHeader = new Instances("OldSchema", oldAttrs, 0);
            oldHeader.setClassIndex(2);

            ReflectionTestUtils.setField(modelTrainingService, "trainingHeader", oldHeader);

            assertThat(modelTrainingService.isModelLoaded()).isFalse();
        }
    }

    @Nested
    @DisplayName("getPredictedLabel()")
    class GetPredictedLabelTests {

        @Test
        @DisplayName("returns H when home win probability is clearly highest")
        void returnsHForHomeWin() {
            double[] probes = {0.55, 0.25, 0.20};

            String label = modelTrainingService.getPredictedLabel(probes);

            assertThat(label).isEqualTo("H");
        }

        @Test
        @DisplayName("returns D when draw probability is highest")
        void returnsDForDraw() {
            double[] probes = {0.25, 0.50, 0.25};

            String label = modelTrainingService.getPredictedLabel(probes);

            assertThat(label).isEqualTo("D");
        }

        @Test
        @DisplayName("returns A when away win probability is clearly highest")
        void returnsAForAwayWin() {
            double[] probes = {0.20, 0.25, 0.55};

            String label = modelTrainingService.getPredictedLabel(probes);

            assertThat(label).isEqualTo("A");
        }

        @Test
        @DisplayName("returns D when draw + threshold >= max non-draw (threshold effect)")
        void returnsDWhenThresholdApplies() {
            // draw=0.38, H=0.42, A=0.20 → draw + 0.05 = 0.43 >= 0.42 → D
            double[] probes = {0.42, 0.38, 0.20};

            String label = modelTrainingService.getPredictedLabel(probes);

            assertThat(label).isEqualTo("D");
        }

        @Test
        @DisplayName("returns H when home > draw + threshold")
        void returnsHWhenClearlyAboveThreshold() {
            // draw=0.30, H=0.50 → draw + 0.05 = 0.35 < 0.50 → H
            double[] probes = {0.50, 0.30, 0.20};

            String label = modelTrainingService.getPredictedLabel(probes);

            assertThat(label).isEqualTo("H");
        }

        @Test
        @DisplayName("returns D when draw + threshold equals max non-draw exactly")
        void returnsDWhenThresholdEqualsMax() {
            // draw=0.35, H=0.40, A=0.25 → draw + 0.05 = 0.40 >= 0.40 → D
            double[] probes = {0.40, 0.35, 0.25};

            String label = modelTrainingService.getPredictedLabel(probes);

            assertThat(label).isEqualTo("D");
        }
    }

    @Nested
    @DisplayName("predict()")
    class PredictTests {

        @Test
        @DisplayName("throws exception when model not loaded")
        void throwsWhenModelNotLoaded() {
            ReflectionTestUtils.setField(modelTrainingService, "trainedModel", null);
            ReflectionTestUtils.setField(modelTrainingService, "trainingHeader", null);

            MatchFeatures features = MatchFeatures.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .build();

            assertThatThrownBy(() -> modelTrainingService.predict(features))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Model not loaded");
        }
    }

    @Nested
    @DisplayName("trainAndEvaluate()")
    class TrainAndEvaluateTests {

        @Test
        @DisplayName("throws exception when not enough data")
        void throwsWhenNotEnoughData() {
            when(matchRepository.findAllByOrderByMatchDateAsc())
                    .thenReturn(List.of()); // Empty list

            assertThatThrownBy(() -> modelTrainingService.trainAndEvaluate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Not enough data");
        }

        @Test
        @DisplayName("throws exception when less than 100 matches")
        void throwsWhenLessThan100Matches() {
            List<Match> matches = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                matches.add(createMatch(i));
            }

            when(matchRepository.findAllByOrderByMatchDateAsc()).thenReturn(matches);

            assertThatThrownBy(() -> modelTrainingService.trainAndEvaluate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("at least 100 matches");
        }
    }

    // ── Helper methods ──────────────────────────────────────────────────

    private Match createMatch(int dayOffset) {
        return Match.builder()
                .homeTeam("Team" + dayOffset)
                .awayTeam("Team" + (dayOffset + 1))
                .fullTimeHomeGoals(2)
                .fullTimeAwayGoals(1)
                .fullTimeResult("H")
                .matchDate(LocalDate.of(2024, 1, 1).plusDays(dayOffset))
                .build();
    }

    private Instances createDummyInstances() {
        ArrayList<Attribute> attrs = WekaSchemaBuilder.buildAttributes();
        Instances instances = new Instances("Test", attrs, 0);
        instances.setClassIndex(WekaSchemaBuilder.IDX_LABEL);
        return instances;
    }
}

