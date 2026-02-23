package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response DTO for team form insights.
 * Used for displaying team form statistics in the prediction view.
 */
@Data
@Builder
public class TeamFormResponse {

    /**
     * Average goals scored in last 5 matches
     */
    private double last5GoalsAvg;

    /**
     * Average goals conceded in last 5 matches
     */
    private double last5ConcededAvg;

    /**
     * Clean sheet rate as decimal (0.0 - 1.0)
     * Percentage of matches where team conceded 0 goals
     */
    private double cleanSheetRate;


    /**
     * Shot conversion rate as decimal
     * Goals scored / shots on target
     */
    private double shotConversion;

    /**
     * Form trend indicator: "up", "down", or "stable"
     */
    private String formTrend;

    /**
     * Recent form string (e.g., "WWDLW")
     */
    private String recentForm;

    /**
     * Goals scored in last 5 matches for sparkline chart
     */
    private List<Integer> goalsTimeline;

    /**
     * Goals conceded in last 5 matches for sparkline chart
     */
    private List<Integer> concededTimeline;

    /**
     * Team name
     */
    private String teamName;
}

