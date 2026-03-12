package com.app.footballprediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Comprehensive referee statistics DTO.
 * Provides detailed referee analysis including card tendencies,
 * foul rates, home advantage impact, and referee classification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefereeStatsDTO {

    /** Referee full name. */
    private String refereeName;

    /** Total number of matches officiated. */
    private int matchesOfficiated;

    /** Average yellow cards per game (home + away). */
    private double avgYellowCards;

    /** Average red cards per game (home + away). */
    private double avgRedCards;

    /** Percentage of matches with at least one red card (0-100). */
    private double redCardPercentage;

    /** Average total fouls per game (home + away). */
    private double avgFoulsPerGame;

    /** Home win percentage in matches officiated (0-100). */
    private double homeWinPercentage;

    /** Average total goals per game (home + away). */
    private double avgGoalsPerGame;

    /**
     * Referee type classification:
     * - "Strict"   (> 4.5 cards/game)
     * - "Lenient"  (< 3 cards/game)
     * - "Balanced" (3 - 4.5 cards/game)
     */
    private String refType;

    /**
     * Card style classification:
     * - "High"   (> 4.5 cards/game)
     * - "Medium" (3 - 4.5 cards/game)
     * - "Low"    (< 3 cards/game)
     */
    private String cardStyle;

    /** Average home yellow cards per game. */
    private double avgHomeYellowCards;

    /** Average away yellow cards per game. */
    private double avgAwayYellowCards;

    /** Average home fouls per game. */
    private double avgHomeFouls;

    /** Average away fouls per game. */
    private double avgAwayFouls;

    /** Draw percentage in matches officiated (0-100). */
    private double drawPercentage;

    /** Away win percentage in matches officiated (0-100). */
    private double awayWinPercentage;

    /** Strictness index (0.0 = lenient, 1.0 = strict). */
    private double strictnessIndex;

    /** Data completeness percentage (0-100). */
    private double dataCompleteness;

    /** Percentage of matches with over 2.5 goals (0-100). */
    private double over25GoalsRate;

    /** Average cards issued per foul committed. */
    private double cardsPerFoul;

    /** Number of distinct seasons the referee has been active. */
    private int seasonsActive;

    /**
     * Create an empty DTO for an unknown referee.
     */
    public static RefereeStatsDTO empty(String refereeName) {
        return RefereeStatsDTO.builder()
                .refereeName(refereeName != null ? refereeName : "Unknown")
                .matchesOfficiated(0)
                .avgYellowCards(0.0)
                .avgRedCards(0.0)
                .redCardPercentage(0.0)
                .avgFoulsPerGame(0.0)
                .homeWinPercentage(46.2)
                .avgGoalsPerGame(2.7)
                .refType("Unknown")
                .cardStyle("Unknown")
                .avgHomeYellowCards(0.0)
                .avgAwayYellowCards(0.0)
                .avgHomeFouls(0.0)
                .avgAwayFouls(0.0)
                .drawPercentage(26.8)
                .awayWinPercentage(27.0)
                .strictnessIndex(0.5)
                .dataCompleteness(0.0)
                .over25GoalsRate(0.0)
                .cardsPerFoul(0.0)
                .seasonsActive(0)
                .build();
    }
}

