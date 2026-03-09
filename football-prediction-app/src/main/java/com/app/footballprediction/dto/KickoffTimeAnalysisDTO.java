package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.List;

/**
 * Wrapper DTO for kick-off time performance analysis.
 *
 * <p>Contains per-slot statistics plus summary fields identifying
 * the best and worst kick-off times for a team, and the overall
 * win rate for comparison.</p>
 *
 * <p>This DTO is immutable (using @Value) for thread safety.</p>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@Value
@Builder
public class KickoffTimeAnalysisDTO {

    /**
     * Team name being analyzed.
     */
    String teamName;

    /**
     * Data scope description (e.g., "Last 50 Matches").
     */
    @Builder.Default
    String dataScope = "Recent Matches";

    /**
     * Number of matches analyzed.
     */
    int matchesAnalyzed;

    /**
     * Number of matches with valid kick-off time data.
     */
    int matchesWithTimeData;

    /**
     * Performance breakdown by time slot.
     */
    @Builder.Default
    List<KickoffTimeStatsDTO> timeSlots = Collections.emptyList();

    /**
     * Best kick-off time slot (highest win percentage).
     */
    String bestTime;

    /**
     * Worst kick-off time slot (lowest win percentage).
     */
    String worstTime;

    /**
     * Overall win rate across all matches (0-100), for comparison baseline.
     */
    double overallWinRate;

    /**
     * Overall average goals scored per match.
     */
    double overallAvgGoalsScored;

    /**
     * Confidence level based on sample size.
     * Values: "High", "Medium", "Low"
     */
    String confidence;

    /**
     * Create an empty result for a team with no data.
     *
     * @param teamName Team name
     * @return Empty analysis DTO
     */
    public static KickoffTimeAnalysisDTO empty(String teamName) {
        return KickoffTimeAnalysisDTO.builder()
                .teamName(teamName)
                .dataScope("No Data")
                .matchesAnalyzed(0)
                .matchesWithTimeData(0)
                .timeSlots(Collections.emptyList())
                .bestTime("N/A")
                .worstTime("N/A")
                .overallWinRate(0.0)
                .overallAvgGoalsScored(0.0)
                .confidence("Low")
                .build();
    }
}

