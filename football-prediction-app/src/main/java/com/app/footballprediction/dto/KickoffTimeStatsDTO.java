package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Value;

/**
 * DTO for performance statistics within a single kick-off time slot.
 *
 * <p>Contains win/draw/loss breakdown, goal averages, and performance
 * classification for matches played at a specific time of day.</p>
 *
 * <p>This DTO is immutable (using @Value) for thread safety.</p>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@Value
@Builder
public class KickoffTimeStatsDTO {

    /**
     * Time slot label (e.g., "Early (12:00–13:30)").
     */
    String timeSlot;

    /**
     * Time slot key for frontend use (e.g., "early", "afternoon", "late", "evening").
     */
    String slotKey;

    /**
     * Number of matches played in this time slot.
     */
    int matchesPlayed;

    /**
     * Number of wins in this time slot.
     */
    int wins;

    /**
     * Number of draws in this time slot.
     */
    int draws;

    /**
     * Number of losses in this time slot.
     */
    int losses;

    /**
     * Win percentage (0-100) for this time slot.
     */
    double winPercentage;

    /**
     * Average goals scored per match in this time slot.
     */
    double avgGoalsScored;

    /**
     * Average goals conceded per match in this time slot.
     */
    double avgGoalsConceded;

    /**
     * Performance classification based on win rate relative to overall.
     * Values: "Strong", "Average", "Weak"
     */
    String performance;

    /**
     * Time range description (e.g., "12:00 – 13:30").
     */
    String timeRange;
}

