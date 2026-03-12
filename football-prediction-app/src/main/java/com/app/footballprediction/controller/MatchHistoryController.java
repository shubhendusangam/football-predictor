package com.app.footballprediction.controller;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.TeamRepository;
import com.app.footballprediction.service.FootballDataApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for match history and upcoming fixture queries.
 *
 * Endpoints:
 * - GET /api/matches/history?team=X&limit=N
 * - GET /api/matches/{id}
 * - GET /api/matches/upcoming?limit=N&refresh=false
 */
@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
@Slf4j
public class MatchHistoryController {

    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;
    private final FootballDataApiService footballDataApiService;

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getMatchHistory(
            @RequestParam(required = false) String team,
            @RequestParam(defaultValue = "20") int limit) {

        var allMatches = matchRepository.findAll();

        Map<String, String> teamLogos = new HashMap<>();
        teamRepository.findAll().forEach(t -> {
            if (t.getLogoUrl() != null && !t.getLogoUrl().isBlank()) {
                teamLogos.put(t.getName().toLowerCase(), t.getLogoUrl());
            }
        });

        var filteredMatches = allMatches.stream()
                .filter(m -> m.getFullTimeHomeGoals() != null && m.getFullTimeAwayGoals() != null)
                .filter(m -> team == null || team.isBlank() ||
                        m.getHomeTeam().equalsIgnoreCase(team) ||
                        m.getAwayTeam().equalsIgnoreCase(team))
                .sorted((m1, m2) -> m2.getMatchDate().compareTo(m1.getMatchDate()))
                .limit(limit)
                .map(match -> toMatchDto(match, teamLogos))
                .toList();

        return ResponseEntity.ok(Map.of(
                "matches", filteredMatches,
                "count", filteredMatches.size(),
                "filter", team != null ? team : "all",
                "limit", limit
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMatchById(@PathVariable Long id) {
        var matchOpt = matchRepository.findById(id);
        if (matchOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Match match = matchOpt.get();

        Map<String, Object> matchData = new HashMap<>();
        matchData.put("id", match.getId());
        matchData.put("date", match.getMatchDate().toString());
        matchData.put("homeTeam", match.getHomeTeam());
        matchData.put("awayTeam", match.getAwayTeam());
        matchData.put("homeGoals", match.getFullTimeHomeGoals());
        matchData.put("awayGoals", match.getFullTimeAwayGoals());
        matchData.put("result", match.getFullTimeResult());
        matchData.put("halfTimeHome", match.getHalfTimeHomeGoals());
        matchData.put("halfTimeAway", match.getHalfTimeAwayGoals());
        matchData.put("homeShots", match.getHomeShots());
        matchData.put("awayShots", match.getAwayShots());
        matchData.put("homeShotsOnTarget", match.getHomeShotsOnTarget());
        matchData.put("awayShotsOnTarget", match.getAwayShotsOnTarget());
        matchData.put("homeCorners", match.getHomeCorners());
        matchData.put("awayCorners", match.getAwayCorners());

        return ResponseEntity.ok(matchData);
    }

    @GetMapping("/upcoming")
    public ResponseEntity<Map<String, Object>> getUpcomingMatches(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "false") boolean refresh) {

        var upcomingMatches = refresh
                ? footballDataApiService.getScheduledMatchesFresh("PL")
                : footballDataApiService.getScheduledMatches("PL");

        if (upcomingMatches == null || upcomingMatches.getMatches() == null) {
            return ResponseEntity.ok(Map.of(
                    "matches", java.util.List.of(),
                    "count", 0,
                    "competition", "Premier League",
                    "cached", !refresh
            ));
        }

        Map<String, String> teamLogos = new HashMap<>();
        teamRepository.findAll().forEach(t -> {
            if (t.getLogoUrl() != null && !t.getLogoUrl().isBlank()) {
                teamLogos.put(t.getName().toLowerCase(), t.getLogoUrl());
            }
        });

        var limitedMatches = upcomingMatches.getMatches().stream()
                .limit(limit)
                .map(match -> {
                    String homeTeam = footballDataApiService.normalizeTeamName(match.getHomeTeam().getName());
                    String awayTeam = footballDataApiService.normalizeTeamName(match.getAwayTeam().getName());

                    Map<String, Object> matchData = new HashMap<>();
                    matchData.put("id", match.getId());
                    matchData.put("homeTeam", homeTeam);
                    matchData.put("awayTeam", awayTeam);
                    matchData.put("utcDate", match.getUtcDate());
                    matchData.put("matchday", match.getMatchday());
                    matchData.put("status", match.getStatus());
                    matchData.put("homeTeamCrest",
                            match.getHomeTeam().getCrest() != null ? match.getHomeTeam().getCrest() :
                                    teamLogos.getOrDefault(homeTeam.toLowerCase(), null));
                    matchData.put("awayTeamCrest",
                            match.getAwayTeam().getCrest() != null ? match.getAwayTeam().getCrest() :
                                    teamLogos.getOrDefault(awayTeam.toLowerCase(), null));
                    return matchData;
                })
                .toList();

        return ResponseEntity.ok(Map.of(
                "matches", limitedMatches,
                "count", limitedMatches.size(),
                "competition", "Premier League",
                "cached", !refresh,
                "fetchedAt", LocalDateTime.now().toString(),
                "hint", "Add ?refresh=true for fresh data"
        ));
    }

    // ── Private ────────────────────────────────────────────────────

    private Map<String, Object> toMatchDto(Match match, Map<String, String> teamLogos) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", match.getId());
        data.put("date", match.getMatchDate().toString());
        data.put("homeTeam", match.getHomeTeam());
        data.put("awayTeam", match.getAwayTeam());
        data.put("homeGoals", match.getFullTimeHomeGoals());
        data.put("awayGoals", match.getFullTimeAwayGoals());
        data.put("result", match.getFullTimeResult());
        data.put("season", match.getSeason());
        data.put("homeTeamCrest", teamLogos.getOrDefault(match.getHomeTeam().toLowerCase(), null));
        data.put("awayTeamCrest", teamLogos.getOrDefault(match.getAwayTeam().toLowerCase(), null));
        return data;
    }
}

