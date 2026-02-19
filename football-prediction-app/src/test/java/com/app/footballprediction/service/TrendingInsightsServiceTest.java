package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.common.service.FeatureEngineeringService;
import com.app.footballprediction.dto.TrendingInsightsResponse;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TrendingInsightsService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TrendingInsightsService Unit Tests")
class TrendingInsightsServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private FeatureEngineeringService featureEngineeringService;

    @Mock
    private ModelTrainingService modelTrainingService;

    @InjectMocks
    private TrendingInsightsService trendingInsightsService;

    private Set<String> sampleTeams;

    @BeforeEach
    void setUp() {
        sampleTeams = new TreeSet<>(Set.of("Arsenal", "Chelsea", "Liverpool", "Man City", "Tottenham"));
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

    private List<Match> createWinningStreak(String team, int streakLength) {
        List<Match> matches = new ArrayList<>();
        LocalDate date = LocalDate.now();
        for (int i = 0; i < streakLength; i++) {
            matches.add(createMatch(team, "Opponent" + i, 2, 0, "H", date.minusDays(i * 7)));
        }
        return matches;
    }

    private List<Match> createLosingStreak(String team, int streakLength) {
        List<Match> matches = new ArrayList<>();
        LocalDate date = LocalDate.now();
        for (int i = 0; i < streakLength; i++) {
            matches.add(createMatch(team, "Opponent" + i, 0, 2, "A", date.minusDays(i * 7)));
        }
        return matches;
    }

    @Nested
    @DisplayName("getTrendingInsights()")
    class GetTrendingInsightsTests {

        @Test
        @DisplayName("should return response with all insight categories")
        void getTrendingInsights_returnsAllCategories() {
            // Given
            when(featureEngineeringService.getAllTeams()).thenReturn(sampleTeams);
            when(matchRepository.findByTeamBeforeDate(any(), any())).thenReturn(Collections.emptyList());
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getHotTeams()).isNotNull();
            assertThat(response.getColdTeams()).isNotNull();
            assertThat(response.getTopScorers()).isNotNull();
            assertThat(response.getDefensiveWalls()).isNotNull();
            assertThat(response.getUpsetAlerts()).isNotNull();
            assertThat(response.getGoalFestMatches()).isNotNull();
            assertThat(response.getGeneratedAt()).isNotNull();
            assertThat(response.getTotalTeamsAnalyzed()).isEqualTo(sampleTeams.size());
        }
    }

    @Nested
    @DisplayName("Hot Teams")
    class HotTeamsTests {

        @Test
        @DisplayName("should identify teams on 3+ win streak")
        void hotTeams_identifiesWinStreaks() {
            // Given
            when(featureEngineeringService.getAllTeams()).thenReturn(Set.of("Arsenal"));
            List<Match> arsenalMatches = createWinningStreak("Arsenal", 5);
            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any())).thenReturn(arsenalMatches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then
            assertThat(response.getHotTeams()).isNotEmpty();
            assertThat(response.getHotTeams().get(0).getTeamName()).isEqualTo("Arsenal");
            assertThat(response.getHotTeams().get(0).getWinStreak()).isEqualTo(5);
        }

        @Test
        @DisplayName("should not include teams with less than 3 wins")
        void hotTeams_excludesShortStreaks() {
            // Given
            when(featureEngineeringService.getAllTeams()).thenReturn(Set.of("Chelsea"));
            List<Match> chelseaMatches = createWinningStreak("Chelsea", 2);
            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any())).thenReturn(chelseaMatches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then
            assertThat(response.getHotTeams()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Cold Teams")
    class ColdTeamsTests {

        @Test
        @DisplayName("should identify teams without win in 5+ matches")
        void coldTeams_identifiesWinlessStreaks() {
            // Given
            when(featureEngineeringService.getAllTeams()).thenReturn(Set.of("Tottenham"));
            List<Match> tottenhamMatches = createLosingStreak("Tottenham", 6);
            when(matchRepository.findByTeamBeforeDate(eq("Tottenham"), any())).thenReturn(tottenhamMatches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then
            assertThat(response.getColdTeams()).isNotEmpty();
            assertThat(response.getColdTeams().get(0).getTeamName()).isEqualTo("Tottenham");
            assertThat(response.getColdTeams().get(0).getMatchesWithoutWin()).isEqualTo(6);
        }

        @Test
        @DisplayName("should not include teams with recent wins")
        void coldTeams_excludesWinningTeams() {
            // Given
            when(featureEngineeringService.getAllTeams()).thenReturn(Set.of("Liverpool"));
            List<Match> liverpoolMatches = createWinningStreak("Liverpool", 3);
            when(matchRepository.findByTeamBeforeDate(eq("Liverpool"), any())).thenReturn(liverpoolMatches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then
            assertThat(response.getColdTeams()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Top Scorers")
    class TopScorersTests {

        @Test
        @DisplayName("should rank teams by total goals scored")
        void topScorers_ranksTeamsByGoals() {
            // Given
            when(featureEngineeringService.getAllTeams()).thenReturn(Set.of("Man City", "Arsenal"));

            List<Match> manCityMatches = Arrays.asList(
                    createMatch("Man City", "Chelsea", 5, 0, "H", LocalDate.now().minusDays(7)),
                    createMatch("Man City", "Liverpool", 3, 1, "H", LocalDate.now().minusDays(14))
            );
            List<Match> arsenalMatches = Arrays.asList(
                    createMatch("Arsenal", "Tottenham", 2, 1, "H", LocalDate.now().minusDays(7)),
                    createMatch("Arsenal", "West Ham", 1, 0, "H", LocalDate.now().minusDays(14))
            );

            when(matchRepository.findByTeamBeforeDate(eq("Man City"), any())).thenReturn(manCityMatches);
            when(matchRepository.findByTeamBeforeDate(eq("Arsenal"), any())).thenReturn(arsenalMatches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then
            assertThat(response.getTopScorers()).isNotEmpty();
            assertThat(response.getTopScorers().get(0).getTeamName()).isEqualTo("Man City");
            assertThat(response.getTopScorers().get(0).getGoalsScored()).isEqualTo(8);
        }
    }

    @Nested
    @DisplayName("Defensive Walls")
    class DefensiveWallsTests {

        @Test
        @DisplayName("should rank teams by clean sheets")
        void defensiveWalls_ranksTeamsByCleanSheets() {
            // Given
            when(featureEngineeringService.getAllTeams()).thenReturn(Set.of("Chelsea"));

            List<Match> chelseaMatches = Arrays.asList(
                    createMatch("Chelsea", "Arsenal", 1, 0, "H", LocalDate.now().minusDays(7)),
                    createMatch("Chelsea", "Liverpool", 2, 0, "H", LocalDate.now().minusDays(14)),
                    createMatch("Chelsea", "Man City", 0, 0, "D", LocalDate.now().minusDays(21))
            );

            when(matchRepository.findByTeamBeforeDate(eq("Chelsea"), any())).thenReturn(chelseaMatches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then
            assertThat(response.getDefensiveWalls()).isNotEmpty();
            assertThat(response.getDefensiveWalls().get(0).getTeamName()).isEqualTo("Chelsea");
            assertThat(response.getDefensiveWalls().get(0).getCleanSheets()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Upset Alerts")
    class UpsetAlertsTests {

        @Test
        @DisplayName("should return empty list when model not loaded")
        void upsetAlerts_emptyWhenModelNotLoaded() {
            // Given
            when(featureEngineeringService.getAllTeams()).thenReturn(sampleTeams);
            when(matchRepository.findByTeamBeforeDate(any(), any())).thenReturn(Collections.emptyList());
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then
            assertThat(response.getUpsetAlerts()).isEmpty();
        }
    }
}

