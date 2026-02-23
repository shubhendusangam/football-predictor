package com.app.footballprediction.config;

import com.app.common.model.Team;
import com.app.common.repository.TeamRepository;
import com.app.common.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Seeds the database with EPL team logos from official online sources.
 * Uses football-data.org API crest URLs which are CORS-friendly.
 * Runs on application startup AFTER data ingestion to ensure teams exist.
 */
@Component
@Order(100) // Run after main ApplicationRunner (which does CSV ingestion)
@RequiredArgsConstructor
@Slf4j
public class TeamLogoSeeder implements ApplicationRunner {

    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;

    // Default fallback logo - a generic football icon from a CDN
    private static final String DEFAULT_LOGO_URL = "https://cdn-icons-png.flaticon.com/512/861/861512.png";

    // EPL team configurations with official online logo URLs
    // Using Premier League resources CDN (reliable and publicly accessible)
    private static final String PL_LOGO_BASE = "https://resources.premierleague.com/premierleague/badges/50/";
    private static final Map<String, TeamLogoConfig> EPL_TEAMS = new LinkedHashMap<>();

    static {
        // Current Premier League teams (2025-26 season)
        // Logo URLs from Premier League official resources
        EPL_TEAMS.put("Arsenal", new TeamLogoConfig("ARS", "#EF0107",
                PL_LOGO_BASE + "t3.png"));
        EPL_TEAMS.put("Aston Villa", new TeamLogoConfig("AVL", "#670E36",
                PL_LOGO_BASE + "t7.png"));
        EPL_TEAMS.put("Bournemouth", new TeamLogoConfig("BOU", "#DA291C",
                PL_LOGO_BASE + "t91.png"));
        EPL_TEAMS.put("Brentford", new TeamLogoConfig("BRE", "#E30613",
                PL_LOGO_BASE + "t94.png"));
        EPL_TEAMS.put("Brighton", new TeamLogoConfig("BHA", "#0057B8",
                PL_LOGO_BASE + "t36.png"));
        EPL_TEAMS.put("Chelsea", new TeamLogoConfig("CHE", "#034694",
                PL_LOGO_BASE + "t8.png"));
        EPL_TEAMS.put("Crystal Palace", new TeamLogoConfig("CRY", "#1B458F",
                PL_LOGO_BASE + "t31.png"));
        EPL_TEAMS.put("Everton", new TeamLogoConfig("EVE", "#003399",
                PL_LOGO_BASE + "t11.png"));
        EPL_TEAMS.put("Fulham", new TeamLogoConfig("FUL", "#000000",
                PL_LOGO_BASE + "t54.png"));
        EPL_TEAMS.put("Ipswich", new TeamLogoConfig("IPS", "#0044AA",
                PL_LOGO_BASE + "t40.png"));
        EPL_TEAMS.put("Ipswich Town", new TeamLogoConfig("IPS", "#0044AA",
                PL_LOGO_BASE + "t40.png"));
        EPL_TEAMS.put("Leicester", new TeamLogoConfig("LEI", "#003090",
                PL_LOGO_BASE + "t13.png"));
        EPL_TEAMS.put("Leicester City", new TeamLogoConfig("LEI", "#003090",
                PL_LOGO_BASE + "t13.png"));
        EPL_TEAMS.put("Liverpool", new TeamLogoConfig("LIV", "#C8102E",
                PL_LOGO_BASE + "t14.png"));
        EPL_TEAMS.put("Man City", new TeamLogoConfig("MCI", "#6CABDD",
                PL_LOGO_BASE + "t43.png"));
        EPL_TEAMS.put("Manchester City", new TeamLogoConfig("MCI", "#6CABDD",
                PL_LOGO_BASE + "t43.png"));
        EPL_TEAMS.put("Man United", new TeamLogoConfig("MUN", "#DA291C",
                PL_LOGO_BASE + "t1.png"));
        EPL_TEAMS.put("Manchester United", new TeamLogoConfig("MUN", "#DA291C",
                PL_LOGO_BASE + "t1.png"));
        EPL_TEAMS.put("Newcastle", new TeamLogoConfig("NEW", "#241F20",
                PL_LOGO_BASE + "t4.png"));
        EPL_TEAMS.put("Newcastle United", new TeamLogoConfig("NEW", "#241F20",
                PL_LOGO_BASE + "t4.png"));
        EPL_TEAMS.put("Nott'm Forest", new TeamLogoConfig("NFO", "#DD0000",
                PL_LOGO_BASE + "t17.png"));
        EPL_TEAMS.put("Nottingham Forest", new TeamLogoConfig("NFO", "#DD0000",
                PL_LOGO_BASE + "t17.png"));
        EPL_TEAMS.put("Southampton", new TeamLogoConfig("SOU", "#D71920",
                PL_LOGO_BASE + "t20.png"));
        EPL_TEAMS.put("Tottenham", new TeamLogoConfig("TOT", "#132257",
                PL_LOGO_BASE + "t6.png"));
        EPL_TEAMS.put("Spurs", new TeamLogoConfig("TOT", "#132257",
                PL_LOGO_BASE + "t6.png"));
        EPL_TEAMS.put("West Ham", new TeamLogoConfig("WHU", "#7A263A",
                PL_LOGO_BASE + "t21.png"));
        EPL_TEAMS.put("West Ham United", new TeamLogoConfig("WHU", "#7A263A",
                PL_LOGO_BASE + "t21.png"));
        EPL_TEAMS.put("Wolves", new TeamLogoConfig("WOL", "#FDB913",
                PL_LOGO_BASE + "t39.png"));
        EPL_TEAMS.put("Wolverhampton", new TeamLogoConfig("WOL", "#FDB913",
                PL_LOGO_BASE + "t39.png"));

        // Recently relegated/promoted teams
        EPL_TEAMS.put("Burnley", new TeamLogoConfig("BUR", "#6C1D45",
                PL_LOGO_BASE + "t90.png"));
        EPL_TEAMS.put("Luton", new TeamLogoConfig("LUT", "#F78F1E",
                PL_LOGO_BASE + "t163.png"));
        EPL_TEAMS.put("Luton Town", new TeamLogoConfig("LUT", "#F78F1E",
                PL_LOGO_BASE + "t163.png"));
        EPL_TEAMS.put("Sheffield United", new TeamLogoConfig("SHU", "#EE2737",
                PL_LOGO_BASE + "t49.png"));
        EPL_TEAMS.put("Sheffield Utd", new TeamLogoConfig("SHU", "#EE2737",
                PL_LOGO_BASE + "t49.png"));
        EPL_TEAMS.put("Leeds", new TeamLogoConfig("LEE", "#FFCD00",
                PL_LOGO_BASE + "t2.png"));
        EPL_TEAMS.put("Leeds United", new TeamLogoConfig("LEE", "#FFCD00",
                PL_LOGO_BASE + "t2.png"));

        // Historic EPL teams - use default logo for non-PL teams
        EPL_TEAMS.put("Watford", new TeamLogoConfig("WAT", "#FBEE23",
                PL_LOGO_BASE + "t57.png"));
        EPL_TEAMS.put("Norwich", new TeamLogoConfig("NOR", "#00A650",
                PL_LOGO_BASE + "t45.png"));
        EPL_TEAMS.put("Norwich City", new TeamLogoConfig("NOR", "#00A650",
                PL_LOGO_BASE + "t45.png"));
        EPL_TEAMS.put("West Brom", new TeamLogoConfig("WBA", "#122F67",
                PL_LOGO_BASE + "t35.png"));
        EPL_TEAMS.put("West Bromwich", new TeamLogoConfig("WBA", "#122F67",
                PL_LOGO_BASE + "t35.png"));
        EPL_TEAMS.put("Stoke", new TeamLogoConfig("STK", "#E03A3E",
                PL_LOGO_BASE + "t110.png"));
        EPL_TEAMS.put("Stoke City", new TeamLogoConfig("STK", "#E03A3E",
                PL_LOGO_BASE + "t110.png"));
        EPL_TEAMS.put("Swansea", new TeamLogoConfig("SWA", "#000000",
                PL_LOGO_BASE + "t80.png"));
        EPL_TEAMS.put("Swansea City", new TeamLogoConfig("SWA", "#000000",
                PL_LOGO_BASE + "t80.png"));
        EPL_TEAMS.put("Sunderland", new TeamLogoConfig("SUN", "#FF0000",
                PL_LOGO_BASE + "t56.png"));
        EPL_TEAMS.put("Hull", new TeamLogoConfig("HUL", "#F5A12D",
                PL_LOGO_BASE + "t88.png"));
        EPL_TEAMS.put("Hull City", new TeamLogoConfig("HUL", "#F5A12D",
                PL_LOGO_BASE + "t88.png"));
        EPL_TEAMS.put("Middlesbrough", new TeamLogoConfig("MID", "#E21E26",
                PL_LOGO_BASE + "t25.png"));
        EPL_TEAMS.put("Cardiff", new TeamLogoConfig("CAR", "#0070B5",
                PL_LOGO_BASE + "t97.png"));
        EPL_TEAMS.put("Cardiff City", new TeamLogoConfig("CAR", "#0070B5",
                PL_LOGO_BASE + "t97.png"));
        EPL_TEAMS.put("QPR", new TeamLogoConfig("QPR", "#1D5BA4",
                PL_LOGO_BASE + "t69.png"));
        EPL_TEAMS.put("Blackburn", new TeamLogoConfig("BLB", "#009EE0",
                PL_LOGO_BASE + "t5.png"));
        EPL_TEAMS.put("Blackburn Rovers", new TeamLogoConfig("BLB", "#009EE0",
                PL_LOGO_BASE + "t5.png"));
        EPL_TEAMS.put("Bolton", new TeamLogoConfig("BOL", "#263C7F",
                PL_LOGO_BASE + "t30.png"));
        EPL_TEAMS.put("Bolton Wanderers", new TeamLogoConfig("BOL", "#263C7F",
                PL_LOGO_BASE + "t30.png"));
        EPL_TEAMS.put("Wigan", new TeamLogoConfig("WIG", "#1D428A",
                PL_LOGO_BASE + "t44.png"));
        EPL_TEAMS.put("Wigan Athletic", new TeamLogoConfig("WIG", "#1D428A",
                PL_LOGO_BASE + "t44.png"));
        EPL_TEAMS.put("Reading", new TeamLogoConfig("REA", "#004494",
                PL_LOGO_BASE + "t68.png"));
        EPL_TEAMS.put("Birmingham", new TeamLogoConfig("BIR", "#0000FF",
                PL_LOGO_BASE + "t10.png"));
        EPL_TEAMS.put("Birmingham City", new TeamLogoConfig("BIR", "#0000FF",
                PL_LOGO_BASE + "t10.png"));
        EPL_TEAMS.put("Portsmouth", new TeamLogoConfig("POR", "#001489",
                PL_LOGO_BASE + "t15.png"));
        EPL_TEAMS.put("Charlton", new TeamLogoConfig("CHA", "#D4021D",
                PL_LOGO_BASE + "t9.png"));
        EPL_TEAMS.put("Charlton Athletic", new TeamLogoConfig("CHA", "#D4021D",
                PL_LOGO_BASE + "t9.png"));
        EPL_TEAMS.put("Derby", new TeamLogoConfig("DER", "#000000",
                PL_LOGO_BASE + "t26.png"));
        EPL_TEAMS.put("Derby County", new TeamLogoConfig("DER", "#000000",
                PL_LOGO_BASE + "t26.png"));
        EPL_TEAMS.put("Coventry", new TeamLogoConfig("COV", "#87CEEB",
                PL_LOGO_BASE + "t46.png"));
        EPL_TEAMS.put("Coventry City", new TeamLogoConfig("COV", "#87CEEB",
                PL_LOGO_BASE + "t46.png"));
        EPL_TEAMS.put("Blackpool", new TeamLogoConfig("BPL", "#F68712",
                PL_LOGO_BASE + "t92.png"));
        EPL_TEAMS.put("Bradford", new TeamLogoConfig("BRA", "#7B1E1E",
                DEFAULT_LOGO_URL));
        EPL_TEAMS.put("Bradford City", new TeamLogoConfig("BRA", "#7B1E1E",
                DEFAULT_LOGO_URL));
        EPL_TEAMS.put("Oldham", new TeamLogoConfig("OLD", "#003399",
                DEFAULT_LOGO_URL));
        EPL_TEAMS.put("Oldham Athletic", new TeamLogoConfig("OLD", "#003399",
                DEFAULT_LOGO_URL));
        EPL_TEAMS.put("Sheffield Wednesday", new TeamLogoConfig("SHW", "#0000FF",
                PL_LOGO_BASE + "t48.png"));
        EPL_TEAMS.put("Sheffield Weds", new TeamLogoConfig("SHW", "#0000FF",
                PL_LOGO_BASE + "t48.png"));
        EPL_TEAMS.put("Wimbledon", new TeamLogoConfig("WIM", "#00008B",
                DEFAULT_LOGO_URL));
        EPL_TEAMS.put("Barnsley", new TeamLogoConfig("BAR", "#FF0000",
                PL_LOGO_BASE + "t112.png"));
        EPL_TEAMS.put("Swindon", new TeamLogoConfig("SWI", "#FF0000",
                DEFAULT_LOGO_URL));
        EPL_TEAMS.put("Swindon Town", new TeamLogoConfig("SWI", "#FF0000",
                DEFAULT_LOGO_URL));
    }


    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("🎨 Starting team logo seeding with online URLs...");

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

