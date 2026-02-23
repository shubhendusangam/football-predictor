package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.model.SeasonTeamStats;
import com.app.common.model.Team;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.SeasonTeamStatsRepository;
import com.app.common.repository.TeamRepository;
import com.app.common.service.EloRatingService;
import com.app.footballprediction.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for handling match completion and updating team statistics.
 *
 * When a match is marked as COMPLETED:
 * 1. Fetch or create SeasonTeamStats for both teams
 * 2. Update all statistics (matches, wins, draws, losses, goals, etc.)
 * 3. Update Elo ratings using the Elo formula
 * 4. Update form and streak data
 * 5. Save both records in a single transaction
 * 6. Invalidate relevant caches
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchCompletionService {

    private final SeasonTeamStatsRepository seasonTeamStatsRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final EloRatingService eloRatingService;

    private static final int FORM_MATCHES = 5;

    /**
     * Process a completed match and update team statistics.
     * This method should be called when a match status changes to COMPLETED.
     *
     * @param match The completed match
     * @throws IllegalArgumentException if match data is invalid
     * @throws IllegalStateException if match is not completed or already processed
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_ELO_RATINGS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_SEASON_STATS, allEntries = true)
    })
    public void processCompletedMatch(Match match) {
        validateMatch(match);

        // Idempotency check - prevent duplicate processing
        if (Boolean.TRUE.equals(match.getStatsProcessed())) {
            log.warn("Match {} has already been processed for stats. Skipping.", match.getId());
            return;
        }

        String seasonId = match.getSeason();
        String homeTeamName = match.getHomeTeam();
        String awayTeamName = match.getAwayTeam();
        int homeGoals = match.getFullTimeHomeGoals();
        int awayGoals = match.getFullTimeAwayGoals();

        log.info("Processing completed match: {} vs {} ({}-{}) in season {}",
                homeTeamName, awayTeamName, homeGoals, awayGoals, seasonId);

        // Get or create team records
        Team homeTeam = getOrCreateTeam(homeTeamName);
        Team awayTeam = getOrCreateTeam(awayTeamName);

        // Get or create season stats
        SeasonTeamStats homeStats = getOrCreateSeasonStats(seasonId, homeTeam);
        SeasonTeamStats awayStats = getOrCreateSeasonStats(seasonId, awayTeam);

        // Calculate new Elo ratings before updating other stats
        double[] newRatings = eloRatingService.calculateMatchRatings(
                homeStats.getEloRating(),
                awayStats.getEloRating(),
                homeGoals,
                awayGoals
        );

        log.debug("Elo update: {} ({} -> {}), {} ({} -> {})",
                homeTeamName, homeStats.getEloRating(), newRatings[0],
                awayTeamName, awayStats.getEloRating(), newRatings[1]);

        // Update home team stats
        updateTeamStats(homeStats, homeGoals, awayGoals, newRatings[0], homeTeamName, seasonId, match.getMatchDate());

        // Update away team stats
        updateTeamStats(awayStats, awayGoals, homeGoals, newRatings[1], awayTeamName, seasonId, match.getMatchDate());

        // Save both in same transaction
        seasonTeamStatsRepository.save(homeStats);
        seasonTeamStatsRepository.save(awayStats);

        // Mark match as processed to prevent duplicate processing
        match.setStatsProcessed(true);
        matchRepository.save(match);

        log.info("Updated stats for {} (Elo: {}) and {} (Elo: {})",
                homeTeamName, homeStats.getEloRating(),
                awayTeamName, awayStats.getEloRating());
    }

    /**
     * Process a match by ID.
     *
     * @param matchId The match ID
     * @throws IllegalArgumentException if match not found
     */
    @Transactional
    public void processCompletedMatch(Long matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));
        processCompletedMatch(match);
    }

    /**
     * Validate that the match has all required data for processing.
     */
    private void validateMatch(Match match) {
        if (match == null) {
            throw new IllegalArgumentException("Match cannot be null");
        }
        if (match.getHomeTeam() == null || match.getHomeTeam().isBlank()) {
            throw new IllegalArgumentException("Home team name is required");
        }
        if (match.getAwayTeam() == null || match.getAwayTeam().isBlank()) {
            throw new IllegalArgumentException("Away team name is required");
        }
        if (match.getFullTimeHomeGoals() == null || match.getFullTimeAwayGoals() == null) {
            throw new IllegalStateException("Match must have final score to be processed");
        }
        if (match.getSeason() == null || match.getSeason().isBlank()) {
            throw new IllegalArgumentException("Season is required");
        }
        if (match.getFullTimeResult() == null || match.getFullTimeResult().isBlank()) {
            throw new IllegalStateException("Match must have a result (H/D/A) to be processed");
        }
    }

    /**
     * Get existing team or create a new one.
     */
    private Team getOrCreateTeam(String teamName) {
        return teamRepository.findByNameIgnoreCase(teamName)
                .orElseGet(() -> {
                    log.info("Creating new team: {}", teamName);
                    Team newTeam = Team.builder()
                            .name(teamName)
                            .build();
                    return teamRepository.save(newTeam);
                });
    }

    /**
     * Get existing season stats or create with default values.
     */
    private SeasonTeamStats getOrCreateSeasonStats(String seasonId, Team team) {
        return seasonTeamStatsRepository.findBySeasonIdAndTeamId(seasonId, team.getId())
                .orElseGet(() -> {
                    log.info("Creating season stats for team {} in season {}", team.getName(), seasonId);
                    return SeasonTeamStats.createDefault(seasonId, team.getId(), team.getName());
                });
    }

    /**
     * Update team statistics based on match result.
     */
    private void updateTeamStats(SeasonTeamStats stats, int goalsFor, int goalsAgainst,
                                  double newEloRating, String teamName, String seasonId,
                                  LocalDate matchDate) {
        // Update basic stats
        stats.setMatchesPlayed(stats.getMatchesPlayed() + 1);
        stats.setGoalsScored(stats.getGoalsScored() + goalsFor);
        stats.setGoalsConceded(stats.getGoalsConceded() + goalsAgainst);

        // Update clean sheets
        if (goalsAgainst == 0) {
            stats.setCleanSheets(stats.getCleanSheets() + 1);
        }

        // Update win/draw/loss and current result
        char currentResult;
        if (goalsFor > goalsAgainst) {
            stats.setWins(stats.getWins() + 1);
            currentResult = 'W';
        } else if (goalsFor < goalsAgainst) {
            stats.setLosses(stats.getLosses() + 1);
            currentResult = 'L';
        } else {
            stats.setDraws(stats.getDraws() + 1);
            currentResult = 'D';
        }

        // Update form string (most recent first)
        String currentForm = stats.getFormString();
        String newForm = currentResult + (currentForm.length() >= FORM_MATCHES
                ? currentForm.substring(0, FORM_MATCHES - 1)
                : currentForm);
        stats.setFormString(newForm);

        // Calculate form points from last 5
        stats.setFormPointsLast5(calculateFormPoints(newForm));

        // Update streak
        stats.setCurrentStreak(calculateStreak(teamName, seasonId, matchDate));

        // Update Elo rating
        stats.setEloRating(newEloRating);
    }

    /**
     * Calculate form points from form string.
     * W = 3, D = 1, L = 0
     */
    private int calculateFormPoints(String formString) {
        int points = 0;
        for (char c : formString.toCharArray()) {
            switch (c) {
                case 'W' -> points += 3;
                case 'D' -> points += 1;
                // L = 0
            }
        }
        return points;
    }

    /**
     * Calculate current streak for a team.
     * Returns format like "W3", "D1", "L2", "U5" (unbeaten)
     */
    private String calculateStreak(String teamName, String seasonId, LocalDate beforeDate) {
        List<Match> recentMatches = matchRepository.findByTeamAndSeasonBeforeDate(
                teamName, seasonId, beforeDate.plusDays(1));

        if (recentMatches.isEmpty()) {
            return "N0"; // No matches
        }

        // Include current match in streak calculation
        int winStreak = 0;
        int drawStreak = 0;
        int lossStreak = 0;
        int unbeatenStreak = 0;

        for (Match match : recentMatches) {
            int points = match.getPointsForTeam(teamName);

            if (points == 3) { // Win
                if (lossStreak > 0 || drawStreak > 0) break;
                winStreak++;
                unbeatenStreak++;
            } else if (points == 1) { // Draw
                if (lossStreak > 0 || winStreak > 0) break;
                drawStreak++;
                unbeatenStreak++;
            } else { // Loss
                if (winStreak > 0 || drawStreak > 0) break;
                lossStreak++;
                unbeatenStreak = 0;
            }
        }

        // Determine streak type
        if (winStreak > 0) {
            return "W" + winStreak;
        } else if (lossStreak > 0) {
            return "L" + lossStreak;
        } else if (drawStreak > 0) {
            return "D" + drawStreak;
        } else {
            return "N0";
        }
    }

    /**
     * Recalculate all stats for a season from scratch.
     * Useful for data corrections or initial data load.
     *
     * @param seasonId The season to recalculate
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_ELO_RATINGS, allEntries = true),
        @CacheEvict(value = CacheConfig.CACHE_SEASON_STATS, allEntries = true)
    })
    public void recalculateSeasonStats(String seasonId) {
        log.info("Recalculating all stats for season: {}", seasonId);

        // Delete existing stats for the season
        seasonTeamStatsRepository.deleteBySeasonId(seasonId);

        // Get all completed matches for the season in chronological order
        List<Match> matches = matchRepository.findAllByOrderByMatchDateAsc().stream()
                .filter(m -> seasonId.equals(m.getSeason()))
                .filter(m -> m.getFullTimeResult() != null)
                .toList();

        log.info("Found {} completed matches to process", matches.size());

        // Reset statsProcessed flag for all matches in this season
        matches.forEach(m -> m.setStatsProcessed(false));

        // Process each match
        for (Match match : matches) {
            try {
                processCompletedMatch(match);
            } catch (Exception e) {
                log.error("Failed to process match {}: {}", match.getId(), e.getMessage());
            }
        }

        log.info("Finished recalculating stats for season: {}", seasonId);
    }

    /**
     * Get current Elo rating for a team in a season.
     *
     * @param seasonId The season
     * @param teamName The team name
     * @return The Elo rating, or default if not found
     */
    public double getTeamEloRating(String seasonId, String teamName) {
        return seasonTeamStatsRepository.findBySeasonIdAndTeamNameIgnoreCase(seasonId, teamName)
                .map(SeasonTeamStats::getEloRating)
                .orElse(SeasonTeamStats.DEFAULT_ELO_RATING);
    }
}

