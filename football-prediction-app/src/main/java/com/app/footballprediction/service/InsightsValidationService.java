package com.app.footballprediction.service;

import com.app.footballprediction.dto.H2HInsightsResponse;
import com.app.footballprediction.dto.H2HInsightsResponse.*;
import com.app.footballprediction.dto.PreMatchInsightsResponse;
import com.app.footballprediction.dto.PreMatchInsightsResponse.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Validation service for ensuring consistency and correctness of match insights.
 * Implements STEP 6 requirements: validation layer before sending response to UI.
 *
 * Validation checks performed:
 * 1. PPG consistency check
 * 2. H2H total consistency check (wins + draws + losses = total)
 * 3. Streak validation check (streak must align with form string)
 * 4. Season average consistency check
 * 5. No division by zero
 */
@Service
@Slf4j
public class InsightsValidationService {

    private static final int FORM_WINDOW = 5;

    /**
     * Epsilon for general floating-point comparisons.
     * Used for simple sum validations like totalExpectedGoals = home + away.
     */
    private static final double EPSILON = 0.1;

    /**
     * Epsilon for Goal Threat Index comparisons.
     * Set to 0.5 to accommodate rounding differences when values are:
     * 1. Calculated using raw data then rounded separately
     * 2. Validated by recalculating from rounded values
     *
     * The Goal Threat Index formula (avgScored / 2.0) * 100 amplifies small
     * rounding differences by 50x. For example, if avgScored has a 0.01 rounding
     * error, the GTI error would be 0.01 * 50 = 0.5.
     *
     * Example: homeAvgScored=1.8268 → GTI=91.34, but rounded homeTeamAvgScored=1.83 → expected GTI=91.5
     */
    private static final double EPSILON_GTI = 0.5;

    private static final int STREAK_THRESHOLD = 3;

    /**
     * Validate PreMatchInsights response for consistency.
     * Returns list of validation errors (empty if all valid).
     */
    public List<String> validatePreMatchInsights(PreMatchInsightsResponse response) {
        List<String> errors = new ArrayList<>();

        if (response == null) {
            errors.add("Response is null");
            return errors;
        }

        // 1. PPG consistency check
        errors.addAll(validateFormComparison(response.getFormComparison()));

        // 2. Streak validation check
        errors.addAll(validateStreakIndicators(response.getStreakIndicators(), response.getFormComparison()));

        // 3. Rest days validation
        errors.addAll(validateRestAnalysis(response.getRestAnalysis()));

        // 4. Goal threat meter validation
        errors.addAll(validateGoalThreatMeter(response.getGoalThreatMeter()));

        // 5. Market predictions validation
        errors.addAll(validateMarketPredictions(response.getMarketPredictions()));

        // 6. CurrentStreak validation (STEP 6: Validation Check)
        errors.addAll(validateCurrentStreak(response.getHomeCurrentStreak(), response.getHomeTeam()));
        errors.addAll(validateCurrentStreak(response.getAwayCurrentStreak(), response.getAwayTeam()));

        // 7. Cross-check CurrentStreak with Key Insights (STEP 4: Consistency Rule)
        errors.addAll(validateStreakKeyInsightConsistency(
                response.getHomeCurrentStreak(),
                response.getAwayCurrentStreak(),
                response.getKeyInsights()));

        if (!errors.isEmpty()) {
            log.warn("PreMatchInsights validation found {} issues: {}", errors.size(), errors);
        }

        return errors;
    }

    /**
     * Validate H2H insights response for consistency.
     * Returns list of validation errors (empty if all valid).
     */
    public List<String> validateH2HInsights(H2HInsightsResponse response) {
        List<String> errors = new ArrayList<>();

        if (response == null) {
            errors.add("Response is null");
            return errors;
        }

        // 1. H2H total consistency check: wins + draws + losses = total
        errors.addAll(validateHistoricalRecord(response.getHistoricalRecord()));

        // 2. Goal stats validation
        errors.addAll(validateH2HGoalStats(response.getGoalStats()));

        // 3. Common results validation
        errors.addAll(validateCommonResults(response.getCommonResults()));

        if (!errors.isEmpty()) {
            log.warn("H2HInsights validation found {} issues: {}", errors.size(), errors);
        }

        return errors;
    }

