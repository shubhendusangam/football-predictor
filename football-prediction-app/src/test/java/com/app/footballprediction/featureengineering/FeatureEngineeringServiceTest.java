package com.app.footballprediction.featureengineering;

import com.app.common.model.Match;
import com.app.common.model.MatchFeatures;
import com.app.common.repository.MatchRepository;
import com.app.common.service.FeatureEngineeringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FeatureEngineeringService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FeatureEngineeringService Unit Tests")
class FeatureEngineeringServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private FeatureEngineeringService featureEngineeringService;

    @BeforeEach
    void setUp() {
        // Set form window to 5 (default)
        ReflectionTestUtils.setField(featureEngineeringService, "formWindow", 5);
    }

    @Nested
    @DisplayName("buildFeaturesForPrediction()")
    class BuildFeaturesForPredictionTests {

        @Test
        @DisplayName("builds features with historical data")
        void buildsFeaturesWithHistory() {
            // Given: Arsenal has won 3 home matches
            List<Match> arsenalHomeMatches = List.of(
                    createMatch("Arsenal", "Chelsea", 2, 1, "H"),
                    createMatch("Arsenal", "Liverpool", 3, 0, "H"),
                    createMatch("Arsenal", "Tottenham", 2, 2, "D")
            );

            // Chelsea has won 2 away matches
            List<Match> chelseaAwayMatches = List.of(
                    createMatch("Liverpool", "Chelsea", 1, 2, "A"),
                    createMatch("Man United", "Chelsea", 0, 1, "A")
            );

            when(matchRepository.findHomeMatchesByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(arsenalHomeMatches);
            when(matchRepository.findAwayMatchesByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(chelseaAwayMatches);
            when(matchRepository.findByTeamBeforeDate(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findH2HBeforeDate(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            // When
            MatchFeatures features = featureEngineeringService
                    .buildFeaturesForPrediction("Arsenal", "Chelsea");

            // Then
            assertThat(features.getHomeTeam()).isEqualTo("Arsenal");
            assertThat(features.getAwayTeam()).isEqualTo("Chelsea");
            assertThat(features.getHomeFormPoints()).isGreaterThan(0);
            assertThat(features.getActualResult()).isNull(); // Prediction, no label
        }

        @Test
        @DisplayName("returns zero form when no history")
        void returnsZeroFormWhenNoHistory() {
            when(matchRepository.findHomeMatchesByTeamBeforeDate(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findAwayMatchesByTeamBeforeDate(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByTeamBeforeDate(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findH2HBeforeDate(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            MatchFeatures features = featureEngineeringService
                    .buildFeaturesForPrediction("NewTeam1", "NewTeam2");

            assertThat(features.getHomeFormPoints()).isEqualTo(0.0);
            assertThat(features.getAwayFormPoints()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns neutral H2H rates (0.33) when no H2H history")
        void returnsNeutralH2HWhenNoHistory() {
            when(matchRepository.findHomeMatchesByTeamBeforeDate(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findAwayMatchesByTeamBeforeDate(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByTeamBeforeDate(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findH2HBeforeDate(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            MatchFeatures features = featureEngineeringService
                    .buildFeaturesForPrediction("Arsenal", "Chelsea");

            // Neutral prior: ~0.33 each
            assertThat(features.getH2hHomeWinRate()).isCloseTo(0.33,
                    org.assertj.core.data.Offset.offset(0.01));
            assertThat(features.getH2hDrawRate()).isCloseTo(0.33,
                    org.assertj.core.data.Offset.offset(0.01));
            assertThat(features.getH2hAwayWinRate()).isCloseTo(0.33,
                    org.assertj.core.data.Offset.offset(0.01));
        }
    }

    @Nested
    @DisplayName("buildFeaturesForTraining()")
    class BuildFeaturesForTrainingTests {

        @Test
        @DisplayName("sets actual result label for training")
        void setsActualResultLabel() {
            Match match = createMatchWithDate(
                  LocalDate.of(2024, 1, 15));

            when(matchRepository.findHomeMatchesByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findAwayMatchesByTeamBeforeDate(eq("Chelsea"), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByTeamBeforeDate(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findH2HBeforeDate(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            MatchFeatures features = featureEngineeringService.buildFeaturesForTraining(match);

            assertThat(features.getActualResult()).isEqualTo("H");
        }

        @Test
        @DisplayName("uses match date as cutoff to prevent data leakage")
        void usesMatchDateAsCutoff() {
            Match match = createMatchWithDate(
                  LocalDate.of(2024, 3, 15));

            when(matchRepository.findHomeMatchesByTeamBeforeDate(eq("Arsenal"),
                    eq(LocalDate.of(2024, 3, 15))))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findAwayMatchesByTeamBeforeDate(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByTeamBeforeDate(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findH2HBeforeDate(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            featureEngineeringService.buildFeaturesForTraining(match);

            // Verification: queries use the match date as cutoff
            // (implicitly verified by mockito matching the exact date)
        }
    }

    @Nested
    @DisplayName("Form Calculation")
    class FormCalculationTests {

        @Test
        @DisplayName("calculates points per game correctly")
        void calculatesPointsPerGame() {
            // 3 wins = 9 points, 1 draw = 1 point, 1 loss = 0
            // Total: 10 points / 5 games = 2.0 ppg
            List<Match> matches = List.of(
                    createMatch("Arsenal", "Chelsea", 2, 0, "H"),    // 3 pts
                    createMatch("Arsenal", "Liverpool", 3, 1, "H"), // 3 pts
                    createMatch("Arsenal", "Tottenham", 1, 1, "D"), // 1 pt
                    createMatch("Arsenal", "Man City", 1, 0, "H"),  // 3 pts
                    createMatch("Arsenal", "Man United", 0, 2, "A") // 0 pts
            );

            when(matchRepository.findHomeMatchesByTeamBeforeDate(eq("Arsenal"), any()))
                    .thenReturn(matches);
            when(matchRepository.findAwayMatchesByTeamBeforeDate(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findByTeamBeforeDate(any(), any()))
                    .thenReturn(Collections.emptyList());
            when(matchRepository.findH2HBeforeDate(any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            MatchFeatures features = featureEngineeringService
                    .buildFeaturesForPrediction("Arsenal", "Other");

            assertThat(features.getHomeFormPoints()).isEqualTo(2.0);
        }
    }

    // ── Helper methods ──────────────────────────────────────────────────

    private Match createMatch(String home, String away, int homeGoals, int awayGoals, String result) {
        return Match.builder()
                .homeTeam(home)
                .awayTeam(away)
                .fullTimeHomeGoals(homeGoals)
                .fullTimeAwayGoals(awayGoals)
                .fullTimeResult(result)
                .matchDate(LocalDate.of(2024, 1, 1))
                .build();
    }

    private Match createMatchWithDate(LocalDate date) {
        return Match.builder()
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .fullTimeHomeGoals(2)
                .fullTimeAwayGoals(1)
                .fullTimeResult("H")
                .matchDate(date)
                .build();
    }
}

