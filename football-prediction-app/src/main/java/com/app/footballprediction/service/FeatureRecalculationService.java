package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.common.service.FeatureEngineeringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for recalculating derived analytics (features) used by the prediction model.
 *
 * After a new match result is recorded, features like teamFormScore, expectedGoalsAverage,
 * shotQualityScore, defensiveStrengthScore, and cardAggressionIndex must be recomputed
 * using the updated historical data.
 *
 * This ensures the model always uses the latest data for predictions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureRecalculationService {

    private final MatchRepository matchRepository;
    private final FeatureEngineeringService featureEngineeringService;

    /**
     * Recalculate features for all teams involved in recently completed matches.
     * Uses the FeatureEngineeringService to rebuild feature vectors using updated historical data.
     *
     * @param recentMatches list of recently completed matches whose teams need feature updates
     * @return map of team name to their recalculated feature snapshot
     */
    public Map<String, FeatureSnapshot> recalculateForRecentMatches(List<Match> recentMatches) {
        if (recentMatches == null || recentMatches.isEmpty()) {
            log.debug("No recent matches to recalculate features for");
            return Map.of();
        }

        // Collect unique teams from recent matches
        List<String> teams = new ArrayList<>();
        for (Match match : recentMatches) {
            if (!teams.contains(match.getHomeTeam())) teams.add(match.getHomeTeam());
            if (!teams.contains(match.getAwayTeam())) teams.add(match.getAwayTeam());
        }

        log.info("Recalculating features for {} teams involved in {} recent matches",
                teams.size(), recentMatches.size());

        Map<String, FeatureSnapshot> results = new LinkedHashMap<>();

        for (String team : teams) {
            try {
                FeatureSnapshot snapshot = recalculateTeamFeatures(team);
                results.put(team, snapshot);
                log.debug("Recalculated features for {}: formScore={}, xGAvg={}, " +
                          "defensiveStrength={}, cardIndex={}",
                        team,
                        String.format("%.2f", snapshot.teamFormScore),
                        String.format("%.2f", snapshot.expectedGoalsAverage),
                        String.format("%.2f", snapshot.defensiveStrengthScore),
                        String.format("%.2f", snapshot.cardAggressionIndex));
            } catch (Exception e) {
                log.warn("Failed to recalculate features for {}: {}", team, e.getMessage());
            }
        }

        log.info("Feature recalculation complete for {} teams", results.size());
        return results;
    }

    /**
     * Recalculate all derived features for a specific team.
     * Creates a dummy match scenario to leverage the existing FeatureEngineeringService.
     */
    public FeatureSnapshot recalculateTeamFeatures(String teamName) {
        LocalDate today = LocalDate.now();

        // Get recent matches for the team
        List<Match> recentMatches = matchRepository.findByTeamBeforeDateIgnoreCase(teamName, today);

        if (recentMatches.isEmpty()) {
            log.debug("No matches found for team {}, returning default features", teamName);
            return FeatureSnapshot.defaults(teamName);
        }

        // Calculate team form score (points from last 5 matches / max possible 15)
        double teamFormScore = calculateTeamFormScore(recentMatches, teamName);

        // Calculate expected goals average (goals scored avg from recent matches)
        double expectedGoalsAverage = calculateExpectedGoalsAverage(recentMatches, teamName);

        // Calculate shot quality score (shots on target / total shots ratio)
        double shotQualityScore = calculateShotQualityScore(recentMatches, teamName);

        // Calculate defensive strength score (clean sheets ratio + low goals conceded)
        double defensiveStrengthScore = calculateDefensiveStrengthScore(recentMatches, teamName);

        // Calculate card aggression index (cards per match average)
        double cardAggressionIndex = calculateCardAggressionIndex(recentMatches, teamName);

        return new FeatureSnapshot(
                teamName,
                teamFormScore,
                expectedGoalsAverage,
                shotQualityScore,
                defensiveStrengthScore,
                cardAggressionIndex
        );
    }

    /**
     * Calculate team form score: points from last N matches normalized to 0-1 scale.
     */
    private double calculateTeamFormScore(List<Match> matches, String teamName) {
        int window = Math.min(5, matches.size());
        double totalPoints = 0;
        for (int i = 0; i < window; i++) {
            totalPoints += matches.get(i).getPointsForTeam(teamName);
        }
        return totalPoints / (window * 3.0); // Normalize to 0-1
    }

    /**
     * Calculate expected goals average: average goals scored in recent matches.
     */
    private double calculateExpectedGoalsAverage(List<Match> matches, String teamName) {
        int window = Math.min(10, matches.size());
        double totalGoals = 0;
        for (int i = 0; i < window; i++) {
            totalGoals += matches.get(i).getGoalsScoredByTeam(teamName);
        }
        return totalGoals / window;
    }

    /**
     * Calculate shot quality score: shots on target / total shots ratio.
     */
    private double calculateShotQualityScore(List<Match> matches, String teamName) {
        int window = Math.min(10, matches.size());
        double totalShots = 0;
        double totalShotsOnTarget = 0;
        int matchesWithData = 0;

        for (int i = 0; i < window; i++) {
            Match m = matches.get(i);
            boolean isHome = m.getHomeTeam() != null && m.getHomeTeam().equalsIgnoreCase(teamName);

            Integer shots = isHome ? m.getHomeShots() : m.getAwayShots();
            Integer shotsOnTarget = isHome ? m.getHomeShotsOnTarget() : m.getAwayShotsOnTarget();

            if (shots != null && shots > 0) {
                totalShots += shots;
                totalShotsOnTarget += (shotsOnTarget != null ? shotsOnTarget : 0);
                matchesWithData++;
            }
        }

        return matchesWithData > 0 && totalShots > 0 ? totalShotsOnTarget / totalShots : 0.3; // Default 30%
    }

    /**
     * Calculate defensive strength score based on goals conceded and clean sheets.
     */
    private double calculateDefensiveStrengthScore(List<Match> matches, String teamName) {
        int window = Math.min(10, matches.size());
        double totalConceded = 0;
        int cleanSheets = 0;

        for (int i = 0; i < window; i++) {
            int conceded = matches.get(i).getGoalsConcededByTeam(teamName);
            totalConceded += conceded;
            if (conceded == 0) cleanSheets++;
        }

        // Combine: lower conceded avg + higher clean sheet ratio = better defense
        double avgConceded = totalConceded / window;
        double cleanSheetRatio = (double) cleanSheets / window;

        // Score: higher is better defense. Max when avgConceded=0, cleanSheetRatio=1
        return Math.max(0, 1.0 - (avgConceded / 3.0)) * 0.6 + cleanSheetRatio * 0.4;
    }

    /**
     * Calculate card aggression index: average total cards per match for the team.
     */
    private double calculateCardAggressionIndex(List<Match> matches, String teamName) {
        int window = Math.min(10, matches.size());
        double totalCards = 0;
        int matchesWithData = 0;

        for (int i = 0; i < window; i++) {
            Match m = matches.get(i);
            boolean isHome = m.getHomeTeam() != null && m.getHomeTeam().equalsIgnoreCase(teamName);

            Integer yellows = isHome ? m.getHomeYellowCards() : m.getAwayYellowCards();
            Integer reds = isHome ? m.getHomeRedCards() : m.getAwayRedCards();

            if (yellows != null || reds != null) {
                totalCards += (yellows != null ? yellows : 0) + (reds != null ? reds * 2 : 0);
                matchesWithData++;
            }
        }

        return matchesWithData > 0 ? totalCards / matchesWithData : 2.0; // Default 2 cards/match
    }

    /**
     * Snapshot of recalculated features for a team.
     */
    public record FeatureSnapshot(
            String teamName,
            double teamFormScore,
            double expectedGoalsAverage,
            double shotQualityScore,
            double defensiveStrengthScore,
            double cardAggressionIndex
    ) {
        public static FeatureSnapshot defaults(String teamName) {
            return new FeatureSnapshot(teamName, 0.5, 1.2, 0.3, 0.5, 2.0);
        }
    }
}

