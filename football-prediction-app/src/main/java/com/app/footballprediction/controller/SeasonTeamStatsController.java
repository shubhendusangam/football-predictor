package com.app.footballprediction.controller;

import com.app.footballprediction.dto.SeasonTeamStatsResponse;
import com.app.footballprediction.service.MatchCompletionService;
import com.app.footballprediction.service.SeasonTeamStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for season team statistics and Elo ratings.
 *
 * Endpoints:
 * - GET /api/season/{seasonId}/team/{teamId}/stats - Get team stats for a season
 * - GET /api/season/{seasonId}/stats - Get all team stats for a season
 * - GET /api/season/{seasonId}/elo-rankings - Get Elo rankings
 * - GET /api/season/{seasonId}/form-rankings - Get form rankings
 * - POST /api/season/{seasonId}/recalculate - Recalculate all stats
 */
@RestController
@RequestMapping("/api/season")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Teams", description = "Team listings, form, logos, stats, analytics, shot quality, fouls, and position history")
public class SeasonTeamStatsController {

    private final SeasonTeamStatsService seasonTeamStatsService;
    private final MatchCompletionService matchCompletionService;

    // ═══════════════════════════════════════════════════════════════════
    // Team Stats Endpoints
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get statistics for a specific team in a season by team ID.
     *
     * GET /api/season/{seasonId}/team/{teamId}/stats
     *
     * @param seasonId The season identifier (e.g., "2025-26")
     * @param teamId The team ID
     * @return Team statistics including Elo rating, form, goals
     */
    @GetMapping("/{seasonId}/team/{teamId}/stats")
    public ResponseEntity<?> getTeamStats(
            @PathVariable String seasonId,
            @PathVariable Long teamId) {
        try {
            log.info("Fetching stats for team {} in season {}", teamId, seasonId);

            return seasonTeamStatsService.getStatsBySeasonAndTeamId(seasonId, teamId)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Failed to fetch team stats: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch team statistics",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get statistics for a team by name in a season.
     *
     * GET /api/season/{seasonId}/team/stats?name={teamName}
     *
     * @param seasonId The season identifier
     * @param name The team name
     * @return Team statistics
     */
    @GetMapping("/{seasonId}/team/stats")
    public ResponseEntity<?> getTeamStatsByName(
            @PathVariable String seasonId,
            @RequestParam String name) {
        try {
            log.info("Fetching stats for team '{}' in season {}", name, seasonId);

            return seasonTeamStatsService.getStatsBySeasonAndTeamName(seasonId, name)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Failed to fetch team stats by name: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch team statistics",
                    "details", e.getMessage()
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Season Rankings Endpoints
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get all team stats for a season ordered by points.
     *
     * GET /api/season/{seasonId}/stats
     *
     * @param seasonId The season identifier
     * @return List of all team statistics ordered by points
     */
    @GetMapping("/{seasonId}/stats")
    public ResponseEntity<?> getSeasonStats(@PathVariable String seasonId) {
        try {
            log.info("Fetching all team stats for season {}", seasonId);
            List<SeasonTeamStatsResponse> stats = seasonTeamStatsService.getSeasonStatsOrderedByPoints(seasonId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to fetch season stats: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch season statistics",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get Elo rankings for a season.
     *
     * GET /api/season/{seasonId}/elo-rankings
     *
     * @param seasonId The season identifier
     * @return List of teams ordered by Elo rating
     */
    @GetMapping("/{seasonId}/elo-rankings")
    public ResponseEntity<?> getEloRankings(@PathVariable String seasonId) {
        try {
            log.info("Fetching Elo rankings for season {}", seasonId);
            List<SeasonTeamStatsResponse> rankings = seasonTeamStatsService.getSeasonStatsOrderedByElo(seasonId);

            double avgElo = seasonTeamStatsService.getAverageEloRating(seasonId);

            return ResponseEntity.ok(Map.of(
                    "seasonId", seasonId,
                    "averageElo", Math.round(avgElo * 100.0) / 100.0,
                    "teamCount", rankings.size(),
                    "rankings", rankings
            ));
        } catch (Exception e) {
            log.error("Failed to fetch Elo rankings: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch Elo rankings",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get form rankings for a season.
     *
     * GET /api/season/{seasonId}/form-rankings?limit=10
     *
     * @param seasonId The season identifier
     * @param limit Maximum number of teams (default 10)
     * @return Top teams by recent form
     */
    @GetMapping("/{seasonId}/form-rankings")
    public ResponseEntity<?> getFormRankings(
            @PathVariable String seasonId,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            log.info("Fetching top {} teams by form for season {}", limit, seasonId);
            List<SeasonTeamStatsResponse> topForm = seasonTeamStatsService.getTopTeamsByForm(seasonId, limit);

            return ResponseEntity.ok(Map.of(
                    "seasonId", seasonId,
                    "rankings", topForm
            ));
        } catch (Exception e) {
            log.error("Failed to fetch form rankings: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch form rankings",
                    "details", e.getMessage()
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Streak Endpoints
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get teams on winning streaks.
     *
     * GET /api/season/{seasonId}/winning-streaks
     *
     * @param seasonId The season identifier
     * @return List of teams on winning streaks
     */
    @GetMapping("/{seasonId}/winning-streaks")
    public ResponseEntity<?> getWinningStreaks(@PathVariable String seasonId) {
        try {
            log.info("Fetching teams on winning streaks for season {}", seasonId);
            List<SeasonTeamStatsResponse> teams = seasonTeamStatsService.getTeamsOnWinningStreak(seasonId);
            return ResponseEntity.ok(Map.of(
                    "seasonId", seasonId,
                    "teams", teams
            ));
        } catch (Exception e) {
            log.error("Failed to fetch winning streaks: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch winning streaks",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get teams on losing streaks.
     *
     * GET /api/season/{seasonId}/losing-streaks
     *
     * @param seasonId The season identifier
     * @return List of teams on losing streaks
     */
    @GetMapping("/{seasonId}/losing-streaks")
    public ResponseEntity<?> getLosingStreaks(@PathVariable String seasonId) {
        try {
            log.info("Fetching teams on losing streaks for season {}", seasonId);
            List<SeasonTeamStatsResponse> teams = seasonTeamStatsService.getTeamsOnLosingStreak(seasonId);
            return ResponseEntity.ok(Map.of(
                    "seasonId", seasonId,
                    "teams", teams
            ));
        } catch (Exception e) {
            log.error("Failed to fetch losing streaks: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch losing streaks",
                    "details", e.getMessage()
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Admin Endpoints
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Recalculate all statistics for a season.
     * This is an admin operation that deletes and recalculates all stats.
     *
     * POST /api/season/{seasonId}/recalculate
     *
     * @param seasonId The season to recalculate
     * @return Success message
     */
    @PostMapping("/{seasonId}/recalculate")
    public ResponseEntity<?> recalculateSeasonStats(@PathVariable String seasonId) {
        try {
            log.info("Recalculating stats for season {}", seasonId);
            long startTime = System.currentTimeMillis();

            matchCompletionService.recalculateSeasonStats(seasonId);

            long duration = System.currentTimeMillis() - startTime;
            long teamCount = seasonTeamStatsService.getTeamCount(seasonId);

            return ResponseEntity.ok(Map.of(
                    "message", "Successfully recalculated season statistics",
                    "seasonId", seasonId,
                    "teamsProcessed", teamCount,
                    "durationMs", duration
            ));
        } catch (Exception e) {
            log.error("Failed to recalculate season stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to recalculate season statistics",
                    "details", e.getMessage()
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Team History Endpoint
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Get historical statistics for a team across all seasons.
     *
     * GET /api/season/team/{teamId}/history
     *
     * @param teamId The team ID
     * @return List of stats for each season
     */
    @GetMapping("/team/{teamId}/history")
    public ResponseEntity<?> getTeamHistory(@PathVariable Long teamId) {
        try {
            log.info("Fetching historical stats for team {}", teamId);
            List<SeasonTeamStatsResponse> history = seasonTeamStatsService.getTeamHistoricalStats(teamId);
            return ResponseEntity.ok(Map.of(
                    "teamId", teamId,
                    "seasons", history
            ));
        } catch (Exception e) {
            log.error("Failed to fetch team history: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch team history",
                    "details", e.getMessage()
            ));
        }
    }
}
