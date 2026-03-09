package com.app.footballprediction.service;

import com.app.common.model.Match;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FeatureRecalculationService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FeatureRecalculationService Unit Tests")
class FeatureRecalculationServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private FeatureEngineeringService featureEngineeringService;

    @InjectMocks
    private FeatureRecalculationService featureRecalculationService;

    private List<Match> sampleMatches;

    @BeforeEach
    void setUp() {
        sampleMatches = List.of(
                Match.builder()
                        .id(1L)
                        .homeTeam("Arsenal")
                        .awayTeam("Chelsea")
                        .matchDate(LocalDate.of(2026, 3, 1))
                        .fullTimeHomeGoals(2)
                        .fullTimeAwayGoals(1)
                        .fullTimeResult("H")
                        .homeShots(15)
                        .awayShots(10)
                        .homeShotsOnTarget(6)
                        .awayShotsOnTarget(4)
                        .homeYellowCards(2)
                        .awayYellowCards(3)
                        .homeCorners(7)
                        .awayCorners(4)
                        .build(),
                Match.builder()
                        .id(2L)
                        .homeTeam("Arsenal")
                        .awayTeam("Liverpool")
                        .matchDate(LocalDate.of(2026, 2, 22))
                        .fullTimeHomeGoals(1)
                        .fullTimeAwayGoals(1)
                        .fullTimeResult("D")
                        .homeShots(12)
                        .awayShots(14)
                        .homeShotsOnTarget(5)
                        .awayShotsOnTarget(6)
                        .homeYellowCards(1)
                        .awayYellowCards(2)
                        .homeCorners(5)
                        .awayCorners(6)
                        .build()
        );
    }

    @Nested
    @DisplayName("recalculateForRecentMatches()")
    class RecalculateForRecentMatchesTests {

        @Test
        @DisplayName("returns empty map for null input")
        void returnsEmptyForNull() {
            Map<String, FeatureRecalculationService.FeatureSnapshot> result =
                    featureRecalculationService.recalculateForRecentMatches(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty map for empty list")
        void returnsEmptyForEmptyList() {
            Map<String, FeatureRecalculationService.FeatureSnapshot> result =
                    featureRecalculationService.recalculateForRecentMatches(List.of());
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("recalculates features for all teams in matches")
        void recalculatesForAllTeams() {
            when(matchRepository.findByTeamBeforeDateIgnoreCase(anyString(), any(LocalDate.class)))
                    .thenReturn(sampleMatches);

            Match match = Match.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .build();

            Map<String, FeatureRecalculationService.FeatureSnapshot> result =
                    featureRecalculationService.recalculateForRecentMatches(List.of(match));

            assertThat(result).hasSize(2);
            assertThat(result).containsKey("Arsenal");
            assertThat(result).containsKey("Chelsea");
        }
    }

    @Nested
    @DisplayName("recalculateTeamFeatures()")
    class RecalculateTeamFeaturesTests {

        @Test
        @DisplayName("returns default features when no match history exists")
        void returnsDefaultsForNoHistory() {
            when(matchRepository.findByTeamBeforeDateIgnoreCase(anyString(), any(LocalDate.class)))
                    .thenReturn(List.of());

            FeatureRecalculationService.FeatureSnapshot result =
                    featureRecalculationService.recalculateTeamFeatures("NewTeam");

            assertThat(result.teamName()).isEqualTo("NewTeam");
            assertThat(result.teamFormScore()).isEqualTo(0.5);
            assertThat(result.expectedGoalsAverage()).isEqualTo(1.2);
        }

        @Test
        @DisplayName("calculates team form score from recent matches")
        void calculatesTeamFormScore() {
            when(matchRepository.findByTeamBeforeDateIgnoreCase(anyString(), any(LocalDate.class)))
                    .thenReturn(sampleMatches);

            FeatureRecalculationService.FeatureSnapshot result =
                    featureRecalculationService.recalculateTeamFeatures("Arsenal");

            // Arsenal: 3 pts (win) + 1 pt (draw) = 4 pts out of 6 max = 4/6 = 0.667
            assertThat(result.teamFormScore()).isCloseTo(0.667, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("calculates expected goals average")
        void calculatesExpectedGoalsAvg() {
            when(matchRepository.findByTeamBeforeDateIgnoreCase(anyString(), any(LocalDate.class)))
                    .thenReturn(sampleMatches);

            FeatureRecalculationService.FeatureSnapshot result =
                    featureRecalculationService.recalculateTeamFeatures("Arsenal");

            // Arsenal scored: 2 + 1 = 3 goals in 2 matches = 1.5 avg
            assertThat(result.expectedGoalsAverage()).isCloseTo(1.5, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("calculates shot quality score")
        void calculatesShotQuality() {
            when(matchRepository.findByTeamBeforeDateIgnoreCase(anyString(), any(LocalDate.class)))
                    .thenReturn(sampleMatches);

            FeatureRecalculationService.FeatureSnapshot result =
                    featureRecalculationService.recalculateTeamFeatures("Arsenal");

            // Arsenal: shots on target (6+5=11), total shots (15+12=27)
            // SOT ratio = 11/27 ≈ 0.407
            assertThat(result.shotQualityScore()).isCloseTo(0.407, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("calculates defensive strength score")
        void calculatesDefensiveStrength() {
            when(matchRepository.findByTeamBeforeDateIgnoreCase(anyString(), any(LocalDate.class)))
                    .thenReturn(sampleMatches);

            FeatureRecalculationService.FeatureSnapshot result =
                    featureRecalculationService.recalculateTeamFeatures("Arsenal");

            // Arsenal conceded: 1 + 1 = 2 in 2 matches = 1.0 avg
            // No clean sheets
            // Score = max(0, 1.0 - 1.0/3.0) * 0.6 + 0/2 * 0.4 = 0.667 * 0.6 = 0.4
            assertThat(result.defensiveStrengthScore()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("calculates card aggression index")
        void calculatesCardIndex() {
            when(matchRepository.findByTeamBeforeDateIgnoreCase(anyString(), any(LocalDate.class)))
                    .thenReturn(sampleMatches);

            FeatureRecalculationService.FeatureSnapshot result =
                    featureRecalculationService.recalculateTeamFeatures("Arsenal");

            // Arsenal cards: 2 + 1 = 3 in 2 matches = 1.5 avg
            assertThat(result.cardAggressionIndex()).isCloseTo(1.5, org.assertj.core.data.Offset.offset(0.01));
        }
    }
}

