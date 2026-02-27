package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Value;

/**
 * DTO for first half vs second half performance analysis.
 *
 * <p>Contains goal distribution, win rates based on half-time position,
 * and pattern classification for a team.</p>
 *
 * <p>This DTO is immutable (using @Value) for thread safety.</p>
 *
 * <h2>Key Metrics:</h2>
 * <ul>
 *   <li>Goal distribution between first and second half</li>
 *   <li>Win rates when leading, drawing, or losing at half-time</li>
 *   <li>Comeback rate (wins after trailing at HT)</li>
 *   <li>Performance pattern classification</li>
 * </ul>
 *
 * @author Football Forecaster Team
 * @version 1.1.0
 */
@Value
@Builder
public class HalfAnalysisDTO {

    /**
     * Team name being analyzed.
     */
    String teamName;

    /**
     * Data scope description (e.g., "Last 20 Matches").
     */
    @Builder.Default
    String dataScope = "Recent Matches";

    /**
     * Average goals scored in the first half per match.
     */
    double firstHalfGoalsAvg;

    /**
     * Average goals scored in the second half per match.
     */
    double secondHalfGoalsAvg;

    /**
     * Total first half goals scored in analyzed matches.
     */
    int totalFirstHalfGoals;

    /**
     * Total second half goals scored in analyzed matches.
     */
    int totalSecondHalfGoals;

    /**
     * Total goals scored across all analyzed matches.
     */
    int totalGoals;

    /**
     * Percentage of total goals scored in first half (0-100).
     */
    double firstHalfPercentage;

    /**
     * Percentage of total goals scored in second half (0-100).
     */
    double secondHalfPercentage;

    /**
     * Which half the team is stronger in.
     * Values: "First Half", "Second Half", "Balanced"
     */
    String strongerHalf;

    /**
     * Win rate when leading at half-time (0-100).
     */
    double winRateWhenLeadingHT;

    /**
     * Win rate when drawing at half-time (0-100).
     */
    double winRateWhenDrawingHT;

    /**
     * Win rate when losing at half-time (0-100).
     */
    double winRateWhenLosingHT;

    /**
     * Comeback rate - wins after trailing at HT (0-100).
     */
    double comebackRate;

    /**
     * Pattern classification based on goal distribution.
     * Values: "Fast Starter", "Strong Finisher", "Balanced"
     */
    String pattern;

    /**
     * Number of matches analyzed.
     */
    int matchesAnalyzed;

    /**
     * Matches where team was leading at HT.
     */
    int matchesLeadingHT;

    /**
     * Matches where team was drawing at HT.
     */
    int matchesDrawingHT;

    /**
     * Matches where team was trailing at HT.
     */
    int matchesTrailingHT;

    /**
     * Average goals conceded in first half per match.
     */
    double firstHalfConcededAvg;

    /**
     * Average goals conceded in second half per match.
     */
    double secondHalfConcededAvg;

    /**
     * Confidence level based on sample size (0-1).
     */
    double confidence;

    /**
     * First half goal differential (scored - conceded).
     */
    double firstHalfGoalDifferential;

    /**
     * Second half goal differential (scored - conceded).
     */
    double secondHalfGoalDifferential;

    /**
     * Indicates if any anomaly was detected in the data.
     */
    @Builder.Default
    boolean anomalyDetected = false;

    /**
     * Description of detected anomalies, if any.
     */
    String anomalyDescription;

    /**
     * Create empty analysis for unknown team.
     *
     * @param teamName The team name
     * @return Empty HalfAnalysisDTO with default/safe values
     */
    public static HalfAnalysisDTO empty(String teamName) {
        return HalfAnalysisDTO.builder()
                .teamName(teamName)
                .dataScope("No Data Available")
                .firstHalfGoalsAvg(0.0)
                .secondHalfGoalsAvg(0.0)
                .totalFirstHalfGoals(0)
                .totalSecondHalfGoals(0)
                .totalGoals(0)
                .firstHalfPercentage(50.0)
                .secondHalfPercentage(50.0)
                .strongerHalf("Balanced")
                .winRateWhenLeadingHT(0.0)
                .winRateWhenDrawingHT(0.0)
                .winRateWhenLosingHT(0.0)
                .comebackRate(0.0)
                .pattern("Balanced")
                .matchesAnalyzed(0)
                .matchesLeadingHT(0)
                .matchesDrawingHT(0)
                .matchesTrailingHT(0)
                .firstHalfConcededAvg(0.0)
                .secondHalfConcededAvg(0.0)
                .confidence(0.0)
                .firstHalfGoalDifferential(0.0)
                .secondHalfGoalDifferential(0.0)
                .anomalyDetected(false)
                .anomalyDescription(null)
                .build();
    }
}

