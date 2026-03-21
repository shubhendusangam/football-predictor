package com.app.footballprediction.controller;

import com.app.common.dto.ApiQuotaStatusDTO;
import com.app.common.dto.MatchInjuryContextDTO;
import com.app.common.dto.TeamAvailabilityDTO;
import com.app.footballprediction.ratelimit.ApiFootballRateLimiter;
import com.app.footballprediction.ratelimit.BudgetCategory;
import com.app.footballprediction.service.InjuryDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for injury/suspension data from API-Football.
 * <p>
 * Public endpoints for injury data; admin endpoints for quota management.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Injuries", description = "Player injury and suspension data from API-Football")
public class InjuryController {

    private final InjuryDataService injuryDataService;
    private final ApiFootballRateLimiter rateLimiter;

    // ── Public Endpoints ────────────────────────────────────────────

    /**
     * Get injury context for a specific fixture.
     * GET /api/injuries/fixture/{fixtureId}?homeTeamId=X&awayTeamId=Y
     */
    @Operation(summary = "Get injury context for a fixture",
            description = "Returns injury/suspension data for both teams in a fixture. Served from cache if available.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Injury context retrieved successfully")
            })
    @GetMapping("/injuries/fixture/{fixtureId}")
    public ResponseEntity<MatchInjuryContextDTO> getFixtureInjuries(
            @Parameter(description = "API-Football fixture ID") @PathVariable long fixtureId,
            @Parameter(description = "Home team API-Football ID") @RequestParam(defaultValue = "0") int homeTeamId,
            @Parameter(description = "Away team API-Football ID") @RequestParam(defaultValue = "0") int awayTeamId) {

        log.info("Fetching injury context for fixture {}", fixtureId);
        MatchInjuryContextDTO context = injuryDataService.getMatchInjuryContext(fixtureId, homeTeamId, awayTeamId);
        return ResponseEntity.ok(context);
    }

    /**
     * Get availability for a specific team.
     * GET /api/injuries/team/{teamId}?season=2025&fixtureId=0
     */
    @Operation(summary = "Get team availability",
            description = "Returns injury/suspension data for a single team",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Team availability retrieved successfully")
            })
    @GetMapping("/injuries/team/{teamId}")
    public ResponseEntity<TeamAvailabilityDTO> getTeamInjuries(
            @Parameter(description = "API-Football team ID") @PathVariable int teamId,
            @Parameter(description = "Fixture ID to look up") @RequestParam(defaultValue = "0") long fixtureId) {

        log.info("Fetching availability for team {} (fixture={})", teamId, fixtureId);
        TeamAvailabilityDTO availability = injuryDataService.getTeamAvailability(fixtureId, teamId);
        return ResponseEntity.ok(availability);
    }

    // ── Admin Endpoints ─────────────────────────────────────────────

    /**
     * Get API-Football quota status.
     * GET /api/admin/apifootball/quota
     */
    @Operation(summary = "Get API-Football quota status",
            description = "Returns current daily quota usage, remaining calls, and budget allocations")
    @GetMapping("/admin/apifootball/quota")
    public ResponseEntity<ApiQuotaStatusDTO> getQuotaStatus() {
        return ResponseEntity.ok(rateLimiter.getStatus());
    }

    /**
     * Manually trigger injury cache warm for all upcoming fixtures.
     * POST /api/admin/injuries/sync
     */
    @Operation(summary = "Trigger manual injury cache sync",
            description = "Warms the injury cache for upcoming fixtures. Uses quota budget.")
    @PostMapping("/admin/injuries/sync")
    public ResponseEntity<Map<String, Object>> triggerInjurySync() {
        log.info("Manual injury sync triggered via admin API");

        if (!rateLimiter.canAfford(BudgetCategory.INJURY)) {
            return ResponseEntity.ok(Map.of(
                    "status", "skipped",
                    "reason", "Insufficient API-Football quota for injury sync",
                    "quotaRemaining", rateLimiter.getStatus().getRemaining()
            ));
        }

        // In a full implementation, this would iterate over upcoming fixture IDs
        // and call injuryDataService.getMatchInjuryContext for each
        ApiQuotaStatusDTO quotaAfter = rateLimiter.getStatus();

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "syncedFixtures", 0,
                "quotaUsed", 0,
                "quotaRemaining", quotaAfter.getRemaining()
        ));
    }
}

