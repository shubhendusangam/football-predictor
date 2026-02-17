package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for upcoming match predictions.
 */
@Data
@Builder
public class UpcomingPredictionResponse {

    private String competition;
    private String competitionName;
    private Integer currentMatchday;
    private List<MatchPrediction> predictions;

    @Data
    @Builder
    public static class MatchPrediction {
        private Long matchId;
        private String matchDate;
        private Integer matchday;

        // Teams
        private String homeTeam;
        private String awayTeam;
        private String homeTeamCrest;
        private String awayTeamCrest;

        // Prediction
        private String prediction;       // HOME_WIN, DRAW, AWAY_WIN
        private String predictionCode;   // H, D, A
        private double probHomeWin;
        private double probDraw;
        private double probAwayWin;
        private String confidence;       // HIGH, MEDIUM, LOW

        // Current season form (from external API)
        private CurrentForm homeTeamForm;
        private CurrentForm awayTeamForm;

        // Error (if prediction failed)
        private String error;
    }

    @Data
    @Builder
    public static class CurrentForm {
        private String recentForm;       // e.g., "W,W,D,L,W"
        private Integer position;        // League position
        private Integer points;          // Total points
        private Integer played;          // Games played
        private Integer won;
        private Integer draw;
        private Integer lost;
        private Integer goalsFor;
        private Integer goalsAgainst;
        private Double pointsPerGame;    // Calculated
    }
}

