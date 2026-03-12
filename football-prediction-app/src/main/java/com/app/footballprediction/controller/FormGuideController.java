package com.app.footballprediction.controller;

import com.app.footballprediction.dto.FormGuideDTO;
import com.app.footballprediction.service.FormGuideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the Form Guide feature.
 *
 * <p>Provides an endpoint that returns a team's recent match results
 * with W-D-L indicators, trend analysis, and a form rating.</p>
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@Slf4j
public class FormGuideController {

    private final FormGuideService formGuideService;

    /**
     * Get the form guide for a team.
     *
     * <pre>
     * GET /api/teams/{teamName}/form-guide
     * GET /api/teams/{teamName}/form-guide?matches=10
     * </pre>
     *
     * @param teamName  Team name (URL-encoded, e.g. "Arsenal", "Man%20United")
     * @param matches   Number of recent matches to include (default 10, max 20)
     * @return FormGuideDTO with match details, trend, and rating
     */
    @GetMapping("/{teamName}/form-guide")
    public ResponseEntity<?> getFormGuide(
            @PathVariable String teamName,
            @RequestParam(defaultValue = "10") int matches) {
        try {
            log.info("Form guide request: team='{}', matches={}", teamName, matches);
            FormGuideDTO guide = formGuideService.getFormGuide(teamName, matches);
            return ResponseEntity.ok(guide);
        } catch (IllegalArgumentException e) {
            log.warn("Form guide - bad request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Team not found",
                    "message", e.getMessage(),
                    "suggestion", "Use GET /api/teams to see available teams"
            ));
        } catch (Exception e) {
            log.error("Form guide error for '{}': {}", teamName, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to compute form guide",
                    "details", e.getMessage()
            ));
        }
    }
}

