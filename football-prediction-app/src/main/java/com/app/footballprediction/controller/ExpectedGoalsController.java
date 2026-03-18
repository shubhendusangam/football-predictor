package com.app.footballprediction.controller;

import com.app.footballprediction.dto.ExpectedGoalsDTO;
import com.app.footballprediction.dto.MatchXGPredictionDTO;
import com.app.footballprediction.service.ExpectedGoalsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Expected Goals (xG) statistics and predictions.
 *
 * <p>Provides endpoints for:</p>
 * <ul>
 *   <li>Team xG statistics (home/away split)</li>
 *   <li>Match xG predictions with over/under goal probabilities</li>
 * </ul>
 *
 * <p><strong>API Endpoints:</strong></p>
 * <ul>
 *   <li>GET /api/teams/{teamName}/expected-goals - Get xG statistics for a team</li>
 *   <li>GET /api/teams/{teamName}/expected-goals/split - Get home/away xG split</li>
 *   <li>GET /api/matches/predict-xg - Predict xG for a match</li>
 * </ul>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 * @see ExpectedGoalsService
 * @see ExpectedGoalsDTO
 * @see MatchXGPredictionDTO
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Analytics", description = "League statistics, pre-match insights, H2H analysis, and trending data")
public class ExpectedGoalsController {

    private final ExpectedGoalsService expectedGoalsService;

    // ══════════════════════════════════════════════════════════════════════
    // TEAM XG STATISTICS ENDPOINT
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Get expected goals (xG) statistics for a specific team.
     *
     * <p><strong>Example Request:</strong></p>
     * <pre>
     * GET /api/teams/Arsenal/expected-goals
     * GET /api/teams/Arsenal/expected-goals?isHome=true
     * GET /api/teams/Man%20City/expected-goals?isHome=false
     * </pre>
     *
     * <p><strong>Response:</strong></p>
     * <pre>
     * {
     *   "teamName": "Arsenal",
     *   "isHome": true,
     *   "avgShotsOnTarget": 5.2,
     *   "expectedGoals": 1.46,
     *   "actualGoals": 1.75,
     *   "xGDifference": 0.29,
     *   "conversionRate": 0.337,
     *   "leagueConversionRate": 0.280,
     *   "performance": "Overperforming +0.3",
     *   "matchesAnalyzed": 18,
     *   "totalShotsOnTarget": 94,
     *   "totalGoals": 32,
     *   "weightedXG": 1.52,
     *   "avgShotsOnTargetAgainst": 3.8,
     *   "expectedGoalsAgainst": 1.06
     * }
     * </pre>
     *
     * @param teamName Team name (URL encoded if contains spaces)
     * @param isHome   Optional filter: true = home matches only, false = away only, null = all
     * @return Expected goals statistics for the team
     */
    @GetMapping("/teams/{teamName}/expected-goals")
    public ResponseEntity<ExpectedGoalsDTO> getExpectedGoals(
            @PathVariable String teamName,
            @RequestParam(required = false) Boolean isHome) {

        log.info("GET /api/teams/{}/expected-goals - isHome: {}", teamName, isHome);

        if (teamName == null || teamName.trim().isEmpty()) {
            log.warn("Invalid request: empty team name");
            throw new IllegalArgumentException("Team name cannot be empty");
        }

        ExpectedGoalsDTO stats = expectedGoalsService.calculateXG(teamName.trim(), isHome);

        log.debug("Returning xG stats for {}: xG={}, actual={}, diff={}",
                  stats.getTeamName(), stats.getExpectedGoals(), stats.getActualGoals(), stats.getXGDifference());

        return ResponseEntity.ok(stats);
    }

    // ══════════════════════════════════════════════════════════════════════
    // MATCH XG PREDICTION ENDPOINT
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Predict expected goals (xG) for an upcoming match.
     *
     * <p><strong>Example Request:</strong></p>
     * <pre>
     * GET /api/matches/predict-xg?home=Arsenal&amp;away=Chelsea
     * GET /api/matches/predict-xg?home=Man%20City&amp;away=Liverpool
     * </pre>
     *
     * <p><strong>Response:</strong></p>
     * <pre>
     * {
     *   "homeTeam": "Arsenal",
     *   "awayTeam": "Chelsea",
     *   "homeXG": 1.72,
     *   "awayXG": 1.15,
     *   "totalXG": 2.87,
     *   "prediction": "Expect Over 2.5 goals (totalXG: 2.9)",
     *   "probOver1_5": 0.823,
     *   "probOver2_5": 0.584,
     *   "probOver3_5": 0.341,
     *   "confidence": 0.85,
     *   "homeShotsOnTarget": 5.2,
     *   "awayShotsOnTarget": 3.8,
     *   "homeMatchesAnalyzed": 18,
     *   "awayMatchesAnalyzed": 16,
     *   "recommendation": "Goals expected - moderate attacking match (2.9 xG)"
     * }
     * </pre>
     *
     * @param home Home team name
     * @param away Away team name
     * @return Match xG predictions
     */
    @GetMapping("/matches/predict-xg")
    public ResponseEntity<MatchXGPredictionDTO> predictMatchXG(
            @RequestParam String home,
            @RequestParam String away) {

        log.info("GET /api/matches/predict-xg - home: {}, away: {}", home, away);

        if (home == null || home.trim().isEmpty()) {
            log.warn("Invalid request: empty home team");
            throw new IllegalArgumentException("Home team name cannot be empty");
        }

        if (away == null || away.trim().isEmpty()) {
            log.warn("Invalid request: empty away team");
            throw new IllegalArgumentException("Away team name cannot be empty");
        }

        MatchXGPredictionDTO prediction = expectedGoalsService.predictMatchXG(
                home.trim(),
                away.trim()
        );

        log.debug("Returning xG prediction for {} vs {}: homeXG={}, awayXG={}, totalXG={}",
                  prediction.getHomeTeam(), prediction.getAwayTeam(),
                  prediction.getHomeXG(), prediction.getAwayXG(), prediction.getTotalXG());

        return ResponseEntity.ok(prediction);
    }

    // ══════════════════════════════════════════════════════════════════════
    // ADDITIONAL UTILITY ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Get xG stats for both home and away for a team (combined view).
     *
     * <p><strong>Example Request:</strong></p>
     * <pre>
     * GET /api/teams/Arsenal/expected-goals/split
     * </pre>
     *
     * @param teamName Team name
     * @return Combined home and away xG statistics
     */
    @GetMapping("/teams/{teamName}/expected-goals/split")
    public ResponseEntity<Map<String, Object>> getExpectedGoalsSplit(@PathVariable String teamName) {
        log.info("GET /api/teams/{}/expected-goals/split", teamName);

        if (teamName == null || teamName.trim().isEmpty()) {
            throw new IllegalArgumentException("Team name cannot be empty");
        }

        String trimmedName = teamName.trim();

        ExpectedGoalsDTO homeStats = expectedGoalsService.calculateXG(trimmedName, true);
        ExpectedGoalsDTO awayStats = expectedGoalsService.calculateXG(trimmedName, false);
        ExpectedGoalsDTO overallStats = expectedGoalsService.calculateXG(trimmedName, null);

        return ResponseEntity.ok(Map.of(
                "teamName", homeStats.getTeamName(),
                "home", homeStats,
                "away", awayStats,
                "overall", overallStats
        ));
    }
}
