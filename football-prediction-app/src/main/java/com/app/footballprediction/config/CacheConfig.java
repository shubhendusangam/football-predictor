package com.app.footballprediction.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Advanced cache configuration using Caffeine - a high-performance caching library.
 *
 * Features:
 * - Time-based expiration (TTL) per cache
 * - Size-based eviction with LRU policy
 * - Cache statistics for monitoring
 * - Automatic cleanup scheduling
 * - Configurable via application.properties
 */
@Configuration
@EnableCaching
@EnableScheduling
@Slf4j
public class CacheConfig {

    // Cache names
    public static final String CACHE_STANDINGS = "standings";
    public static final String CACHE_MATCHES = "matches";
    public static final String CACHE_NEWS = "news";
    public static final String CACHE_PREDICTIONS = "predictions";
    public static final String CACHE_TEAM_STATS = "teamStats";
    public static final String CACHE_TEAM_FORM = "teamForm";
    public static final String CACHE_TEAM_LOGOS = "teamLogos";
    public static final String CACHE_H2H_INSIGHTS = "h2hInsights";
    public static final String CACHE_TRENDING_INSIGHTS = "trendingInsights";
    public static final String CACHE_API_RESPONSES = "apiResponses";
    public static final String CACHE_SEASONS = "seasons";
    public static final String CACHE_SEASON_STATS = "seasonStats";
    public static final String CACHE_TEAM_ANALYTICS = "teamAnalytics";
    public static final String CACHE_PRE_MATCH_INSIGHTS = "preMatchInsights";
    public static final String CACHE_LEAGUE_STATS = "leagueStats";
    public static final String CACHE_ELO_RATINGS = "eloRatings";
    public static final String CACHE_SHOT_QUALITY = "shotQuality";
    public static final String CACHE_FOULS_ANALYSIS = "foulsAnalysis";
    public static final String CACHE_CORNER_STATS = "cornerStats";
    public static final String CACHE_CORNER_PREDICTION = "cornerPrediction";
    public static final String CACHE_CARDS_PREDICTION = "cardsPrediction";
    public static final String CACHE_TEAM_DISCIPLINE = "teamDiscipline";
    public static final String CACHE_HALF_ANALYSIS = "halfAnalysis";
    public static final String CACHE_EXPECTED_GOALS = "expectedGoals";
    public static final String CACHE_XG_PREDICTION = "xgPrediction";
    public static final String CACHE_KICKOFF_TIME_ANALYSIS = "kickoffTimeAnalysis";
    public static final String CACHE_FIXTURE_CONGESTION = "fixtureCongestion";
    public static final String CACHE_API_SYNC = "apiSync";
    public static final String CACHE_REFEREE_STATS = "refereeStats";
    public static final String CACHE_ALL_REFEREES = "allReferees";
    public static final String CACHE_ALL_REFEREE_STATS = "allRefereeStats";
    public static final String CACHE_TOP4_RACE = "top4Race";
    public static final String CACHE_RELEGATION_BATTLE = "relegationBattle";

    // TTL values from properties (in seconds)
    @Value("${cache.standings.ttl:300}")
    private int standingsTtl;

    @Value("${cache.matches.ttl:300}")
    private int matchesTtl;

    @Value("${cache.news.ttl:900}")
    private int newsTtl;

    @Value("${cache.predictions.ttl:60}")
    private int predictionsTtl;

    @Value("${cache.teamStats.ttl:600}")
    private int teamStatsTtl;

    @Value("${cache.teamLogos.ttl:3600}")
    private int teamLogosTtl;

    @Value("${cache.h2h.ttl:600}")
    private int h2hTtl;

    @Value("${cache.trending.ttl:300}")
    private int trendingTtl;

    @Value("${cache.api.ttl:300}")
    private int apiTtl;

    @Value("${cache.seasons.ttl:3600}")
    private int seasonsTtl;

    @Value("${cache.seasonStats.ttl:1800}")
    private int seasonStatsTtl;

    @Value("${cache.eloRatings.ttl:600}")
    private int eloRatingsTtl;


    @Value("${cache.teamAnalytics.ttl:900}")
    private int teamAnalyticsTtl;

