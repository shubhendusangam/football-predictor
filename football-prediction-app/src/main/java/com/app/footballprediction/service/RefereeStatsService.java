package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.config.CacheConfig;
import com.app.footballprediction.dto.RefereeImpactDTO;
import com.app.footballprediction.dto.RefereeStats;
import com.app.footballprediction.dto.RefereeStatsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for computing referee statistics from match history.
 * Provides insights into referee tendencies for cards, fouls, and result distributions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefereeStatsService {

    private final MatchRepository matchRepository;

    // League-wide averages for normalization (Premier League historical data)
    private static final double LEAGUE_AVG_YELLOW_CARDS = 3.5;
    private static final double LEAGUE_AVG_RED_CARDS = 0.08;
    private static final double LEAGUE_AVG_GOALS = 2.7;
    private static final double LEAGUE_AVG_HOME_WIN_PCT = 46.2;
    private static final double LEAGUE_AVG_FOULS = 22.0;

    // Thresholds for referee classification
    private static final double STRICT_THRESHOLD = 4.5;
    private static final double LENIENT_THRESHOLD = 3.0;

    // ── Original RefereeStats methods (preserved for backward compatibility) ──

    /**
     * Get statistics for a specific referee (original DTO).
     *
     * @param refereeName Referee name exactly as stored in database
     * @return RefereeStats with aggregated statistics
     */
    @Cacheable(value = CacheConfig.CACHE_REFEREE_STATS, key = "#refereeName")
    public RefereeStats getRefereeStats(String refereeName) {
        if (refereeName == null || refereeName.isBlank()) {
            return RefereeStats.empty(refereeName);
        }

        List<Match> matches = getMatchesByReferee(refereeName);

        if (matches.isEmpty()) {
            log.info("No matches found for referee: {}", refereeName);
            return RefereeStats.empty(refereeName);
        }

        return calculateStats(refereeName, matches);
    }

    /**
     * Get all referee names in the database.
     */
    @Cacheable(value = CacheConfig.CACHE_ALL_REFEREES)
    public List<String> getAllReferees() {
        List<Match> allMatches = matchRepository.findAllByOrderByMatchDateDesc();
        return allMatches.stream()
                .map(Match::getReferee)
                .filter(Objects::nonNull)
                .filter(r -> !r.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Get statistics for all referees (original DTO).
     */
    @Cacheable(value = CacheConfig.CACHE_ALL_REFEREE_STATS)
    public List<RefereeStats> getAllRefereeStats() {
        List<String> referees = getAllReferees();
        return referees.stream()
                .map(this::getRefereeStats)
                .filter(s -> s.getMatchesOfficiated() >= 5)
                .sorted(Comparator.comparingInt(RefereeStats::getMatchesOfficiated).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get top N strictest referees (by cards per match).
     */
    public List<RefereeStats> getStrictestReferees(int limit) {
        return getAllRefereeStats().stream()
                .sorted(Comparator.comparingDouble(RefereeStats::getStrictnessIndex).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get top N most lenient referees.
     */
    public List<RefereeStats> getMostLenientReferees(int limit) {
        return getAllRefereeStats().stream()
                .sorted(Comparator.comparingDouble(RefereeStats::getStrictnessIndex))
                .limit(limit)
                .collect(Collectors.toList());
    }

    // ── New Comprehensive RefereeStatsDTO methods ──

    /**
     * Calculate comprehensive referee stats (new DTO format).
     *
     * @param refereeName Referee name
     * @return RefereeStatsDTO with full statistics
     */
    @Cacheable(value = CacheConfig.CACHE_REFEREE_COMPREHENSIVE, key = "#refereeName?.toLowerCase()")
    public RefereeStatsDTO calculateRefereeStats(String refereeName) {
        if (refereeName == null || refereeName.isBlank()) {
            return RefereeStatsDTO.empty(refereeName);
        }

        List<Match> matches = getMatchesByReferee(refereeName);
        if (matches.isEmpty()) {
            log.info("No matches found for referee: {}", refereeName);
            return RefereeStatsDTO.empty(refereeName);
        }

        return buildComprehensiveStats(refereeName, matches);
    }

    /**
     * Get comprehensive stats for all referees.
     *
     * @return List of RefereeStatsDTO sorted by matches officiated
     */
    @Cacheable(value = CacheConfig.CACHE_ALL_REFEREE_COMPREHENSIVE)
    public List<RefereeStatsDTO> getAllRefereeComprehensiveStats() {
        List<String> referees = getAllReferees();
        return referees.stream()
                .map(this::calculateRefereeStats)
                .filter(s -> s.getMatchesOfficiated() >= 5)
                .sorted(Comparator.comparingInt(RefereeStatsDTO::getMatchesOfficiated).reversed())
                .collect(Collectors.toList());
    }

    // ── League Summary ──

    /**
     * Get league-wide referee summary statistics.
     */
    @Cacheable(value = CacheConfig.CACHE_REFEREE_SUMMARY)
    public Map<String, Object> getLeagueSummary() {
        List<RefereeStatsDTO> all = getAllRefereeComprehensiveStats();
        if (all.isEmpty()) {
            return Map.of("totalReferees", 0);
        }

        int total = all.size();
        double avgCards = all.stream().mapToDouble(RefereeStatsDTO::getAvgYellowCards).average().orElse(0);
        double avgGoals = all.stream().mapToDouble(RefereeStatsDTO::getAvgGoalsPerGame).average().orElse(0);
        double avgFouls = all.stream().mapToDouble(RefereeStatsDTO::getAvgFoulsPerGame).average().orElse(0);
        double avgHomeWin = all.stream().mapToDouble(RefereeStatsDTO::getHomeWinPercentage).average().orElse(0);
        long strictCount = all.stream().filter(r -> "Strict".equals(r.getRefType())).count();
        long balancedCount = all.stream().filter(r -> "Balanced".equals(r.getRefType())).count();
        long lenientCount = all.stream().filter(r -> "Lenient".equals(r.getRefType())).count();
        int totalMatches = all.stream().mapToInt(RefereeStatsDTO::getMatchesOfficiated).sum();

        // Top 3 strictest
        List<Map<String, Object>> strictest = all.stream()
                .sorted(Comparator.comparingDouble(RefereeStatsDTO::getAvgYellowCards).reversed())
                .limit(3)
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", r.getRefereeName());
                    m.put("avgCards", round(r.getAvgYellowCards() + r.getAvgRedCards()));
                    m.put("type", r.getRefType());
                    return m;
                })
                .collect(Collectors.toList());

        // Top 3 most lenient
        List<Map<String, Object>> lenient = all.stream()
                .sorted(Comparator.comparingDouble(RefereeStatsDTO::getAvgYellowCards))
                .limit(3)
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", r.getRefereeName());
                    m.put("avgCards", round(r.getAvgYellowCards() + r.getAvgRedCards()));
                    m.put("type", r.getRefType());
                    return m;
                })
                .collect(Collectors.toList());

        // Most experienced
        RefereeStatsDTO mostExp = all.stream()
                .max(Comparator.comparingInt(RefereeStatsDTO::getMatchesOfficiated))
                .orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalReferees", total);
        result.put("totalMatchesCovered", totalMatches);
        result.put("leagueAvgCardsPerGame", round(avgCards));
        result.put("leagueAvgGoalsPerGame", round(avgGoals));
        result.put("leagueAvgFoulsPerGame", round(avgFouls));
        result.put("leagueAvgHomeWinPct", round(avgHomeWin));
        result.put("strictCount", strictCount);
        result.put("balancedCount", balancedCount);
        result.put("lenientCount", lenientCount);
        result.put("top3Strictest", strictest);
        result.put("top3Lenient", lenient);
        if (mostExp != null) {
            result.put("mostExperienced", Map.of(
                    "name", mostExp.getRefereeName(),
                    "matches", mostExp.getMatchesOfficiated()
            ));
        }
        return result;
    }

    // ── Referee Comparison ──

    /**
     * Compare two referees side by side.
     */
    @Cacheable(value = CacheConfig.CACHE_REFEREE_COMPARE,
               key = "#ref1?.toLowerCase() + '_' + #ref2?.toLowerCase()")
    public Map<String, Object> compareReferees(String ref1, String ref2) {
        RefereeStatsDTO stats1 = calculateRefereeStats(ref1);
        RefereeStatsDTO stats2 = calculateRefereeStats(ref2);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("referee1", stats1);
        result.put("referee2", stats2);

        // Compute comparison verdicts
        List<Map<String, String>> verdicts = new ArrayList<>();
        addVerdict(verdicts, "Cards per Game",
                stats1.getAvgYellowCards(), stats2.getAvgYellowCards(),
                stats1.getRefereeName(), stats2.getRefereeName(), true);
        addVerdict(verdicts, "Fouls per Game",
                stats1.getAvgFoulsPerGame(), stats2.getAvgFoulsPerGame(),
                stats1.getRefereeName(), stats2.getRefereeName(), true);
        addVerdict(verdicts, "Goals per Game",
                stats1.getAvgGoalsPerGame(), stats2.getAvgGoalsPerGame(),
                stats1.getRefereeName(), stats2.getRefereeName(), false);
        addVerdict(verdicts, "Home Win %",
                stats1.getHomeWinPercentage(), stats2.getHomeWinPercentage(),
                stats1.getRefereeName(), stats2.getRefereeName(), false);
        addVerdict(verdicts, "Red Card %",
                stats1.getRedCardPercentage(), stats2.getRedCardPercentage(),
                stats1.getRefereeName(), stats2.getRefereeName(), true);

        result.put("verdicts", verdicts);
        return result;
    }

    private void addVerdict(List<Map<String, String>> verdicts, String metric,
                            double val1, double val2, String name1, String name2,
                            boolean higherMeansStricter) {
        String winner;
        String note;
        double diff = Math.abs(val1 - val2);
        if (diff < 0.1) {
            winner = "tie";
            note = "Very similar " + metric.toLowerCase();
        } else if ((val1 > val2) == higherMeansStricter) {
            winner = name1;
            note = name1 + " shows higher " + metric.toLowerCase();
        } else {
            winner = name2;
            note = name2 + " shows higher " + metric.toLowerCase();
        }
        Map<String, String> v = new LinkedHashMap<>();
        v.put("metric", metric);
        v.put("winner", winner);
        v.put("note", note);
        verdicts.add(v);
    }

    // ── Referee Impact Prediction ──

    /**
     * Predict referee impact on a specific match.
     *
     * @param refereeName Referee name
     * @param homeTeam    Home team name
     * @param awayTeam    Away team name
     * @return RefereeImpactDTO with predicted impact
     */
    @Cacheable(value = CacheConfig.CACHE_REFEREE_IMPACT,
               key = "#refereeName?.toLowerCase() + '_' + #homeTeam?.toLowerCase() + '_' + #awayTeam?.toLowerCase()")
    public RefereeImpactDTO predictRefereeImpact(String refereeName, String homeTeam, String awayTeam) {
        log.info("Predicting referee impact: referee={}, home={}, away={}", refereeName, homeTeam, awayTeam);

        if (refereeName == null || refereeName.isBlank()) {
            return RefereeImpactDTO.empty(refereeName, homeTeam, awayTeam);
        }

        RefereeStatsDTO refStats = calculateRefereeStats(refereeName);
        if (refStats.getMatchesOfficiated() == 0) {
            return RefereeImpactDTO.empty(refereeName, homeTeam, awayTeam);
        }

        TeamDisciplineInfo homeDiscipline = getTeamDiscipline(homeTeam, true);
        TeamDisciplineInfo awayDiscipline = getTeamDiscipline(awayTeam, false);

        return buildImpactPrediction(refStats, homeTeam, awayTeam, homeDiscipline, awayDiscipline);
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Get all matches officiated by a referee (case-insensitive).
     */
    private List<Match> getMatchesByReferee(String refereeName) {
        List<Match> allMatches = matchRepository.findAllByOrderByMatchDateDesc();
        return allMatches.stream()
                .filter(m -> refereeName.equalsIgnoreCase(m.getReferee()))
                .collect(Collectors.toList());
    }

    /**
     * Build comprehensive referee stats from match data.
     */
    private RefereeStatsDTO buildComprehensiveStats(String refereeName, List<Match> matches) {
        int totalMatches = matches.size();

        // Card statistics
        double totalYellowCards = 0, totalRedCards = 0;
        double totalHomeYellow = 0, totalAwayYellow = 0;
        int matchesWithRedCard = 0, matchesWithCardData = 0;

        // Foul statistics
        double totalFouls = 0, totalHomeFouls = 0, totalAwayFouls = 0;
        int matchesWithFoulData = 0;

        // Goals statistics
        double totalGoals = 0;
        int matchesWithGoalData = 0;
        int over25GoalsMatches = 0;

        // Result statistics
        int homeWins = 0, draws = 0, awayWins = 0, matchesWithResultData = 0;

        // Season tracking
        Set<String> seasons = new TreeSet<>();

        for (Match m : matches) {
            if (m.getSeason() != null && !m.getSeason().isBlank()) {
                seasons.add(m.getSeason());
            }

            // Cards
            if (m.getHomeYellowCards() != null && m.getAwayYellowCards() != null) {
                int hy = m.getHomeYellowCards();
                int ay = m.getAwayYellowCards();
                totalYellowCards += hy + ay;
                totalHomeYellow += hy;
                totalAwayYellow += ay;
                matchesWithCardData++;
            }
            if (m.getHomeRedCards() != null && m.getAwayRedCards() != null) {
                int hr = m.getHomeRedCards();
                int ar = m.getAwayRedCards();
                totalRedCards += hr + ar;
                if (hr > 0 || ar > 0) {
                    matchesWithRedCard++;
                }
            }

            // Fouls
            if (m.getHomeFouls() != null && m.getAwayFouls() != null) {
                int hf = m.getHomeFouls();
                int af = m.getAwayFouls();
                totalFouls += hf + af;
                totalHomeFouls += hf;
                totalAwayFouls += af;
                matchesWithFoulData++;
            }

            // Goals
            if (m.getFullTimeHomeGoals() != null && m.getFullTimeAwayGoals() != null) {
                int goals = m.getFullTimeHomeGoals() + m.getFullTimeAwayGoals();
                totalGoals += goals;
                matchesWithGoalData++;
                if (goals > 2) over25GoalsMatches++;
            }

            // Results
            if (m.getFullTimeResult() != null) {
                matchesWithResultData++;
                switch (m.getFullTimeResult()) {
                    case "H" -> homeWins++;
                    case "D" -> draws++;
                    case "A" -> awayWins++;
                }
            }
        }

        // Calculate averages
        double avgYellow = matchesWithCardData > 0 ? totalYellowCards / matchesWithCardData : 0.0;
        double avgRed = matchesWithCardData > 0 ? totalRedCards / matchesWithCardData : 0.0;
        double avgHomeYellow = matchesWithCardData > 0 ? totalHomeYellow / matchesWithCardData : 0.0;
        double avgAwayYellow = matchesWithCardData > 0 ? totalAwayYellow / matchesWithCardData : 0.0;
        double redCardPct = matchesWithCardData > 0 ? (double) matchesWithRedCard / matchesWithCardData * 100.0 : 0.0;

        double avgFouls = matchesWithFoulData > 0 ? totalFouls / matchesWithFoulData : 0.0;
        double avgHomeFouls = matchesWithFoulData > 0 ? totalHomeFouls / matchesWithFoulData : 0.0;
        double avgAwayFouls = matchesWithFoulData > 0 ? totalAwayFouls / matchesWithFoulData : 0.0;

        double avgGoals = matchesWithGoalData > 0 ? totalGoals / matchesWithGoalData : 0.0;
        double over25Pct = matchesWithGoalData > 0 ? (double) over25GoalsMatches / matchesWithGoalData * 100.0 : 0.0;

        double homeWinPct = matchesWithResultData > 0 ? (double) homeWins / matchesWithResultData * 100.0 : LEAGUE_AVG_HOME_WIN_PCT;
        double drawPct = matchesWithResultData > 0 ? (double) draws / matchesWithResultData * 100.0 : 26.8;
        double awayWinPct = matchesWithResultData > 0 ? (double) awayWins / matchesWithResultData * 100.0 : 27.0;

        double totalCardsPerGame = avgYellow + avgRed;
        double cardsPerFoul = (avgFouls > 0) ? totalCardsPerGame / avgFouls : 0.0;

        // Classify referee
        String refType = classifyReferee(totalCardsPerGame);
        String cardStyle = classifyCardStyle(totalCardsPerGame);

        // Strictness index
        double strictnessIndex = calculateStrictnessIndex(avgYellow, avgRed);

        // Data completeness
        double dataCompleteness = totalMatches > 0 ? (double) matchesWithCardData / totalMatches * 100.0 : 0.0;

        log.debug("Comprehensive stats for {}: {} matches, cards/game={}, type={}",
                refereeName, totalMatches, totalCardsPerGame, refType);

        return RefereeStatsDTO.builder()
                .refereeName(refereeName)
                .matchesOfficiated(totalMatches)
                .avgYellowCards(round(avgYellow))
                .avgRedCards(round(avgRed))
                .redCardPercentage(round(redCardPct))
                .avgFoulsPerGame(round(avgFouls))
                .homeWinPercentage(round(homeWinPct))
                .avgGoalsPerGame(round(avgGoals))
                .refType(refType)
                .cardStyle(cardStyle)
                .avgHomeYellowCards(round(avgHomeYellow))
                .avgAwayYellowCards(round(avgAwayYellow))
                .avgHomeFouls(round(avgHomeFouls))
                .avgAwayFouls(round(avgAwayFouls))
                .drawPercentage(round(drawPct))
                .awayWinPercentage(round(awayWinPct))
                .strictnessIndex(round(strictnessIndex))
                .dataCompleteness(round(dataCompleteness))
                .over25GoalsRate(round(over25Pct))
                .cardsPerFoul(round(cardsPerFoul))
                .seasonsActive(seasons.size())
                .build();
    }

    /**
     * Build the referee impact prediction for a match.
     */
    private RefereeImpactDTO buildImpactPrediction(RefereeStatsDTO refStats,
                                                    String homeTeam, String awayTeam,
                                                    TeamDisciplineInfo homeDiscipline,
                                                    TeamDisciplineInfo awayDiscipline) {

        // --- Expected yellow cards ---
        double homeTeamFactor = homeDiscipline.avgYellowCards > 0
                ? homeDiscipline.avgYellowCards / (LEAGUE_AVG_YELLOW_CARDS / 2.0) : 1.0;
        double awayTeamFactor = awayDiscipline.avgYellowCards > 0
                ? awayDiscipline.avgYellowCards / (LEAGUE_AVG_YELLOW_CARDS / 2.0) : 1.0;

        double expectedHomeYellow = refStats.getAvgHomeYellowCards() * homeTeamFactor;
        double expectedAwayYellow = refStats.getAvgAwayYellowCards() * awayTeamFactor;
        double expectedYellow = expectedHomeYellow + expectedAwayYellow;

        // Clamp to reasonable range
        expectedYellow = Math.max(1.0, Math.min(8.0, expectedYellow));
        expectedHomeYellow = Math.max(0.5, Math.min(4.0, expectedHomeYellow));
        expectedAwayYellow = Math.max(0.5, Math.min(4.0, expectedAwayYellow));

        // --- Red card probability ---
        double baseRedProb = refStats.getRedCardPercentage() / 100.0;
        double teamRedFactor = (homeDiscipline.avgRedCards + awayDiscipline.avgRedCards);
        if (LEAGUE_AVG_RED_CARDS * 2 > 0 && teamRedFactor > 0) {
            teamRedFactor = teamRedFactor / (LEAGUE_AVG_RED_CARDS * 2);
        } else {
            teamRedFactor = 1.0;
        }
        double redCardProb = Math.min(1.0, baseRedProb * Math.max(0.5, teamRedFactor));
        if (Double.isNaN(redCardProb) || redCardProb < 0) redCardProb = baseRedProb;

        // --- Home advantage adjustment ---
        double refHomeWinPct = refStats.getHomeWinPercentage();
        double homeAdvAdj = refHomeWinPct - LEAGUE_AVG_HOME_WIN_PCT;

        // --- Expected fouls ---
        double expectedFouls = refStats.getAvgFoulsPerGame();
        if (homeDiscipline.avgFouls > 0 && awayDiscipline.avgFouls > 0) {
            double teamAvgFouls = homeDiscipline.avgFouls + awayDiscipline.avgFouls;
            expectedFouls = (refStats.getAvgFoulsPerGame() + teamAvgFouls) / 2.0;
        }

        // --- Warning ---
        String warning = generateWarning(refStats, homeDiscipline, awayDiscipline, homeTeam, awayTeam);

        // --- Risk level ---
        String riskLevel = determineRiskLevel(expectedYellow, redCardProb);

        // --- Confidence ---
        String confidence = determineConfidence(refStats.getMatchesOfficiated(),
                homeDiscipline.matchCount, awayDiscipline.matchCount);

        return RefereeImpactDTO.builder()
                .refereeName(refStats.getRefereeName())
                .baseStats(refStats)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .expectedYellowCards(round(expectedYellow))
                .expectedHomeYellowCards(round(expectedHomeYellow))
                .expectedAwayYellowCards(round(expectedAwayYellow))
                .redCardProbability(round(redCardProb))
                .homeAdvantageAdjustment(round(homeAdvAdj))
                .expectedFouls(round(expectedFouls))
                .warning(warning)
                .riskLevel(riskLevel)
                .confidence(confidence)
                .build();
    }

    /**
     * Generate warning messages based on referee-team interaction.
     */
    private String generateWarning(RefereeStatsDTO refStats,
                                    TeamDisciplineInfo homeDiscipline,
                                    TeamDisciplineInfo awayDiscipline,
                                    String homeTeam, String awayTeam) {
        List<String> warnings = new ArrayList<>();

        boolean isStrict = "Strict".equals(refStats.getRefType());

        // Strict referee with aggressive teams
        if (isStrict && (homeDiscipline.avgYellowCards > 2.0 || awayDiscipline.avgYellowCards > 2.0)) {
            String aggressiveTeam = homeDiscipline.avgYellowCards > awayDiscipline.avgYellowCards ? homeTeam : awayTeam;
            warnings.add("Strict referee with aggressive " + aggressiveTeam + " - expect high card count");
        }

        // Strict referee with both disciplined teams
        if (isStrict && homeDiscipline.avgYellowCards < 1.5 && awayDiscipline.avgYellowCards < 1.5) {
            warnings.add("Strict referee assigned to two disciplined teams - may still see above-average cards");
        }

        // High red card probability
        if (refStats.getRedCardPercentage() > 15.0) {
            warnings.add("This referee shows " + round(refStats.getRedCardPercentage()) + "% red card rate");
        }

        // Strong home advantage bias
        if (refStats.getHomeWinPercentage() > 55.0 && refStats.getMatchesOfficiated() >= 20) {
            warnings.add("Notable home advantage bias: " + round(refStats.getHomeWinPercentage()) + "% home wins");
        }

        if (warnings.isEmpty()) {
            return null;
        }
        return String.join("; ", warnings);
    }

    /**
     * Determine risk level based on expected card activity.
     */
    private String determineRiskLevel(double expectedYellow, double redCardProb) {
        if (expectedYellow > 5.0 || redCardProb > 0.20) return "High";
        if (expectedYellow > 3.5 || redCardProb > 0.10) return "Medium";
        return "Low";
    }

    /**
     * Determine prediction confidence based on sample sizes.
     */
    private String determineConfidence(int refMatches, int homeMatches, int awayMatches) {
        int minMatches = Math.min(refMatches, Math.min(homeMatches, awayMatches));
        if (minMatches >= 20) return "High";
        if (minMatches >= 10) return "Medium";
        return "Low";
    }

    /**
     * Get team discipline info from recent matches.
     */
    private TeamDisciplineInfo getTeamDiscipline(String teamName, boolean isHome) {
        if (teamName == null || teamName.isBlank()) {
            return TeamDisciplineInfo.empty();
        }

        LocalDate today = LocalDate.now();
        List<Match> matches;
        if (isHome) {
            matches = matchRepository.findHomeMatchesByTeamBeforeDate(teamName, today);
            if (matches.isEmpty()) {
                matches = matchRepository.findHomeMatchesByTeamBeforeDateIgnoreCase(teamName, today);
            }
        } else {
            matches = matchRepository.findAwayMatchesByTeamBeforeDate(teamName, today);
            if (matches.isEmpty()) {
                matches = matchRepository.findAwayMatchesByTeamBeforeDateIgnoreCase(teamName, today);
            }
        }

        // Take most recent 20 matches
        List<Match> recent = matches.stream().limit(20).collect(Collectors.toList());
        if (recent.isEmpty()) {
            return TeamDisciplineInfo.empty();
        }

        double totalYellow = 0, totalRed = 0, totalFouls = 0;
        int cardMatches = 0, foulMatches = 0;

        for (Match m : recent) {
            if (isHome) {
                if (m.getHomeYellowCards() != null) {
                    totalYellow += m.getHomeYellowCards();
                    totalRed += (m.getHomeRedCards() != null ? m.getHomeRedCards() : 0);
                    cardMatches++;
                }
                if (m.getHomeFouls() != null) {
                    totalFouls += m.getHomeFouls();
                    foulMatches++;
                }
            } else {
                if (m.getAwayYellowCards() != null) {
                    totalYellow += m.getAwayYellowCards();
                    totalRed += (m.getAwayRedCards() != null ? m.getAwayRedCards() : 0);
                    cardMatches++;
                }
                if (m.getAwayFouls() != null) {
                    totalFouls += m.getAwayFouls();
                    foulMatches++;
                }
            }
        }

        return new TeamDisciplineInfo(
                cardMatches > 0 ? totalYellow / cardMatches : 0,
                cardMatches > 0 ? totalRed / cardMatches : 0,
                foulMatches > 0 ? totalFouls / foulMatches : 0,
                recent.size());
    }

    /**
     * Classify referee type based on total cards per game.
     */
    private String classifyReferee(double cardsPerGame) {
        if (cardsPerGame > STRICT_THRESHOLD) return "Strict";
        if (cardsPerGame < LENIENT_THRESHOLD) return "Lenient";
        return "Balanced";
    }

    /**
     * Classify card style.
     */
    private String classifyCardStyle(double cardsPerGame) {
        if (cardsPerGame > STRICT_THRESHOLD) return "High";
        if (cardsPerGame < LENIENT_THRESHOLD) return "Low";
        return "Medium";
    }

    // ── Original calculateStats (preserved for backward compatibility) ──

    /**
     * Calculate aggregated statistics for a referee (original DTO format).
     */
    private RefereeStats calculateStats(String refereeName, List<Match> matches) {
        int totalMatches = matches.size();

        double totalYellowCards = 0, totalRedCards = 0;
        int matchesWithCardData = 0;

        double totalGoals = 0, totalHomeGoals = 0, totalAwayGoals = 0;
        int matchesWithGoalData = 0;

        int homeWins = 0, draws = 0, awayWins = 0, matchesWithResultData = 0;

        for (Match m : matches) {
            if (m.getHomeYellowCards() != null && m.getAwayYellowCards() != null) {
                totalYellowCards += m.getHomeYellowCards() + m.getAwayYellowCards();
                matchesWithCardData++;
            }
            if (m.getHomeRedCards() != null && m.getAwayRedCards() != null) {
                totalRedCards += m.getHomeRedCards() + m.getAwayRedCards();
            }

            if (m.getFullTimeHomeGoals() != null && m.getFullTimeAwayGoals() != null) {
                totalHomeGoals += m.getFullTimeHomeGoals();
                totalAwayGoals += m.getFullTimeAwayGoals();
                totalGoals += m.getFullTimeHomeGoals() + m.getFullTimeAwayGoals();
                matchesWithGoalData++;
            }

            if (m.getFullTimeResult() != null) {
                matchesWithResultData++;
                switch (m.getFullTimeResult()) {
                    case "H" -> homeWins++;
                    case "D" -> draws++;
                    case "A" -> awayWins++;
                }
            }
        }

        double avgYellowCards = matchesWithCardData > 0 ? totalYellowCards / matchesWithCardData : 0.0;
        double avgRedCards = matchesWithCardData > 0 ? totalRedCards / matchesWithCardData : 0.0;
        double avgGoalsPerMatch = matchesWithGoalData > 0 ? totalGoals / matchesWithGoalData : 0.0;
        double avgHomeGoals = matchesWithGoalData > 0 ? totalHomeGoals / matchesWithGoalData : 0.0;
        double avgAwayGoals = matchesWithGoalData > 0 ? totalAwayGoals / matchesWithGoalData : 0.0;

        double homeWinRate = matchesWithResultData > 0 ? (double) homeWins / matchesWithResultData : 0.462;
        double drawRate = matchesWithResultData > 0 ? (double) draws / matchesWithResultData : 0.268;
        double awayWinRate = matchesWithResultData > 0 ? (double) awayWins / matchesWithResultData : 0.270;

        double strictnessIndex = calculateStrictnessIndex(avgYellowCards, avgRedCards);
        double dataCompleteness = (double) matchesWithCardData / totalMatches;

        return RefereeStats.builder()
                .refereeName(refereeName).matchesOfficiated(totalMatches)
                .avgYellowCards(round(avgYellowCards)).avgRedCards(round(avgRedCards)).avgFouls(0.0)
                .homeWinRate(round(homeWinRate)).drawRate(round(drawRate)).awayWinRate(round(awayWinRate))
                .avgGoalsPerMatch(round(avgGoalsPerMatch)).avgHomeGoals(round(avgHomeGoals)).avgAwayGoals(round(avgAwayGoals))
                .strictnessIndex(round(strictnessIndex)).dataCompleteness(round(dataCompleteness))
                .build();
    }

    /**
     * Calculate strictness index based on cards relative to league average.
     */
    private double calculateStrictnessIndex(double avgYellowCards, double avgRedCards) {
        double cardScore = (avgYellowCards / LEAGUE_AVG_YELLOW_CARDS) * 0.9
                         + (avgRedCards / LEAGUE_AVG_RED_CARDS) * 0.1;
        return Math.max(0.0, Math.min(1.0, cardScore / 2.0));
    }

    /**
     * Round to 3 decimal places.
     */
    private double round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.0;
        return Math.round(value * 1000.0) / 1000.0;
    }

    // ── Inner helper class ──

    /**
     * Simple holder for team discipline metrics.
     */
    private static class TeamDisciplineInfo {
        final double avgYellowCards;
        final double avgRedCards;
        final double avgFouls;
        final int matchCount;

        TeamDisciplineInfo(double avgYellowCards, double avgRedCards, double avgFouls, int matchCount) {
            this.avgYellowCards = avgYellowCards;
            this.avgRedCards = avgRedCards;
            this.avgFouls = avgFouls;
            this.matchCount = matchCount;
        }

        static TeamDisciplineInfo empty() {
            return new TeamDisciplineInfo(0, 0, 0, 0);
        }
    }
}
