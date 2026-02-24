package com.app.footballprediction.service;

import com.app.footballprediction.dto.H2HInsightsResponse;
import com.app.footballprediction.dto.H2HInsightsResponse.*;
import com.app.footballprediction.dto.PreMatchInsightsResponse;
import com.app.footballprediction.dto.PreMatchInsightsResponse.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for InsightsValidationService.
 * Tests validation rules for consistency and correctness of match insights.
 */
@DisplayName("InsightsValidationService Unit Tests")
class InsightsValidationServiceTest {

    private InsightsValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new InsightsValidationService();
    }

    @Nested
    @DisplayName("PreMatchInsights Validation")
    class PreMatchInsightsValidationTests {

        @Test
        @DisplayName("should pass validation for consistent form comparison")
        void validateFormComparison_consistent_noErrors() {
            // Given - Correct PPG and points calculation
            // WDWDW = W(3) + D(1) + W(3) + D(1) + W(3) = 11 points
            // But for simplicity let's use WWDDD = 3+3+1+1+1 = 9 points
            // Actually let's recalculate: WDWWW = W(3)+D(1)+W(3)+W(3)+W(3) = 13 points
            // And DLWDD = D(1)+L(0)+W(3)+D(1)+D(1) = 6 points
            FormComparison form = FormComparison.builder()
                    .homeFormPoints(13)  // WDWWW = 13 points (3+1+3+3+3)
                    .homePointsPerGame(2.6)  // 13/5 = 2.6
                    .homeFormString("WDWWW")
                    .homeFormRating(86.67)
                    .homeMaxPoints(15)
                    .awayFormPoints(6)  // DLWDD = 6 points (1+0+3+1+1)
                    .awayPointsPerGame(1.2)  // 6/5 = 1.2
                    .awayFormString("DLWDD")
                    .awayFormRating(40.0)
                    .awayMaxPoints(15)
                    .formAdvantage("Home Team (Strong)")
                    .pointsDifference(7)  // 13 - 6 = 7
                    .dataScope("Last 5 Matches")
                    .build();

            PreMatchInsightsResponse response = PreMatchInsightsResponse.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .formComparison(form)
                    .streakIndicators(Collections.emptyList())
                    .restAnalysis(RestAnalysis.builder()
                            .homeTeamRestDays(5)
                            .awayTeamRestDays(3)
                            .restDifference(2)
                            .fatigueWarnings(Collections.emptyList())
                            .dataScope("Days since last match")
                            .build())
                    .goalThreatMeter(GoalThreatMeter.builder()
                            .homeTeamAvgScored(1.5)
                            .homeTeamAvgConceded(0.8)
                            .awayTeamAvgScored(1.2)
                            .awayTeamAvgConceded(1.0)
                            .homeExpectedGoals(1.25)
                            .awayExpectedGoals(1.1)
                            .totalExpectedGoals(2.35)
                            .homeThreatRating(62.5)
                            .awayThreatRating(55.0)
                            .homeGoalThreatIndex(75.0)
                            .awayGoalThreatIndex(60.0)
                            .dataScope("Season Average (All Matches)")
                            .threatIndexFormula("(Avg Goals Scored / 2.0) * 100")
                            .build())
                    .marketPredictions(MarketPredictions.builder()
                            .expectedHomeGoals(1.5)
                            .expectedAwayGoals(1.2)
                            .expectedTotalGoals(2.7)
                            .dataScope("Season Average (All Matches)")
                            .build())
                    .keyInsights(Collections.emptyList())
                    .build();

            // When
            List<String> errors = validationService.validatePreMatchInsights(response);

            // Then
            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("should detect PPG inconsistency")
        void validateFormComparison_ppgInconsistent_returnsError() {
            // Given - Wrong PPG calculation (should be 2.0, but is 1.5)
            FormComparison form = FormComparison.builder()
                    .homeFormPoints(10)
                    .homePointsPerGame(1.5)  // WRONG! Should be 2.0
                    .homeFormString("WDWWW")
                    .homeMaxPoints(15)
                    .awayFormPoints(5)
                    .awayPointsPerGame(1.0)
                    .awayFormString("DLWDD")
                    .awayMaxPoints(15)
                    .pointsDifference(5)
                    .build();

            PreMatchInsightsResponse response = PreMatchInsightsResponse.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .formComparison(form)
                    .build();

            // When
            List<String> errors = validationService.validatePreMatchInsights(response);

            // Then
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0)).contains("Home PPG inconsistency");
        }

        @Test
        @DisplayName("should detect form string not matching points")
        void validateFormComparison_formStringMismatch_returnsError() {
            // Given - Form string implies different points
            FormComparison form = FormComparison.builder()
                    .homeFormPoints(15)  // WRONG! WDWWW = 10 points, not 15
                    .homePointsPerGame(3.0)  // Consistent with 15 but not with form string
                    .homeFormString("WDWWW")  // This implies only 10 points
                    .homeMaxPoints(15)
                    .awayFormPoints(5)
                    .awayPointsPerGame(1.0)
                    .awayFormString("DLWDD")
                    .awayMaxPoints(15)
                    .pointsDifference(10)
                    .build();

            PreMatchInsightsResponse response = PreMatchInsightsResponse.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .formComparison(form)
                    .build();

            // When
            List<String> errors = validationService.validatePreMatchInsights(response);

            // Then
            assertThat(errors).isNotEmpty();
            assertThat(errors.stream().anyMatch(e -> e.contains("form string"))).isTrue();
        }

        @Test
        @DisplayName("should detect rest difference calculation error")
        void validateRestAnalysis_wrongDifference_returnsError() {
            // Given - Wrong rest difference
            RestAnalysis rest = RestAnalysis.builder()
                    .homeTeamRestDays(7)
                    .awayTeamRestDays(4)
                    .restDifference(2)  // WRONG! Should be 3
                    .fatigueWarnings(Collections.emptyList())
                    .build();

            PreMatchInsightsResponse response = PreMatchInsightsResponse.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .restAnalysis(rest)
                    .build();

            // When
            List<String> errors = validationService.validatePreMatchInsights(response);

            // Then
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0)).contains("Rest difference mismatch");
        }

        @Test
        @DisplayName("should detect total expected goals calculation error")
        void validateGoalThreatMeter_wrongTotal_returnsError() {
            // Given - Wrong total expected goals
            GoalThreatMeter meter = GoalThreatMeter.builder()
                    .homeTeamAvgScored(1.5)
                    .awayTeamAvgScored(1.2)
                    .homeExpectedGoals(1.5)
                    .awayExpectedGoals(1.2)
                    .totalExpectedGoals(3.0)  // WRONG! Should be 2.7
                    .homeThreatRating(50.0)
                    .awayThreatRating(50.0)
                    .homeGoalThreatIndex(75.0)
                    .awayGoalThreatIndex(60.0)
                    .build();

            PreMatchInsightsResponse response = PreMatchInsightsResponse.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .goalThreatMeter(meter)
                    .build();

            // When
            List<String> errors = validationService.validatePreMatchInsights(response);

            // Then
            assertThat(errors).isNotEmpty();
            assertThat(errors.stream().anyMatch(e -> e.contains("Total expected goals mismatch"))).isTrue();
        }
    }

    @Nested
    @DisplayName("H2H Insights Validation")
    class H2HInsightsValidationTests {

        @Test
        @DisplayName("should pass validation for consistent H2H record")
        void validateH2H_consistent_noErrors() {
            // Given - wins + draws + losses = total
            HistoricalRecord record = HistoricalRecord.builder()
                    .totalMatches(10)
                    .homeTeamWins(5)
                    .draws(3)
                    .awayTeamWins(2)  // 5 + 3 + 2 = 10 ✓
                    .homeTeamWinPercentage(50.0)
                    .drawPercentage(30.0)
                    .awayTeamWinPercentage(20.0)  // 50 + 30 + 20 = 100 ✓
                    .dataScope("All Historical Meetings")
                    .isConsistent(true)
                    .build();

            H2HInsightsResponse response = H2HInsightsResponse.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .historicalRecord(record)
                    .goalStats(H2HGoalStats.builder()
                            .avgTotalGoals(2.5)
                            .avgHomeTeamGoals(1.4)
                            .avgAwayTeamGoals(1.1)
                            .dataScope("H2H Only (All Meetings)")
                            .build())
                    .build();

            // When
            List<String> errors = validationService.validateH2HInsights(response);

            // Then
            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("should detect H2H total mismatch")
        void validateH2H_totalMismatch_returnsError() {
            // Given - wins + draws + losses != total
            HistoricalRecord record = HistoricalRecord.builder()
                    .totalMatches(10)
                    .homeTeamWins(5)
                    .draws(3)
                    .awayTeamWins(3)  // 5 + 3 + 3 = 11, not 10!
                    .homeTeamWinPercentage(50.0)
                    .drawPercentage(30.0)
                    .awayTeamWinPercentage(30.0)
                    .dataScope("All Historical Meetings")
                    .build();

            H2HInsightsResponse response = H2HInsightsResponse.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .historicalRecord(record)
                    .build();

            // When
            List<String> errors = validationService.validateH2HInsights(response);

            // Then
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0)).contains("H2H total mismatch");
        }

        @Test
        @DisplayName("should detect percentages not summing to 100")
        void validateH2H_percentagesMismatch_returnsError() {
            // Given - percentages don't sum to 100
            HistoricalRecord record = HistoricalRecord.builder()
                    .totalMatches(10)
                    .homeTeamWins(5)
                    .draws(3)
                    .awayTeamWins(2)
                    .homeTeamWinPercentage(50.0)
                    .drawPercentage(30.0)
                    .awayTeamWinPercentage(25.0)  // 50 + 30 + 25 = 105!
                    .dataScope("All Historical Meetings")
                    .build();

            H2HInsightsResponse response = H2HInsightsResponse.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .historicalRecord(record)
                    .build();

            // When
            List<String> errors = validationService.validateH2HInsights(response);

            // Then
            assertThat(errors).isNotEmpty();
            assertThat(errors.stream().anyMatch(e -> e.contains("percentages don't sum to 100"))).isTrue();
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle null response")
        void validatePreMatchInsights_nullResponse_returnsError() {
            // When
            List<String> errors = validationService.validatePreMatchInsights(null);

            // Then
            assertThat(errors).containsExactly("Response is null");
        }

        @Test
        @DisplayName("should handle empty form comparison")
        void validatePreMatchInsights_emptyFormComparison_noErrors() {
            // Given
            PreMatchInsightsResponse response = PreMatchInsightsResponse.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .formComparison(null)
                    .build();

            // When
            List<String> errors = validationService.validatePreMatchInsights(response);

            // Then
            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("should handle null H2H response")
        void validateH2HInsights_nullResponse_returnsError() {
            // When
            List<String> errors = validationService.validateH2HInsights(null);

            // Then
            assertThat(errors).containsExactly("Response is null");
        }
    }
}

