package com.app.footballprediction.service;

import com.app.common.model.PlayerAvailability;
import com.app.common.model.PlayerAvailability.AvailabilityStatus;
import com.app.common.repository.PlayerAvailabilityRepository;
import com.app.footballprediction.dto.PlayerAvailabilityDTO;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PlayerImpactService.
 * Tests squad strength calculation, attack/defence impact, probability adjustments,
 * and availability rating derivation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlayerImpactService Tests")
class PlayerImpactServiceTest {

    @Mock
    private PlayerAvailabilityRepository playerAvailabilityRepository;

    @InjectMocks
    private PlayerImpactService playerImpactService;

    private static final String TEAM = "Chelsea";
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 19);

    // ── Test Data Builders ───────────────────────────────────────────────

    private PlayerAvailability buildPlayer(String name, String position,
                                            AvailabilityStatus status,
                                            int importance, boolean keyStar,
                                            double goalsPerGame, double assistsPerGame) {
        return PlayerAvailability.builder()
                .teamName(TEAM)
                .playerName(name)
                .position(position)
                .status(status)
                .importanceRating(importance)
                .keyStar(keyStar)
                .avgGoalsPerGame(goalsPerGame)
                .avgAssistsPerGame(assistsPerGame)
                .expectedReturn(null)
                .reportDate(TODAY)
                .season("2025-26")
                .build();
    }

    private PlayerAvailability buildInjuredStar(String name, String position,
                                                 double goals, double assists) {
        return buildPlayer(name, position, AvailabilityStatus.INJURED, 9, true, goals, assists);
    }

    private PlayerAvailability buildInjuredRegular(String name, String position) {
        return buildPlayer(name, position, AvailabilityStatus.INJURED, 5, false, 0.1, 0.1);
    }

    private PlayerAvailability buildSuspendedPlayer(String name, String position, int importance) {
        return buildPlayer(name, position, AvailabilityStatus.SUSPENDED, importance, false, 0.0, 0.0);
    }

    private PlayerAvailability buildDoubtfulPlayer(String name, String position, int importance) {
        return buildPlayer(name, position, AvailabilityStatus.DOUBTFUL, importance, false, 0.1, 0.1);
    }

    // ══════════════════════════════════════════════════════════════════════
    // SQUAD STRENGTH TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("calculateSquadStrength()")
    class SquadStrengthTests {

        @Test
        @DisplayName("Full strength when no absences")
        void fullStrength_noAbsences() {
            when(playerAvailabilityRepository.findActiveAbsences(eq(TEAM), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            double strength = playerImpactService.calculateSquadStrength(TEAM, TODAY);

            assertThat(strength).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Reduced when key star injured (importance=9)")
        void reduced_keyStarInjured() {
            when(playerAvailabilityRepository.findActiveAbsences(eq(TEAM), any(LocalDate.class)))
                    .thenReturn(List.of(
                            buildInjuredStar("Cole Palmer", "MID", 0.8, 0.5)
                    ));

            double strength = playerImpactService.calculateSquadStrength(TEAM, TODAY);

            // Penalty = 9 * 1.0 = 9; Normalized = 9/30 = 0.3; Strength = 0.7
            assertThat(strength).isCloseTo(0.7, within(0.01));
        }

        @Test
        @DisplayName("Heavily reduced when multiple key stars injured")
        void heavilyReduced_multipleStarsInjured() {
            when(playerAvailabilityRepository.findActiveAbsences(eq(TEAM), any(LocalDate.class)))
                    .thenReturn(List.of(
                            buildInjuredStar("Cole Palmer", "MID", 0.8, 0.5),
                            buildInjuredStar("Reece James", "DEF", 0.1, 0.3),
                            buildInjuredStar("Enzo Fernandez", "MID", 0.2, 0.4)
                    ));

            double strength = playerImpactService.calculateSquadStrength(TEAM, TODAY);

            // Penalty = 27; Normalized = 27/30 = 0.9; Strength = 0.3 (floor)
            assertThat(strength).isCloseTo(0.3, within(0.05));
        }

        @Test
        @DisplayName("Doubtful players count at 50% weight")
        void doubtful_halfWeight() {
            when(playerAvailabilityRepository.findActiveAbsences(eq(TEAM), any(LocalDate.class)))
                    .thenReturn(List.of(
                            buildDoubtfulPlayer("Marc Cucurella", "DEF", 6)
                    ));

            double strength = playerImpactService.calculateSquadStrength(TEAM, TODAY);

            // Penalty = 6 * 0.5 = 3; Normalized = 3/30 = 0.1; Strength = 0.9
            assertThat(strength).isCloseTo(0.9, within(0.01));
        }

        @Test
        @DisplayName("Floor at 0.3 even with catastrophic injuries")
        void floor_at_0_3() {
            when(playerAvailabilityRepository.findActiveAbsences(eq(TEAM), any(LocalDate.class)))
                    .thenReturn(List.of(
                            buildInjuredStar("Player1", "FWD", 1.0, 0.5),
                            buildInjuredStar("Player2", "MID", 0.5, 0.8),
                            buildInjuredStar("Player3", "DEF", 0.1, 0.3),
                            buildPlayer("Player4", "GK", AvailabilityStatus.INJURED, 10, true, 0, 0)
                    ));

            double strength = playerImpactService.calculateSquadStrength(TEAM, TODAY);

            assertThat(strength).isGreaterThanOrEqualTo(0.3);
        }

        @Test
        @DisplayName("Minor impact from fringe player (importance=2)")
        void minorImpact_fringePlayer() {
            when(playerAvailabilityRepository.findActiveAbsences(eq(TEAM), any(LocalDate.class)))
                    .thenReturn(List.of(
                            buildPlayer("Youth Player", "FWD", AvailabilityStatus.INJURED,
                                    2, false, 0.0, 0.0)
                    ));

            double strength = playerImpactService.calculateSquadStrength(TEAM, TODAY);

            // Penalty = 2; Normalized = 2/30 ≈ 0.067; Strength ≈ 0.933
            assertThat(strength).isGreaterThan(0.9);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ATTACK IMPACT TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("calculateAttackImpact()")
    class AttackImpactTests {

        @Test
        @DisplayName("Zero attack impact when no absences")
        void zeroImpact_noAbsences() {
            when(playerAvailabilityRepository.findActiveAbsences(eq(TEAM), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            double impact = playerImpactService.calculateAttackImpact(TEAM, TODAY);

            assertThat(impact).isEqualTo(0.0);
        }

        @Test
        @DisplayName("High attack impact when prolific scorer injured")
        void highImpact_topScorerInjured() {
            when(playerAvailabilityRepository.findActiveAbsences(eq(TEAM), any(LocalDate.class)))
                    .thenReturn(List.of(
                            buildInjuredStar("Erling Haaland", "FWD", 1.0, 0.3)
                    ));

            double impact = playerImpactService.calculateAttackImpact(TEAM, TODAY);

            // Attack contribution = (1.0*2 + 0.3)/3 ≈ 0.767; × 1.0 × (9/10) ≈ 0.69
            assertThat(impact).isGreaterThan(0.5);
        }

        @Test
        @DisplayName("Low attack impact when defender injured (low goals/assists)")
        void lowImpact_defenderInjured() {
            when(playerAvailabilityRepository.findActiveAbsences(eq(TEAM), any(LocalDate.class)))
                    .thenReturn(List.of(
                            buildPlayer("Virgil van Dijk", "DEF", AvailabilityStatus.INJURED,
                                    8, true, 0.05, 0.02)
                    ));

            double impact = playerImpactService.calculateAttackImpact(TEAM, TODAY);

            assertThat(impact).isLessThan(0.1);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DEFENCE IMPACT TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("calculateDefenceImpact()")
    class DefenceImpactTests {

        @Test
        @DisplayName("Zero defence impact when no absences")
        void zeroImpact_noAbsences() {
            when(playerAvailabilityRepository.findActiveAbsences(eq(TEAM), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            double impact = playerImpactService.calculateDefenceImpact(TEAM, TODAY);

            assertThat(impact).isEqualTo(0.0);
        }

        @Test
        @DisplayName("High defence impact when key defender injured")
        void highImpact_keyDefenderInjured() {
            when(playerAvailabilityRepository.findActiveAbsences(eq(TEAM), any(LocalDate.class)))
                    .thenReturn(List.of(
                            buildPlayer("Reece James", "DEF", AvailabilityStatus.INJURED,
                                    9, true, 0.1, 0.3)
                    ));

            double impact = playerImpactService.calculateDefenceImpact(TEAM, TODAY);

            // DEF weight = 0.8; × 1.0 × (9/10) = 0.72
            assertThat(impact).isGreaterThan(0.5);
        }

        @Test
        @DisplayName("GK injury has very high defence impact")
        void veryHighImpact_goalkeeperInjured() {
            when(playerAvailabilityRepository.findActiveAbsences(eq(TEAM), any(LocalDate.class)))
                    .thenReturn(List.of(
                            buildPlayer("Robert Sanchez", "GK", AvailabilityStatus.INJURED,
                                    8, false, 0.0, 0.0)
                    ));

            double impact = playerImpactService.calculateDefenceImpact(TEAM, TODAY);

            // GK weight = 0.9; × 1.0 × (8/10) = 0.72
            assertThat(impact).isGreaterThan(0.6);
        }

        @Test
        @DisplayName("FWD injury has minimal defence impact")
        void minimalImpact_forwardInjured() {
            when(playerAvailabilityRepository.findActiveAbsences(eq(TEAM), any(LocalDate.class)))
                    .thenReturn(List.of(
                            buildInjuredStar("Erling Haaland", "FWD", 1.0, 0.3)
                    ));

            double impact = playerImpactService.calculateDefenceImpact(TEAM, TODAY);

            // FWD weight = 0.15; × 1.0 × (9/10) = 0.135
            assertThat(impact).isLessThan(0.2);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PROBABILITY ADJUSTMENT TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("adjustPredictionProbabilities()")
    class ProbabilityAdjustmentTests {

        @Test
        @DisplayName("No adjustment when both teams at full strength")
        void noAdjustment_bothFullStrength() {
            double[] probs = {0.5, 0.25, 0.25};
            double[] adjusted = playerImpactService
                    .adjustPredictionProbabilities(probs, 1.0, 1.0);

            assertThat(adjusted[0]).isCloseTo(0.5, within(0.02));
            assertThat(adjusted[1]).isCloseTo(0.25, within(0.02));
            assertThat(adjusted[2]).isCloseTo(0.25, within(0.02));
        }

        @Test
        @DisplayName("Shifts toward home when away team is weakened")
        void shiftsToHome_awayWeakened() {
            double[] probs = {0.4, 0.3, 0.3};
            double[] adjusted = playerImpactService
                    .adjustPredictionProbabilities(probs, 1.0, 0.6);

            assertThat(adjusted[0]).isGreaterThan(probs[0]);
            assertThat(adjusted[2]).isLessThan(probs[2]);
        }

        @Test
        @DisplayName("Shifts toward away when home team is weakened")
        void shiftsToAway_homeWeakened() {
            double[] probs = {0.4, 0.3, 0.3};
            double[] adjusted = playerImpactService
                    .adjustPredictionProbabilities(probs, 0.5, 1.0);

            assertThat(adjusted[0]).isLessThan(probs[0]);
            assertThat(adjusted[2]).isGreaterThan(probs[2]);
        }

        @Test
        @DisplayName("Probabilities sum to 1.0 after adjustment")
        void probabilitiesSumToOne() {
            double[] probs = {0.45, 0.30, 0.25};
            double[] adjusted = playerImpactService
                    .adjustPredictionProbabilities(probs, 0.7, 0.9);

            double sum = adjusted[0] + adjusted[1] + adjusted[2];
            assertThat(sum).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("Returns original if probs is null")
        void returnsOriginal_ifNull() {
            double[] result = playerImpactService
                    .adjustPredictionProbabilities(null, 1.0, 1.0);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("All probabilities stay above 0.01")
        void probsStayAboveMinimum() {
            double[] probs = {0.02, 0.02, 0.96};
            double[] adjusted = playerImpactService
                    .adjustPredictionProbabilities(probs, 0.3, 1.0);

            // After renormalization, values may dip slightly below 0.01 but stay positive
            assertThat(adjusted[0]).isGreaterThan(0.0);
            assertThat(adjusted[1]).isGreaterThan(0.0);
            assertThat(adjusted[2]).isGreaterThan(0.0);
            // Sum should still be 1.0
            assertThat(adjusted[0] + adjusted[1] + adjusted[2]).isCloseTo(1.0, within(0.001));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // AVAILABILITY DTO TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getTeamAvailability()")
    class AvailabilityDTOTests {

        @Test
        @DisplayName("Returns FULL_STRENGTH when no absences")
        void fullStrength() {
            when(playerAvailabilityRepository.findActiveAbsences(eq(TEAM), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            PlayerAvailabilityDTO dto = playerImpactService.getTeamAvailability(TEAM, TODAY);

            assertThat(dto.getTeamName()).isEqualTo(TEAM);
            assertThat(dto.getSquadStrength()).isEqualTo(1.0);
            assertThat(dto.getAvailabilityRating()).isEqualTo("FULL_STRENGTH");
            assertThat(dto.getAbsentPlayers()).isEmpty();
            assertThat(dto.getAvailabilityNote()).contains("full strength");
        }

        @Test
        @DisplayName("Returns WEAKENED with absent player details")
        void weakened_withDetails() {
            PlayerAvailability reece = buildPlayer("Reece James", "DEF",
                    AvailabilityStatus.INJURED, 8, true, 0.1, 0.3);
            reece.setReason("Hamstring injury");

            when(playerAvailabilityRepository.findActiveAbsences(eq(TEAM), any(LocalDate.class)))
                    .thenReturn(List.of(reece));

            PlayerAvailabilityDTO dto = playerImpactService.getTeamAvailability(TEAM, TODAY);

            assertThat(dto.getAvailabilityRating()).isIn("MINOR_CONCERNS", "WEAKENED");
            assertThat(dto.getAbsentPlayers()).hasSize(1);
            assertThat(dto.getAbsentPlayers().get(0).getPlayerName()).isEqualTo("Reece James");
            assertThat(dto.getAbsentPlayers().get(0).getStatus()).isEqualTo("INJURED");
            assertThat(dto.getAvailabilityNote()).contains("Reece James");
        }

        @Test
        @DisplayName("Returns SEVERELY_WEAKENED when multiple stars out")
        void severelyWeakened() {
            when(playerAvailabilityRepository.findActiveAbsences(eq(TEAM), any(LocalDate.class)))
                    .thenReturn(List.of(
                            buildInjuredStar("Star1", "FWD", 0.8, 0.4),
                            buildInjuredStar("Star2", "MID", 0.5, 0.6),
                            buildInjuredStar("Star3", "DEF", 0.1, 0.2)
                    ));

            PlayerAvailabilityDTO dto = playerImpactService.getTeamAvailability(TEAM, TODAY);

            assertThat(dto.getAvailabilityRating()).isEqualTo("SEVERELY_WEAKENED");
            assertThat(dto.getSquadStrength()).isLessThanOrEqualTo(0.3);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // STATIC HELPER TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Static helpers")
    class StaticHelperTests {

        @Test
        @DisplayName("statusWeight returns correct values")
        void statusWeight() {
            assertThat(PlayerImpactService.statusWeight(AvailabilityStatus.INJURED)).isEqualTo(1.0);
            assertThat(PlayerImpactService.statusWeight(AvailabilityStatus.SUSPENDED)).isEqualTo(1.0);
            assertThat(PlayerImpactService.statusWeight(AvailabilityStatus.DOUBTFUL)).isEqualTo(0.5);
            assertThat(PlayerImpactService.statusWeight(AvailabilityStatus.AVAILABLE)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("defencePositionWeight returns highest for GK")
        void defencePositionWeight() {
            assertThat(PlayerImpactService.defencePositionWeight("GK")).isEqualTo(0.9);
            assertThat(PlayerImpactService.defencePositionWeight("DEF")).isEqualTo(0.8);
            assertThat(PlayerImpactService.defencePositionWeight("MID")).isEqualTo(0.4);
            assertThat(PlayerImpactService.defencePositionWeight("FWD")).isEqualTo(0.15);
            assertThat(PlayerImpactService.defencePositionWeight(null)).isEqualTo(0.3);
        }

        @Test
        @DisplayName("deriveRating thresholds are correct")
        void deriveRating() {
            assertThat(PlayerImpactService.deriveRating(1.0)).isEqualTo("FULL_STRENGTH");
            assertThat(PlayerImpactService.deriveRating(0.95)).isEqualTo("FULL_STRENGTH");
            assertThat(PlayerImpactService.deriveRating(0.85)).isEqualTo("MINOR_CONCERNS");
            assertThat(PlayerImpactService.deriveRating(0.70)).isEqualTo("WEAKENED");
            assertThat(PlayerImpactService.deriveRating(0.50)).isEqualTo("SEVERELY_WEAKENED");
            assertThat(PlayerImpactService.deriveRating(0.3)).isEqualTo("SEVERELY_WEAKENED");
        }
    }
}

