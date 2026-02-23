package com.app.footballprediction.config;

import com.app.common.model.Team;
import com.app.common.model.SystemSettings;
import com.app.common.repository.TeamRepository;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.SystemSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Seeds the database with EPL team logos from official online sources.
 * Uses football-data.org API crest URLs which are CORS-friendly.
 *
 * OPTIMIZATION: Only seeds logos once per season, not on every startup.
 * Logos are stored in the database and cached for efficient retrieval.
 *
 * Runs on application startup AFTER data ingestion to ensure teams exist.
 */
@Component
@Order(100) // Run after main ApplicationRunner (which does CSV ingestion)
@RequiredArgsConstructor
@Slf4j
public class TeamLogoSeeder implements ApplicationRunner {

    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final SystemSettingsRepository systemSettingsRepository;

    // Default fallback logo - a generic football icon from a CDN
    private static final String DEFAULT_LOGO_URL = "https://cdn-icons-png.flaticon.com/512/861/861512.png";

    // EPL team configurations with official online logo URLs
    // Using API-Football media CDN (CORS-friendly and publicly accessible)
    // Alternative: football-data.org crests which also support CORS
    private static final String LOGO_BASE = "https://media.api-sports.io/football/teams/";
    private static final Map<String, TeamLogoConfig> EPL_TEAMS = new LinkedHashMap<>();

