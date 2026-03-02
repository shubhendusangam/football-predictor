package com.app.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a team's standing in a league for a specific season.
 * Stores position, points, goals, and form data for league table display.
 */
@Entity
@Table(name = "league_standings",
        indexes = {
                @Index(name = "idx_standings_league_season", columnList = "league_id, season"),
                @Index(name = "idx_standings_points_gd", columnList = "points DESC, goal_difference DESC")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeagueStanding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the league.
     */
    @Column(name = "league_id", nullable = false)
    private Long leagueId;

    /**
     * Season identifier in standard format (e.g., "2025-26").
     */
    @Column(name = "season", nullable = false, length = 20)
    private String season;

    /**
     * Team ID reference.
     */
    @Column(name = "team_id")
    private Long teamId;

    /**
     * Team name for quick access without join.
     */
    @Column(name = "team_name", nullable = false)
    private String teamName;

    /**
     * Current position in the league table.
     */
    @Column(name = "position")
    private Integer position;

    /**
     * Number of matches played.
     */
    @Column(name = "played")
    @Builder.Default
    private Integer played = 0;

    /**
     * Number of matches won.
     */
    @Column(name = "won")
    @Builder.Default
    private Integer won = 0;

    /**
     * Number of matches drawn.
     */
    @Column(name = "drawn")
    @Builder.Default
    private Integer drawn = 0;

    /**
     * Number of matches lost.
     */
    @Column(name = "lost")
    @Builder.Default
    private Integer lost = 0;

    /**
     * Total goals scored.
     */
    @Column(name = "goals_for")
    @Builder.Default
    private Integer goalsFor = 0;

    /**
     * Total goals conceded.
     */
    @Column(name = "goals_against")
    @Builder.Default
    private Integer goalsAgainst = 0;

    /**
     * Goal difference (goals_for - goals_against).
     */
    @Column(name = "goal_difference")
    @Builder.Default
    private Integer goalDifference = 0;

    /**
     * Total points earned (3 for win, 1 for draw).
     */
    @Column(name = "points")
    @Builder.Default
    private Integer points = 0;

    /**
     * Last 5 match results (e.g., "W W D L W").
     */
    @Column(name = "form", length = 20)
    private String form;

    /**
     * Position change compared to previous week (-1, 0, +1, etc.).
     */
    @Column(name = "position_change")
    @Builder.Default
    private Integer positionChange = 0;

    /**
     * Timestamp of last update.
     */
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    /**
     * Calculate and update goal difference.
     */
    public void calculateGoalDifference() {
        this.goalDifference = (this.goalsFor != null ? this.goalsFor : 0)
                            - (this.goalsAgainst != null ? this.goalsAgainst : 0);
    }

    /**
     * Calculate points from wins and draws.
     */
    public void calculatePoints() {
        this.points = (this.won != null ? this.won * 3 : 0)
                    + (this.drawn != null ? this.drawn : 0);
    }

    /**
     * Update statistics after a match result.
     */
    public void updateStats(int goalsScored, int goalsConceded, String result) {
        this.played = (this.played != null ? this.played : 0) + 1;
        this.goalsFor = (this.goalsFor != null ? this.goalsFor : 0) + goalsScored;
        this.goalsAgainst = (this.goalsAgainst != null ? this.goalsAgainst : 0) + goalsConceded;

        if ("W".equals(result)) {
            this.won = (this.won != null ? this.won : 0) + 1;
        } else if ("D".equals(result)) {
            this.drawn = (this.drawn != null ? this.drawn : 0) + 1;
        } else if ("L".equals(result)) {
            this.lost = (this.lost != null ? this.lost : 0) + 1;
        }

        calculateGoalDifference();
        calculatePoints();
        updateForm(result);
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Update form string with new result.
     * Keeps only last 5 results.
     */
    private void updateForm(String result) {
        if (this.form == null || this.form.isEmpty()) {
            this.form = result;
        } else {
            String[] results = this.form.split(" ");
            StringBuilder newForm = new StringBuilder();

            // Add new result at the start
            newForm.append(result);

            // Keep up to 4 previous results
            int count = 0;
            for (String r : results) {
                if (count >= 4) break;
                newForm.append(" ").append(r);
                count++;
            }

            this.form = newForm.toString();
        }
    }
}