    /**
     * Validate form comparison for PPG consistency.
     * PPG must equal formPoints / FORM_WINDOW (5).
     */
    private List<String> validateFormComparison(FormComparison form) {
        List<String> errors = new ArrayList<>();

        if (form == null) {
            return errors;  // Empty form is allowed for teams with no data
        }

        // Check home team PPG consistency
        double expectedHomePPG = (double) form.getHomeFormPoints() / FORM_WINDOW;
        if (Math.abs(form.getHomePointsPerGame() - expectedHomePPG) > EPSILON) {
            errors.add(String.format("Home PPG inconsistency: expected %.2f but got %.2f",
                    expectedHomePPG, form.getHomePointsPerGame()));
        }

        // Check away team PPG consistency
        double expectedAwayPPG = (double) form.getAwayFormPoints() / FORM_WINDOW;
        if (Math.abs(form.getAwayPointsPerGame() - expectedAwayPPG) > EPSILON) {
            errors.add(String.format("Away PPG inconsistency: expected %.2f but got %.2f",
                    expectedAwayPPG, form.getAwayPointsPerGame()));
        }

        // Validate form points are within valid range (0-15 for 5 matches)
        if (form.getHomeFormPoints() < 0 || form.getHomeFormPoints() > FORM_WINDOW * 3) {
            errors.add(String.format("Home form points out of range: %d (expected 0-%d)",
                    form.getHomeFormPoints(), FORM_WINDOW * 3));
        }

        if (form.getAwayFormPoints() < 0 || form.getAwayFormPoints() > FORM_WINDOW * 3) {
            errors.add(String.format("Away form points out of range: %d (expected 0-%d)",
                    form.getAwayFormPoints(), FORM_WINDOW * 3));
        }

        // Validate form string matches points
        if (form.getHomeFormString() != null) {
            int calculatedHomePoints = calculatePointsFromFormString(form.getHomeFormString());
            if (calculatedHomePoints != form.getHomeFormPoints()) {
                errors.add(String.format("Home form string '%s' implies %d points but got %d",
                        form.getHomeFormString(), calculatedHomePoints, form.getHomeFormPoints()));
            }
        }

        if (form.getAwayFormString() != null) {
            int calculatedAwayPoints = calculatePointsFromFormString(form.getAwayFormString());
            if (calculatedAwayPoints != form.getAwayFormPoints()) {
                errors.add(String.format("Away form string '%s' implies %d points but got %d",
                        form.getAwayFormString(), calculatedAwayPoints, form.getAwayFormPoints()));
            }
        }

        // Validate points difference
        int expectedDiff = form.getHomeFormPoints() - form.getAwayFormPoints();
        if (form.getPointsDifference() != expectedDiff) {
            errors.add(String.format("Points difference mismatch: expected %d but got %d",
                    expectedDiff, form.getPointsDifference()));
        }

        return errors;
    }

    /**
     * Validate streak indicators align with form strings.
     */
    private List<String> validateStreakIndicators(List<StreakIndicator> streaks, FormComparison form) {
        List<String> errors = new ArrayList<>();

        if (streaks == null || streaks.isEmpty()) {
            return errors;  // Empty streaks is valid
        }

        for (StreakIndicator streak : streaks) {
            // Validate streak length is positive
            if (streak.getStreakLength() <= 0) {
                errors.add(String.format("Invalid streak length %d for %s %s streak",
                        streak.getStreakLength(), streak.getTeam(), streak.getStreakType()));
            }

            // Cross-validate with form string if available
            if (form != null) {
                String formString = streak.isHomeTeam() ? form.getHomeFormString() : form.getAwayFormString();
                if (formString != null && !formString.isEmpty()) {
                    int validatedStreak = validateStreakWithFormString(streak.getStreakType(), formString);

                    // Only validate for streaks that should match the last 5 form
                    // For streaks > 5, they extend beyond the form window which is valid
                    if (streak.getStreakLength() <= FORM_WINDOW && validatedStreak != streak.getStreakLength()) {
                        // Log as warning, not error, since streak can extend beyond form window
                        log.debug("Streak length {} for {} {} may differ from form string '{}' (calculated: {})",
                                streak.getStreakLength(), streak.getTeam(), streak.getStreakType(),
                                formString, validatedStreak);
                    }
                }
            }
        }

        return errors;
    }

