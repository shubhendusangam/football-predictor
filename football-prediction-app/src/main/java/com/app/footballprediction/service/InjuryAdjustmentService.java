package com.app.footballprediction.service;

import com.app.common.dto.TeamAvailabilityDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Adjusts match probabilities based on injury/suspension data.
 * <p>
 * Algorithm:
 * - Home attack reduction lowers homeWin probability
 * - Away defence reduction slightly boosts homeWin (opposition benefit)
 * - Mirror logic for away team
 * - Draw absorbs the residual
 * - All values clamped to [0.05, 0.85] then renormalised to sum to 1.0
 */
@Service
@Slf4j
public class InjuryAdjustmentService {

    private static final double MIN_PROB = 0.05;
    private static final double MAX_PROB = 0.85;

    /**
     * Adjust probabilities based on injury availability data.
     *
     * @return double[3]: { adjustedHome, adjustedDraw, adjustedAway }
     */
    public double[] adjustProbabilities(double homeWinProb, double drawProb, double awayWinProb,
                                         TeamAvailabilityDTO homeAvail, TeamAvailabilityDTO awayAvail) {

        // If neither team has real injury data, return unchanged
        if (!homeAvail.isDataAvailable() && !awayAvail.isDataAvailable()) {
            return new double[]{homeWinProb, drawProb, awayWinProb};
        }

        double adjustedHome = homeWinProb
                * (1.0 - homeAvail.getAttackImpactReduction())
                * (1.0 + awayAvail.getDefenceImpactReduction() * 0.5);

        double adjustedAway = awayWinProb
                * (1.0 - awayAvail.getAttackImpactReduction())
                * (1.0 + homeAvail.getDefenceImpactReduction() * 0.5);

        double adjustedDraw = 1.0 - adjustedHome - adjustedAway;

        // Constrained normalisation: ensure all values are in [MIN_PROB, MAX_PROB]
        // AND sum to exactly 1.0.
        //
        // Algorithm: repeatedly fix boundary-pinned values and redistribute
        // the remainder among free values.  Converges in ≤ 3 passes.
        double[] probs = {adjustedHome, adjustedDraw, adjustedAway};
        normaliseConstrained(probs);
        adjustedHome = probs[0];
        adjustedDraw = probs[1];
        adjustedAway = probs[2];

        log.debug("Injury adjustment: H {}→{}  D {}→{}  A {}→{}",
                homeWinProb, adjustedHome, drawProb, adjustedDraw, awayWinProb, adjustedAway);

        return new double[]{adjustedHome, adjustedDraw, adjustedAway};
    }

    /**
     * Build a human-readable note describing the injury-based adjustment.
     */
    public String buildAdjustmentNote(TeamAvailabilityDTO homeAvail, TeamAvailabilityDTO awayAvail) {
        boolean homeWeakened = homeAvail.isDataAvailable() && homeAvail.getTotalMissing() > 0;
        boolean awayWeakened = awayAvail.isDataAvailable() && awayAvail.getTotalMissing() > 0;

        if (!homeAvail.isDataAvailable() && !awayAvail.isDataAvailable()) {
            return "Base prediction — injury data unavailable";
        }

        boolean homeSevere = "SEVERELY_WEAKENED".equals(homeAvail.getAvailabilityRating());
        boolean awaySevere = "SEVERELY_WEAKENED".equals(awayAvail.getAvailabilityRating());

        if (homeSevere && awaySevere) {
            return "⚠️ Both teams severely weakened — prediction confidence reduced";
        }
        if (homeSevere) {
            return "⚠️ " + homeAvail.getTeamName() + " severely weakened — prediction confidence reduced";
        }
        if (awaySevere) {
            return "⚠️ " + awayAvail.getTeamName() + " severely weakened — prediction confidence reduced";
        }
        if (homeWeakened && awayWeakened) {
            return "Both teams with absences — draw probability raised";
        }
        if (homeWeakened) {
            return "Home team weakened: " + homeAvail.getImpactSummary();
        }
        if (awayWeakened) {
            return "Away team weakened: " + awayAvail.getImpactSummary();
        }

        return "No significant absences — base prediction unchanged";
    }

    private double clamp(double value) {
        return Math.min(MAX_PROB, Math.max(MIN_PROB, value));
    }

    /**
     * Normalise an array of 3 probabilities so they sum to 1.0
     * while keeping every value in [MIN_PROB, MAX_PROB].
     * <p>
     * Algorithm: pin boundary values, distribute residual among free values.
     * Guaranteed to converge in at most 3 passes for 3 outcomes.
     */
    private void normaliseConstrained(double[] p) {
        for (int iter = 0; iter < 10; iter++) {
            // 1. Identify pinned (at boundary) and free indices
            boolean[] pinned = new boolean[3];
            double pinnedSum = 0;
            double freeSum = 0;
            int freeCount = 0;

            for (int i = 0; i < 3; i++) {
                if (p[i] <= MIN_PROB) {
                    p[i] = MIN_PROB;
                    pinned[i] = true;
                    pinnedSum += MIN_PROB;
                } else if (p[i] >= MAX_PROB) {
                    p[i] = MAX_PROB;
                    pinned[i] = true;
                    pinnedSum += MAX_PROB;
                } else {
                    freeSum += p[i];
                    freeCount++;
                }
            }

            double target = 1.0 - pinnedSum;

            if (freeCount == 0) {
                // All pinned — shouldn't happen with sensible bounds, but handle gracefully
                double sum = p[0] + p[1] + p[2];
                if (sum > 0) {
                    p[0] /= sum;
                    p[1] /= sum;
                    p[2] /= sum;
                }
                return;
            }

            if (target <= 0) {
                // Pinned values already exceed 1.0, distribute MIN_PROB to free slots
                for (int i = 0; i < 3; i++) {
                    if (!pinned[i]) p[i] = MIN_PROB;
                }
                double sum = p[0] + p[1] + p[2];
                if (sum > 0) {
                    p[0] /= sum;
                    p[1] /= sum;
                    p[2] /= sum;
                }
                return;
            }

            // 2. Scale free values to fill the remaining target
            double scale = (freeSum > 0) ? target / freeSum : 0;
            boolean allInBounds = true;
            for (int i = 0; i < 3; i++) {
                if (!pinned[i]) {
                    p[i] *= scale;
                    if (p[i] < MIN_PROB || p[i] > MAX_PROB) {
                        allInBounds = false;
                    }
                }
            }

            if (allInBounds) return; // converged
        }
    }
}


