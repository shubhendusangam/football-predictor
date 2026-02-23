package com.app.footballprediction.dto.dashboard;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for upcoming matches dashboard section.
 * Groups matches by match day with predict button support.
 */
@Data
@Builder
public class UpcomingMatchesResponse {

    /**
     * Match day header (e.g., "Matchday 25").
     */
    private String matchDayHeader;

    /**
     * Match day number.
     */
    private Integer matchDay;

    /**
     * Competition name.
     */
    private String competition;

    /**
     * List of upcoming matches.
     */
    private List<UpcomingMatchDto> matches;

    /**
     * Total count of matches.
     */
    private int totalMatches;

    /**
     * Timestamp when data was fetched.
     */
    private String lastUpdated;

    /**
     * Individual match DTO.
     */
    @Data
    @Builder
    public static class UpcomingMatchDto {

        /**
         * Match ID for prediction link.
         */
        private Long matchId;

        /**
         * Home team name.
         */
        private String homeTeam;

        /**
         * Away team name.
         */
        private String awayTeam;

        /**
         * Home team logo URL.
         */
        private String homeTeamLogo;

        /**
         * Away team logo URL.
         */
        private String awayTeamLogo;

        /**
         * Match date.
         */
        private LocalDate matchDate;

        /**
         * Match time (local).
         */
        private String matchTime;

        /**
         * Formatted date string.
         */
        private String formattedDate;

        /**
         * Venue/Stadium name.
         */
        private String venue;

        /**
         * Match status (SCHEDULED, TIMED, LIVE, etc.).
         */
        private String status;

        /**
         * Match day number.
         */
        private Integer matchDay;

        /**
         * Whether prediction is available.
         */
        private boolean canPredict;
    }
}

