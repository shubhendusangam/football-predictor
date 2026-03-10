package com.app.footballprediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a team's status in the Champions League race (Top 4 battle).
 * Contains position data, gaps to key positions, probability calculations, and status indicators.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Top4RaceDTO {

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
     * Points gap to 1st place (title race indicator).
     * Negative if team is in 1st place.
     */
    private int gapToFirst;

    /**
     * Points gap to 4th place (UCL qualification indicator).
     * Negative if team is above 4th, positive if below.
     */
    private int gapToFourth;

    /**
     * Points gap to 5th place (margin of safety indicator).
     * Positive means safe margin, negative means danger.
     */
    private int gapToFifth;

    /**
     * Number of remaining matches in the season.
     */
    private int remainingMatches;

    /**
     * Estimated points needed to secure top 4 (target: 70-75 points).
     */
    private int pointsNeeded;

    /**
     * Probability of finishing in top 4 (0-100%).
     * Based on current points, remaining matches, and historical win rate.
     */
    private double top4Probability;

    /**
     * Current status in the race.
     * Values: "Champion" | "UCL Safe" | "Fighting" | "Unlikely"
     */
    private String status;

    /**
     * Motivation level for upcoming matches.
     * Values: "High" | "Medium" | "Low"
     */
    private String motivation;

    /**
     * Historical win rate (percentage).
     */
    private double winRate;

    /**
     * Goal difference (for tiebreaker context).
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
}

