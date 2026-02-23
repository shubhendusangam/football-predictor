package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.model.Team;
import com.app.common.model.SeasonTeamStats;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.SeasonTeamStatsRepository;
import com.app.common.repository.TeamRepository;
import com.app.footballprediction.config.CacheConfig;
import com.app.footballprediction.dto.TeamDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing team data including logos.
 * Includes caching for performance optimization.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeamService {

    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final SeasonTeamStatsRepository seasonTeamStatsRepository;

    private static final int RELEGATION_ZONE_SIZE = 3;
    private static final int PROMOTION_ZONE_SIZE = 3;

    /**
     * Get all teams as DTOs.
     * Cached for 1 hour (team data rarely changes).
     */
    @Cacheable(value = CacheConfig.CACHE_TEAM_LOGOS, key = "'allTeams'")
    public List<TeamDTO> getAllTeams() {
        log.debug("Fetching all teams from database");
        return teamRepository.findAllByOrderByNameAsc().stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Get a team by name.
     * Cached by team name.
     */
    @Cacheable(value = CacheConfig.CACHE_TEAM_LOGOS, key = "#name.toLowerCase()")
    public Optional<TeamDTO> getTeamByName(String name) {
        log.debug("Fetching team by name: {}", name);
        return teamRepository.findByNameIgnoreCase(name)
                .map(this::toDTO);
    }

    /**
     * Get team logo URL by team name.
     * Returns default logo if team not found or logo not set.
     * Cached by team name.
     */
    @Cacheable(value = CacheConfig.CACHE_TEAM_LOGOS, key = "'logo_' + #teamName.toLowerCase()")
    public String getTeamLogoUrl(String teamName) {
        log.debug("Fetching logo URL for team: {}", teamName);
        return teamRepository.findByNameIgnoreCase(teamName)
                .map(Team::getLogoUrl)
                .filter(url -> url != null && !url.isEmpty())
                .orElse(TeamDTO.DEFAULT_LOGO);
    }

    /**
     * Get a map of team names to their logo URLs.
     * Useful for batch operations.
     * Cached as a single map for efficiency.
     */
    @Cacheable(value = CacheConfig.CACHE_TEAM_LOGOS, key = "'logoMap'")
    public Map<String, String> getTeamLogoMap() {
        log.debug("Fetching team logo map from database");
        Map<String, String> logoMap = new HashMap<>();
        teamRepository.findAll().forEach(team -> {
            String logoUrl = team.getLogoUrl() != null ? team.getLogoUrl() : TeamDTO.DEFAULT_LOGO;
            logoMap.put(team.getName(), logoUrl);
        });
        return logoMap;
    }

    /**
     * Create or update a team.
     * Evicts all team logo caches to ensure consistency.
     */
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_TEAM_LOGOS, allEntries = true)
    public TeamDTO saveTeam(TeamDTO teamDTO) {
        log.info("Saving team: {}", teamDTO.getName());
        Team team = teamRepository.findByNameIgnoreCase(teamDTO.getName())
                .orElse(Team.builder().name(teamDTO.getName()).build());

        team.setLogoUrl(teamDTO.getLogoUrl());
        team.setShortName(teamDTO.getShortName());
        team.setPrimaryColor(teamDTO.getPrimaryColor());

        return toDTO(teamRepository.save(team));
    }

    /**
     * Ensure a team exists in the database, creating it with defaults if not.
     * Evicts cache if a new team is created.
     */
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_TEAM_LOGOS, allEntries = true, condition = "!@teamRepository.existsByName(#teamName)")
    public void ensureTeamExists(String teamName) {
        if (!teamRepository.existsByName(teamName)) {
            Team team = Team.builder()
                    .name(teamName)
                    .logoUrl(TeamDTO.DEFAULT_LOGO)
                    .build();
            teamRepository.save(team);
            log.debug("Created team entry: {}", teamName);
        }
    }

    /**
     * Clear all team logo caches.
     * Called when team data is updated in bulk.
     */
    @CacheEvict(value = CacheConfig.CACHE_TEAM_LOGOS, allEntries = true)
    public void clearCache() {
        log.info("Team logo cache cleared");
    }

    /**
     * Get all teams that played in a specific season.
     * Returns teams with their logo information and standings status.
     *
     * For current season: Shows live standings with relegation zone
     * For previous seasons: Shows final standings with relegated teams and European qualifiers
     *
     * @param season Season identifier (e.g., "2025-26")
     * @return List of teams that played in the season with status indicators
     */
    @Cacheable(value = CacheConfig.CACHE_TEAM_LOGOS, key = "'season_teams_' + #season", condition = "!#season.equals(@teamService.getCurrentSeason())")
    public List<TeamDTO> getTeamsBySeason(String season) {
        log.debug("Fetching teams for season: {}", season);

        // Get team names from match data for this season
        List<String> teamNames = matchRepository.findAllDistinctTeamNamesBySeason(season);

        if (teamNames.isEmpty()) {
            log.warn("No teams found for season: {}", season);
            return Collections.emptyList();
        }

        boolean isCurrentSeason = season.equals(getCurrentSeason());

        // Get promoted teams (teams new to this season that weren't in previous season)
        Set<String> promotedTeams = findPromotedTeams(season, teamNames);

        // Get standings and zones
        Map<String, Integer> teamPositions = new HashMap<>();
        Map<String, String> teamZones = new HashMap<>();
        Map<String, String> teamStatuses = new HashMap<>();

        // Try to get standings from SeasonTeamStats first
        List<SeasonTeamStats> standings = seasonTeamStatsRepository.findBySeasonIdOrderByPointsDesc(season);

        if (!standings.isEmpty()) {
            log.debug("Using SeasonTeamStats for {} teams in season {}", standings.size(), season);
            int totalTeams = standings.size();
            int position = 1;

            for (SeasonTeamStats stats : standings) {
                String teamName = stats.getTeamName();
                teamPositions.put(teamName, position);

                // Determine zone based on position
                String zone = determineZone(position, totalTeams);
                teamZones.put(teamName, zone);

                // Determine status
                String status = determineStatus(teamName, zone, promotedTeams, isCurrentSeason);
                teamStatuses.put(teamName, status);

                position++;
            }
        } else {
            // No SeasonTeamStats data - calculate standings from match results
            log.debug("Calculating standings from match data for season {}", season);
            Map<String, TeamStandingData> standingsMap = calculateStandingsFromMatches(season, teamNames);

            if (!standingsMap.isEmpty()) {
                // Sort by points, then goal difference, then goals scored
                List<Map.Entry<String, TeamStandingData>> sortedStandings = standingsMap.entrySet().stream()
                    .sorted((a, b) -> {
                        int pointsCompare = Integer.compare(b.getValue().points, a.getValue().points);
                        if (pointsCompare != 0) return pointsCompare;
                        int gdCompare = Integer.compare(b.getValue().goalDifference, a.getValue().goalDifference);
                        if (gdCompare != 0) return gdCompare;
                        return Integer.compare(b.getValue().goalsFor, a.getValue().goalsFor);
                    })
                    .collect(Collectors.toList());

                int totalTeams = sortedStandings.size();
                int position = 1;

                for (Map.Entry<String, TeamStandingData> entry : sortedStandings) {
                    String teamName = entry.getKey();
                    teamPositions.put(teamName, position);

                    String zone = determineZone(position, totalTeams);
                    teamZones.put(teamName, zone);

                    String status = determineStatus(teamName, zone, promotedTeams, isCurrentSeason);
                    teamStatuses.put(teamName, status);

                    position++;
                }
            }
        }

        // Map to DTOs with logo and status information
        List<TeamDTO> teams = new ArrayList<>();
        for (String teamName : teamNames) {
            TeamDTO.TeamDTOBuilder builder = TeamDTO.builder()
                    .name(teamName)
                    .logoUrl(TeamDTO.DEFAULT_LOGO);

            // Get team entity for logo and colors
            Optional<Team> teamEntity = teamRepository.findByNameIgnoreCase(teamName);
            if (teamEntity.isPresent()) {
                Team entity = teamEntity.get();
                builder.logoUrl(entity.getLogoUrl() != null ? entity.getLogoUrl() : TeamDTO.DEFAULT_LOGO)
                       .shortName(entity.getShortName())
                       .primaryColor(entity.getPrimaryColor());
            }

            // Add position and zone if available
            Integer position = teamPositions.get(teamName);
            String zone = teamZones.get(teamName);
            String status = teamStatuses.get(teamName);

            builder.position(position);
            builder.zone(zone);
            builder.status(status);

            // For promoted teams in current season
            if (promotedTeams.contains(teamName) && isCurrentSeason) {
                builder.status("promoted");
            }

            teams.add(builder.build());
        }

        // Sort by position if available, otherwise alphabetically
        if (!teamPositions.isEmpty()) {
            teams.sort(Comparator.comparing(t -> teamPositions.getOrDefault(t.getName(), 999)));
        } else {
            teams.sort(Comparator.comparing(TeamDTO::getName));
        }

        log.info("Found {} teams for season {} (isCurrentSeason: {}, promoted: {}, relegated/relegation: {})",
                teams.size(), season, isCurrentSeason, promotedTeams.size(),
                teams.stream().filter(t -> "relegation".equals(t.getZone()) || "relegated".equals(t.getStatus())).count());
        return teams;
    }

    /**
     * Find teams that were promoted to this season (new teams compared to previous season)
     */
    private Set<String> findPromotedTeams(String season, List<String> currentSeasonTeams) {
        Set<String> promotedTeams = new HashSet<>();
        String previousSeason = getPreviousSeason(season);
        List<String> previousSeasonTeams = matchRepository.findAllDistinctTeamNamesBySeason(previousSeason);

        if (!previousSeasonTeams.isEmpty()) {
            for (String teamName : currentSeasonTeams) {
                if (!previousSeasonTeams.contains(teamName)) {
                    promotedTeams.add(teamName);
                    log.debug("Identified promoted team for {}: {}", season, teamName);
                }
            }
        }
        return promotedTeams;
    }

    /**
     * Determine the zone based on league position
     */
    private String determineZone(int position, int totalTeams) {
        if (position <= 4) {
            return "champions";
        } else if (position <= 6) {
            return "europa";
        } else if (position == 7) {
            return "conference";
        } else if (position > totalTeams - RELEGATION_ZONE_SIZE) {
            return "relegation";
        }
        return "mid";
    }

    /**
     * Determine team status based on zone and other factors
     */
    private String determineStatus(String teamName, String zone, Set<String> promotedTeams, boolean isCurrentSeason) {
        if (promotedTeams.contains(teamName)) {
            return "promoted";
        }

        switch (zone) {
            case "champions":
                return isCurrentSeason ? "champions-league" : "qualified-ucl";
            case "europa":
                return isCurrentSeason ? "europa-league" : "qualified-uel";
            case "conference":
                return isCurrentSeason ? "conference-league" : "qualified-uecl";
            case "relegation":
                return isCurrentSeason ? "relegation" : "relegated";
            default:
                return "mid-table";
        }
    }

    /**
     * Get the next season string (e.g., "2024-25" -> "2025-26")
     */
    private String getNextSeason(String currentSeason) {
        try {
            String[] parts = currentSeason.split("-");
            int startYear = Integer.parseInt(parts[0]) + 1;
            int endYear = (Integer.parseInt(parts[1]) + 1) % 100;
            return startYear + "-" + String.format("%02d", endYear);
        } catch (Exception e) {
            return "2025-26"; // Fallback
        }
    }

    /**
     * Get the current season string (e.g., "2025-26")
     */
    public String getCurrentSeason() {
        int year = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();

        // Football seasons typically run Aug-May
        if (month >= 8) {
            return year + "-" + String.format("%02d", (year + 1) % 100);
        } else {
            return (year - 1) + "-" + String.format("%02d", year % 100);
        }
    }

    /**
     * Get previous season string
     */
    private String getPreviousSeason(String currentSeason) {
        try {
            String[] parts = currentSeason.split("-");
            int startYear = Integer.parseInt(parts[0]) - 1;
            int endYear = Integer.parseInt(parts[1]) - 1;
            if (endYear < 0) endYear = 99;
            return startYear + "-" + String.format("%02d", endYear);
        } catch (Exception e) {
            return "2024-25"; // Fallback
        }
    }

    /**
     * Get all available seasons.
     *
     * @return List of seasons sorted descending (newest first)
     */
    @Cacheable(value = CacheConfig.CACHE_TEAM_LOGOS, key = "'allSeasons'")
    public List<String> getAllSeasons() {
        log.debug("Fetching all seasons");
        return matchRepository.findAllSeasons();
    }

    /**
     * Calculate standings from match results for a season.
     * Used as fallback when SeasonTeamStats table doesn't have data.
     */
    private Map<String, TeamStandingData> calculateStandingsFromMatches(String season, List<String> teamNames) {
        Map<String, TeamStandingData> standings = new HashMap<>();

        // Initialize all teams
        for (String teamName : teamNames) {
            standings.put(teamName, new TeamStandingData());
        }

        // Get all matches for this season
        List<Match> matches = matchRepository.findBySeasonOrderByMatchDateDesc(season);

        for (Match match : matches) {
            if (match.getFullTimeHomeGoals() == null || match.getFullTimeAwayGoals() == null) {
                continue; // Skip matches without results
            }

            String homeTeam = match.getHomeTeam();
            String awayTeam = match.getAwayTeam();
            int homeGoals = match.getFullTimeHomeGoals();
            int awayGoals = match.getFullTimeAwayGoals();

            TeamStandingData homeData = standings.computeIfAbsent(homeTeam, k -> new TeamStandingData());
            TeamStandingData awayData = standings.computeIfAbsent(awayTeam, k -> new TeamStandingData());

            // Update goals
            homeData.goalsFor += homeGoals;
            homeData.goalsAgainst += awayGoals;
            awayData.goalsFor += awayGoals;
            awayData.goalsAgainst += homeGoals;

            // Update matches played
            homeData.played++;
            awayData.played++;

            // Determine result and update points
            if (homeGoals > awayGoals) {
                // Home win
                homeData.wins++;
                homeData.points += 3;
                awayData.losses++;
            } else if (awayGoals > homeGoals) {
                // Away win
                awayData.wins++;
                awayData.points += 3;
                homeData.losses++;
            } else {
                // Draw
                homeData.draws++;
                awayData.draws++;
                homeData.points++;
                awayData.points++;
            }
        }

        // Calculate goal difference
        for (TeamStandingData data : standings.values()) {
            data.goalDifference = data.goalsFor - data.goalsAgainst;
        }

        return standings;
    }

    /**
     * Helper class to hold team standing data
     */
    private static class TeamStandingData {
        int played = 0;
        int wins = 0;
        int draws = 0;
        int losses = 0;
        int goalsFor = 0;
        int goalsAgainst = 0;
        int goalDifference = 0;
        int points = 0;
    }

    /**
     * Convert Team entity to TeamDTO.
     */
    private TeamDTO toDTO(Team team) {
        return TeamDTO.builder()
                .name(team.getName())
                .logoUrl(team.getLogoUrl() != null ? team.getLogoUrl() : TeamDTO.DEFAULT_LOGO)
                .shortName(team.getShortName())
                .primaryColor(team.getPrimaryColor())
                .build();
    }
}

