package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Value;

/**
 * DTO for match cards prediction results.
 * Contains predicted yellow and red card statistics with referee influence.
 *
 * <p>This DTO is immutable (using @Value) for thread safety.</p>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@Value
@Builder
public class CardsPredictionDTO {

    /**
     * Home team name.
     */
    String homeTeam;

    /**
     * Away team name.
     */
    String awayTeam;

    /**
     * Referee name (if provided).
     */
    String referee;

    /**
     * Expected yellow cards for home team.
     */
    double expectedYellowCardsHome;

    /**
     * Expected yellow cards for away team.
     */
    double expectedYellowCardsAway;

    /**
     * Expected total yellow cards for the match.
     * Should equal expectedYellowCardsHome + expectedYellowCardsAway.
     */
    double expectedTotalYellowCards;

    /**
     * Probability of at least one red card in the match (0 to 1).
     */
    double redCardProbability;

    /**
     * Discipline warning message.
     * Examples: "High Card Risk", "High Red Card Risk", null if no warning.
     */
    String disciplineWarning;

    /**
     * Home team's average yellow cards per match (historical).
     */
    double homeTeamAvgYellowCards;

    /**
     * Away team's average yellow cards per match (historical).
     */
    double awayTeamAvgYellowCards;

    /**
     * Referee's average yellow cards per match.
     */
    double refereeAvgYellowCards;

    /**
     * Referee strictness index (0 = lenient, 1 = strict).
     */
    double refereeStrictnessIndex;

    /**
     * Referee card impact description.
     */
    String refereeImpact;

    /**
     * Home team matches analyzed.
     */
    int homeMatchesAnalyzed;

    /**
     * Away team matches analyzed.
     */
    int awayMatchesAnalyzed;

    /**
     * Prediction confidence level (0 to 1).
     */
    double confidence;

    /**
     * Create an empty prediction for error cases.
     */
    public static CardsPredictionDTO empty(String homeTeam, String awayTeam, String referee) {
        return CardsPredictionDTO.builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .referee(referee)
                .expectedYellowCardsHome(0.0)
                .expectedYellowCardsAway(0.0)
                .expectedTotalYellowCards(0.0)
                .redCardProbability(0.0)
                .disciplineWarning(null)
                .homeTeamAvgYellowCards(0.0)
                .awayTeamAvgYellowCards(0.0)
                .refereeAvgYellowCards(0.0)
                .refereeStrictnessIndex(0.5)
                .refereeImpact("Unknown")
                .homeMatchesAnalyzed(0)
                .awayMatchesAnalyzed(0)
                .confidence(0.0)
                .build();
    }
}

