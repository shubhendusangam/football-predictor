package com.app.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing aggregated team statistics for a specific season.
 * Includes Elo rating, form tracking, and performance metrics.
 */
@Entity
@Table(name = "season_team_stats",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_season_team", columnNames = {"season_id", "team_id"})
        },
        indexes = {
                @Index(name = "idx_sts_season", columnList = "season_id"),
                @Index(name = "idx_sts_team", columnList = "team_id"),
                @Index(name = "idx_sts_elo", columnList = "elo_rating DESC")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeasonTeamStats {

    public static final double DEFAULT_ELO_RATING = 1500.0;
    public static final int ELO_K_FACTOR = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Version field for optimistic locking.
     * Prevents concurrent updates from corrupting data.
     */
    @Version
    private Long version;

    /**
     * Season identifier (e.g., "2025-26").
     */
    @Column(name = "season_id", nullable = false, length = 20)
    private String seasonId;

    /**
     * Team ID reference.
     */
    @Column(name = "team_id", nullable = false)
    private Long teamId;

    /**
     * Team name for quick access without join.
     */
    @Column(name = "team_name", nullable = false)
    private String teamName;

    /**
     * Number of matches played.
     */
    @Column(name = "matches_played", nullable = false)
    @Builder.Default
    private Integer matchesPlayed = 0;

    /**
     * Number of matches won.
     */
    @Column(name = "wins", nullable = false)
    @Builder.Default
    private Integer wins = 0;

    /**
     * Number of matches drawn.
     */
    @Column(name = "draws", nullable = false)
    @Builder.Default
    private Integer draws = 0;

    /**
     * Number of matches lost.
     */
    @Column(name = "losses", nullable = false)
    @Builder.Default
    private Integer losses = 0;

    /**
     * Total goals scored.
     */
    @Column(name = "goals_scored", nullable = false)
    @Builder.Default
    private Integer goalsScored = 0;

    /**
     * Total goals conceded.
     */
    @Column(name = "goals_conceded", nullable = false)
    @Builder.Default
    private Integer goalsConceded = 0;

    /**
     * Number of clean sheets (matches with zero goals conceded).
     */
    @Column(name = "clean_sheets", nullable = false)
    @Builder.Default
    private Integer cleanSheets = 0;

    /**
     * Current streak description.
     * Format: "W3" (3 wins), "D1" (1 draw), "L2" (2 losses), "U5" (5 unbeaten)
     */
    @Column(name = "current_streak", length = 10)
    @Builder.Default
    private String currentStreak = "N0";

    /**
     * Form points from last 5 matches (max 15).
     * Win = 3, Draw = 1, Loss = 0
     */
    @Column(name = "form_points_last5", nullable = false)
    @Builder.Default
    private Integer formPointsLast5 = 0;

    /**
     * Last 5 results as string (e.g., "WWDLW").
     * Most recent first.
     */
    @Column(name = "form_string", length = 5)
    @Builder.Default
    private String formString = "";

    /**
     * Current Elo rating.
     * Default: 1500 (average rating)
     */
    @Column(name = "elo_rating", nullable = false)
    @Builder.Default
    private Double eloRating = DEFAULT_ELO_RATING;

    /**
     * Timestamp of last update.
     */
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    /**
     * Calculate goal difference.
     */
    public int getGoalDifference() {
        return goalsScored - goalsConceded;
    }

    /**
     * Calculate total points (3 for win, 1 for draw).
     */
    public int getTotalPoints() {
        return (wins * 3) + draws;
    }

    /**
     * Calculate points per game average.
     */
    public double getPointsPerGame() {
        if (matchesPlayed == 0) return 0.0;
        return (double) getTotalPoints() / matchesPlayed;
    }

    /**
     * Calculate win percentage.
     */
    public double getWinPercentage() {
        if (matchesPlayed == 0) return 0.0;
        return (double) wins / matchesPlayed * 100;
    }

    /**
     * Calculate average goals scored per game.
     */
    public double getAvgGoalsScored() {
        if (matchesPlayed == 0) return 0.0;
        return (double) goalsScored / matchesPlayed;
    }

    /**
     * Calculate average goals conceded per game.
     */
    public double getAvgGoalsConceded() {
        if (matchesPlayed == 0) return 0.0;
        return (double) goalsConceded / matchesPlayed;
    }

    /**
     * Initialize default values for a new season team stats entry.
     */
    public static SeasonTeamStats createDefault(String seasonId, Long teamId, String teamName) {
        return SeasonTeamStats.builder()
                .seasonId(seasonId)
                .teamId(teamId)
                .teamName(teamName)
                .matchesPlayed(0)
                .wins(0)
                .draws(0)
                .losses(0)
                .goalsScored(0)
                .goalsConceded(0)
                .cleanSheets(0)
                .currentStreak("N0")
                .formPointsLast5(0)
                .formString("")
                .eloRating(DEFAULT_ELO_RATING)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }
}

