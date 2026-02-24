package com.app.common.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LeaguePositionService.
 * Verifies correct standings calculation with season-based filtering.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LeaguePositionService Unit Tests")
class LeaguePositionServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private LeaguePositionService leaguePositionService;

    private static final String TEST_SEASON = "2023-24";

    @Nested
    @DisplayName("calculateStandingsAsOfDate() with season filter")
    class CalculateStandingsWithSeasonTests {

        @Test
        @DisplayName("returns empty map when no matches exist for season")
        void returnsEmptyMapWhenNoMatches() {
            when(matchRepository.findBySeasonBeforeDateForTable(eq(TEST_SEASON), any()))
                    .thenReturn(Collections.emptyList());

            Map<String, Integer> standings = leaguePositionService
                    .calculateStandingsAsOfDate(TEST_SEASON, LocalDate.now());

            assertThat(standings).isEmpty();
        }

        @Test
        @DisplayName("calculates positions correctly based on points within season")
        void calculatesPositionsBasedOnPoints() {
            // Given: 2 matches in the season - Arsenal beats Chelsea, Liverpool beats Arsenal
            List<Match> matches = List.of(
                    createMatchInSeason("Arsenal", "Chelsea", 2, 0, "H", LocalDate.of(2024, 1, 1)),
                    createMatchInSeason("Liverpool", "Arsenal", 3, 1, "H", LocalDate.of(2024, 1, 2))
            );

            when(matchRepository.findBySeasonBeforeDateForTable(eq(TEST_SEASON), any()))
                    .thenReturn(matches);

            // When
            Map<String, Integer> standings = leaguePositionService
                    .calculateStandingsAsOfDate(TEST_SEASON, LocalDate.of(2024, 1, 3));

            // Then: Liverpool (3pts, +2GD) > Arsenal (3pts, 0GD) > Chelsea (0pts)
            assertThat(standings.get("Liverpool")).isEqualTo(1);
            assertThat(standings.get("Arsenal")).isEqualTo(2);
            assertThat(standings.get("Chelsea")).isEqualTo(3);
        }

        @Test
        @DisplayName("sorts by goal difference when points are equal")
        void sortsByGoalDifferenceWhenPointsEqual() {
            // Given: Both teams have 3 points, but different GD
            List<Match> matches = List.of(
                    createMatchInSeason("Arsenal", "Chelsea", 3, 0, "H", LocalDate.of(2024, 1, 1)),
                    createMatchInSeason("Liverpool", "Everton", 1, 0, "H", LocalDate.of(2024, 1, 1))
            );

            when(matchRepository.findBySeasonBeforeDateForTable(eq(TEST_SEASON), any()))
                    .thenReturn(matches);

            Map<String, Integer> standings = leaguePositionService
                    .calculateStandingsAsOfDate(TEST_SEASON, LocalDate.of(2024, 1, 3));

            // Arsenal has GD +3, Liverpool has GD +1
            assertThat(standings.get("Arsenal")).isEqualTo(1);
            assertThat(standings.get("Liverpool")).isEqualTo(2);
        }

        @Test
        @DisplayName("sorts by goals scored when points and GD are equal")
        void sortsByGoalsScoredWhenPointsAndGDEqual() {
            // Given: Both teams have same points and GD, different goals scored
            List<Match> matches = List.of(
                    createMatchInSeason("Arsenal", "Chelsea", 4, 2, "H", LocalDate.of(2024, 1, 1)),
                    createMatchInSeason("Liverpool", "Everton", 2, 0, "H", LocalDate.of(2024, 1, 1))
            );

            when(matchRepository.findBySeasonBeforeDateForTable(eq(TEST_SEASON), any()))
                    .thenReturn(matches);

            Map<String, Integer> standings = leaguePositionService
                    .calculateStandingsAsOfDate(TEST_SEASON, LocalDate.of(2024, 1, 3));

            // Both have 3pts, +2 GD, but Arsenal scored 4, Liverpool scored 2
            assertThat(standings.get("Arsenal")).isEqualTo(1);
            assertThat(standings.get("Liverpool")).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getTeamPositionAsOfDate() with season")
    class GetTeamPositionWithSeasonTests {

        @Test
        @DisplayName("returns correct position for team in standings")
        void returnsCorrectPosition() {
            List<Match> matches = List.of(
                    createMatchInSeason("Arsenal", "Chelsea", 2, 0, "H", LocalDate.of(2024, 1, 1))
            );

            when(matchRepository.findBySeasonBeforeDateForTable(eq(TEST_SEASON), any()))
                    .thenReturn(matches);

            int position = leaguePositionService
                    .getTeamPositionAsOfDate("Arsenal", TEST_SEASON, LocalDate.of(2024, 1, 5));

            assertThat(position).isEqualTo(1);  // Arsenal won, so top
        }

        @Test
        @DisplayName("returns default 10 for unknown team")
        void returnsDefaultForUnknownTeam() {
            when(matchRepository.findBySeasonBeforeDateForTable(eq(TEST_SEASON), any()))
                    .thenReturn(Collections.emptyList());

            int position = leaguePositionService
                    .getTeamPositionAsOfDate("Unknown FC", TEST_SEASON, LocalDate.now());

            assertThat(position).isEqualTo(10);  // Mid-table default
        }

        @Test
        @DisplayName("returns default 10 for null team name")
        void returnsDefaultForNullTeam() {
            int position = leaguePositionService
                    .getTeamPositionAsOfDate(null, TEST_SEASON, LocalDate.now());

            assertThat(position).isEqualTo(10);
        }

        @Test
        @DisplayName("returns default 10 for null season")
        void returnsDefaultForNullSeason() {
            int position = leaguePositionService
                    .getTeamPositionAsOfDate("Arsenal", null, LocalDate.now());

            assertThat(position).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("Historical Premier League Table Validation")
    class HistoricalTableValidationTests {

        /**
         * Test validates that calculated standings match official PL table.
         * Using known historical data from 2022-23 season after first 5 rounds.
         *
         * Note: This requires actual match data in the database.
         * The test documents expected behavior for validation.
         */
        @Test
        @DisplayName("validates standings calculation logic matches PL methodology")
        void validatesStandingsCalculationLogic() {
            // Simulating a realistic scenario:
            // After Round 5, Arsenal was 1st with 15 pts (5W, 0D, 0L)
            // Man City was 2nd with 13 pts (4W, 1D, 0L)

            List<Match> round1to5Matches = List.of(
                    // Arsenal matches (5 wins)
                    createMatchInSeason("Arsenal", "Crystal Palace", 2, 0, "H", LocalDate.of(2022, 8, 5)),
                    createMatchInSeason("Leicester", "Arsenal", 0, 4, "A", LocalDate.of(2022, 8, 13)),
                    createMatchInSeason("Arsenal", "Fulham", 2, 1, "H", LocalDate.of(2022, 8, 27)),
                    createMatchInSeason("Arsenal", "Aston Villa", 2, 1, "H", LocalDate.of(2022, 8, 31)),
                    createMatchInSeason("Arsenal", "Man United", 3, 1, "H", LocalDate.of(2022, 9, 4)),

                    // Man City matches (4 wins, 1 draw)
                    createMatchInSeason("Man City", "Bournemouth", 4, 0, "H", LocalDate.of(2022, 8, 13)),
                    createMatchInSeason("Man City", "Newcastle", 3, 3, "D", LocalDate.of(2022, 8, 21)),
                    createMatchInSeason("Man City", "Crystal Palace", 4, 2, "H", LocalDate.of(2022, 8, 27)),
                    createMatchInSeason("Man City", "Nott'm Forest", 6, 0, "H", LocalDate.of(2022, 8, 31)),
                    createMatchInSeason("Aston Villa", "Man City", 1, 2, "A", LocalDate.of(2022, 9, 3))
            );

            when(matchRepository.findBySeasonBeforeDateForTable(eq("2022-23"), any()))
                    .thenReturn(round1to5Matches);

            Map<String, Integer> standings = leaguePositionService
                    .calculateStandingsAsOfDate("2022-23", LocalDate.of(2022, 9, 5));

            // Arsenal: 5W, GD=+12 (13-1), Points=15 → should be 1st
            // Man City: 4W 1D, GD=+15 (19-4), Points=13 → should be 2nd
            assertThat(standings.get("Arsenal")).isEqualTo(1);
            assertThat(standings.get("Man City")).isEqualTo(2);
        }
    }

    // ── Helper methods ──────────────────────────────────────────────────

    private Match createMatchInSeason(String home, String away, int homeGoals, int awayGoals,
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
}