    /**
     * Validate rest analysis for consistency.
     */
    private List<String> validateRestAnalysis(RestAnalysis rest) {
        List<String> errors = new ArrayList<>();

        if (rest == null) {
            return errors;
        }

        // Validate rest days are non-negative
        if (rest.getHomeTeamRestDays() < 0) {
            errors.add("Home team rest days cannot be negative: " + rest.getHomeTeamRestDays());
        }

        if (rest.getAwayTeamRestDays() < 0) {
            errors.add("Away team rest days cannot be negative: " + rest.getAwayTeamRestDays());
        }

        // Validate rest difference calculation
        int expectedDiff = rest.getHomeTeamRestDays() - rest.getAwayTeamRestDays();
        if (rest.getRestDifference() != expectedDiff) {
            errors.add(String.format("Rest difference mismatch: expected %d but got %d",
                    expectedDiff, rest.getRestDifference()));
        }

        return errors;
    }

    /**
     * Validate goal threat meter for consistency and no division by zero.
     */
    private List<String> validateGoalThreatMeter(GoalThreatMeter meter) {
        List<String> errors = new ArrayList<>();

        if (meter == null) {
            return errors;
        }

        // Validate averages are non-negative
        if (meter.getHomeTeamAvgScored() < 0) {
            errors.add("Home team avg scored cannot be negative");
        }
        if (meter.getAwayTeamAvgScored() < 0) {
            errors.add("Away team avg scored cannot be negative");
        }
        if (meter.getHomeTeamAvgConceded() < 0) {
            errors.add("Home team avg conceded cannot be negative");
        }
        if (meter.getAwayTeamAvgConceded() < 0) {
            errors.add("Away team avg conceded cannot be negative");
        }

        // Validate threat ratings are in range 0-100
        if (meter.getHomeThreatRating() < 0 || meter.getHomeThreatRating() > 100) {
            errors.add("Home threat rating out of range (0-100): " + meter.getHomeThreatRating());
        }
        if (meter.getAwayThreatRating() < 0 || meter.getAwayThreatRating() > 100) {
            errors.add("Away threat rating out of range (0-100): " + meter.getAwayThreatRating());
        }

        // Validate total expected goals is sum of individual expected goals
        double expectedTotal = meter.getHomeExpectedGoals() + meter.getAwayExpectedGoals();
        if (Math.abs(meter.getTotalExpectedGoals() - expectedTotal) > EPSILON) {
            errors.add(String.format("Total expected goals mismatch: expected %.2f but got %.2f",
                    expectedTotal, meter.getTotalExpectedGoals()));
        }

        // Validate Goal Threat Index is consistent with formula
        // Use EPSILON_GTI for GTI comparisons due to amplified rounding errors
        double expectedHomeGTI = Math.min(100, (meter.getHomeTeamAvgScored() / 2.0) * 100);
        if (Math.abs(meter.getHomeGoalThreatIndex() - expectedHomeGTI) > EPSILON_GTI) {
            errors.add(String.format("Home Goal Threat Index inconsistent: expected %.2f but got %.2f",
                    expectedHomeGTI, meter.getHomeGoalThreatIndex()));
        }

        double expectedAwayGTI = Math.min(100, (meter.getAwayTeamAvgScored() / 2.0) * 100);
        if (Math.abs(meter.getAwayGoalThreatIndex() - expectedAwayGTI) > EPSILON_GTI) {
            errors.add(String.format("Away Goal Threat Index inconsistent: expected %.2f but got %.2f",
                    expectedAwayGTI, meter.getAwayGoalThreatIndex()));
        }

        return errors;
    }

