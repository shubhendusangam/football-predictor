package com.app.footballprediction.service;

import com.app.common.model.Team;
import com.app.common.repository.TeamRepository;
import com.app.footballprediction.config.CacheConfig;
import com.app.footballprediction.dto.TeamDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for managing team data including logos.
 * Includes caching for performance optimization.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeamService {

    private final TeamRepository teamRepository;

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

