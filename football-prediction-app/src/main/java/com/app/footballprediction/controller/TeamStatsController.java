package com.app.footballprediction.controller;

import com.app.common.dto.ShotQualityDTO;
import com.app.footballprediction.config.TeamLogoSeeder;
import com.app.footballprediction.dto.FoulsAnalysisDTO;
import com.app.footballprediction.dto.TeamAnalyticsDto;
import com.app.footballprediction.dto.TeamDTO;
import com.app.footballprediction.dto.TeamFormResponse;
import com.app.footballprediction.dto.TeamStatsResponse;
import com.app.footballprediction.service.FoulsAnalysisService;
import com.app.footballprediction.service.ShotQualityService;
import com.app.footballprediction.service.TeamAnalyticsService;
import com.app.footballprediction.service.TeamService;
import com.app.footballprediction.service.TeamStatsService;
import com.app.common.service.FeatureEngineeringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for team statistics endpoints.
 *
 * Provides comprehensive statistics for each team including:
 * - Overall stats (W/D/L, goals, points)
 * - Home vs Away performance
 * - Goal patterns (first half vs second half)
 * - Form and momentum (streaks, recent form)
 * - Head-to-head records against rivals
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Slf4j
public class TeamStatsController {

    private final TeamStatsService teamStatsService;
    private final TeamAnalyticsService teamAnalyticsService;
    private final TeamService teamService;
    private final TeamLogoSeeder teamLogoSeeder;
    private final FeatureEngineeringService featureEngineeringService;
    private final ShotQualityService shotQualityService;
    private final FoulsAnalysisService foulsAnalysisService;

