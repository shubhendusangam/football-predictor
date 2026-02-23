package com.app.footballprediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for season team statistics.
 * Contains comprehensive stats including Elo rating, form, and performance metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeasonTeamStatsResponse {

    private Long id;
    private String seasonId;
    private Long teamId;
    private String teamName;

    // ═══════════════════════════════════════════════════════════════════
    // Match Statistics
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Number of matches played.
     */
    private Integer matchesPlayed;

    /**
     * Number of matches won.
     */
    private Integer wins;

    /**
     * Number of matches drawn.
     */
    private Integer draws;

    /**
     * Number of matches lost.
     */
    private Integer losses;

    // ═══════════════════════════════════════════════════════════════════
    // Goals Statistics
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Total goals scored.
     */
    private Integer goalsScored;

    /**
     * Total goals conceded.
     */
    private Integer goalsConceded;

    /**
     * Goal difference (scored - conceded).
     */
    private Integer goalDifference;

    /**
     * Number of clean sheets.
     */
    private Integer cleanSheets;

    // ═══════════════════════════════════════════════════════════════════
    // Points Statistics
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Total points (3 per win, 1 per draw).
     */
    private Integer totalPoints;

    /**
     * Average points per game.
     */
    private Double pointsPerGame;

    // ═══════════════════════════════════════════════════════════════════
    // Form and Streak
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Current streak (e.g., "W3", "D1", "L2").
     */
    private String currentStreak;

    /**
     * Form points from last 5 matches (max 15).
     */
    private Integer formPointsLast5;

    /**
     * Last 5 results as string (e.g., "WWDLW").
     */
    private String formString;

    // ═══════════════════════════════════════════════════════════════════
    // Elo Rating
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Current Elo rating.
     */
    private Double eloRating;

    // ═══════════════════════════════════════════════════════════════════
    // Calculated Metrics
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Win percentage.
     */
    private Double winPercentage;

    /**
     * Average goals scored per game.
     */
    private Double avgGoalsScored;

    /**
     * Average goals conceded per game.
     */
    private Double avgGoalsConceded;

    /**
     * Last update timestamp.
     */
    private LocalDateTime lastUpdated;

    /**
     * Get Elo rating tier/class description.
     *
     * Classification:
     * - elo < 1450  → "Weak"
     * - 1450–1600   → "Competitive"
     * - 1600–1750   → "Strong"
     * - 1750+       → "Elite"
     */
    public String getEloTier() {
        if (eloRating == null) return "Unknown";
        if (eloRating >= 1750) return "Elite";
        if (eloRating >= 1600) return "Strong";
        if (eloRating >= 1450) return "Competitive";
        return "Weak";
    }

    /**
     * Get form description based on last 5 form points.
     */
    public String getFormDescription() {
        if (formPointsLast5 == null) return "Unknown";
        if (formPointsLast5 >= 13) return "Excellent";
        if (formPointsLast5 >= 10) return "Good";
        if (formPointsLast5 >= 7) return "Average";
        if (formPointsLast5 >= 4) return "Poor";
        return "Very Poor";
    }
}

