package com.app.footballprediction.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PredictResponse DTO.
 */
@DisplayName("PredictResponse DTO Unit Tests")
class PredictResponseTest {

    @Test
    @DisplayName("builds response with all fields")
    void buildsCompleteResponse() {
        PredictResponse.FeatureSummary features = PredictResponse.FeatureSummary.builder()
                .homeFormPoints(2.4)
                .awayFormPoints(1.8)
                .homeGoalsScoredAvg(2.1)
                .awayGoalsScoredAvg(1.5)
                .h2hHomeWinRate(0.45)
                .h2hDrawRate(0.30)
                .h2hAwayWinRate(0.25)
                .build();

        PredictResponse response = PredictResponse.builder()
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .prediction("HOME_WIN")
                .predictionCode("H")
                .probHomeWin(0.55)
                .probDraw(0.25)
                .probAwayWin(0.20)
                .confidence("MEDIUM")
                .features(features)
                .build();

        assertThat(response.getHomeTeam()).isEqualTo("Arsenal");
        assertThat(response.getAwayTeam()).isEqualTo("Chelsea");
        assertThat(response.getPrediction()).isEqualTo("HOME_WIN");
        assertThat(response.getPredictionCode()).isEqualTo("H");
        assertThat(response.getProbHomeWin()).isEqualTo(0.55);
        assertThat(response.getConfidence()).isEqualTo("MEDIUM");
        assertThat(response.getFeatures().getHomeFormPoints()).isEqualTo(2.4);
    }

    @Test
    @DisplayName("probabilities sum to approximately 1.0")
    void probabilitiesSumToOne() {
        PredictResponse response = PredictResponse.builder()
                .probHomeWin(0.55)
                .probDraw(0.25)
                .probAwayWin(0.20)
                .build();

        double sum = response.getProbHomeWin() +
                response.getProbDraw() +
                response.getProbAwayWin();

        assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("FeatureSummary builds correctly")
    void featureSummaryBuilds() {
        PredictResponse.FeatureSummary features = PredictResponse.FeatureSummary.builder()
                .homeFormPoints(2.4)
                .awayFormPoints(1.8)
                .homeGoalsScoredAvg(2.1)
                .awayGoalsScoredAvg(1.5)
                .h2hHomeWinRate(0.45)
                .h2hDrawRate(0.30)
                .h2hAwayWinRate(0.25)
                .build();

        assertThat(features.getHomeFormPoints()).isEqualTo(2.4);
        assertThat(features.getAwayFormPoints()).isEqualTo(1.8);
        assertThat(features.getH2hHomeWinRate()).isEqualTo(0.45);
    }

    @Test
    @DisplayName("confidence levels are valid values")
    void validConfidenceLevels() {
        PredictResponse highConfidence = PredictResponse.builder()
                .confidence("HIGH")
                .build();

        PredictResponse mediumConfidence = PredictResponse.builder()
                .confidence("MEDIUM")
                .build();

        PredictResponse lowConfidence = PredictResponse.builder()
                .confidence("LOW")
                .build();

        assertThat(highConfidence.getConfidence()).isIn("HIGH", "MEDIUM", "LOW");
        assertThat(mediumConfidence.getConfidence()).isIn("HIGH", "MEDIUM", "LOW");
        assertThat(lowConfidence.getConfidence()).isIn("HIGH", "MEDIUM", "LOW");
    }

    @Test
    @DisplayName("prediction codes are valid")
    void validPredictionCodes() {
        PredictResponse homeWin = PredictResponse.builder()
                .predictionCode("H")
                .prediction("HOME_WIN")
                .build();

        PredictResponse draw = PredictResponse.builder()
                .predictionCode("D")
                .prediction("DRAW")
                .build();

        PredictResponse awayWin = PredictResponse.builder()
                .predictionCode("A")
                .prediction("AWAY_WIN")
                .build();

        assertThat(homeWin.getPredictionCode()).isIn("H", "D", "A");
        assertThat(draw.getPredictionCode()).isIn("H", "D", "A");
        assertThat(awayWin.getPredictionCode()).isIn("H", "D", "A");
    }
}

