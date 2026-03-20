package com.app.footballprediction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DTO for scoreline prediction response.
 *
 * <p>Uses a Dixon-Coles Poisson regression model to predict exact match scores,
 * goal-market probabilities, and both-teams-to-score probabilities.</p>
 */
@Data
@Builder
@Schema(description = "Match score prediction from Poisson regression model")
public class ScorePredictionDTO {

    @Schema(description = "Home team name", example = "Arsenal")
    private String homeTeam;

    @Schema(description = "Away team name", example = "Chelsea")
    private String awayTeam;

    // ── Core score prediction ─────────────────────────────────────────

    @Schema(description = "Score prediction details")
    private ScorePrediction scorePrediction;

    // ── Poisson lambda (expected goals) ───────────────────────────────

    @Schema(description = "Expected goals for home team (Poisson λ)", example = "1.85")
    private double homeExpectedGoals;

    @Schema(description = "Expected goals for away team (Poisson λ)", example = "1.12")
    private double awayExpectedGoals;

    // ── Outcome probabilities derived from score matrix ───────────────

    @Schema(description = "Probability of home win derived from score matrix", example = "0.48")
    private double probHomeWin;

    @Schema(description = "Probability of draw derived from score matrix", example = "0.26")
    private double probDraw;

    @Schema(description = "Probability of away win derived from score matrix", example = "0.26")
    private double probAwayWin;

    // ── Score probability matrix (0-0 to 5-5) ────────────────────────

    @Schema(description = "Score probability matrix, keys are 'i-j' (e.g., '2-1' = 0.12)")
    private Map<String, Double> scoreMatrix;

    // ── Confidence ────────────────────────────────────────────────────

    @Schema(description = "Model confidence level", example = "HIGH",
            allowableValues = {"HIGH", "MEDIUM", "LOW"})
    private String confidence;

    /**
     * Score prediction sub-object matching the requested output format.
     */
    @Data
    @Builder
    @Schema(description = "Detailed score prediction with market probabilities")
    public static class ScorePrediction {

        @Schema(description = "Most likely scoreline", example = "2-1")
        private String mostLikelyScore;

        @Schema(description = "Probability of the most likely score", example = "0.17")
        private double probability;

        @Schema(description = "Top 3 most likely scorelines with probabilities")
        private List<Map<String, Double>> top3Scores;

        @Schema(description = "Probability of over 1.5 goals", example = "0.82")
        private double over15Prob;

        @Schema(description = "Probability of over 2.5 goals", example = "0.61")
        private double over25Prob;

        @Schema(description = "Probability of over 3.5 goals", example = "0.38")
        private double over35Prob;

        @Schema(description = "Probability of both teams to score", example = "0.54")
        private double bttsProb;

        @Schema(description = "Probability of home team clean sheet", example = "0.28")
        private double cleanSheetHome;

        @Schema(description = "Probability of away team clean sheet", example = "0.19")
        private double cleanSheetAway;
    }
}

