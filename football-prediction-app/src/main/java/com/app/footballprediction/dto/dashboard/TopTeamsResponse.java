package com.app.footballprediction.dto.dashboard;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response DTO for top teams dashboard section.
 */
@Data
@Builder
public class TopTeamsResponse {

    private List<TopTeamDto> teamsByPoints;
    private List<TopTeamDto> teamsByGoalDifference;
    private List<TopTeamDto> teamsByForm;
    private String season;
    private String lastUpdated;

    @Data
    @Builder
    public static class TopTeamDto {
        private int rank;
        private String teamName;
        private String teamLogo;
        private int points;
        private int goalDifference;
        private int goalsFor;
        private int goalsAgainst;
        private String form;
        private int won;
        private int drawn;
        private int lost;
        private int played;
        private double winPercentage;
    }
}