        for (String teamName : allTeamNames) {
            Optional<Team> existingTeamOpt = teamRepository.findByName(teamName);
            TeamLogoConfig config = EPL_TEAMS.get(teamName);

            if (config != null) {
                // Team has known logo config - always update to ensure latest
                Team team = existingTeamOpt.orElse(Team.builder().name(teamName).build());
                team.setLogoUrl(config.logoUrl);
                team.setShortName(config.shortName);
                team.setPrimaryColor(config.primaryColor);
                teamRepository.save(team);

                if (existingTeamOpt.isPresent()) {
                    updated++;
                } else {
                    created++;
                }
            } else if (existingTeamOpt.isEmpty()) {
                // Unknown team - create with default logo
                Team team = Team.builder()
                        .name(teamName)
                        .logoUrl(DEFAULT_LOGO_URL)
                        .build();
                teamRepository.save(team);
                created++;
            } else {
                // Existing team without known config - update if logo is missing or local
                Team team = existingTeamOpt.get();
                if (team.getLogoUrl() == null ||
                    team.getLogoUrl().isEmpty() ||
                    team.getLogoUrl().startsWith("/images/")) {
                    team.setLogoUrl(DEFAULT_LOGO_URL);
                    teamRepository.save(team);
                    updated++;
                }
            }
        }

