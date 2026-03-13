package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.common.util.SeasonHelper;
import com.app.footballprediction.config.CacheConfig;
import com.app.footballprediction.dto.GameweekPositionDTO;
import com.app.footballprediction.dto.PositionHistoryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for calculating a team's league-position progression over a season.
 * <p>
 * For every match the team has played (treated as a "gameweek"), the full
 * league table up to and including that match date is recalculated so that
 * the team's position at each point in time is accurate.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PositionHistoryService {

    private final MatchRepository matchRepository;
    private final TeamValidationService teamValidationService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Get the position history for a team across a season.
     *
     * @param teamName raw team name (alias / partial / exact)
     * @param season   season string, e.g. "2025-26". If {@code null}, defaults to the current season.
     * @return {@link PositionHistoryDTO} with chronological progression
     */
    @Cacheable(value = CacheConfig.CACHE_POSITION_HISTORY, key = "#teamName + '_' + #season")
    public PositionHistoryDTO getPositionHistory(String teamName, String season) {
        log.info("Calculating position history for team='{}', season='{}'", teamName, season);

        // Resolve canonical team name
        String resolvedTeam = teamValidationService.resolveTeamName(teamName);

        // Normalise season (or derive current)
        String resolvedSeason = (season != null && !season.isBlank())
                ? SeasonHelper.normalizeSeason(season)
                : SeasonHelper.currentSeason();

        log.debug("Resolved: team='{}', season='{}'", resolvedTeam, resolvedSeason);

        // ── 1. Fetch ALL completed matches for the season (chronological) ──
        //      Try both dash (2025-26) and slash (2025/26) formats for backward compatibility
        LocalDate seasonEnd = getSeasonEndDate(resolvedSeason);
        List<Match> allSeasonMatches = matchRepository.findBySeasonBeforeDateForTable(resolvedSeason, seasonEnd);

        if (allSeasonMatches.isEmpty()) {
            String altSeason = resolvedSeason.replace("-", "/");
            allSeasonMatches = matchRepository.findBySeasonBeforeDateForTable(altSeason, seasonEnd);
            if (!allSeasonMatches.isEmpty()) {
                log.info("Found matches with alternate season format: {}", altSeason);
            }
        }

        if (allSeasonMatches.isEmpty()) {
            log.warn("No matches found for season {}", resolvedSeason);
            return PositionHistoryDTO.builder()
                    .teamName(resolvedTeam)
                    .season(resolvedSeason)
                    .progression(Collections.emptyList())
                    .highestPosition(0)
                    .lowestPosition(0)
                    .currentPosition(0)
                    .totalTeams(0)
                    .build();
        }

        // ── 2. Identify the team's own match dates (ascending) ──
        List<Match> teamMatches = allSeasonMatches.stream()
                .filter(m -> m.getHomeTeam() != null && m.getAwayTeam() != null)
                .filter(m -> m.getHomeTeam().equalsIgnoreCase(resolvedTeam)
                          || m.getAwayTeam().equalsIgnoreCase(resolvedTeam))
                .toList();

        if (teamMatches.isEmpty()) {
            log.warn("Team '{}' has no matches in season {}", resolvedTeam, resolvedSeason);
            return PositionHistoryDTO.builder()
                    .teamName(resolvedTeam)
                    .season(resolvedSeason)
                    .progression(Collections.emptyList())
                    .highestPosition(0)
                    .lowestPosition(0)
                    .currentPosition(0)
                    .totalTeams(0)
                    .build();
        }

        // ── 3. For each team match, compute the full league table up to that date ──
        List<GameweekPositionDTO> progression = new ArrayList<>();
        int highestPosition = Integer.MAX_VALUE;
        int lowestPosition = 0;
        int currentPosition = 0;
        int totalTeams = 0;
        int gameweek = 0;

        for (Match teamMatch : teamMatches) {
            gameweek++;

            // All matches played on or before this match's date
            LocalDate cutoffDate = teamMatch.getMatchDate().plusDays(1); // inclusive
            List<Match> matchesUpToNow = allSeasonMatches.stream()
                    .filter(m -> m.getMatchDate().isBefore(cutoffDate))
                    .toList();

            // Build standings
            Map<String, TeamStandingData> standingsMap = buildStandings(matchesUpToNow);
            List<Map.Entry<String, TeamStandingData>> sorted = sortStandings(standingsMap);

            // Find team's position
            int position = 0;
            for (int i = 0; i < sorted.size(); i++) {
                if (sorted.get(i).getKey().equalsIgnoreCase(resolvedTeam)) {
                    position = i + 1;
                    break;
                }
            }

            totalTeams = sorted.size();
            if (position == 0) position = totalTeams; // safety fallback

            highestPosition = Math.min(highestPosition, position);
            lowestPosition = Math.max(lowestPosition, position);
            currentPosition = position;

            // Determine opponent & result
            boolean isHome = teamMatch.getHomeTeam().equalsIgnoreCase(resolvedTeam);
            String opponent = isHome ? teamMatch.getAwayTeam() : teamMatch.getHomeTeam();
            String result = getResultLetter(teamMatch.getPointsForTeam(resolvedTeam));

            // Points for this team so far
            int points = standingsMap.containsKey(resolvedTeam)
                    ? standingsMap.get(resolvedTeam).points
                    : standingsMap.entrySet().stream()
                        .filter(e -> e.getKey().equalsIgnoreCase(resolvedTeam))
                        .map(e -> e.getValue().points)
                        .findFirst().orElse(0);

            progression.add(GameweekPositionDTO.builder()
                    .gameweek(gameweek)
                    .position(position)
                    .points(points)
                    .date(teamMatch.getMatchDate().format(DATE_FORMATTER))
                    .opponent(opponent)
                    .result(result)
                    .build());
        }

        if (highestPosition == Integer.MAX_VALUE) highestPosition = 0;

        return PositionHistoryDTO.builder()
                .teamName(resolvedTeam)
                .season(resolvedSeason)
                .progression(progression)
                .highestPosition(highestPosition)
                .lowestPosition(lowestPosition)
                .currentPosition(currentPosition)
                .totalTeams(totalTeams)
                .build();
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    /**
     * Build a standings map from a list of matches.
     */
    private Map<String, TeamStandingData> buildStandings(List<Match> matches) {
        Map<String, TeamStandingData> map = new LinkedHashMap<>();
        for (Match m : matches) {
            String home = m.getHomeTeam();
            String away = m.getAwayTeam();
            if (home == null || away == null) continue;

            map.computeIfAbsent(home, k -> new TeamStandingData());
            map.computeIfAbsent(away, k -> new TeamStandingData());

            int hg = m.getFullTimeHomeGoals() != null ? m.getFullTimeHomeGoals() : 0;
            int ag = m.getFullTimeAwayGoals() != null ? m.getFullTimeAwayGoals() : 0;

            map.get(home).update(hg, ag);
            map.get(away).update(ag, hg);
        }
        return map;
    }

    /**
     * Sort standings: points DESC → goal difference DESC → goals for DESC → name ASC.
     */
    private List<Map.Entry<String, TeamStandingData>> sortStandings(Map<String, TeamStandingData> map) {
        return map.entrySet().stream()
                .sorted((a, b) -> {
                    int cmp = Integer.compare(b.getValue().points, a.getValue().points);
                    if (cmp != 0) return cmp;
                    cmp = Integer.compare(b.getValue().goalDifference, a.getValue().goalDifference);
                    if (cmp != 0) return cmp;
                    cmp = Integer.compare(b.getValue().goalsFor, a.getValue().goalsFor);
                    if (cmp != 0) return cmp;
                    return a.getKey().compareToIgnoreCase(b.getKey());
                })
                .collect(Collectors.toList());
    }

    private String getResultLetter(int points) {
        return switch (points) {
            case 3 -> "W";
            case 1 -> "D";
            default -> "L";
        };
    }

    private LocalDate getSeasonEndDate(String season) {
        try {
            String[] parts = season.split("[/-]");
            int endYear;
            if (parts.length > 1) {
                String yearPart = parts[1];
                endYear = Integer.parseInt(yearPart.length() == 2 ? "20" + yearPart : yearPart);
            } else {
                endYear = Integer.parseInt(parts[0]) + 1;
            }
            return LocalDate.of(endYear, 7, 31);
        } catch (Exception e) {
            log.warn("Failed to parse season end date from: {}", season);
            return LocalDate.of(2026, 7, 31);
        }
    }

    /**
     * Internal accumulator for points / GD / GF.
     */
    private static class TeamStandingData {
        int points = 0;
        int goalsFor = 0;
        int goalsAgainst = 0;
        int goalDifference = 0;

        void update(int scored, int conceded) {
            this.goalsFor += scored;
            this.goalsAgainst += conceded;
            this.goalDifference = this.goalsFor - this.goalsAgainst;
            if (scored > conceded) this.points += 3;
            else if (scored == conceded) this.points += 1;
        }
    }
}



