package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.TeamStatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TeamStatsService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeamStatsService Unit Tests")
class TeamStatsServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private TeamValidationService teamValidationService;

    @InjectMocks
    private TeamStatsService teamStatsService;

    private List<Match> arsenalMatches;

    @BeforeEach
    void setUp() {
        lenient().when(teamValidationService.resolveTeamName(any()))
                .thenAnswer(inv -> {
                    String name = inv.getArgument(0);
                    if (name == null || name.isBlank()) {
                        throw new IllegalArgumentException("Team name cannot be empty");
                    }
                    return name;
                });
        // Create sample match data for Arsenal
        arsenalMatches = Arrays.asList(
                createMatch("Arsenal", "Chelsea", 2, 1, "H", LocalDate.of(2025, 12, 1)),
                createMatch("Arsenal", "Liverpool", 1, 1, "D", LocalDate.of(2025, 11, 25)),
                createMatch("Man City", "Arsenal", 2, 0, "H", LocalDate.of(2025, 11, 20)),
                createMatch("Arsenal", "Tottenham", 3, 0, "H", LocalDate.of(2025, 11, 15)),
                createMatch("Newcastle", "Arsenal", 1, 2, "A", LocalDate.of(2025, 11, 10))
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
                .halfTimeHomeGoals(homeGoals > 0 ? 1 : 0)
                .halfTimeAwayGoals(awayGoals > 0 ? 1 : 0)
                .homeShotsOnTarget(5)
                .awayShotsOnTarget(3)
                .homeCorners(6)
                .awayCorners(4)
                .build();
    }

    @Test
    @DisplayName("should return comprehensive stats for valid team")
    void getTeamStats_validTeam_returnsStats() {
        // Given
        String teamName = "Arsenal";
        when(matchRepository.findByTeamBeforeDate(eq(teamName), any()))
                .thenReturn(arsenalMatches);
        when(matchRepository.findHomeMatchesByTeamBeforeDate(eq(teamName), any()))
                .thenReturn(arsenalMatches.stream()
                        .filter(m -> m.getHomeTeam().equals(teamName))
                        .toList());
        when(matchRepository.findAwayMatchesByTeamBeforeDate(eq(teamName), any()))
                .thenReturn(arsenalMatches.stream()
                        .filter(m -> m.getAwayTeam().equals(teamName))
                        .toList());

        // When
        TeamStatsResponse stats = teamStatsService.getTeamStats(teamName);

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getTeamName()).isEqualTo(teamName);
        assertThat(stats.getOverall()).isNotNull();
        assertThat(stats.getOverall().getTotalMatches()).isEqualTo(5);
        assertThat(stats.getHomeStats()).isNotNull();
        assertThat(stats.getAwayStats()).isNotNull();
        assertThat(stats.getGoalStats()).isNotNull();
        assertThat(stats.getFormStats()).isNotNull();
        assertThat(stats.getRecentMatches()).isNotNull();
    }

    @Test
    @DisplayName("should throw exception for team with no matches")
    void getTeamStats_noMatches_throwsException() {
        // Given
        String teamName = "NonExistentFC";
        when(teamValidationService.resolveTeamName(teamName))
                .thenThrow(new IllegalArgumentException("No matches found for team: 'NonExistentFC'"));

        // When/Then
        assertThatThrownBy(() -> teamStatsService.getTeamStats(teamName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No matches found");
    }

    @Test
    @DisplayName("should calculate correct win percentage")
    void getTeamStats_calculatesWinPercentage() {
        // Given
        String teamName = "Arsenal";
        when(matchRepository.findByTeamBeforeDate(eq(teamName), any()))
                .thenReturn(arsenalMatches);
        when(matchRepository.findHomeMatchesByTeamBeforeDate(eq(teamName), any()))
                .thenReturn(arsenalMatches.stream()
                        .filter(m -> m.getHomeTeam().equals(teamName))
                        .toList());
        when(matchRepository.findAwayMatchesByTeamBeforeDate(eq(teamName), any()))
                .thenReturn(arsenalMatches.stream()
                        .filter(m -> m.getAwayTeam().equals(teamName))
                        .toList());

        // When
        TeamStatsResponse stats = teamStatsService.getTeamStats(teamName);

        // Then
        // Arsenal: 3 wins (vs Chelsea H, Tottenham H, Newcastle A), 1 draw, 1 loss
        // Win% = 3/5 = 60%
        assertThat(stats.getOverall().getWins()).isEqualTo(3);
        assertThat(stats.getOverall().getDraws()).isEqualTo(1);
        assertThat(stats.getOverall().getLosses()).isEqualTo(1);
        assertThat(stats.getOverall().getWinPercentage()).isEqualTo(60.0);
    }

    @Test
    @DisplayName("should generate form string correctly")
    void getTeamStats_generatesFormString() {
        // Given
        String teamName = "Arsenal";
        when(matchRepository.findByTeamBeforeDate(eq(teamName), any()))
                .thenReturn(arsenalMatches);
        when(matchRepository.findHomeMatchesByTeamBeforeDate(eq(teamName), any()))
                .thenReturn(arsenalMatches.stream()
                        .filter(m -> m.getHomeTeam().equals(teamName))
                        .toList());
        when(matchRepository.findAwayMatchesByTeamBeforeDate(eq(teamName), any()))
                .thenReturn(arsenalMatches.stream()
                        .filter(m -> m.getAwayTeam().equals(teamName))
                        .toList());

        // When
        TeamStatsResponse stats = teamStatsService.getTeamStats(teamName);

        // Then
        assertThat(stats.getFormStats().getLast5Form()).isNotNull();
        assertThat(stats.getFormStats().getLast5Form()).hasSize(5);
        // Most recent first: W (Chelsea), D (Liverpool), L (City), W (Tottenham), W (Newcastle)
        assertThat(stats.getFormStats().getLast5Form()).isEqualTo("WDLWW");
    }

    @Test
    @DisplayName("should return recent matches in correct order")
    void getTeamStats_recentMatchesOrdered() {
        // Given
        String teamName = "Arsenal";
        when(matchRepository.findByTeamBeforeDate(eq(teamName), any()))
                .thenReturn(arsenalMatches);
        when(matchRepository.findHomeMatchesByTeamBeforeDate(eq(teamName), any()))
                .thenReturn(arsenalMatches.stream()
                        .filter(m -> m.getHomeTeam().equals(teamName))
                        .toList());
        when(matchRepository.findAwayMatchesByTeamBeforeDate(eq(teamName), any()))
                .thenReturn(arsenalMatches.stream()
                        .filter(m -> m.getAwayTeam().equals(teamName))
                        .toList());

        // When
        TeamStatsResponse stats = teamStatsService.getTeamStats(teamName);

        // Then
        assertThat(stats.getRecentMatches()).isNotEmpty();
        assertThat(stats.getRecentMatches().get(0).getOpponent()).isEqualTo("Chelsea");
    }
}