        log.info("   ✓ Team logo seeding complete. Created: {}, Updated: {}, Total teams: {}",
                created, updated, teamRepository.count());
    }

    /**
     * Manually trigger logo seeding (for API endpoint use).
     * @return Map with seeding statistics
     */
    @Transactional
    public Map<String, Object> seedLogos() {
        log.info("Manual team logo seeding triggered...");

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

        for (String teamName : allTeamNames) {
            Optional<Team> existingTeamOpt = teamRepository.findByName(teamName);
            TeamLogoConfig config = EPL_TEAMS.get(teamName);

            if (config != null) {
                Team team = existingTeamOpt.orElse(Team.builder().name(teamName).build());
                team.setLogoUrl(config.logoUrl);
                team.setShortName(config.shortName);
                team.setPrimaryColor(config.primaryColor);
                teamRepository.save(team);

                if (existingTeamOpt.isPresent()) {
                    updated++;
                } else {
                    created++;
                }
            } else if (existingTeamOpt.isEmpty()) {
                Team team = Team.builder()
                        .name(teamName)
                        .logoUrl(DEFAULT_LOGO_URL)
                        .build();
                teamRepository.save(team);
                created++;
            } else {
                // Existing team without known config - update if logo is missing or local
                Team team = existingTeamOpt.get();
                if (team.getLogoUrl() == null ||
                    team.getLogoUrl().isEmpty() ||
                    team.getLogoUrl().startsWith("/images/")) {
                    team.setLogoUrl(DEFAULT_LOGO_URL);
                    teamRepository.save(team);
                    updated++;
                }
            }
        }

        log.info("   ✓ Logo seeding complete. Created: {}, Updated: {}", created, updated);

        return Map.of(
                "success", true,
                "created", created,
                "updated", updated,
                "totalTeams", teamRepository.count()
        );
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

