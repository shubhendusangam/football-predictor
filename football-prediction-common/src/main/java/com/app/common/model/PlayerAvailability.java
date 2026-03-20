package com.app.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Tracks player injury/suspension status for squad availability analysis.
 *
 * <p>Used by the prediction pipeline to compute squad strength features
 * (Phase 10) which adjust predictions based on key player absences.</p>
 */
@Entity
@Table(name = "player_availability", indexes = {
        @Index(name = "idx_pa_team_season_status", columnList = "team_name, season, status"),
        @Index(name = "idx_pa_team_name", columnList = "team_name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Team name (normalized, e.g. "Arsenal"). */
    @Column(name = "team_name", nullable = false)
    private String teamName;

    /** Player full name. */
    @Column(name = "player_name", nullable = false)
    private String playerName;

    /** Player position: GK, DEF, MID, FWD. */
    @Column(length = 10)
    private String position;

    /** Absence status. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AvailabilityStatus status;

    /** Reason for absence (e.g. "Knee ligament injury", "Red card suspension"). */
    private String reason;

    /** Expected return date (null = unknown). */
    private LocalDate expectedReturn;

    /**
     * Player importance rating (1–10).
     * <ul>
     *   <li>9–10: Star player (Haaland, Salah) — massive impact</li>
     *   <li>7–8: Key player (first-choice XI) — significant impact</li>
     *   <li>5–6: Regular starter — moderate impact</li>
     *   <li>3–4: Squad rotation player — minor impact</li>
     *   <li>1–2: Fringe/youth player — negligible impact</li>
     * </ul>
     */
    @Builder.Default
    @Column(name = "importance_rating")
    private int importanceRating = 5;

    /** Whether the player is a designated key star (top ~3 per squad). */
    @Builder.Default
    @Column(name = "is_key_star")
    private boolean keyStar = false;

    /** Average goals per game (career/season). */
    @Builder.Default
    @Column(name = "avg_goals_per_game")
    private double avgGoalsPerGame = 0.0;

    /** Average assists per game (career/season). */
    @Builder.Default
    @Column(name = "avg_assists_per_game")
    private double avgAssistsPerGame = 0.0;

    /** Number of suspension matches remaining (0 if not suspended). */
    @Builder.Default
    @Column(name = "suspension_matches_remaining")
    private int suspensionMatchesRemaining = 0;

    /** Date this record was last synced/updated. */
    @Column(name = "report_date")
    private LocalDate reportDate;

    /** Season this record belongs to (e.g. "2025-26"). */
    private String season;

    // ── Enum ─────────────────────────────────────────────────────────

    public enum AvailabilityStatus {
        /** Confirmed injured — will not play. */
        INJURED,
        /** Suspended (red card / accumulated yellows). */
        SUSPENDED,
        /** Fitness doubt — may or may not play. */
        DOUBTFUL,
        /** Available — included for tracking but currently fit. */
        AVAILABLE
    }
}

