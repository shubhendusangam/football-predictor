package com.app.footballprediction.controller;

import com.app.footballprediction.dto.RefereeImpactDTO;
import com.app.footballprediction.dto.RefereeStats;
import com.app.footballprediction.dto.RefereeStatsDTO;
import com.app.footballprediction.service.RefereeStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for referee statistics endpoints.
 * Provides access to referee performance data, tendencies, and match impact predictions.
 */
@RestController
@RequestMapping("/api/referees")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Referees", description = "Referee statistics, tendencies, and match-impact predictions")
public class RefereeController {

    private final RefereeStatsService refereeStatsService;

    /**
     * Get a list of all referees in the database.
     *
     * GET /api/referees
     *
     * @return List of referee names
     */
    @GetMapping
    public ResponseEntity<List<String>> getAllReferees() {
        log.info("Getting all referees");
        List<String> referees = refereeStatsService.getAllReferees();
        return ResponseEntity.ok(referees);
    }

    /**
     * Get statistics for all referees (original DTO, with minimum 5 matches).
     *
     * GET /api/referees/stats
     *
     * @return List of RefereeStats sorted by matches officiated
     */
    @GetMapping("/stats")
    public ResponseEntity<List<RefereeStats>> getAllRefereeStats() {
        log.info("Getting all referee stats");
        List<RefereeStats> stats = refereeStatsService.getAllRefereeStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get comprehensive statistics for all referees (new DTO).
     *
     * GET /api/referees/comprehensive
     *
     * @return List of RefereeStatsDTO sorted by matches officiated
     */
    @GetMapping("/comprehensive")
    public ResponseEntity<List<RefereeStatsDTO>> getAllRefereeComprehensiveStats() {
        log.info("Getting all comprehensive referee stats");
        List<RefereeStatsDTO> stats = refereeStatsService.getAllRefereeComprehensiveStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get league-wide referee summary statistics for dashboard.
     *
     * GET /api/referees/summary
     *
     * @return Map with league averages, strictest/lenient top-3, total referee count
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getLeagueSummary() {
        log.info("Getting referee league summary");
        try {
            Map<String, Object> summary = refereeStatsService.getLeagueSummary();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error getting league summary", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to calculate league summary",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Compare two referees side by side.
     *
     * GET /api/referees/compare?ref1={name1}&ref2={name2}
     *
     * @param ref1 First referee name
     * @param ref2 Second referee name
     * @return Comparison data with stats for both referees and verdicts
     */
    @GetMapping("/compare")
    public ResponseEntity<?> compareReferees(
            @RequestParam("ref1") String ref1,
            @RequestParam("ref2") String ref2) {
        log.info("Comparing referees: {} vs {}", ref1, ref2);

        if (ref1 == null || ref1.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "First referee name is required (ref1 parameter)"
            ));
        }
        if (ref2 == null || ref2.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Second referee name is required (ref2 parameter)"
            ));
        }

        try {
            Map<String, Object> comparison = refereeStatsService.compareReferees(ref1.trim(), ref2.trim());
            return ResponseEntity.ok(comparison);
        } catch (Exception e) {
            log.error("Error comparing referees: {} vs {}", ref1, ref2, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to compare referees",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get statistics for a specific referee (original DTO).
     *
     * GET /api/referees/{name}
     *
     * @param name Referee name (URL encoded if contains spaces)
     * @return RefereeStats for the referee
     */
    @GetMapping("/{name}")
    public ResponseEntity<?> getRefereeStats(@PathVariable String name) {
        log.info("Getting stats for referee: {}", name);

        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Referee name is required"
            ));
        }

        RefereeStats stats = refereeStatsService.getRefereeStats(name);

        if (stats.getMatchesOfficiated() == 0) {
            return ResponseEntity.ok(Map.of(
                    "refereeName", name,
                    "message", "No matches found for this referee",
                    "hint", "Use GET /api/referees to see all available referees"
            ));
        }

        return ResponseEntity.ok(stats);
    }

    /**
     * Get comprehensive statistics for a specific referee (new DTO).
     *
     * GET /api/referees/{name}/stats
     *
     * @param name Referee name
     * @return RefereeStatsDTO with full statistics
     */
    @GetMapping("/{name}/stats")
    public ResponseEntity<?> getRefereeComprehensiveStats(@PathVariable String name) {
        log.info("Getting comprehensive stats for referee: {}", name);

        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Referee name is required"
            ));
        }

        try {
            RefereeStatsDTO stats = refereeStatsService.calculateRefereeStats(name);

            if (stats.getMatchesOfficiated() == 0) {
                return ResponseEntity.ok(Map.of(
                        "refereeName", name,
                        "message", "No matches found for this referee",
                        "hint", "Use GET /api/referees to see all available referees"
                ));
            }

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting comprehensive stats for referee: {}", name, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to calculate referee stats",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get the strictest referees (by cards per match).
     *
     * GET /api/referees/strictest?limit=5
     *
     * @param limit Number of referees to return (default 5)
     * @return List of strictest referees
     */
    @GetMapping("/strictest")
    public ResponseEntity<List<RefereeStats>> getStrictestReferees(
            @RequestParam(defaultValue = "5") int limit) {
        log.info("Getting {} strictest referees", limit);
        List<RefereeStats> stats = refereeStatsService.getStrictestReferees(Math.min(limit, 20));
        return ResponseEntity.ok(stats);
    }

    /**
     * Get the most lenient referees (by cards per match).
     *
     * GET /api/referees/lenient?limit=5
     *
     * @param limit Number of referees to return (default 5)
     * @return List of most lenient referees
     */
    @GetMapping("/lenient")
    public ResponseEntity<List<RefereeStats>> getMostLenientReferees(
            @RequestParam(defaultValue = "5") int limit) {
        log.info("Getting {} most lenient referees", limit);
        List<RefereeStats> stats = refereeStatsService.getMostLenientReferees(Math.min(limit, 20));
        return ResponseEntity.ok(stats);
    }

    /**
     * Predict referee impact on a specific match.
     *
     * GET /api/matches/referee-impact?ref={ref}&home={home}&away={away}
     * Also available at: GET /api/referees/impact?ref={ref}&home={home}&away={away}
     *
     * @param ref  Referee name
     * @param home Home team name
     * @param away Away team name
     * @return RefereeImpactDTO with impact prediction
     */
    @GetMapping("/impact")
    public ResponseEntity<?> getRefereeImpact(
            @RequestParam("ref") String ref,
            @RequestParam("home") String home,
            @RequestParam("away") String away) {
        log.info("Getting referee impact: ref={}, home={}, away={}", ref, home, away);

        if (ref == null || ref.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Referee name is required (ref parameter)"
            ));
        }
        if (home == null || home.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Home team name is required (home parameter)"
            ));
        }
        if (away == null || away.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Away team name is required (away parameter)"
            ));
        }

        try {
            RefereeImpactDTO impact = refereeStatsService.predictRefereeImpact(ref.trim(), home.trim(), away.trim());
            return ResponseEntity.ok(impact);
        } catch (Exception e) {
            log.error("Error predicting referee impact: ref={}, home={}, away={}", ref, home, away, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to predict referee impact",
                    "message", e.getMessage()
            ));
        }
    }
}
