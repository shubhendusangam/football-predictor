package com.app.footballprediction.service;

import com.app.common.model.PlayerAvailability;
import com.app.common.model.PlayerAvailability.AvailabilityStatus;
import com.app.common.repository.PlayerAvailabilityRepository;
import com.app.footballprediction.dto.PlayerAvailabilityDTO;
import com.app.footballprediction.dto.PlayerAvailabilityDTO.AbsentPlayerDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Computes the impact of player absences on team squad strength.
 *
 * <p>Squad strength is a 0.0–1.0 value where 1.0 means full strength (no key
 * absentees) and lower values indicate significant absences. The calculation
 * weights each absent player by their importance rating and status certainty.</p>
 *
 * <p><b>Impact Weights:</b></p>
 * <ul>
 *   <li>INJURED → 1.0 (definite miss)</li>
 *   <li>SUSPENDED → 1.0 (definite miss)</li>
 *   <li>DOUBTFUL → 0.5 (50/50 chance of playing)</li>
 * </ul>
 *
 * <p>Attack and defence impacts are computed by weighting absent players
 * by their goals/assists contribution and position respectively.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerImpactService {

    private final PlayerAvailabilityRepository playerAvailabilityRepository;

    /**
     * Maximum possible penalty: 3 key stars × importance 10 × weight 1.0 = 30.
     * This is used to normalize the penalty to 0.0–1.0 range.
     */
    private static final double MAX_PENALTY = 30.0;

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Calculate squad strength for a team (0.0–1.0, 1.0 = full strength).
     *
     * @param teamName normalized team name
     * @return squad strength factor
     */
    public double calculateSquadStrength(String teamName) {
        return calculateSquadStrength(teamName, LocalDate.now());
    }

    /**
     * Calculate squad strength as of a specific date.
     */
    public double calculateSquadStrength(String teamName, LocalDate asOfDate) {
        List<PlayerAvailability> absences = playerAvailabilityRepository
                .findActiveAbsences(teamName, asOfDate);

        if (absences.isEmpty()) {
            return 1.0;
        }

        double totalPenalty = 0.0;
        for (PlayerAvailability p : absences) {
            double weight = statusWeight(p.getStatus());
            totalPenalty += p.getImportanceRating() * weight;
        }

        // Normalize penalty to 0.0–1.0 range, then invert (higher = better)
        double normalizedPenalty = Math.min(totalPenalty / MAX_PENALTY, 1.0);
        double squadStrength = 1.0 - normalizedPenalty;

        // Floor at 0.3 — even with catastrophic injuries a PL squad has depth
        return Math.max(0.3, squadStrength);
    }

    /**
     * Calculate attacking impact of absences (0.0–1.0, higher = worse).
     * Weighted by goals/assists contribution of missing players.
     */
    public double calculateAttackImpact(String teamName) {
        return calculateAttackImpact(teamName, LocalDate.now());
    }

    public double calculateAttackImpact(String teamName, LocalDate asOfDate) {
        List<PlayerAvailability> absences = playerAvailabilityRepository
                .findActiveAbsences(teamName, asOfDate);

        if (absences.isEmpty()) return 0.0;

        double totalImpact = 0.0;
        for (PlayerAvailability p : absences) {
            double weight = statusWeight(p.getStatus());
            // Attack impact = (goals + assists) contribution weighted by importance
            double attackContribution = (p.getAvgGoalsPerGame() * 2.0 + p.getAvgAssistsPerGame()) / 3.0;
            totalImpact += attackContribution * weight * (p.getImportanceRating() / 10.0);
        }

        return Math.min(1.0, totalImpact);
    }

    /**
     * Calculate defensive impact of absences (0.0–1.0, higher = worse).
     * Defensive positions (GK, DEF, defensive MID) weighted more heavily.
     */
    public double calculateDefenceImpact(String teamName) {
        return calculateDefenceImpact(teamName, LocalDate.now());
    }

    public double calculateDefenceImpact(String teamName, LocalDate asOfDate) {
        List<PlayerAvailability> absences = playerAvailabilityRepository
                .findActiveAbsences(teamName, asOfDate);

        if (absences.isEmpty()) return 0.0;

        double totalImpact = 0.0;
        for (PlayerAvailability p : absences) {
            double weight = statusWeight(p.getStatus());
            double defWeight = defencePositionWeight(p.getPosition());
            totalImpact += defWeight * weight * (p.getImportanceRating() / 10.0);
        }

        return Math.min(1.0, totalImpact);
    }

    /**
     * Build a full availability DTO for a team (for API response).
     */
    public PlayerAvailabilityDTO getTeamAvailability(String teamName) {
        return getTeamAvailability(teamName, LocalDate.now());
    }

    public PlayerAvailabilityDTO getTeamAvailability(String teamName, LocalDate asOfDate) {
        // Fetch absences once — reuse for all calculations to avoid redundant DB queries
        List<PlayerAvailability> absences = playerAvailabilityRepository
                .findActiveAbsences(teamName, asOfDate);

        double squadStrength = calculateSquadStrengthFromList(absences);
        double attackImpact = calculateAttackImpactFromList(absences);
        double defenceImpact = calculateDefenceImpactFromList(absences);

        List<AbsentPlayerDTO> absentList = absences.stream()
                .map(p -> AbsentPlayerDTO.builder()
                        .playerName(p.getPlayerName())
                        .position(p.getPosition())
                        .status(p.getStatus().name())
                        .reason(p.getReason())
                        .expectedReturn(p.getExpectedReturn() != null
                                ? p.getExpectedReturn().toString() : null)
                        .importanceRating(p.getImportanceRating())
                        .keyStar(p.isKeyStar())
                        .build())
                .collect(Collectors.toList());

        String rating = deriveRating(squadStrength);
        String note = buildAvailabilityNote(teamName, absences);

        return PlayerAvailabilityDTO.builder()
                .teamName(teamName)
                .squadStrength(round(squadStrength))
                .attackImpact(round(attackImpact))
                .defenceImpact(round(defenceImpact))
                .availabilityRating(rating)
                .absentPlayers(absentList)
                .availabilityNote(note)
                .build();
    }

    /**
     * Adjust raw prediction probabilities based on squad strength asymmetry.
     *
     * <p>If one team is significantly weakened, this shifts probabilities
     * toward the opponent. The maximum adjustment is ±8% per outcome.</p>
     *
     * @param probs       raw [homeWin, draw, awayWin] probabilities
     * @param homeStrength home squad strength (0.0–1.0)
     * @param awayStrength away squad strength (0.0–1.0)
     * @return adjusted probabilities (normalized to sum to 1.0)
     */
    public double[] adjustPredictionProbabilities(double[] probs,
                                                   double homeStrength,
                                                   double awayStrength) {
        if (probs == null || probs.length != 3) return probs;

        // Strength difference: positive = home advantage
        double diff = homeStrength - awayStrength;

        // Scale factor: max ±8% shift
        double maxShift = 0.08;
        double shift = diff * maxShift;

        double adjHome = probs[0] + shift;
        double adjDraw = probs[1] - Math.abs(shift) * 0.3; // draws slightly less likely
        double adjAway = probs[2] - shift;

        // Clamp to [0.01, 0.99]
        adjHome = Math.max(0.01, Math.min(0.99, adjHome));
        adjDraw = Math.max(0.01, Math.min(0.99, adjDraw));
        adjAway = Math.max(0.01, Math.min(0.99, adjAway));

        // Renormalize
        double sum = adjHome + adjDraw + adjAway;
        return new double[]{adjHome / sum, adjDraw / sum, adjAway / sum};
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════

    /** Squad strength from a pre-fetched absence list (avoids extra DB call). */
    private double calculateSquadStrengthFromList(List<PlayerAvailability> absences) {
        if (absences.isEmpty()) return 1.0;

        double totalPenalty = 0.0;
        for (PlayerAvailability p : absences) {
            double weight = statusWeight(p.getStatus());
            totalPenalty += p.getImportanceRating() * weight;
        }

        double normalizedPenalty = Math.min(totalPenalty / MAX_PENALTY, 1.0);
        return Math.max(0.3, 1.0 - normalizedPenalty);
    }

    /** Attack impact from a pre-fetched absence list. */
    private double calculateAttackImpactFromList(List<PlayerAvailability> absences) {
        if (absences.isEmpty()) return 0.0;

        double totalImpact = 0.0;
        for (PlayerAvailability p : absences) {
            double weight = statusWeight(p.getStatus());
            double attackContribution = (p.getAvgGoalsPerGame() * 2.0 + p.getAvgAssistsPerGame()) / 3.0;
            totalImpact += attackContribution * weight * (p.getImportanceRating() / 10.0);
        }
        return Math.min(1.0, totalImpact);
    }

    /** Defence impact from a pre-fetched absence list. */
    private double calculateDefenceImpactFromList(List<PlayerAvailability> absences) {
        if (absences.isEmpty()) return 0.0;

        double totalImpact = 0.0;
        for (PlayerAvailability p : absences) {
            double weight = statusWeight(p.getStatus());
            double defWeight = defencePositionWeight(p.getPosition());
            totalImpact += defWeight * weight * (p.getImportanceRating() / 10.0);
        }
        return Math.min(1.0, totalImpact);
    }

    /** Weight applied to each status type. */
    static double statusWeight(AvailabilityStatus status) {
        return switch (status) {
            case INJURED -> 1.0;
            case SUSPENDED -> 1.0;
            case DOUBTFUL -> 0.5;
            case AVAILABLE -> 0.0;
        };
    }

    /** Weight for defensive positions (GK/DEF weighted highest). */
    static double defencePositionWeight(String position) {
        if (position == null) return 0.3;
        return switch (position.toUpperCase()) {
            case "GK" -> 0.9;
            case "DEF" -> 0.8;
            case "MID" -> 0.4;
            case "FWD" -> 0.15;
            default -> 0.3;
        };
    }

    /** Derive human-readable rating from squad strength. */
    static String deriveRating(double squadStrength) {
        if (squadStrength >= 0.95) return "FULL_STRENGTH";
        if (squadStrength >= 0.80) return "MINOR_CONCERNS";
        if (squadStrength >= 0.60) return "WEAKENED";
        return "SEVERELY_WEAKENED";
    }

    /** Build a readable note from absences. */
    private String buildAvailabilityNote(String teamName, List<PlayerAvailability> absences) {
        if (absences.isEmpty()) {
            return teamName + " at full strength";
        }

        // Focus on key stars and high-importance players
        List<String> keyAbsences = new ArrayList<>();
        for (PlayerAvailability p : absences) {
            if (p.getImportanceRating() >= 7 || p.isKeyStar()) {
                String statusLabel = switch (p.getStatus()) {
                    case INJURED -> "injury";
                    case SUSPENDED -> "suspension";
                    case DOUBTFUL -> "doubt";
                    default -> "unavailable";
                };
                keyAbsences.add(p.getPlayerName() + " (" + statusLabel + ")");
            }
        }

        if (keyAbsences.isEmpty()) {
            return teamName + " missing " + absences.size() + " squad player(s)";
        }

        return teamName + " missing " + String.join(", ", keyAbsences);
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}

