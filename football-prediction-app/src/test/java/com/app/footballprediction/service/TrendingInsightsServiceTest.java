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

    private static final String TEST_SEASON = "2024-25";

    private Match createMatch(String home, String away, int homeGoals, int awayGoals,
                              String result, LocalDate date) {
        return Match.builder()
                .homeTeam(home)
                .awayTeam(away)
                .fullTimeHomeGoals(homeGoals)
                .fullTimeAwayGoals(awayGoals)
                .fullTimeResult(result)
                .matchDate(date)
                .season(TEST_SEASON)
                .build();
    }

    private List<Match> createWinningStreak(String team, int streakLength) {
        List<Match> matches = new ArrayList<>();
        LocalDate date = LocalDate.now();
        for (int i = 0; i < streakLength; i++) {
            Match match = createMatch(team, "Opponent" + i, 2, 0, "H", date.minusDays(i * 7));
            matches.add(match);
        }
        return matches;
    }

    private List<Match> createLosingStreak(String team, int streakLength) {
        List<Match> matches = new ArrayList<>();
        LocalDate date = LocalDate.now();
        for (int i = 0; i < streakLength; i++) {
            Match match = createMatch(team, "Opponent" + i, 0, 2, "A", date.minusDays(i * 7));
            matches.add(match);
        }
        return matches;
    }

    @Nested
    @DisplayName("getTrendingInsights()")
    class GetTrendingInsightsTests {

        @Test
        @DisplayName("should return response with all insight categories")
        void getTrendingInsights_returnsAllCategories() {
            // Given - mock season availability
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(new ArrayList<>(sampleTeams));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());
            when(matchRepository.findByTeamAndSeasonBeforeDate(any(), eq(TEST_SEASON), any())).thenReturn(Collections.emptyList());
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
            assertThat(response.getSeason()).isEqualTo(TEST_SEASON);
        }
    }

    @Nested
    @DisplayName("Hot Teams")
    class HotTeamsTests {

        @Test
        @DisplayName("should identify teams on 3+ win streak")
        void hotTeams_identifiesWinStreaks() {
            // Given
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("Arsenal"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            List<Match> arsenalMatches = createWinningStreak("Arsenal", 5);
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("Arsenal"), eq(TEST_SEASON), any())).thenReturn(arsenalMatches);
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
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("Chelsea"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            List<Match> chelseaMatches = createWinningStreak("Chelsea", 2);
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("Chelsea"), eq(TEST_SEASON), any())).thenReturn(chelseaMatches);
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
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("Tottenham"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            List<Match> tottenhamMatches = createLosingStreak("Tottenham", 6);
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("Tottenham"), eq(TEST_SEASON), any())).thenReturn(tottenhamMatches);
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
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("Liverpool"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            List<Match> liverpoolMatches = createWinningStreak("Liverpool", 3);
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("Liverpool"), eq(TEST_SEASON), any())).thenReturn(liverpoolMatches);
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
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("Man City", "Arsenal"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            List<Match> manCityMatches = Arrays.asList(
                    createMatch("Man City", "Chelsea", 5, 0, "H", LocalDate.now().minusDays(7)),
                    createMatch("Man City", "Liverpool", 3, 1, "H", LocalDate.now().minusDays(14))
            );
            List<Match> arsenalMatches = Arrays.asList(
                    createMatch("Arsenal", "Tottenham", 2, 1, "H", LocalDate.now().minusDays(7)),
                    createMatch("Arsenal", "West Ham", 1, 0, "H", LocalDate.now().minusDays(14))
            );

            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("Man City"), eq(TEST_SEASON), any())).thenReturn(manCityMatches);
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("Arsenal"), eq(TEST_SEASON), any())).thenReturn(arsenalMatches);
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
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("Chelsea"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            List<Match> chelseaMatches = Arrays.asList(
                    createMatch("Chelsea", "Arsenal", 1, 0, "H", LocalDate.now().minusDays(7)),
                    createMatch("Chelsea", "Liverpool", 2, 0, "H", LocalDate.now().minusDays(14)),
                    createMatch("Chelsea", "Man City", 0, 0, "D", LocalDate.now().minusDays(21))
            );

            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("Chelsea"), eq(TEST_SEASON), any())).thenReturn(chelseaMatches);
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
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(new ArrayList<>(sampleTeams));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());
            when(matchRepository.findByTeamAndSeasonBeforeDate(any(), eq(TEST_SEASON), any())).thenReturn(Collections.emptyList());
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then
            assertThat(response.getUpsetAlerts()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Edge Cases and Validation")
    class EdgeCaseTests {

        @Test
        @DisplayName("Hot Teams: should require minimum 5 matches to qualify")
        void hotTeams_requiresMinimumMatches() {
            // Given - team with only 3 matches (less than HOT_FORM_WINDOW)
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("NewTeam"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            List<Match> newTeamMatches = Arrays.asList(
                    createMatch("NewTeam", "Opponent1", 3, 0, "H", LocalDate.now().minusDays(7)),
                    createMatch("NewTeam", "Opponent2", 2, 0, "H", LocalDate.now().minusDays(14)),
                    createMatch("NewTeam", "Opponent3", 1, 0, "H", LocalDate.now().minusDays(21))
            );
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("NewTeam"), eq(TEST_SEASON), any())).thenReturn(newTeamMatches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then - Should not be included despite 3-win streak (less than 5 matches)
            assertThat(response.getHotTeams()).isEmpty();
        }

        @Test
        @DisplayName("Cold Teams: should require minimum 5 matches to qualify")
        void coldTeams_requiresMinimumMatches() {
            // Given - team with only 4 matches (less than COLD_STREAK_THRESHOLD)
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("NewTeam"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            List<Match> newTeamMatches = Arrays.asList(
                    createMatch("NewTeam", "Opponent1", 0, 2, "A", LocalDate.now().minusDays(7)),
                    createMatch("NewTeam", "Opponent2", 0, 1, "A", LocalDate.now().minusDays(14)),
                    createMatch("NewTeam", "Opponent3", 0, 3, "A", LocalDate.now().minusDays(21)),
                    createMatch("NewTeam", "Opponent4", 0, 2, "A", LocalDate.now().minusDays(28))
            );
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("NewTeam"), eq(TEST_SEASON), any())).thenReturn(newTeamMatches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then - Should not be flagged as cold despite 4-match losing streak (less than 5 matches total)
            assertThat(response.getColdTeams()).isEmpty();
        }

        @Test
        @DisplayName("Cold Teams: should correctly calculate draws vs losses")
        void coldTeams_calculatesDrawsAndLosses() {
            // Given - team with 3 draws and 3 losses (6 matches without win)
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("StrugglingTeam"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            List<Match> matches = Arrays.asList(
                    createMatch("StrugglingTeam", "Opponent1", 1, 1, "D", LocalDate.now().minusDays(7)),
                    createMatch("StrugglingTeam", "Opponent2", 0, 2, "A", LocalDate.now().minusDays(14)),
                    createMatch("StrugglingTeam", "Opponent3", 2, 2, "D", LocalDate.now().minusDays(21)),
                    createMatch("StrugglingTeam", "Opponent4", 0, 1, "A", LocalDate.now().minusDays(28)),
                    createMatch("StrugglingTeam", "Opponent5", 0, 0, "D", LocalDate.now().minusDays(35)),
                    createMatch("StrugglingTeam", "Opponent6", 0, 3, "A", LocalDate.now().minusDays(42)),
                    // 7th match is a win - should not be counted
                    createMatch("StrugglingTeam", "Opponent7", 2, 0, "H", LocalDate.now().minusDays(49))
            );
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("StrugglingTeam"), eq(TEST_SEASON), any())).thenReturn(matches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then
            assertThat(response.getColdTeams()).hasSize(1);
            TrendingInsightsResponse.ColdTeam coldTeam = response.getColdTeams().get(0);
            assertThat(coldTeam.getMatchesWithoutWin()).isEqualTo(6);
            assertThat(coldTeam.getDraws()).isEqualTo(3);
            assertThat(coldTeam.getLosses()).isEqualTo(3);
        }

        @Test
        @DisplayName("Top Scorers: should handle null goal values safely")
        void topScorers_handlesNullGoals() {
            // Given - match with null goals
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("TestTeam"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            Match matchWithNulls = Match.builder()
                    .homeTeam("TestTeam")
                    .awayTeam("Opponent")
                    .fullTimeHomeGoals(null)
                    .fullTimeAwayGoals(null)
                    .fullTimeResult("H")
                    .matchDate(LocalDate.now().minusDays(7))
                    .season(TEST_SEASON)
                    .build();
            // Add more matches to meet minimum
            List<Match> matches = new ArrayList<>();
            matches.add(matchWithNulls);
            for (int i = 0; i < 5; i++) {
                matches.add(createMatch("TestTeam", "Opp" + i, 2, 1, "H", LocalDate.now().minusDays(14 + i * 7)));
            }
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("TestTeam"), eq(TEST_SEASON), any())).thenReturn(matches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When/Then - Should not throw NPE
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();
            assertThat(response.getTopScorers()).isNotEmpty();
        }

        @Test
        @DisplayName("Defensive Walls: should calculate clean sheet percentage without division by zero")
        void defensiveWalls_noDivisionByZero() {
            // Given
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("DefensiveTeam"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            List<Match> matches = Arrays.asList(
                    createMatch("DefensiveTeam", "Opponent1", 1, 0, "H", LocalDate.now().minusDays(7)),
                    createMatch("DefensiveTeam", "Opponent2", 2, 0, "H", LocalDate.now().minusDays(14))
            );
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("DefensiveTeam"), eq(TEST_SEASON), any())).thenReturn(matches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then
            assertThat(response.getDefensiveWalls()).isNotEmpty();
            assertThat(response.getDefensiveWalls().get(0).getCleanSheetPercentage()).isEqualTo(100.0);
            assertThat(response.getDefensiveWalls().get(0).getAvgGoalsConceded()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Defensive Walls: should correctly identify home vs away conceding")
        void defensiveWalls_correctHomeAwayMapping() {
            // Given - team plays 2 home, 2 away matches
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("TestTeam"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            List<Match> matches = Arrays.asList(
                    // Home matches - team concedes via fullTimeAwayGoals
                    createMatch("TestTeam", "Opponent1", 2, 1, "H", LocalDate.now().minusDays(7)),  // Conceded 1
                    createMatch("TestTeam", "Opponent2", 1, 2, "A", LocalDate.now().minusDays(14)), // Conceded 2
                    // Away matches - team concedes via fullTimeHomeGoals
                    createMatch("Opponent3", "TestTeam", 3, 1, "H", LocalDate.now().minusDays(21)), // Conceded 3
                    createMatch("Opponent4", "TestTeam", 0, 2, "A", LocalDate.now().minusDays(28))  // Conceded 0 (clean sheet)
            );
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("TestTeam"), eq(TEST_SEASON), any())).thenReturn(matches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then
            assertThat(response.getDefensiveWalls()).isNotEmpty();
            TrendingInsightsResponse.DefensiveWall wall = response.getDefensiveWalls().get(0);
            // Total conceded: 1 + 2 + 3 + 0 = 6
            assertThat(wall.getGoalsConceded()).isEqualTo(6);
            // Avg: 6 / 4 = 1.5
            assertThat(wall.getAvgGoalsConceded()).isEqualTo(1.5);
            // Clean sheets: only 1 (last away match)
            assertThat(wall.getCleanSheets()).isEqualTo(1);
        }

        @Test
        @DisplayName("Hot Teams: should include hot form teams when streak teams are insufficient")
        void hotTeams_includesHotFormTeams() {
            // Given - team with 4 wins in 5 matches but not consecutive
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("FormTeam"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            List<Match> matches = Arrays.asList(
                    createMatch("FormTeam", "Opponent1", 2, 0, "H", LocalDate.now().minusDays(7)),  // Win
                    createMatch("FormTeam", "Opponent2", 3, 1, "H", LocalDate.now().minusDays(14)), // Win
                    createMatch("FormTeam", "Opponent3", 0, 0, "D", LocalDate.now().minusDays(21)), // Draw (breaks streak)
                    createMatch("FormTeam", "Opponent4", 2, 1, "H", LocalDate.now().minusDays(28)), // Win
                    createMatch("FormTeam", "Opponent5", 1, 0, "H", LocalDate.now().minusDays(35))  // Win
            );
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("FormTeam"), eq(TEST_SEASON), any())).thenReturn(matches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then - Should be included as hot form team (4 wins in 5 matches)
            assertThat(response.getHotTeams()).hasSize(1);
            assertThat(response.getHotTeams().get(0).getTeamName()).isEqualTo("FormTeam");
            assertThat(response.getHotTeams().get(0).getWinStreak()).isEqualTo(4); // Uses wins in window
        }

        @Test
        @DisplayName("Top Scorers: should correctly aggregate goals for away matches")
        void topScorers_correctAwayGoalAttribution() {
            // Given - team plays mostly away
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("AwayTeam"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            List<Match> matches = Arrays.asList(
                    createMatch("Opponent1", "AwayTeam", 1, 3, "A", LocalDate.now().minusDays(7)),  // Scored 3 (away)
                    createMatch("Opponent2", "AwayTeam", 0, 2, "A", LocalDate.now().minusDays(14)), // Scored 2 (away)
                    createMatch("AwayTeam", "Opponent3", 4, 1, "H", LocalDate.now().minusDays(21)), // Scored 4 (home)
                    createMatch("Opponent4", "AwayTeam", 2, 1, "H", LocalDate.now().minusDays(28)), // Scored 1 (away)
                    createMatch("Opponent5", "AwayTeam", 1, 2, "A", LocalDate.now().minusDays(35))  // Scored 2 (away)
            );
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("AwayTeam"), eq(TEST_SEASON), any())).thenReturn(matches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then
            assertThat(response.getTopScorers()).isNotEmpty();
            TrendingInsightsResponse.TopScorer scorer = response.getTopScorers().get(0);
            // Total goals: 3 + 2 + 4 + 1 + 2 = 12
            assertThat(scorer.getGoalsScored()).isEqualTo(12);
            assertThat(scorer.getAvgGoalsPerMatch()).isEqualTo(2.4);
        }

        @Test
        @DisplayName("should handle empty team set gracefully")
        void emptyTeamSet_handledGracefully() {
            // Given - no season available (simulates empty database)
            when(matchRepository.findCurrentSeason()).thenReturn(null);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then
            assertThat(response.getHotTeams()).isEmpty();
            assertThat(response.getColdTeams()).isEmpty();
            assertThat(response.getTopScorers()).isEmpty();
            assertThat(response.getDefensiveWalls()).isEmpty();
            assertThat(response.getTotalTeamsAnalyzed()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("should complete trending insights calculation within 300ms")
        void performance_completesWithin300ms() {
            // Given
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(new ArrayList<>(sampleTeams));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());
            when(matchRepository.findByTeamAndSeasonBeforeDate(any(), eq(TEST_SEASON), any())).thenReturn(Collections.emptyList());
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            long startTime = System.currentTimeMillis();
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();
            long endTime = System.currentTimeMillis();

            // Then
            assertThat(endTime - startTime).isLessThan(300);
            assertThat(response).isNotNull();
        }
    }

    @Nested
    @DisplayName("Win Percentage and Form Calculation")
    class WinPercentageTests {

        @Test
        @DisplayName("should not have integer division errors in form calculation")
        void formCalculation_noIntegerDivision() {
            // Given - 1 win in 5 matches = 20% win rate
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("TestTeam"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            List<Match> matches = Arrays.asList(
                    createMatch("TestTeam", "Opponent1", 1, 0, "H", LocalDate.now().minusDays(7)),  // Win
                    createMatch("TestTeam", "Opponent2", 0, 1, "A", LocalDate.now().minusDays(14)), // Loss
                    createMatch("TestTeam", "Opponent3", 1, 1, "D", LocalDate.now().minusDays(21)), // Draw
                    createMatch("TestTeam", "Opponent4", 0, 2, "A", LocalDate.now().minusDays(28)), // Loss
                    createMatch("TestTeam", "Opponent5", 1, 1, "D", LocalDate.now().minusDays(35))  // Draw
            );
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("TestTeam"), eq(TEST_SEASON), any())).thenReturn(matches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then - Form string should be "WLDLD"
            // Not a hot team (only 1 win), not a cold team (has a win in last 5)
            assertThat(response.getHotTeams()).isEmpty();
            assertThat(response.getColdTeams()).isEmpty();
        }

        @Test
        @DisplayName("should correctly calculate goals per match as double")
        void topScorers_goalsPerMatchIsDouble() {
            // Given - 5 goals in 3 matches = 1.67 avg
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("TestTeam"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            List<Match> matches = Arrays.asList(
                    createMatch("TestTeam", "Opponent1", 2, 0, "H", LocalDate.now().minusDays(7)),
                    createMatch("TestTeam", "Opponent2", 1, 0, "H", LocalDate.now().minusDays(14)),
                    createMatch("TestTeam", "Opponent3", 2, 1, "H", LocalDate.now().minusDays(21))
            );
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("TestTeam"), eq(TEST_SEASON), any())).thenReturn(matches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then
            assertThat(response.getTopScorers()).isNotEmpty();
            // 5 goals / 3 matches = 1.67 (not 1 from integer division)
            assertThat(response.getTopScorers().get(0).getAvgGoalsPerMatch()).isEqualTo(1.67);
        }
    }

    @Nested
    @DisplayName("Tie Handling")
    class TieHandlingTests {

        @Test
        @DisplayName("Top Scorers: should use avg goals per match as tiebreaker")
        void topScorers_usesAvgGoalsAsTiebreaker() {
            // Given - Two teams with same total goals, different matches
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("TeamA", "TeamB"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            // TeamA: 6 goals in 3 matches = 2.0 avg
            List<Match> teamAMatches = Arrays.asList(
                    createMatch("TeamA", "Opp1", 2, 0, "H", LocalDate.now().minusDays(7)),
                    createMatch("TeamA", "Opp2", 2, 0, "H", LocalDate.now().minusDays(14)),
                    createMatch("TeamA", "Opp3", 2, 0, "H", LocalDate.now().minusDays(21))
            );

            // TeamB: 6 goals in 6 matches = 1.0 avg
            List<Match> teamBMatches = Arrays.asList(
                    createMatch("TeamB", "Opp1", 1, 0, "H", LocalDate.now().minusDays(7)),
                    createMatch("TeamB", "Opp2", 1, 0, "H", LocalDate.now().minusDays(14)),
                    createMatch("TeamB", "Opp3", 1, 0, "H", LocalDate.now().minusDays(21)),
                    createMatch("TeamB", "Opp4", 1, 0, "H", LocalDate.now().minusDays(28)),
                    createMatch("TeamB", "Opp5", 1, 0, "H", LocalDate.now().minusDays(35)),
                    createMatch("TeamB", "Opp6", 1, 0, "H", LocalDate.now().minusDays(42))
            );

            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("TeamA"), eq(TEST_SEASON), any())).thenReturn(teamAMatches);
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("TeamB"), eq(TEST_SEASON), any())).thenReturn(teamBMatches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then - TeamA should be first (higher avg goals per match)
            assertThat(response.getTopScorers()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(response.getTopScorers().get(0).getTeamName()).isEqualTo("TeamA");
        }

        @Test
        @DisplayName("Defensive Walls: should use clean sheet percentage as tiebreaker")
        void defensiveWalls_usesPercentageAsTiebreaker() {
            // Given - Two teams with same clean sheets, different total matches
            when(matchRepository.findCurrentSeason()).thenReturn(TEST_SEASON);
            when(matchRepository.findAllSeasons()).thenReturn(List.of(TEST_SEASON));
            when(matchRepository.findAllDistinctTeamNamesBySeason(TEST_SEASON)).thenReturn(List.of("TeamA", "TeamB"));
            when(matchRepository.findBySeasonOrderByMatchDateDesc(TEST_SEASON)).thenReturn(Collections.emptyList());

            // TeamA: 2 clean sheets in 4 matches = 50%
            List<Match> teamAMatches = Arrays.asList(
                    createMatch("TeamA", "Opp1", 1, 0, "H", LocalDate.now().minusDays(7)),
                    createMatch("TeamA", "Opp2", 2, 1, "H", LocalDate.now().minusDays(14)),
                    createMatch("TeamA", "Opp3", 1, 0, "H", LocalDate.now().minusDays(21)),
                    createMatch("TeamA", "Opp4", 0, 2, "A", LocalDate.now().minusDays(28))
            );

            // TeamB: 2 clean sheets in 2 matches = 100%
            List<Match> teamBMatches = Arrays.asList(
                    createMatch("TeamB", "Opp1", 1, 0, "H", LocalDate.now().minusDays(7)),
                    createMatch("TeamB", "Opp2", 2, 0, "H", LocalDate.now().minusDays(14))
            );

            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("TeamA"), eq(TEST_SEASON), any())).thenReturn(teamAMatches);
            when(matchRepository.findByTeamAndSeasonBeforeDate(eq("TeamB"), eq(TEST_SEASON), any())).thenReturn(teamBMatches);
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            // When
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Then - TeamB should be first (higher clean sheet percentage)
            assertThat(response.getDefensiveWalls()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(response.getDefensiveWalls().get(0).getTeamName()).isEqualTo("TeamB");
        }
    }
}

