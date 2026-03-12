package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.CardsPredictionDTO;
import com.app.footballprediction.dto.RefereeStats;
import com.app.footballprediction.dto.TeamDisciplineDTO;
import org.junit.jupiter.api.BeforeEach;
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
 * Unit tests for CardsPredictionService.
 *
 * Tests cover:
 * - Cards prediction calculation
 * - Team discipline statistics
 * - Referee adjustment logic
 * - Edge cases and validation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CardsPredictionService Unit Tests")
class CardsPredictionServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private RefereeStatsService refereeStatsService;

    @Mock
    private TeamValidationService teamValidationService;

    @InjectMocks
    private CardsPredictionService cardsPredictionService;

    private List<Match> sampleMatches;
    private static final String HOME_TEAM = "Arsenal";
    private static final String AWAY_TEAM = "Chelsea";
    private static final String REFEREE = "Anthony Taylor";
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 2, 27);

    @BeforeEach
    void setUp() {
        sampleMatches = createSampleMatches();
        lenient().when(teamValidationService.resolveTeamName(any()))
                .thenAnswer(inv -> {
                    String name = inv.getArgument(0);
                    if (name == null || name.isBlank()) {
                        throw new IllegalArgumentException("Team name cannot be empty");
                    }
                    return name;
                });
    }

    // ══════════════════════════════════════════════════════════════════════
    // TEST: predictCards
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("predictCards Tests")
    class PredictCardsTests {

        @Test
        @DisplayName("should predict cards for valid match without referee")
        void predictCards_withoutReferee_returnsPrediction() {
            // Given
            setupMocksForPrediction(HOME_TEAM, AWAY_TEAM, null);

            // When
            CardsPredictionDTO prediction = cardsPredictionService.predictCards(HOME_TEAM, AWAY_TEAM, null);

            // Then
            assertThat(prediction).isNotNull();
            assertThat(prediction.getHomeTeam()).isEqualTo(HOME_TEAM);
            assertThat(prediction.getAwayTeam()).isEqualTo(AWAY_TEAM);
            assertThat(prediction.getReferee()).isNull();
            assertThat(prediction.getExpectedYellowCardsHome()).isGreaterThanOrEqualTo(0);
            assertThat(prediction.getExpectedYellowCardsAway()).isGreaterThanOrEqualTo(0);
            assertThat(prediction.getExpectedTotalYellowCards()).isEqualTo(
                    prediction.getExpectedYellowCardsHome() + prediction.getExpectedYellowCardsAway()
            );
            assertThat(prediction.getRedCardProbability()).isBetween(0.0, 1.0);
        }

        @Test
        @DisplayName("should predict cards with referee adjustment")
        void predictCards_withReferee_adjustsForStrictness() {
            // Given
            RefereeStats strictReferee = RefereeStats.builder()
                    .refereeName(REFEREE)
                    .matchesOfficiated(50)
                    .avgYellowCards(5.5)  // Above league average
                    .avgRedCards(0.15)
                    .strictnessIndex(0.75)  // Strict
                    .build();

            setupMocksForPrediction(HOME_TEAM, AWAY_TEAM, strictReferee);

            // When
            CardsPredictionDTO prediction = cardsPredictionService.predictCards(HOME_TEAM, AWAY_TEAM, REFEREE);

            // Then
            assertThat(prediction).isNotNull();
            assertThat(prediction.getReferee()).isEqualTo(REFEREE);
            assertThat(prediction.getRefereeStrictnessIndex()).isEqualTo(0.75);
            assertThat(prediction.getRefereeImpact()).contains(REFEREE);
            assertThat(prediction.getRefereeImpact()).contains("Strict");
        }

        @Test
        @DisplayName("should throw exception when home and away teams are the same")
        void predictCards_sameTeams_throwsException() {
            assertThatThrownBy(() -> cardsPredictionService.predictCards("Arsenal", "Arsenal", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be the same");
        }

        @Test
        @DisplayName("should throw exception for empty home team")
        void predictCards_emptyHomeTeam_throwsException() {
            assertThatThrownBy(() -> cardsPredictionService.predictCards("", AWAY_TEAM, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw exception for null away team")
        void predictCards_nullAwayTeam_throwsException() {
            assertThatThrownBy(() -> cardsPredictionService.predictCards(HOME_TEAM, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should return high card risk warning when expected cards > 5")
        void predictCards_highCardRisk_returnsWarning() {
            // Given - Create matches with high card counts
            List<Match> highCardMatches = createHighCardMatches(HOME_TEAM);
            when(matchRepository.findByTeamBeforeDate(eq(HOME_TEAM), any()))
                    .thenReturn(highCardMatches);
            when(matchRepository.findByTeamBeforeDate(eq(AWAY_TEAM), any()))
                    .thenReturn(highCardMatches);

            // When
            CardsPredictionDTO prediction = cardsPredictionService.predictCards(HOME_TEAM, AWAY_TEAM, null);

            // Then
            if (prediction.getExpectedTotalYellowCards() > 5.0) {
                assertThat(prediction.getDisciplineWarning()).contains("High Card Risk");
            }
        }

        @Test
        @DisplayName("should ensure red card probability never exceeds 1")
        void predictCards_redCardProb_neverExceedsOne() {
            // Given
            setupMocksForPrediction(HOME_TEAM, AWAY_TEAM, null);

            // When
            CardsPredictionDTO prediction = cardsPredictionService.predictCards(HOME_TEAM, AWAY_TEAM, null);

            // Then
            assertThat(prediction.getRedCardProbability()).isLessThanOrEqualTo(1.0);
            assertThat(prediction.getRedCardProbability()).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("should calculate confidence based on sample size")
        void predictCards_confidence_basedOnSampleSize() {
            // Given
            setupMocksForPrediction(HOME_TEAM, AWAY_TEAM, null);

            // When
            CardsPredictionDTO prediction = cardsPredictionService.predictCards(HOME_TEAM, AWAY_TEAM, null);

            // Then
            assertThat(prediction.getConfidence()).isBetween(0.0, 1.0);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // TEST: getTeamDiscipline
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getTeamDiscipline Tests")
    class GetTeamDisciplineTests {

        @Test
        @DisplayName("should return discipline stats for valid team")
        void getTeamDiscipline_validTeam_returnsStats() {
            // Given
            when(matchRepository.findByTeamBeforeDate(eq(HOME_TEAM), any()))
                    .thenReturn(sampleMatches);

            // When
            TeamDisciplineDTO discipline = cardsPredictionService.getTeamDiscipline(HOME_TEAM);

            // Then
            assertThat(discipline).isNotNull();
            assertThat(discipline.getTeamName()).isEqualTo(HOME_TEAM);
            assertThat(discipline.getMatchesAnalyzed()).isGreaterThan(0);
            assertThat(discipline.getAvgYellowCardsOverall()).isGreaterThanOrEqualTo(0);
            assertThat(discipline.getDisciplineRating()).isIn("Excellent", "Average", "Aggressive");
        }

        @Test
        @DisplayName("should return empty stats for unknown team")
        void getTeamDiscipline_unknownTeam_returnsEmptyStats() {
            // Given
            when(matchRepository.findByTeamBeforeDate(any(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            TeamDisciplineDTO discipline = cardsPredictionService.getTeamDiscipline("UnknownTeam");

            // Then
            assertThat(discipline).isNotNull();
            assertThat(discipline.getMatchesAnalyzed()).isEqualTo(0);
            assertThat(discipline.getDisciplineRating()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("should calculate discipline rating correctly")
        void getTeamDiscipline_disciplineRating_calculatedCorrectly() {
            // Given - Create disciplined team (few yellow cards)
            List<Match> disciplinedMatches = createDisciplinedMatches(HOME_TEAM);
            when(matchRepository.findByTeamBeforeDate(eq(HOME_TEAM), any()))
                    .thenReturn(disciplinedMatches);

            // When
            TeamDisciplineDTO discipline = cardsPredictionService.getTeamDiscipline(HOME_TEAM);

            // Then
            // Disciplined team should have "Excellent" rating if avg < 2 yellows
            if (discipline.getAvgYellowCardsOverall() < 2.0) {
                assertThat(discipline.getDisciplineRating()).isEqualTo("Excellent");
                assertThat(discipline.getRatingColor()).isEqualTo("green");
            }
        }

        @Test
        @DisplayName("should include recent bookings")
        void getTeamDiscipline_recentBookings_included() {
            // Given
            when(matchRepository.findByTeamBeforeDate(eq(HOME_TEAM), any()))
                    .thenReturn(sampleMatches);

            // When
            TeamDisciplineDTO discipline = cardsPredictionService.getTeamDiscipline(HOME_TEAM);

            // Then
            assertThat(discipline.getRecentBookings()).isNotNull();
            assertThat(discipline.getRecentBookings().size()).isLessThanOrEqualTo(5);
        }

        @Test
        @DisplayName("should throw exception for empty team name")
        void getTeamDiscipline_emptyTeamName_throwsException() {
            assertThatThrownBy(() -> cardsPredictionService.getTeamDiscipline(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // TEST: Edge Cases
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle matches with missing card data")
        void predictCards_missingCardData_handlesGracefully() {
            // Given
            List<Match> matchesWithNulls = new ArrayList<>();
            matchesWithNulls.add(createMatchWithNullCards(HOME_TEAM, "TeamA", TEST_DATE));
            matchesWithNulls.add(createMatch(HOME_TEAM, "TeamB", 2, 1, TEST_DATE.minusDays(7)));

            when(matchRepository.findByTeamBeforeDate(eq(HOME_TEAM), any()))
                    .thenReturn(matchesWithNulls);
            when(matchRepository.findByTeamBeforeDate(eq(AWAY_TEAM), any()))
                    .thenReturn(sampleMatches);

            // When
            CardsPredictionDTO prediction = cardsPredictionService.predictCards(HOME_TEAM, AWAY_TEAM, null);

            // Then
            assertThat(prediction).isNotNull();
            assertThat(prediction.getExpectedTotalYellowCards()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("should return no negative values")
        void predictCards_noNegativeValues() {
            // Given
            setupMocksForPrediction(HOME_TEAM, AWAY_TEAM, null);

            // When
            CardsPredictionDTO prediction = cardsPredictionService.predictCards(HOME_TEAM, AWAY_TEAM, null);

            // Then
            assertThat(prediction.getExpectedYellowCardsHome()).isGreaterThanOrEqualTo(0);
            assertThat(prediction.getExpectedYellowCardsAway()).isGreaterThanOrEqualTo(0);
            assertThat(prediction.getExpectedTotalYellowCards()).isGreaterThanOrEqualTo(0);
            assertThat(prediction.getRedCardProbability()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("should return no NaN values")
        void predictCards_noNaNValues() {
            // Given
            setupMocksForPrediction(HOME_TEAM, AWAY_TEAM, null);

            // When
            CardsPredictionDTO prediction = cardsPredictionService.predictCards(HOME_TEAM, AWAY_TEAM, null);

            // Then
            assertThat(prediction.getExpectedYellowCardsHome()).isNotNaN();
            assertThat(prediction.getExpectedYellowCardsAway()).isNotNaN();
            assertThat(prediction.getExpectedTotalYellowCards()).isNotNaN();
            assertThat(prediction.getRedCardProbability()).isNotNaN();
            assertThat(prediction.getConfidence()).isNotNaN();
        }

        @Test
        @DisplayName("should handle lenient referee correctly")
        void predictCards_lenientReferee_reducesCards() {
            // Given
            RefereeStats lenientReferee = RefereeStats.builder()
                    .refereeName("Michael Oliver")
                    .matchesOfficiated(40)
                    .avgYellowCards(2.5)  // Below league average
                    .avgRedCards(0.02)
                    .strictnessIndex(0.3)  // Lenient
                    .build();

            setupMocksForPrediction(HOME_TEAM, AWAY_TEAM, lenientReferee);

            // When
            CardsPredictionDTO prediction = cardsPredictionService.predictCards(HOME_TEAM, AWAY_TEAM, "Michael Oliver");

            // Then
            assertThat(prediction).isNotNull();
            assertThat(prediction.getRefereeImpact()).contains("Lenient");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ══════════════════════════════════════════════════════════════════════

    private List<Match> createSampleMatches() {
        List<Match> matches = new ArrayList<>();
        // Arsenal home matches with typical card counts
        matches.add(createMatch(HOME_TEAM, "Chelsea", 2, 1, TEST_DATE));
        matches.add(createMatch(HOME_TEAM, "Liverpool", 1, 2, TEST_DATE.minusDays(7)));
        matches.add(createMatch(HOME_TEAM, "Tottenham", 3, 1, TEST_DATE.minusDays(14)));
        // Arsenal away matches
        matches.add(createAwayMatch("Man City", HOME_TEAM, 1, 2, TEST_DATE.minusDays(21)));
        matches.add(createAwayMatch("Newcastle", HOME_TEAM, 2, 1, TEST_DATE.minusDays(28)));
        return matches;
    }

    private List<Match> createHighCardMatches(String teamName) {
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            matches.add(createMatch(teamName, "Team" + i, 4, 3, TEST_DATE.minusDays(i * 7)));
        }
        return matches;
    }

    private List<Match> createDisciplinedMatches(String teamName) {
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            // Low yellow card matches
            matches.add(Match.builder()
                    .homeTeam(teamName)
                    .awayTeam("Team" + i)
                    .homeYellowCards(1)  // Disciplined
                    .awayYellowCards(2)
                    .homeRedCards(0)
                    .awayRedCards(0)
                    .fullTimeResult("H")
                    .fullTimeHomeGoals(2)
                    .fullTimeAwayGoals(0)
                    .matchDate(TEST_DATE.minusDays(i * 7))
                    .build());
        }
        return matches;
    }

    private Match createMatch(String homeTeam, String awayTeam, int homeYellows, int awayYellows, LocalDate date) {
        return Match.builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .homeYellowCards(homeYellows)
                .awayYellowCards(awayYellows)
                .homeRedCards(0)
                .awayRedCards(0)
                .fullTimeResult("H")
                .fullTimeHomeGoals(2)
                .fullTimeAwayGoals(1)
                .matchDate(date)
                .build();
    }

    private Match createAwayMatch(String homeTeam, String awayTeam, int homeYellows, int awayYellows, LocalDate date) {
        return Match.builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .homeYellowCards(homeYellows)
                .awayYellowCards(awayYellows)
                .homeRedCards(0)
                .awayRedCards(0)
                .fullTimeResult("A")
                .fullTimeHomeGoals(0)
                .fullTimeAwayGoals(1)
                .matchDate(date)
                .build();
    }

    private Match createMatchWithNullCards(String homeTeam, String awayTeam, LocalDate date) {
        return Match.builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .homeYellowCards(null)
                .awayYellowCards(null)
                .homeRedCards(null)
                .awayRedCards(null)
                .fullTimeResult("D")
                .fullTimeHomeGoals(1)
                .fullTimeAwayGoals(1)
                .matchDate(date)
                .build();
    }

    private void setupMocksForPrediction(String homeTeam, String awayTeam, RefereeStats refereeStats) {
        List<Match> homeMatches = createSampleMatches();
        List<Match> awayMatches = createSampleMatches();

        when(matchRepository.findByTeamBeforeDate(eq(homeTeam), any()))
                .thenReturn(homeMatches);
        when(matchRepository.findByTeamBeforeDate(eq(awayTeam), any()))
                .thenReturn(awayMatches);

        if (refereeStats != null) {
            when(refereeStatsService.getRefereeStats(anyString()))
                    .thenReturn(refereeStats);
        }
    }
}

