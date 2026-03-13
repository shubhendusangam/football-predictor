package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO representing a team's league position after a specific gameweek.
 */
@Data
@Builder
public class GameweekPositionDTO {

    /**
     * Gameweek number (1-38).
     */
    private int gameweek;

    /**
     * League position after this gameweek (1-20).
     */
    private int position;

    /**
     * Cumulative points total at this point.
     */
    private int points;

    /**
     * Date of the match played in this gameweek.
     */
    private String date;

    /**
     * Opponent faced in this gameweek.
     */
    private String opponent;

    /**
     * Result of the match: W, D, or L.
     */
    private String result;
}


