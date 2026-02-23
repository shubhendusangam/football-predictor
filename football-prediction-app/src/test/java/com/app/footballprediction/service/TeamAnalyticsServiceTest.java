package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.model.Prediction;
import com.app.common.model.Team;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.PredictionRepository;
import com.app.common.repository.TeamRepository;
import com.app.footballprediction.dto.TeamAnalyticsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TeamAnalyticsService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeamAnalyticsService Unit Tests")
class TeamAnalyticsServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private PredictionRepository predictionRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamStatsService teamStatsService;

    @InjectMocks
    private TeamAnalyticsService teamAnalyticsService;

    private static final String TEAM_NAME = "Arsenal";
    private static final String OPPONENT = "Chelsea";
    private static final LocalDate TODAY = LocalDate.now();

    @Nested
    @DisplayName("getTeamAnalytics()")
    class GetTeamAnalyticsTests {

        @Test
        @DisplayName("Should return analytics for valid team")
        void shouldReturnAnalyticsForValidTeam() {
            // Given
            Match match = createMatch(TEAM_NAME, OPPONENT, "H", TODAY.minusDays(7), "2023-24");
            when(matchRepository.findByTeamBeforeDate(anyString(), any(LocalDate.class)))
                    .thenReturn(List.of(match));
            when(matchRepository.findHomeMatchesByTeamBeforeDate(anyString(), any(LocalDate.class)))
                    .thenReturn(List.of(match));
            when(matchRepository.findAwayMatchesByTeamBeforeDate(anyString(), any(LocalDate.class)))
                    .thenReturn(List.of());
            when(predictionRepository.findByTeamNameIgnoreCaseOrderByMatchDateDesc(anyString()))
                    .thenReturn(List.of());
            when(teamRepository.findByNameIgnoreCase(anyString()))
                    .thenReturn(Optional.of(createTeam()));

            // When
            TeamAnalyticsDto result = teamAnalyticsService.getTeamAnalytics(TEAM_NAME);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTeamInfo()).isNotNull();
            assertThat(result.getTeamInfo().getName()).isEqualTo(TEAM_NAME);
            assertThat(result.getSeasonHistory()).isNotEmpty();
            assertThat(result.getLastUpdated()).isNotNull();
        }

        @Test
        @DisplayName("Should throw exception for empty team name")
        void shouldThrowExceptionForEmptyTeamName() {
            assertThatThrownBy(() -> teamAnalyticsService.getTeamAnalytics(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be empty");
        }

        @Test
        @DisplayName("Should throw exception for null team name")
        void shouldThrowExceptionForNullTeamName() {
            assertThatThrownBy(() -> teamAnalyticsService.getTeamAnalytics(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be empty");
        }

        @Test
        @DisplayName("Should throw exception for unknown team")
        void shouldThrowExceptionForUnknownTeam() {
            when(matchRepository.findByTeamBeforeDate(anyString(), any(LocalDate.class)))
                    .thenReturn(List.of());
            when(matchRepository.findByTeamBeforeDateIgnoreCase(anyString(), any(LocalDate.class)))
                    .thenReturn(List.of());
            when(matchRepository.findTeamNamesContaining(anyString()))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> teamAnalyticsService.getTeamAnalytics("UnknownTeam"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Team not found");
        }
    }

    @Nested
    @DisplayName("Season History Building")
    class SeasonHistoryTests {

        @Test
        @DisplayName("Should calculate season stats correctly")
        void shouldCalculateSeasonStatsCorrectly() {
            // Given - 3 wins, 1 draw, 1 loss
            List<Match> matches = List.of(
                    createMatch(TEAM_NAME, "Team1", "H", TODAY.minusDays(35), "2023-24"),  // Win
                    createMatch(TEAM_NAME, "Team2", "H", TODAY.minusDays(28), "2023-24"),  // Win
                    createMatch(TEAM_NAME, "Team3", "H", TODAY.minusDays(21), "2023-24"),  // Win
                    createMatch(TEAM_NAME, "Team4", "D", TODAY.minusDays(14), "2023-24"),  // Draw
                    createMatch(TEAM_NAME, "Team5", "A", TODAY.minusDays(7), "2023-24")   // Loss
            );

            when(matchRepository.findByTeamBeforeDate(anyString(), any(LocalDate.class)))
                    .thenReturn(matches);
            when(matchRepository.findHomeMatchesByTeamBeforeDate(anyString(), any(LocalDate.class)))
                    .thenReturn(matches);
            when(matchRepository.findAwayMatchesByTeamBeforeDate(anyString(), any(LocalDate.class)))
                    .thenReturn(List.of());
            when(predictionRepository.findByTeamNameIgnoreCaseOrderByMatchDateDesc(anyString()))
                    .thenReturn(List.of());
            when(teamRepository.findByNameIgnoreCase(anyString()))
                    .thenReturn(Optional.empty());

            // When
            TeamAnalyticsDto result = teamAnalyticsService.getTeamAnalytics(TEAM_NAME);

            // Then
            assertThat(result.getSeasonHistory()).hasSize(1);
            var season = result.getSeasonHistory().get(0);
            assertThat(season.getSeason()).isEqualTo("2023-24");
            assertThat(season.getMatchesPlayed()).isEqualTo(5);
            assertThat(season.getWins()).isEqualTo(3);
            assertThat(season.getDraws()).isEqualTo(1);
            assertThat(season.getLosses()).isEqualTo(1);
            assertThat(season.getPoints()).isEqualTo(10); // 3*3 + 1
        }
    }

    @Nested
    @DisplayName("Model Accuracy Building")
    class ModelAccuracyTests {

        @Test
        @DisplayName("Should return empty accuracy when no predictions")
        void shouldReturnEmptyAccuracyWhenNoPredictions() {
            // Given
            Match match = createMatch(TEAM_NAME, OPPONENT, "H", TODAY.minusDays(7), "2023-24");
            when(matchRepository.findByTeamBeforeDate(anyString(), any(LocalDate.class)))
                    .thenReturn(List.of(match));
            when(matchRepository.findHomeMatchesByTeamBeforeDate(anyString(), any(LocalDate.class)))
                    .thenReturn(List.of(match));
            when(matchRepository.findAwayMatchesByTeamBeforeDate(anyString(), any(LocalDate.class)))
                    .thenReturn(List.of());
            when(predictionRepository.findByTeamNameIgnoreCaseOrderByMatchDateDesc(anyString()))
                    .thenReturn(List.of());
            when(teamRepository.findByNameIgnoreCase(anyString()))
                    .thenReturn(Optional.empty());

            // When
            TeamAnalyticsDto result = teamAnalyticsService.getTeamAnalytics(TEAM_NAME);

            // Then
            assertThat(result.getModelAccuracy()).isNotNull();
            assertThat(result.getModelAccuracy().getTotalPredictions()).isEqualTo(0);
            assertThat(result.getModelAccuracy().getOverallAccuracy()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should calculate accuracy correctly with predictions")
        void shouldCalculateAccuracyCorrectlyWithPredictions() {
            // Given
            Match match = createMatch(TEAM_NAME, OPPONENT, "H", TODAY.minusDays(7), "2023-24");
            List<Prediction> predictions = List.of(
                    createPrediction(TEAM_NAME, "WIN", "WIN", true, 0.7),   // Correct
                    createPrediction(TEAM_NAME, "WIN", "LOSS", true, 0.6),  // Wrong
                    createPrediction(TEAM_NAME, "DRAW", "DRAW", true, 0.5) // Correct
            );

            when(matchRepository.findByTeamBeforeDate(anyString(), any(LocalDate.class)))
                    .thenReturn(List.of(match));
            when(matchRepository.findHomeMatchesByTeamBeforeDate(anyString(), any(LocalDate.class)))
                    .thenReturn(List.of(match));
            when(matchRepository.findAwayMatchesByTeamBeforeDate(anyString(), any(LocalDate.class)))
                    .thenReturn(List.of());
            when(predictionRepository.findByTeamNameIgnoreCaseOrderByMatchDateDesc(anyString()))
                    .thenReturn(predictions);
            when(teamRepository.findByNameIgnoreCase(anyString()))
                    .thenReturn(Optional.empty());

            // When
            TeamAnalyticsDto result = teamAnalyticsService.getTeamAnalytics(TEAM_NAME);

            // Then
            assertThat(result.getModelAccuracy()).isNotNull();
            assertThat(result.getModelAccuracy().getTotalPredictions()).isEqualTo(3);
            assertThat(result.getModelAccuracy().getCorrectPredictions()).isEqualTo(2);
            // 66.67% accuracy
            assertThat(result.getModelAccuracy().getOverallAccuracy()).isBetween(66.0, 67.0);
        }
    }

    @Nested
    @DisplayName("Home/Away Trend Building")
    class HomeAwayTrendTests {

        @Test
        @DisplayName("Should identify home as stronger venue when home win rate higher")
        void shouldIdentifyHomeAsStrongerVenue() {
            // Given - 3 home wins, 0 away wins
            List<Match> homeMatches = List.of(
                    createMatch(TEAM_NAME, "Team1", "H", TODAY.minusDays(21), "2023-24"),
                    createMatch(TEAM_NAME, "Team2", "H", TODAY.minusDays(14), "2023-24"),
                    createMatch(TEAM_NAME, "Team3", "H", TODAY.minusDays(7), "2023-24")
            );
            List<Match> awayMatches = List.of(
                    createAwayMatch("Team4", TEAM_NAME, "H", TODAY.minusDays(28), "2023-24"),  // Loss
                    createAwayMatch("Team5", TEAM_NAME, "H", TODAY.minusDays(35), "2023-24")   // Loss
            );

            // Must also return for findByTeamBeforeDate (used for name resolution)
            when(matchRepository.findByTeamBeforeDate(anyString(), any(LocalDate.class)))
                    .thenReturn(homeMatches);
            when(matchRepository.findHomeMatchesByTeamBeforeDate(anyString(), any(LocalDate.class)))
                    .thenReturn(homeMatches);
            when(matchRepository.findAwayMatchesByTeamBeforeDate(anyString(), any(LocalDate.class)))
                    .thenReturn(awayMatches);
            when(predictionRepository.findByTeamNameIgnoreCaseOrderByMatchDateDesc(anyString()))
                    .thenReturn(List.of());
            when(teamRepository.findByNameIgnoreCase(anyString()))
                    .thenReturn(Optional.empty());

            // When
            TeamAnalyticsDto result = teamAnalyticsService.getTeamAnalytics(TEAM_NAME);

            // Then
            assertThat(result.getHomeAwayTrend()).isNotNull();
            assertThat(result.getHomeAwayTrend().getStrongerVenue()).isEqualTo("HOME");
            assertThat(result.getHomeAwayTrend().getHomeTrend().getWins()).isEqualTo(3);
            assertThat(result.getHomeAwayTrend().getAwayTrend().getLosses()).isEqualTo(2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════

    private Match createMatch(String homeTeam, String awayTeam, String result, LocalDate date, String season) {
        return Match.builder()
                .id(1L)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .fullTimeResult(result)
                .fullTimeHomeGoals(result.equals("H") ? 2 : result.equals("D") ? 1 : 0)
                .fullTimeAwayGoals(result.equals("A") ? 2 : result.equals("D") ? 1 : 0)
                .matchDate(date)
                .season(season)
                .build();
    }

    private Match createAwayMatch(String homeTeam, String awayTeam, String result, LocalDate date, String season) {
        return Match.builder()
                .id(2L)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .fullTimeResult(result)
                .fullTimeHomeGoals(result.equals("H") ? 2 : result.equals("D") ? 1 : 0)
                .fullTimeAwayGoals(result.equals("A") ? 2 : result.equals("D") ? 1 : 0)
                .matchDate(date)
                .season(season)
                .build();
    }

    private Prediction createPrediction(String teamName, String predicted, String actual, boolean isHome, double confidence) {
        return Prediction.builder()
                .id(1L)
                .matchId(1L)
                .teamName(teamName)
                .opponentName(OPPONENT)
                .isHome(isHome)
                .season("2023-24")
                .matchDate(TODAY.minusDays(7))
                .predictedResult(predicted)
                .actualResult(actual)
                .isCorrect(predicted.equals(actual))
                .confidence(confidence)
                .predictionDate(LocalDateTime.now())
                .build();
    }

    private Team createTeam() {
        return Team.builder()
                .id(1L)
                .name(TEAM_NAME)
                .shortName("ARS")
                .logoUrl("https://example.com/arsenal.png")
                .primaryColor("#EF0107")
                .build();
    }
}

