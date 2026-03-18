package com.app.footballprediction.controller;

import com.app.footballprediction.dto.CornerPredictionDTO;
import com.app.footballprediction.dto.CornerStatsDTO;
import com.app.footballprediction.service.CornerStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for corner kick statistics and predictions.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Analytics", description = "League statistics, pre-match insights, H2H analysis, and trending data")
public class CornerStatsController {

    private final CornerStatsService cornerStatsService;

    // ══════════════════════════════════════════════════════════════════════
    // TEAM CORNER STATISTICS ENDPOINT
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Get corner kick statistics for a specific team.
     *
     * <p><strong>Example Request:</strong></p>
     * <pre>
     * GET /api/teams/Arsenal/corner-stats
     * GET /api/teams/Arsenal/corner-stats?isHome=true
     * GET /api/teams/Man%20City/corner-stats?isHome=false
     * </pre>
     *
     * <p><strong>Response:</strong></p>
     * <pre>
     * {
     *   "teamName": "Arsenal",
     *   "isHome": true,
     *   "avgCornersWon": 6.45,
     *   "avgCornersAgainst": 4.20,
     *   "cornerDominance": 0.606,
     *   "successRate": 0.583,
     *   "matchesAnalyzed": 20,
     *   "totalCornersWon": 129,
     *   "totalCornersAgainst": 84,
     *   "weightedAvgCorners": 6.78
     * }
     * </pre>
     *
     * @param teamName Team name (URL encoded if contains spaces)
     * @param isHome   Optional filter: true = home matches only, false = away only, null = all
     * @return Corner statistics for the team
     */
    @GetMapping("/teams/{teamName}/corner-stats")
    public ResponseEntity<CornerStatsDTO> getCornerStats(
            @PathVariable String teamName,
            @RequestParam(required = false) Boolean isHome) {

        log.info("GET /api/teams/{}/corner-stats - isHome: {}", teamName, isHome);

        // Validate team name
        if (teamName == null || teamName.trim().isEmpty()) {
            log.warn("Invalid request: empty team name");
            throw new IllegalArgumentException("Team name cannot be empty");
        }

        // Calculate corner stats
        CornerStatsDTO stats = cornerStatsService.calculateCornerStats(teamName.trim(), isHome);

        log.debug("Returning corner stats for {}: avgWon={}, dominance={}",
                  stats.getTeamName(), stats.getAvgCornersWon(), stats.getCornerDominance());

        return ResponseEntity.ok(stats);
    }

    // ══════════════════════════════════════════════════════════════════════
    // MATCH CORNER PREDICTION ENDPOINT
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Predict corner statistics for an upcoming match.
     *
     * <p><strong>Example Request:</strong></p>
     * <pre>
     * GET /api/matches/predict-corners?home=Arsenal&amp;away=Chelsea
     * GET /api/matches/predict-corners?home=Man%20City&amp;away=Liverpool
     * </pre>
     *
     * <p><strong>Response:</strong></p>
     * <pre>
     * {
     *   "homeTeam": "Arsenal",
     *   "awayTeam": "Chelsea",
     *   "expectedTotalCorners": 10.45,
     *   "expectedHomeCorners": 5.72,
     *   "expectedAwayCorners": 4.73,
     *   "probOver9_5": 0.623,
     *   "probOver10_5": 0.458,
     *   "probOver11_5": 0.302,
     *   "confidence": 0.85,
     *   "homeWeightedCorners": 6.78,
     *   "awayWeightedCorners": 4.92,
     *   "homeMatchesAnalyzed": 18,
     *   "awayMatchesAnalyzed": 16
     * }
     * </pre>
     *
     * @param home Home team name
     * @param away Away team name
     * @return Corner predictions for the match
     */
    @GetMapping("/matches/predict-corners")
    public ResponseEntity<CornerPredictionDTO> predictMatchCorners(
            @RequestParam String home,
            @RequestParam String away) {

        log.info("GET /api/matches/predict-corners - home: {}, away: {}", home, away);

        // Validate inputs
        if (home == null || home.trim().isEmpty()) {
            log.warn("Invalid request: empty home team");
            throw new IllegalArgumentException("Home team name cannot be empty");
        }

        if (away == null || away.trim().isEmpty()) {
            log.warn("Invalid request: empty away team");
            throw new IllegalArgumentException("Away team name cannot be empty");
        }

        // Predict match corners
        CornerPredictionDTO prediction = cornerStatsService.predictMatchCorners(
                home.trim(),
                away.trim()
        );

        log.debug("Returning corner prediction for {} vs {}: expected={}, prob10.5={}",
                  prediction.getHomeTeam(), prediction.getAwayTeam(),
                  prediction.getExpectedTotalCorners(), prediction.getProbOver10_5());

        return ResponseEntity.ok(prediction);
    }

    // ══════════════════════════════════════════════════════════════════════
    // ADDITIONAL UTILITY ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Get corner stats for both home and away for a team (combined view).
     *
     * <p><strong>Example Request:</strong></p>
     * <pre>
     * GET /api/teams/Arsenal/corner-stats/split
     * </pre>
     *
     * @param teamName Team name
     * @return Combined home and away corner statistics
     */
    @GetMapping("/teams/{teamName}/corner-stats/split")
    public ResponseEntity<Map<String, Object>> getCornerStatsSplit(@PathVariable String teamName) {
        log.info("GET /api/teams/{}/corner-stats/split", teamName);

        if (teamName == null || teamName.trim().isEmpty()) {
            throw new IllegalArgumentException("Team name cannot be empty");
        }

        String trimmedName = teamName.trim();

        CornerStatsDTO homeStats = cornerStatsService.calculateCornerStats(trimmedName, true);
        CornerStatsDTO awayStats = cornerStatsService.calculateCornerStats(trimmedName, false);
        CornerStatsDTO overallStats = cornerStatsService.calculateCornerStats(trimmedName, null);

        return ResponseEntity.ok(Map.of(
                "teamName", homeStats.getTeamName(),
                "home", homeStats,
                "away", awayStats,
                "overall", overallStats
        ));
    }
}

