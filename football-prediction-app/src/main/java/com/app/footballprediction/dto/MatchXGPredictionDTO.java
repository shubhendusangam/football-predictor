package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Data Transfer Object for match xG predictions.
 * Provides expected goals predictions for an upcoming match between two teams.
 *
 * <p>This DTO is immutable and thread-safe.</p>
 *
 * <p><strong>Prediction Model:</strong></p>
 * <ul>
 *   <li>Uses historical shots on target averages from both teams</li>
 *   <li>Applies home advantage factor to expected goals</li>
 *   <li>Uses Poisson distribution for over/under goal probabilities</li>
 * </ul>
 *
 * <p><strong>Validation Rules:</strong></p>
 * <ul>
 *   <li>totalXG MUST equal homeXG + awayXG</li>
 *   <li>All probabilities must be between 0.0 and 1.0</li>
 *   <li>xG values should be in range 0.5 - 3.5 per team</li>
 * </ul>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@Value
@Builder
public class MatchXGPredictionDTO {

    /**
     * Name of the home team.
     */
    String homeTeam;

    /**
     * Name of the away team.
     */
    String awayTeam;

    /**
     * Expected goals for the home team.
     */
    double homeXG;

    /**
     * Expected goals for the away team.
     */
    double awayXG;

    /**
     * Total expected goals in the match (homeXG + awayXG).
     */
    double totalXG;

    /**
     * Summary prediction string.
     * e.g., "Expect Over 2.5 goals (totalXG: 3.2)"
     */
    String prediction;

    /**
     * Probability of over 1.5 goals in the match.
     * Range: 0.0 to 1.0
     */
    double probOver1_5;

    /**
     * Probability of over 2.5 goals in the match.
     * Range: 0.0 to 1.0
     */
    double probOver2_5;

    /**
     * Probability of over 3.5 goals in the match.
     * Range: 0.0 to 1.0
     */
    double probOver3_5;

    /**
     * Confidence level of the prediction based on sample sizes.
     * Range: 0.0 (low) to 1.0 (high)
     */
    double confidence;

    /**
     * Home team's average shots on target.
     */
    double homeShotsOnTarget;

    /**
     * Away team's average shots on target.
     */
    double awayShotsOnTarget;

    /**
     * Number of home team matches analyzed.
     */
    int homeMatchesAnalyzed;

    /**
     * Number of away team matches analyzed.
     */
    int awayMatchesAnalyzed;

    /**
     * Recommendation text for the match.
     * e.g., "High-scoring match expected (3.7 xG)" or "Low-scoring match expected (1.8 xG)"
     */
    String recommendation;
}