    static {
        // Current Premier League teams (2025-26 season)
        // Logo URLs from API-Football media CDN (CORS enabled)
        EPL_TEAMS.put("Arsenal", new TeamLogoConfig("ARS", "#EF0107",
                LOGO_BASE + "42.png"));
        EPL_TEAMS.put("Aston Villa", new TeamLogoConfig("AVL", "#670E36",
                LOGO_BASE + "66.png"));
        EPL_TEAMS.put("Bournemouth", new TeamLogoConfig("BOU", "#DA291C",
                LOGO_BASE + "35.png"));
        EPL_TEAMS.put("Brentford", new TeamLogoConfig("BRE", "#E30613",
                LOGO_BASE + "55.png"));
        EPL_TEAMS.put("Brighton", new TeamLogoConfig("BHA", "#0057B8",
                LOGO_BASE + "51.png"));
        EPL_TEAMS.put("Chelsea", new TeamLogoConfig("CHE", "#034694",
                LOGO_BASE + "49.png"));
        EPL_TEAMS.put("Crystal Palace", new TeamLogoConfig("CRY", "#1B458F",
                LOGO_BASE + "52.png"));
        EPL_TEAMS.put("Everton", new TeamLogoConfig("EVE", "#003399",
                LOGO_BASE + "45.png"));
        EPL_TEAMS.put("Fulham", new TeamLogoConfig("FUL", "#000000",
                LOGO_BASE + "36.png"));
        EPL_TEAMS.put("Ipswich", new TeamLogoConfig("IPS", "#0044AA",
                LOGO_BASE + "57.png"));
        EPL_TEAMS.put("Ipswich Town", new TeamLogoConfig("IPS", "#0044AA",
                LOGO_BASE + "57.png"));
        EPL_TEAMS.put("Leicester", new TeamLogoConfig("LEI", "#003090",
                LOGO_BASE + "46.png"));
        EPL_TEAMS.put("Leicester City", new TeamLogoConfig("LEI", "#003090",
                LOGO_BASE + "46.png"));
        EPL_TEAMS.put("Liverpool", new TeamLogoConfig("LIV", "#C8102E",
                LOGO_BASE + "40.png"));
        EPL_TEAMS.put("Man City", new TeamLogoConfig("MCI", "#6CABDD",
                LOGO_BASE + "50.png"));
        EPL_TEAMS.put("Manchester City", new TeamLogoConfig("MCI", "#6CABDD",
                LOGO_BASE + "50.png"));
        EPL_TEAMS.put("Man United", new TeamLogoConfig("MUN", "#DA291C",
                LOGO_BASE + "33.png"));
        EPL_TEAMS.put("Manchester United", new TeamLogoConfig("MUN", "#DA291C",
                LOGO_BASE + "33.png"));
        EPL_TEAMS.put("Newcastle", new TeamLogoConfig("NEW", "#241F20",
                LOGO_BASE + "34.png"));
        EPL_TEAMS.put("Newcastle United", new TeamLogoConfig("NEW", "#241F20",
                LOGO_BASE + "34.png"));
        EPL_TEAMS.put("Nott'm Forest", new TeamLogoConfig("NFO", "#DD0000",
                LOGO_BASE + "65.png"));
        EPL_TEAMS.put("Nottingham Forest", new TeamLogoConfig("NFO", "#DD0000",
                LOGO_BASE + "65.png"));
        EPL_TEAMS.put("Southampton", new TeamLogoConfig("SOU", "#D71920",
                LOGO_BASE + "41.png"));
        EPL_TEAMS.put("Tottenham", new TeamLogoConfig("TOT", "#132257",
                LOGO_BASE + "47.png"));
        EPL_TEAMS.put("Spurs", new TeamLogoConfig("TOT", "#132257",
                LOGO_BASE + "47.png"));
        EPL_TEAMS.put("West Ham", new TeamLogoConfig("WHU", "#7A263A",
                LOGO_BASE + "48.png"));
        EPL_TEAMS.put("West Ham United", new TeamLogoConfig("WHU", "#7A263A",
                LOGO_BASE + "48.png"));
        EPL_TEAMS.put("Wolves", new TeamLogoConfig("WOL", "#FDB913",
                LOGO_BASE + "39.png"));
        EPL_TEAMS.put("Wolverhampton", new TeamLogoConfig("WOL", "#FDB913",
                LOGO_BASE + "39.png"));

        // Recently relegated/promoted teams
        EPL_TEAMS.put("Burnley", new TeamLogoConfig("BUR", "#6C1D45",
                LOGO_BASE + "44.png"));
        EPL_TEAMS.put("Luton", new TeamLogoConfig("LUT", "#F78F1E",
                LOGO_BASE + "1359.png"));
        EPL_TEAMS.put("Luton Town", new TeamLogoConfig("LUT", "#F78F1E",
                LOGO_BASE + "1359.png"));
        EPL_TEAMS.put("Sheffield United", new TeamLogoConfig("SHU", "#EE2737",
                LOGO_BASE + "62.png"));
        EPL_TEAMS.put("Sheffield Utd", new TeamLogoConfig("SHU", "#EE2737",
                LOGO_BASE + "62.png"));
        EPL_TEAMS.put("Leeds", new TeamLogoConfig("LEE", "#FFCD00",
                LOGO_BASE + "63.png"));
        EPL_TEAMS.put("Leeds United", new TeamLogoConfig("LEE", "#FFCD00",
                LOGO_BASE + "63.png"));

        // Historic EPL teams
        EPL_TEAMS.put("Watford", new TeamLogoConfig("WAT", "#FBEE23",
                LOGO_BASE + "38.png"));
        EPL_TEAMS.put("Norwich", new TeamLogoConfig("NOR", "#00A650",
                LOGO_BASE + "71.png"));
        EPL_TEAMS.put("Norwich City", new TeamLogoConfig("NOR", "#00A650",
                LOGO_BASE + "71.png"));
        EPL_TEAMS.put("West Brom", new TeamLogoConfig("WBA", "#122F67",
                LOGO_BASE + "60.png"));
        EPL_TEAMS.put("West Bromwich", new TeamLogoConfig("WBA", "#122F67",
                LOGO_BASE + "60.png"));
        EPL_TEAMS.put("Stoke", new TeamLogoConfig("STK", "#E03A3E",
                LOGO_BASE + "75.png"));
        EPL_TEAMS.put("Stoke City", new TeamLogoConfig("STK", "#E03A3E",
                LOGO_BASE + "75.png"));
        EPL_TEAMS.put("Swansea", new TeamLogoConfig("SWA", "#000000",
                LOGO_BASE + "72.png"));
        EPL_TEAMS.put("Swansea City", new TeamLogoConfig("SWA", "#000000",
                LOGO_BASE + "72.png"));
        EPL_TEAMS.put("Sunderland", new TeamLogoConfig("SUN", "#FF0000",
                LOGO_BASE + "74.png"));
        EPL_TEAMS.put("Hull", new TeamLogoConfig("HUL", "#F5A12D",
                LOGO_BASE + "64.png"));
        EPL_TEAMS.put("Hull City", new TeamLogoConfig("HUL", "#F5A12D",
                LOGO_BASE + "64.png"));
        EPL_TEAMS.put("Middlesbrough", new TeamLogoConfig("MID", "#E21E26",
                LOGO_BASE + "59.png"));
        EPL_TEAMS.put("Cardiff", new TeamLogoConfig("CAR", "#0070B5",
                LOGO_BASE + "43.png"));
        EPL_TEAMS.put("Cardiff City", new TeamLogoConfig("CAR", "#0070B5",
                LOGO_BASE + "43.png"));
        EPL_TEAMS.put("QPR", new TeamLogoConfig("QPR", "#1D5BA4",
                LOGO_BASE + "69.png"));
        EPL_TEAMS.put("Blackburn", new TeamLogoConfig("BLB", "#009EE0",
                LOGO_BASE + "56.png"));
        EPL_TEAMS.put("Blackburn Rovers", new TeamLogoConfig("BLB", "#009EE0",
                LOGO_BASE + "56.png"));
        EPL_TEAMS.put("Bolton", new TeamLogoConfig("BOL", "#263C7F",
                LOGO_BASE + "58.png"));
        EPL_TEAMS.put("Bolton Wanderers", new TeamLogoConfig("BOL", "#263C7F",
                LOGO_BASE + "58.png"));
        EPL_TEAMS.put("Wigan", new TeamLogoConfig("WIG", "#1D428A",
                LOGO_BASE + "68.png"));
        EPL_TEAMS.put("Wigan Athletic", new TeamLogoConfig("WIG", "#1D428A",
                LOGO_BASE + "68.png"));
        EPL_TEAMS.put("Reading", new TeamLogoConfig("REA", "#004494",
                LOGO_BASE + "53.png"));
        EPL_TEAMS.put("Birmingham", new TeamLogoConfig("BIR", "#0000FF",
                LOGO_BASE + "54.png"));
        EPL_TEAMS.put("Birmingham City", new TeamLogoConfig("BIR", "#0000FF",
                LOGO_BASE + "54.png"));
        EPL_TEAMS.put("Portsmouth", new TeamLogoConfig("POR", "#001489",
                LOGO_BASE + "73.png"));
        EPL_TEAMS.put("Charlton", new TeamLogoConfig("CHA", "#D4021D",
                LOGO_BASE + "70.png"));
        EPL_TEAMS.put("Charlton Athletic", new TeamLogoConfig("CHA", "#D4021D",
                LOGO_BASE + "70.png"));
        EPL_TEAMS.put("Derby", new TeamLogoConfig("DER", "#000000",
                LOGO_BASE + "61.png"));
        EPL_TEAMS.put("Derby County", new TeamLogoConfig("DER", "#000000",
                LOGO_BASE + "61.png"));
        EPL_TEAMS.put("Coventry", new TeamLogoConfig("COV", "#87CEEB",
                LOGO_BASE + "1415.png"));
        EPL_TEAMS.put("Coventry City", new TeamLogoConfig("COV", "#87CEEB",
                LOGO_BASE + "1415.png"));
        EPL_TEAMS.put("Blackpool", new TeamLogoConfig("BPL", "#F68712",
                LOGO_BASE + "38.png"));
        EPL_TEAMS.put("Bradford", new TeamLogoConfig("BRA", "#7B1E1E",
                DEFAULT_LOGO_URL));
        EPL_TEAMS.put("Bradford City", new TeamLogoConfig("BRA", "#7B1E1E",
                DEFAULT_LOGO_URL));
        EPL_TEAMS.put("Oldham", new TeamLogoConfig("OLD", "#003399",
                DEFAULT_LOGO_URL));
        EPL_TEAMS.put("Oldham Athletic", new TeamLogoConfig("OLD", "#003399",
                DEFAULT_LOGO_URL));
        EPL_TEAMS.put("Sheffield Wednesday", new TeamLogoConfig("SHW", "#0000FF",
                LOGO_BASE + "67.png"));
        EPL_TEAMS.put("Sheffield Weds", new TeamLogoConfig("SHW", "#0000FF",
                LOGO_BASE + "67.png"));
        EPL_TEAMS.put("Wimbledon", new TeamLogoConfig("WIM", "#00008B",
                DEFAULT_LOGO_URL));
        EPL_TEAMS.put("Barnsley", new TeamLogoConfig("BAR", "#FF0000",
                LOGO_BASE + "77.png"));
        EPL_TEAMS.put("Swindon", new TeamLogoConfig("SWI", "#FF0000",
                DEFAULT_LOGO_URL));
        EPL_TEAMS.put("Swindon Town", new TeamLogoConfig("SWI", "#FF0000",
                DEFAULT_LOGO_URL));
    }


    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String currentSeason = getCurrentSeason();

