package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.CardsPredictionDTO;
import com.app.footballprediction.dto.RefereeStats;
import com.app.footballprediction.dto.TeamDisciplineDTO;
import com.app.footballprediction.dto.TeamDisciplineDTO.MatchBookingSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for predicting yellow and red cards in matches and tracking team discipline.
 *
 * <p>Provides comprehensive card analytics including:</p>
 * <ul>
 *   <li>Match card predictions with referee influence</li>
 *   <li>Team discipline statistics and ratings</li>
 *   <li>Historical booking patterns</li>
 * </ul>
 *
 * <p><strong>Data Sources:</strong></p>
 * <ul>
 *   <li>HY (Home Yellow Cards)</li>
 *   <li>AY (Away Yellow Cards)</li>
 *   <li>HR (Home Red Cards)</li>
 *   <li>AR (Away Red Cards)</li>
 * </ul>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CardsPredictionService {

    private final MatchRepository matchRepository;
    private final RefereeStatsService refereeStatsService;
    private final TeamValidationService teamValidationService;

    // ══════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Maximum recent matches to analyze per team.
     */
    private static final int MAX_MATCHES = 20;

    /**
     * Minimum matches required for confident predictions.
     */
    private static final int MIN_MATCHES_FOR_CONFIDENCE = 5;

    /**
     * Recent bookings to include in discipline summary.
     */
    private static final int RECENT_BOOKINGS_COUNT = 5;

    /**
     * League average yellow cards per team per match.
     */
    private static final double LEAGUE_AVG_YELLOW_CARDS_PER_TEAM = 1.75;

    /**
     * League average red cards per match (total).
     */
    private static final double LEAGUE_AVG_RED_CARDS = 0.08;

    /**
     * High card risk threshold (total yellow cards).
     */
    private static final double HIGH_CARD_RISK_THRESHOLD = 5.0;

    /**
     * High red card risk threshold (probability).
     */
    private static final double HIGH_RED_CARD_RISK_THRESHOLD = 0.20;

    /**
     * Discipline rating thresholds.
     */
    private static final double EXCELLENT_DISCIPLINE_THRESHOLD = 2.0;
    private static final double AVERAGE_DISCIPLINE_THRESHOLD = 3.0;

    /**
     * Referee strictness adjustment factors.
     */
    private static final double STRICT_REFEREE_BONUS = 1.5;  // Additional cards for strict referee
    private static final double LENIENT_REFEREE_REDUCTION = 0.5;  // Reduction for lenient referee

    /**
     * Strictness thresholds.
     */
    private static final double STRICT_THRESHOLD = 0.6;
    private static final double LENIENT_THRESHOLD = 0.4;

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Predict cards for an upcoming match.
     *
     * <p>Takes into account:</p>
     * <ul>
     *   <li>Home team's historical yellow card rate (home matches)</li>
     *   <li>Away team's historical yellow card rate (away matches)</li>
     *   <li>Referee strictness adjustment</li>
     *   <li>Red card probability estimation</li>
     * </ul>
     *
     * @param homeTeam    Home team name
     * @param awayTeam    Away team name
     * @param refereeName Referee name (optional, can be null)
     * @return CardsPredictionDTO with prediction results
     * @throws IllegalArgumentException if team names are invalid
     */
    @Cacheable(value = "cardsPrediction", key = "#homeTeam + '-vs-' + #awayTeam + '-' + #refereeName")
    public CardsPredictionDTO predictCards(String homeTeam, String awayTeam, String refereeName) {
        log.info("Predicting cards for match: {} vs {} (referee: {})", homeTeam, awayTeam, refereeName);

        // Validate inputs
        validateTeamName(homeTeam, "Home team");
        validateTeamName(awayTeam, "Away team");

        if (homeTeam.trim().equalsIgnoreCase(awayTeam.trim())) {
            throw new IllegalArgumentException("Home and away teams cannot be the same");
        }

        LocalDate beforeDate = LocalDate.now().plusDays(1);
        String currentSeason = matchRepository.findCurrentSeason();

        if (currentSeason == null) {
            log.warn("No current season found, using all available data");
        }

        // Get team discipline stats
        TeamDisciplineDTO homeStats = getTeamDiscipline(homeTeam);
        TeamDisciplineDTO awayStats = getTeamDiscipline(awayTeam);

        // Get referee stats (optional)
        RefereeStats refereeStats = null;
        if (refereeName != null && !refereeName.isBlank()) {
            refereeStats = refereeStatsService.getRefereeStats(refereeName.trim());
            log.debug("Referee stats for {}: avgYellow={}, strictness={}",
                    refereeName, refereeStats.getAvgYellowCards(), refereeStats.getStrictnessIndex());
        }

        // Build prediction
        return buildCardsPrediction(homeTeam.trim(), awayTeam.trim(), homeStats, awayStats, refereeStats);
    }

    /**
     * Get discipline statistics for a team.
     *
     * @param teamName Team name
     * @return TeamDisciplineDTO with discipline statistics
     * @throws IllegalArgumentException if team name is invalid
     */
    @Cacheable(value = "teamDiscipline", key = "#teamName")
    public TeamDisciplineDTO getTeamDiscipline(String teamName) {
        log.info("Getting discipline stats for team: {}", teamName);

        validateTeamName(teamName, "Team");

        LocalDate beforeDate = LocalDate.now().plusDays(1);
        String resolvedTeam = resolveTeamName(teamName.trim(), beforeDate);

        // Fetch recent matches
        List<Match> allMatches = matchRepository.findByTeamBeforeDate(resolvedTeam, beforeDate);

        if (allMatches.isEmpty()) {
            log.warn("No matches found for team: {}", resolvedTeam);
            return TeamDisciplineDTO.empty(resolvedTeam);
        }

        // Limit to recent matches
        List<Match> recentMatches = allMatches.stream()
                .limit(MAX_MATCHES)
                .toList();

        log.debug("Analyzing {} matches for discipline stats", recentMatches.size());

        return buildTeamDiscipline(resolvedTeam, recentMatches);
    }

    // ══════════════════════════════════════════════════════════════════════
    // PREDICTION CALCULATION
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Build cards prediction from team and referee statistics.
     */
    private CardsPredictionDTO buildCardsPrediction(String homeTeam, String awayTeam,
                                                     TeamDisciplineDTO homeStats,
                                                     TeamDisciplineDTO awayStats,
                                                     RefereeStats refereeStats) {
        // Base expected yellow cards (from historical data)
        double baseHomeYellows = calculateBaseYellowCards(homeStats, true);
        double baseAwayYellows = calculateBaseYellowCards(awayStats, false);

        log.debug("Base yellow cards - Home: {}, Away: {}", baseHomeYellows, baseAwayYellows);

        // Referee adjustment
        double refereeAdjustment = 0.0;
        double refereeAvgYellows = LEAGUE_AVG_YELLOW_CARDS_PER_TEAM * 2;  // Default
        double refereeStrictness = 0.5;  // Default (neutral)
        String refereeImpact = "Unknown Referee - Using league averages";
        String refereeName = null;

        if (refereeStats != null && refereeStats.getMatchesOfficiated() > 0) {
            refereeName = refereeStats.getRefereeName();
            refereeAvgYellows = refereeStats.getAvgYellowCards();
            refereeStrictness = refereeStats.getStrictnessIndex();
            refereeAdjustment = calculateRefereeAdjustment(refereeStats);
            refereeImpact = buildRefereeImpactDescription(refereeStats);

            log.debug("Referee adjustment for {}: {} (strictness: {})",
                    refereeName, refereeAdjustment, refereeStrictness);
        }

        // Apply referee adjustment proportionally
        double adjustedHomeYellows = applyRefereeAdjustment(baseHomeYellows, refereeAdjustment);
        double adjustedAwayYellows = applyRefereeAdjustment(baseAwayYellows, refereeAdjustment);

        // Ensure non-negative
        adjustedHomeYellows = Math.max(0.0, adjustedHomeYellows);
        adjustedAwayYellows = Math.max(0.0, adjustedAwayYellows);

        double totalYellows = adjustedHomeYellows + adjustedAwayYellows;

        // Red card probability
        double redCardProb = calculateRedCardProbability(homeStats, awayStats, refereeStats);

        // Discipline warning
        String warning = buildDisciplineWarning(totalYellows, redCardProb);

        // Confidence based on sample size
        double confidence = calculateConfidence(homeStats.getMatchesAnalyzed(), awayStats.getMatchesAnalyzed());

        log.info("Card prediction: {} vs {} - Total yellows: {}, Red prob: {}, Warning: {}",
                homeTeam, awayTeam, totalYellows, redCardProb, warning);

        return CardsPredictionDTO.builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .referee(refereeName)
                .expectedYellowCardsHome(roundToTwoDecimals(adjustedHomeYellows))
                .expectedYellowCardsAway(roundToTwoDecimals(adjustedAwayYellows))
                .expectedTotalYellowCards(roundToTwoDecimals(totalYellows))
                .redCardProbability(roundToThreeDecimals(redCardProb))
                .disciplineWarning(warning)
                .homeTeamAvgYellowCards(roundToTwoDecimals(homeStats.getAvgYellowCardsOverall()))
                .awayTeamAvgYellowCards(roundToTwoDecimals(awayStats.getAvgYellowCardsOverall()))
                .refereeAvgYellowCards(roundToTwoDecimals(refereeAvgYellows))
                .refereeStrictnessIndex(roundToThreeDecimals(refereeStrictness))
                .refereeImpact(refereeImpact)
                .homeMatchesAnalyzed(homeStats.getMatchesAnalyzed())
                .awayMatchesAnalyzed(awayStats.getMatchesAnalyzed())
                .confidence(roundToThreeDecimals(confidence))
                .build();
    }

    /**
     * Calculate base expected yellow cards for a team.
     */
    private double calculateBaseYellowCards(TeamDisciplineDTO stats, boolean isHome) {
        if (stats.getMatchesAnalyzed() == 0) {
            return LEAGUE_AVG_YELLOW_CARDS_PER_TEAM;
        }

        // Use venue-specific average if available
        double venueAvg = isHome ? stats.getAvgYellowCardsHome() : stats.getAvgYellowCardsAway();

        // If venue-specific data is missing, use overall
        if (venueAvg == 0.0) {
            venueAvg = stats.getAvgYellowCardsOverall();
        }

        // Fallback to league average if still zero
        if (venueAvg == 0.0) {
            return LEAGUE_AVG_YELLOW_CARDS_PER_TEAM;
        }

        return venueAvg;
    }

    /**
     * Calculate referee adjustment based on strictness.
     *
     * @return Adjustment value (positive = more cards, negative = fewer cards)
     */
    private double calculateRefereeAdjustment(RefereeStats refereeStats) {
        double strictness = refereeStats.getStrictnessIndex();
        double avgCards = refereeStats.getAvgYellowCards();

        // Calculate deviation from league average
        double leagueAvgPerMatch = LEAGUE_AVG_YELLOW_CARDS_PER_TEAM * 2;
        double deviation = avgCards - leagueAvgPerMatch;

        if (strictness >= STRICT_THRESHOLD) {
            // Strict referee: add cards proportional to strictness
            return Math.min(STRICT_REFEREE_BONUS, deviation * 0.5);
        } else if (strictness <= LENIENT_THRESHOLD) {
            // Lenient referee: reduce cards
            return Math.max(-LENIENT_REFEREE_REDUCTION, deviation * 0.5);
        }

        // Neutral referee: minimal adjustment
        return deviation * 0.25;
    }

    /**
     * Apply referee adjustment to base yellow cards.
     */
    private double applyRefereeAdjustment(double baseCards, double adjustment) {
        // Distribute adjustment proportionally
        double adjustmentPerTeam = adjustment / 2.0;
        return baseCards + adjustmentPerTeam;
    }

    /**
     * Build referee impact description.
     */
    private String buildRefereeImpactDescription(RefereeStats stats) {
        double strictness = stats.getStrictnessIndex();
        String label;

        if (strictness >= STRICT_THRESHOLD) {
            label = "Strict";
        } else if (strictness <= LENIENT_THRESHOLD) {
            label = "Lenient";
        } else {
            label = "Average";
        }

        return String.format("%s (%s - %.1f cards/game avg)",
                stats.getRefereeName(), label, stats.getAvgYellowCards());
    }

    /**
     * Calculate red card probability.
     */
    private double calculateRedCardProbability(TeamDisciplineDTO homeStats,
                                                TeamDisciplineDTO awayStats,
                                                RefereeStats refereeStats) {
        // Base probability from team red card rates
        double homeRedRate = homeStats.getAvgRedCards();
        double awayRedRate = awayStats.getAvgRedCards();

        // Combined probability (simplified)
        double baseProb = 1.0 - ((1.0 - homeRedRate) * (1.0 - awayRedRate));

        // Adjust for aggressive teams
        if ("Aggressive".equals(homeStats.getDisciplineRating()) ||
            "Aggressive".equals(awayStats.getDisciplineRating())) {
            baseProb *= 1.3;  // 30% increase for aggressive teams
        }

        // Adjust for referee
        if (refereeStats != null && refereeStats.getAvgRedCards() > LEAGUE_AVG_RED_CARDS * 1.5) {
            baseProb *= 1.2;  // 20% increase for strict referee
        }

        // Ensure bounds [0, 1]
        return Math.max(0.0, Math.min(1.0, baseProb));
    }

    /**
     * Build discipline warning message.
     */
    private String buildDisciplineWarning(double expectedYellows, double redCardProb) {
        List<String> warnings = new ArrayList<>();

        if (expectedYellows > HIGH_CARD_RISK_THRESHOLD) {
            warnings.add("High Card Risk");
        }

        if (redCardProb > HIGH_RED_CARD_RISK_THRESHOLD) {
            warnings.add("High Red Card Risk");
        }

        if (warnings.isEmpty()) {
            return null;
        }

        return String.join(", ", warnings);
    }

    // ══════════════════════════════════════════════════════════════════════
    // TEAM DISCIPLINE CALCULATION
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Build team discipline DTO from match data.
     */
    private TeamDisciplineDTO buildTeamDiscipline(String teamName, List<Match> matches) {
        int totalYellowCards = 0;
        int totalRedCards = 0;
        int totalOpponentYellowCards = 0;
        int homeMatchCount = 0;
        int awayMatchCount = 0;
        int homeYellowCards = 0;
        int awayYellowCards = 0;
        int matchesWithCardData = 0;

        List<MatchBookingSummary> recentBookings = new ArrayList<>();

        for (int i = 0; i < matches.size(); i++) {
            Match match = matches.get(i);

            boolean isHome = match.getHomeTeam() != null &&
                             match.getHomeTeam().equalsIgnoreCase(teamName);

            // Get card data
            Integer teamYellows = isHome ? match.getHomeYellowCards() : match.getAwayYellowCards();
            Integer teamReds = isHome ? match.getHomeRedCards() : match.getAwayRedCards();
            Integer oppYellows = isHome ? match.getAwayYellowCards() : match.getHomeYellowCards();

            if (teamYellows != null) {
                totalYellowCards += teamYellows;
                if (isHome) {
                    homeYellowCards += teamYellows;
                    homeMatchCount++;
                } else {
                    awayYellowCards += teamYellows;
                    awayMatchCount++;
                }
                matchesWithCardData++;
            }

            if (teamReds != null) {
                totalRedCards += teamReds;
            }

            if (oppYellows != null) {
                totalOpponentYellowCards += oppYellows;
            }

            // Build recent bookings (first N matches)
            if (i < RECENT_BOOKINGS_COUNT && teamYellows != null) {
                String opponent = isHome ? match.getAwayTeam() : match.getHomeTeam();
                String result = getResultForTeam(match, teamName);

                recentBookings.add(MatchBookingSummary.builder()
                        .matchDate(formatDate(match.getMatchDate()))
                        .opponent(opponent)
                        .isHome(isHome)
                        .yellowCards(teamYellows)
                        .redCards(teamReds != null ? teamReds : 0)
                        .result(result)
                        .build());
            }
        }

        // Calculate averages
        double avgYellowHome = safeDivide(homeYellowCards, homeMatchCount);
        double avgYellowAway = safeDivide(awayYellowCards, awayMatchCount);
        double avgYellowOverall = safeDivide(totalYellowCards, matchesWithCardData);
        double avgRed = safeDivide(totalRedCards, matchesWithCardData);
        double avgOppYellow = safeDivide(totalOpponentYellowCards, matchesWithCardData);
        double cardDiff = avgYellowOverall - avgOppYellow;

        // Determine discipline rating
        String rating = calculateDisciplineRating(avgYellowOverall, avgRed);
        String ratingColor = getDisciplineRatingColor(rating);

        log.debug("Discipline stats for {}: avgYellow={}, avgRed={}, rating={}",
                teamName, avgYellowOverall, avgRed, rating);

        return TeamDisciplineDTO.builder()
                .teamName(teamName)
                .avgYellowCardsHome(roundToTwoDecimals(avgYellowHome))
                .avgYellowCardsAway(roundToTwoDecimals(avgYellowAway))
                .avgYellowCardsOverall(roundToTwoDecimals(avgYellowOverall))
                .avgRedCards(roundToThreeDecimals(avgRed))
                .totalYellowCardsSeason(totalYellowCards)
                .totalRedCardsSeason(totalRedCards)
                .matchesAnalyzed(matchesWithCardData)
                .disciplineRating(rating)
                .ratingColor(ratingColor)
                .recentBookings(recentBookings)
                .avgOpponentYellowCards(roundToTwoDecimals(avgOppYellow))
                .cardDifferential(roundToTwoDecimals(cardDiff))
                .build();
    }

    /**
     * Calculate discipline rating based on card averages.
     */
    private String calculateDisciplineRating(double avgYellowCards, double avgRedCards) {
        // Red cards heavily impact rating
        if (avgRedCards > 0.15) {
            return "Aggressive";
        }

        if (avgYellowCards < EXCELLENT_DISCIPLINE_THRESHOLD) {
            return "Excellent";
        } else if (avgYellowCards <= AVERAGE_DISCIPLINE_THRESHOLD) {
            return "Average";
        } else {
            return "Aggressive";
        }
    }

    /**
     * Get color for discipline rating.
     */
    private String getDisciplineRatingColor(String rating) {
        return switch (rating) {
            case "Excellent" -> "green";
            case "Average" -> "yellow";
            case "Aggressive" -> "red";
            default -> "gray";
        };
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Calculate prediction confidence based on sample sizes.
     */
    private double calculateConfidence(int homeSamples, int awaySamples) {
        int minSamples = Math.min(homeSamples, awaySamples);

        if (minSamples < MIN_MATCHES_FOR_CONFIDENCE) {
            return minSamples / (double) MIN_MATCHES_FOR_CONFIDENCE * 0.5;
        }

        return Math.min(1.0, 0.5 + (minSamples / (double) MAX_MATCHES) * 0.5);
    }

    /**
     * Get match result from team's perspective.
     */
    private String getResultForTeam(Match match, String teamName) {
        if (match.getFullTimeResult() == null) return "-";

        boolean isHome = match.getHomeTeam() != null &&
                         match.getHomeTeam().equalsIgnoreCase(teamName);

        return switch (match.getFullTimeResult()) {
            case "H" -> isHome ? "W" : "L";
            case "A" -> isHome ? "L" : "W";
            case "D" -> "D";
            default -> "-";
        };
    }

    /**
     * Format date for display.
     */
    private String formatDate(LocalDate date) {
        if (date == null) return "Unknown";
        return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    /**
     * Resolve team name via centralized validation service.
     */
    private String resolveTeamName(String teamName, LocalDate beforeDate) {
        return teamValidationService.resolveTeamName(teamName);
    }

    /**
     * Validate team name.
     */
    private void validateTeamName(String teamName, String fieldName) {
        if (teamName == null || teamName.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " name cannot be null or empty");
        }
    }

    /**
     * Safe division preventing divide-by-zero.
     */
    private double safeDivide(int numerator, int denominator) {
        if (denominator == 0) return 0.0;
        return (double) numerator / denominator;
    }

    /**
     * Round to two decimal places.
     */
    private double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Round to three decimal places.
     */
    private double roundToThreeDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.round(value * 1000.0) / 1000.0;
    }
}

