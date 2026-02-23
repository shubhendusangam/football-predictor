package com.app.footballprediction.controller;

import com.app.footballprediction.dto.LeagueStatsResponse;
import com.app.footballprediction.dto.PreMatchInsightsResponse;
import com.app.footballprediction.dto.H2HInsightsResponse;
import com.app.footballprediction.dto.TrendingInsightsResponse;
import com.app.footballprediction.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for analytics endpoints.
 * Provides comprehensive statistics, insights, and analysis features.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final LeagueStatsService leagueStatsService;
    private final PreMatchInsightsService preMatchInsightsService;
    private final H2HInsightsService h2hInsightsService;
    private final TrendingInsightsService trendingInsightsService;

    // ========== League Statistics ==========

    /**
     * Get comprehensive league statistics.
     *
     * GET /api/analytics/league/stats
     *
     * @return LeagueStatsResponse with season overview, trends, records
     */
    @GetMapping("/league/stats")
    public ResponseEntity<?> getLeagueStats() {
        try {
            log.info("Fetching league statistics");
            LeagueStatsResponse stats = leagueStatsService.getLeagueStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to fetch league stats: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch league statistics",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get current season statistics only.
     *
     * GET /api/analytics/league/current-season
     *
     * @return LeagueStatsResponse for current season
     */
    @GetMapping("/league/current-season")
    public ResponseEntity<?> getCurrentSeasonStats() {
        try {
            log.info("Fetching current season statistics");
            LeagueStatsResponse stats = leagueStatsService.getCurrentSeasonStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to fetch current season stats: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch current season statistics",
                    "details", e.getMessage()
            ));
        }
    }

    // ========== Pre-Match Insights ==========

    /**
     * Get comprehensive pre-match insights for a fixture.
     *
     * GET /api/analytics/pre-match?homeTeam=Arsenal&awayTeam=Chelsea
     *
     * @param homeTeam Home team name
     * @param awayTeam Away team name
     * @return PreMatchInsightsResponse with form, streaks, rest analysis
     */
    @GetMapping("/pre-match")
    public ResponseEntity<?> getPreMatchInsights(
            @RequestParam String homeTeam,
            @RequestParam String awayTeam) {
        try {
            log.info("Fetching pre-match insights for {} vs {}", homeTeam, awayTeam);
            PreMatchInsightsResponse insights = preMatchInsightsService.getPreMatchInsights(homeTeam, awayTeam);
            return ResponseEntity.ok(insights);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid team: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Team not found",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to fetch pre-match insights: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch pre-match insights",
                    "details", e.getMessage()
            ));
        }
    }

    // ========== Head-to-Head Insights ==========

    /**
     * Get enhanced H2H insights between two teams.
     *
     * GET /api/analytics/h2h?homeTeam=Arsenal&awayTeam=Chelsea
     *
     * @param homeTeam Home team name
     * @param awayTeam Away team name
     * @return H2HInsightsResponse with historical record, recent meetings
     */
    @GetMapping("/h2h")
    public ResponseEntity<?> getH2HInsights(
            @RequestParam String homeTeam,
            @RequestParam String awayTeam) {
        try {
            log.info("Fetching H2H insights for {} vs {}", homeTeam, awayTeam);
            H2HInsightsResponse insights = h2hInsightsService.getH2HInsights(homeTeam, awayTeam);
            return ResponseEntity.ok(insights);
        } catch (Exception e) {
            log.error("Failed to fetch H2H insights: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch H2H insights",
                    "details", e.getMessage()
            ));
        }
    }


    // ========== Trending Insights ==========

    /**
     * Get all trending insights (hot/cold teams, upset alerts, etc).
     * If no season is specified, uses the current season.
     *
     * GET /api/analytics/trends
     * GET /api/analytics/trends?season=2024-25
     *
     * @param season Optional season filter (e.g., "2024-25")
     * @return TrendingInsightsResponse with all trending data for the specified season
     */
    @GetMapping("/trends")
    public ResponseEntity<?> getTrendingInsights(
            @RequestParam(required = false) String season) {
        try {
            TrendingInsightsResponse insights;
            if (season != null && !season.isBlank()) {
                log.info("Fetching trending insights for season: {}", season);
                insights = trendingInsightsService.getTrendingInsightsBySeason(season);
            } else {
                log.info("Fetching trending insights for current season");
                insights = trendingInsightsService.getTrendingInsights();
            }
            return ResponseEntity.ok(insights);
        } catch (Exception e) {
            log.error("Failed to fetch trending insights: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch trending insights",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get list of available seasons for insights.
     *
     * GET /api/analytics/seasons
     *
     * @return List of available season identifiers
     */
    @GetMapping("/seasons")
    public ResponseEntity<?> getAvailableSeasons() {
        try {
            log.info("Fetching available seasons");
            var seasons = trendingInsightsService.getAvailableSeasons();
            String currentSeason = trendingInsightsService.getCurrentSeason();
            return ResponseEntity.ok(Map.of(
                    "seasons", seasons,
                    "currentSeason", currentSeason != null ? currentSeason : ""
            ));
        } catch (Exception e) {
            log.error("Failed to fetch available seasons: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch available seasons",
                    "details", e.getMessage()
            ));
        }
    }

    // ========== Combined Match Analysis ==========

    /**
     * Get complete match analysis combining all insights.
     *
     * GET /api/analytics/match?homeTeam=Arsenal&awayTeam=Chelsea
     *
     * @param homeTeam Home team name
     * @param awayTeam Away team name
     * @return Combined analysis with all available data
     */
    @GetMapping("/match")
    public ResponseEntity<?> getCompleteMatchAnalysis(
            @RequestParam String homeTeam,
            @RequestParam String awayTeam) {
        try {
            log.info("Fetching complete match analysis for {} vs {}", homeTeam, awayTeam);

            // Fetch all insights in parallel would be ideal, but for simplicity:
            PreMatchInsightsResponse preMatch = preMatchInsightsService.getPreMatchInsights(homeTeam, awayTeam);
            H2HInsightsResponse h2h = h2hInsightsService.getH2HInsights(homeTeam, awayTeam);

            // Use normalized team names from preMatch response for consistency
            return ResponseEntity.ok(Map.of(
                    "homeTeam", preMatch.getHomeTeam(),
                    "awayTeam", preMatch.getAwayTeam(),
                    "preMatchInsights", preMatch,
                    "h2hInsights", h2h
            ));
        } catch (Exception e) {
            log.error("Failed to fetch complete match analysis: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch match analysis",
                    "details", e.getMessage()
            ));
        }
    }
}

