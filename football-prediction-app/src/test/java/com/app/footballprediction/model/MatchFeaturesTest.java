package com.app.footballprediction.model;

import com.app.common.model.MatchFeatures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MatchFeatures POJO.
 */
@DisplayName("MatchFeatures Unit Tests")
class MatchFeaturesTest {

    @Test
    @DisplayName("builds feature vector with all fields")
    void buildsCompleteFeatureVector() {
        MatchFeatures features = MatchFeatures.builder()
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .homeFormPoints(2.4)
                .awayFormPoints(1.8)
                .homeGoalsScoredAvg(2.1)
                .homeGoalsConcededAvg(0.9)
                .awayGoalsScoredAvg(1.5)
                .awayGoalsConcededAvg(1.2)
                .homeTotalGoalsAvg(3.0)
                .awayTotalGoalsAvg(2.7)
                .h2hHomeWinRate(0.45)
                .h2hDrawRate(0.30)
                .h2hAwayWinRate(0.25)
                .homeShotsOnTargetAvg(5.5)
                .awayShotsOnTargetAvg(4.2)
                .homeCornersAvg(6.0)
                .awayCornersAvg(4.5)
                .actualResult("H")
                .build();

        assertThat(features.getHomeTeam()).isEqualTo("Arsenal");
        assertThat(features.getAwayTeam()).isEqualTo("Chelsea");
        assertThat(features.getHomeFormPoints()).isEqualTo(2.4);
        assertThat(features.getAwayFormPoints()).isEqualTo(1.8);
        assertThat(features.getH2hHomeWinRate()).isEqualTo(0.45);
        assertThat(features.getActualResult()).isEqualTo("H");
    }

    @Test
    @DisplayName("null actualResult for prediction (no label)")
    void nullActualResultForPrediction() {
        MatchFeatures features = MatchFeatures.builder()
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .homeFormPoints(2.0)
                .awayFormPoints(1.5)
                .build();

        assertThat(features.getActualResult()).isNull();
    }

    @Test
    @DisplayName("default values are zero for unset numeric fields")
    void defaultsToZeroForUnsetNumericFields() {
        MatchFeatures features = MatchFeatures.builder()
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .build();

        assertThat(features.getHomeFormPoints()).isEqualTo(0.0);
        assertThat(features.getH2hHomeWinRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("H2H rates sum to approximately 1.0")
    void h2hRatesSumToOne() {
        MatchFeatures features = MatchFeatures.builder()
                .h2hHomeWinRate(0.4)
                .h2hDrawRate(0.3)
                .h2hAwayWinRate(0.3)
                .build();

        double sum = features.getH2hHomeWinRate() +
                features.getH2hDrawRate() +
                features.getH2hAwayWinRate();

        assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }
}

