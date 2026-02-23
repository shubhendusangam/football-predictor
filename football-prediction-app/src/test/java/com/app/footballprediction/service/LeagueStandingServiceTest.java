package com.app.footballprediction.service;

import com.app.common.model.League;
import com.app.common.model.LeagueStanding;
import com.app.common.model.Match;
import com.app.common.model.Team;
import com.app.common.repository.LeagueRepository;
import com.app.common.repository.LeagueStandingRepository;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.TeamRepository;
import com.app.footballprediction.dto.LeagueStandingsResponse;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LeagueStandingService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LeagueStandingService Unit Tests")
class LeagueStandingServiceTest {

    @Mock
    private LeagueStandingRepository standingRepository;

    @Mock
    private LeagueRepository leagueRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private LeagueStandingService leagueStandingService;

    private League premierLeague;
    private List<LeagueStanding> sampleStandings;

    @BeforeEach
    void setUp() {
        // Create sample league
        premierLeague = League.builder()
                .id(1L)
                .code("PL")
                .name("Premier League")
                .countryCode("ENG")
                .countryName("England")
                .currentSeason("2025/26")
                .enabled(true)
                .displayOrder(1)
                .build();

        // Create sample standings
        sampleStandings = Arrays.asList(
                createStanding(1L, 1L, "2025/26", "Arsenal", 1, 23, 17, 3, 3, 52, 21, 31, 54, "W W D W L"),
                createStanding(2L, 1L, "2025/26", "Liverpool", 2, 23, 16, 4, 3, 48, 20, 28, 52, "W W W L D"),
                createStanding(3L, 1L, "2025/26", "Manchester City", 3, 23, 15, 5, 3, 45, 18, 27, 50, "D W W W W"),
                createStanding(4L, 1L, "2025/26", "Chelsea", 4, 23, 14, 5, 4, 42, 22, 20, 47, "W L W D W"),
                createStanding(5L, 1L, "2025/26", "Newcastle", 5, 23, 12, 6, 5, 38, 25, 13, 42, "L W D W W"),
                createStanding(18L, 1L, "2025/26", "Everton", 18, 23, 5, 7, 11, 22, 35, -13, 22, "L L D L W"),
                createStanding(19L, 1L, "2025/26", "Luton", 19, 23, 4, 6, 13, 18, 42, -24, 18, "L L L D L"),
                createStanding(20L, 1L, "2025/26", "Sheffield United", 20, 23, 3, 5, 15, 14, 48, -34, 14, "L L L L L")
        );
    }

