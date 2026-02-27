package com.app.footballprediction.controller;

import com.app.footballprediction.dto.CardsPredictionDTO;
import com.app.footballprediction.dto.TeamDisciplineDTO;
import com.app.footballprediction.service.CardsPredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for cards prediction and team discipline endpoints.
 *
 * <p>Provides access to:</p>
 * <ul>
 *   <li>Match card predictions with referee influence</li>
 *   <li>Team discipline statistics</li>
 * </ul>
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class CardsController {

    private final CardsPredictionService cardsPredictionService;

    /**
     * Predict yellow and red cards for a match.
     *
     * <p>GET /api/matches/predict-cards?home={homeTeam}&amp;away={awayTeam}&amp;referee={refereeName}</p>
     *
     * @param home    Home team name (required)
     * @param away    Away team name (required)
     * @param referee Referee name (optional)
     * @return CardsPredictionDTO with prediction results
     */
    @GetMapping("/matches/predict-cards")
    public ResponseEntity<?> predictCards(
            @RequestParam("home") String home,
            @RequestParam("away") String away,
            @RequestParam(value = "referee", required = false) String referee) {

        log.info("Received cards prediction request: {} vs {} (referee: {})", home, away, referee);

        // Validate required parameters
        if (home == null || home.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", "Home team name is required",
                    "status", 400
            ));
        }

        if (away == null || away.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", "Away team name is required",
                    "status", 400
            ));
        }

        try {
            CardsPredictionDTO prediction = cardsPredictionService.predictCards(home, away, referee);
            return ResponseEntity.ok(prediction);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for cards prediction: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", e.getMessage(),
                    "status", 400
            ));
        } catch (Exception e) {
            log.error("Error predicting cards for {} vs {}: {}", home, away, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Failed to predict cards: " + e.getMessage(),
                    "status", 500
            ));
        }
    }

    /**
     * Get discipline statistics for a team.
     *
     * <p>GET /api/teams/{teamName}/discipline</p>
     *
     * @param teamName Team name (URL encoded if contains spaces)
     * @return TeamDisciplineDTO with discipline statistics
     */
    @GetMapping("/teams/{teamName}/discipline")
    public ResponseEntity<?> getTeamDiscipline(@PathVariable String teamName) {

        log.info("Received discipline request for team: {}", teamName);

        // Validate parameter
        if (teamName == null || teamName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", "Team name is required",
                    "status", 400
            ));
        }

        try {
            TeamDisciplineDTO discipline = cardsPredictionService.getTeamDiscipline(teamName);

            // Check if team was found
            if (discipline.getMatchesAnalyzed() == 0) {
                log.warn("No discipline data found for team: {}", teamName);
                return ResponseEntity.ok(Map.of(
                        "teamName", teamName,
                        "message", "No match data available for this team",
                        "matchesAnalyzed", 0
                ));
            }

            return ResponseEntity.ok(discipline);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for team discipline: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Bad Request",
                    "message", e.getMessage(),
                    "status", 400
            ));
        } catch (Exception e) {
            log.error("Error getting discipline for team {}: {}", teamName, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Failed to get team discipline: " + e.getMessage(),
                    "status", 500
            ));
        }
    }
}

