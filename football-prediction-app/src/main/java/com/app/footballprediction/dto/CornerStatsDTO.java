package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Data Transfer Object for corner kick statistics.
 * Provides comprehensive corner statistics for a team.
 *
 * <p>This DTO is immutable and thread-safe.</p>
 *
 * <p><strong>Key Metrics:</strong></p>
 * <ul>
 *   <li><strong>avgCornersWon</strong>: Average corners won per match</li>
 *   <li><strong>avgCornersAgainst</strong>: Average corners conceded per match</li>
 *   <li><strong>cornerDominance</strong>: Ratio of corners won to total corners (0 to 1)</li>
 *   <li><strong>successRate</strong>: Win rate when having more corners than opponent (0 to 1)</li>
 * </ul>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@Value
@Builder
public class CornerStatsDTO {

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
     * Average corners won per match.
     * Typical Premier League values: 4.0 - 8.0
     */
    double avgCornersWon;

    /**
     * Average corners conceded per match.
     * Typical Premier League values: 3.5 - 6.5
     */
    double avgCornersAgainst;

    /**
     * Corner dominance ratio: cornersWon / (cornersWon + cornersAgainst).
     * Range: 0.0 to 1.0
     * Values > 0.55 indicate strong corner dominance.
     * Values < 0.45 indicate weak corner dominance.
     */
    double cornerDominance;

    /**
     * Win rate when team has more corners than opponent.
     * Range: 0.0 to 1.0
     * Measures correlation between corner superiority and match wins.
     */
    double successRate;

    /**
     * Number of matches analyzed to compute these statistics.
     * Used to indicate confidence level in the statistics.
     */
    int matchesAnalyzed;

    /**
     * Total corners won across all analyzed matches.
     */
    int totalCornersWon;

    /**
     * Total corners conceded across all analyzed matches.
     */
    int totalCornersAgainst;

    /**
     * Weighted average corners (recent matches weighted more).
     * Uses exponential decay weighting with more recent matches having higher weight.
     */
    double weightedAvgCorners;
}