        // Check if logos have already been seeded for this season
        if (isLogoSeedingRequired(currentSeason)) {
            log.info("🎨 Starting team logo seeding for season {}...", currentSeason);
            performLogoSeeding(currentSeason);
        } else {
            log.info("✓ Team logos already seeded for season {}. Skipping startup seeding.", currentSeason);
        }
    }

    /**
     * Determine if logo seeding is required for the current season.
     * Returns true if:
     * - No previous seeding record exists
     * - Previous seeding was for a different season
     * - There are teams without logos
     */
    private boolean isLogoSeedingRequired(String currentSeason) {
        Optional<SystemSettings> settingsOpt = systemSettingsRepository.findById(1L);

        if (settingsOpt.isEmpty()) {
            log.debug("No system settings found - logo seeding required");
            return true;
        }

        SystemSettings settings = settingsOpt.get();
        String lastSeededSeason = settings.getLogoSeedingSeason();

        if (lastSeededSeason == null || !lastSeededSeason.equals(currentSeason)) {
            log.debug("Logo seeding required: last seeded season={}, current={}", lastSeededSeason, currentSeason);
            return true;
        }

        // Check if there are teams without logos (new teams added)
        long teamsWithoutLogos = teamRepository.findAll().stream()
                .filter(t -> t.getLogoUrl() == null || t.getLogoUrl().isEmpty()
                        || t.getLogoSeededSeason() == null
                        || !t.getLogoSeededSeason().equals(currentSeason))
                .count();

        if (teamsWithoutLogos > 0) {
            log.debug("Found {} teams without logos for current season", teamsWithoutLogos);
            return true;
        }

        return false;
    }

    /**
     * Perform the actual logo seeding operation.
     */
    @CacheEvict(value = CacheConfig.CACHE_TEAM_LOGOS, allEntries = true)
    private void performLogoSeeding(String currentSeason) {
        // Get all unique team names from matches
        Set<String> allTeamNames = new HashSet<>();
        matchRepository.findAll().forEach(match -> {
            allTeamNames.add(match.getHomeTeam());
            allTeamNames.add(match.getAwayTeam());
        });

        if (allTeamNames.isEmpty()) {
            log.warn("   ⚠ No teams found in database - skipping logo seeding");
            return;
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        LocalDateTime now = LocalDateTime.now();

        for (String teamName : allTeamNames) {
            Optional<Team> existingTeamOpt = teamRepository.findByName(teamName);
            TeamLogoConfig config = EPL_TEAMS.get(teamName);

            if (config != null) {
                Team team = existingTeamOpt.orElse(Team.builder().name(teamName).build());

                // Only update if logo has changed or not set for current season
                if (!currentSeason.equals(team.getLogoSeededSeason())
                        || !config.logoUrl.equals(team.getLogoUrl())) {
                    team.setLogoUrl(config.logoUrl);
                    team.setShortName(config.shortName);
                    team.setPrimaryColor(config.primaryColor);
                    team.setLogoLastUpdated(now);
                    team.setLogoSeededSeason(currentSeason);
                    teamRepository.save(team);

                    if (existingTeamOpt.isPresent()) {
                        updated++;
                    } else {
                        created++;
                    }
                } else {
                    skipped++;
                }
            } else if (existingTeamOpt.isEmpty()) {
                // Unknown team - create with default logo
                Team team = Team.builder()
                        .name(teamName)
                        .logoUrl(DEFAULT_LOGO_URL)
                        .logoLastUpdated(now)
                        .logoSeededSeason(currentSeason)
                        .build();
                teamRepository.save(team);
                created++;
            } else {
                // Existing team without known config - update if logo is missing or local
                Team team = existingTeamOpt.get();
                if (team.getLogoUrl() == null ||
                    team.getLogoUrl().isEmpty() ||
                    team.getLogoUrl().startsWith("/images/") ||
                    !currentSeason.equals(team.getLogoSeededSeason())) {
                    team.setLogoUrl(DEFAULT_LOGO_URL);
                    team.setLogoLastUpdated(now);
                    team.setLogoSeededSeason(currentSeason);
                    teamRepository.save(team);
                    updated++;
                } else {
                    skipped++;
                }
            }
        }

        // Update system settings with seeding info
        updateSystemSettings(currentSeason, now);

        log.info("   ✓ Team logo seeding complete. Created: {}, Updated: {}, Skipped: {}, Total teams: {}",
                created, updated, skipped, teamRepository.count());
    }

    /**
     * Update system settings to record logo seeding completion.
     */
    private void updateSystemSettings(String season, LocalDateTime timestamp) {
        SystemSettings settings = systemSettingsRepository.findById(1L)
                .orElse(SystemSettings.builder().build());
        settings.setLogoSeedingSeason(season);
        settings.setLogoSeedingTimestamp(timestamp);
        systemSettingsRepository.save(settings);
    }

    /**
     * Get the current football season string (e.g., "2025-26").
     * Season runs from August to May, so:
     * - Jan-Jul: previous year to current year (e.g., 2025-26 in Jan 2026)
     * - Aug-Dec: current year to next year (e.g., 2025-26 in Sep 2025)
     */
    private String getCurrentSeason() {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        if (month >= 8) {
            // Aug-Dec: season is currentYear-nextYear
            return year + "-" + String.format("%02d", (year + 1) % 100);
        } else {
            // Jan-Jul: season is previousYear-currentYear
            return (year - 1) + "-" + String.format("%02d", year % 100);
        }
    }

    /**
     * Manually trigger logo seeding (for API endpoint use).
     * Forces refresh regardless of current season status.
     * @param forceRefresh if true, refresh all logos regardless of season
     * @return Map with seeding statistics
     */
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_TEAM_LOGOS, allEntries = true)
    public Map<String, Object> seedLogos(boolean forceRefresh) {
        log.info("Manual team logo seeding triggered (forceRefresh={})...", forceRefresh);
        String currentSeason = getCurrentSeason();

        if (!forceRefresh && !isLogoSeedingRequired(currentSeason)) {
            return Map.of(
                    "success", true,
                    "message", "Logos already seeded for season " + currentSeason,
                    "skipped", true,
                    "season", currentSeason
            );
        }

        Set<String> allTeamNames = new HashSet<>();
        matchRepository.findAll().forEach(match -> {
            allTeamNames.add(match.getHomeTeam());
            allTeamNames.add(match.getAwayTeam());
        });

        if (allTeamNames.isEmpty()) {
            return Map.of("success", false, "message", "No teams found in database");
        }

        int created = 0;
        int updated = 0;
        int skipped = 0;
        LocalDateTime now = LocalDateTime.now();

        for (String teamName : allTeamNames) {
            Optional<Team> existingTeamOpt = teamRepository.findByName(teamName);
            TeamLogoConfig config = EPL_TEAMS.get(teamName);

            if (config != null) {
                Team team = existingTeamOpt.orElse(Team.builder().name(teamName).build());

                // Force refresh or check if update needed
                if (forceRefresh || !currentSeason.equals(team.getLogoSeededSeason())
                        || !config.logoUrl.equals(team.getLogoUrl())) {
                    team.setLogoUrl(config.logoUrl);
                    team.setShortName(config.shortName);
                    team.setPrimaryColor(config.primaryColor);
                    team.setLogoLastUpdated(now);
                    team.setLogoSeededSeason(currentSeason);
                    teamRepository.save(team);

                    if (existingTeamOpt.isPresent()) {
                        updated++;
                    } else {
                        created++;
                    }
                } else {
                    skipped++;
                }
            } else if (existingTeamOpt.isEmpty()) {
                Team team = Team.builder()
                        .name(teamName)
                        .logoUrl(DEFAULT_LOGO_URL)
                        .logoLastUpdated(now)
                        .logoSeededSeason(currentSeason)
                        .build();
                teamRepository.save(team);
                created++;
            } else {
                Team team = existingTeamOpt.get();
                if (forceRefresh || team.getLogoUrl() == null ||
                    team.getLogoUrl().isEmpty() ||
                    team.getLogoUrl().startsWith("/images/") ||
                    !currentSeason.equals(team.getLogoSeededSeason())) {
                    team.setLogoUrl(DEFAULT_LOGO_URL);
                    team.setLogoLastUpdated(now);
                    team.setLogoSeededSeason(currentSeason);
                    teamRepository.save(team);
                    updated++;
                } else {
                    skipped++;
                }
            }
        }

        // Update system settings
        updateSystemSettings(currentSeason, now);

        log.info("   ✓ Logo seeding complete. Created: {}, Updated: {}, Skipped: {}", created, updated, skipped);

        return Map.of(
                "success", true,
                "created", created,
                "updated", updated,
                "skipped", skipped,
                "totalTeams", teamRepository.count(),
                "season", currentSeason
        );
    }

    /**
     * Manually trigger logo seeding (for API endpoint use).
     * @return Map with seeding statistics
     */
    @Transactional
    public Map<String, Object> seedLogos() {
        return seedLogos(false);
    }

    /**
     * Get the current logo seeding status.
     * @return Map with status information
     */
    public Map<String, Object> getLogoSeedingStatus() {
        String currentSeason = getCurrentSeason();
        Optional<SystemSettings> settingsOpt = systemSettingsRepository.findById(1L);

        long totalTeams = teamRepository.count();
        long teamsWithLogos = teamRepository.findAll().stream()
                .filter(t -> t.getLogoUrl() != null && !t.getLogoUrl().isEmpty())
                .count();
        long teamsForCurrentSeason = teamRepository.findAll().stream()
                .filter(t -> currentSeason.equals(t.getLogoSeededSeason()))
                .count();

        Map<String, Object> status = new HashMap<>();
        status.put("currentSeason", currentSeason);
        status.put("totalTeams", totalTeams);
        status.put("teamsWithLogos", teamsWithLogos);
        status.put("teamsSeededForCurrentSeason", teamsForCurrentSeason);
        status.put("seedingRequired", isLogoSeedingRequired(currentSeason));

        if (settingsOpt.isPresent()) {
            SystemSettings settings = settingsOpt.get();
            status.put("lastSeededSeason", settings.getLogoSeedingSeason());
            status.put("lastSeededTimestamp", settings.getLogoSeedingTimestamp() != null
                    ? settings.getLogoSeedingTimestamp().toString() : null);
        } else {
            status.put("lastSeededSeason", null);
            status.put("lastSeededTimestamp", null);
        }

        return status;
    }

    /**
     * Configuration holder for team logo data.
     */
    private static class TeamLogoConfig {
        final String shortName;
        final String primaryColor;
        final String logoUrl;

        TeamLogoConfig(String shortName, String primaryColor, String logoUrl) {
            this.shortName = shortName;
            this.primaryColor = primaryColor;
            this.logoUrl = logoUrl;
        }
    }
}