    /**
     * Get all available teams with logo information.
     *
     * GET /api/teams
     * GET /api/teams?season=2025-26
     *
     * @param season Optional season filter (e.g., "2025-26"). If not provided, returns all teams.
     * @return List of teams with logos
     */
    @GetMapping
    public ResponseEntity<?> getAllTeams(@RequestParam(required = false) String season) {
        try {
            List<TeamDTO> teams;

            if (season != null && !season.isBlank()) {
                log.info("Fetching teams for season: {}", season);
                teams = teamService.getTeamsBySeason(season);

                if (teams.isEmpty()) {
                    return ResponseEntity.ok(Map.of(
                            "teams", teams,
                            "season", season,
                            "message", "No teams found for season " + season
                    ));
                }

                return ResponseEntity.ok(Map.of(
                        "teams", teams,
                        "season", season,
                        "count", teams.size()
                ));
            }

            // No season specified - return all teams
            log.info("Fetching all teams with logos");
            teams = teamService.getAllTeams();

            // If no teams in database yet, fall back to legacy method
            if (teams.isEmpty()) {
                log.info("No teams in database, falling back to match-derived teams");
                var teamNames = featureEngineeringService.getAllTeams();
                teams = teamNames.stream()
                        .map(TeamDTO::fromName)
                        .toList();
            }

            return ResponseEntity.ok(teams);
        } catch (Exception e) {
            log.error("Failed to fetch teams: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch teams",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get all available seasons for the teams filter.
     *
     * GET /api/teams/seasons
     *
     * @return List of seasons sorted descending (newest first)
     */
    @GetMapping("/seasons")
    public ResponseEntity<?> getAvailableSeasons() {
        try {
            log.info("Fetching available seasons for teams filter");
            List<String> seasons = teamService.getAllSeasons();
            return ResponseEntity.ok(Map.of(
                    "seasons", seasons,
                    "count", seasons.size()
            ));
        } catch (Exception e) {
            log.error("Failed to fetch seasons: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch seasons",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get team form insights for the prediction view.
     *
     * GET /api/teams/form?team=Arsenal
     *
     * @param team The team name (e.g., "Arsenal", "Man City")
     * @return TeamFormResponse with form insights
     */
    @GetMapping("/form")
    public ResponseEntity<?> getTeamFormInsights(@RequestParam String team) {
        try {
            log.info("Fetching form insights for team: {}", team);
            TeamFormResponse formInsights = teamStatsService.getTeamFormInsights(team);
            return ResponseEntity.ok(formInsights);
        } catch (IllegalArgumentException e) {
            log.warn("Team not found for form insights: {} - {}", team, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Team not found",
                    "message", e.getMessage(),
                    "suggestion", "Use GET /api/teams to see available teams"
            ));
        } catch (Exception e) {
            log.error("Failed to fetch team form insights for {}: {}", team, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch team form insights",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get team logo URL by team name.
     *
     * GET /api/teams/logo?team=Arsenal
     *
     * @param team The team name
     * @return Logo URL for the team
     */
    @GetMapping("/logo")
    public ResponseEntity<?> getTeamLogo(@RequestParam String team) {
        try {
            log.info("Fetching logo for team: {}", team);
            String logoUrl = teamService.getTeamLogoUrl(team);
            return ResponseEntity.ok(Map.of(
                    "team", team,
                    "logoUrl", logoUrl
            ));
        } catch (Exception e) {
            log.error("Failed to fetch team logo for {}: {}", team, e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "team", team,
                    "logoUrl", TeamDTO.DEFAULT_LOGO
            ));
        }
    }

    /**
     * Get logo URLs for multiple teams at once.
     *
     * GET /api/teams/logos
     *
     * @return Map of team names to their logo URLs
     */
    @GetMapping("/logos")
    public ResponseEntity<?> getAllTeamLogos() {
        try {
            log.info("Fetching all team logos");
            Map<String, String> logoMap = teamService.getTeamLogoMap();
            return ResponseEntity.ok(logoMap);
        } catch (Exception e) {
            log.error("Failed to fetch team logos: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch team logos",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get logo seeding status - shows when logos were last seeded and for which season.
     *
     * GET /api/teams/logo-status
     *
     * @return Logo seeding status information
     */
    @GetMapping("/logo-status")
    public ResponseEntity<?> getLogoSeedingStatus() {
        try {
            log.info("Fetching logo seeding status");
            Map<String, Object> status = teamLogoSeeder.getLogoSeedingStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to fetch logo seeding status: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch logo seeding status",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Search for teams by partial name match.
     * Useful for autocomplete and team name validation.
     *
     * GET /api/teams/search?q=man
     *
     * @param q The search query (partial team name)
     * @param limit Maximum number of results (default: 10)
     * @return List of matching team names
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchTeams(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            log.info("Searching teams with query: '{}'", q);

            if (q == null || q.trim().length() < 2) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Search query too short",
                        "message", "Please provide at least 2 characters"
                ));
            }

            var allTeams = featureEngineeringService.getAllTeams();
            String searchLower = q.trim().toLowerCase();

            // Find teams that contain the search query (case-insensitive)
            var matchingTeams = allTeams.stream()
                    .filter(team -> team.toLowerCase().contains(searchLower))
                    .limit(limit)
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "query", q,
                    "results", matchingTeams,
                    "count", matchingTeams.size()
            ));
        } catch (Exception e) {
            log.error("Failed to search teams: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to search teams",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get team summaries with basic W/D/L statistics for dashboard display.
     * <p>
     * GET /api/teams/summary?limit=10
     *
     * @param limit Number of teams to return (default: 10)
     * @return List of team summaries with name, wins, draws, losses, points
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getTeamSummaries(@RequestParam(defaultValue = "10") int limit) {
        try {
            log.info("Fetching team summaries (limit: {})", limit);
            var allTeams = featureEngineeringService.getAllTeams();

            var teamSummaries = allTeams.stream()
                    .limit(limit)
                    .map(teamName -> {
                        try {
                            var stats = teamStatsService.getTeamStats(teamName);
                            return Map.of(
                                    "name", teamName,
                                    "wins", stats.getOverall().getWins(),
                                    "draws", stats.getOverall().getDraws(),
                                    "losses", stats.getOverall().getLosses(),
                                    "points", stats.getOverall().getPoints(),
                                    "goalsScored", stats.getOverall().getGoalsScored(),
                                    "goalsConceded", stats.getOverall().getGoalsConceded(),
                                    "logo", "⚽"
                            );
                        } catch (Exception e) {
                            log.warn("Failed to fetch stats for team {}: {}", teamName, e.getMessage());
                            return Map.of(
                                    "name", teamName,
                                    "wins", 0,
                                    "draws", 0,
                                    "losses", 0,
                                    "points", 0,
                                    "goalsScored", 0,
                                    "goalsConceded", 0,
                                    "logo", "⚽"
                            );
                        }
                    })
                    .sorted((a, b) -> Integer.compare((Integer) b.get("points"), (Integer) a.get("points")))
                    .toList();

            return ResponseEntity.ok(teamSummaries);
        } catch (Exception e) {
            log.error("Failed to fetch team summaries: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch team summaries",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get comprehensive statistics for a specific team.
     *
     * GET /api/teams/{teamName}/stats
     *
     * @param teamName The team name (e.g., "Arsenal", "Man City")
     * @return TeamStatsResponse with all statistics
     */
    @GetMapping("/{teamName}/stats")
    public ResponseEntity<?> getTeamStats(@PathVariable String teamName) {
        try {
            log.info("Fetching stats for team: {}", teamName);
            TeamStatsResponse stats = teamStatsService.getTeamStats(teamName);
            return ResponseEntity.ok(stats);
        } catch (IllegalArgumentException e) {
            log.warn("Team not found: {} - {}", teamName, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Team not found",
                    "message", e.getMessage(),
                    "suggestion", "Use GET /api/teams to see available teams"
            ));
        } catch (Exception e) {
            log.error("Failed to fetch team stats for {}: {}", teamName, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch team statistics",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get comprehensive analytics for a team.
     * Includes upcoming matches, season history, model accuracy,
     * prediction comparisons, and home/away trends.
     *
     * GET /api/teams/{teamName}/analytics
     *
     * @param teamName The team name (e.g., "Arsenal", "Man City")
     * @return TeamAnalyticsDto with comprehensive analytics
     */
    @GetMapping("/{teamName}/analytics")
    public ResponseEntity<?> getTeamAnalytics(@PathVariable String teamName) {
        try {
            // Input validation
            if (teamName == null || teamName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid team name",
                        "message", "Team name cannot be empty"
                ));
            }

            // Sanitize input - remove potentially harmful characters
            String sanitizedTeamName = teamName.trim()
                    .replaceAll("[<>\"'&;]", "")  // Remove XSS vectors
                    .substring(0, Math.min(teamName.trim().length(), 100));  // Limit length

            if (sanitizedTeamName.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid team name",
                        "message", "Team name contains invalid characters"
                ));
            }

            log.info("Fetching analytics for team: {}", sanitizedTeamName);
            TeamAnalyticsDto analytics = teamAnalyticsService.getTeamAnalytics(sanitizedTeamName);
            return ResponseEntity.ok(analytics);
        } catch (IllegalArgumentException e) {
            log.warn("Team not found for analytics: {} - {}", teamName, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Team not found",
                    "message", e.getMessage(),
                    "suggestion", "Use GET /api/teams to see available teams"
            ));
        } catch (Exception e) {
            log.error("Failed to fetch team analytics for {}: {}", teamName, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch team analytics",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get fouls and discipline analysis for a team.
     * Provides comprehensive fouls statistics including averages, discipline score,
     * and win rates based on foul counts.
     *
     * GET /api/teams/{teamName}/fouls-analysis
     * GET /api/teams/{teamName}/fouls-analysis?isHome=true
     *
     * @param teamName The team name (e.g., "Arsenal", "Man City")
     * @param isHome Whether to analyze home matches only (default: true)
     * @return FoulsAnalysisDTO with comprehensive fouls metrics
     */
    @GetMapping("/{teamName}/fouls-analysis")
    public ResponseEntity<?> getFoulsAnalysis(
            @PathVariable String teamName,
            @RequestParam(defaultValue = "true") boolean isHome) {
        try {
            // Validate input
            if (teamName == null || teamName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid team name",
                        "message", "Team name cannot be empty"
                ));
            }

            // Sanitize input - remove potentially harmful characters
            String sanitizedTeamName = teamName.trim()
                    .replaceAll("[<>\"'&;]", "")
                    .substring(0, Math.min(teamName.trim().length(), 100));

            if (sanitizedTeamName.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid team name",
                        "message", "Team name contains invalid characters"
                ));
            }

            log.info("Fetching fouls analysis for team: {} (isHome: {})", sanitizedTeamName, isHome);
            FoulsAnalysisDTO analysis = foulsAnalysisService.analyzeFouls(sanitizedTeamName, isHome);
            return ResponseEntity.ok(analysis);
        } catch (IllegalArgumentException e) {
            log.warn("Team not found for fouls analysis: {} - {}", teamName, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Team not found",
                    "message", e.getMessage(),
                    "suggestion", "Use GET /api/teams to see available teams"
            ));
        } catch (Exception e) {
            log.error("Failed to fetch fouls analysis for {}: {}", teamName, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch fouls analysis",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get shot quality analysis for a team.
     * Provides comprehensive shot efficiency metrics including accuracy,
     * conversion rate, quality score, and comparison with league average.
     *
     * GET /api/teams/{teamName}/shot-quality
     * GET /api/teams/{teamName}/shot-quality?split=true (for home/away split)
     *
     * @param teamName The team name (e.g., "Arsenal", "Man City")
     * @param split If true, returns separate home and away analysis
     * @return ShotQualityDTO or home/away split response
     */
    @GetMapping("/{teamName}/shot-quality")
    public ResponseEntity<?> getShotQuality(
            @PathVariable String teamName,
            @RequestParam(defaultValue = "false") boolean split) {
        try {
            // Input validation
            if (teamName == null || teamName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid team name",
                        "message", "Team name cannot be empty"
                ));
            }

            // Sanitize input
            String sanitizedTeamName = teamName.trim()
                    .replaceAll("[<>\"'&;]", "")
                    .substring(0, Math.min(teamName.trim().length(), 100));

            if (sanitizedTeamName.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid team name",
                        "message", "Team name contains invalid characters"
                ));
            }

            log.info("Fetching shot quality for team: {} (split={})", sanitizedTeamName, split);

            if (split) {
                // Return home and away shot quality side by side
                ShotQualityDTO homeQuality = shotQualityService.calculateShotQuality(sanitizedTeamName, true);
                ShotQualityDTO awayQuality = shotQualityService.calculateShotQuality(sanitizedTeamName, false);

                return ResponseEntity.ok(Map.of(
                        "teamName", homeQuality.getTeamName(),
                        "home", homeQuality,
                        "away", awayQuality
                ));
            } else {
                // Return combined shot quality
                ShotQualityDTO quality = shotQualityService.calculateCombinedShotQuality(sanitizedTeamName);
                return ResponseEntity.ok(quality);
            }

        } catch (IllegalArgumentException e) {
            log.warn("Team not found for shot quality: {} - {}", teamName, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Team not found",
                    "message", e.getMessage(),
                    "suggestion", "Use GET /api/teams to see available teams"
            ));
        } catch (Exception e) {
            log.error("Failed to fetch shot quality for {}: {}", teamName, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch shot quality analysis",
                    "details", e.getMessage()
            ));
        }
    }


    /**
     * Compare two teams side-by-side.
     *
     * GET /api/teams/compare?team1=Arsenal&team2=Chelsea
     *
     * @param team1 First team name
     * @param team2 Second team name
     * @return Comparison of both teams' statistics
     */
    @GetMapping("/compare")
    public ResponseEntity<?> compareTeams(
            @RequestParam String team1,
            @RequestParam String team2) {
        try {
            log.info("Comparing teams: {} vs {}", team1, team2);

            TeamStatsResponse stats1 = teamStatsService.getTeamStats(team1);
            TeamStatsResponse stats2 = teamStatsService.getTeamStats(team2);

            return ResponseEntity.ok(Map.of(
                    "team1", stats1,
                    "team2", stats2,
                    "comparison", buildComparison(stats1, stats2)
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Team comparison failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Team not found",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to compare teams: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to compare teams",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Build a quick comparison summary between two teams.
     */
    private Map<String, Object> buildComparison(TeamStatsResponse stats1, TeamStatsResponse stats2) {
        return Map.of(
                "winPercentage", Map.of(
                        stats1.getTeamName(), stats1.getOverall().getWinPercentage(),
                        stats2.getTeamName(), stats2.getOverall().getWinPercentage(),
                        "advantage", stats1.getOverall().getWinPercentage() > stats2.getOverall().getWinPercentage()
                                ? stats1.getTeamName() : stats2.getTeamName()
                ),
                "goalsScored", Map.of(
                        stats1.getTeamName(), stats1.getOverall().getGoalsScored(),
                        stats2.getTeamName(), stats2.getOverall().getGoalsScored(),
                        "advantage", stats1.getOverall().getGoalsScored() > stats2.getOverall().getGoalsScored()
                                ? stats1.getTeamName() : stats2.getTeamName()
                ),
                "recentForm", Map.of(
                        stats1.getTeamName(), stats1.getFormStats().getLast5Form(),
                        stats2.getTeamName(), stats2.getFormStats().getLast5Form(),
                        "advantage", stats1.getFormStats().getLast5FormPoints() > stats2.getFormStats().getLast5FormPoints()
                                ? stats1.getTeamName() : stats2.getTeamName()
                ),
                "cleanSheets", Map.of(
                        stats1.getTeamName(), stats1.getGoalStats().getCleanSheetPercentage(),
                        stats2.getTeamName(), stats2.getGoalStats().getCleanSheetPercentage(),
                        "advantage", stats1.getGoalStats().getCleanSheetPercentage() > stats2.getGoalStats().getCleanSheetPercentage()
                                ? stats1.getTeamName() : stats2.getTeamName()
                )
        );
    }

    /**
     * Clear team logo cache.
     * Admin-only endpoint to refresh team logo data.
     *
     * DELETE /api/teams/cache
     *
     * @return Success message
     */
    @DeleteMapping("/cache")
    public ResponseEntity<?> clearTeamCache() {
        try {
            log.info("Clearing team logo cache");
            teamService.clearCache();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Team logo cache cleared successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to clear team cache: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to clear team cache",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Seed team logos from online sources.
     * Admin-only endpoint to populate team logo URLs.
     *
     * By default, only seeds logos if needed for the current season.
     * Use forceRefresh=true to refresh all logos regardless.
     *
     * POST /api/teams/seed-logos?forceRefresh=false
     *
     * @param forceRefresh if true, refresh all logos regardless of season status
     * @return Seeding statistics
     */
    @PostMapping("/seed-logos")
    public ResponseEntity<?> seedTeamLogos(
            @RequestParam(defaultValue = "false") boolean forceRefresh) {
        try {
            log.info("Manually seeding team logos (forceRefresh={})", forceRefresh);
            Map<String, Object> result = teamLogoSeeder.seedLogos(forceRefresh);
            // Clear cache after seeding to ensure fresh data
            teamService.clearCache();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to seed team logos: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to seed team logos",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Clear team analytics cache.
     * Admin-only endpoint to refresh analytics data.
     *
     * DELETE /api/teams/analytics/cache
     *
     * @return Success message
     */
    @DeleteMapping("/analytics/cache")
    public ResponseEntity<?> clearAnalyticsCache() {
        try {
            log.info("Clearing team analytics cache");
            teamAnalyticsService.evictAllAnalyticsCache();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Team analytics cache cleared successfully"
            ));
        } catch (Exception e) {
            log.error("Failed to clear analytics cache: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to clear analytics cache",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Clear analytics cache for a specific team.
     * Admin-only endpoint to refresh analytics data for one team.
     *
     * DELETE /api/teams/{teamName}/analytics/cache
     *
     * @param teamName The team name
     * @return Success message
     */
    @DeleteMapping("/{teamName}/analytics/cache")
    public ResponseEntity<?> clearTeamAnalyticsCache(@PathVariable String teamName) {
        try {
            log.info("Clearing analytics cache for team: {}", teamName);
            teamAnalyticsService.evictTeamAnalyticsCache(teamName);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Analytics cache cleared for team: " + teamName
            ));
        } catch (Exception e) {
            log.error("Failed to clear analytics cache for {}: {}", teamName, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to clear analytics cache",
                    "details", e.getMessage()
            ));
        }
    }
}