    @Value("${cache.preMatchInsights.ttl:600}")
    private int preMatchInsightsTtl;

    @Value("${cache.leagueStats.ttl:1800}")
    private int leagueStatsTtl;

    @Value("${cache.shotQuality.ttl:900}")
    private int shotQualityTtl;

    @Value("${cache.foulsAnalysis.ttl:900}")
    private int foulsAnalysisTtl;

    @Value("${cache.cornerStats.ttl:900}")
    private int cornerStatsTtl;

    @Value("${cache.cornerPrediction.ttl:600}")
    private int cornerPredictionTtl;

    @Value("${cache.cardsPrediction.ttl:600}")
    private int cardsPredictionTtl;

    @Value("${cache.teamDiscipline.ttl:900}")
    private int teamDisciplineTtl;

    @Value("${cache.halfAnalysis.ttl:900}")
    private int halfAnalysisTtl;

    @Value("${cache.expectedGoals.ttl:900}")
    private int expectedGoalsTtl;

    @Value("${cache.xgPrediction.ttl:600}")
    private int xgPredictionTtl;

    @Value("${cache.kickoffTimeAnalysis.ttl:900}")
    private int kickoffTimeAnalysisTtl;

    @Value("${cache.fixtureCongestion.ttl:600}")
    private int fixtureCongestionTtl;

    @Value("${cache.apiSync.ttl:60}")
    private int apiSyncTtl;

    @Value("${cache.refereeStats.ttl:900}")
    private int refereeStatsTtl;

    @Value("${cache.allReferees.ttl:3600}")
    private int allRefereesTtl;

    @Value("${cache.allRefereeStats.ttl:1800}")
    private int allRefereeStatsTtl;

    @Value("${cache.top4Race.ttl:600}")
    private int top4RaceTtl;

    @Value("${cache.relegationBattle.ttl:600}")
    private int relegationBattleTtl;

    // Max size limits
    @Value("${cache.standings.maxSize:50}")
    private int standingsMaxSize;

    @Value("${cache.matches.maxSize:200}")
    private int matchesMaxSize;

    @Value("${cache.news.maxSize:100}")
    private int newsMaxSize;

    @Value("${cache.predictions.maxSize:500}")
    private int predictionsMaxSize;

    @Value("${cache.teamStats.maxSize:100}")
    private int teamStatsMaxSize;

    @Value("${cache.teamLogos.maxSize:200}")
    private int teamLogosMaxSize;

    @Value("${cache.h2h.maxSize:200}")
    private int h2hMaxSize;

    @Value("${cache.trending.maxSize:50}")
    private int trendingMaxSize;

    @Value("${cache.api.maxSize:100}")
    private int apiMaxSize;

    @Value("${cache.seasons.maxSize:10}")
    private int seasonsMaxSize;

    @Value("${cache.seasonStats.maxSize:100}")
    private int seasonStatsMaxSize;

    @Value("${cache.eloRatings.maxSize:200}")
    private int eloRatingsMaxSize;


    @Value("${cache.teamAnalytics.maxSize:100}")
    private int teamAnalyticsMaxSize;

    @Value("${cache.preMatchInsights.maxSize:200}")
    private int preMatchInsightsMaxSize;

    @Value("${cache.leagueStats.maxSize:50}")
    private int leagueStatsMaxSize;

    @Value("${cache.shotQuality.maxSize:100}")
    private int shotQualityMaxSize;

    @Value("${cache.foulsAnalysis.maxSize:100}")
    private int foulsAnalysisMaxSize;

    @Value("${cache.cornerStats.maxSize:100}")
    private int cornerStatsMaxSize;

    @Value("${cache.halfAnalysis.maxSize:100}")
    private int halfAnalysisMaxSize;

    @Value("${cache.cornerPrediction.maxSize:200}")
    private int cornerPredictionMaxSize;

    @Value("${cache.cardsPrediction.maxSize:200}")
    private int cardsPredictionMaxSize;

    @Value("${cache.teamDiscipline.maxSize:100}")
    private int teamDisciplineMaxSize;

    @Value("${cache.expectedGoals.maxSize:100}")
    private int expectedGoalsMaxSize;

    @Value("${cache.xgPrediction.maxSize:200}")
    private int xgPredictionMaxSize;

