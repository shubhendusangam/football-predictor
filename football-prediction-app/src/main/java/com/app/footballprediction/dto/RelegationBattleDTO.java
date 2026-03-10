package com.app.footballprediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a team's status in the relegation battle.
 * Contains position data, survival probabilities, and status indicators.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelegationBattleDTO {

    /**
     * Team name.
     */
    private String teamName;

    /**
     * Team logo URL.
     */
    private String teamLogo;

    /**
     * Current position in the league table.
     */
    private int currentPosition;

    /**
     * Current points total.
     */
    private int points;

    /**
     * Points gap to safety (17th position).
     * Positive = above safety line, Negative = in relegation zone.
     */
    private int gapToSafety;

    /**
     * Points gap to 18th position (first relegation spot).
     * Positive = above relegation zone.
     */
    private int gapToRelegation;

    /**
     * Number of remaining matches in the season.
     */
    private int remainingMatches;

    /**
     * Estimated points needed to reach safety (35-40 points target).
     */
    private int pointsNeededForSafety;

    /**
     * Probability of survival (0-100%).
     * Based on current points, remaining matches, and historical data.
     */
    private double survivalProbability;

    /**
     * Current status in the relegation battle.
     * Values: "Safe" | "Fighting" | "Danger" | "Relegated"
     */
    private String status;

    /**
     * Desperation level for the team.
     * Values: "Low" | "Medium" | "High" | "Extreme"
     */
    private String desperationLevel;

    /**
     * Historical win rate (percentage).
     */
    private double winRate;

    /**
     * Goal difference (important for tiebreakers).
     */
    private int goalDifference;

    /**
     * Recent form (last 5 matches).
     */
    private String form;

    /**
     * Points per game average.
     */
    private double pointsPerGame;

    /**
     * Matches played so far.
     */
    private int played;
}

