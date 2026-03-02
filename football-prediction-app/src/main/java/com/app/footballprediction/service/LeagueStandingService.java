package com.app.footballprediction.service;

import com.app.common.model.League;
import com.app.common.model.LeagueStanding;
import com.app.common.model.Match;
import com.app.common.model.Team;
import com.app.common.repository.LeagueRepository;
import com.app.common.repository.LeagueStandingRepository;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.TeamRepository;
import com.app.footballprediction.config.CacheConfig;
import com.app.footballprediction.dto.LeagueStandingsResponse;
import com.app.footballprediction.dto.LeagueStandingsResponse.StandingDto;
import com.app.footballprediction.util.SeasonUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing league standings.
 * Provides methods for calculating, updating, and retrieving league tables.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeagueStandingService {

    private final LeagueStandingRepository standingRepository;
    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;

    private static final int CHAMPIONS_LEAGUE_POSITIONS = 4;
    private static final int EUROPA_LEAGUE_POSITIONS = 2;  // 5-6
    private static final int CONFERENCE_LEAGUE_POSITION = 7;
    private static final int RELEGATION_POSITIONS = 3;

    /**
     * Get current league table for a specific league.
     * Cached for performance.
     *
     * @param leagueId League ID
     * @return LeagueStandingsResponse with sorted standings
     */
    @Cacheable(value = CacheConfig.CACHE_STANDINGS, key = "'league_' + #leagueId")
    @Transactional
    public LeagueStandingsResponse getCurrentLeagueTable(Long leagueId) {
        log.info("Fetching current league table for league ID: {}", leagueId);

        // Get league info
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        // Get current season and normalize to standard format
        String currentSeason = league.getCurrentSeason();
        if (currentSeason == null || currentSeason.isEmpty()) {
            currentSeason = getCurrentSeasonString();
        } else {
            currentSeason = SeasonUtils.normalizeSeason(currentSeason);
        }

        return getLeagueTableForSeason(leagueId, currentSeason);
    }

    /**
     * Get league table for a specific season.
     *
     * @param leagueId League ID
     * @param season   Season string (e.g., "2025-26")
     * @return LeagueStandingsResponse
     */
    @Cacheable(value = CacheConfig.CACHE_STANDINGS, key = "'league_' + #leagueId + '_season_' + #season")
    @Transactional
    public LeagueStandingsResponse getLeagueTableForSeason(Long leagueId, String season) {
        // Normalize season to standard format
        String normalizedSeason = SeasonUtils.normalizeSeason(season);
        log.info("Fetching league table for league ID: {} season: {} (normalized from: {})",
                leagueId, normalizedSeason, season);

        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        // Try to get standings with normalized season first
        List<LeagueStanding> standings = standingRepository
                .findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(leagueId, normalizedSeason);

        // If no standings found, try alternate format (for backward compatibility)
        if (standings.isEmpty()) {
            String altSeason = normalizedSeason.replace("-", "/");
            standings = standingRepository
                    .findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(leagueId, altSeason);

            if (!standings.isEmpty()) {
                log.info("Found standings with alternate season format: {}", altSeason);
            }
        }

        // If still no standings exist, calculate from matches
        if (standings.isEmpty()) {
            log.info("No standings found, calculating from match history...");
            standings = calculateStandingsFromMatches(leagueId, normalizedSeason);
        }

        // Map to DTOs
        List<StandingDto> standingDtos = mapToStandingDtos(standings);

        // Return season in standard format (YYYY-YY with dash)
        // Frontend handles display formatting (dash to slash)
        return LeagueStandingsResponse.builder()
                .leagueName(league.getName())
                .leagueCode(league.getCode())
                .season(normalizedSeason)
                .totalTeams(standings.size())
                .standings(standingDtos)
                .lastUpdated(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    /**
     * Calculate standings from match history.
     * Used when standings table is empty or needs refresh.
     */
    @Transactional
    public List<LeagueStanding> calculateStandingsFromMatches(Long leagueId, String season) {
        String normalizedSeason = SeasonUtils.normalizeSeason(season);
        String altSeason = normalizedSeason.replace("-", "/"); // For backward compatibility

        log.info("Calculating standings from matches for league: {}, season: {}", leagueId, normalizedSeason);

        // Get season date range
        LocalDate seasonStart = getSeasonStartDate(normalizedSeason);
        LocalDate seasonEnd = getSeasonEndDate(normalizedSeason);

        // Fetch all matches for the season - filter by both date range AND season field
        List<Match> matches = matchRepository.findAllByOrderByMatchDateAsc().stream()
                .filter(m -> m.getMatchDate() != null)
                .filter(m -> !m.getMatchDate().isBefore(seasonStart) && !m.getMatchDate().isAfter(seasonEnd))
                .filter(m -> m.getSeason() == null ||
                            normalizedSeason.equals(m.getSeason()) ||
                            altSeason.equals(m.getSeason()))
                .filter(m -> m.getFullTimeHomeGoals() != null && m.getFullTimeAwayGoals() != null) // Only finished matches
                .toList();

        log.info("Found {} finished matches for season {}", matches.size(), normalizedSeason);

        // Calculate standings
        Map<String, LeagueStanding> standingsMap = new HashMap<>();

        for (Match match : matches) {
            processMatchForStandings(standingsMap, match, leagueId, normalizedSeason);
        }

        // Convert to list and sort
        List<LeagueStanding> standings = new ArrayList<>(standingsMap.values());
        standings.sort(Comparator
                .comparingInt(LeagueStanding::getPoints).reversed()
                .thenComparingInt(LeagueStanding::getGoalDifference).reversed()
                .thenComparingInt(LeagueStanding::getGoalsFor).reversed()
                .thenComparing(LeagueStanding::getTeamName));

        // Assign positions
        for (int i = 0; i < standings.size(); i++) {
            standings.get(i).setPosition(i + 1);
            standings.get(i).setLastUpdated(LocalDateTime.now());
        }

        // Save to database - delete both formats
        standingRepository.deleteByLeagueIdAndSeason(leagueId, normalizedSeason);
        standingRepository.deleteByLeagueIdAndSeason(leagueId, altSeason);
        standingRepository.saveAll(standings);

        log.info("Calculated and saved {} team standings", standings.size());
        return standings;
    }

    /**
     * Process a single match and update standings.
     */
    private void processMatchForStandings(Map<String, LeagueStanding> standingsMap,
                                          Match match, Long leagueId, String season) {
        String homeTeam = match.getHomeTeam();
        String awayTeam = match.getAwayTeam();

        // Initialize team standings if not present
        standingsMap.computeIfAbsent(homeTeam, name -> createNewStanding(leagueId, season, name));
        standingsMap.computeIfAbsent(awayTeam, name -> createNewStanding(leagueId, season, name));

        LeagueStanding homeSt = standingsMap.get(homeTeam);
        LeagueStanding awaySt = standingsMap.get(awayTeam);

        int homeGoals = match.getFullTimeHomeGoals() != null ? match.getFullTimeHomeGoals() : 0;
        int awayGoals = match.getFullTimeAwayGoals() != null ? match.getFullTimeAwayGoals() : 0;

        // Determine result
        String homeResult, awayResult;
        if (homeGoals > awayGoals) {
            homeResult = "W";
            awayResult = "L";
        } else if (homeGoals < awayGoals) {
            homeResult = "L";
            awayResult = "W";
        } else {
            homeResult = "D";
            awayResult = "D";
        }

        // Update statistics
        homeSt.updateStats(homeGoals, awayGoals, homeResult);
        awaySt.updateStats(awayGoals, homeGoals, awayResult);
    }

    /**
     * Create a new standing entry for a team.
     */
    private LeagueStanding createNewStanding(Long leagueId, String season, String teamName) {
        // Try to find team ID
        Long teamId = teamRepository.findByNameIgnoreCase(teamName)
                .map(Team::getId)
                .orElse(null);

        return LeagueStanding.builder()
                .leagueId(leagueId)
                .season(season)
                .teamId(teamId)
                .teamName(teamName)
                .position(0)
                .played(0)
                .won(0)
                .drawn(0)
                .lost(0)
                .goalsFor(0)
                .goalsAgainst(0)
                .goalDifference(0)
                .points(0)
                .form("")
                .positionChange(0)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    /**
     * Map entity standings to DTOs with zone information.
     */
    private List<StandingDto> mapToStandingDtos(List<LeagueStanding> standings) {
        int totalTeams = standings.size();

        return standings.stream().map(s -> {
            String zone = determineZone(s.getPosition(), totalTeams);
            String teamLogo = getTeamLogo(s.getTeamName());

            return StandingDto.builder()
                    .position(s.getPosition())
                    .teamName(s.getTeamName())
                    .teamLogo(teamLogo)
                    .played(s.getPlayed() != null ? s.getPlayed() : 0)
                    .won(s.getWon() != null ? s.getWon() : 0)
                    .drawn(s.getDrawn() != null ? s.getDrawn() : 0)
                    .lost(s.getLost() != null ? s.getLost() : 0)
                    .goalsFor(s.getGoalsFor() != null ? s.getGoalsFor() : 0)
                    .goalsAgainst(s.getGoalsAgainst() != null ? s.getGoalsAgainst() : 0)
                    .goalDifference(s.getGoalDifference() != null ? s.getGoalDifference() : 0)
                    .points(s.getPoints() != null ? s.getPoints() : 0)
                    .form(s.getForm() != null ? s.getForm() : "")
                    .positionChange(s.getPositionChange() != null ? s.getPositionChange() : 0)
                    .zone(zone)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Determine the zone (Champions League, Europa, Relegation, etc.) for a position.
     */
    private String determineZone(int position, int totalTeams) {
        if (position <= CHAMPIONS_LEAGUE_POSITIONS) {
            return "champions";
        } else if (position <= CHAMPIONS_LEAGUE_POSITIONS + EUROPA_LEAGUE_POSITIONS) {
            return "europa";
        } else if (position == CONFERENCE_LEAGUE_POSITION) {
            return "conference";
        } else if (position > totalTeams - RELEGATION_POSITIONS) {
            return "relegation";
        }
        return "mid";
    }

    /**
     * Get team logo URL.
     */
    private String getTeamLogo(String teamName) {
        return teamRepository.findByNameIgnoreCase(teamName)
                .map(Team::getLogoUrl)
                .orElse(null);
    }

    /**
     * Get all available leagues.
     */
    public List<Map<String, Object>> getAvailableLeagues() {
        return leagueRepository.findByEnabledTrueOrderByDisplayOrderAsc().stream()
                .map(league -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", league.getId());
                    map.put("code", league.getCode());
                    map.put("name", league.getName());
                    map.put("country", league.getCountryName());
                    map.put("currentSeason", league.getCurrentSeason());
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get available seasons for a league.
     */
    public List<String> getAvailableSeasons(Long leagueId) {
        return standingRepository.findDistinctSeasonsByLeagueId(leagueId);
    }

    /**
     * Refresh standings cache for a league.
     */
    @CacheEvict(value = CacheConfig.CACHE_STANDINGS, allEntries = true)
    @Transactional
    public void refreshStandings(Long leagueId) {
        log.info("Refreshing standings for league: {}", leagueId);

        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        String currentSeason = league.getCurrentSeason();
        if (currentSeason == null || currentSeason.isEmpty()) {
            currentSeason = getCurrentSeasonString();
        }

        calculateStandingsFromMatches(leagueId, currentSeason);
    }

    /**
     * Clear all standings cache.
     */
    @CacheEvict(value = CacheConfig.CACHE_STANDINGS, allEntries = true)
    public void clearStandingsCache() {
        log.info("Cleared all standings cache");
    }

    // ========== Utility Methods ==========

    /**
     * Get current season string (e.g., "2025-26").
     * Uses dash format to match database convention.
     */
    private String getCurrentSeasonString() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        // Football season typically starts in August
        if (month < 8) {
            year--;
        }

        int nextYear = year + 1;
        return String.format("%d-%02d", year, nextYear % 100);
    }

    /**
     * Get season start date from season string.
     */
    private LocalDate getSeasonStartDate(String season) {
        try {
            // Parse season string like "2025/26" or "2025-26"
            String yearPart = season.split("[/-]")[0];
            int year = Integer.parseInt(yearPart.length() == 2 ? "20" + yearPart : yearPart);
            return LocalDate.of(year, 8, 1);
        } catch (Exception e) {
            log.warn("Failed to parse season start date from: {}", season);
            return LocalDate.of(2025, 8, 1);
        }
    }

    /**
     * Get season end date from season string.
     */
    private LocalDate getSeasonEndDate(String season) {
        try {
            String[] parts = season.split("[/-]");
            int endYear;
            if (parts.length > 1) {
                String yearPart = parts[1];
                endYear = Integer.parseInt(yearPart.length() == 2 ? "20" + yearPart : yearPart);
            } else {
                endYear = Integer.parseInt(parts[0]) + 1;
            }
            return LocalDate.of(endYear, 7, 31);
        } catch (Exception e) {
            log.warn("Failed to parse season end date from: {}", season);
            return LocalDate.of(2026, 7, 31);
        }
    }

    /**
     * Get the default league ID (Premier League).
     */
    public Long getDefaultLeagueId() {
        return leagueRepository.findByCode("PL")
                .map(League::getId)
                .orElse(1L);
    }


    // ========== Date-Based Standings (for Feature Engineering) ==========

    /**
     * Calculate league standings as of a specific date.
     * Used by feature engineering to get accurate historical positions.
     *
     * @param asOfDate The date to calculate standings up to (exclusive)
     * @return Map of team name to position (1-based)
     */
    @Cacheable(value = CacheConfig.CACHE_STANDINGS, key = "'date_' + #asOfDate.toString()")
    public Map<String, Integer> getStandingsAsOfDate(LocalDate asOfDate) {
        log.debug("Calculating standings as of date: {}", asOfDate);

        // Get all matches before this date
        List<Match> matches = matchRepository.findAllByOrderByMatchDateAsc().stream()
                .filter(m -> m.getMatchDate() != null)
                .filter(m -> m.getMatchDate().isBefore(asOfDate))
                .filter(m -> m.getFullTimeResult() != null)
                .toList();

        if (matches.isEmpty()) {
            log.debug("No matches found before date: {}", asOfDate);
            return Collections.emptyMap();
        }

        // Calculate standings from matches
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

            // Update stats
            homeSt.update(homeGoals, awayGoals);
            awaySt.update(awayGoals, homeGoals);
        }

        // Sort by points, then goal difference, then goals scored
        List<Map.Entry<String, TeamStandingData>> sortedList = standingsMap.entrySet().stream()
                .sorted((e1, e2) -> {
                    int cmp = Integer.compare(e2.getValue().points, e1.getValue().points);
                    if (cmp != 0) return cmp;
                    cmp = Integer.compare(e2.getValue().goalDifference, e1.getValue().goalDifference);
                    if (cmp != 0) return cmp;
                    return Integer.compare(e2.getValue().goalsFor, e1.getValue().goalsFor);
                })
                .toList();

        // Build position map
        Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < sortedList.size(); i++) {
            positions.put(sortedList.get(i).getKey(), i + 1);
        }

        return positions;
    }

    /**
     * Get a team's league position as of a specific date.
     *
     * @param teamName The team to get position for
     * @param asOfDate The date to calculate position as of
     * @return Position (1-based), or 10 (mid-table) if not found
     */
    public int getTeamPositionAsOfDate(String teamName, LocalDate asOfDate) {
        Map<String, Integer> standings = getStandingsAsOfDate(asOfDate);
        return standings.getOrDefault(teamName, 10);  // Default to mid-table
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
