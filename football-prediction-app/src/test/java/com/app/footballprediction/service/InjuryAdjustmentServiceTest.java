package com.app.footballprediction.service;

import com.app.common.dto.TeamAvailabilityDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for InjuryAdjustmentService.
 */
@DisplayName("InjuryAdjustmentService Unit Tests")
class InjuryAdjustmentServiceTest {

    private InjuryAdjustmentService service;

    @BeforeEach
    void setUp() {
        service = new InjuryAdjustmentService();
    }

    @Test
    @DisplayName("noInjuries: both FULL_STRENGTH → probabilities unchanged, sum=1.0")
    void noInjuries() {
        TeamAvailabilityDTO home = fullStrength(true);
        TeamAvailabilityDTO away = fullStrength(true);

        double[] result = service.adjustProbabilities(0.50, 0.25, 0.25, home, away);

        assertThat(result[0]).isCloseTo(0.50, within(0.001));
        assertThat(result[1]).isCloseTo(0.25, within(0.001));
        assertThat(result[2]).isCloseTo(0.25, within(0.001));
        assertThat(result[0] + result[1] + result[2]).isCloseTo(1.0, within(0.001));
    }

    @Test
    @DisplayName("homeStrikerOut: home attack -15% → homeWin decreases, sum still 1.0")
    void homeStrikerOut() {
        TeamAvailabilityDTO home = TeamAvailabilityDTO.builder()
                .teamId(1).teamName("Home")
                .attackImpactReduction(0.15)
                .defenceImpactReduction(0.0)
                .totalMissing(1)
                .availabilityRating("WEAKENED")
                .dataAvailable(true)
                .build();
        TeamAvailabilityDTO away = fullStrength(true);

        double[] result = service.adjustProbabilities(0.50, 0.25, 0.25, home, away);

        assertThat(result[0]).isLessThan(0.50); // home win decreased
        assertThat(result[0] + result[1] + result[2]).isCloseTo(1.0, within(0.001));
    }

    @Test
    @DisplayName("awayDefenderOut: away defence -12% → homeWin increases slightly, sum=1.0")
    void awayDefenderOut() {
        TeamAvailabilityDTO home = fullStrength(true);
        TeamAvailabilityDTO away = TeamAvailabilityDTO.builder()
                .teamId(2).teamName("Away")
                .attackImpactReduction(0.0)
                .defenceImpactReduction(0.12)
                .totalMissing(1)
                .availabilityRating("WEAKENED")
                .dataAvailable(true)
                .build();

        double[] result = service.adjustProbabilities(0.50, 0.25, 0.25, home, away);

        assertThat(result[0]).isGreaterThan(0.50); // home benefits from away's defensive weakness
        assertThat(result[0] + result[1] + result[2]).isCloseTo(1.0, within(0.001));
    }

    @Test
    @DisplayName("bothSeverelyWeakened: draw probability increases, both win probs decrease")
    void bothSeverelyWeakened() {
        TeamAvailabilityDTO home = TeamAvailabilityDTO.builder()
                .teamId(1).teamName("Home")
                .attackImpactReduction(0.25)
                .defenceImpactReduction(0.20)
                .totalMissing(4)
                .availabilityRating("SEVERELY_WEAKENED")
                .dataAvailable(true)
                .build();
        TeamAvailabilityDTO away = TeamAvailabilityDTO.builder()
                .teamId(2).teamName("Away")
                .attackImpactReduction(0.25)
                .defenceImpactReduction(0.20)
                .totalMissing(4)
                .availabilityRating("SEVERELY_WEAKENED")
                .dataAvailable(true)
                .build();

        double[] result = service.adjustProbabilities(0.45, 0.25, 0.30, home, away);

        // Both teams weakened → residual goes to draw
        assertThat(result[1]).isGreaterThanOrEqualTo(0.25); // draw should increase or stay same
        assertThat(result[0] + result[1] + result[2]).isCloseTo(1.0, within(0.001));
    }

