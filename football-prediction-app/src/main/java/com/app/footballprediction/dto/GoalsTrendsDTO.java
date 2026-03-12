package com.app.footballprediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for league-wide goals trends analysis across multiple seasons.
 * Contains per-season breakdowns and overall trend direction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalsTrendsDTO {

    /** Per-season goals statistics, sorted chronologically by season. */
    private List<SeasonGoalsStatsDTO> seasonStats;

    /**
     * Overall trend direction of goals per game across the requested seasons.
     * Values: "Increasing", "Decreasing", or "Stable".
     */
    private String trendDirection;

    /**
     * Average change in goals per game between consecutive seasons.
     * Positive = goals trending up, negative = trending down.
     */
    private double avgChange;

    /** Number of seasons analyzed. */
    private int seasonsAnalyzed;

    /** Timestamp when this analysis was generated (ISO date). */
    private String generatedAt;
}

