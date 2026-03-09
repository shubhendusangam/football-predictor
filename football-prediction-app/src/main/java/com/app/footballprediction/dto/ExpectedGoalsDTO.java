package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Data Transfer Object for Expected Goals (xG) statistics.
 * Provides xG metrics for a team based on shots on target and historical conversion rates.
 *
 * <p>This DTO is immutable and thread-safe.</p>
 *
 * <p><strong>Key Metrics:</strong></p>
 * <ul>
 *   <li><strong>avgShotsOnTarget</strong>: Average shots on target per match</li>
 *   <li><strong>expectedGoals</strong>: xG per game = avgShotsOnTarget × conversionRate</li>
 *   <li><strong>actualGoals</strong>: Actual goals scored per game</li>
 *   <li><strong>xGDifference</strong>: actual - xG (positive = overperforming)</li>
 *   <li><strong>conversionRate</strong>: Team-specific shot conversion rate</li>
 * </ul>
 *
 * <p><strong>xG Model:</strong></p>
 * <p>Uses a shots-on-target proxy model where xG = avgShotsOnTarget × leagueConversionRate.
 * Team-specific conversion rates are also calculated for comparison.</p>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@Value
@Builder
public class ExpectedGoalsDTO {

    /**
     * Name of the team.
     */
    String teamName;

    /**
     * Indicates if statistics are for home matches only.
     * True = home, False = away, null = all matches.
     */
    Boolean isHome;

    /**
     * Average shots on target per match.
     * Typical Premier League values: 3.0 - 6.0
     */
    double avgShotsOnTarget;

    /**
     * Expected goals per game (xG) based on shots on target × league conversion rate.
     * Typical values: 0.5 - 3.5
     */
    double expectedGoals;

    /**
     * Actual goals scored per game.
     */
    double actualGoals;

    /**
     * Difference between actual goals and xG (actualGoals - expectedGoals).
     * Positive = overperforming, Negative = underperforming.
     */
    double xGDifference;

    /**
     * Team-specific shot-to-goal conversion rate.
     * Range: 0.0 to 1.0 (typically 0.20 - 0.40 in Premier League)
     */
    double conversionRate;

    /**
     * League-wide average conversion rate used in xG calculation.
     */
    double leagueConversionRate;

    /**
     * Performance summary string.
     * e.g., "Overperforming +0.3" or "Underperforming -0.2"
     */
    String performance;

    /**
     * Number of matches analyzed to compute these statistics.
     */
    int matchesAnalyzed;

    /**
     * Total shots on target across all analyzed matches.
     */
    int totalShotsOnTarget;

    /**
     * Total goals scored across all analyzed matches.
     */
    int totalGoals;

    /**
     * Weighted average xG (recent matches weighted more).
     * Uses exponential decay weighting with more recent matches having higher weight.
     */
    double weightedXG;

    /**
     * Average shots conceded on target per match (defensive metric).
     */
    double avgShotsOnTargetAgainst;

    /**
     * Expected goals against per game based on opponent shots on target.
     */
    double expectedGoalsAgainst;
}

