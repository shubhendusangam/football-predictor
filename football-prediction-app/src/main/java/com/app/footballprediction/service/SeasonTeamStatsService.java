package com.app.footballprediction.service;

import com.app.common.model.SeasonTeamStats;
import com.app.common.repository.SeasonTeamStatsRepository;
import com.app.footballprediction.config.CacheConfig;
import com.app.footballprediction.dto.SeasonTeamStatsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for retrieving team statistics for seasons.
 * Provides read-only access to season team stats including Elo ratings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SeasonTeamStatsService {

    private final SeasonTeamStatsRepository seasonTeamStatsRepository;

    /**
     * Get stats for a specific team in a specific season.
     *
     * @param seasonId The season identifier
     * @param teamId The team ID
     * @return Optional containing stats if found
     */
    @Cacheable(value = CacheConfig.CACHE_ELO_RATINGS, key = "'team_' + #seasonId + '_' + #teamId")
    public Optional<SeasonTeamStatsResponse> getStatsBySeasonAndTeamId(String seasonId, Long teamId) {
        log.debug("Fetching stats for team {} in season {}", teamId, seasonId);
        return seasonTeamStatsRepository.findBySeasonIdAndTeamId(seasonId, teamId)
                .map(this::toResponse);
    }

    /**
     * Get stats for a team by name in a specific season.
     *
     * @param seasonId The season identifier
     * @param teamName The team name
     * @return Optional containing stats if found
     */
    @Cacheable(value = CacheConfig.CACHE_ELO_RATINGS, key = "'team_' + #seasonId + '_' + #teamName.toLowerCase()")
    public Optional<SeasonTeamStatsResponse> getStatsBySeasonAndTeamName(String seasonId, String teamName) {
        log.debug("Fetching stats for team '{}' in season {}", teamName, seasonId);
        return seasonTeamStatsRepository.findBySeasonIdAndTeamNameIgnoreCase(seasonId, teamName)
                .map(this::toResponse);
    }

    /**
     * Get all team stats for a season ordered by Elo rating.
     *
     * @param seasonId The season identifier
     * @return List of team stats ordered by Elo rating descending
     */
    @Cacheable(value = CacheConfig.CACHE_ELO_RATINGS, key = "'elo_rankings_' + #seasonId")
    public List<SeasonTeamStatsResponse> getSeasonStatsOrderedByElo(String seasonId) {
        log.debug("Fetching all team stats for season {} ordered by Elo", seasonId);
        return seasonTeamStatsRepository.findBySeasonIdOrderByEloRatingDesc(seasonId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get all team stats for a season ordered by points.
     *
     * @param seasonId The season identifier
     * @return List of team stats ordered by points descending
     */
    @Cacheable(value = CacheConfig.CACHE_SEASON_STATS, key = "'points_' + #seasonId")
    public List<SeasonTeamStatsResponse> getSeasonStatsOrderedByPoints(String seasonId) {
        log.debug("Fetching all team stats for season {} ordered by points", seasonId);
        return seasonTeamStatsRepository.findBySeasonIdOrderByPointsDesc(seasonId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get historical stats for a team across all seasons.
     *
     * @param teamId The team ID
     * @return List of stats for each season
     */
    @Cacheable(value = CacheConfig.CACHE_SEASON_STATS, key = "'history_' + #teamId")
    public List<SeasonTeamStatsResponse> getTeamHistoricalStats(Long teamId) {
        log.debug("Fetching historical stats for team {}", teamId);
        return seasonTeamStatsRepository.findByTeamIdOrderBySeasonIdDesc(teamId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get teams currently on a winning streak.
     *
     * @param seasonId The season identifier
     * @return List of teams on winning streaks
     */
    @Cacheable(value = CacheConfig.CACHE_ELO_RATINGS, key = "'winning_streak_' + #seasonId")
    public List<SeasonTeamStatsResponse> getTeamsOnWinningStreak(String seasonId) {
        log.debug("Fetching teams on winning streak in season {}", seasonId);
        return seasonTeamStatsRepository.findTeamsOnWinningStreak(seasonId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get teams currently on a losing streak.
     *
     * @param seasonId The season identifier
     * @return List of teams on losing streaks
     */
    @Cacheable(value = CacheConfig.CACHE_ELO_RATINGS, key = "'losing_streak_' + #seasonId")
    public List<SeasonTeamStatsResponse> getTeamsOnLosingStreak(String seasonId) {
        log.debug("Fetching teams on losing streak in season {}", seasonId);
        return seasonTeamStatsRepository.findTeamsOnLosingStreak(seasonId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get top teams by form (last 5 matches).
     *
     * @param seasonId The season identifier
     * @param limit Maximum number of teams to return
     * @return List of top teams by form
     */
    @Cacheable(value = CacheConfig.CACHE_ELO_RATINGS, key = "'form_' + #seasonId + '_' + #limit")
    public List<SeasonTeamStatsResponse> getTopTeamsByForm(String seasonId, int limit) {
        log.debug("Fetching top {} teams by form in season {}", limit, seasonId);
        return seasonTeamStatsRepository.findTopByForm(seasonId)
                .stream()
                .limit(limit)
                .map(this::toResponse)
                .toList();
    }

    /**
     * Get average Elo rating for a season.
     *
     * @param seasonId The season identifier
     * @return Average Elo rating
     */
    @Cacheable(value = CacheConfig.CACHE_ELO_RATINGS, key = "'avg_elo_' + #seasonId")
    public double getAverageEloRating(String seasonId) {
        Double avg = seasonTeamStatsRepository.getAverageEloRating(seasonId);
        return avg != null ? avg : SeasonTeamStats.DEFAULT_ELO_RATING;
    }

    /**
     * Check if stats exist for a team in a season.
     *
     * @param seasonId The season identifier
     * @param teamId The team ID
     * @return true if stats exist
     */
    public boolean statsExist(String seasonId, Long teamId) {
        return seasonTeamStatsRepository.existsBySeasonIdAndTeamId(seasonId, teamId);
    }

    /**
     * Get count of teams in a season.
     *
     * @param seasonId The season identifier
     * @return Number of teams with stats
     */
    public long getTeamCount(String seasonId) {
        return seasonTeamStatsRepository.countBySeasonId(seasonId);
    }

    /**
     * Convert entity to response DTO.
     */
    private SeasonTeamStatsResponse toResponse(SeasonTeamStats stats) {
        return SeasonTeamStatsResponse.builder()
                .id(stats.getId())
                .seasonId(stats.getSeasonId())
                .teamId(stats.getTeamId())
                .teamName(stats.getTeamName())
                .matchesPlayed(stats.getMatchesPlayed())
                .wins(stats.getWins())
                .draws(stats.getDraws())
                .losses(stats.getLosses())
                .goalsScored(stats.getGoalsScored())
                .goalsConceded(stats.getGoalsConceded())
                .goalDifference(stats.getGoalDifference())
                .cleanSheets(stats.getCleanSheets())
                .totalPoints(stats.getTotalPoints())
                .pointsPerGame(Math.round(stats.getPointsPerGame() * 100.0) / 100.0)
                .currentStreak(stats.getCurrentStreak())
                .formPointsLast5(stats.getFormPointsLast5())
                .formString(stats.getFormString())
                .eloRating(Math.round(stats.getEloRating() * 100.0) / 100.0)
                .winPercentage(Math.round(stats.getWinPercentage() * 10.0) / 10.0)
                .avgGoalsScored(Math.round(stats.getAvgGoalsScored() * 100.0) / 100.0)
                .avgGoalsConceded(Math.round(stats.getAvgGoalsConceded() * 100.0) / 100.0)
                .lastUpdated(stats.getLastUpdated())
                .build();
    }
}