    @Value("${cache.kickoffTimeAnalysis.maxSize:100}")
    private int kickoffTimeAnalysisMaxSize;

    @Value("${cache.fixtureCongestion.maxSize:200}")
    private int fixtureCongestionMaxSize;

    @Value("${cache.apiSync.maxSize:10}")
    private int apiSyncMaxSize;

    @Value("${cache.refereeStats.maxSize:100}")
    private int refereeStatsMaxSize;

    @Value("${cache.allReferees.maxSize:10}")
    private int allRefereesMaxSize;

    @Value("${cache.allRefereeStats.maxSize:10}")
    private int allRefereeStatsMaxSize;

    @Value("${cache.top4Race.maxSize:20}")
    private int top4RaceMaxSize;

    @Value("${cache.relegationBattle.maxSize:20}")
    private int relegationBattleMaxSize;

    /**
     * Creates the primary cache manager with customized Caffeine caches.
     * Each cache has specific TTL and size limits based on data characteristics.
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // Configure individual caches with specific settings
        Map<String, Caffeine<Object, Object>> cacheConfigs = new HashMap<>();

        // Standings: moderate TTL, small size (one per competition)
        cacheConfigs.put(CACHE_STANDINGS, buildCache(standingsTtl, standingsMaxSize));

        // Matches: moderate TTL, larger size (multiple match queries)
        cacheConfigs.put(CACHE_MATCHES, buildCache(matchesTtl, matchesMaxSize));

        // News: longer TTL (15 min default), moderate size
        cacheConfigs.put(CACHE_NEWS, buildCache(newsTtl, newsMaxSize));

        // Predictions: short TTL for freshness, larger size
        cacheConfigs.put(CACHE_PREDICTIONS, buildCache(predictionsTtl, predictionsMaxSize));

        // Team stats: longer TTL, moderate size
        cacheConfigs.put(CACHE_TEAM_STATS, buildCache(teamStatsTtl, teamStatsMaxSize));

        // Team form: same TTL as team stats
        cacheConfigs.put(CACHE_TEAM_FORM, buildCache(teamStatsTtl, teamStatsMaxSize));

        // Team logos: long TTL (1 hour), larger size (logos rarely change)
        cacheConfigs.put(CACHE_TEAM_LOGOS, buildCache(teamLogosTtl, teamLogosMaxSize));

        // H2H insights: longer TTL, moderate size
        cacheConfigs.put(CACHE_H2H_INSIGHTS, buildCache(h2hTtl, h2hMaxSize));

        // Trending insights: moderate TTL, small size
        cacheConfigs.put(CACHE_TRENDING_INSIGHTS, buildCache(trendingTtl, trendingMaxSize));

        // API responses: moderate TTL, larger size
        cacheConfigs.put(CACHE_API_RESPONSES, buildCache(apiTtl, apiMaxSize));

        // Seasons: longer TTL (1 hour), small size (seasons rarely change)
        cacheConfigs.put(CACHE_SEASONS, buildCache(seasonsTtl, seasonsMaxSize));

        // Season stats: moderate TTL (30 min), larger size for pagination
        cacheConfigs.put(CACHE_SEASON_STATS, buildCache(seasonStatsTtl, seasonStatsMaxSize));

        // Elo ratings: moderate TTL (10 min), larger size for rankings and team lookups
        cacheConfigs.put(CACHE_ELO_RATINGS, buildCache(eloRatingsTtl, eloRatingsMaxSize));


        // Team analytics: moderate TTL (15 min), larger size for comprehensive team data
        cacheConfigs.put(CACHE_TEAM_ANALYTICS, buildCache(teamAnalyticsTtl, teamAnalyticsMaxSize));

        // Pre-match insights: moderate TTL (10 min), larger size for match combinations
        cacheConfigs.put(CACHE_PRE_MATCH_INSIGHTS, buildCache(preMatchInsightsTtl, preMatchInsightsMaxSize));

        // League stats: longer TTL (30 min), small size (aggregated stats)
        cacheConfigs.put(CACHE_LEAGUE_STATS, buildCache(leagueStatsTtl, leagueStatsMaxSize));

        // Shot quality: moderate TTL (15 min), moderate size for team shot analysis
        cacheConfigs.put(CACHE_SHOT_QUALITY, buildCache(shotQualityTtl, shotQualityMaxSize));

        // Fouls analysis: moderate TTL (15 min), moderate size for team discipline analysis
        cacheConfigs.put(CACHE_FOULS_ANALYSIS, buildCache(foulsAnalysisTtl, foulsAnalysisMaxSize));

        // Corner stats: moderate TTL (15 min), moderate size for team corner analysis
        cacheConfigs.put(CACHE_CORNER_STATS, buildCache(cornerStatsTtl, cornerStatsMaxSize));

        // Corner prediction: moderate TTL (10 min), larger size for match combinations
        cacheConfigs.put(CACHE_CORNER_PREDICTION, buildCache(cornerPredictionTtl, cornerPredictionMaxSize));

        // Cards prediction: moderate TTL (10 min), larger size for match combinations
        cacheConfigs.put(CACHE_CARDS_PREDICTION, buildCache(cardsPredictionTtl, cardsPredictionMaxSize));

        // Team discipline: moderate TTL (15 min), moderate size for team discipline stats
        cacheConfigs.put(CACHE_TEAM_DISCIPLINE, buildCache(teamDisciplineTtl, teamDisciplineMaxSize));

        // Half analysis: moderate TTL (15 min), moderate size for team half analysis
        cacheConfigs.put(CACHE_HALF_ANALYSIS, buildCache(halfAnalysisTtl, halfAnalysisMaxSize));

        // Expected goals (xG): moderate TTL (15 min), moderate size for team xG analysis
        cacheConfigs.put(CACHE_EXPECTED_GOALS, buildCache(expectedGoalsTtl, expectedGoalsMaxSize));

        // xG prediction: moderate TTL (10 min), larger size for match combinations
        cacheConfigs.put(CACHE_XG_PREDICTION, buildCache(xgPredictionTtl, xgPredictionMaxSize));

        // Kickoff time analysis: moderate TTL (15 min), moderate size for team kickoff time patterns
        cacheConfigs.put(CACHE_KICKOFF_TIME_ANALYSIS, buildCache(kickoffTimeAnalysisTtl, kickoffTimeAnalysisMaxSize));

        // Fixture congestion: moderate TTL (10 min), larger size for team+date combinations
        cacheConfigs.put(CACHE_FIXTURE_CONGESTION, buildCache(fixtureCongestionTtl, fixtureCongestionMaxSize));

        // API sync: short TTL (1 min), small size for sync status
        cacheConfigs.put(CACHE_API_SYNC, buildCache(apiSyncTtl, apiSyncMaxSize));

        // Referee stats: moderate TTL (15 min), moderate size for individual referee lookups
        cacheConfigs.put(CACHE_REFEREE_STATS, buildCache(refereeStatsTtl, refereeStatsMaxSize));

        // All referees list: longer TTL (1 hour), small size (single list)
        cacheConfigs.put(CACHE_ALL_REFEREES, buildCache(allRefereesTtl, allRefereesMaxSize));

        // All referee stats: moderate TTL (30 min), small size (single aggregated list)
        cacheConfigs.put(CACHE_ALL_REFEREE_STATS, buildCache(allRefereeStatsTtl, allRefereeStatsMaxSize));

        // Top 4 race analysis: moderate TTL (10 min), small size for season-based analysis
        cacheConfigs.put(CACHE_TOP4_RACE, buildCache(top4RaceTtl, top4RaceMaxSize));

        // Relegation battle analysis: moderate TTL (10 min), small size for season-based analysis
        cacheConfigs.put(CACHE_RELEGATION_BATTLE, buildCache(relegationBattleTtl, relegationBattleMaxSize));

        // Register all cache names
        cacheManager.setCacheNames(cacheConfigs.keySet());

        // Apply custom configurations
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .recordStats()
                .removalListener((key, value, cause) -> {
                    if (cause == RemovalCause.EXPIRED || cause == RemovalCause.SIZE) {
                        log.debug("Cache entry removed - key: {}, cause: {}", key, cause);
                    }
                }));

        // Register individual cache builders
        cacheConfigs.forEach((name, caffeine) -> {
            log.info("Configured cache '{}' with TTL={}s", name, getTtlForCache(name));
        });

        return new CustomCaffeineCacheManager(cacheConfigs);
    }

    /**
     * Builds a Caffeine cache configuration with specific TTL and max size.
     */
    private Caffeine<Object, Object> buildCache(int ttlSeconds, int maxSize) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .maximumSize(maxSize)
                .recordStats()
                .removalListener((key, value, cause) -> {
                    if (log.isDebugEnabled() && cause != RemovalCause.REPLACED) {
                        log.debug("Cache eviction - key: {}, cause: {}", key, cause);
                    }
                });
    }

    /**
     * Get TTL for a specific cache name (used for logging).
     */
    private int getTtlForCache(String cacheName) {
        return switch (cacheName) {
            case CACHE_STANDINGS -> standingsTtl;
            case CACHE_MATCHES -> matchesTtl;
            case CACHE_NEWS -> newsTtl;
            case CACHE_PREDICTIONS -> predictionsTtl;
            case CACHE_TEAM_STATS -> teamStatsTtl;
            case CACHE_TEAM_FORM -> teamStatsTtl;
            case CACHE_H2H_INSIGHTS -> h2hTtl;
            case CACHE_TRENDING_INSIGHTS -> trendingTtl;
            case CACHE_API_RESPONSES -> apiTtl;
            case CACHE_SEASONS -> seasonsTtl;
            case CACHE_SEASON_STATS -> seasonStatsTtl;
            case CACHE_ELO_RATINGS -> eloRatingsTtl;
            case CACHE_TEAM_ANALYTICS -> teamAnalyticsTtl;
            case CACHE_PRE_MATCH_INSIGHTS -> preMatchInsightsTtl;
            case CACHE_LEAGUE_STATS -> leagueStatsTtl;
            case CACHE_SHOT_QUALITY -> shotQualityTtl;
            case CACHE_FOULS_ANALYSIS -> foulsAnalysisTtl;
            case CACHE_CORNER_STATS -> cornerStatsTtl;
            case CACHE_CORNER_PREDICTION -> cornerPredictionTtl;
            case CACHE_CARDS_PREDICTION -> cardsPredictionTtl;
            case CACHE_TEAM_DISCIPLINE -> teamDisciplineTtl;
            case CACHE_HALF_ANALYSIS -> halfAnalysisTtl;
            case CACHE_EXPECTED_GOALS -> expectedGoalsTtl;
            case CACHE_XG_PREDICTION -> xgPredictionTtl;
            case CACHE_KICKOFF_TIME_ANALYSIS -> kickoffTimeAnalysisTtl;
            case CACHE_FIXTURE_CONGESTION -> fixtureCongestionTtl;
            case CACHE_API_SYNC -> apiSyncTtl;
            case CACHE_REFEREE_STATS -> refereeStatsTtl;
            case CACHE_ALL_REFEREES -> allRefereesTtl;
            case CACHE_ALL_REFEREE_STATS -> allRefereeStatsTtl;
            default -> 300;
        };
    }

    /**
     * Scheduled task to log cache statistics every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void logCacheStatistics() {
        if (log.isDebugEnabled()) {
            log.debug("=== Cache Statistics ===");
            // Statistics will be logged by CustomCaffeineCacheManager
        }
    }

    /**
     * Custom CacheManager that supports per-cache Caffeine configurations.
     */
    private static class CustomCaffeineCacheManager extends CaffeineCacheManager {
        private final Map<String, Caffeine<Object, Object>> cacheConfigs;

        public CustomCaffeineCacheManager(Map<String, Caffeine<Object, Object>> cacheConfigs) {
            this.cacheConfigs = cacheConfigs;
            this.setCacheNames(cacheConfigs.keySet());
        }

        @Override
        protected com.github.benmanes.caffeine.cache.Cache<Object, Object> createNativeCaffeineCache(String name) {
            Caffeine<Object, Object> caffeine = cacheConfigs.get(name);
            if (caffeine != null) {
                return caffeine.build();
            }
            // Default cache configuration
            return Caffeine.newBuilder()
                    .expireAfterWrite(5, TimeUnit.MINUTES)
                    .maximumSize(100)
                    .recordStats()
                    .build();
        }
    }
}
