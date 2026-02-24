package com.app.footballprediction.polling.listener;

import com.app.common.ingestion.event.MatchUpdatedEvent;
import com.app.common.ingestion.event.StatsRefreshEvent;
import com.app.common.model.Match;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener that refreshes dashboard and insights when matches are updated.
 *
 * <p>Triggered by:
 * <ul>
 *   <li>MatchUpdatedEvent - Individual match completion</li>
 *   <li>StatsRefreshEvent - Batch update completion</li>
 * </ul>
 *
 * <p>Actions:
 * <ul>
 *   <li>Invalidate relevant caches</li>
 *   <li>Log dashboard refresh triggers</li>
 * </ul>
 *
 * <p><b>Note:</b> This listener does NOT modify any calculation logic.
 * It only invalidates caches so fresh data is fetched on next request.
 * The existing TrendingInsightsService, LeagueStandingService, etc.
 * continue to calculate insights from the database.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardRefreshListener {

    private final CacheManager cacheManager;

    // Cache names from existing CacheConfig
    private static final String CACHE_TRENDING_INSIGHTS = "trendingInsights";
    private static final String CACHE_TEAM_STATS = "teamStats";
    private static final String CACHE_STANDINGS = "standings";
    private static final String CACHE_MATCHES = "matches";
    private static final String CACHE_PREDICTIONS = "predictions";
    private static final String CACHE_SEASON_STATS = "seasonStats";

    /**
     * Handle individual match update.
     * Invalidates caches related to the updated match.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onMatchUpdated(MatchUpdatedEvent event) {
        Match match = event.getMatch();

        log.debug("🔄 Dashboard refresh triggered for match: {} vs {} ({})",
            match.getHomeTeam(), match.getAwayTeam(), event.getUpdateType());

        // Only invalidate on actual result updates
        if (match.getFullTimeResult() == null) {
            log.debug("Match not completed, skipping cache invalidation");
            return;
        }

        String season = match.getSeason();
        String homeTeam = match.getHomeTeam();
        String awayTeam = match.getAwayTeam();

        // Invalidate trending insights for this season
        invalidateSeasonInsights(season);

        // Invalidate team-specific caches
        invalidateTeamCaches(homeTeam);
        invalidateTeamCaches(awayTeam);

        // Invalidate standings
        clearCache(CACHE_STANDINGS);

        log.info("✅ Dashboard caches invalidated for {} vs {} (Season: {})",
            homeTeam, awayTeam, season);
    }

    /**
     * Handle batch stats refresh.
     * Invalidates all affected caches.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onStatsRefresh(StatsRefreshEvent event) {
        log.debug("🔄 Batch stats refresh triggered for {} seasons, {} teams",
            event.getAffectedSeasons().size(), event.getAffectedTeams().size());

        // Invalidate insights for all affected seasons
        for (String season : event.getAffectedSeasons()) {
            invalidateSeasonInsights(season);
        }

        // Invalidate team caches
        for (String team : event.getAffectedTeams()) {
            invalidateTeamCaches(team);
        }

        // Always invalidate global caches
        clearCache(CACHE_STANDINGS);
        evictFromCache(CACHE_TRENDING_INSIGHTS, "all");

        log.info("✅ Batch dashboard refresh complete: {} seasons, {} teams",
            event.getAffectedSeasons().size(), event.getAffectedTeams().size());
    }

    /**
     * Invalidate insights caches for a specific season.
     */
    private void invalidateSeasonInsights(String season) {
        if (season == null) return;

        evictFromCache(CACHE_TRENDING_INSIGHTS, season);
        evictFromCache(CACHE_TRENDING_INSIGHTS, "all");
        evictFromCache(CACHE_SEASON_STATS, season);

        log.trace("Invalidated insights for season: {}", season);
    }

    /**
     * Invalidate caches for a specific team.
     */
    private void invalidateTeamCaches(String teamName) {
        if (teamName == null) return;

        evictFromCache(CACHE_TEAM_STATS, teamName);
        evictFromCache(CACHE_MATCHES, teamName);
        evictFromCache(CACHE_PREDICTIONS, teamName);

        log.trace("Invalidated caches for team: {}", teamName);
    }

    /**
     * Clear an entire cache.
     */
    private void clearCache(String cacheName) {
        try {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.trace("Cleared cache: {}", cacheName);
            }
        } catch (Exception e) {
            log.warn("Failed to clear cache {}: {}", cacheName, e.getMessage());
        }
    }

    /**
     * Evict a specific key from a cache.
     */
    private void evictFromCache(String cacheName, String key) {
        try {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
                log.trace("Evicted {} from cache {}", key, cacheName);
            }
        } catch (Exception e) {
            log.warn("Failed to evict {} from cache {}: {}", key, cacheName, e.getMessage());
        }
    }
}

