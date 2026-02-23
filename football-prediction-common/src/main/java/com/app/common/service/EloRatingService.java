package com.app.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for calculating Elo ratings.
 *
 * Elo Rating System:
 * - ExpectedScore = 1 / (1 + 10^((opponentElo - teamElo) / 400))
 * - NewRating = OldRating + K * (ActualScore - ExpectedScore)
 *
 * K-Factor: 20 (standard for established competitions)
 *
 * Actual Score:
 * - Win: 1.0
 * - Draw: 0.5
 * - Loss: 0.0
 */
@Service
@Slf4j
public class EloRatingService {

    /**
     * K-Factor determines how much ratings can change per match.
     * Higher K = more volatile ratings
     * Lower K = more stable ratings
     *
     * Common values:
     * - 10-16: Established players/teams
     * - 20-32: Standard competitions
     * - 40: New players/teams
     */
    public static final int K_FACTOR = 20;

    /**
     * The divisor used in expected score calculation.
     * Standard Elo uses 400.
     */
    public static final double ELO_DIVISOR = 400.0;

    /**
     * Default starting Elo rating.
     */
    public static final double DEFAULT_RATING = 1500.0;

    /**
     * Result constants for actual score calculation.
     */
    public static final double RESULT_WIN = 1.0;
    public static final double RESULT_DRAW = 0.5;
    public static final double RESULT_LOSS = 0.0;

    /**
     * Calculate the expected score for a team against an opponent.
     *
     * Formula: E = 1 / (1 + 10^((opponentElo - teamElo) / 400))
     *
     * @param teamElo The team's current Elo rating
     * @param opponentElo The opponent's current Elo rating
     * @return Expected score between 0 and 1
     */
    public double calculateExpectedScore(double teamElo, double opponentElo) {
        double exponent = (opponentElo - teamElo) / ELO_DIVISOR;
        return 1.0 / (1.0 + Math.pow(10, exponent));
    }

    /**
     * Calculate the new Elo rating after a match.
     *
     * Formula: R' = R + K * (S - E)
     * Where:
     * - R' = new rating
     * - R = old rating
     * - K = K-factor (20)
     * - S = actual score (1 for win, 0.5 for draw, 0 for loss)
     * - E = expected score
     *
     * @param currentRating The team's current Elo rating
     * @param expectedScore The expected score (from calculateExpectedScore)
     * @param actualScore The actual result (1.0 win, 0.5 draw, 0.0 loss)
     * @return The new Elo rating
     */
    public double calculateNewRating(double currentRating, double expectedScore, double actualScore) {
        return currentRating + K_FACTOR * (actualScore - expectedScore);
    }

    /**
     * Calculate new Elo rating directly from match result.
     * Convenience method combining expected score and new rating calculation.
     *
     * @param teamElo The team's current Elo rating
     * @param opponentElo The opponent's current Elo rating
     * @param actualScore The actual result (1.0 win, 0.5 draw, 0.0 loss)
     * @return The new Elo rating for the team
     */
    public double calculateNewRatingFromMatch(double teamElo, double opponentElo, double actualScore) {
        double expectedScore = calculateExpectedScore(teamElo, opponentElo);
        double newRating = calculateNewRating(teamElo, expectedScore, actualScore);

        log.debug("Elo calculation: teamElo={}, opponentElo={}, expected={}, actual={}, newRating={}",
                teamElo, opponentElo, expectedScore, actualScore, newRating);

        return newRating;
    }

    /**
     * Calculate new ratings for both teams after a match.
     * Returns an array: [newHomeRating, newAwayRating]
     *
     * @param homeElo Home team's current Elo rating
     * @param awayElo Away team's current Elo rating
     * @param homeGoals Goals scored by home team
     * @param awayGoals Goals scored by away team
     * @return Array containing [newHomeRating, newAwayRating]
     */
    public double[] calculateMatchRatings(double homeElo, double awayElo, int homeGoals, int awayGoals) {
        double homeActual;
        double awayActual;

        if (homeGoals > awayGoals) {
            homeActual = RESULT_WIN;
            awayActual = RESULT_LOSS;
        } else if (homeGoals < awayGoals) {
            homeActual = RESULT_LOSS;
            awayActual = RESULT_WIN;
        } else {
            homeActual = RESULT_DRAW;
            awayActual = RESULT_DRAW;
        }

        double homeExpected = calculateExpectedScore(homeElo, awayElo);
        double awayExpected = calculateExpectedScore(awayElo, homeElo);

        double newHomeRating = calculateNewRating(homeElo, homeExpected, homeActual);
        double newAwayRating = calculateNewRating(awayElo, awayExpected, awayActual);

        log.debug("Match Elo update: Home({}->{}}), Away({}->{})",
                homeElo, newHomeRating, awayElo, newAwayRating);

        return new double[]{newHomeRating, newAwayRating};
    }

    /**
     * Get the actual score value for a match result.
     *
     * @param goalsFor Goals scored by the team
     * @param goalsAgainst Goals scored by the opponent
     * @return 1.0 for win, 0.5 for draw, 0.0 for loss
     */
    public double getActualScore(int goalsFor, int goalsAgainst) {
        if (goalsFor > goalsAgainst) {
            return RESULT_WIN;
        } else if (goalsFor < goalsAgainst) {
            return RESULT_LOSS;
        } else {
            return RESULT_DRAW;
        }
    }

    /**
     * Calculate Elo rating change (delta) for a match.
     * Positive value = rating increase, negative = decrease.
     *
     * @param teamElo The team's current Elo rating
     * @param opponentElo The opponent's current Elo rating
     * @param actualScore The actual result (1.0 win, 0.5 draw, 0.0 loss)
     * @return The rating change (can be positive or negative)
     */
    public double calculateRatingChange(double teamElo, double opponentElo, double actualScore) {
        double expectedScore = calculateExpectedScore(teamElo, opponentElo);
        return K_FACTOR * (actualScore - expectedScore);
    }

    /**
     * Calculate win probability based on Elo ratings.
     * Note: This is a simplified calculation assuming no draws.
     *
     * @param teamElo The team's Elo rating
     * @param opponentElo The opponent's Elo rating
     * @return Probability of winning (0 to 1)
     */
    public double calculateWinProbability(double teamElo, double opponentElo) {
        return calculateExpectedScore(teamElo, opponentElo);
    }

    /**
     * Get rating tier/class based on Elo rating.
     *
     * Classification:
     * - elo < 1450  → "Weak"
     * - 1450–1600   → "Competitive"
     * - 1600–1750   → "Strong"
     * - 1750+       → "Elite"
     *
     * @param eloRating The Elo rating
     * @return Rating tier description
     */
    public String getRatingTier(double eloRating) {
        if (eloRating >= 1750) return "Elite";
        if (eloRating >= 1600) return "Strong";
        if (eloRating >= 1450) return "Competitive";
        return "Weak";
    }
}

