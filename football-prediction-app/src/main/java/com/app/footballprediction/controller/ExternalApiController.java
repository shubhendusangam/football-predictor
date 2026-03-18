package com.app.footballprediction.controller;

import com.app.footballprediction.dto.UpcomingPredictionResponse;
import com.app.footballprediction.dto.UpcomingPredictionResponse.CurrentForm;
import com.app.footballprediction.dto.UpcomingPredictionResponse.MatchPrediction;
import com.app.footballprediction.dto.external.FootballApiResponse;
import com.app.footballprediction.dto.external.StandingsResponse;
import com.app.common.service.FeatureEngineeringService;
import com.app.common.model.MatchFeatures;
import com.app.common.util.PredictionUtils;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import com.app.footballprediction.service.FootballDataApiService;
import com.app.footballprediction.service.PredictionTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

import java.util.*;

/**
 * Controller for external API integration with football-data.org.
 *
 * Provides endpoints to:
 * - Fetch current season data
 * - Predict upcoming matches using historical + current season form
 *
 * Free tier: 10 requests/minute - use caching wisely!
 */
@RestController
@RequestMapping("/api/external")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Predictions", description = "Match outcome predictions, H2H insights, and trending analytics")
public class ExternalApiController {

    private final FootballDataApiService footballDataApiService;
    private final FeatureEngineeringService featureEngineeringService;
    private final ModelTrainingService modelTrainingService;
    private final PredictionTrackingService predictionTrackingService;

