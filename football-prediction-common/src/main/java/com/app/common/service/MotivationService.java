package com.app.common.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.common.util.SeasonHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * Service for calculating team motivation levels based on league position and context.
 *
 * <p><strong>Motivation Calculation Logic:</strong></p>
 * <ul>
 *   <li>Position 1-3 AND points gap to leader &lt;10: motivation = 10 (Title fight)</li>
 *   <li>Position 4-6 AND gap to top 4 &lt;8: motivation = 9 (Top 4 fight)</li>
 *   <li>Position 7-10 AND gap to Europe &lt;10: motivation = 7 (European push)</li>
 *   <li>Position 11-14 AND safe (&gt;8 points from relegation): motivation = 4 (Mid-table comfort)</li>
 *   <li>Position 15-20 AND in danger: motivation = 10 (Survival fight)</li>
 *   <li>Mathematically relegated: motivation = 1 (Nothing to play for)</li>
 * </ul>
 *
 * <p>The motivation score reflects how much a team has to play for at a given point
 * in the season, which can significantly impact performance.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MotivationService {

    private final MatchRepository matchRepository;

    /** Default motivation when insufficient data */
    private static final int DEFAULT_MOTIVATION = 5;

    /** Total matches in a Premier League season */
    private static final int TOTAL_SEASON_MATCHES = 38;

    /** Points for a win */
    private static final int POINTS_FOR_WIN = 3;


    /** Top 4 positions qualify for Champions League */
    private static final int TOP_4_POSITION = 4;

    /** Positions 5-6 typically qualify for Europa League */
    private static final int EUROPA_POSITION = 6;

    /** Positions 18-20 get relegated */
    private static final int RELEGATION_POSITION = 18;

    /**
     * Calculate motivation level for a team at a specific match date.
     *
     * @param teamName  The team to calculate motivation for
     * @param matchDate The date to calculate motivation as of
     * @return Motivation score (0-10), with 10 being highest motivation
     */
    public int calculateMotivation(String teamName, LocalDate matchDate) {
        if (teamName == null || matchDate == null) {
            return DEFAULT_MOTIVATION;
        }

        String season = deriveSeason(matchDate);
        log.debug("Calculating motivation for {} on {} (season: {})", teamName, matchDate, season);

        // Get current standings
        Map<String, TeamStanding> standings = calculateDetailedStandingsAsOfDate(season, matchDate);

        if (standings.isEmpty() || !standings.containsKey(teamName)) {
            log.debug("No standings data for {}, returning default motivation", teamName);
            return DEFAULT_MOTIVATION;
        }

        TeamStanding teamStanding = standings.get(teamName);
        int position = teamStanding.position;
        int points = teamStanding.points;

        // Calculate context metrics
        int gamesPlayed = countGamesPlayed(teamName, season, matchDate);
        int gamesRemaining = TOTAL_SEASON_MATCHES - gamesPlayed;
        int maxPossiblePoints = points + (gamesRemaining * POINTS_FOR_WIN);

        // Get key position points for comparison
        int leaderPoints = getPointsAtPosition(standings, 1);
        int top4Points = getPointsAtPosition(standings, TOP_4_POSITION);
        int europaPoints = getPointsAtPosition(standings, EUROPA_POSITION);
        int relegationZonePoints = getPointsAtPosition(standings, RELEGATION_POSITION);
        int safePoints = getPointsAtPosition(standings, RELEGATION_POSITION - 1); // Position 17

        int pointsToLeader = leaderPoints - points;
        int pointsToTop4 = top4Points - points;
        int pointsToEuropa = europaPoints - points;
        int pointsAboveRelegation = points - relegationZonePoints;

        log.debug("Position: {}, Points: {}, Games Remaining: {}, Max Possible: {}",
                position, points, gamesRemaining, maxPossiblePoints);
        log.debug("Gap to leader: {}, Gap to top 4: {}, Gap to Europa: {}, Above relegation: {}",
                pointsToLeader, pointsToTop4, pointsToEuropa, pointsAboveRelegation);

        // Check if mathematically relegated
        if (isRelgated(position, maxPossiblePoints, safePoints, gamesRemaining)) {
            log.debug("{} is mathematically relegated - motivation: 1", teamName);
            return 1;
        }

        // Title fight (positions 1-3 with realistic chance)
        if (position <= 3 && pointsToLeader < 10) {
            log.debug("{} fighting for title - motivation: 10", teamName);
            return 10;
        }

        // Relegation fight (positions 15-20 in danger or already in zone)
        // pointsAboveRelegation <= 0 means team is IN the relegation zone
        if (position >= 15 || (position >= RELEGATION_POSITION && pointsAboveRelegation <= 0)) {
            log.debug("{} fighting for survival (pos={}, above rel={}) - motivation: 10", 
                     teamName, position, pointsAboveRelegation);
            return 10;
        }

        // Teams just above relegation zone but in danger
        if (position >= 14 && pointsAboveRelegation <= 5) {
            log.debug("{} in relegation danger zone - motivation: 10", teamName);
            return 10;
        }

        // Top 4 fight (positions 4-6 with realistic chance)
        if (position >= 4 && position <= 6 && pointsToTop4 < 8) {
            log.debug("{} fighting for top 4 - motivation: 9", teamName);
            return 9;
        }

        // European push (positions 7-10 with realistic chance)
        if (position >= 7 && position <= 10 && pointsToEuropa < 10) {
            log.debug("{} pushing for Europe - motivation: 7", teamName);
            return 7;
        }

        // Safe mid-table (positions 11-14, comfortable gap to relegation)
        if (position >= 11 && position <= 14 && pointsAboveRelegation > 8) {
            log.debug("{} safe mid-table - motivation: 4", teamName);
            return 4;
        }

        // Lower mid-table but still safe
        if (position >= 11 && position <= 14 && pointsAboveRelegation > 5) {
            log.debug("{} lower mid-table, relatively safe - motivation: 5", teamName);
            return 5;
        }

        // Neither fighting for anything nor in danger - "nothing to play for"
        if (position >= 11 && position <= 13 && pointsAboveRelegation > 3) {
            log.debug("{} mid-table comfort zone - motivation: 3", teamName);
            return 3;
        }

        // Default case
        log.debug("{} defaulting to mid-level motivation", teamName);
        return DEFAULT_MOTIVATION;
    }

    /**
     * Check if a team is mathematically relegated.
     */
    private boolean isRelgated(int position, int maxPossiblePoints, int safePoints, int gamesRemaining) {
        // Only check if in bottom 3 and there are few games left
        if (position >= RELEGATION_POSITION && gamesRemaining <= 10) {
            // If max possible points can't catch the safe zone
            return maxPossiblePoints < safePoints - 3; // Give some margin for calculation
        }
        return false;
    }

    /**
     * Get points of team at a specific position.
     */
    private int getPointsAtPosition(Map<String, TeamStanding> standings, int position) {
        return standings.values().stream()
                .filter(s -> s.position == position)
                .findFirst()
                .map(s -> s.points)
                .orElse(0);
    }

    /**
     * Count games played by a team in the season before the given date.
     */
    private int countGamesPlayed(String teamName, String season, LocalDate beforeDate) {
        List<Match> matches = matchRepository.findBySeasonBeforeDateForTable(season, beforeDate);
        return (int) matches.stream()
                .filter(m -> teamName.equalsIgnoreCase(m.getHomeTeam()) ||
                        teamName.equalsIgnoreCase(m.getAwayTeam()))
                .count();
    }

    /**
     * Calculate detailed standings including points and goal difference.
     */
    private Map<String, TeamStanding> calculateDetailedStandingsAsOfDate(String season, LocalDate asOfDate) {
        List<Match> matches = matchRepository.findBySeasonBeforeDateForTable(season, asOfDate);

        if (matches.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, TeamStanding> standingsMap = new HashMap<>();

        for (Match match : matches) {
            String homeTeam = match.getHomeTeam();
            String awayTeam = match.getAwayTeam();

            standingsMap.computeIfAbsent(homeTeam, k -> new TeamStanding(homeTeam));
            standingsMap.computeIfAbsent(awayTeam, k -> new TeamStanding(awayTeam));

            TeamStanding homeSt = standingsMap.get(homeTeam);
            TeamStanding awaySt = standingsMap.get(awayTeam);

            int homeGoals = match.getFullTimeHomeGoals() != null ? match.getFullTimeHomeGoals() : 0;
            int awayGoals = match.getFullTimeAwayGoals() != null ? match.getFullTimeAwayGoals() : 0;

            homeSt.update(homeGoals, awayGoals);
            awaySt.update(awayGoals, homeGoals);
        }

        // Sort and assign positions
        List<TeamStanding> sortedList = standingsMap.values().stream()
                .sorted((e1, e2) -> {
                    int cmp = Integer.compare(e2.points, e1.points);
                    if (cmp != 0) return cmp;
                    cmp = Integer.compare(e2.goalDifference, e1.goalDifference);
                    if (cmp != 0) return cmp;
                    cmp = Integer.compare(e2.goalsFor, e1.goalsFor);
                    if (cmp != 0) return cmp;
                    return e1.teamName.compareTo(e2.teamName);
                })
                .toList();

        // Assign positions
        for (int i = 0; i < sortedList.size(); i++) {
            sortedList.get(i).position = i + 1;
        }

        return standingsMap;
    }

    /**
     * Derive season string from a date.
     * Delegates to shared {@link SeasonHelper}.
     */
    private String deriveSeason(LocalDate date) {
        return SeasonHelper.deriveSeason(date);
    }

    /**
     * Internal class for tracking team standings with full detail.
     */
    private static class TeamStanding {
        String teamName;
        int points = 0;
        int goalsFor = 0;
        int goalsAgainst = 0;
        int goalDifference = 0;
        int position = 0;

        TeamStanding(String teamName) {
            this.teamName = teamName;
        }

        void update(int scored, int conceded) {
            this.goalsFor += scored;
            this.goalsAgainst += conceded;
            this.goalDifference = this.goalsFor - this.goalsAgainst;

            if (scored > conceded) {
                this.points += 3;
            } else if (scored == conceded) {
                this.points += 1;
            }
        }
    }
}

