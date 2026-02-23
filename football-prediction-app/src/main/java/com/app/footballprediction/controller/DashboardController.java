package com.app.footballprediction.controller;

import com.app.footballprediction.dto.LeagueStandingsResponse;
import com.app.footballprediction.dto.dashboard.ModelAccuracyResponse;
import com.app.footballprediction.dto.dashboard.TodaysPredictionsResponse;
import com.app.footballprediction.dto.dashboard.TopTeamsResponse;
import com.app.footballprediction.dto.dashboard.UpcomingMatchesResponse;
import com.app.footballprediction.service.DashboardService;
import com.app.footballprediction.service.LeagueStandingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for dashboard-specific endpoints.
 * All endpoints are optimized for <300ms response times.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;
    private final LeagueStandingService leagueStandingService;

    /**
     * Get upcoming matches for dashboard.
     * GET /api/dashboard/upcoming-matches
     */
    @GetMapping("/upcoming-matches")
    public ResponseEntity<UpcomingMatchesResponse> getUpcomingMatches() {
        log.debug("GET /api/dashboard/upcoming-matches");
        long startTime = System.currentTimeMillis();

        UpcomingMatchesResponse response = dashboardService.getUpcomingMatches();

        log.debug("Upcoming matches response time: {}ms", System.currentTimeMillis() - startTime);
        return ResponseEntity.ok(response);
    }

    /**
     * Get league standings for dashboard.
     * GET /api/dashboard/league-standings?leagueId=1&season=2025/26
     */
    @GetMapping("/league-standings")
    public ResponseEntity<LeagueStandingsResponse> getLeagueStandings(
            @RequestParam(required = false, defaultValue = "1") Long leagueId,
            @RequestParam(required = false) String season) {
        log.debug("GET /api/dashboard/league-standings leagueId={} season={}", leagueId, season);
        long startTime = System.currentTimeMillis();

        LeagueStandingsResponse response;
        if (season != null && !season.isEmpty()) {
            response = leagueStandingService.getLeagueTableForSeason(leagueId, season);
        } else {
            response = leagueStandingService.getCurrentLeagueTable(leagueId);
        }

        log.debug("League standings response time: {}ms", System.currentTimeMillis() - startTime);
        return ResponseEntity.ok(response);
    }

    /**
     * Get today's predictions for dashboard.
     * GET /api/dashboard/todays-predictions
     */
    @GetMapping("/todays-predictions")
    public ResponseEntity<TodaysPredictionsResponse> getTodaysPredictions() {
        log.debug("GET /api/dashboard/todays-predictions");
        long startTime = System.currentTimeMillis();

        TodaysPredictionsResponse response = dashboardService.getTodaysPredictions();

        log.debug("Today's predictions response time: {}ms", System.currentTimeMillis() - startTime);
        return ResponseEntity.ok(response);
    }

    /**
     * Get top teams for dashboard.
     * GET /api/dashboard/top-teams
     */
    @GetMapping("/top-teams")
    public ResponseEntity<TopTeamsResponse> getTopTeams() {
        log.debug("GET /api/dashboard/top-teams");
        long startTime = System.currentTimeMillis();

        TopTeamsResponse response = dashboardService.getTopTeams();

        log.debug("Top teams response time: {}ms", System.currentTimeMillis() - startTime);
        return ResponseEntity.ok(response);
    }

    /**
     * Get model accuracy stats for dashboard.
     * GET /api/dashboard/model-accuracy
     */
    @GetMapping("/model-accuracy")
    public ResponseEntity<ModelAccuracyResponse> getModelAccuracy() {
        log.debug("GET /api/dashboard/model-accuracy");
        long startTime = System.currentTimeMillis();

        ModelAccuracyResponse response = dashboardService.getModelAccuracy();

        log.debug("Model accuracy response time: {}ms", System.currentTimeMillis() - startTime);
        return ResponseEntity.ok(response);
    }
}