    private LeagueStanding createStanding(Long id, Long leagueId, String season, String teamName,
                                          int position, int played, int won, int drawn, int lost,
                                          int goalsFor, int goalsAgainst, int goalDifference,
                                          int points, String form) {
        return LeagueStanding.builder()
                .id(id)
                .leagueId(leagueId)
                .season(season)
                .teamName(teamName)
                .position(position)
                .played(played)
                .won(won)
                .drawn(drawn)
                .lost(lost)
                .goalsFor(goalsFor)
                .goalsAgainst(goalsAgainst)
                .goalDifference(goalDifference)
                .points(points)
                .form(form)
                .positionChange(0)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    private Match createMatch(String home, String away, int homeGoals, int awayGoals, LocalDate date) {
        String result = homeGoals > awayGoals ? "H" : homeGoals < awayGoals ? "A" : "D";
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
    @DisplayName("getCurrentLeagueTable()")
    class GetCurrentLeagueTableTests {

        @Test
        @DisplayName("should return league table for valid league ID")
        void getCurrentLeagueTable_validLeague_returnsTable() {
            // Given
            when(leagueRepository.findById(1L)).thenReturn(Optional.of(premierLeague));
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(1L, "2025/26"))
                    .thenReturn(sampleStandings);

            // When
            LeagueStandingsResponse response = leagueStandingService.getCurrentLeagueTable(1L);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getLeagueName()).isEqualTo("Premier League");
            assertThat(response.getLeagueCode()).isEqualTo("PL");
            assertThat(response.getSeason()).isEqualTo("2025/26");
            assertThat(response.getTotalTeams()).isEqualTo(8);
            assertThat(response.getStandings()).hasSize(8);
        }

        @Test
        @DisplayName("should throw exception for invalid league ID")
        void getCurrentLeagueTable_invalidLeague_throwsException() {
            // Given
            when(leagueRepository.findById(999L)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> leagueStandingService.getCurrentLeagueTable(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("League not found");
        }

        @Test
        @DisplayName("should calculate standings from matches when none exist")
        void getCurrentLeagueTable_noStandings_calculatesFromMatches() {
            // Given
            when(leagueRepository.findById(1L)).thenReturn(Optional.of(premierLeague));
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(1L, "2025/26"))
                    .thenReturn(Collections.emptyList());

            List<Match> matches = Arrays.asList(
                    createMatch("Arsenal", "Chelsea", 2, 1, LocalDate.of(2025, 9, 15)),
                    createMatch("Liverpool", "Arsenal", 1, 1, LocalDate.of(2025, 9, 22))
            );
            when(matchRepository.findAllByOrderByMatchDateAsc()).thenReturn(matches);
            when(teamRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());

            // When
            LeagueStandingsResponse response = leagueStandingService.getCurrentLeagueTable(1L);

            // Then
            assertThat(response).isNotNull();
            verify(standingRepository).saveAll(any());
        }
    }

    @Nested
    @DisplayName("getLeagueTableForSeason()")
    class GetLeagueTableForSeasonTests {

        @Test
        @DisplayName("should return standings for specific season")
        void getLeagueTableForSeason_validSeason_returnsStandings() {
            // Given
            when(leagueRepository.findById(1L)).thenReturn(Optional.of(premierLeague));
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(1L, "2024/25"))
                    .thenReturn(sampleStandings);

            // When
            LeagueStandingsResponse response = leagueStandingService.getLeagueTableForSeason(1L, "2024/25");

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getSeason()).isEqualTo("2024/25");
        }
    }

    @Nested
    @DisplayName("Zone determination")
    class ZoneDeterminationTests {

        @Test
        @DisplayName("should correctly identify Champions League zone (top 4)")
        void standings_topFour_shouldBeChampionsZone() {
            // Given
            when(leagueRepository.findById(1L)).thenReturn(Optional.of(premierLeague));
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(1L, "2025/26"))
                    .thenReturn(sampleStandings);

            // When
            LeagueStandingsResponse response = leagueStandingService.getCurrentLeagueTable(1L);

            // Then
            // Top 4 should be champions zone
            assertThat(response.getStandings().get(0).getZone()).isEqualTo("champions");
            assertThat(response.getStandings().get(1).getZone()).isEqualTo("champions");
            assertThat(response.getStandings().get(2).getZone()).isEqualTo("champions");
            assertThat(response.getStandings().get(3).getZone()).isEqualTo("champions");
        }

        @Test
        @DisplayName("should correctly identify Europa League zone (5-6)")
        void standings_fiveAndSix_shouldBeEuropaZone() {
            // Given
            when(leagueRepository.findById(1L)).thenReturn(Optional.of(premierLeague));
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(1L, "2025/26"))
                    .thenReturn(sampleStandings);

            // When
            LeagueStandingsResponse response = leagueStandingService.getCurrentLeagueTable(1L);

            // Then
            assertThat(response.getStandings().get(4).getZone()).isEqualTo("europa");
        }

        @Test
        @DisplayName("should correctly identify relegation zone (bottom 3)")
        void standings_bottomThree_shouldBeRelegationZone() {
            // Given
            when(leagueRepository.findById(1L)).thenReturn(Optional.of(premierLeague));
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(1L, "2025/26"))
                    .thenReturn(sampleStandings);

            // When
            LeagueStandingsResponse response = leagueStandingService.getCurrentLeagueTable(1L);

            // Then - positions 18, 19, 20 are relegation in a 20-team league
            // But our sample has only 8 teams, so bottom 3 = positions 6, 7, 8
            List<LeagueStandingsResponse.StandingDto> standings = response.getStandings();
            assertThat(standings.get(standings.size() - 1).getZone()).isEqualTo("relegation");
            assertThat(standings.get(standings.size() - 2).getZone()).isEqualTo("relegation");
            assertThat(standings.get(standings.size() - 3).getZone()).isEqualTo("relegation");
        }
    }

    @Nested
    @DisplayName("Sorting tests")
    class SortingTests {

        @Test
        @DisplayName("should sort by points descending")
        void standings_shouldSortByPoints() {
            // Given
            when(leagueRepository.findById(1L)).thenReturn(Optional.of(premierLeague));
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(1L, "2025/26"))
                    .thenReturn(sampleStandings);

            // When
            LeagueStandingsResponse response = leagueStandingService.getCurrentLeagueTable(1L);

            // Then - verify descending order by points
            List<LeagueStandingsResponse.StandingDto> standings = response.getStandings();
            for (int i = 1; i < standings.size(); i++) {
                assertThat(standings.get(i - 1).getPoints())
                        .isGreaterThanOrEqualTo(standings.get(i).getPoints());
            }
        }

        @Test
        @DisplayName("should use goal difference as tiebreaker")
        void standings_samePoints_shouldSortByGoalDifference() {
            // Given
            List<LeagueStanding> tiedStandings = Arrays.asList(
                    createStanding(1L, 1L, "2025/26", "Team A", 1, 10, 5, 2, 3, 20, 10, 10, 17, "W W D L W"),
                    createStanding(2L, 1L, "2025/26", "Team B", 2, 10, 5, 2, 3, 15, 10, 5, 17, "W D W L W")
            );

            when(leagueRepository.findById(1L)).thenReturn(Optional.of(premierLeague));
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(1L, "2025/26"))
                    .thenReturn(tiedStandings);

            // When
            LeagueStandingsResponse response = leagueStandingService.getCurrentLeagueTable(1L);

            // Then - Team A should be first (higher goal difference)
            assertThat(response.getStandings().get(0).getTeamName()).isEqualTo("Team A");
            assertThat(response.getStandings().get(1).getTeamName()).isEqualTo("Team B");
        }
    }

    @Nested
    @DisplayName("Empty standings")
    class EmptyStandingsTests {

        @Test
        @DisplayName("should return empty list when no teams in league")
        void getLeagueTable_noTeams_returnsEmptyList() {
            // Given
            when(leagueRepository.findById(1L)).thenReturn(Optional.of(premierLeague));
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(1L, "2025/26"))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findAllByOrderByMatchDateAsc()).thenReturn(Collections.emptyList());

            // When
            LeagueStandingsResponse response = leagueStandingService.getCurrentLeagueTable(1L);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getStandings()).isEmpty();
            assertThat(response.getTotalTeams()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getAvailableLeagues()")
    class GetAvailableLeaguesTests {

        @Test
        @DisplayName("should return list of enabled leagues")
        void getAvailableLeagues_returnsEnabledLeagues() {
            // Given
            List<League> leagues = Arrays.asList(
                    premierLeague,
                    League.builder().id(2L).code("PD").name("La Liga").enabled(true).build()
            );
            when(leagueRepository.findByEnabledTrueOrderByDisplayOrderAsc()).thenReturn(leagues);

            // When
            var result = leagueStandingService.getAvailableLeagues();

            // Then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getAvailableSeasons()")
    class GetAvailableSeasonsTests {

        @Test
        @DisplayName("should return distinct seasons for league")
        void getAvailableSeasons_returnsSeasons() {
            // Given
            List<String> seasons = Arrays.asList("2025/26", "2024/25", "2023/24");
            when(standingRepository.findDistinctSeasonsByLeagueId(1L)).thenReturn(seasons);

            // When
            var result = leagueStandingService.getAvailableSeasons(1L);

            // Then
            assertThat(result).hasSize(3);
            assertThat(result).containsExactly("2025/26", "2024/25", "2023/24");
        }
    }

    @Nested
    @DisplayName("getDefaultLeagueId()")
    class GetDefaultLeagueIdTests {

        @Test
        @DisplayName("should return Premier League ID when exists")
        void getDefaultLeagueId_premierLeagueExists_returnsId() {
            // Given
            when(leagueRepository.findByCode("PL")).thenReturn(Optional.of(premierLeague));

            // When
            Long result = leagueStandingService.getDefaultLeagueId();

            // Then
            assertThat(result).isEqualTo(1L);
        }

        @Test
        @DisplayName("should return 1L when Premier League not found")
        void getDefaultLeagueId_noLeague_returnsDefaultOne() {
            // Given
            when(leagueRepository.findByCode("PL")).thenReturn(Optional.empty());

            // When
            Long result = leagueStandingService.getDefaultLeagueId();

            // Then
            assertThat(result).isEqualTo(1L);
        }
    }
}

