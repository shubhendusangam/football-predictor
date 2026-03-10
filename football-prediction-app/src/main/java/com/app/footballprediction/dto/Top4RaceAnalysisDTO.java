package com.app.footballprediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for the complete Top 4 race analysis.
 * Contains season context, estimated safety threshold, and all teams in the race.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Top4RaceAnalysisDTO {

    /**
     * Season identifier (e.g., "2025-26").
     */
    private String season;

    /**
     * Date the analysis was calculated as of.
     */
    private LocalDate asOfDate;

    /**
     * List of teams in the race (typically top 7-10).
     * Sorted by current position.
     */
    private List<Top4RaceDTO> teamsInRace;

    /**
     * Estimated points needed for 4th place safety.
     * Typically calculated based on historical averages (70-75 points).
     */
    private int pointsForSafety;

    /**
     * Total number of matches in the season (typically 38 for Premier League).
     */
    private int totalMatchesInSeason;

    /**
     * Number of matchdays completed.
     */
    private int matchdaysCompleted;

    /**
     * Percentage of season completed.
     */
    private double seasonProgressPercent;

    /**
     * Title race summary - are multiple teams in contention?
     */
    private TitleRaceSummary titleRace;

    /**
     * Last updated timestamp (ISO format).
     */
    private String lastUpdated;

    /**
     * Summary of the title race contention.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TitleRaceSummary {
        /**
         * Number of teams realistically in title contention (within 9 points).
         */
        private int contenders;

        /**
         * Points gap between 1st and 2nd place.
         */
        private int gapFirstToSecond;

        /**
         * Is the title mathematically decided?
         */
        private boolean decided;

        /**
         * Current leader team name.
         */
        private String leader;

        /**
         * Title race intensity.
         * Values: "Wide Open" | "Close Race" | "Comfortable Lead" | "Decided"
         */
        private String intensity;
    }
}

