package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO for the full form guide of a team.
 * Contains recent match results, trend analysis, and form rating.
 */
@Data
@Builder
public class FormGuideDTO {

    /**
     * Team name (database short format).
     */
    private String teamName;

    /**
     * Recent matches ordered newest-first, limited to requested count.
     */
    private List<FormMatchDTO> recentMatches;

    /**
     * Total points earned in the last 5 matches (max 15).
     */
    private int pointsInLast5;

    /**
     * Total points earned in the previous 5 matches (matches 6-10, max 15).
     */
    private int pointsInPrevious5;

    /**
     * Form trend based on comparing last 5 vs previous 5 points.
     * Values: "Improving", "Declining", "Stable".
     */
    private String formTrend;

    /**
     * Form rating on a 0-10 scale based on points in the requested window.
     * 15/15 points → 10.0, 0/15 points → 0.0.
     */
    private double formRating;

    /**
     * Human-readable form string for the requested window.
     * Example: "W-W-D-L-W"
     */
    private String formString;

    /**
     * Season the form guide is scoped to.
     */
    private String season;

    /**
     * Total matches available for this team in the current season.
     */
    private int totalMatchesInSeason;
}

