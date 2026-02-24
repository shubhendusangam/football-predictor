package com.app.footballprediction.model;

import com.app.common.model.Match;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Match entity methods.
 */
@DisplayName("Match Entity Unit Tests")
class MatchTest {

    @Nested
    @DisplayName("getPointsForTeam()")
    class GetPointsForTeamTests {

        @Test
        @DisplayName("returns 3 points for home team on home win")
        void homeTeamWins_returns3Points() {
            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeResult("H")
                    .build();

            assertThat(match.getPointsForTeam("Arsenal")).isEqualTo(3);
        }

        @Test
        @DisplayName("returns 0 points for away team on home win")
        void awayTeamLoses_returns0Points() {
            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeResult("H")
                    .build();

            assertThat(match.getPointsForTeam("Chelsea")).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 3 points for away team on away win")
        void awayTeamWins_returns3Points() {
            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeResult("A")
                    .build();

            assertThat(match.getPointsForTeam("Chelsea")).isEqualTo(3);
        }

        @Test
        @DisplayName("returns 1 point for both teams on draw")
        void draw_returns1PointForBothTeams() {
            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeResult("D")
                    .build();

            assertThat(match.getPointsForTeam("Arsenal")).isEqualTo(1);
            assertThat(match.getPointsForTeam("Chelsea")).isEqualTo(1);
        }

        @Test
        @DisplayName("returns 0 for team not in match")
        void teamNotInMatch_returns0() {
            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeResult("H")
                    .build();

            assertThat(match.getPointsForTeam("Liverpool")).isEqualTo(0);
        }

        @Test
        @DisplayName("is case insensitive for team names")
        void caseInsensitive() {
            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeResult("H")
                    .build();

            assertThat(match.getPointsForTeam("arsenal")).isEqualTo(3);
            assertThat(match.getPointsForTeam("ARSENAL")).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("getGoalsScoredByTeam()")
    class GetGoalsScoredByTeamTests {

        @Test
        @DisplayName("returns home goals for home team")
        void homeTeam_returnsHomeGoals() {
            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeHomeGoals(3)
                    .fullTimeAwayGoals(1)
                    .fullTimeResult("H")
                    .build();

            assertThat(match.getGoalsScoredByTeam("Arsenal")).isEqualTo(3);
        }

        @Test
        @DisplayName("returns away goals for away team")
        void awayTeam_returnsAwayGoals() {
            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeHomeGoals(3)
                    .fullTimeAwayGoals(1)
                    .fullTimeResult("H")
                    .build();

            assertThat(match.getGoalsScoredByTeam("Chelsea")).isEqualTo(1);
        }

        @Test
        @DisplayName("returns 0 for team not in match")
        void teamNotInMatch_returns0() {
            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeHomeGoals(3)
                    .fullTimeAwayGoals(1)
                    .fullTimeResult("H")
                    .build();

            assertThat(match.getGoalsScoredByTeam("Liverpool")).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 0 when fullTimeResult is null (null safety)")
        void returnsZeroWhenResultIsNull() {
            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeHomeGoals(3)
                    .fullTimeAwayGoals(1)
                    .fullTimeResult(null)
                    .build();

            assertThat(match.getGoalsScoredByTeam("Arsenal")).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getGoalsConcededByTeam()")
    class GetGoalsConcededByTeamTests {

        @Test
        @DisplayName("returns away goals for home team")
        void homeTeam_returnsAwayGoals() {
            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeHomeGoals(3)
                    .fullTimeAwayGoals(1)
                    .fullTimeResult("H")
                    .build();

            assertThat(match.getGoalsConcededByTeam("Arsenal")).isEqualTo(1);
        }

        @Test
        @DisplayName("returns home goals for away team")
        void awayTeam_returnsHomeGoals() {
            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeHomeGoals(3)
                    .fullTimeAwayGoals(1)
                    .fullTimeResult("H")
                    .build();

            assertThat(match.getGoalsConcededByTeam("Chelsea")).isEqualTo(3);
        }

        @Test
        @DisplayName("returns 0 when fullTimeResult is null (null safety)")
        void returnsZeroWhenResultIsNull() {
            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeHomeGoals(3)
                    .fullTimeAwayGoals(1)
                    .fullTimeResult(null)
                    .build();

            assertThat(match.getGoalsConcededByTeam("Arsenal")).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Builder and Entity")
    class BuilderTests {

        @Test
        @DisplayName("builds match with all fields")
        void buildsCompleteMatch() {
            LocalDate date = LocalDate.of(2024, 1, 15);

            Match match = Match.builder()
                    .id(1L)
                    .matchDate(date)
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeHomeGoals(2)
                    .fullTimeAwayGoals(1)
                    .fullTimeResult("H")
                    .halfTimeHomeGoals(1)
                    .halfTimeAwayGoals(0)
                    .halfTimeResult("H")
                    .homeShots(15)
                    .awayShots(10)
                    .homeShotsOnTarget(6)
                    .awayShotsOnTarget(4)
                    .homeCorners(7)
                    .awayCorners(3)
                    .homeYellowCards(2)
                    .awayYellowCards(3)
                    .homeRedCards(0)
                    .awayRedCards(1)
                    .build();

            assertThat(match.getId()).isEqualTo(1L);
            assertThat(match.getMatchDate()).isEqualTo(date);
            assertThat(match.getHomeTeam()).isEqualTo("Arsenal");
            assertThat(match.getAwayTeam()).isEqualTo("Chelsea");
            assertThat(match.getFullTimeResult()).isEqualTo("H");
            assertThat(match.getHomeShots()).isEqualTo(15);
            assertThat(match.getHomeShotsOnTarget()).isEqualTo(6);
        }

        @Test
        @DisplayName("allows null optional fields")
        void allowsNullOptionalFields() {
            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeHomeGoals(2)
                    .fullTimeAwayGoals(1)
                    .fullTimeResult("H")
                    .build();

            assertThat(match.getHomeShots()).isNull();
            assertThat(match.getHomeShotsOnTarget()).isNull();
            assertThat(match.getHalfTimeHomeGoals()).isNull();
        }
    }
}

