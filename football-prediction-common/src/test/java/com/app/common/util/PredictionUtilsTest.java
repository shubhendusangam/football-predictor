package com.app.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PredictionUtils.
 */
@DisplayName("PredictionUtils Unit Tests")
class PredictionUtilsTest {

    @Nested
    @DisplayName("safe()")
    class SafeTests {

        @Test
        @DisplayName("returns 0.0 for NaN")
        void returnsZeroForNaN() {
            assertThat(PredictionUtils.safe(Double.NaN)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns 0.0 for Positive Infinity")
        void returnsZeroForInfinity() {
            assertThat(PredictionUtils.safe(Double.POSITIVE_INFINITY)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns original value for normal double")
        void returnsOriginalForNormal() {
            assertThat(PredictionUtils.safe(1.5)).isEqualTo(1.5);
        }
    }

    @Nested
    @DisplayName("round()")
    class RoundTests {

        @Test
        @DisplayName("rounds to 2 decimal places")
        void roundsTo2DecimalPlaces() {
            assertThat(PredictionUtils.round(1.456)).isEqualTo(1.46);
            assertThat(PredictionUtils.round(1.454)).isEqualTo(1.45);
        }
    }

    @Nested
    @DisplayName("getConfidence()")
    class GetConfidenceTests {

        @Test
        @DisplayName("returns HIGH for decisive prediction")
        void returnsHighForDecisive() {
            double[] probs = {0.65, 0.20, 0.15};
            assertThat(PredictionUtils.getConfidence(probs)).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("returns MEDIUM for moderate prediction")
        void returnsMediumForModerate() {
            double[] probs = {0.50, 0.30, 0.20};
            assertThat(PredictionUtils.getConfidence(probs)).isEqualTo("MEDIUM");
        }

        @Test
        @DisplayName("returns LOW for nearly uniform distribution")
        void returnsLowForUniform() {
            double[] probs = {0.35, 0.33, 0.32};
            assertThat(PredictionUtils.getConfidence(probs)).isEqualTo("LOW");
        }
    }

    @Nested
    @DisplayName("getConfidenceScore()")
    class GetConfidenceScoreTests {

        @Test
        @DisplayName("returns high score for decisive prediction")
        void returnsHighForDecisive() {
            double[] probs = {0.70, 0.15, 0.15};
            double score = PredictionUtils.getConfidenceScore(probs);
            assertThat(score).isGreaterThan(0.5);
        }

        @Test
        @DisplayName("returns low score for uniform prediction")
        void returnsLowForUniform() {
            double[] probs = {0.34, 0.33, 0.33};
            double score = PredictionUtils.getConfidenceScore(probs);
            assertThat(score).isLessThan(0.1);
        }

        @Test
        @DisplayName("score is between 0 and 1")
        void scoreIsBounded() {
            double[] probs = {0.5, 0.3, 0.2};
            double score = PredictionUtils.getConfidenceScore(probs);
            assertThat(score).isBetween(0.0, 1.0);
        }
    }

    @Nested
    @DisplayName("labelToText()")
    class LabelToTextTests {

        @Test
        @DisplayName("converts H to HOME_WIN")
        void convertsH() {
            assertThat(PredictionUtils.labelToText("H")).isEqualTo("HOME_WIN");
        }

        @Test
        @DisplayName("converts D to DRAW")
        void convertsD() {
            assertThat(PredictionUtils.labelToText("D")).isEqualTo("DRAW");
        }

        @Test
        @DisplayName("converts A to AWAY_WIN")
        void convertsA() {
            assertThat(PredictionUtils.labelToText("A")).isEqualTo("AWAY_WIN");
        }

        @Test
        @DisplayName("returns UNKNOWN for invalid label")
        void convertsUnknown() {
            assertThat(PredictionUtils.labelToText("X")).isEqualTo("UNKNOWN");
        }
    }
}

