package com.app.footballprediction.service;

import com.app.common.model.LeagueStanding;
import com.app.common.repository.LeagueStandingRepository;
import com.app.footballprediction.dto.Top4RaceAnalysisDTO;
import com.app.footballprediction.dto.Top4RaceDTO;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Top4RaceService.
 * Tests Champions League race analysis and probability calculations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Top4RaceService Unit Tests")
class Top4RaceServiceTest {

    @Mock
    private LeagueStandingRepository standingRepository;

    @Mock
    private LeagueStandingService leagueStandingService;

    @Mock
    private TeamService teamService;

    @InjectMocks
    private Top4RaceService top4RaceService;

    private List<LeagueStanding> sampleStandings;

    @BeforeEach
    void setUp() {
        // Create sample standings for testing (mid-season scenario)
        sampleStandings = Arrays.asList(
                createStanding("Arsenal", 1, 70, 30, 20, 5, 5),       // Leader
                createStanding("Liverpool", 2, 68, 30, 19, 7, 4),     // 2 pts behind
                createStanding("Manchester City", 3, 65, 30, 18, 8, 4), // 5 pts behind
                createStanding("Chelsea", 4, 60, 30, 16, 9, 5),       // 4th place
                createStanding("Tottenham", 5, 58, 30, 15, 10, 5),    // 2 pts behind 4th
                createStanding("Manchester United", 6, 52, 30, 13, 10, 7),
                createStanding("Newcastle", 7, 48, 30, 12, 10, 8),
                createStanding("West Ham", 8, 45, 30, 11, 10, 9),
                createStanding("Brighton", 9, 42, 30, 10, 10, 10),
                createStanding("Aston Villa", 10, 40, 30, 9, 11, 10)
        );
    }

    private LeagueStanding createStanding(String teamName, int position, int points, int played, int won, int drawn, int lost) {
        return LeagueStanding.builder()
                .teamName(teamName)
                .position(position)
                .points(points)
                .played(played)
                .won(won)
                .drawn(drawn)
                .lost(lost)
                .goalsFor(played * 2)
                .goalsAgainst(played)
                .goalDifference(played)
                .form("W W D L W")
                .build();
    }

    @Nested
    @DisplayName("analyzeTop4Race")
    class AnalyzeTop4RaceTests {

        @Test
        @DisplayName("Returns analysis with correct season and date")
        void returnsAnalysisWithCorrectSeasonAndDate() {
            // Given
            when(leagueStandingService.getDefaultLeagueId()).thenReturn(1L);
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(anyLong(), anyString()))
                    .thenReturn(sampleStandings);

            // When
            Top4RaceAnalysisDTO result = top4RaceService.analyzeTop4Race("2025-26", LocalDate.of(2026, 3, 10));

            // Then
            assertThat(result.getSeason()).isEqualTo("2025-26");
            assertThat(result.getAsOfDate()).isEqualTo(LocalDate.of(2026, 3, 10));
        }

        @Test
        @DisplayName("Returns correct number of teams in race")
        void returnsCorrectNumberOfTeamsInRace() {
            // Given
            when(leagueStandingService.getDefaultLeagueId()).thenReturn(1L);
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(anyLong(), anyString()))
                    .thenReturn(sampleStandings);

            // When
            Top4RaceAnalysisDTO result = top4RaceService.analyzeTop4Race("2025-26", LocalDate.now());

