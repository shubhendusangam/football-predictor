package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO containing referee statistics aggregated from match history.
 * Used for referee bias analysis and feature engineering.
 */
@Data
@Builder
public class RefereeStats {

    /**
     * Referee name.
     */
    private String refereeName;

    /**
     * Total number of matches officiated.
     */
    private int matchesOfficiated;

    /**
     * Average yellow cards per match.
     */
    private double avgYellowCards;

    /**
     * Average red cards per match.
     */
    private double avgRedCards;

    /**
     * Average total fouls per match (if data available).
     */
    private double avgFouls;

    /**
     * Home win rate in matches officiated.
     * Useful for detecting home bias.
     */
    private double homeWinRate;

    /**
     * Draw rate in matches officiated.
     */
    private double drawRate;

    /**
     * Away win rate in matches officiated.
     */
    private double awayWinRate;

    /**
     * Average total goals per match.
     */
    private double avgGoalsPerMatch;

    /**
     * Average home goals per match.
     */
    private double avgHomeGoals;

    /**
     * Average away goals per match.
     */
    private double avgAwayGoals;

    /**
     * Strictness index (0.0 = lenient, 1.0 = strict).
     * Based on cards per match relative to league average.
     */
    private double strictnessIndex;

    /**
     * Data quality indicator (percentage of matches with complete data).
     */
    private double dataCompleteness;

    /**
     * Create empty stats for unknown referee.
     */
    public static RefereeStats empty(String refereeName) {
        return RefereeStats.builder()
                .refereeName(refereeName)
                .matchesOfficiated(0)
                .avgYellowCards(0.0)
                .avgRedCards(0.0)
                .avgFouls(0.0)
                .homeWinRate(0.462)  // League average
                .drawRate(0.268)     // League average
                .awayWinRate(0.270)  // League average
                .avgGoalsPerMatch(2.7)
                .avgHomeGoals(1.5)
                .avgAwayGoals(1.2)
                .strictnessIndex(0.5)
                .dataCompleteness(0.0)
                .build();
    }
}

