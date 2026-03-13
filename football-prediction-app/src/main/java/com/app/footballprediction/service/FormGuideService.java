package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.common.util.TeamNameNormalizer;
import com.app.footballprediction.dto.FormGuideDTO;
import com.app.footballprediction.dto.FormMatchDTO;
import com.app.footballprediction.util.SeasonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for computing a team's visual form guide.
 *
 * <p>Provides the last N match results with W-D-L indicators,
 * trend analysis (Improving / Declining / Stable), and a 0–10 form rating.</p>
 *
 * <p>All queries are season-scoped to prevent cross-season data mixing.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FormGuideService {

    private final MatchRepository matchRepository;
    private final TeamValidationService teamValidationService;

    private static final int MAX_MATCHES = 20;
    private static final int TREND_WINDOW = 5;

    /**
     * Get the form guide for a team.
     *
     * @param teamName   Team name (normalized or raw — will be resolved)
     * @param numMatches Number of recent matches to include (clamped to 1–20)
     * @return FormGuideDTO with match details, trend, and rating
     * @throws IllegalArgumentException if team cannot be found
     */
    @Cacheable(value = "formGuide",
            key = "#teamName + '-' + T(java.lang.Math).max(1, T(java.lang.Math).min(#numMatches, 20))")
    public FormGuideDTO getFormGuide(String teamName, int numMatches) {
        numMatches = Math.max(1, Math.min(numMatches, MAX_MATCHES));

        log.info("Computing form guide for '{}' (last {} matches)", teamName, numMatches);

        // Resolve team name (handles normalization + fuzzy matching)
        String resolvedName = resolveTeamName(teamName);
        String season = determineCurrentSeason();

        log.debug("Resolved '{}' → '{}', season={}", teamName, resolvedName, season);

        // Fetch completed matches for the team in the current season, newest first
        List<Match> seasonMatches = matchRepository.findByTeamAndSeason(resolvedName, season);

        if (seasonMatches.isEmpty()) {
            log.warn("No matches found for '{}' in season {}", resolvedName, season);
            return buildEmptyGuide(resolvedName, season);
        }

        // Take the requested window (already ordered newest-first by repository)
        List<Match> windowMatches = seasonMatches.stream()
                .limit(numMatches)
                .toList();

        // Build per-match DTOs
        List<FormMatchDTO> formMatches = windowMatches.stream()
                .map(m -> toFormMatch(m, resolvedName))
                .toList();

        // Points calculations
        int pointsLast5 = calculatePoints(formMatches, 0, TREND_WINDOW);
        int pointsPrev5 = calculatePoints(formMatches, TREND_WINDOW, TREND_WINDOW * 2);
        String trend = determineTrend(pointsLast5, pointsPrev5, formMatches.size());
        double rating = calculateFormRating(formMatches);
        String formString = buildFormString(formMatches);

        return FormGuideDTO.builder()
                .teamName(resolvedName)
                .recentMatches(formMatches)
                .pointsInLast5(pointsLast5)
                .pointsInPrevious5(pointsPrev5)
                .formTrend(trend)
                .formRating(rating)
                .formString(formString)
                .season(season)
                .totalMatchesInSeason(seasonMatches.size())
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Convert a Match entity to a FormMatchDTO from the given team's perspective.
     */
    private FormMatchDTO toFormMatch(Match match, String teamName) {
        boolean isHome = match.getHomeTeam() != null
                && match.getHomeTeam().equalsIgnoreCase(teamName);
        String opponent = isHome
                ? (match.getAwayTeam() != null ? match.getAwayTeam() : "Unknown")
                : (match.getHomeTeam() != null ? match.getHomeTeam() : "Unknown");
        String venue = isHome ? "H" : "A";
        int goalsFor = match.getGoalsScoredByTeam(teamName);
        int goalsAgainst = match.getGoalsConcededByTeam(teamName);
        int points = match.getPointsForTeam(teamName);
        String result = pointsToResult(points);

        return FormMatchDTO.builder()
                .matchDate(match.getMatchDate())
                .opponent(opponent)
                .venue(venue)
                .goalsFor(goalsFor)
                .goalsAgainst(goalsAgainst)
                .result(result)
                .points(points)
                .build();
    }

    /**
     * Map points to result character.
     */
    private String pointsToResult(int points) {
        return switch (points) {
            case 3 -> "W";
            case 1 -> "D";
            default -> "L";
        };
    }

    /**
     * Sum points from a slice of the form matches list.
     */
    private int calculatePoints(List<FormMatchDTO> matches, int fromIndex, int toIndex) {
        int end = Math.min(toIndex, matches.size());
        if (fromIndex >= end) return 0;

        return matches.subList(fromIndex, end).stream()
                .mapToInt(FormMatchDTO::getPoints)
                .sum();
    }

    /**
     * Determine the form trend by comparing the last 5 vs previous 5 points.
     * Requires a full 10-match history (both windows filled) for a meaningful comparison.
     * A difference of more than 3 points (i.e. 4+) is needed to classify as Improving/Declining,
     * since a 3-point swing can result from a single match outcome change.
     */
    private String determineTrend(int pointsLast5, int pointsPrev5, int totalMatches) {
        if (totalMatches < TREND_WINDOW * 2) {
            // Need both 5-match windows filled for meaningful trend
            return "Stable";
        }
        int diff = pointsLast5 - pointsPrev5;
        if (diff > 3) return "Improving";
        if (diff < -3) return "Declining";
        return "Stable";
    }

    /**
     * Calculate a 0–10 form rating based on points earned in the window.
     * <p>Max possible = 3 pts × window size. Rating = (actual / max) × 10.</p>
     */
    private double calculateFormRating(List<FormMatchDTO> matches) {
        if (matches.isEmpty()) return 0.0;

        int totalPoints = matches.stream().mapToInt(FormMatchDTO::getPoints).sum();
        int maxPoints = matches.size() * 3;
        double rating = ((double) totalPoints / maxPoints) * 10.0;

        return Math.round(rating * 10.0) / 10.0; // 1 decimal place
    }

    /**
     * Build a human-readable form string, e.g. "W-W-D-L-W".
     * Ordered newest-first (same as the list).
     */
    private String buildFormString(List<FormMatchDTO> matches) {
        return matches.stream()
                .map(FormMatchDTO::getResult)
                .collect(Collectors.joining("-"));
    }

    /**
     * Build an empty guide when no matches exist for the season.
     */
    private FormGuideDTO buildEmptyGuide(String teamName, String season) {
        return FormGuideDTO.builder()
                .teamName(teamName)
                .recentMatches(new ArrayList<>())
                .pointsInLast5(0)
                .pointsInPrevious5(0)
                .formTrend("Stable")
                .formRating(0.0)
                .formString("")
                .season(season)
                .totalMatchesInSeason(0)
                .build();
    }

    /**
     * Resolve team name via centralized validation service.
     */
    private String resolveTeamName(String teamName) {
        return teamValidationService.resolveTeamName(teamName);
    }

    /**
     * Determine the current football season.
     * Delegates to the repository for data-driven season detection,
     * falling back to {@link SeasonUtils#getCurrentSeason()}.
     */
    private String determineCurrentSeason() {
        String season = matchRepository.findCurrentSeason();
        if (season != null && !season.isBlank()) return season;

        return SeasonUtils.getCurrentSeason();
    }
}

