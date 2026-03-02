package com.app.footballprediction.controller;

import com.app.footballprediction.dto.SeasonStatsResponse;
import com.app.footballprediction.service.SeasonStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for season-based historical data endpoints.
 *
 * Provides endpoints for:
 * - Listing all available seasons
 * - Fetching team statistics for a specific season
 *
 * Supports pagination, sorting, and filtering.
 */
@RestController
@RequestMapping("/api/seasons")
@RequiredArgsConstructor
@Slf4j
public class SeasonsController {

    private final SeasonStatsService seasonStatsService;

    /**
     * Get all available seasons.
     *
     * GET /api/seasons
     *
     * @return List of season strings sorted in descending order (newest first)
     */
    @GetMapping
    public ResponseEntity<?> getAllSeasons() {
        try {
            log.info("Fetching all available seasons");
            List<String> seasons = seasonStatsService.getAllSeasons();
            return ResponseEntity.ok(Map.of(
                    "seasons", seasons,
                    "count", seasons.size()
            ));
        } catch (Exception e) {
            log.error("Failed to fetch seasons: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch seasons",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get statistics for a specific season.
     *
     * GET /api/seasons/{year}/stats
     *
     * @param year Season identifier (e.g., "2023-24" or "2025/26")
     * @param page Page number (0-indexed, default: 0)
     * @param pageSize Items per page (default: 10)
     * @param sortBy Field to sort by (team, matches, bttsRate, over25Rate, winRate, points - default: points)
     * @param sortDir Sort direction (asc, desc - default: desc)
     * @param team Optional team name filter
     * @return SeasonStatsResponse with team statistics, pagination info
     */
    @GetMapping(value = {"/{year}/stats", "/{year1}/{year2}/stats"})
    public ResponseEntity<?> getSeasonStats(
            @PathVariable(required = false) String year,
            @PathVariable(required = false) String year1,
            @PathVariable(required = false) String year2,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "points") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String team) {

        // Handle both formats: "2023-24" and "2025/26"
        String seasonYear = year != null ? year : (year1 + "/" + year2);

        try {
            log.info("Fetching stats for season: {}, page: {}, pageSize: {}, sortBy: {}, sortDir: {}, team: {}",
                    seasonYear, page, pageSize, sortBy, sortDir, team);

            // Validate page size
            if (pageSize < 1 || pageSize > 100) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid page size",
                        "message", "Page size must be between 1 and 100"
                ));
            }

            // Validate sort direction
            if (!sortDir.equalsIgnoreCase("asc") && !sortDir.equalsIgnoreCase("desc")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid sort direction",
                        "message", "Sort direction must be 'asc' or 'desc'"
                ));
            }

            SeasonStatsResponse stats = seasonStatsService.getSeasonStats(
                    seasonYear, page, pageSize, sortBy, sortDir, team);

            if (stats.getTotalMatches() == 0) {
                return ResponseEntity.ok(Map.of(
                        "message", "No data found for season: " + seasonYear,
                        "season", seasonYear,
                        "teamStats", List.of(),
                        "pagination", Map.of(
                                "page", page,
                                "pageSize", pageSize,
                                "totalItems", 0,
                                "totalPages", 0
                        )
                ));
            }

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to fetch season stats for {}: {}", seasonYear, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch season statistics",
                    "details", e.getMessage()
            ));
        }
    }
}