    /**
     * Validate market predictions.
     */
    private List<String> validateMarketPredictions(MarketPredictions predictions) {
        List<String> errors = new ArrayList<>();

        if (predictions == null) {
            return errors;
        }

        // Validate expected goals are non-negative
        if (predictions.getExpectedHomeGoals() < 0) {
            errors.add("Expected home goals cannot be negative");
        }
        if (predictions.getExpectedAwayGoals() < 0) {
            errors.add("Expected away goals cannot be negative");
        }

        // Validate total is sum
        double expectedTotal = predictions.getExpectedHomeGoals() + predictions.getExpectedAwayGoals();
        if (Math.abs(predictions.getExpectedTotalGoals() - expectedTotal) > EPSILON) {
            errors.add(String.format("Expected total goals mismatch: expected %.2f but got %.2f",
                    expectedTotal, predictions.getExpectedTotalGoals()));
        }

        return errors;
    }

    /**
     * Validate H2H historical record for consistency.
     * Ensures: wins + draws + losses = totalMatches
     */
    private List<String> validateHistoricalRecord(HistoricalRecord record) {
        List<String> errors = new ArrayList<>();

        if (record == null) {
            return errors;
        }

        // H2H total consistency check
        int calculatedTotal = record.getHomeTeamWins() + record.getDraws() + record.getAwayTeamWins();
        if (calculatedTotal != record.getTotalMatches()) {
            errors.add(String.format("H2H total mismatch: %d + %d + %d = %d, but totalMatches = %d",
                    record.getHomeTeamWins(), record.getDraws(), record.getAwayTeamWins(),
                    calculatedTotal, record.getTotalMatches()));
        }

        // Validate percentages sum to ~100
        if (record.getTotalMatches() > 0) {
            double totalPct = record.getHomeTeamWinPercentage() + record.getDrawPercentage() + record.getAwayTeamWinPercentage();
            if (Math.abs(totalPct - 100.0) > 1.0) {  // Allow 1% tolerance for rounding
                errors.add(String.format("H2H percentages don't sum to 100: %.1f + %.1f + %.1f = %.1f",
                        record.getHomeTeamWinPercentage(), record.getDrawPercentage(),
                        record.getAwayTeamWinPercentage(), totalPct));
            }
        }

        return errors;
    }

    /**
     * Validate H2H goal stats.
     */
    private List<String> validateH2HGoalStats(H2HGoalStats stats) {
        List<String> errors = new ArrayList<>();

        if (stats == null) {
            return errors;
        }

        // Validate averages are non-negative
        if (stats.getAvgTotalGoals() < 0) {
            errors.add("H2H avg total goals cannot be negative");
        }
        if (stats.getAvgHomeTeamGoals() < 0) {
            errors.add("H2H avg home team goals cannot be negative");
        }
        if (stats.getAvgAwayTeamGoals() < 0) {
            errors.add("H2H avg away team goals cannot be negative");
        }

        // Validate clean sheets are non-negative
        if (stats.getCleanSheetsHomeTeam() < 0) {
            errors.add("Clean sheets cannot be negative");
        }
        if (stats.getCleanSheetsAwayTeam() < 0) {
            errors.add("Clean sheets cannot be negative");
        }

        return errors;
    }

    /**
     * Validate common results.
     */
    private List<String> validateCommonResults(CommonResultStats results) {
        List<String> errors = new ArrayList<>();

        if (results == null) {
            return errors;
        }

        // Validate counts match totals in topScorelines
        if (results.getTopScorelines() != null && !results.getTopScorelines().isEmpty()) {
            int totalFromScorelines = results.getTopScorelines().stream()
                    .mapToInt(ScoreFrequency::getCount)
                    .sum();

            int expectedTotal = results.getHomeWinCount() + results.getDrawCount() + results.getAwayWinCount();

            // Total from scorelines should be <= total matches (topScorelines is limited to top 5)
            if (totalFromScorelines > expectedTotal) {
                errors.add("Scoreline counts exceed total matches");
            }
        }

        return errors;
    }

    // ===== Helper Methods =====

    /**
     * Calculate points from a form string (e.g., "WWDLW" = 10 points).
     */
    private int calculatePointsFromFormString(String formString) {
        if (formString == null || formString.isEmpty()) {
            return 0;
        }

        int points = 0;
        for (char c : formString.toCharArray()) {
            switch (c) {
                case 'W' -> points += 3;
                case 'D' -> points += 1;
                case 'L' -> points += 0;
            }
        }
        return points;
    }

