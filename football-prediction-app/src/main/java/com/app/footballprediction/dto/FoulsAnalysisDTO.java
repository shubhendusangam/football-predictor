package com.app.footballprediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for fouls and discipline analysis.
 * Provides comprehensive fouls statistics and discipline metrics for a team.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoulsAnalysisDTO {

    /**
     * Team name being analyzed.
     */
    private String teamName;

    /**
     * Whether this analysis is for home or away matches.
     */
    private boolean isHome;

    /**
     * Number of matches analyzed.
     */
    private int matchesAnalyzed;

    /**
     * Average fouls committed per match.
     */
    private double avgFoulsCommitted;

    /**
     * Average fouls drawn (opponent's fouls) per match.
     */
    private double avgFoulsDrawn;

    /**
     * Fouls differential (drawn - committed).
     * Positive = team draws more fouls than they commit (good discipline).
     * Negative = team commits more fouls than they draw (poor discipline).
     */
    private double foulsDifferential;

    /**
     * Discipline score from 0-10.
     * Higher score = better discipline (lower fouls committed).
     * Dynamically normalized based on league averages.
     */
    private double disciplineScore;

    /**
     * Rating text based on discipline score.
     * Excellent (8-10), Good (6-8), Average (4-6), Poor (0-4).
     */
    private String disciplineRating;

    /**
     * Win rate when team commits low fouls (<10).
     * As a percentage (0-100).
     */
    private double winRateWhenLowFouls;

    /**
     * Win rate when team commits high fouls (>15).
     * As a percentage (0-100).
     */
    private double winRateWhenHighFouls;

    /**
     * Win rate when team has controlled aggression (<12 fouls).
     * As a percentage (0-100).
     */
    private double winRateWhenControlled;

    /**
     * Total fouls committed across all analyzed matches.
     */
    private int totalFoulsCommitted;

    /**
     * Total fouls drawn across all analyzed matches.
     */
    private int totalFoulsDrawn;

    /**
     * Number of matches with low fouls (<10).
     */
    private int lowFoulsMatchCount;

    /**
     * Number of matches with high fouls (>15).
     */
    private int highFoulsMatchCount;

    /**
     * Number of matches with controlled aggression (<12 fouls).
     */
    private int controlledMatchCount;

    /**
     * Data scope description.
     */
    @Builder.Default
    private String dataScope = "Last 20 Matches";

    /**
     * Get discipline rating CSS class for frontend styling.
     */
    public String getDisciplineRatingClass() {
        if (disciplineScore >= 8) return "excellent";
        if (disciplineScore >= 6) return "good";
        if (disciplineScore >= 4) return "average";
        return "poor";
    }
}

