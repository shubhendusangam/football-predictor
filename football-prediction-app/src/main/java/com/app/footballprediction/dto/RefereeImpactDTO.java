package com.app.footballprediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for referee impact analysis on a specific match.
 * Combines referee tendencies with team discipline to predict match impact.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefereeImpactDTO {

    /** Referee full name. */
    private String refereeName;

    /** Base referee statistics. */
    private RefereeStatsDTO baseStats;

    /** Home team name. */
    private String homeTeam;

    /** Away team name. */
    private String awayTeam;

    /** Expected total yellow cards for this match. */
    private double expectedYellowCards;

    /** Expected home yellow cards. */
    private double expectedHomeYellowCards;

    /** Expected away yellow cards. */
    private double expectedAwayYellowCards;

    /** Probability of at least one red card (0.0 - 1.0). */
    private double redCardProbability;

    /** Home advantage adjustment percentage (+/- %). */
    private double homeAdvantageAdjustment;

    /** Expected total fouls. */
    private double expectedFouls;

    /**
     * Warning message if strict referee is assigned to high-discipline teams,
     * or any notable mismatch.
     */
    private String warning;

    /** Risk level: "Low", "Medium", "High". */
    private String riskLevel;

    /** Confidence in the prediction: "Low", "Medium", "High". */
    private String confidence;

    /**
     * Create an empty impact DTO.
     */
    public static RefereeImpactDTO empty(String refereeName, String homeTeam, String awayTeam) {
        return RefereeImpactDTO.builder()
                .refereeName(refereeName != null ? refereeName : "Unknown")
                .baseStats(RefereeStatsDTO.empty(refereeName))
                .homeTeam(homeTeam != null ? homeTeam : "Unknown")
                .awayTeam(awayTeam != null ? awayTeam : "Unknown")
                .expectedYellowCards(0.0)
                .expectedHomeYellowCards(0.0)
                .expectedAwayYellowCards(0.0)
                .redCardProbability(0.0)
                .homeAdvantageAdjustment(0.0)
                .expectedFouls(0.0)
                .warning("Insufficient data for prediction")
                .riskLevel("Unknown")
                .confidence("Low")
                .build();
    }
}

