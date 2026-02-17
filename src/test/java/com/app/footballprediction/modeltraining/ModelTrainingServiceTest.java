package com.app.footballprediction.modeltraining;

import com.app.footballprediction.featureengineering.FeatureEngineeringService;
import com.app.footballprediction.model.Match;
import com.app.footballprediction.model.MatchFeatures;
import com.app.footballprediction.repository.MatchRepository;
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
    }

    @Nested
    @DisplayName("getPredictedLabel()")
    class GetPredictedLabelTests {

        @Test
        @DisplayName("returns H when home win probability is highest")
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
        @DisplayName("returns A when away win probability is highest")
        void returnsAForAwayWin() {
            double[] probes = {0.20, 0.25, 0.55};

            String label = modelTrainingService.getPredictedLabel(probes);

            assertThat(label).isEqualTo("A");
        }

        @Test
        @DisplayName("returns H when home and draw are equal (home preference)")
        void returnsHWhenHomeAndDrawEqual() {
            double[] probes = {0.40, 0.40, 0.20};

            String label = modelTrainingService.getPredictedLabel(probes);

            assertThat(label).isEqualTo("H");
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
        ArrayList<Attribute> attrs = new ArrayList<>();
        attrs.add(new Attribute("homeFormPoints"));
        attrs.add(new Attribute("awayFormPoints"));

        ArrayList<String> labels = new ArrayList<>(List.of("H", "D", "A"));
        attrs.add(new Attribute("result", labels));

        Instances instances = new Instances("Test", attrs, 0);
        instances.setClassIndex(2);
        return instances;
    }
}

