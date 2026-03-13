package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO representing a team's league position progression over a season.
 */
@Data
@Builder
public class PositionHistoryDTO {

    /**
     * The team name.
     */
    private String teamName;

    /**
     * Season string (e.g., "2025-26").
     */
    private String season;

    /**
     * Chronological list of gameweek positions.
     */
    private List<GameweekPositionDTO> progression;

    /**
     * Highest (best) league position achieved during the season (numerically lowest).
     */
    private int highestPosition;

    /**
     * Lowest (worst) league position during the season (numerically highest).
     */
    private int lowestPosition;

    /**
     * Current position (after the latest played gameweek).
     */
    private int currentPosition;

    /**
     * Total number of teams in the league (for chart Y-axis bounds).
     */
    private int totalTeams;
}

