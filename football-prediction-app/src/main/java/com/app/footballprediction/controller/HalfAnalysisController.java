package com.app.footballprediction.controller;

import com.app.footballprediction.dto.HalfAnalysisDTO;
import com.app.footballprediction.service.HalfAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for first half vs second half performance analysis.
 *
 * <p>Provides access to half-time performance metrics including:</p>
 * <ul>
 *   <li>Goal distribution by half</li>
 *   <li>Win rates based on half-time position</li>
 *   <li>Comeback statistics</li>
 *   <li>Pattern classification</li>
 * </ul>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Analytics", description = "League statistics, pre-match insights, H2H analysis, and trending data")
public class HalfAnalysisController {

    private final HalfAnalysisService halfAnalysisService;

    /**
     * Get first half vs second half performance analysis for a team.
     *
     * <p>GET /api/teams/{teamName}/half-analysis</p>
     *
     * <p>Returns comprehensive analysis including:</p>
     * <ul>
     *   <li>Goal distribution between halves</li>
     *   <li>Win rates when leading, drawing, or losing at HT</li>
     *   <li>Comeback rate</li>
     *   <li>Pattern classification (Fast Starter, Strong Finisher, Balanced)</li>
     * </ul>
     *
     * @param teamName Team name (URL encoded if contains spaces)
     * @return HalfAnalysisDTO with comprehensive half analysis
     */
    @GetMapping("/{teamName}/half-analysis")
    public ResponseEntity<?> getHalfAnalysis(@PathVariable String teamName) {

        log.info("Received half analysis request for team: {}", teamName);

        // Validate parameter
        if (teamName == null || teamName.isBlank()) {
            log.warn("Invalid request: team name is blank");
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", "Team name is required",
                    "status", 400
            ));
        }

        try {
            HalfAnalysisDTO analysis = halfAnalysisService.analyzeByHalf(teamName);

            // Check if team was found
            if (analysis.getMatchesAnalyzed() == 0) {
                log.warn("No half-time data found for team: {}", teamName);
                return ResponseEntity.status(404).body(Map.of(
                        "error", "Not Found",
                        "message", "No match data with half-time statistics available for team: " + teamName,
                        "teamName", teamName,
                        "status", 404
                ));
            }

            log.info("Half analysis completed for {}: pattern={}, 1H={}%, 2H={}%",
                    teamName, analysis.getPattern(),
                    String.format("%.1f", analysis.getFirstHalfPercentage()),
                    String.format("%.1f", analysis.getSecondHalfPercentage()));

            return ResponseEntity.ok(analysis);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for half analysis: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", e.getMessage(),
                    "status", 400
            ));
        } catch (Exception e) {
            log.error("Error analyzing half performance for team {}: {}", teamName, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Failed to analyze half performance: " + e.getMessage(),
                    "status", 500
            ));
        }
    }
}
