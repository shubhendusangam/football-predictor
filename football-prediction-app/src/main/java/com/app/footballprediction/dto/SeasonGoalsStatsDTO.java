package com.app.footballprediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-season goals statistics for the league-wide goals trends feature.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeasonGoalsStatsDTO {

    /** Season identifier (e.g., "2025-26"). */
    private String season;

    /** Total goals scored across all matches in this season. */
    private int totalGoals;

    /** Total matches played (completed) in this season. */
    private int totalMatches;

    /** Average goals per game across the season. */
    private double avgGoalsPerGame;

    /** Average home goals per game. */
    private double homeGoalsAvg;

    /** Average away goals per game. */
    private double awayGoalsAvg;

    /** Percentage of matches with at least one clean sheet (home or away scored 0). */
    private double cleanSheetPercentage;

    /** Percentage of matches with more than 4 total goals. */
    private double highScoringPercentage;

    /** Percentage of matches with fewer than 2 total goals. */
    private double lowScoringPercentage;

    /** Percentage of matches with 2-3 total goals (medium scoring). */
    private double mediumScoringPercentage;
}

