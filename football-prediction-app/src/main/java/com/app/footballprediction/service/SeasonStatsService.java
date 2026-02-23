package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.SeasonStatsResponse;
import com.app.footballprediction.dto.SeasonStatsResponse.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for season-based historical data analysis.
 * Provides statistics for teams across different seasons.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SeasonStatsService {

    private final MatchRepository matchRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Get all available seasons.
     *
     * @return List of season strings (e.g., ["2023-24", "2022-23", ...])
     */
    @Cacheable(value = "seasons", key = "'allSeasons'")
    public List<String> getAllSeasons() {
        log.info("Fetching all available seasons");
        List<Match> allMatches = matchRepository.findAllByOrderByMatchDateDesc();

        return allMatches.stream()
                .map(Match::getSeason)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    /**
     * Get statistics for a specific season with pagination, sorting, and filtering.
     *
     * @param season Season string (e.g., "2023-24")
     * @param page Page number (0-indexed)
     * @param pageSize Number of items per page
     * @param sortBy Field to sort by (team, matches, bttsRate, over25Rate, winRate, points)
     * @param sortDir Sort direction (asc, desc)
     * @param teamFilter Optional team name filter
     * @return SeasonStatsResponse with team statistics
     */
    @Cacheable(value = "seasonStats", key = "#season + '_' + #page + '_' + #pageSize + '_' + #sortBy + '_' + #sortDir + '_' + #teamFilter")
    public SeasonStatsResponse getSeasonStats(String season, int page, int pageSize,
                                               String sortBy, String sortDir, String teamFilter) {
        log.info("Fetching stats for season: {}, page: {}, pageSize: {}, sortBy: {}, sortDir: {}, filter: {}",
                season, page, pageSize, sortBy, sortDir, teamFilter);

        List<Match> allMatches = matchRepository.findAllByOrderByMatchDateDesc();

        // Filter by season
        List<Match> seasonMatches = allMatches.stream()
                .filter(m -> season.equals(m.getSeason()))
                .collect(Collectors.toList());

        if (seasonMatches.isEmpty()) {
            log.warn("No matches found for season: {}", season);
            return SeasonStatsResponse.builder()
                    .season(season)
                    .totalMatches(0)
                    .totalTeams(0)
                    .teamStats(Collections.emptyList())
                    .pagination(PaginationInfo.builder()
                            .page(page)
                            .pageSize(pageSize)
                            .totalItems(0)
                            .totalPages(0)
                            .build())
                    .build();
        }

        // Get all unique teams
        Set<String> teams = new HashSet<>();
        seasonMatches.forEach(m -> {
            if (m.getHomeTeam() != null) teams.add(m.getHomeTeam());
            if (m.getAwayTeam() != null) teams.add(m.getAwayTeam());
        });

        // Calculate stats for each team
        List<TeamSeasonStats> allTeamStats = teams.stream()
                .map(team -> calculateTeamSeasonStats(team, seasonMatches))
                .collect(Collectors.toList());

        // Apply team filter
        if (teamFilter != null && !teamFilter.isEmpty()) {
            String lowerFilter = teamFilter.toLowerCase();
            allTeamStats = allTeamStats.stream()
                    .filter(ts -> ts.getTeam().toLowerCase().contains(lowerFilter))
                    .collect(Collectors.toList());
        }

        // Sort
        Comparator<TeamSeasonStats> comparator = getComparator(sortBy, sortDir);
        allTeamStats.sort(comparator);

        // Pagination
        int totalItems = allTeamStats.size();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        int start = page * pageSize;
        int end = Math.min(start + pageSize, totalItems);

        List<TeamSeasonStats> pagedStats = start < totalItems
                ? allTeamStats.subList(start, end)
                : Collections.emptyList();

        return SeasonStatsResponse.builder()
                .season(season)
                .totalMatches(seasonMatches.size())
                .totalTeams(teams.size())
                .teamStats(pagedStats)
                .pagination(PaginationInfo.builder()
                        .page(page)
                        .pageSize(pageSize)
                        .totalItems(totalItems)
                        .totalPages(totalPages)
                        .build())
                .build();
    }

    /**
     * Calculate statistics for a single team in a season.
     */
    private TeamSeasonStats calculateTeamSeasonStats(String team, List<Match> seasonMatches) {
        List<Match> teamMatches = seasonMatches.stream()
                .filter(m -> team.equals(m.getHomeTeam()) || team.equals(m.getAwayTeam()))
                .sorted(Comparator.comparing(Match::getMatchDate).reversed())
                .toList();

        int matches = 0;
        int wins = 0;
        int draws = 0;
        int losses = 0;
        int goalsScored = 0;
        int goalsConceded = 0;

        List<MatchResult> recentForm = new ArrayList<>();

        for (Match match : teamMatches) {
            boolean isHome = team.equals(match.getHomeTeam());
            Integer teamGoals = isHome ? match.getFullTimeHomeGoals() : match.getFullTimeAwayGoals();
            Integer oppGoals = isHome ? match.getFullTimeAwayGoals() : match.getFullTimeHomeGoals();
            String opponent = isHome ? match.getAwayTeam() : match.getHomeTeam();

            if (teamGoals == null || oppGoals == null) continue;

            matches++;
            goalsScored += teamGoals;
            goalsConceded += oppGoals;

            String result;
            int points;
            if (teamGoals > oppGoals) {
                wins++;
                result = "W";
                points = 3;
            } else if (teamGoals.equals(oppGoals)) {
                draws++;
                result = "D";
                points = 1;
            } else {
                losses++;
                result = "L";
                points = 0;
            }

            // Add to match history (all matches in season)
            recentForm.add(MatchResult.builder()
                    .date(match.getMatchDate().format(DATE_FORMATTER))
                    .opponent(opponent)
                    .goalsScored(teamGoals)
                    .goalsConceded(oppGoals)
                    .result(result)
                    .points(points)
                    .build());
        }

        int totalPoints = (wins * 3) + draws;
        double winRate = matches > 0 ? (double) wins / matches * 100 : 0;

        return TeamSeasonStats.builder()
                .team(team)
                .matches(matches)
                .wins(wins)
                .draws(draws)
                .losses(losses)
                .goalsScored(goalsScored)
                .goalsConceded(goalsConceded)
                .points(totalPoints)
                .winRate(Math.round(winRate * 10.0) / 10.0)
                .recentForm(recentForm)
                .build();
    }

    /**
     * Get comparator based on sort parameters.
     */
    private Comparator<TeamSeasonStats> getComparator(String sortBy, String sortDir) {
        Comparator<TeamSeasonStats> comparator = switch (sortBy.toLowerCase()) {
           case "matches" -> Comparator.comparingInt(TeamSeasonStats::getMatches);
           case "winrate" -> Comparator.comparingDouble(TeamSeasonStats::getWinRate);
           case "points" -> Comparator.comparingInt(TeamSeasonStats::getPoints);
           default -> Comparator.comparing(TeamSeasonStats::getTeam);
        };

       if ("desc".equalsIgnoreCase(sortDir)) {
            comparator = comparator.reversed();
        }

        return comparator;
    }
}

