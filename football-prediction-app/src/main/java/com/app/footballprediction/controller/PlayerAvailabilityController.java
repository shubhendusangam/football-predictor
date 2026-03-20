package com.app.footballprediction.controller;

import com.app.common.model.PlayerAvailability.AvailabilityStatus;
import com.app.common.repository.PlayerAvailabilityRepository;
import com.app.footballprediction.dto.PlayerAvailabilityDTO;
import com.app.footballprediction.service.PlayerAvailabilityApiService;
import com.app.footballprediction.service.PlayerImpactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for player availability / injury tracking.
 * Provides endpoints to query and manage player absence data.
 */
@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Player Availability", description = "Player injury, suspension, and fitness tracking")
public class PlayerAvailabilityController {

    private final PlayerImpactService playerImpactService;
    private final PlayerAvailabilityRepository playerAvailabilityRepository;
    private final PlayerAvailabilityApiService playerAvailabilityApiService;

    /**
     * Get team squad availability.
     * GET /api/availability/team?name=Chelsea
     */
    @Operation(summary = "Get team squad availability",
            description = "Returns squad strength, absent players, and impact assessment for a team")
    @GetMapping("/team")
    public ResponseEntity<PlayerAvailabilityDTO> getTeamAvailability(
            @Parameter(description = "Team name", example = "Chelsea")
            @RequestParam String name) {
        log.info("Getting availability for team: {}", name);
        PlayerAvailabilityDTO dto = playerImpactService.getTeamAvailability(name);
        return ResponseEntity.ok(dto);
    }

    /**
     * Get squad availability for all teams.
     * GET /api/availability/all
     */
    @Operation(summary = "Get all teams' squad availability")
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllTeamAvailability() {
        List<String> teams = playerAvailabilityRepository.findAllTeamNames();

        List<PlayerAvailabilityDTO> allAvailability = teams.stream()
                .map(playerImpactService::getTeamAvailability)
                .collect(Collectors.toList());

        // Summary stats
        long fullStrength = allAvailability.stream()
                .filter(a -> "FULL_STRENGTH".equals(a.getAvailabilityRating())).count();
        long weakened = allAvailability.stream()
                .filter(a -> "WEAKENED".equals(a.getAvailabilityRating())
                        || "SEVERELY_WEAKENED".equals(a.getAvailabilityRating())).count();

        return ResponseEntity.ok(Map.of(
                "teams", allAvailability,
                "totalTeams", allAvailability.size(),
                "fullStrength", fullStrength,
                "weakened", weakened
        ));
    }

    /**
     * Get match availability context (both teams).
     * GET /api/availability/match?home=Arsenal&away=Chelsea
     */
    @Operation(summary = "Get match availability context",
            description = "Returns availability for both teams in a match, including impact comparison")
    @GetMapping("/match")
    public ResponseEntity<Map<String, Object>> getMatchAvailability(
            @Parameter(description = "Home team", example = "Arsenal") @RequestParam String home,
            @Parameter(description = "Away team", example = "Chelsea") @RequestParam String away) {

        PlayerAvailabilityDTO homeAvail = playerImpactService.getTeamAvailability(home);
        PlayerAvailabilityDTO awayAvail = playerImpactService.getTeamAvailability(away);

        double strengthDiff = homeAvail.getSquadStrength() - awayAvail.getSquadStrength();
        String advantage;
        if (Math.abs(strengthDiff) < 0.05) {
            advantage = "EVEN";
        } else if (strengthDiff > 0) {
            advantage = "HOME";
        } else {
            advantage = "AWAY";
        }

        Map<String, Object> response = new HashMap<>();
        response.put("homeAvailability", homeAvail);
        response.put("awayAvailability", awayAvail);
        response.put("strengthDifference", Math.round(strengthDiff * 10000.0) / 10000.0);
        response.put("availabilityAdvantage", advantage);
        response.put("note", buildMatchNote(homeAvail, awayAvail));

        return ResponseEntity.ok(response);
    }

    /**
     * Update a player's injury/suspension status.
     * POST /api/availability/update
     */
    @Operation(summary = "Update player availability status",
            description = "Manually update a player's injury/suspension status")
    @PostMapping("/update")
    public ResponseEntity<Map<String, String>> updatePlayerStatus(
            @RequestBody UpdatePlayerStatusRequest request) {
        try {
            AvailabilityStatus status = AvailabilityStatus.valueOf(request.getStatus().toUpperCase());
            LocalDate returnDate = request.getExpectedReturn() != null
                    ? LocalDate.parse(request.getExpectedReturn()) : null;

            playerAvailabilityApiService.updatePlayerStatus(
                    request.getTeamName(),
                    request.getPlayerName(),
                    status,
                    request.getReason(),
                    returnDate);

            return ResponseEntity.ok(Map.of(
                    "status", "updated",
                    "player", request.getPlayerName(),
                    "team", request.getTeamName()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid status: " + request.getStatus(),
                    "validStatuses", "INJURED, SUSPENDED, DOUBTFUL, AVAILABLE"
            ));
        }
    }

    /**
     * Trigger manual sync of player data.
     * POST /api/availability/sync
     */
    @Operation(summary = "Trigger player availability sync",
            description = "Manually triggers a sync of player availability data from external sources")
    @PostMapping("/sync")
    public ResponseEntity<Map<String, String>> triggerSync() {
        log.info("Manual player availability sync triggered");
        try {
            playerAvailabilityApiService.syncAllTeams();
            return ResponseEntity.ok(Map.of("status", "sync_complete"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "sync_failed",
                    "error", e.getMessage()
            ));
        }
    }

    // ── Request DTOs ─────────────────────────────────────────────────────

    @Data
    public static class UpdatePlayerStatusRequest {
        private String teamName;
        private String playerName;
        private String status;          // INJURED, SUSPENDED, DOUBTFUL, AVAILABLE
        private String reason;
        private String expectedReturn;  // ISO date string
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private String buildMatchNote(PlayerAvailabilityDTO home, PlayerAvailabilityDTO away) {
        StringBuilder sb = new StringBuilder();
        if (home.getAvailabilityNote() != null) sb.append(home.getAvailabilityNote());
        if (away.getAvailabilityNote() != null) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(away.getAvailabilityNote());
        }
        return sb.length() > 0 ? sb.toString() : "Both teams at full strength";
    }
}

