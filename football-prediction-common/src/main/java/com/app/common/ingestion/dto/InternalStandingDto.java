package com.app.common.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Canonical standings representation independent of data source.
 * All providers map their standings data to this format.
 *
 * <p>This DTO provides a unified view of league table entries
 * regardless of the source API structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalStandingDto {

    /**
     * Team name.
     */
    private String teamName;

    /**
     * Competition code (e.g., "PL").
     */
    private String competition;

    /**
     * Season identifier (e.g., "2025-26").
     */
    private String season;

    /**
     * Current league position (1-based).
     */
    private int position;

    /**
     * Matches played.
     */
    private int played;

    /**
     * Matches won.
     */
    private int won;

    /**
     * Matches drawn.
     */
    private int drawn;

    /**
     * Matches lost.
     */
    private int lost;

    /**
     * Goals scored.
     */
    private int goalsFor;

    /**
     * Goals conceded.
     */
    private int goalsAgainst;

    /**
     * Goal difference (goalsFor - goalsAgainst).
     */
    private int goalDifference;

    /**
     * Total points.
     */
    private int points;

    /**
     * Recent form string (e.g., "WWLDW" for last 5 matches).
     * W = Win, L = Loss, D = Draw
     */
    private String form;

    /**
     * Team crest/logo URL.
     */
    private String teamCrest;

    /**
     * External team ID from provider.
     */
    private String externalTeamId;

    /**
     * Provider that supplied this data.
     */
    private String providerName;

    /**
     * Timestamp when this data was fetched.
     */
    private Instant fetchedAt;

    // ══════════════════════════════════════════════════════════════
    // Computed Properties
    // ══════════════════════════════════════════════════════════════

    /**
     * Calculate points per game.
     */
    public double getPointsPerGame() {
        return played > 0 ? (double) points / played : 0.0;
    }

    /**
     * Calculate win rate.
     */
    public double getWinRate() {
        return played > 0 ? (double) won / played : 0.0;
    }

    /**
     * Calculate goals scored per game.
     */
    public double getGoalsPerGame() {
        return played > 0 ? (double) goalsFor / played : 0.0;
    }

    /**
     * Calculate goals conceded per game.
     */
    public double getGoalsConcededPerGame() {
        return played > 0 ? (double) goalsAgainst / played : 0.0;
    }

    /**
     * Parse form string to calculate form points (last 5).
     * W = 3 points, D = 1 point, L = 0 points
     */
    public int getFormPoints() {
        if (form == null || form.isEmpty()) {
            return 0;
        }
        int points = 0;
        for (char c : form.toCharArray()) {
            points += switch (c) {
                case 'W' -> 3;
                case 'D' -> 1;
                default -> 0;
            };
        }
        return points;
    }
}

