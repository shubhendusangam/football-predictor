package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.H2HInsightsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for H2HInsightsService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("H2HInsightsService Unit Tests")
class H2HInsightsServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private H2HInsightsService h2hInsightsService;

    private List<Match> h2hMatches;

    @BeforeEach
    void setUp() {
        // Create sample H2H match data between Arsenal and Chelsea
        h2hMatches = Arrays.asList(
                createMatch("Arsenal", "Chelsea", 2, 1, "H", LocalDate.of(2025, 12, 1)),
                createMatch("Chelsea", "Arsenal", 1, 1, "D", LocalDate.of(2025, 8, 15)),
                createMatch("Arsenal", "Chelsea", 3, 2, "H", LocalDate.of(2025, 3, 10)),
                createMatch("Chelsea", "Arsenal", 0, 2, "A", LocalDate.of(2024, 11, 20)),
                createMatch("Arsenal", "Chelsea", 1, 0, "H", LocalDate.of(2024, 4, 5)),
                createMatch("Chelsea", "Arsenal", 2, 2, "D", LocalDate.of(2023, 10, 12)),
                createMatch("Arsenal", "Chelsea", 0, 1, "A", LocalDate.of(2023, 5, 2))
        );
    }

    private Match createMatch(String home, String away, int homeGoals, int awayGoals,
                              String result, LocalDate date) {
        return Match.builder()
                .homeTeam(home)
                .awayTeam(away)
                .fullTimeHomeGoals(homeGoals)
                .fullTimeAwayGoals(awayGoals)
                .fullTimeResult(result)
                .matchDate(date)
                .build();
    }

    @Nested
    @DisplayName("getH2HInsights()")
    class GetH2HInsightsTests {

        @Test
        @DisplayName("should return comprehensive H2H insights for valid teams")
        void getH2HInsights_validTeams_returnsInsights() {
            // Given
            when(matchRepository.findH2HBeforeDate(eq("Arsenal"), eq("Chelsea"), any()))
                    .thenReturn(h2hMatches);

            // When
            H2HInsightsResponse insights = h2hInsightsService.getH2HInsights("Arsenal", "Chelsea");

            // Then
            assertThat(insights).isNotNull();
            assertThat(insights.getHomeTeam()).isEqualTo("Arsenal");
            assertThat(insights.getAwayTeam()).isEqualTo("Chelsea");
            assertThat(insights.getHistoricalRecord()).isNotNull();
            assertThat(insights.getRecentMeetings()).isNotNull();
            assertThat(insights.getGoalStats()).isNotNull();
            assertThat(insights.getCommonResults()).isNotNull();
            assertThat(insights.getVenueAdvantage()).isNotNull();
        }

        @Test
        @DisplayName("should return empty response when no H2H history exists")
        void getH2HInsights_noHistory_returnsEmptyResponse() {
            // Given
            when(matchRepository.findH2HBeforeDate(eq("Arsenal"), eq("Brighton"), any()))
                    .thenReturn(Collections.emptyList());

            // When
            H2HInsightsResponse insights = h2hInsightsService.getH2HInsights("Arsenal", "Brighton");

            // Then
            assertThat(insights).isNotNull();
            assertThat(insights.getHomeTeam()).isEqualTo("Arsenal");
            assertThat(insights.getAwayTeam()).isEqualTo("Brighton");
            assertThat(insights.getHistoricalRecord().getTotalMatches()).isEqualTo(0);
            assertThat(insights.getRecentMeetings()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Historical Record")
    class HistoricalRecordTests {

        @Test
        @DisplayName("should calculate correct win/draw/loss counts")
        void historicalRecord_calculatesCorrectCounts() {
            // Given
            when(matchRepository.findH2HBeforeDate(eq("Arsenal"), eq("Chelsea"), any()))
                    .thenReturn(h2hMatches);

            // When
            H2HInsightsResponse insights = h2hInsightsService.getH2HInsights("Arsenal", "Chelsea");

            // Then
            var record = insights.getHistoricalRecord();
            assertThat(record.getTotalMatches()).isEqualTo(7);
            // Arsenal wins: H (2-1), H (3-2), A (away win means Arsenal won away), H (1-0) = 4 wins
            // Chelsea wins: A (away win in Arsenal home) = 1 win
            // Draws: 2 draws
            assertThat(record.getHomeTeamWins()).isEqualTo(4); // Arsenal wins
            assertThat(record.getAwayTeamWins()).isEqualTo(1); // Chelsea wins
            assertThat(record.getDraws()).isEqualTo(2);
        }

        @Test
        @DisplayName("should correctly identify dominant team")
        void historicalRecord_identifiesDominantTeam() {
            // Given
            when(matchRepository.findH2HBeforeDate(eq("Arsenal"), eq("Chelsea"), any()))
                    .thenReturn(h2hMatches);

            // When
            H2HInsightsResponse insights = h2hInsightsService.getH2HInsights("Arsenal", "Chelsea");

            // Then
            assertThat(insights.getHistoricalRecord().getDominantTeam()).isEqualTo("HOME"); // Arsenal dominates
            assertThat(insights.getHistoricalRecord().getSummary()).contains("Arsenal leads");
        }
    }

    @Nested
    @DisplayName("Recent Meetings")
    class RecentMeetingsTests {

        @Test
        @DisplayName("should return last 5 meetings ordered by date")
        void recentMeetings_returnsLast5Matches() {
            // Given
            when(matchRepository.findH2HBeforeDate(eq("Arsenal"), eq("Chelsea"), any()))
                    .thenReturn(h2hMatches);

            // When
            H2HInsightsResponse insights = h2hInsightsService.getH2HInsights("Arsenal", "Chelsea");

            // Then
            assertThat(insights.getRecentMeetings()).hasSize(5);
            // First match should be the most recent
            assertThat(insights.getRecentMeetings().get(0).getDate()).isEqualTo("2025-12-01");
            assertThat(insights.getRecentMeetings().get(0).getScore()).isEqualTo("2-1");
        }

        @Test
        @DisplayName("should correctly identify winner for each match")
        void recentMeetings_identifiesWinners() {
            // Given
            when(matchRepository.findH2HBeforeDate(eq("Arsenal"), eq("Chelsea"), any()))
                    .thenReturn(h2hMatches);

            // When
            H2HInsightsResponse insights = h2hInsightsService.getH2HInsights("Arsenal", "Chelsea");

            // Then
            var firstMatch = insights.getRecentMeetings().get(0);
            assertThat(firstMatch.getWinner()).isEqualTo("Arsenal"); // Home win at Arsenal
        }
    }

    @Nested
    @DisplayName("Goal Stats")
    class GoalStatsTests {

        @Test
        @DisplayName("should calculate average goals per match")
        void goalStats_calculatesAvgGoals() {
            // Given
            when(matchRepository.findH2HBeforeDate(eq("Arsenal"), eq("Chelsea"), any()))
                    .thenReturn(h2hMatches);

            // When
            H2HInsightsResponse insights = h2hInsightsService.getH2HInsights("Arsenal", "Chelsea");

            // Then
            var goalStats = insights.getGoalStats();
            // Total goals: (2+1) + (1+1) + (3+2) + (0+2) + (1+0) + (2+2) + (0+1) = 3+2+5+2+1+4+1 = 18
            // Avg: 18 / 7 = 2.57
            assertThat(goalStats.getAvgTotalGoals()).isGreaterThan(2.0);
            assertThat(goalStats.getTotalGoalsAllTime()).isEqualTo(18);
        }

    }

    @Nested
    @DisplayName("Common Results")
    class CommonResultsTests {

        @Test
        @DisplayName("should identify most common outcome")
        void commonResults_identifiesMostCommonOutcome() {
            // Given
            when(matchRepository.findH2HBeforeDate(eq("Arsenal"), eq("Chelsea"), any()))
                    .thenReturn(h2hMatches);

            // When
            H2HInsightsResponse insights = h2hInsightsService.getH2HInsights("Arsenal", "Chelsea");

            // Then
            var commonResults = insights.getCommonResults();
            // Arsenal wins 4, Chelsea wins 1, Draws 2
            assertThat(commonResults.getMostCommonOutcome()).isEqualTo("HOME_WIN");
            assertThat(commonResults.getHomeWinCount()).isEqualTo(4);
            assertThat(commonResults.getDrawCount()).isEqualTo(2);
            assertThat(commonResults.getAwayWinCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return top scorelines")
        void commonResults_returnsTopScorelines() {
            // Given
            when(matchRepository.findH2HBeforeDate(eq("Arsenal"), eq("Chelsea"), any()))
                    .thenReturn(h2hMatches);

            // When
            H2HInsightsResponse insights = h2hInsightsService.getH2HInsights("Arsenal", "Chelsea");

            // Then
            assertThat(insights.getCommonResults().getTopScorelines()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Venue Advantage")
    class VenueAdvantageTests {

        @Test
        @DisplayName("should calculate home win percentages for both teams")
        void venueAdvantage_calculatesHomeWinPercentages() {
            // Given
            when(matchRepository.findH2HBeforeDate(eq("Arsenal"), eq("Chelsea"), any()))
                    .thenReturn(h2hMatches);

            // When
            H2HInsightsResponse insights = h2hInsightsService.getH2HInsights("Arsenal", "Chelsea");

            // Then
            var venueAdvantage = insights.getVenueAdvantage();
            // Arsenal at home: 4 matches, 3 wins (H results), 0 draws, 1 loss
            // Chelsea at home: 3 matches, 0 wins (H results), 2 draws, 1 Arsenal away win
            assertThat(venueAdvantage.getHomeTeamHomeMatches()).isEqualTo(4);
            assertThat(venueAdvantage.getAwayTeamHomeMatches()).isEqualTo(3);
        }

        @Test
        @DisplayName("should generate venue advantage description")
        void venueAdvantage_generatesDescription() {
            // Given
            when(matchRepository.findH2HBeforeDate(eq("Arsenal"), eq("Chelsea"), any()))
                    .thenReturn(h2hMatches);

            // When
            H2HInsightsResponse insights = h2hInsightsService.getH2HInsights("Arsenal", "Chelsea");

            // Then
            assertThat(insights.getVenueAdvantage().getHomeAdvantageDescription())
                    .isNotEmpty()
                    .isNotEqualTo("No H2H history available");
        }
    }
}

