package com.app.footballprediction.controller;

import com.app.common.model.AdminAuditLog;
import com.app.common.model.League;
import com.app.common.model.Match;
import com.app.common.model.SystemSettings;
import com.app.common.model.Team;
import com.app.common.repository.LeagueStandingRepository;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.service.AdminService;
import com.app.footballprediction.service.ApiDataSyncService;
import com.app.footballprediction.service.PredictionTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for Admin operations.
 *
 * All endpoints in this controller require ADMIN role authentication.
 */
@RestController
@RequestMapping("/api/admin")
@Slf4j
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final PredictionTrackingService predictionTrackingService;
    private final ApiDataSyncService apiDataSyncService;
    private final MatchRepository matchRepository;
    private final LeagueStandingRepository standingRepository;

    // ======================= AUTHENTICATION =======================

    /**
     * Verify admin credentials.
     * Returns success if the provided credentials are valid.
     *
     * GET /api/admin/verify
     */
    @GetMapping("/verify")
    public ResponseEntity<?> verifyAdmin(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            log.info("Admin verified: {}", authentication.getName());
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Admin authenticated successfully",
                "username", authentication.getName(),
                "role", "ADMIN"
            ));
        }
        return ResponseEntity.status(401).body(Map.of(
            "status", "error",
            "message", "Authentication required"
        ));
    }

    /**
     * Logout admin (client should clear credentials).
     *
     * POST /api/admin/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(Authentication authentication) {
        if (authentication != null) {
            adminService.logAuditAction(authentication.getName(),
                AdminAuditLog.ActionType.LOGOUT, "Admin logged out",
                null, null, null, null, true, null);
        }
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Logged out successfully"
        ));
    }

    // ======================= DASHBOARD =======================

    /**
     * Get admin dashboard data.
     *
     * GET /api/admin/dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(Authentication authentication) {
        try {
            Map<String, Object> stats = adminService.getDashboardStats();
            SystemSettings settings = adminService.getSettings();
            List<League> leagues = adminService.getAllLeagues();

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "stats", stats,
                "settings", settings,
                "leagues", leagues
            ));
        } catch (Exception e) {
            log.error("Error fetching admin dashboard", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to fetch dashboard data: " + e.getMessage()
            ));
        }
    }

    // ======================= SYSTEM CONTROLS =======================

    /**
     * Toggle prediction engine on/off.
     *
     * POST /api/admin/toggle-engine
     */
    @PostMapping("/toggle-engine")
    public ResponseEntity<?> toggleEngine(
            @RequestBody Map<String, Boolean> request,
            Authentication authentication) {
        try {
            boolean enabled = request.getOrDefault("enabled", true);
            SystemSettings settings = adminService.togglePredictionEngine(enabled, authentication.getName());

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Prediction engine " + (enabled ? "enabled" : "disabled"),
                "predictionEngineEnabled", settings.getPredictionEngineEnabled()
            ));
        } catch (Exception e) {
            log.error("Error toggling prediction engine", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to toggle engine: " + e.getMessage()
            ));
        }
    }

    /**
     * Trigger model retraining.
     *
     * POST /api/admin/retrain
     */
    @PostMapping("/retrain")
    public ResponseEntity<?> retrain(Authentication authentication) {
        try {
            // Record the retraining request (actual training is handled by the model service)
            adminService.recordModelRetraining(authentication.getName(), null);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Model retraining initiated",
                "timestamp", java.time.LocalDateTime.now().toString()
            ));
        } catch (Exception e) {
            log.error("Error initiating model retraining", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to initiate retraining: " + e.getMessage()
            ));
        }
    }

    // ======================= SETTINGS =======================

    /**
     * Get current system settings.
     *
     * GET /api/admin/settings
     */
    @GetMapping("/settings")
    public ResponseEntity<?> getSettings() {
        try {
            SystemSettings settings = adminService.getSettings();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "settings", settings
            ));
        } catch (Exception e) {
            log.error("Error fetching settings", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to fetch settings: " + e.getMessage()
            ));
        }
    }

    /**
     * Update system settings.
     *
     * PUT /api/admin/settings
     */
    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(
            @RequestBody SystemSettings newSettings,
            Authentication authentication) {
        try {
            SystemSettings updated = adminService.updateSettings(newSettings, authentication.getName());
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Settings updated successfully",
                "settings", updated
            ));
        } catch (Exception e) {
            log.error("Error updating settings", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to update settings: " + e.getMessage()
            ));
        }
    }

    // ======================= MATCH OVERRIDE =======================

    /**
     * Override match result.
     *
     * POST /api/admin/match-override
     */
    @PostMapping("/match-override")
    public ResponseEntity<?> overrideMatch(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            Long matchId = Long.parseLong(request.get("matchId").toString());
            String result = (String) request.get("result");
            Integer homeGoals = Integer.parseInt(request.get("homeGoals").toString());
            Integer awayGoals = Integer.parseInt(request.get("awayGoals").toString());

            Match updated = adminService.overrideMatchResult(matchId, result, homeGoals, awayGoals, authentication.getName());

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Match result overridden successfully",
                "match", Map.of(
                    "id", updated.getId(),
                    "homeTeam", updated.getHomeTeam(),
                    "awayTeam", updated.getAwayTeam(),
                    "result", updated.getFullTimeResult(),
                    "homeGoals", updated.getFullTimeHomeGoals(),
                    "awayGoals", updated.getFullTimeAwayGoals()
                )
            ));
        } catch (Exception e) {
            log.error("Error overriding match result", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to override match: " + e.getMessage()
            ));
        }
    }

    // ======================= LEAGUE MANAGEMENT =======================

    /**
     * Get all leagues.
     *
     * GET /api/admin/leagues
     */
    @GetMapping("/leagues")
    public ResponseEntity<?> getLeagues() {
        try {
            List<League> leagues = adminService.getAllLeagues();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "leagues", leagues
            ));
        } catch (Exception e) {
            log.error("Error fetching leagues", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to fetch leagues: " + e.getMessage()
            ));
        }
    }

    /**
     * Toggle league enabled status.
     *
     * POST /api/admin/leagues/{code}/toggle
     */
    @PostMapping("/leagues/{code}/toggle")
    public ResponseEntity<?> toggleLeague(
            @PathVariable String code,
            @RequestBody Map<String, Boolean> request,
            Authentication authentication) {
        try {
            boolean enabled = request.getOrDefault("enabled", true);
            League league = adminService.toggleLeague(code, enabled, authentication.getName());

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "League " + code + " " + (enabled ? "enabled" : "disabled"),
                "league", league
            ));
        } catch (Exception e) {
            log.error("Error toggling league", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to toggle league: " + e.getMessage()
            ));
        }
    }

    /**
     * Update league details.
     *
     * PUT /api/admin/leagues/{id}
     */
    @PutMapping("/leagues/{id}")
    public ResponseEntity<?> updateLeague(
            @PathVariable Long id,
            @RequestBody League updates,
            Authentication authentication) {
        try {
            League league = adminService.updateLeague(id, updates, authentication.getName());
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "League updated successfully",
                "league", league
            ));
        } catch (Exception e) {
            log.error("Error updating league", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to update league: " + e.getMessage()
            ));
        }
    }

    // ======================= TEAM LOGO MANAGEMENT =======================

    /**
     * Get teams with missing logos.
     *
     * GET /api/admin/teams/missing-logos
     */
    @GetMapping("/teams/missing-logos")
    public ResponseEntity<?> getTeamsWithMissingLogos() {
        try {
            List<Team> teams = adminService.getTeamsWithMissingLogos();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "count", teams.size(),
                "teams", teams
            ));
        } catch (Exception e) {
            log.error("Error fetching teams with missing logos", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to fetch teams: " + e.getMessage()
            ));
        }
    }

    /**
     * Update team logo.
     *
     * PUT /api/admin/teams/{id}/logo
     */
    @PutMapping("/teams/{id}/logo")
    public ResponseEntity<?> updateTeamLogo(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        try {
            String logoUrl = request.get("logoUrl");
            Team team = adminService.updateTeamLogo(id, logoUrl, authentication.getName());
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Team logo updated successfully",
                "team", Map.of(
                    "id", team.getId(),
                    "name", team.getName(),
                    "logoUrl", team.getLogoUrl() != null ? team.getLogoUrl() : ""
                )
            ));
        } catch (Exception e) {
            log.error("Error updating team logo", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to update team logo: " + e.getMessage()
            ));
        }
    }

    // ======================= AUDIT LOGS =======================

    /**
     * Get recent audit logs.
     *
     * GET /api/admin/audit-logs
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<?> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            Page<AdminAuditLog> logs = adminService.getRecentAuditLogs(page, size);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "logs", logs.getContent(),
                "totalPages", logs.getTotalPages(),
                "totalElements", logs.getTotalElements(),
                "currentPage", page
            ));
        } catch (Exception e) {
            log.error("Error fetching audit logs", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to fetch audit logs: " + e.getMessage()
            ));
        }
    }

    // ======================= PREDICTIONS =======================

    /**
     * Manually trigger update of unresolved predictions.
     * Updates predictions with actual results from completed matches.
     *
     * POST /api/admin/predictions/update-results
     */
    @PostMapping("/predictions/update-results")
    public ResponseEntity<?> updatePredictionResults(Authentication authentication) {
        try {
            log.info("Manual prediction results update triggered by: {}",
                    authentication != null ? authentication.getName() : "unknown");

            predictionTrackingService.updateUnresolvedPredictions();

            if (authentication != null) {
                adminService.logAuditAction(authentication.getName(),
                    AdminAuditLog.ActionType.UPDATE_SETTINGS,
                    "Manually triggered prediction results update",
                    null, null, null, null, true, null);
            }

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Prediction results update completed"
            ));
        } catch (Exception e) {
            log.error("Error updating prediction results", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to update prediction results: " + e.getMessage()
            ));
        }
    }

    // ======================= DATA MIGRATION =======================

    /**
     * Update existing matches with fouls data (HF/AF) from CSV files.
     * This is a one-time migration to populate fouls data for existing matches.
     *
     * POST /api/admin/data/update-fouls
     */
    @PostMapping("/data/update-fouls")
    public ResponseEntity<?> updateFoulsData(Authentication authentication) {
        try {
            log.info("Fouls data update triggered by: {}",
                    authentication != null ? authentication.getName() : "unknown");

            int updated = adminService.updateFoulsData();

            if (authentication != null) {
                adminService.logAuditAction(authentication.getName(),
                    AdminAuditLog.ActionType.UPDATE_SETTINGS,
                    "Updated fouls data for " + updated + " matches",
                    null, null, null, null, true, null);
            }

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Fouls data update completed",
                "matchesUpdated", updated
            ));
        } catch (Exception e) {
            log.error("Error updating fouls data", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to update fouls data: " + e.getMessage()
            ));
        }
    }

    // ======================= API DATA SYNC =======================

    /**
     * Sync standings from football-data.org API to database.
     *
     * POST /api/admin/sync/standings?competition=PL
     */
    @PostMapping("/sync/standings")
    public ResponseEntity<?> syncStandings(
            @RequestParam(defaultValue = "PL") String competition,
            Authentication authentication) {
        try {
            log.info("Standings sync triggered by: {} for competition: {}",
                    authentication != null ? authentication.getName() : "unknown", competition);

            int count = apiDataSyncService.syncStandings(competition);

            if (authentication != null) {
                adminService.logAuditAction(authentication.getName(),
                    AdminAuditLog.ActionType.UPDATE_SETTINGS,
                    "Synced " + count + " team standings from API",
                    null, null, null, null, true, null);
            }

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "✅ Standings synced successfully",
                "competition", competition,
                "teamsCount", count
            ));
        } catch (Exception e) {
            log.error("Error syncing standings", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "❌ Failed to sync standings: " + e.getMessage()
            ));
        }
    }

    /**
     * Sync finished matches from football-data.org API to database.
     *
     * POST /api/admin/sync/matches?competition=PL
     */
    @PostMapping("/sync/matches")
    public ResponseEntity<?> syncMatches(
            @RequestParam(defaultValue = "PL") String competition,
            Authentication authentication) {
        try {
            log.info("Matches sync triggered by: {} for competition: {}",
                    authentication != null ? authentication.getName() : "unknown", competition);

            int[] result = apiDataSyncService.syncFinishedMatches(competition);

            if (authentication != null) {
                adminService.logAuditAction(authentication.getName(),
                    AdminAuditLog.ActionType.UPDATE_SETTINGS,
                    "Synced matches: " + result[0] + " new, " + result[1] + " updated",
                    null, null, null, null, true, null);
            }

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "✅ Matches synced successfully",
                "competition", competition,
                "newMatches", result[0],
                "updatedMatches", result[1]
            ));
        } catch (Exception e) {
            log.error("Error syncing matches", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "❌ Failed to sync matches: " + e.getMessage()
            ));
        }
    }

    /**
     * Perform full data sync (standings + finished matches + scheduled matches).
     *
     * POST /api/admin/sync/all?competition=PL
     */
    @PostMapping("/sync/all")
    public ResponseEntity<Map<String, Object>> syncAll(
            @RequestParam(defaultValue = "PL") String competition,
            Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();

        try {
            log.info("Full data sync triggered by: {} for competition: {}",
                    authentication != null ? authentication.getName() : "unknown", competition);

            apiDataSyncService.syncAll(competition);

            long duration = System.currentTimeMillis() - startTime;

            if (authentication != null) {
                adminService.logAuditAction(authentication.getName(),
                    AdminAuditLog.ActionType.UPDATE_SETTINGS,
                    "Full data sync completed in " + duration + "ms",
                    null, null, null, null, true, null);
            }

            response.put("status", "success");
            response.put("competition", competition);
            response.put("duration", duration + "ms");
            response.put("message", "✅ Full sync completed");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error during full sync", e);
            long duration = System.currentTimeMillis() - startTime;

            response.put("status", "error");
            response.put("competition", competition);
            response.put("duration", duration + "ms");
            response.put("message", "❌ Full sync failed: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get current sync status (latest match date, counts, etc.).
     *
     * GET /api/admin/sync/status
     */
    @GetMapping("/sync/status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            // Get latest match date
            List<Match> latestMatches = matchRepository.findAllByOrderByMatchDateDesc();
            if (!latestMatches.isEmpty()) {
                Match latest = latestMatches.get(0);
                status.put("latestMatchDate", latest.getMatchDate().toString());
                status.put("latestMatch", latest.getHomeTeam() + " vs " + latest.getAwayTeam());
            } else {
                status.put("latestMatchDate", "N/A");
                status.put("latestMatch", "No matches found");
            }

            // Get counts
            status.put("standingsCount", standingRepository.count());
            status.put("matchesCount", matchRepository.count());

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Error fetching sync status", e);
            status.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(status);
        }
    }

    /**
     * Normalize all season data to standard format (YYYY-YY with dash).
     * Also recalculates missing form data for standings.
     *
     * This fixes:
     * - Inconsistent season formats ("2025/26" vs "2025-26")
     * - Empty/null form data in league standings
     *
     * POST /api/admin/sync/normalize-seasons
     */
    @PostMapping("/sync/normalize-seasons")
    public ResponseEntity<Map<String, Object>> normalizeSeasons(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();

        try {
            log.info("Season normalization triggered by: {}",
                    authentication != null ? authentication.getName() : "unknown");

            Map<String, Integer> result = apiDataSyncService.normalizeAllSeasonData();

            long duration = System.currentTimeMillis() - startTime;

            if (authentication != null) {
                adminService.logAuditAction(authentication.getName(),
                    AdminAuditLog.ActionType.UPDATE_SETTINGS,
                    "Season data normalized in " + duration + "ms",
                    null, null, null, null, true, null);
            }

            response.put("status", "success");
            response.put("matchesNormalized", result.get("matchesNormalized"));
            response.put("standingsNormalized", result.get("standingsNormalized"));
            response.put("formsCalculated", result.get("formsCalculated"));
            response.put("duration", duration + "ms");
            response.put("message", "✅ Season data normalized successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error normalizing season data", e);
            long duration = System.currentTimeMillis() - startTime;

            response.put("status", "error");
            response.put("duration", duration + "ms");
            response.put("message", "❌ Normalization failed: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }
}
