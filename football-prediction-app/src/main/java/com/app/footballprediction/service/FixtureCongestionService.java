package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.CongestionComparisonDTO;
import com.app.footballprediction.dto.FixtureCongestionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for analysing fixture congestion and fatigue impact.
 *
 * <p>Calculates days between matches, a fatigue index (0–100),
 * and historical win rates segmented by rest-day buckets.</p>
 *
 * <p><strong>Fatigue Index scale:</strong></p>
 * <ul>
 *   <li>100 = very congested (&lt; 3 days average between matches)</li>
 *   <li> 50 = normal (4–5 days)</li>
 *   <li>  0 = well rested (&gt; 7 days)</li>
 * </ul>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FixtureCongestionService {

    private final MatchRepository matchRepository;
    private final TeamValidationService teamValidationService;

    // ══════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ══════════════════════════════════════════════════════════════════════

    /** Recent matches to derive the gap timeline. */
    private static final int RECENT_MATCHES = 6; // 6 matches → 5 gaps

    /** Historical matches for win-rate-by-rest analysis. */
    private static final int HISTORY_MATCHES = 80;

    /** Short rest threshold (days, exclusive). */
    private static final int SHORT_REST_THRESHOLD = 3;

    /** Normal rest upper bound (days, inclusive). */
    private static final int NORMAL_REST_UPPER = 5;

    /** Minimum fatigue-index difference to declare an advantage. */
    private static final int ADVANTAGE_THRESHOLD = 10;

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Analyse fixture congestion for a single team.
     *
     * @param teamName team to analyse
     * @param asOfDate reference date (typically match-day or today)
     * @return congestion metrics
     */
    @Cacheable(value = "fixtureCongestion", key = "#teamName + '_' + #asOfDate")
    public FixtureCongestionDTO analyzeFixtureCongestion(String teamName, LocalDate asOfDate) {
        log.info("Analysing fixture congestion for {} as-of {}", teamName, asOfDate);

        validateTeamName(teamName);

        LocalDate before = asOfDate.plusDays(1);
        String resolved = resolveTeamName(teamName.trim(), before);

        List<Match> allMatches = matchRepository.findByTeamBeforeDate(resolved, before);
        if (allMatches.isEmpty()) {
            log.warn("No matches found for team: {}", resolved);
            return FixtureCongestionDTO.empty(resolved);
        }

        // ── Recent gaps ──────────────────────────────────────────────────
        List<Match> recent = allMatches.stream()
                .filter(m -> m.getMatchDate() != null)
                .limit(RECENT_MATCHES)
                .toList();

        List<Integer> gaps = new ArrayList<>();
        for (int i = 0; i < recent.size() - 1; i++) {
            long days = ChronoUnit.DAYS.between(recent.get(i + 1).getMatchDate(),
                    recent.get(i).getMatchDate());
            gaps.add((int) Math.abs(days));
        }

        double avgDays = gaps.isEmpty() ? 0
                : gaps.stream().mapToInt(Integer::intValue).average().orElse(0);

        int fatigueIndex = calculateFatigueIndex(avgDays);
        String fatigueLevel = classifyFatigue(fatigueIndex);

        LocalDate lastMatchDate = recent.isEmpty() ? null : recent.getFirst().getMatchDate();
        int daysSinceLast = lastMatchDate != null
                ? (int) ChronoUnit.DAYS.between(lastMatchDate, asOfDate)
                : 0;

        // ── Historical win-rate by rest bucket ───────────────────────────
        List<Match> history = allMatches.stream()
                .filter(m -> m.getMatchDate() != null && m.getFullTimeResult() != null)
                .limit(HISTORY_MATCHES)
                .toList();

        int shortWins = 0, shortTotal = 0;
        int normalWins = 0, normalTotal = 0;
        int longWins = 0, longTotal = 0;

        for (int i = 0; i < history.size() - 1; i++) {
            Match current = history.get(i);
            Match previous = history.get(i + 1);
            long rest = Math.abs(ChronoUnit.DAYS.between(previous.getMatchDate(),
                    current.getMatchDate()));
            boolean won = current.getPointsForTeam(resolved) == 3;

            if (rest < SHORT_REST_THRESHOLD) {
                shortTotal++;
                if (won) shortWins++;
            } else if (rest <= NORMAL_REST_UPPER) {
                normalTotal++;
                if (won) normalWins++;
            } else {
                longTotal++;
                if (won) longWins++;
            }
        }

        double winShort = pct(shortWins, shortTotal);
        double winNormal = pct(normalWins, normalTotal);
        double winLong = pct(longWins, longTotal);

        String impact = buildImpactSummary(winShort, winNormal, winLong,
                shortTotal, normalTotal, longTotal);

        log.info("Congestion for {}: avgDays={}, fatigue={} ({}), shortWin={}%, longWin={}%",
                resolved,
                String.format("%.1f", avgDays), fatigueIndex, fatigueLevel,
                String.format("%.1f", winShort), String.format("%.1f", winLong));

        return FixtureCongestionDTO.builder()
                .teamName(resolved)
                .daysBetweenMatches(gaps)
                .avgDaysBetween(round2(avgDays))
                .fatigueIndex(fatigueIndex)
                .fatigueLevel(fatigueLevel)
                .winRateShortRest(round2(winShort))
                .winRateNormalRest(round2(winNormal))
                .winRateLongRest(round2(winLong))
                .matchesShortRest(shortTotal)
                .matchesNormalRest(normalTotal)
                .matchesLongRest(longTotal)
                .lastMatchDate(lastMatchDate)
                .daysSinceLastMatch(daysSinceLast)
                .matchesAnalyzed(recent.size())
                .totalHistoricalMatches(history.size() - 1) // gaps = n-1
                .impactSummary(impact)
                .build();
    }

    /**
     * Compare fixture congestion between two teams.
     */
    public CongestionComparisonDTO compareFixtureCongestion(String homeTeam, String awayTeam, LocalDate asOfDate) {
        log.info("Comparing congestion: {} vs {} as-of {}", homeTeam, awayTeam, asOfDate);

        FixtureCongestionDTO home = analyzeFixtureCongestion(homeTeam, asOfDate);
        FixtureCongestionDTO away = analyzeFixtureCongestion(awayTeam, asOfDate);

        int diff = home.getFatigueIndex() - away.getFatigueIndex(); // positive = home MORE fatigued

        String advantageTeam;
        String summary;

        if (Math.abs(diff) < ADVANTAGE_THRESHOLD) {
            advantageTeam = "neutral";
            summary = String.format("Similar schedule intensity — %s %.1f days avg vs %s %.1f days avg",
                    home.getTeamName(), home.getAvgDaysBetween(),
                    away.getTeamName(), away.getAvgDaysBetween());
        } else if (diff > 0) {
            // home is more fatigued → away has advantage
            advantageTeam = "away";
            summary = String.format("%s well rested (%.1f days avg) vs %s congested (%.1f days avg)",
                    away.getTeamName(), away.getAvgDaysBetween(),
                    home.getTeamName(), home.getAvgDaysBetween());
        } else {
            advantageTeam = "home";
            summary = String.format("%s well rested (%.1f days avg) vs %s congested (%.1f days avg)",
                    home.getTeamName(), home.getAvgDaysBetween(),
                    away.getTeamName(), away.getAvgDaysBetween());
        }

        return CongestionComparisonDTO.builder()
                .home(home)
                .away(away)
                .advantageTeam(advantageTeam)
                .advantageSummary(summary)
                .fatigueDifference(diff)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Map average-days-between-matches → 0-100 fatigue index.
     * <ul>
     *   <li>≤ 2 days → 100</li>
     *   <li>  3 days →  85</li>
     *   <li>  4 days →  65</li>
     *   <li>  5 days →  50</li>
     *   <li>  6 days →  30</li>
     *   <li>  7 days →  15</li>
     *   <li>≥ 8 days →   0</li>
     * </ul>
     */
    private int calculateFatigueIndex(double avgDays) {
        if (avgDays <= 0) return 0;
        if (avgDays <= 2) return 100;
        if (avgDays >= 8) return 0;
        // Linear interpolation between 2 → 100 and 8 → 0
        double index = 100.0 * (8.0 - avgDays) / 6.0;
        return (int) Math.round(Math.max(0, Math.min(100, index)));
    }

    private String classifyFatigue(int index) {
        if (index >= 70) return "High";
        if (index >= 35) return "Medium";
        return "Low";
    }

    private String buildImpactSummary(double winShort, double winNormal, double winLong,
                                      int shortN, int normalN, int longN) {
        // Compare rested vs congested
        if (longN >= 3 && shortN >= 3) {
            double diff = winLong - winShort;
            if (diff > 0) {
                return String.format("Win rate drops %.0f%% when <3 days rest (%.0f%% vs %.0f%% well-rested)",
                        diff, winShort, winLong);
            } else if (diff < -2) {
                return String.format("Team performs %.0f%% better in congested periods (%.0f%% vs %.0f%%)",
                        Math.abs(diff), winShort, winLong);
            }
            return String.format("Rest days have minimal impact (%.0f%% short vs %.0f%% long rest)",
                    winShort, winLong);
        }
        if (normalN >= 3) {
            return String.format("Normal rest win rate: %.0f%% (%d matches analysed)", winNormal, normalN);
        }
        return "Insufficient rest-day data for comparison";
    }

    private double pct(int numerator, int denominator) {
        if (denominator == 0) return 0;
        return (double) numerator / denominator * 100.0;
    }

    private double round2(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0;
        return Math.round(v * 100.0) / 100.0;
    }

    private String resolveTeamName(String teamName, LocalDate before) {
        return teamValidationService.resolveTeamName(teamName);
    }

    private void validateTeamName(String teamName) {
        if (teamName == null || teamName.trim().isEmpty()) {
            throw new IllegalArgumentException("Team name cannot be null or empty");
        }
    }
}

