package com.app.footballprediction.dto.dashboard;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for model accuracy dashboard section.
 */
@Data
@Builder
public class ModelAccuracyResponse {

    private double overallAccuracy;
    private double last10Accuracy;
    private long totalPredictions;
    private long correctPredictions;
    private long incorrectPredictions;
    private long pendingPredictions;

    private double homeAccuracy;
    private double awayAccuracy;
    private double highConfidenceAccuracy;

    private String trendIndicator; // UP, DOWN, STABLE
    private double trendChange;

    private AccuracyBreakdown breakdown;
    private String lastUpdated;

    @Data
    @Builder
    public static class AccuracyBreakdown {
        private long homeWins;
        private long awayWins;
        private long draws;
        private long correctHomeWins;
        private long correctAwayWins;
        private long correctDraws;
    }
}

