package com.app.footballprediction.controller;

import com.app.footballprediction.dto.RefereeStats;
import com.app.footballprediction.service.RefereeStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for referee statistics endpoints.
 * Provides access to referee performance data and tendencies.
 */
@RestController
@RequestMapping("/api/referees")
@RequiredArgsConstructor
@Slf4j
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
     * Get statistics for all referees (with minimum 5 matches).
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
     * Get statistics for a specific referee.
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
}

