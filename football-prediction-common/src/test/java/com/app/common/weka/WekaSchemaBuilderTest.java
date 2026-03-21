package com.app.common.weka;

import com.app.common.model.MatchFeatures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for WekaSchemaBuilder — the shared Weka schema definition.
 */
@DisplayName("WekaSchemaBuilder Unit Tests")
class WekaSchemaBuilderTest {

    @Nested
    @DisplayName("buildAttributes()")
    class BuildAttributesTests {

        @Test
        @DisplayName("returns correct number of attributes")
        void returnsCorrectNumberOfAttributes() {
            ArrayList<Attribute> attrs = WekaSchemaBuilder.buildAttributes();
            assertThat(attrs).hasSize(WekaSchemaBuilder.TOTAL_ATTRIBUTES);
        }

        @Test
        @DisplayName("last attribute is the class label")
        void lastAttributeIsClassLabel() {
            ArrayList<Attribute> attrs = WekaSchemaBuilder.buildAttributes();
            Attribute classAttr = attrs.get(WekaSchemaBuilder.IDX_LABEL);
            assertThat(classAttr.isNominal()).isTrue();
            assertThat(classAttr.numValues()).isEqualTo(3);
            assertThat(classAttr.value(0)).isEqualTo("H");
            assertThat(classAttr.value(1)).isEqualTo("D");
            assertThat(classAttr.value(2)).isEqualTo("A");
        }

