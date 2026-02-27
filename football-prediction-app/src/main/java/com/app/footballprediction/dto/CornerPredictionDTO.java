package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Data Transfer Object for corner kick predictions.
 * Provides match-specific corner predictions with probability distributions.
 *
 * <p>This DTO is immutable and thread-safe.</p>
 *
 * <p><strong>Prediction Model:</strong></p>
 * <ul>
 *   <li>Uses historical corner averages from both teams</li>
 *   <li>Applies weighted recency factor for recent form</li>
 *   <li>Uses normal distribution approximation for over/under probabilities</li>
 * </ul>
 *
 * <p><strong>Validation Rules:</strong></p>
 * <ul>
 *   <li>expectedTotalCorners MUST equal expectedHomeCorners + expectedAwayCorners</li>
 *   <li>All probabilities must be between 0.0 and 1.0</li>
 *   <li>No NaN or negative values allowed</li>
 * </ul>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@Value
@Builder
public class CornerPredictionDTO {

    /**
     * Name of the home team.
     */
    String homeTeam;

    /**
     * Name of the away team.
     */
    String awayTeam;

    /**
     * Expected total corners in the match (home + away).
     * Typical Premier League average: 10.0 - 11.0
     */
    double expectedTotalCorners;

    /**
     * Expected corners for the home team.
     * Home teams typically win 5-6 corners on average.
     */
    double expectedHomeCorners;

    /**
     * Expected corners for the away team.
     * Away teams typically win 4-5 corners on average.
     */
    double expectedAwayCorners;

    /**
     * Probability that total corners will be over 9.5.
     * Range: 0.0 to 1.0
     */
    double probOver9_5;

    /**
     * Probability that total corners will be over 10.5.
     * Range: 0.0 to 1.0
     */
    double probOver10_5;

    /**
     * Probability that total corners will be over 11.5.
     * Range: 0.0 to 1.0
     */
    double probOver11_5;

    /**
     * Confidence level of the prediction based on sample size.
     * Range: 0.0 (low) to 1.0 (high)
     * Based on number of historical matches available for both teams.
     */
    double confidence;

    /**
     * Home team's weighted corner average used in prediction.
     */
    double homeWeightedCorners;

    /**
     * Away team's weighted corner average used in prediction.
     */
    double awayWeightedCorners;

    /**
     * Number of home team matches analyzed.
     */
    int homeMatchesAnalyzed;

    /**
     * Number of away team matches analyzed.
     */
    int awayMatchesAnalyzed;
}

