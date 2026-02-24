package com.app.common.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * Service for calculating league positions based on match history.
 * Used by feature engineering to determine team positions at any point in time.
 *
 * <p><strong>IMPORTANT: Season-Based Filtering</strong></p>
 * <p>All standings calculations are scoped to a specific season to align with
 * official Premier League statistical methodology. Cross-season data is excluded.</p>
 *
 * <p>Standings are calculated using standard football rules:</p>
 * <ul>
 *   <li>Primary: Points (3 for win, 1 for draw, 0 for loss)</li>
 *   <li>Secondary: Goal Difference (goals scored - goals conceded)</li>
 *   <li>Tertiary: Goals Scored</li>
 *   <li>Quaternary: Team Name (alphabetical)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaguePositionService {

    private final MatchRepository matchRepository;

    /** Default position for teams not in standings (mid-table) */
    private static final int DEFAULT_POSITION = 10;

    /**
     * Get a team's league position as of a specific date within a season.
     * Calculates standings from matches in the same season before the given date.
     *
     * @param teamName The team to get position for
     * @param season   Season identifier (e.g., "2024-25")
     * @param asOfDate The date to calculate position as of (exclusive)
     * @return Position (1-based, 1=top), or DEFAULT_POSITION if team not found
     */
    public int getTeamPositionAsOfDate(String teamName, String season, LocalDate asOfDate) {
        if (teamName == null || season == null || asOfDate == null) {
            return DEFAULT_POSITION;
        }

        Map<String, Integer> standings = calculateStandingsAsOfDate(season, asOfDate);
        return standings.getOrDefault(teamName, DEFAULT_POSITION);
    }

    /**
     * Get a team's league position as of a specific date (legacy method).
     * Calculates standings from all matches before the given date (no season filter).
     *
     * @param teamName The team to get position for
     * @param asOfDate The date to calculate position as of (exclusive)
     * @return Position (1-based, 1=top), or DEFAULT_POSITION if team not found
     * @deprecated Use {@link #getTeamPositionAsOfDate(String, String, LocalDate)} instead
     */
    @Deprecated
    public int getTeamPositionAsOfDate(String teamName, LocalDate asOfDate) {
        if (teamName == null || asOfDate == null) {
            return DEFAULT_POSITION;
        }

        Map<String, Integer> standings = calculateStandingsAsOfDateNoSeason(asOfDate);
        return standings.getOrDefault(teamName, DEFAULT_POSITION);
    }

    /**
     * Calculate league standings as of a specific date within a season.
     * Uses season-filtered data from the repository.
     *
     * @param season   Season identifier (e.g., "2024-25")
     * @param asOfDate The date to calculate standings up to (exclusive)
     * @return Map of team name to position (1-based)
     */
    public Map<String, Integer> calculateStandingsAsOfDate(String season, LocalDate asOfDate) {
        log.debug("Calculating standings for season {} as of date: {}", season, asOfDate);

        // Get completed matches in this season before the date (uses new repo method)
        List<Match> matches = matchRepository.findBySeasonBeforeDateForTable(season, asOfDate);

        if (matches.isEmpty()) {
            log.debug("No completed matches found for season {} before date: {}", season, asOfDate);
            return Collections.emptyMap();
        }

        return calculatePositionsFromMatches(matches);
    }

    /**
     * Calculate league standings as of a specific date (no season filter).
     * Used for legacy compatibility.
     *
     * @param asOfDate The date to calculate standings up to (exclusive)
     * @return Map of team name to position (1-based)
     */
    public Map<String, Integer> calculateStandingsAsOfDateNoSeason(LocalDate asOfDate) {
        log.debug("Calculating standings as of date (no season filter): {}", asOfDate);

        List<Match> allMatches = matchRepository.findAllByOrderByMatchDateAsc();

        List<Match> matches = allMatches.stream()
                .filter(m -> m.getMatchDate() != null)
                .filter(m -> m.getMatchDate().isBefore(asOfDate))
                .filter(m -> m.getFullTimeResult() != null)
                .toList();

        if (matches.isEmpty()) {
            log.debug("No completed matches found before date: {}", asOfDate);
            return Collections.emptyMap();
        }

        return calculatePositionsFromMatches(matches);
    }

    /**
     * Calculate positions from a list of matches.
     * Shared logic for both season-filtered and unfiltered calculations.
     */
    private Map<String, Integer> calculatePositionsFromMatches(List<Match> matches) {
        Map<String, TeamStandingData> standingsMap = new HashMap<>();

        for (Match match : matches) {
            String homeTeam = match.getHomeTeam();
            String awayTeam = match.getAwayTeam();

            standingsMap.computeIfAbsent(homeTeam, k -> new TeamStandingData());
            standingsMap.computeIfAbsent(awayTeam, k -> new TeamStandingData());

            TeamStandingData homeSt = standingsMap.get(homeTeam);
            TeamStandingData awaySt = standingsMap.get(awayTeam);

            int homeGoals = match.getFullTimeHomeGoals() != null ? match.getFullTimeHomeGoals() : 0;
            int awayGoals = match.getFullTimeAwayGoals() != null ? match.getFullTimeAwayGoals() : 0;

            homeSt.update(homeGoals, awayGoals);
            awaySt.update(awayGoals, homeGoals);
        }

        // Sort by points DESC, goal difference DESC, goals scored DESC, name ASC
        List<Map.Entry<String, TeamStandingData>> sortedList = standingsMap.entrySet().stream()
                .sorted((e1, e2) -> {
                    int cmp = Integer.compare(e2.getValue().points, e1.getValue().points);
                    if (cmp != 0) return cmp;
                    cmp = Integer.compare(e2.getValue().goalDifference, e1.getValue().goalDifference);
                    if (cmp != 0) return cmp;
                    cmp = Integer.compare(e2.getValue().goalsFor, e1.getValue().goalsFor);
                    if (cmp != 0) return cmp;
                    return e1.getKey().compareTo(e2.getKey());
                })
                .toList();

        // Build position map (1-based)
        Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < sortedList.size(); i++) {
            positions.put(sortedList.get(i).getKey(), i + 1);
        }

        return positions;
    }

    /**
     * Internal class for calculating standings.
     */
    private static class TeamStandingData {
        int points = 0;
        int goalsFor = 0;
        int goalsAgainst = 0;
        int goalDifference = 0;

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

