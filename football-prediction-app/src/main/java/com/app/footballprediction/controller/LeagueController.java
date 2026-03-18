package com.app.footballprediction.controller;

import com.app.footballprediction.dto.GoalsTrendsDTO;
import com.app.footballprediction.dto.RelegationBattleAnalysisDTO;
import com.app.footballprediction.dto.Top4RaceAnalysisDTO;
import com.app.footballprediction.service.GoalsTrendsService;
import com.app.footballprediction.service.RelegationBattleService;
import com.app.footballprediction.service.Top4RaceService;
import com.app.footballprediction.util.SeasonUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for league-related endpoints.
 * Provides Champions League race analysis, title battles, relegation battle, goals trends, and league metrics.
 */
@RestController
@RequestMapping("/api/league")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "League", description = "Top-4 race, relegation battle, goals trends, and league metrics")
public class LeagueController {

    private final Top4RaceService top4RaceService;
    private final RelegationBattleService relegationBattleService;
    private final GoalsTrendsService goalsTrendsService;

    /**
     * Get Top 4 (Champions League) race analysis for a season.
     *
     * Analyzes the battle for top 4 positions including:
     * - Current standings and gaps
     * - Probability calculations
     * - Title race summary
     * - Team motivation levels
     *
     * GET /api/league/top4-race?season=2025-26
     *
     * @param season Season identifier (e.g., "2025-26"), defaults to current season
     * @return Top4RaceAnalysisDTO with complete race analysis
     */
    @GetMapping("/top4-race")
    public ResponseEntity<?> getTop4Race(
            @RequestParam(required = false) String season) {

        try {
            log.debug("GET /api/league/top4-race season={}", season);
            long startTime = System.currentTimeMillis();

            // Default to current season if not provided
            String effectiveSeason = (season != null && !season.isBlank())
                    ? season
                    : SeasonUtils.getCurrentSeason();

            Top4RaceAnalysisDTO analysis = top4RaceService.analyzeTop4Race(
                    effectiveSeason, LocalDate.now());

            log.debug("Top 4 race analysis completed in {}ms for season {}",
                    System.currentTimeMillis() - startTime, effectiveSeason);

            return ResponseEntity.ok(analysis);

        } catch (Exception e) {
            log.error("Failed to analyze Top 4 race: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to analyze Top 4 race",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get Top 4 race analysis for a specific date.
     * Useful for historical analysis.
     *
     * GET /api/league/top4-race/historical?season=2025-26&asOfDate=2026-02-15
     *
     * @param season   Season identifier
     * @param asOfDate Date to calculate analysis as of (ISO format)
     * @return Top4RaceAnalysisDTO with race analysis as of the specified date
     */
    @GetMapping("/top4-race/historical")
    public ResponseEntity<?> getTop4RaceHistorical(
            @RequestParam String season,
            @RequestParam String asOfDate) {

        try {
            log.debug("GET /api/league/top4-race/historical season={} asOfDate={}", season, asOfDate);

            LocalDate date = LocalDate.parse(asOfDate);
            Top4RaceAnalysisDTO analysis = top4RaceService.analyzeTop4Race(season, date);

            return ResponseEntity.ok(analysis);

        } catch (Exception e) {
            log.error("Failed to get historical Top 4 race analysis: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to get historical analysis",
                    "details", e.getMessage()
            ));
        }
    }

    // ========== Relegation Battle ==========

    /**
     * Get relegation battle analysis for a season.
     *
     * Analyzes the fight to avoid relegation including:
     * - Teams in positions 14-20
     * - Survival probabilities
     * - Gap to safety line
     * - Desperation levels
     *
     * GET /api/league/relegation-battle?season=2025-26
     *
     * @param season Season identifier (e.g., "2025-26"), defaults to current season
     * @return RelegationBattleAnalysisDTO with complete battle analysis
     */
    @GetMapping("/relegation-battle")
    public ResponseEntity<?> getRelegationBattle(
            @RequestParam(required = false) String season) {

        try {
            log.debug("GET /api/league/relegation-battle season={}", season);
            long startTime = System.currentTimeMillis();

            // Default to current season if not provided
            String effectiveSeason = (season != null && !season.isBlank())
                    ? season
                    : SeasonUtils.getCurrentSeason();

            RelegationBattleAnalysisDTO analysis = relegationBattleService.analyzeRelegationBattle(
                    effectiveSeason, LocalDate.now());

            log.debug("Relegation battle analysis completed in {}ms for season {}",
                    System.currentTimeMillis() - startTime, effectiveSeason);

            return ResponseEntity.ok(analysis);

        } catch (Exception e) {
            log.error("Failed to analyze relegation battle: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to analyze relegation battle",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get relegation battle analysis for a specific date.
     * Useful for historical analysis.
     *
     * GET /api/league/relegation-battle/historical?season=2025-26&asOfDate=2026-02-15
     *
     * @param season   Season identifier
     * @param asOfDate Date to calculate analysis as of (ISO format)
     * @return RelegationBattleAnalysisDTO with battle analysis as of the specified date
     */
    @GetMapping("/relegation-battle/historical")
    public ResponseEntity<?> getRelegationBattleHistorical(
            @RequestParam String season,
            @RequestParam String asOfDate) {

        try {
            log.debug("GET /api/league/relegation-battle/historical season={} asOfDate={}", season, asOfDate);

            LocalDate date = LocalDate.parse(asOfDate);
            RelegationBattleAnalysisDTO analysis = relegationBattleService.analyzeRelegationBattle(season, date);

            return ResponseEntity.ok(analysis);

        } catch (Exception e) {
            log.error("Failed to get historical relegation battle analysis: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to get historical analysis",
                    "details", e.getMessage()
            ));
        }
    }

    // ========== Goals Trends ==========

    /**
     * Get league-wide goals trends analysis across multiple seasons.
     *
     * Tracks how goal-scoring patterns change across seasons including:
     * - Average goals per game
     * - Home vs away goals breakdown
     * - Clean sheet percentage
     * - High-scoring (>4 goals) and low-scoring (<2 goals) game percentages
     * - Overall trend direction (Increasing / Decreasing / Stable)
     *
     * GET /api/league/goals-trends?seasons=2020-21,2021-22,2022-23
     *
     * @param seasons Comma-separated season identifiers. If omitted, defaults to last 6 seasons.
     * @return GoalsTrendsDTO with per-season stats and trend analysis
     */
    @GetMapping("/goals-trends")
    public ResponseEntity<?> getGoalsTrends(
            @RequestParam(required = false) String seasons) {

        try {
            log.debug("GET /api/league/goals-trends seasons={}", seasons);
            long startTime = System.currentTimeMillis();

            List<String> seasonList = null;
            if (seasons != null && !seasons.isBlank()) {
                seasonList = Arrays.stream(seasons.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
            }

            GoalsTrendsDTO trends = goalsTrendsService.calculateGoalsTrends(seasonList);

            log.debug("Goals trends analysis completed in {}ms ({} seasons)",
                    System.currentTimeMillis() - startTime, trends.getSeasonsAnalyzed());

            return ResponseEntity.ok(trends);

        } catch (Exception e) {
            log.error("Failed to calculate goals trends: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to calculate goals trends",
                    "details", e.getMessage()
            ));
        }
    }
}
