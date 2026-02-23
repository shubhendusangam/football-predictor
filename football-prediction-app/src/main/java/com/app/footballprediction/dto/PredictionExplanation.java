package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO for prediction explainability.
 * Provides breakdown of factors contributing to the prediction.
 */
@Data
@Builder
public class PredictionExplanation {

    /**
     * Impact of Elo rating difference on prediction.
     * Format: "+6%" or "-4%"
     */
    private String eloImpact;

    /**
     * Impact of recent form on prediction.
     * Format: "+3%" or "-2%"
     */
    private String formImpact;

    /**
     * Impact of goal scoring/conceding trends.
     * Format: "+2%" or "-3%"
     */
    private String goalTrendImpact;

    /**
     * Impact of head-to-head history.
     * Format: "+4%" or "-1%"
     */
    private String h2hImpact;

    /**
     * Impact of home advantage.
     * Format: "+3%"
     */
    private String homeAdvantageImpact;

    /**
     * Summary explanation text.
     */
    private String summary;

    /**
     * Create explanation with formatted percentage impacts.
     */
    public static String formatImpact(double impact) {
        if (impact == 0) {
            return "0%";
        }
        String sign = impact > 0 ? "+" : "";
        return String.format("%s%.0f%%", sign, impact);
    }
}