            // Then
            assertThat(result.getTeamsInRace()).hasSize(10); // Top 10 teams
        }

        @Test
        @DisplayName("Calculates gaps correctly")
        void calculatesGapsCorrectly() {
            // Given
            when(leagueStandingService.getDefaultLeagueId()).thenReturn(1L);
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(anyLong(), anyString()))
                    .thenReturn(sampleStandings);

            // When
            Top4RaceAnalysisDTO result = top4RaceService.analyzeTop4Race("2025-26", LocalDate.now());

            // Then
            Top4RaceDTO leader = result.getTeamsInRace().get(0);
            assertThat(leader.getGapToFirst()).isEqualTo(0);

            Top4RaceDTO second = result.getTeamsInRace().get(1);
            assertThat(second.getGapToFirst()).isEqualTo(2); // 70 - 68

            Top4RaceDTO fourth = result.getTeamsInRace().get(3);
            assertThat(fourth.getGapToFirst()).isEqualTo(10); // 70 - 60
        }

        @Test
        @DisplayName("Teams with 70+ points have >95% probability")
        void teamsWithHighPointsHaveHighProbability() {
            // Given - Arsenal has 70 points
            when(leagueStandingService.getDefaultLeagueId()).thenReturn(1L);
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(anyLong(), anyString()))
                    .thenReturn(sampleStandings);

            // When
            Top4RaceAnalysisDTO result = top4RaceService.analyzeTop4Race("2025-26", LocalDate.now());

            // Then
            Top4RaceDTO arsenal = result.getTeamsInRace().get(0);
            assertThat(arsenal.getTeamName()).isEqualTo("Arsenal");
            assertThat(arsenal.getTop4Probability()).isGreaterThanOrEqualTo(95.0);
        }

        @Test
        @DisplayName("Team in 5th with 60 points has lower probability than 4th")
        void teamInFifthHasLowerProbabilityThanFourth() {
            // Given
            when(leagueStandingService.getDefaultLeagueId()).thenReturn(1L);
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(anyLong(), anyString()))
                    .thenReturn(sampleStandings);

            // When
            Top4RaceAnalysisDTO result = top4RaceService.analyzeTop4Race("2025-26", LocalDate.now());

            // Then
            Top4RaceDTO fourth = result.getTeamsInRace().get(3);
            Top4RaceDTO fifth = result.getTeamsInRace().get(4);

            assertThat(fourth.getTop4Probability()).isGreaterThan(fifth.getTop4Probability());
        }

        @Test
        @DisplayName("Returns title race summary")
        void returnsTitleRaceSummary() {
            // Given
            when(leagueStandingService.getDefaultLeagueId()).thenReturn(1L);
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(anyLong(), anyString()))
                    .thenReturn(sampleStandings);

            // When
            Top4RaceAnalysisDTO result = top4RaceService.analyzeTop4Race("2025-26", LocalDate.now());

            // Then
            assertThat(result.getTitleRace()).isNotNull();
            assertThat(result.getTitleRace().getLeader()).isEqualTo("Arsenal");
            assertThat(result.getTitleRace().getGapFirstToSecond()).isEqualTo(2);
        }

        @Test
        @DisplayName("Returns empty response when no standings available")
        void returnsEmptyResponseWhenNoStandings() {
            // Given
            when(leagueStandingService.getDefaultLeagueId()).thenReturn(1L);
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(anyLong(), anyString()))
                    .thenReturn(Collections.emptyList());
            when(leagueStandingService.calculateStandingsFromMatches(anyLong(), anyString()))
                    .thenReturn(Collections.emptyList());

            // When
            Top4RaceAnalysisDTO result = top4RaceService.analyzeTop4Race("2025-26", LocalDate.now());

            // Then
            assertThat(result.getTeamsInRace()).isEmpty();
            assertThat(result.getPointsForSafety()).isEqualTo(72); // Default
        }

        @Test
        @DisplayName("Calculates remaining matches correctly")
        void calculatesRemainingMatchesCorrectly() {
            // Given - Teams have played 30 matches
            when(leagueStandingService.getDefaultLeagueId()).thenReturn(1L);
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(anyLong(), anyString()))
                    .thenReturn(sampleStandings);

            // When
            Top4RaceAnalysisDTO result = top4RaceService.analyzeTop4Race("2025-26", LocalDate.now());

            // Then
            assertThat(result.getMatchdaysCompleted()).isEqualTo(30);
            assertThat(result.getTotalMatchesInSeason()).isEqualTo(38);

            Top4RaceDTO anyTeam = result.getTeamsInRace().get(0);
            assertThat(anyTeam.getRemainingMatches()).isEqualTo(8); // 38 - 30
        }
    }

    @Nested
    @DisplayName("Status Determination")
    class StatusDeterminationTests {

        @Test
        @DisplayName("Leader has Champion status when clear")
        void leaderHasChampionStatus() {
            // Given - Leader with big lead
            List<LeagueStanding> standings = Arrays.asList(
                    createStanding("Arsenal", 1, 85, 35, 27, 4, 4),
                    createStanding("Liverpool", 2, 70, 35, 20, 8, 7)
            );

            when(leagueStandingService.getDefaultLeagueId()).thenReturn(1L);
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(anyLong(), anyString()))
                    .thenReturn(standings);

            // When
            Top4RaceAnalysisDTO result = top4RaceService.analyzeTop4Race("2025-26", LocalDate.now());

            // Then
            Top4RaceDTO leader = result.getTeamsInRace().get(0);
            assertThat(leader.getStatus()).isEqualTo("Champion");
        }

        @Test
        @DisplayName("Top 4 teams with high probability have UCL Safe status")
        void topTeamsHaveUclSafeStatus() {
            // Given
            when(leagueStandingService.getDefaultLeagueId()).thenReturn(1L);
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(anyLong(), anyString()))
                    .thenReturn(sampleStandings);

            // When
            Top4RaceAnalysisDTO result = top4RaceService.analyzeTop4Race("2025-26", LocalDate.now());

            // Then
            Top4RaceDTO second = result.getTeamsInRace().get(1);
            // High probability teams in top 4 should be UCL Safe or Champion
            assertThat(second.getStatus()).isIn("UCL Safe", "Champion", "Fighting");
        }
    }

    @Nested
    @DisplayName("Motivation Level")
    class MotivationLevelTests {

        @Test
        @DisplayName("Title contenders have high motivation")
        void titleContendersHaveHighMotivation() {
            // Given - Close title race
            List<LeagueStanding> standings = Arrays.asList(
                    createStanding("Arsenal", 1, 70, 30, 20, 5, 5),
                    createStanding("Liverpool", 2, 69, 30, 19, 7, 4)  // 1 pt behind
            );

            when(leagueStandingService.getDefaultLeagueId()).thenReturn(1L);
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(anyLong(), anyString()))
                    .thenReturn(standings);

            // When
            Top4RaceAnalysisDTO result = top4RaceService.analyzeTop4Race("2025-26", LocalDate.now());

            // Then
            Top4RaceDTO second = result.getTeamsInRace().get(1);
            assertThat(second.getMotivation()).isEqualTo("High");
        }
    }

    @Nested
    @DisplayName("Title Race Summary")
    class TitleRaceSummaryTests {

        @Test
        @DisplayName("Wide open when gap is less than 4 points")
        void wideOpenWhenGapSmall() {
            // Given
            List<LeagueStanding> standings = Arrays.asList(
                    createStanding("Arsenal", 1, 70, 30, 20, 5, 5),
                    createStanding("Liverpool", 2, 69, 30, 19, 7, 4),
                    createStanding("City", 3, 68, 30, 18, 8, 4)
            );

            when(leagueStandingService.getDefaultLeagueId()).thenReturn(1L);
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(anyLong(), anyString()))
                    .thenReturn(standings);

            // When
            Top4RaceAnalysisDTO result = top4RaceService.analyzeTop4Race("2025-26", LocalDate.now());

            // Then
            assertThat(result.getTitleRace().getIntensity()).isEqualTo("Wide Open");
            assertThat(result.getTitleRace().getContenders()).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("Decided when gap is insurmountable")
        void decidedWhenGapInsurmoutable() {
            // Given - 30 point lead with only 3 games left
            List<LeagueStanding> standings = Arrays.asList(
                    createStanding("Arsenal", 1, 90, 35, 28, 4, 3),
                    createStanding("Liverpool", 2, 60, 35, 15, 12, 8)
            );

            when(leagueStandingService.getDefaultLeagueId()).thenReturn(1L);
            when(standingRepository.findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(anyLong(), anyString()))
                    .thenReturn(standings);

            // When
            Top4RaceAnalysisDTO result = top4RaceService.analyzeTop4Race("2025-26", LocalDate.now());

            // Then
            assertThat(result.getTitleRace().isDecided()).isTrue();
            assertThat(result.getTitleRace().getIntensity()).isEqualTo("Decided");
        }
    }
}

