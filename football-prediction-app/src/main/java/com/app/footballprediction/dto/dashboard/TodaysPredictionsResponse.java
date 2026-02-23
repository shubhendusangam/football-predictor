package com.app.footballprediction.dto.dashboard;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for today's predictions dashboard section.
 */
@Data
@Builder
public class TodaysPredictionsResponse {

    private List<TodaysPredictionDto> predictions;
    private int totalCount;
    private int pendingCount;
    private int wonCount;
    private int lostCount;
    private String lastUpdated;

    @Data
    @Builder
    public static class TodaysPredictionDto {
        private Long matchId;
        private String homeTeam;
        private String awayTeam;
        private String homeTeamLogo;
        private String awayTeamLogo;
        private LocalDate matchDate;
        private String matchTime;
        private String predictedWinner;
        private String predictedResult;
        private double confidence;
        private String status; // PENDING, WON, LOST
        private Integer actualHomeGoals;
        private Integer actualAwayGoals;
    }
}

