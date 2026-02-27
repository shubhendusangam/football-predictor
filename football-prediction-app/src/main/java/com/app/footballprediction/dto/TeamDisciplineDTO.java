package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * DTO containing team discipline statistics.
 * Tracks yellow and red card patterns for a team.
 *
 * <p>This DTO is immutable (using @Value) for thread safety.</p>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@Value
@Builder
public class TeamDisciplineDTO {

    /**
     * Team name.
     */
    String teamName;

    /**
     * Average yellow cards per home match.
     */
    double avgYellowCardsHome;

    /**
     * Average yellow cards per away match.
     */
    double avgYellowCardsAway;

    /**
     * Average yellow cards per match (overall).
     */
    double avgYellowCardsOverall;

    /**
     * Average red cards per match.
     */
    double avgRedCards;

    /**
     * Total yellow cards received this season.
     */
    int totalYellowCardsSeason;

    /**
     * Total red cards received this season.
     */
    int totalRedCardsSeason;

    /**
     * Total matches analyzed.
     */
    int matchesAnalyzed;

    /**
     * Discipline rating based on card frequency.
     * Values: "Excellent", "Average", "Aggressive"
     */
    String disciplineRating;

    /**
     * Rating color for UI display.
     * Values: "green", "yellow", "red"
     */
    String ratingColor;

    /**
     * Recent match bookings (last 5 matches).
     */
    List<MatchBookingSummary> recentBookings;

    /**
     * Average cards received by opponents.
     */
    double avgOpponentYellowCards;

    /**
     * Card differential (team cards - opponent cards).
     * Negative means team is more disciplined.
     */
    double cardDifferential;

    /**
     * Nested class for match booking summary.
     */
    @Value
    @Builder
    public static class MatchBookingSummary {
        /**
         * Match date string.
         */
        String matchDate;

        /**
         * Opponent team name.
         */
        String opponent;

        /**
         * Whether team played at home.
         */
        boolean isHome;

        /**
         * Yellow cards received in this match.
         */
        int yellowCards;

        /**
         * Red cards received in this match.
         */
        int redCards;

        /**
         * Match result (W/D/L).
         */
        String result;
    }

    /**
     * Create empty discipline stats for unknown team.
     */
    public static TeamDisciplineDTO empty(String teamName) {
        return TeamDisciplineDTO.builder()
                .teamName(teamName)
                .avgYellowCardsHome(0.0)
                .avgYellowCardsAway(0.0)
                .avgYellowCardsOverall(0.0)
                .avgRedCards(0.0)
                .totalYellowCardsSeason(0)
                .totalRedCardsSeason(0)
                .matchesAnalyzed(0)
                .disciplineRating("Unknown")
                .ratingColor("gray")
                .recentBookings(List.of())
                .avgOpponentYellowCards(0.0)
                .cardDifferential(0.0)
                .build();
    }
}

