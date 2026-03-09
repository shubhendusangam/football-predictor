package com.app.footballprediction.controller;

import com.app.footballprediction.dto.CongestionComparisonDTO;
import com.app.footballprediction.dto.FixtureCongestionDTO;
import com.app.footballprediction.service.FixtureCongestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * REST controller for fixture congestion &amp; fatigue analysis.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET /api/teams/{teamName}/fixture-congestion — single-team analysis</li>
 *   <li>GET /api/matches/congestion-comparison — head-to-head comparison</li>
 * </ul>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class FixtureCongestionController {

    private final FixtureCongestionService congestionService;

    // ─── Single team ─────────────────────────────────────────────────────

    @GetMapping("/api/teams/{teamName}/fixture-congestion")
    public ResponseEntity<?> getFixtureCongestion(@PathVariable String teamName) {

        log.info("Fixture congestion request for team: {}", teamName);

        if (teamName == null || teamName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", "Team name is required",
                    "status", 400));
        }

        try {
            FixtureCongestionDTO dto = congestionService.analyzeFixtureCongestion(
                    teamName, LocalDate.now());

            if (dto.getMatchesAnalyzed() == 0) {
                return ResponseEntity.status(404).body(Map.of(
                        "error", "Not Found",
                        "message", "No match data for team: " + teamName,
                        "teamName", teamName,
                        "status", 404));
            }

            return ResponseEntity.ok(dto);

        } catch (IllegalArgumentException e) {
            log.warn("Bad request for fixture congestion: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", e.getMessage(),
                    "status", 400));
        } catch (Exception e) {
            log.error("Error analysing fixture congestion for {}: {}", teamName, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Failed to analyse fixture congestion: " + e.getMessage(),
                    "status", 500));
        }
    }

    // ─── Match comparison ────────────────────────────────────────────────

    @GetMapping("/api/matches/congestion-comparison")
    public ResponseEntity<?> getCongestionComparison(
            @RequestParam String home,
            @RequestParam String away) {

        log.info("Congestion comparison: {} vs {}", home, away);

        if (home == null || home.isBlank() || away == null || away.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", "Both home and away team names are required",
                    "status", 400));
        }

        try {
            CongestionComparisonDTO dto = congestionService.compareFixtureCongestion(
                    home, away, LocalDate.now());

            return ResponseEntity.ok(dto);

        } catch (IllegalArgumentException e) {
            log.warn("Bad request for congestion comparison: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", e.getMessage(),
                    "status", 400));
        } catch (Exception e) {
            log.error("Error comparing congestion {} vs {}: {}", home, away, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Failed to compare fixture congestion: " + e.getMessage(),
                    "status", 500));
        }
    }
}

