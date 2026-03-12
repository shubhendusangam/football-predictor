package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.util.SeasonHelper;
import com.app.footballprediction.dto.external.FootballApiResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Stateless mapper between external API match DTOs and the local {@link Match} entity.
 *
 * <p>Extracted from {@code ApiDataSyncService} to isolate data-transformation
 * logic from orchestration/sync logic.</p>
 */
@Slf4j
public final class MatchMapper {

    private MatchMapper() {}

    /**
     * Build a new {@link Match} entity from an API response match.
     *
     * @param apiMatch  the external API match DTO
     * @param homeTeam  normalized home team name
     * @param awayTeam  normalized away team name
     * @param matchDate parsed match date
     * @return a fully populated (but unsaved) {@code Match}
     */
    public static Match fromApi(FootballApiResponse.ApiMatch apiMatch,
                                String homeTeam,
                                String awayTeam,
                                LocalDate matchDate) {
        Integer homeGoals = null;
        Integer awayGoals = null;
        Integer htHomeGoals = null;
        Integer htAwayGoals = null;
        String result = null;
        String htResult = null;

        if (apiMatch.getScore() != null) {
            if (apiMatch.getScore().getFullTime() != null) {
                homeGoals = apiMatch.getScore().getFullTime().getHome();
                awayGoals = apiMatch.getScore().getFullTime().getAway();
                result = determineResult(homeGoals, awayGoals);
            }
            if (apiMatch.getScore().getHalfTime() != null) {
                htHomeGoals = apiMatch.getScore().getHalfTime().getHome();
                htAwayGoals = apiMatch.getScore().getHalfTime().getAway();
                htResult = determineResult(htHomeGoals, htAwayGoals);
            }
        }

        return Match.builder()
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .matchDate(matchDate)
                .season(SeasonHelper.deriveSeason(matchDate))
                .kickoffTime(extractKickoffTime(apiMatch.getUtcDate()))
                .fullTimeHomeGoals(homeGoals)
                .fullTimeAwayGoals(awayGoals)
                .fullTimeResult(result)
                .halfTimeHomeGoals(htHomeGoals)
                .halfTimeAwayGoals(htAwayGoals)
                .halfTimeResult(htResult)
                .build();
    }

    /**
     * Merge API data into an existing match.
     *
     * @return {@code true} if any field changed
     */
    public static boolean updateFromApi(Match existing, FootballApiResponse.ApiMatch apiMatch) {
        boolean changed = false;

        if (apiMatch.getScore() != null && apiMatch.getScore().getFullTime() != null) {
            Integer newHomeGoals = apiMatch.getScore().getFullTime().getHome();
            Integer newAwayGoals = apiMatch.getScore().getFullTime().getAway();

            if (newHomeGoals != null && !newHomeGoals.equals(existing.getFullTimeHomeGoals())) {
                existing.setFullTimeHomeGoals(newHomeGoals);
                changed = true;
            }
            if (newAwayGoals != null && !newAwayGoals.equals(existing.getFullTimeAwayGoals())) {
                existing.setFullTimeAwayGoals(newAwayGoals);
                changed = true;
            }
            if (changed) {
                existing.setFullTimeResult(determineResult(newHomeGoals, newAwayGoals));
            }
        }

        if (apiMatch.getScore() != null && apiMatch.getScore().getHalfTime() != null) {
            Integer htHome = apiMatch.getScore().getHalfTime().getHome();
            Integer htAway = apiMatch.getScore().getHalfTime().getAway();

            boolean htChanged = false;
            if (htHome != null && !htHome.equals(existing.getHalfTimeHomeGoals())) {
                existing.setHalfTimeHomeGoals(htHome);
                htChanged = true;
            }
            if (htAway != null && !htAway.equals(existing.getHalfTimeAwayGoals())) {
                existing.setHalfTimeAwayGoals(htAway);
                htChanged = true;
            }
            if (htChanged) {
                // Recompute half-time result using the now-current values on the entity
                existing.setHalfTimeResult(determineResult(
                        existing.getHalfTimeHomeGoals(), existing.getHalfTimeAwayGoals()));
                changed = true;
            }
        }

        // Backfill kick-off time if missing
        if (existing.getKickoffTime() == null || existing.getKickoffTime().isBlank()) {
            String kickoff = extractKickoffTime(apiMatch.getUtcDate());
            if (kickoff != null) {
                existing.setKickoffTime(kickoff);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Extract {@link LocalDate} from an API UTC date string ({@code "2026-02-15T15:00:00Z"}).
     */
    public static LocalDate extractMatchDate(String utcDate) {
        if (utcDate == null || utcDate.isEmpty()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(utcDate.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", utcDate);
            return LocalDate.now();
        }
    }

    /**
     * Extract kick-off time ({@code "HH:mm"}) from a UTC date string.
     */
    public static String extractKickoffTime(String utcDate) {
        if (utcDate == null || utcDate.length() < 16) {
            return null;
        }
        try {
            return utcDate.substring(11, 16);
        } catch (Exception e) {
            log.warn("Failed to extract kick-off time from: {}", utcDate);
            return null;
        }
    }

    /**
     * Determine match result from goal counts.
     *
     * @return {@code "H"} for home win, {@code "D"} for draw, {@code "A"} for away win, or {@code null}
     */
    public static String determineResult(Integer homeGoals, Integer awayGoals) {
        if (homeGoals == null || awayGoals == null) return null;
        if (homeGoals > awayGoals) return "H";
        if (homeGoals < awayGoals) return "A";
        return "D";
    }
}