        @Test
        @DisplayName("all features are numeric except the label")
        void allFeaturesAreNumeric() {
            ArrayList<Attribute> attrs = WekaSchemaBuilder.buildAttributes();
            for (int i = 0; i < WekaSchemaBuilder.IDX_LABEL; i++) {
                assertThat(attrs.get(i).isNumeric())
                        .as("Attribute %d (%s) should be numeric", i, attrs.get(i).name())
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("toWekaInstances()")
    class ToWekaInstancesTests {

        @Test
        @DisplayName("creates dataset with correct size and class index")
        void createsDatasetCorrectly() {
            List<MatchFeatures> features = List.of(
                    createMinimalFeatures("H"),
                    createMinimalFeatures("D"),
                    createMinimalFeatures("A")
            );

            Instances dataset = WekaSchemaBuilder.toWekaInstances(features, "TestData");

            assertThat(dataset.numInstances()).isEqualTo(3);
            assertThat(dataset.classIndex()).isEqualTo(WekaSchemaBuilder.IDX_LABEL);
            assertThat(dataset.numAttributes()).isEqualTo(WekaSchemaBuilder.TOTAL_ATTRIBUTES);
        }
    }

    @Nested
    @DisplayName("toWekaInstance()")
    class ToWekaInstanceTests {

        @Test
        @DisplayName("converts MatchFeatures with all features populated")
        void convertsFeatures() {
            MatchFeatures features = MatchFeatures.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .homeFormPoints(2.4)
                    .awayFormPoints(1.8)
                    .homeGoalsScoredAvg(1.5)
                    .homeGoalsConcededAvg(0.8)
                    .awayGoalsScoredAvg(1.2)
                    .awayGoalsConcededAvg(1.0)
                    .homeTotalGoalsAvg(2.5)
                    .awayTotalGoalsAvg(2.8)
                    .h2hHomeWinRate(0.5)
                    .h2hDrawRate(0.3)
                    .h2hAwayWinRate(0.2)
                    .homeShotsOnTargetAvg(5.0)
                    .awayShotsOnTargetAvg(3.5)
                    .homeCornersAvg(6.0)
                    .awayCornersAvg(4.0)
                    .homeGoalDifference(0.7)
                    .awayGoalDifference(0.2)
                    .homeOverallFormPoints(2.2)
                    .awayOverallFormPoints(1.6)
                    .homeWinStreak(3)
                    .awayWinStreak(1)
                    .homeUnbeatenStreak(5)
                    .awayUnbeatenStreak(2)
                    .homeDaysSinceLastMatch(7)
                    .awayDaysSinceLastMatch(4)
                    .homePossessionProxy(0.55)
                    .awayPossessionProxy(0.45)
                    .homeHalfTimeLeadRate(0.4)
                    .awayHalfTimeLeadRate(0.2)
                    .homeComebackRate(0.1)
                    .awayComebackRate(0.15)
                    .homeLeaguePosition(3)
                    .awayLeaguePosition(8)
                    .homeEloRating(1650.0)
                    .awayEloRating(1520.0)
                    .formDifference(0.6)
                    .goalDiffDifference(0.5)
                    .h2hDominance(0.3)
                    .restAdvantage(3.0)
                    .eloDifference(130.0)
                    .homeWeightedForm(2.6)
                    .awayWeightedForm(1.5)
                    .formSymmetry(0.6)
                    .goalSymmetry(0.3)
                    .drawTendency(0.25)
                    .defensiveTightness(0.9)
                    .actualResult("H")
                    .build();

            Instances dataset = WekaSchemaBuilder.toWekaInstances(List.of(features), "TestData");
            Instance instance = dataset.instance(0);

            assertThat(instance.value(WekaSchemaBuilder.IDX_HOME_FORM)).isEqualTo(2.4);
            assertThat(instance.value(WekaSchemaBuilder.IDX_AWAY_FORM)).isEqualTo(1.8);
            assertThat(instance.value(WekaSchemaBuilder.IDX_HOME_ELO)).isEqualTo(1650.0);
            assertThat(instance.value(WekaSchemaBuilder.IDX_ELO_DIFF)).isEqualTo(130.0);
            assertThat(instance.value(WekaSchemaBuilder.IDX_FORM_SYMMETRY)).isEqualTo(0.6);
            assertThat(instance.value(WekaSchemaBuilder.IDX_GOAL_SYMMETRY)).isEqualTo(0.3);
            assertThat(instance.value(WekaSchemaBuilder.IDX_DRAW_TENDENCY)).isEqualTo(0.25);
            assertThat(instance.value(WekaSchemaBuilder.IDX_DEFENSIVE_TIGHTNESS)).isEqualTo(0.9);
            assertThat(instance.stringValue(WekaSchemaBuilder.IDX_LABEL)).isEqualTo("H");
        }

        @Test
        @DisplayName("handles NaN values safely")
        void handlesNaNValues() {
            MatchFeatures features = MatchFeatures.builder()
                    .homeTeam("Test")
                    .awayTeam("Test2")
                    .homeFormPoints(Double.NaN)
                    .awayFormPoints(Double.POSITIVE_INFINITY)
                    .actualResult("D")
                    .build();

            Instances dataset = WekaSchemaBuilder.toWekaInstances(List.of(features), "TestData");
            Instance instance = dataset.instance(0);

            // NaN and Infinity should be converted to 0.0
            assertThat(instance.value(WekaSchemaBuilder.IDX_HOME_FORM)).isEqualTo(0.0);
            assertThat(instance.value(WekaSchemaBuilder.IDX_AWAY_FORM)).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("Constants consistency")
    class ConstantsTests {

        @Test
        @DisplayName("TOTAL_ATTRIBUTES = FEATURE_COUNT + 1")
        void totalAttributesIsFeatureCountPlusOne() {
            assertThat(WekaSchemaBuilder.TOTAL_ATTRIBUTES)
                    .isEqualTo(WekaSchemaBuilder.FEATURE_COUNT + 1);
        }

        @Test
        @DisplayName("IDX_LABEL = FEATURE_COUNT")
        void idxLabelEqualsFeatureCount() {
            assertThat(WekaSchemaBuilder.IDX_LABEL)
                    .isEqualTo(WekaSchemaBuilder.FEATURE_COUNT);
        }
    }

    // ── Helpers ──

    private MatchFeatures createMinimalFeatures(String result) {
        return MatchFeatures.builder()
                .homeTeam("TeamA")
                .awayTeam("TeamB")
                .homeFormPoints(1.0)
                .awayFormPoints(1.0)
                .actualResult(result)
                .build();
    }
}

