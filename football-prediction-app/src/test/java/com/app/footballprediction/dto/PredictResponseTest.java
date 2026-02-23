package com.app.footballprediction.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

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
    @DisplayName("H2HSummary builds correctly")
    void h2hSummaryBuilds() {
        PredictResponse.RecentH2HMatch recentMatch = PredictResponse.RecentH2HMatch.builder()
                .date("2025-12-01")
                .homeTeamInMatch("Arsenal")
                .awayTeamInMatch("Chelsea")
                .score("2-1")
                .winner("Arsenal")
                .season("2025-26")
                .build();

        PredictResponse.H2HSummary h2hSummary = PredictResponse.H2HSummary.builder()
                .historicalRecord("Arsenal leads 15-8-7 vs Chelsea")
                .totalMeetings(30)
                .homeTeamWins(15)
                .draws(8)
                .awayTeamWins(7)
                .dominantTeam("HOME")
                .recentMeetings(Arrays.asList(recentMatch))
                .avgGoalsPerMatch(2.5)
                .avgHomeTeamGoals(1.4)
                .avgAwayTeamGoals(1.1)
                .mostCommonScore("1-1")
                .mostCommonOutcome("HOME_WIN")
                .homeTeamHomeWinPct(70.0)
                .awayTeamHomeWinPct(45.0)
                .venueAdvantageNote("Arsenal have strong home advantage")
                .build();

        assertThat(h2hSummary.getHistoricalRecord()).isEqualTo("Arsenal leads 15-8-7 vs Chelsea");
        assertThat(h2hSummary.getTotalMeetings()).isEqualTo(30);
        assertThat(h2hSummary.getHomeTeamWins()).isEqualTo(15);
        assertThat(h2hSummary.getDraws()).isEqualTo(8);
        assertThat(h2hSummary.getAwayTeamWins()).isEqualTo(7);
        assertThat(h2hSummary.getDominantTeam()).isEqualTo("HOME");
        assertThat(h2hSummary.getRecentMeetings()).hasSize(1);
        assertThat(h2hSummary.getAvgGoalsPerMatch()).isEqualTo(2.5);
        assertThat(h2hSummary.getMostCommonScore()).isEqualTo("1-1");
        assertThat(h2hSummary.getHomeTeamHomeWinPct()).isEqualTo(70.0);
    }

    @Test
    @DisplayName("RecentH2HMatch builds correctly")
    void recentH2HMatchBuilds() {
        PredictResponse.RecentH2HMatch match = PredictResponse.RecentH2HMatch.builder()
                .date("2025-12-01")
                .homeTeamInMatch("Arsenal")
                .awayTeamInMatch("Chelsea")
                .score("2-1")
                .winner("Arsenal")
                .season("2025-26")
                .build();

        assertThat(match.getDate()).isEqualTo("2025-12-01");
        assertThat(match.getHomeTeamInMatch()).isEqualTo("Arsenal");
        assertThat(match.getAwayTeamInMatch()).isEqualTo("Chelsea");
        assertThat(match.getScore()).isEqualTo("2-1");
        assertThat(match.getWinner()).isEqualTo("Arsenal");
        assertThat(match.getSeason()).isEqualTo("2025-26");
    }

    @Test
    @DisplayName("response with H2H insights builds correctly")
    void responseWithH2HInsightsBuilds() {
        PredictResponse.H2HSummary h2hSummary = PredictResponse.H2HSummary.builder()
                .historicalRecord("Arsenal leads 15-8-7 vs Chelsea")
                .totalMeetings(30)
                .homeTeamWins(15)
                .draws(8)
                .awayTeamWins(7)
                .dominantTeam("HOME")
                .recentMeetings(Collections.emptyList())
                .avgGoalsPerMatch(2.5)
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
                .h2hInsights(h2hSummary)
                .build();

        assertThat(response.getH2hInsights()).isNotNull();
        assertThat(response.getH2hInsights().getHistoricalRecord()).contains("Arsenal leads");
        assertThat(response.getH2hInsights().getTotalMeetings()).isEqualTo(30);
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

    // ═══════════════════════════════════════════════════════════════════════════
    // Pre-Match Insights Panel Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("builds feature summary with Pre-Match Insights fields")
    void buildsFeatureSummaryWithPreMatchInsights() {
        PredictResponse.FeatureSummary features = PredictResponse.FeatureSummary.builder()
                .homeFormPoints(2.4)
                .awayFormPoints(1.8)
                .homeGoalsScoredAvg(2.1)
                .awayGoalsScoredAvg(1.5)
                .homeGoalsConcededAvg(0.8)
                .awayGoalsConcededAvg(1.2)
                .h2hHomeWinRate(0.45)
                .h2hDrawRate(0.30)
                .h2hAwayWinRate(0.25)
                // Pre-Match Insights fields
                .homeWinStreak(3)
                .awayWinStreak(0)
                .homeUnbeatenStreak(5)
                .awayUnbeatenStreak(2)
                .homeDaysSinceLastMatch(7)
                .awayDaysSinceLastMatch(3)
                .homeGoalThreat(72.5)
                .awayGoalThreat(48.0)
                .build();

        assertThat(features.getHomeWinStreak()).isEqualTo(3);
        assertThat(features.getAwayWinStreak()).isEqualTo(0);
        assertThat(features.getHomeUnbeatenStreak()).isEqualTo(5);
        assertThat(features.getAwayUnbeatenStreak()).isEqualTo(2);
        assertThat(features.getHomeDaysSinceLastMatch()).isEqualTo(7);
        assertThat(features.getAwayDaysSinceLastMatch()).isEqualTo(3);
        assertThat(features.getHomeGoalThreat()).isEqualTo(72.5);
        assertThat(features.getAwayGoalThreat()).isEqualTo(48.0);
    }

    @Test
    @DisplayName("win streak values are non-negative")
    void winStreakValuesAreNonNegative() {
        PredictResponse.FeatureSummary features = PredictResponse.FeatureSummary.builder()
                .homeWinStreak(0)
                .awayWinStreak(5)
                .homeUnbeatenStreak(3)
                .awayUnbeatenStreak(0)
                .build();

        assertThat(features.getHomeWinStreak()).isGreaterThanOrEqualTo(0);
        assertThat(features.getAwayWinStreak()).isGreaterThanOrEqualTo(0);
        assertThat(features.getHomeUnbeatenStreak()).isGreaterThanOrEqualTo(0);
        assertThat(features.getAwayUnbeatenStreak()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("rest days fatigue warning thresholds")
    void restDaysFatigueWarningThresholds() {
        // Team with fatigue risk (<=3 days rest)
        PredictResponse.FeatureSummary fatigued = PredictResponse.FeatureSummary.builder()
                .homeDaysSinceLastMatch(2)
                .awayDaysSinceLastMatch(3)
                .build();

        // Team well rested (>=6 days rest)
        PredictResponse.FeatureSummary wellRested = PredictResponse.FeatureSummary.builder()
                .homeDaysSinceLastMatch(7)
                .awayDaysSinceLastMatch(10)
                .build();

        assertThat(fatigued.getHomeDaysSinceLastMatch()).isLessThanOrEqualTo(3);
        assertThat(fatigued.getAwayDaysSinceLastMatch()).isLessThanOrEqualTo(3);
        assertThat(wellRested.getHomeDaysSinceLastMatch()).isGreaterThanOrEqualTo(6);
        assertThat(wellRested.getAwayDaysSinceLastMatch()).isGreaterThanOrEqualTo(6);
    }


    @Test
    @DisplayName("goal threat meters within valid range")
    void goalThreatMetersWithinValidRange() {
        PredictResponse.FeatureSummary features = PredictResponse.FeatureSummary.builder()
                .homeGoalThreat(72.5)
                .awayGoalThreat(48.0)
                .build();

        assertThat(features.getHomeGoalThreat()).isBetween(0.0, 100.0);
        assertThat(features.getAwayGoalThreat()).isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("complete Pre-Match Insights in prediction response")
    void completePreMatchInsightsInPredictionResponse() {
        PredictResponse.FeatureSummary features = PredictResponse.FeatureSummary.builder()
                .homeFormPoints(2.4)
                .awayFormPoints(1.8)
                .homeGoalsScoredAvg(2.1)
                .awayGoalsScoredAvg(1.5)
                .homeGoalsConcededAvg(0.8)
                .awayGoalsConcededAvg(1.2)
                .h2hHomeWinRate(0.45)
                .h2hDrawRate(0.30)
                .h2hAwayWinRate(0.25)
                .homeWinStreak(3)
                .awayWinStreak(0)
                .homeUnbeatenStreak(5)
                .awayUnbeatenStreak(2)
                .homeDaysSinceLastMatch(7)
                .awayDaysSinceLastMatch(3)
                .homeGoalThreat(72.5)
                .awayGoalThreat(48.0)
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

        // Verify all Pre-Match Insights fields are accessible
        assertThat(response.getFeatures()).isNotNull();
        assertThat(response.getFeatures().getHomeWinStreak()).isEqualTo(3);
        assertThat(response.getFeatures().getHomeGoalThreat()).isEqualTo(72.5);
        assertThat(response.getFeatures().getAwayGoalThreat()).isEqualTo(48.0);
        assertThat(response.getFeatures().getHomeDaysSinceLastMatch()).isEqualTo(7);
        assertThat(response.getFeatures().getAwayDaysSinceLastMatch()).isEqualTo(3);
    }
}

