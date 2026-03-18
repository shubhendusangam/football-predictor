package com.app.footballprediction.controller;

import com.app.footballprediction.dto.KickoffTimeAnalysisDTO;
import com.app.footballprediction.service.KickoffTimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for kick-off time performance analysis.
 *
 * <p>Provides access to performance metrics grouped by kick-off time slot:</p>
 * <ul>
 *   <li>Win/draw/loss breakdown per time slot</li>
 *   <li>Goal averages per time slot</li>
 *   <li>Best and worst kick-off times</li>
 *   <li>Performance classification (Strong/Average/Weak)</li>
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
public class KickoffTimeController {

    private final KickoffTimeService kickoffTimeService;

    /**
     * Get kick-off time performance analysis for a team.
     *
     * <p>GET /api/teams/{teamName}/kickoff-analysis</p>
     *
     * <p>Returns performance breakdown by time slot including:</p>
     * <ul>
     *   <li>Win percentage per slot</li>
     *   <li>Average goals scored/conceded per slot</li>
     *   <li>Best and worst kick-off times</li>
     *   <li>Overall win rate for baseline comparison</li>
     * </ul>
     *
     * @param teamName Team name (URL encoded if contains spaces)
     * @return KickoffTimeAnalysisDTO with comprehensive kick-off time analysis
     */
    @GetMapping("/{teamName}/kickoff-analysis")
    public ResponseEntity<?> getKickoffTimeAnalysis(@PathVariable String teamName) {

        log.info("Received kick-off time analysis request for team: {}", teamName);

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
            KickoffTimeAnalysisDTO analysis = kickoffTimeService.analyzeByKickoffTime(teamName);

            // Check if team was found
            if (analysis.getMatchesWithTimeData() == 0) {
                log.warn("No kick-off time data found for team: {}", teamName);

                String message;
                if (analysis.getMatchesAnalyzed() > 0) {
                    // Team exists but matches are from seasons without kick-off time data (pre-2019/20)
                    message = "Kick-off time data is not available for " + teamName
                            + ". Time data is only available for matches from the 2019-20 season onwards ("
                            + analysis.getMatchesAnalyzed() + " matches found without time data).";
                } else {
                    message = "No match data found for team: " + teamName;
                }

                return ResponseEntity.ok(Map.of(
                        "teamName", teamName,
                        "matchesAnalyzed", analysis.getMatchesAnalyzed(),
                        "matchesWithTimeData", 0,
                        "message", message,
                        "dataAvailable", false
                ));
            }

            log.info("Kick-off analysis completed for {}: best={}, worst={}, matches={}",
                    teamName, analysis.getBestTime(), analysis.getWorstTime(),
                    analysis.getMatchesWithTimeData());

            return ResponseEntity.ok(analysis);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for kick-off analysis: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", e.getMessage(),
                    "status", 400
            ));
        } catch (Exception e) {
            log.error("Error analyzing kick-off time performance for team {}: {}", teamName, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Failed to analyze kick-off time performance: " + e.getMessage(),
                    "status", 500
            ));
        }
    }
}
