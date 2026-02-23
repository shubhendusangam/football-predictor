package com.app.footballprediction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response DTO for league standings table.
 * Contains league information and sorted standings list.
 */
@Data
@Builder
public class LeagueStandingsResponse {

    /**
     * League name (e.g., "Premier League").
     */
    private String leagueName;

    /**
     * League code (e.g., "PL").
     */
    private String leagueCode;

    /**
     * Current season (e.g., "2025/26").
     */
    private String season;

    /**
     * Total number of teams in the league.
     */
    private int totalTeams;

    /**
     * List of team standings, sorted by position.
     */
    private List<StandingDto> standings;

    /**
     * Timestamp when standings were last updated.
     */
    private String lastUpdated;

    /**
     * Individual team standing DTO.
     */
    @Data
    @Builder
    public static class StandingDto {

        /**
         * Position in the league table (1-20).
         */
        private int position;

        /**
         * Team name.
         */
        private String teamName;

        /**
         * Team logo URL (optional).
         */
        private String teamLogo;

        /**
         * Number of matches played.
         */
        @JsonProperty("P")
        private int played;

        /**
         * Number of matches won.
         */
        @JsonProperty("W")
        private int won;

        /**
         * Number of matches drawn.
         */
        @JsonProperty("D")
        private int drawn;

        /**
         * Number of matches lost.
         */
        @JsonProperty("L")
        private int lost;

        /**
         * Goals scored (For).
         */
        @JsonProperty("GF")
        private int goalsFor;

        /**
         * Goals conceded (Against).
         */
        @JsonProperty("GA")
        private int goalsAgainst;

        /**
         * Goal difference (GF - GA).
         */
        @JsonProperty("GD")
        private int goalDifference;

        /**
         * Total points.
         */
        @JsonProperty("Pts")
        private int points;

        /**
         * Last 5 match results (e.g., "W W D L W").
         */
        private String form;

        /**
         * Position change from previous week.
         * Positive = moved up, Negative = moved down, 0 = unchanged.
         */
        private int positionChange;

        /**
         * Zone type for styling.
         * "champions" = Champions League (top 4)
         * "europa" = Europa League (5-6)
         * "conference" = Conference League (7)
         * "relegation" = Relegation zone (bottom 3)
         * "mid" = Mid-table
         */
        private String zone;
    }
}

