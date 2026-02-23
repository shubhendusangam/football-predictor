package com.app.footballprediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for season statistics.
 * Contains team-level statistics for a specific season.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeasonStatsResponse {

    private String season;
    private int totalMatches;
    private int totalTeams;
    private List<TeamSeasonStats> teamStats;
    private PaginationInfo pagination;

    /**
     * Statistics for a single team in a season.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamSeasonStats {
        private String team;
        private int matches;
        private int wins;
        private int draws;
        private int losses;
        private int goalsScored;
        private int goalsConceded;
        private int points;
        private double winRate;         // Win percentage
        private List<MatchResult> recentForm;  // Last 5-10 matches for chart
    }

    /**
     * Simplified match result for form chart.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchResult {
        private String date;
        private String opponent;
        private int goalsScored;
        private int goalsConceded;
        private String result;  // W, D, L
        private int points;
    }

    /**
     * Pagination information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaginationInfo {
        private int page;
        private int pageSize;
        private int totalItems;
        private int totalPages;
    }
}

