package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * DTO representing a single match in a team's form guide.
 * Contains match result details from the team's perspective.
 */
@Data
@Builder
public class FormMatchDTO {

    /**
     * Date the match was played.
     */
    private LocalDate matchDate;

    /**
     * Opponent team name.
     */
    private String opponent;

    /**
     * Venue from the team's perspective: "H" (home) or "A" (away).
     */
    private String venue;

    /**
     * Goals scored by the team.
     */
    private int goalsFor;

    /**
     * Goals conceded by the team.
     */
    private int goalsAgainst;

    /**
     * Match result from the team's perspective: "W", "D", or "L".
     */
    private String result;

    /**
     * Points earned: 3 (win), 1 (draw), 0 (loss).
     */
    private int points;
}

