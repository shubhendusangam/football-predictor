package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.model.SeasonTeamStats;
import com.app.common.model.Team;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.SeasonTeamStatsRepository;
import com.app.common.repository.TeamRepository;
import com.app.common.service.EloRatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MatchCompletionService.
 * Tests the match completion flow including stats updates and Elo calculations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MatchCompletionService Unit Tests")
class MatchCompletionServiceTest {

    @Mock
    private SeasonTeamStatsRepository seasonTeamStatsRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private EloRatingService eloRatingService;

    @InjectMocks
    private MatchCompletionService matchCompletionService;

    @Captor
    private ArgumentCaptor<SeasonTeamStats> statsCaptor;

    private Team homeTeam;
    private Team awayTeam;
    private Match completedMatch;

    @BeforeEach
    void setUp() {
        homeTeam = Team.builder()
                .id(1L)
                .name("Arsenal")
                .build();

        awayTeam = Team.builder()
                .id(2L)
                .name("Chelsea")
                .build();

        completedMatch = Match.builder()
                .id(1L)
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .fullTimeHomeGoals(2)
                .fullTimeAwayGoals(1)
                .fullTimeResult("H")
                .season("2025-26")
                .matchDate(LocalDate.of(2026, 2, 23))
                .build();
    }

    @Nested
    @DisplayName("Match Validation")
    class ValidationTests {