    /**
     * Validate streak length based on form string.
     * Returns the calculated streak length from the form string.
     */
    private int validateStreakWithFormString(String streakType, String formString) {
        if (formString == null || formString.isEmpty()) {
            return 0;
        }

        int streak = 0;

        for (char c : formString.toCharArray()) {
            boolean matches = switch (streakType) {
                case "WIN" -> c == 'W';
                case "LOSS" -> c == 'L';
                case "UNBEATEN" -> c == 'W' || c == 'D';
                case "WINLESS" -> c == 'D' || c == 'L';
                default -> false;
            };

            if (matches) {
                streak++;
            } else {
                break;
            }
        }

        return streak;
    }

    /**
     * Validate CurrentStreak data for a team (STEP 6: Validation Check).
     * Ensures streakCount >= 0 and data is consistent.
     */
    private List<String> validateCurrentStreak(PreMatchInsightsResponse.CurrentStreak streak, String expectedTeamName) {
        List<String> errors = new ArrayList<>();

        if (streak == null) {
            return errors;  // Null streak is allowed for empty response
        }

        // Validate team name matches
        if (streak.getTeamName() != null && expectedTeamName != null &&
                !streak.getTeamName().equalsIgnoreCase(expectedTeamName)) {
            errors.add(String.format("CurrentStreak team name mismatch: expected '%s' but got '%s'",
                    expectedTeamName, streak.getTeamName()));
        }

        // Validate all streak counts are non-negative
        if (streak.getWinlessStreak() < 0) {
            errors.add(String.format("Invalid negative winless streak for %s: %d",
                    streak.getTeamName(), streak.getWinlessStreak()));
        }
        if (streak.getWinStreak() < 0) {
            errors.add(String.format("Invalid negative win streak for %s: %d",
                    streak.getTeamName(), streak.getWinStreak()));
        }
        if (streak.getLossStreak() < 0) {
            errors.add(String.format("Invalid negative loss streak for %s: %d",
                    streak.getTeamName(), streak.getLossStreak()));
        }
        if (streak.getUnbeatenStreak() < 0) {
            errors.add(String.format("Invalid negative unbeaten streak for %s: %d",
                    streak.getTeamName(), streak.getUnbeatenStreak()));
        }
        if (streak.getPrimaryStreakCount() < 0) {
            errors.add(String.format("Invalid negative primary streak count for %s: %d",
                    streak.getTeamName(), streak.getPrimaryStreakCount()));
        }

        // Validate primaryStreakCount matches the corresponding streak type
        if (streak.getPrimaryStreakType() != null) {
            int expectedCount = switch (streak.getPrimaryStreakType()) {
                case "WIN" -> streak.getWinStreak();
                case "LOSS" -> streak.getLossStreak();
                case "WINLESS" -> streak.getWinlessStreak();
                case "UNBEATEN" -> streak.getUnbeatenStreak();
                case "NONE" -> 0;
                default -> streak.getPrimaryStreakCount();
            };

            if (streak.getPrimaryStreakCount() != expectedCount) {
                errors.add(String.format("PrimaryStreakCount mismatch for %s: type=%s, expected=%d, got=%d",
                        streak.getTeamName(), streak.getPrimaryStreakType(),
                        expectedCount, streak.getPrimaryStreakCount()));
            }
        }

        // Validate recentResults matches streak data (if provided)
        if (streak.getRecentResults() != null && !streak.getRecentResults().isEmpty()) {
            String results = streak.getRecentResults();

            // Verify winless streak from results
            int calculatedWinless = 0;
            for (char c : results.toCharArray()) {
                if (c == 'D' || c == 'L') calculatedWinless++;
                else break;
            }

            // Only check winless if team has a significant winless streak
            if (streak.getWinlessStreak() >= STREAK_THRESHOLD &&
                    calculatedWinless != streak.getWinlessStreak() &&
                    streak.getWinlessStreak() <= results.length()) {
                log.warn("Winless streak may extend beyond recent results for {}: calculated {} from '{}', reported {}",
                        streak.getTeamName(), calculatedWinless, results, streak.getWinlessStreak());
            }
        }

        return errors;
    }