    /**
     * Get current standings with team form.
     *
     * GET /api/external/standings?competition=PL
     */
    @GetMapping("/standings")
    public ResponseEntity<?> getStandings(
            @RequestParam(defaultValue = "PL") String competition) {
        try {
            StandingsResponse standings = footballDataApiService.getStandings(competition);
            return ResponseEntity.ok(standings);
        } catch (Exception e) {
            log.error("Failed to fetch standings: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch standings",
                    "details", e.getMessage(),
                    "hint", "Check your API key in application.properties"
            ));
        }
    }

    /**
     * Clear the external API cache.
     * Useful when data appears stale or after errors.
     * <p>
     * POST /api/external/clear-cache
     */
    @PostMapping("/clear-cache")
    public ResponseEntity<?> clearCache() {
        try {
            footballDataApiService.clearCache();
            log.info("External API cache cleared via API request");
            return ResponseEntity.ok(Map.of(
                    "message", "Cache cleared successfully",
                    "timestamp", java.time.Instant.now().toString()
            ));
        } catch (Exception e) {
            log.error("Failed to clear cache: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to clear cache",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get upcoming scheduled matches.
     * <p>
     * GET /api/external/upcoming?competition=PL
     */
    @GetMapping("/upcoming")
    public ResponseEntity<?> getUpcomingMatches(
            @RequestParam(defaultValue = "PL") String competition) {
        try {
            FootballApiResponse matches = footballDataApiService.getScheduledMatches(competition);
            return ResponseEntity.ok(matches);
        } catch (Exception e) {
            log.error("Failed to fetch upcoming matches: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch upcoming matches",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get finished matches from current season.
     * <p>
     * GET /api/external/finished?competition=PL
     */
    @GetMapping("/finished")
    public ResponseEntity<?> getFinishedMatches(
            @RequestParam(defaultValue = "PL") String competition) {
        try {
            FootballApiResponse matches = footballDataApiService.getFinishedMatches(competition);
            return ResponseEntity.ok(matches);
        } catch (Exception e) {
            log.error("Failed to fetch finished matches: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch finished matches",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get matches for a specific date and predict outcomes.
     * <p>
     * GET /api/external/matches-by-date?date=2026-02-17&competition=PL
     */
    @GetMapping("/matches-by-date")
    public ResponseEntity<?> getMatchesByDate(
            @RequestParam String date,
            @RequestParam(defaultValue = "PL") String competition) {
        try {
            // Check if model is loaded
            if (!modelTrainingService.isModelLoaded()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Model not loaded",
                        "hint", "Call POST /api/model/train first"
                ));
            }

            // Fetch all matches (scheduled + finished) and filter by date
            FootballApiResponse scheduledResponse = footballDataApiService.getScheduledMatches(competition);
            FootballApiResponse finishedResponse = footballDataApiService.getFinishedMatches(competition);
            StandingsResponse standingsResponse = footballDataApiService.getStandings(competition);

            // Build standings lookup map
            Map<String, StandingsResponse.TableEntry> standingsMap = buildStandingsMap(standingsResponse);

            // Combine and filter matches by date
            List<FootballApiResponse.ApiMatch> allMatches = new java.util.ArrayList<>();
            if (scheduledResponse.getMatches() != null) {
                allMatches.addAll(scheduledResponse.getMatches());
            }
            if (finishedResponse.getMatches() != null) {
                allMatches.addAll(finishedResponse.getMatches());
            }

            // Filter matches by the requested date
            List<FootballApiResponse.ApiMatch> matchesOnDate = allMatches.stream()
                    .filter(m -> m.getUtcDate() != null && m.getUtcDate().startsWith(date))
                    .toList();

            if (matchesOnDate.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "date", date,
                        "competition", competition,
                        "matchCount", 0,
                        "matches", List.of(),
                        "message", "No matches found for this date"
                ));
            }

            // Predict each match
            List<UpcomingPredictionResponse.MatchPrediction> predictions = matchesOnDate.stream()
                    .map(match -> predictMatch(match, standingsMap))
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "date", date,
                    "competition", competition,
                    "competitionName", scheduledResponse.getCompetition() != null ?
                            scheduledResponse.getCompetition().getName() : "Premier League",
                    "matchCount", predictions.size(),
                    "matches", predictions
            ));

        } catch (Exception e) {
            log.error("Failed to fetch matches by date: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch matches",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Predict upcoming matches using historical data + current season form.
     *
     * GET /api/external/predict?competition=PL&limit=10
     *
     * This is the main endpoint that combines:
     * 1. Historical data from our database (form, H2H, goals)
     * 2. Current season form from football-data.org API
     */
    @GetMapping("/predict")
    public ResponseEntity<?> predictUpcomingMatches(
            @RequestParam(defaultValue = "PL") String competition,
            @RequestParam(defaultValue = "10") int limit) {

        try {
            // Check if model is loaded
            if (!modelTrainingService.isModelLoaded()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Model not loaded",
                        "hint", "Call POST /api/model/train first"
                ));
            }

            // Fetch upcoming matches and standings (2 API calls)
            FootballApiResponse upcomingResponse = footballDataApiService.getScheduledMatches(competition);
            StandingsResponse standingsResponse = footballDataApiService.getStandings(competition);

            if (upcomingResponse.getMatches() == null || upcomingResponse.getMatches().isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "competition", competition,
                        "message", "No upcoming matches found",
                        "predictions", List.of()
                ));
            }

            // Build standings lookup map (for current form)
            Map<String, StandingsResponse.TableEntry> standingsMap = buildStandingsMap(standingsResponse);

            // Predict each upcoming match
            List<MatchPrediction> predictions = upcomingResponse.getMatches().stream()
                    .limit(limit)
                    .map(match -> predictMatch(match, standingsMap))
                    .toList();

            // Build response
            UpcomingPredictionResponse response = UpcomingPredictionResponse.builder()
                    .competition(competition)
                    .competitionName(upcomingResponse.getCompetition() != null ?
                            upcomingResponse.getCompetition().getName() : competition)
                    .currentMatchday(standingsResponse.getSeason() != null ?
                            standingsResponse.getSeason().getCurrentMatchday() : null)
                    .predictions(predictions)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to predict upcoming matches: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to predict upcoming matches",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Predict a single match using historical data + current form.
     */
    private MatchPrediction predictMatch(FootballApiResponse.ApiMatch match,
                                         Map<String, StandingsResponse.TableEntry> standingsMap) {

        String apiHomeName = match.getHomeTeam().getName();
        String apiAwayName = match.getAwayTeam().getName();

        // Normalize team names to match our database
        String homeTeam = footballDataApiService.normalizeTeamName(apiHomeName);
        String awayTeam = footballDataApiService.normalizeTeamName(apiAwayName);

        // Get current form from standings
        StandingsResponse.TableEntry homeEntry = standingsMap.get(apiHomeName);
        StandingsResponse.TableEntry awayEntry = standingsMap.get(apiAwayName);

        log.debug("Looking up form for {} -> found: {}", apiHomeName, homeEntry != null);
        log.debug("Looking up form for {} -> found: {}", apiAwayName, awayEntry != null);

        CurrentForm homeForm = buildCurrentForm(homeEntry);
        CurrentForm awayForm = buildCurrentForm(awayEntry);

        try {
            // Build features from historical data
            MatchFeatures features = featureEngineeringService.buildFeaturesForPrediction(homeTeam, awayTeam);

            // Run prediction
            double[] probs = modelTrainingService.predict(features);
            String label = modelTrainingService.getPredictedLabel(probs);
            // Calculate raw confidence as the max probability
            double confidence = Math.max(probs[0], Math.max(probs[1], probs[2]));

            // Parse match date and determine season
            LocalDate matchDate = parseMatchDate(match.getUtcDate());
            String season = determineSeason(matchDate);

            // Record predictions for both teams (for model accuracy tracking)
            try {
                predictionTrackingService.recordMatchPredictions(
                        match.getId(),
                        homeTeam,
                        awayTeam,
                        season,
                        matchDate,
                        label,
                        confidence,
                        probs[0],
                        probs[1],
                        probs[2]
                );
                log.debug("Recorded prediction for {} vs {} on {}", homeTeam, awayTeam, matchDate);
            } catch (Exception e) {
                log.warn("Failed to record prediction for {} vs {}: {}", homeTeam, awayTeam, e.getMessage());
            }

            return MatchPrediction.builder()
                    .matchId(match.getId())
                    .matchDate(match.getUtcDate())
                    .matchday(match.getMatchday())
                    .homeTeam(homeTeam)
                    .awayTeam(awayTeam)
                    .homeTeamCrest(match.getHomeTeam().getCrest())
                    .awayTeamCrest(match.getAwayTeam().getCrest())
                    .prediction(PredictionUtils.labelToText(label))
                    .predictionCode(label)
                    .probHomeWin(PredictionUtils.round(probs[0]))
                    .probDraw(PredictionUtils.round(probs[1]))
                    .probAwayWin(PredictionUtils.round(probs[2]))
                    .confidence(PredictionUtils.getConfidence(probs))
                    .homeTeamForm(homeForm)
                    .awayTeamForm(awayForm)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to predict {} vs {}: {}", homeTeam, awayTeam, e.getMessage());
            return MatchPrediction.builder()
                    .matchId(match.getId())
                    .matchDate(match.getUtcDate())
                    .matchday(match.getMatchday())
                    .homeTeam(homeTeam)
                    .awayTeam(awayTeam)
                    .homeTeamCrest(match.getHomeTeam().getCrest())
                    .awayTeamCrest(match.getAwayTeam().getCrest())
                    .homeTeamForm(homeForm)
                    .awayTeamForm(awayForm)
                    .error("Team not found in historical data: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Parse match date from API format (ISO 8601).
     */
    private LocalDate parseMatchDate(String utcDate) {
        if (utcDate == null || utcDate.isEmpty()) {
            return LocalDate.now();
        }
        try {
            // Format: "2024-02-24T15:00:00Z"
            return LocalDate.parse(utcDate.substring(0, 10));
        } catch (Exception e) {
            log.warn("Failed to parse date {}: {}", utcDate, e.getMessage());
            return LocalDate.now();
        }
    }

    /**
     * Determine season from match date.
     * Football season runs August to May, so:
     * - Aug-Dec = current year - next year (e.g., 2025-26)
     * - Jan-Jul = previous year - current year (e.g., 2024-25)
     */
    private String determineSeason(LocalDate matchDate) {
        int year = matchDate.getYear();
        int month = matchDate.getMonthValue();

        if (month >= 8) {
            // August-December: season is currentYear-nextYear
            return String.format("%d-%02d", year, (year + 1) % 100);
        } else {
            // January-July: season is previousYear-currentYear
            return String.format("%d-%02d", year - 1, year % 100);
        }
    }

    /**
     * Build a map from team name to standings entry for quick lookup.
     */
    private Map<String, StandingsResponse.TableEntry> buildStandingsMap(StandingsResponse standings) {
        Map<String, StandingsResponse.TableEntry> map = new HashMap<>();

        if (standings.getStandings() != null) {
            for (StandingsResponse.StandingType standing : standings.getStandings()) {
                // Use TOTAL standings (not HOME or AWAY specific)
                if ("TOTAL".equals(standing.getType()) && standing.getTable() != null) {
                    for (StandingsResponse.TableEntry entry : standing.getTable()) {
                        if (entry.getTeam() != null) {
                            map.put(entry.getTeam().getName(), entry);
                        }
                    }
                }
            }
        }

        return map;
    }

    /**
     * Build current form DTO from standings entry.
     */
    private CurrentForm buildCurrentForm(StandingsResponse.TableEntry entry) {
        if (entry == null) {
            return null;
        }

        double ppg = entry.getPlayedGames() > 0
                ? (double) entry.getPoints() / entry.getPlayedGames()
                : 0.0;

        return CurrentForm.builder()
                .recentForm(entry.getForm())
                .position(entry.getPosition())
                .points(entry.getPoints())
                .played(entry.getPlayedGames())
                .won(entry.getWon())
                .draw(entry.getDraw())
                .lost(entry.getLost())
                .goalsFor(entry.getGoalsFor())
                .goalsAgainst(entry.getGoalsAgainst())
                .pointsPerGame(PredictionUtils.round(ppg))
                .build();
    }
}