        @Test
        @DisplayName("should throw exception for null match")
        void nullMatch_throwsException() {
            assertThatThrownBy(() -> matchCompletionService.processCompletedMatch((Match) null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be null");
        }

        @Test
        @DisplayName("should throw exception for missing home team")
        void missingHomeTeam_throwsException() {
            Match match = Match.builder()
                    .awayTeam("Chelsea")
                    .fullTimeHomeGoals(1)
                    .fullTimeAwayGoals(0)
                    .fullTimeResult("H")
                    .season("2025-26")
                    .build();

            assertThatThrownBy(() -> matchCompletionService.processCompletedMatch(match))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Home team");
        }

        @Test
        @DisplayName("should throw exception for missing score")
        void missingScore_throwsException() {
            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeResult("H")
                    .season("2025-26")
                    .build();

            assertThatThrownBy(() -> matchCompletionService.processCompletedMatch(match))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("final score");
        }

        @Test
        @DisplayName("should throw exception for missing result")
        void missingResult_throwsException() {
            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeHomeGoals(2)
                    .fullTimeAwayGoals(1)
                    .season("2025-26")
                    .build();

            assertThatThrownBy(() -> matchCompletionService.processCompletedMatch(match))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("result");
        }

        @Test
        @DisplayName("should throw exception for missing season")
        void missingSeason_throwsException() {
            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeHomeGoals(2)
                    .fullTimeAwayGoals(1)
                    .fullTimeResult("H")
                    .build();

            assertThatThrownBy(() -> matchCompletionService.processCompletedMatch(match))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Season");
        }
    }

    @Nested
    @DisplayName("Stats Creation for New Teams")
    class NewTeamStatsTests {

        @Test
        @DisplayName("should create new stats for teams without existing records")
        void newTeams_createsStats() {
            // Given
            when(teamRepository.findByNameIgnoreCase("Arsenal")).thenReturn(Optional.of(homeTeam));
            when(teamRepository.findByNameIgnoreCase("Chelsea")).thenReturn(Optional.of(awayTeam));
            when(seasonTeamStatsRepository.findBySeasonIdAndTeamId(anyString(), anyLong()))
                    .thenReturn(Optional.empty());
            when(eloRatingService.calculateMatchRatings(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(new double[]{1510.0, 1490.0});
            when(matchRepository.findByTeamAndSeasonBeforeDate(anyString(), anyString(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            matchCompletionService.processCompletedMatch(completedMatch);

            // Then
            verify(seasonTeamStatsRepository, times(2)).save(statsCaptor.capture());
            var savedStats = statsCaptor.getAllValues();

            // Home team (winner)
            SeasonTeamStats homeStats = savedStats.stream()
                    .filter(s -> s.getTeamName().equals("Arsenal"))
                    .findFirst()
                    .orElseThrow();
            assertThat(homeStats.getMatchesPlayed()).isEqualTo(1);
            assertThat(homeStats.getWins()).isEqualTo(1);
            assertThat(homeStats.getGoalsScored()).isEqualTo(2);
            assertThat(homeStats.getGoalsConceded()).isEqualTo(1);

            // Away team (loser)
            SeasonTeamStats awayStats = savedStats.stream()
                    .filter(s -> s.getTeamName().equals("Chelsea"))
                    .findFirst()
                    .orElseThrow();
            assertThat(awayStats.getMatchesPlayed()).isEqualTo(1);
            assertThat(awayStats.getLosses()).isEqualTo(1);
            assertThat(awayStats.getGoalsScored()).isEqualTo(1);
            assertThat(awayStats.getGoalsConceded()).isEqualTo(2);
        }

        @Test
        @DisplayName("should create team record if team doesn't exist")
        void teamNotExists_createsTeam() {
            // Given
            when(teamRepository.findByNameIgnoreCase("Arsenal")).thenReturn(Optional.empty());
            when(teamRepository.save(any(Team.class))).thenReturn(homeTeam);
            when(teamRepository.findByNameIgnoreCase("Chelsea")).thenReturn(Optional.of(awayTeam));
            when(seasonTeamStatsRepository.findBySeasonIdAndTeamId(anyString(), anyLong()))
                    .thenReturn(Optional.empty());
            when(eloRatingService.calculateMatchRatings(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(new double[]{1510.0, 1490.0});
            when(matchRepository.findByTeamAndSeasonBeforeDate(anyString(), anyString(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            matchCompletionService.processCompletedMatch(completedMatch);

            // Then
            verify(teamRepository).save(argThat(team -> team.getName().equals("Arsenal")));
        }
    }

    @Nested
    @DisplayName("Stats Updates")
    class StatsUpdateTests {

        private SeasonTeamStats existingHomeStats;
        private SeasonTeamStats existingAwayStats;

        @BeforeEach
        void setUpExistingStats() {
            existingHomeStats = SeasonTeamStats.builder()
                    .id(1L)
                    .seasonId("2025-26")
                    .teamId(1L)
                    .teamName("Arsenal")
                    .matchesPlayed(10)
                    .wins(7)
                    .draws(2)
                    .losses(1)
                    .goalsScored(25)
                    .goalsConceded(10)
                    .cleanSheets(4)
                    .formString("WWDWW")
                    .formPointsLast5(13)
                    .currentStreak("W2")
                    .eloRating(1600.0)
                    .build();

            existingAwayStats = SeasonTeamStats.builder()
                    .id(2L)
                    .seasonId("2025-26")
                    .teamId(2L)
                    .teamName("Chelsea")
                    .matchesPlayed(10)
                    .wins(5)
                    .draws(3)
                    .losses(2)
                    .goalsScored(18)
                    .goalsConceded(12)
                    .cleanSheets(3)
                    .formString("WDWDL")
                    .formPointsLast5(8)
                    .currentStreak("L1")
                    .eloRating(1550.0)
                    .build();
        }

        @Test
        @DisplayName("should update existing stats for home win")
        void homeWin_updatesStats() {
            // Given
            when(teamRepository.findByNameIgnoreCase("Arsenal")).thenReturn(Optional.of(homeTeam));
            when(teamRepository.findByNameIgnoreCase("Chelsea")).thenReturn(Optional.of(awayTeam));
            when(seasonTeamStatsRepository.findBySeasonIdAndTeamId("2025-26", 1L))
                    .thenReturn(Optional.of(existingHomeStats));
            when(seasonTeamStatsRepository.findBySeasonIdAndTeamId("2025-26", 2L))
                    .thenReturn(Optional.of(existingAwayStats));
            when(eloRatingService.calculateMatchRatings(1600.0, 1550.0, 2, 1))
                    .thenReturn(new double[]{1607.0, 1543.0});
            when(matchRepository.findByTeamAndSeasonBeforeDate(anyString(), anyString(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            matchCompletionService.processCompletedMatch(completedMatch);

            // Then
            verify(seasonTeamStatsRepository, times(2)).save(statsCaptor.capture());
            var savedStats = statsCaptor.getAllValues();

            SeasonTeamStats homeStats = savedStats.stream()
                    .filter(s -> s.getTeamName().equals("Arsenal"))
                    .findFirst()
                    .orElseThrow();

            // Verify home stats updated
            assertThat(homeStats.getMatchesPlayed()).isEqualTo(11);
            assertThat(homeStats.getWins()).isEqualTo(8);
            assertThat(homeStats.getGoalsScored()).isEqualTo(27);
            assertThat(homeStats.getGoalsConceded()).isEqualTo(11);
            assertThat(homeStats.getEloRating()).isEqualTo(1607.0);

            SeasonTeamStats awayStats = savedStats.stream()
                    .filter(s -> s.getTeamName().equals("Chelsea"))
                    .findFirst()
                    .orElseThrow();

            // Verify away stats updated
            assertThat(awayStats.getMatchesPlayed()).isEqualTo(11);
            assertThat(awayStats.getLosses()).isEqualTo(3);
            assertThat(awayStats.getGoalsScored()).isEqualTo(19);
            assertThat(awayStats.getGoalsConceded()).isEqualTo(14);
            assertThat(awayStats.getEloRating()).isEqualTo(1543.0);
        }

        @Test
        @DisplayName("should update clean sheets when team keeps clean sheet")
        void cleanSheet_updatesCount() {
            // Given - Home team wins 2-0
            completedMatch.setFullTimeAwayGoals(0);

            when(teamRepository.findByNameIgnoreCase("Arsenal")).thenReturn(Optional.of(homeTeam));
            when(teamRepository.findByNameIgnoreCase("Chelsea")).thenReturn(Optional.of(awayTeam));
            when(seasonTeamStatsRepository.findBySeasonIdAndTeamId("2025-26", 1L))
                    .thenReturn(Optional.of(existingHomeStats));
            when(seasonTeamStatsRepository.findBySeasonIdAndTeamId("2025-26", 2L))
                    .thenReturn(Optional.of(existingAwayStats));
            when(eloRatingService.calculateMatchRatings(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(new double[]{1610.0, 1540.0});
            when(matchRepository.findByTeamAndSeasonBeforeDate(anyString(), anyString(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            matchCompletionService.processCompletedMatch(completedMatch);

            // Then
            verify(seasonTeamStatsRepository, times(2)).save(statsCaptor.capture());
            var savedStats = statsCaptor.getAllValues();

            SeasonTeamStats homeStats = savedStats.stream()
                    .filter(s -> s.getTeamName().equals("Arsenal"))
                    .findFirst()
                    .orElseThrow();

            assertThat(homeStats.getCleanSheets()).isEqualTo(5); // Was 4, now 5
        }

        @Test
        @DisplayName("should update form string correctly")
        void formString_updatesCorrectly() {
            // Given
            when(teamRepository.findByNameIgnoreCase("Arsenal")).thenReturn(Optional.of(homeTeam));
            when(teamRepository.findByNameIgnoreCase("Chelsea")).thenReturn(Optional.of(awayTeam));
            when(seasonTeamStatsRepository.findBySeasonIdAndTeamId("2025-26", 1L))
                    .thenReturn(Optional.of(existingHomeStats));
            when(seasonTeamStatsRepository.findBySeasonIdAndTeamId("2025-26", 2L))
                    .thenReturn(Optional.of(existingAwayStats));
            when(eloRatingService.calculateMatchRatings(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(new double[]{1607.0, 1543.0});
            when(matchRepository.findByTeamAndSeasonBeforeDate(anyString(), anyString(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            matchCompletionService.processCompletedMatch(completedMatch);

            // Then
            verify(seasonTeamStatsRepository, times(2)).save(statsCaptor.capture());
            var savedStats = statsCaptor.getAllValues();

            SeasonTeamStats homeStats = savedStats.stream()
                    .filter(s -> s.getTeamName().equals("Arsenal"))
                    .findFirst()
                    .orElseThrow();

            // Form was "WWDWW", after win should be "WWWDW" (W prepended, last char dropped)
            assertThat(homeStats.getFormString()).isEqualTo("WWWDW");
            assertThat(homeStats.getFormPointsLast5()).isEqualTo(13); // 4W + 1D = 13

            SeasonTeamStats awayStats = savedStats.stream()
                    .filter(s -> s.getTeamName().equals("Chelsea"))
                    .findFirst()
                    .orElseThrow();

            // Form was "WDWDL", after loss should be "LWDWD"
            assertThat(awayStats.getFormString()).isEqualTo("LWDWD");
            assertThat(awayStats.getFormPointsLast5()).isEqualTo(8); // L(0) + W(3) + D(1) + W(3) + D(1) = 8
        }
    }

    @Nested
    @DisplayName("Draw Match")
    class DrawMatchTests {

        @Test
        @DisplayName("should correctly handle draw result")
        void draw_updatesStatsCorrectly() {
            // Given - Match ends 1-1
            Match drawMatch = Match.builder()
                    .id(2L)
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeHomeGoals(1)
                    .fullTimeAwayGoals(1)
                    .fullTimeResult("D")
                    .season("2025-26")
                    .matchDate(LocalDate.of(2026, 2, 23))
                    .build();

            when(teamRepository.findByNameIgnoreCase("Arsenal")).thenReturn(Optional.of(homeTeam));
            when(teamRepository.findByNameIgnoreCase("Chelsea")).thenReturn(Optional.of(awayTeam));
            when(seasonTeamStatsRepository.findBySeasonIdAndTeamId(anyString(), anyLong()))
                    .thenReturn(Optional.empty());
            when(eloRatingService.calculateMatchRatings(1500.0, 1500.0, 1, 1))
                    .thenReturn(new double[]{1500.0, 1500.0});
            when(matchRepository.findByTeamAndSeasonBeforeDate(anyString(), anyString(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            matchCompletionService.processCompletedMatch(drawMatch);

            // Then
            verify(seasonTeamStatsRepository, times(2)).save(statsCaptor.capture());
            var savedStats = statsCaptor.getAllValues();

            // Both teams should have 1 draw
            assertThat(savedStats).allSatisfy(stats -> {
                assertThat(stats.getDraws()).isEqualTo(1);
                assertThat(stats.getWins()).isEqualTo(0);
                assertThat(stats.getLosses()).isEqualTo(0);
                assertThat(stats.getFormString()).isEqualTo("D");
                assertThat(stats.getFormPointsLast5()).isEqualTo(1);
            });
        }
    }

    @Nested
    @DisplayName("Elo Rating Integration")
    class EloRatingIntegrationTests {

        @Test
        @DisplayName("should call EloRatingService with correct parameters")
        void eloService_calledCorrectly() {
            // Given
            SeasonTeamStats homeStats = SeasonTeamStats.createDefault("2025-26", 1L, "Arsenal");
            homeStats.setEloRating(1650.0);
            SeasonTeamStats awayStats = SeasonTeamStats.createDefault("2025-26", 2L, "Chelsea");
            awayStats.setEloRating(1480.0);

            when(teamRepository.findByNameIgnoreCase("Arsenal")).thenReturn(Optional.of(homeTeam));
            when(teamRepository.findByNameIgnoreCase("Chelsea")).thenReturn(Optional.of(awayTeam));
            when(seasonTeamStatsRepository.findBySeasonIdAndTeamId("2025-26", 1L))
                    .thenReturn(Optional.of(homeStats));
            when(seasonTeamStatsRepository.findBySeasonIdAndTeamId("2025-26", 2L))
                    .thenReturn(Optional.of(awayStats));
            when(eloRatingService.calculateMatchRatings(1650.0, 1480.0, 2, 1))
                    .thenReturn(new double[]{1655.0, 1475.0});
            when(matchRepository.findByTeamAndSeasonBeforeDate(anyString(), anyString(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            matchCompletionService.processCompletedMatch(completedMatch);

            // Then
            verify(eloRatingService).calculateMatchRatings(1650.0, 1480.0, 2, 1);
        }

        @Test
        @DisplayName("should update both teams' Elo ratings from result")
        void eloRatings_updatedFromResult() {
            // Given
            when(teamRepository.findByNameIgnoreCase("Arsenal")).thenReturn(Optional.of(homeTeam));
            when(teamRepository.findByNameIgnoreCase("Chelsea")).thenReturn(Optional.of(awayTeam));
            when(seasonTeamStatsRepository.findBySeasonIdAndTeamId(anyString(), anyLong()))
                    .thenReturn(Optional.empty());
            when(eloRatingService.calculateMatchRatings(1500.0, 1500.0, 2, 1))
                    .thenReturn(new double[]{1510.0, 1490.0});
            when(matchRepository.findByTeamAndSeasonBeforeDate(anyString(), anyString(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            matchCompletionService.processCompletedMatch(completedMatch);

            // Then
            verify(seasonTeamStatsRepository, times(2)).save(statsCaptor.capture());
            var savedStats = statsCaptor.getAllValues();

            SeasonTeamStats homeStats = savedStats.stream()
                    .filter(s -> s.getTeamName().equals("Arsenal"))
                    .findFirst()
                    .orElseThrow();
            assertThat(homeStats.getEloRating()).isEqualTo(1510.0);

            SeasonTeamStats awayStats = savedStats.stream()
                    .filter(s -> s.getTeamName().equals("Chelsea"))
                    .findFirst()
                    .orElseThrow();
            assertThat(awayStats.getEloRating()).isEqualTo(1490.0);
        }
    }

    @Nested
    @DisplayName("Transactional Behavior")
    class TransactionalTests {

        @Test
        @DisplayName("should save both team stats in same operation")
        void savesBothTeams() {
            // Given
            when(teamRepository.findByNameIgnoreCase("Arsenal")).thenReturn(Optional.of(homeTeam));
            when(teamRepository.findByNameIgnoreCase("Chelsea")).thenReturn(Optional.of(awayTeam));
            when(seasonTeamStatsRepository.findBySeasonIdAndTeamId(anyString(), anyLong()))
                    .thenReturn(Optional.empty());
            when(eloRatingService.calculateMatchRatings(anyDouble(), anyDouble(), anyInt(), anyInt()))
                    .thenReturn(new double[]{1510.0, 1490.0});
            when(matchRepository.findByTeamAndSeasonBeforeDate(anyString(), anyString(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            matchCompletionService.processCompletedMatch(completedMatch);

            // Then - both saves happen
            verify(seasonTeamStatsRepository, times(2)).save(any(SeasonTeamStats.class));
        }
    }

    @Nested
    @DisplayName("Get Team Elo Rating")
    class GetEloRatingTests {

        @Test
        @DisplayName("should return team's Elo rating when stats exist")
        void statsExist_returnsRating() {
            // Given
            SeasonTeamStats stats = SeasonTeamStats.builder()
                    .seasonId("2025-26")
                    .teamName("Arsenal")
                    .eloRating(1650.0)
                    .build();
            when(seasonTeamStatsRepository.findBySeasonIdAndTeamNameIgnoreCase("2025-26", "Arsenal"))
                    .thenReturn(Optional.of(stats));

            // When
            double rating = matchCompletionService.getTeamEloRating("2025-26", "Arsenal");

            // Then
            assertThat(rating).isEqualTo(1650.0);
        }

        @Test
        @DisplayName("should return default rating when stats don't exist")
        void noStats_returnsDefault() {
            // Given
            when(seasonTeamStatsRepository.findBySeasonIdAndTeamNameIgnoreCase("2025-26", "NewTeam"))
                    .thenReturn(Optional.empty());

            // When
            double rating = matchCompletionService.getTeamEloRating("2025-26", "NewTeam");

            // Then
            assertThat(rating).isEqualTo(1500.0);
        }
    }
}