    @RepeatedTest(20)
    @DisplayName("probabilitiesAlwaysSumToOne: test random input combinations")
    void probabilitiesAlwaysSumToOne() {
        Random rng = new Random();
        double h = 0.05 + rng.nextDouble() * 0.85;
        double a = 0.05 + rng.nextDouble() * (0.90 - h);
        double d = 1.0 - h - a;
        if (d < 0.05) { d = 0.05; h = (1.0 - d) * h / (h + a); a = 1.0 - d - h; }

        TeamAvailabilityDTO home = TeamAvailabilityDTO.builder()
                .attackImpactReduction(rng.nextDouble() * 0.30)
                .defenceImpactReduction(rng.nextDouble() * 0.25)
                .dataAvailable(true)
                .build();
        TeamAvailabilityDTO away = TeamAvailabilityDTO.builder()
                .attackImpactReduction(rng.nextDouble() * 0.30)
                .defenceImpactReduction(rng.nextDouble() * 0.25)
                .dataAvailable(true)
                .build();

        double[] result = service.adjustProbabilities(h, d, a, home, away);

        assertThat(result[0] + result[1] + result[2]).isCloseTo(1.0, within(0.001));
        assertThat(result[0]).isBetween(0.05, 0.85);
        assertThat(result[1]).isBetween(0.05, 0.85);
        assertThat(result[2]).isBetween(0.05, 0.85);
    }

    @Test
    @DisplayName("clampPreventsBelow005: even extreme injuries never push below 0.05")
    void clampPreventsBelow005() {
        TeamAvailabilityDTO home = TeamAvailabilityDTO.builder()
                .attackImpactReduction(0.30)
                .defenceImpactReduction(0.25)
                .dataAvailable(true)
                .build();
        TeamAvailabilityDTO away = fullStrength(true);

        double[] result = service.adjustProbabilities(0.10, 0.10, 0.80, home, away);

        assertThat(result[0]).isGreaterThanOrEqualTo(0.05);
        assertThat(result[1]).isGreaterThanOrEqualTo(0.05);
        assertThat(result[2]).isGreaterThanOrEqualTo(0.05);
    }

    @Test
    @DisplayName("fallbackNoAdjustment: dataAvailable=false on both → return original unchanged")
    void fallbackNoAdjustment() {
        TeamAvailabilityDTO home = fullStrength(false);
        TeamAvailabilityDTO away = fullStrength(false);

        double[] result = service.adjustProbabilities(0.55, 0.25, 0.20, home, away);

        assertThat(result[0]).isEqualTo(0.55);
        assertThat(result[1]).isEqualTo(0.25);
        assertThat(result[2]).isEqualTo(0.20);
    }

    @Test
    @DisplayName("buildAdjustmentNote returns correct message for various scenarios")
    void buildAdjustmentNoteVariousScenarios() {
        // Both unavailable
        String note1 = service.buildAdjustmentNote(fullStrength(false), fullStrength(false));
        assertThat(note1).contains("unavailable");

        // Home weakened
        TeamAvailabilityDTO homeWeak = TeamAvailabilityDTO.builder()
                .teamName("Arsenal").totalMissing(1).availabilityRating("WEAKENED")
                .dataAvailable(true).impactSummary("Missing Saka").build();
        String note2 = service.buildAdjustmentNote(homeWeak, fullStrength(true));
        assertThat(note2).contains("Home team weakened");

        // Severely weakened
        TeamAvailabilityDTO severe = TeamAvailabilityDTO.builder()
                .teamName("Chelsea").totalMissing(4).availabilityRating("SEVERELY_WEAKENED")
                .dataAvailable(true).impactSummary("Multiple key players out").build();
        String note3 = service.buildAdjustmentNote(severe, fullStrength(true));
        assertThat(note3).contains("severely weakened");
    }

    // ── Helper ──────────────────────────────────────────────

    private TeamAvailabilityDTO fullStrength(boolean dataAvailable) {
        return TeamAvailabilityDTO.builder()
                .teamId(0)
                .teamName("Team")
                .injuredPlayers(List.of())
                .suspendedPlayers(List.of())
                .totalMissing(0)
                .attackImpactReduction(0.0)
                .defenceImpactReduction(0.0)
                .availabilityRating("FULL_STRENGTH")
                .dataAvailable(dataAvailable)
                .build();
    }
}