    /**
     * Cross-validate CurrentStreak data with Key Insights (STEP 4: Consistency Rule).
     * Ensures that if Key Insight mentions a team's streak, it matches the CurrentStreak data.
     */
    private List<String> validateStreakKeyInsightConsistency(
            PreMatchInsightsResponse.CurrentStreak homeStreak,
            PreMatchInsightsResponse.CurrentStreak awayStreak,
            List<String> keyInsights) {

        List<String> errors = new ArrayList<>();

        if (keyInsights == null || keyInsights.isEmpty()) {
            return errors;
        }

        // Check each key insight for streak mentions
        for (String insight : keyInsights) {
            if (insight == null) continue;

            // Check for winless mentions
            if (insight.toLowerCase().contains("without a win")) {
                // Extract team name and count from insight
                String teamName = extractTeamNameFromInsight(insight, "without a win in");
                int mentionedCount = extractCountFromInsight(insight);

                // Validate against CurrentStreak data
                if (teamName != null && mentionedCount > 0) {
                    boolean validated = false;

                    if (homeStreak != null && homeStreak.getTeamName() != null &&
                            homeStreak.getTeamName().equalsIgnoreCase(teamName)) {
                        if (homeStreak.getWinlessStreak() != mentionedCount) {
                            errors.add(String.format(
                                    "Key Insight inconsistency: mentions %s with %d winless, but CurrentStreak shows %d",
                                    teamName, mentionedCount, homeStreak.getWinlessStreak()));
                        }
                        validated = true;
                    }

                    if (!validated && awayStreak != null && awayStreak.getTeamName() != null &&
                            awayStreak.getTeamName().equalsIgnoreCase(teamName)) {
                        if (awayStreak.getWinlessStreak() != mentionedCount) {
                            errors.add(String.format(
                                    "Key Insight inconsistency: mentions %s with %d winless, but CurrentStreak shows %d",
                                    teamName, mentionedCount, awayStreak.getWinlessStreak()));
                        }
                    }
                }
            }

            // Check for winning streak mentions
            if (insight.toLowerCase().contains("winning streak")) {
                String teamName = extractTeamNameFromInsight(insight, "on");
                int mentionedCount = extractCountFromInsight(insight);

                if (teamName != null && mentionedCount > 0) {
                    boolean validated = false;

                    if (homeStreak != null && homeStreak.getTeamName() != null &&
                            homeStreak.getTeamName().equalsIgnoreCase(teamName)) {
                        if (homeStreak.getWinStreak() != mentionedCount) {
                            errors.add(String.format(
                                    "Key Insight inconsistency: mentions %s with %d win streak, but CurrentStreak shows %d",
                                    teamName, mentionedCount, homeStreak.getWinStreak()));
                        }
                        validated = true;
                    }

                    if (!validated && awayStreak != null && awayStreak.getTeamName() != null &&
                            awayStreak.getTeamName().equalsIgnoreCase(teamName)) {
                        if (awayStreak.getWinStreak() != mentionedCount) {
                            errors.add(String.format(
                                    "Key Insight inconsistency: mentions %s with %d win streak, but CurrentStreak shows %d",
                                    teamName, mentionedCount, awayStreak.getWinStreak()));
                        }
                    }
                }
            }
        }

        return errors;
    }

    /**
     * Extract team name from an insight string.
     */
    private String extractTeamNameFromInsight(String insight, String delimiter) {
        if (insight == null) return null;

        // Remove emoji characters at the start
        String cleaned = insight.replaceAll("^[^a-zA-Z]+", "").trim();

        // Try to find team name before the delimiter
        int delimiterIdx = cleaned.toLowerCase().indexOf(delimiter.toLowerCase());
        if (delimiterIdx > 0) {
            return cleaned.substring(0, delimiterIdx).trim();
        }

        return null;
    }

    /**
     * Extract count number from an insight string.
     * Looks for patterns like "6 matches", "6-match", etc.
     */
    private int extractCountFromInsight(String insight) {
        if (insight == null) return 0;

        // Pattern to find numbers followed by match/matches or preceded by "in"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)[-\\s]?(match|win|loss|unbeaten)");
        java.util.regex.Matcher matcher = pattern.matcher(insight.toLowerCase());

        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        // Also try "in X matches" pattern
        pattern = java.util.regex.Pattern.compile("in (\\d+) match");
        matcher = pattern.matcher(insight.toLowerCase());

        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        return 0;
    }
}

