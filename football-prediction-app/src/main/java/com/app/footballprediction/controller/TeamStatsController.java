package com.app.footballprediction.controller;

import com.app.footballprediction.dto.TeamStatsResponse;
import com.app.footballprediction.service.TeamStatsService;
import com.app.common.service.FeatureEngineeringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final FeatureEngineeringService featureEngineeringService;

    /**
     * Get all available team names.
     *
     * GET /api/teams
     *
     * @return List of all team names
     */
    @GetMapping
    public ResponseEntity<?> getAllTeams() {
        try {
            log.info("Fetching all team names");
            var teams = featureEngineeringService.getAllTeams();
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
}

