package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.FoulsAnalysisDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FoulsAnalysisService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FoulsAnalysisService Unit Tests")
class FoulsAnalysisServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private FoulsAnalysisService foulsAnalysisService;

    private List<Match> createTestMatches(String teamName, boolean isHome, int count,
                                          int baseFoulsCommitted, int baseFoulsDrawn) {
        List<Match> matches = new ArrayList<>();
        LocalDate date = LocalDate.now().minusDays(1);

        for (int i = 0; i < count; i++) {
            Match.MatchBuilder builder = Match.builder()
                    .id((long) i)
                    .matchDate(date.minusDays(i))
                    .season("2025-26")
                    .fullTimeResult(i % 3 == 0 ? "H" : (i % 3 == 1 ? "A" : "D"));

            if (isHome) {
                builder.homeTeam(teamName)
                       .awayTeam("Opponent" + i)
                       .fullTimeHomeGoals(2)
                       .fullTimeAwayGoals(1)
                       .homeFouls(baseFoulsCommitted + (i % 5))
                       .awayFouls(baseFoulsDrawn + (i % 4));
            } else {
                builder.homeTeam("Opponent" + i)
                       .awayTeam(teamName)
                       .fullTimeHomeGoals(1)
                       .fullTimeAwayGoals(2)
                       .homeFouls(baseFoulsDrawn + (i % 4))
                       .awayFouls(baseFoulsCommitted + (i % 5));
            }

            matches.add(builder.build());
        }
        return matches;
    }

    @Nested
    @DisplayName("analyzeFouls()")
    class AnalyzeFoulsTests {

        @Test
        @DisplayName("Should return fouls analysis for home matches")
        void shouldReturnFoulsAnalysisForHomeMatches() {
            // Given
            String teamName = "Arsenal";
            List<Match> homeMatches = createTestMatches(teamName, true, 15, 10, 12);

            when(matchRepository.findHomeMatchesByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(homeMatches);

            // When
            FoulsAnalysisDTO result = foulsAnalysisService.analyzeFouls(teamName, true);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTeamName()).isEqualTo(teamName);
            assertThat(result.isHome()).isTrue();
            assertThat(result.getMatchesAnalyzed()).isEqualTo(15);
            assertThat(result.getAvgFoulsCommitted()).isGreaterThan(0);
            assertThat(result.getAvgFoulsDrawn()).isGreaterThan(0);
            assertThat(result.getDisciplineScore()).isBetween(0.0, 10.0);
            assertThat(result.getDisciplineRating()).isNotNull();
        }

        @Test
        @DisplayName("Should return fouls analysis for away matches")
        void shouldReturnFoulsAnalysisForAwayMatches() {
            // Given
            String teamName = "Chelsea";
            List<Match> awayMatches = createTestMatches(teamName, false, 10, 8, 14);

            when(matchRepository.findAwayMatchesByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(awayMatches);

            // When
            FoulsAnalysisDTO result = foulsAnalysisService.analyzeFouls(teamName, false);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTeamName()).isEqualTo(teamName);
            assertThat(result.isHome()).isFalse();
            assertThat(result.getMatchesAnalyzed()).isEqualTo(10);
        }

        @Test
        @DisplayName("Should throw exception for empty team name")
        void shouldThrowExceptionForEmptyTeamName() {
            // When/Then
            assertThatThrownBy(() -> foulsAnalysisService.analyzeFouls("", true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Team name cannot be empty");
        }

        @Test
        @DisplayName("Should throw exception for null team name")
        void shouldThrowExceptionForNullTeamName() {
            // When/Then
            assertThatThrownBy(() -> foulsAnalysisService.analyzeFouls(null, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Team name cannot be empty");
        }

        @Test
        @DisplayName("Should return empty analysis when no fouls data available")
        void shouldReturnEmptyAnalysisWhenNoFoulsData() {
            // Given
            String teamName = "Manchester United";
            List<Match> matchesWithoutFouls = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                matchesWithoutFouls.add(Match.builder()
                        .id((long) i)
                        .homeTeam(teamName)
                        .awayTeam("Opponent")
                        .matchDate(LocalDate.now().minusDays(i))
                        .fullTimeResult("H")
                        .homeFouls(null) // No fouls data
                        .awayFouls(null)
                        .build());
            }

            when(matchRepository.findHomeMatchesByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matchesWithoutFouls);

            // When
            FoulsAnalysisDTO result = foulsAnalysisService.analyzeFouls(teamName, true);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getMatchesAnalyzed()).isEqualTo(0);
            assertThat(result.getDisciplineRating()).isEqualTo("N/A");
        }

        @Test
        @DisplayName("Should limit to 20 matches")
        void shouldLimitTo20Matches() {
            // Given
            String teamName = "Liverpool";
            List<Match> manyMatches = createTestMatches(teamName, true, 30, 11, 10);

            when(matchRepository.findHomeMatchesByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(manyMatches);

            // When
            FoulsAnalysisDTO result = foulsAnalysisService.analyzeFouls(teamName, true);

            // Then
            assertThat(result.getMatchesAnalyzed()).isEqualTo(20);
        }

        @Test
        @DisplayName("Should calculate positive differential when drawing more fouls")
        void shouldCalculatePositiveDifferentialWhenDrawingMoreFouls() {
            // Given
            String teamName = "Man City";
            List<Match> matches = createTestMatches(teamName, true, 10, 8, 14); // Commits 8, draws 14

            when(matchRepository.findHomeMatchesByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            FoulsAnalysisDTO result = foulsAnalysisService.analyzeFouls(teamName, true);

            // Then
            assertThat(result.getFoulsDifferential()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should calculate negative differential when committing more fouls")
        void shouldCalculateNegativeDifferentialWhenCommittingMoreFouls() {
            // Given
            String teamName = "Tottenham";
            List<Match> matches = createTestMatches(teamName, true, 10, 15, 8); // Commits 15, draws 8

            when(matchRepository.findHomeMatchesByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            FoulsAnalysisDTO result = foulsAnalysisService.analyzeFouls(teamName, true);

            // Then
            assertThat(result.getFoulsDifferential()).isLessThan(0);
        }

        @Test
        @DisplayName("Should calculate win rates for different foul thresholds")
        void shouldCalculateWinRatesForDifferentFoulThresholds() {
            // Given
            String teamName = "Newcastle";
            List<Match> matches = new ArrayList<>();
            LocalDate date = LocalDate.now().minusDays(1);

            // Create matches with specific foul counts
            // Low fouls (<10): wins
            for (int i = 0; i < 5; i++) {
                matches.add(Match.builder()
                        .id((long) i)
                        .homeTeam(teamName)
                        .awayTeam("Opponent")
                        .matchDate(date.minusDays(i))
                        .fullTimeResult("H")
                        .homeFouls(8) // Low fouls
                        .awayFouls(12)
                        .build());
            }
            // High fouls (>15): losses
            for (int i = 5; i < 10; i++) {
                matches.add(Match.builder()
                        .id((long) i)
                        .homeTeam(teamName)
                        .awayTeam("Opponent")
                        .matchDate(date.minusDays(i))
                        .fullTimeResult("A")
                        .homeFouls(18) // High fouls
                        .awayFouls(10)
                        .build());
            }

            when(matchRepository.findHomeMatchesByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            FoulsAnalysisDTO result = foulsAnalysisService.analyzeFouls(teamName, true);

            // Then
            assertThat(result.getWinRateWhenLowFouls()).isEqualTo(100.0); // 5/5 wins
            assertThat(result.getWinRateWhenHighFouls()).isEqualTo(0.0);  // 0/5 wins
            assertThat(result.getLowFoulsMatchCount()).isEqualTo(5);
            assertThat(result.getHighFoulsMatchCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should try case-insensitive search when exact match fails")
        void shouldTryCaseInsensitiveSearchWhenExactMatchFails() {
            // Given
            String teamName = "arsenal";
            List<Match> matches = createTestMatches("Arsenal", true, 5, 10, 12);

            when(matchRepository.findHomeMatchesByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findHomeMatchesByTeamBeforeDateIgnoreCase(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            FoulsAnalysisDTO result = foulsAnalysisService.analyzeFouls(teamName, true);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getMatchesAnalyzed()).isEqualTo(5);
            verify(matchRepository).findHomeMatchesByTeamBeforeDateIgnoreCase(eq(teamName), any(LocalDate.class));
        }

        @Test
        @DisplayName("Should throw exception when team not found")
        void shouldThrowExceptionWhenTeamNotFound() {
            // Given
            String teamName = "Unknown FC";

            when(matchRepository.findHomeMatchesByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findHomeMatchesByTeamBeforeDateIgnoreCase(eq(teamName), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            // When/Then
            assertThatThrownBy(() -> foulsAnalysisService.analyzeFouls(teamName, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Team not found");
        }
    }

    @Nested
    @DisplayName("Discipline Score Calculation")
    class DisciplineScoreTests {

        @Test
        @DisplayName("Should give high discipline score for low fouls")
        void shouldGiveHighScoreForLowFouls() {
            // Given
            String teamName = "Fair Play FC";
            List<Match> matches = createTestMatches(teamName, true, 10, 6, 10); // Very low fouls

            when(matchRepository.findHomeMatchesByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            FoulsAnalysisDTO result = foulsAnalysisService.analyzeFouls(teamName, true);

            // Then
            assertThat(result.getDisciplineScore()).isGreaterThanOrEqualTo(6.0);
            assertThat(result.getDisciplineRating()).isIn("Excellent", "Good");
        }

        @Test
        @DisplayName("Should give low discipline score for high fouls")
        void shouldGiveLowScoreForHighFouls() {
            // Given
            String teamName = "Rough FC";
            List<Match> matches = createTestMatches(teamName, true, 10, 18, 8); // Very high fouls

            when(matchRepository.findHomeMatchesByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            FoulsAnalysisDTO result = foulsAnalysisService.analyzeFouls(teamName, true);

            // Then
            assertThat(result.getDisciplineScore()).isLessThanOrEqualTo(4.0);
            assertThat(result.getDisciplineRating()).isIn("Poor", "Average");
        }

        @Test
        @DisplayName("Should clamp discipline score between 0 and 10")
        void shouldClampDisciplineScoreBetween0And10() {
            // Given
            String teamName = "Extreme FC";
            List<Match> matches = createTestMatches(teamName, true, 10, 25, 5); // Extremely high fouls

            when(matchRepository.findHomeMatchesByTeamBeforeDate(eq(teamName), any(LocalDate.class)))
                    .thenReturn(matches);

            // When
            FoulsAnalysisDTO result = foulsAnalysisService.analyzeFouls(teamName, true);

            // Then
            assertThat(result.getDisciplineScore()).isBetween(0.0, 10.0);
        }
    }
}

