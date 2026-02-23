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

        // Get current season
        String currentSeason = league.getCurrentSeason();
        if (currentSeason == null || currentSeason.isEmpty()) {
            currentSeason = getCurrentSeasonString();
        }

        return getLeagueTableForSeason(leagueId, currentSeason);
    }

    /**
     * Get league table for a specific season.
     *
     * @param leagueId League ID
     * @param season   Season string (e.g., "2025/26")
     * @return LeagueStandingsResponse
     */
    @Cacheable(value = CacheConfig.CACHE_STANDINGS, key = "'league_' + #leagueId + '_season_' + #season")
    @Transactional
    public LeagueStandingsResponse getLeagueTableForSeason(Long leagueId, String season) {
        log.info("Fetching league table for league ID: {} season: {}", leagueId, season);

        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        // Get standings from database
        List<LeagueStanding> standings = standingRepository
                .findByLeagueIdAndSeasonOrderByPointsDescGoalDifferenceDescGoalsForDesc(leagueId, season);

        // If no standings exist, calculate from matches
        if (standings.isEmpty()) {
            log.info("No standings found, calculating from match history...");
            standings = calculateStandingsFromMatches(leagueId, season);
        }

        // Map to DTOs
        List<StandingDto> standingDtos = mapToStandingDtos(standings);

        return LeagueStandingsResponse.builder()
                .leagueName(league.getName())
                .leagueCode(league.getCode())
                .season(formatSeasonForDisplay(season))
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
        log.info("Calculating standings from matches for league: {}, season: {}", leagueId, season);

        // Get season date range
        LocalDate seasonStart = getSeasonStartDate(season);
        LocalDate seasonEnd = getSeasonEndDate(season);

        // Fetch all matches for the season
        List<Match> matches = matchRepository.findAllByOrderByMatchDateAsc().stream()
                .filter(m -> m.getMatchDate() != null)
                .filter(m -> !m.getMatchDate().isBefore(seasonStart) && !m.getMatchDate().isAfter(seasonEnd))
                .toList();

        log.info("Found {} matches for season {}", matches.size(), season);

        // Calculate standings
        Map<String, LeagueStanding> standingsMap = new HashMap<>();

        for (Match match : matches) {
            processMatchForStandings(standingsMap, match, leagueId, season);
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

        // Save to database
        standingRepository.deleteByLeagueIdAndSeason(leagueId, season);
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

    /**
     * Format season string for display (e.g., "2025-26" -> "2025/26").
     */
    private String formatSeasonForDisplay(String season) {
        if (season == null) {
            return "";
        }
        // Convert dash to slash for display
        return season.replace("-", "/");
    }
}

