package com.app.footballprediction.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Cache configuration for the application.
 * Uses in-memory caching for API responses.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_STANDINGS = "standings";
    public static final String CACHE_MATCHES = "matches";
    public static final String CACHE_NEWS = "news";
    public static final String CACHE_PREDICTIONS = "predictions";
    public static final String CACHE_TEAM_STATS = "teamStats";
    public static final String CACHE_H2H_INSIGHTS = "h2hInsights";
    public static final String CACHE_TRENDING_INSIGHTS = "trendingInsights";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                new ConcurrentMapCache(CACHE_STANDINGS),
                new ConcurrentMapCache(CACHE_MATCHES),
                new ConcurrentMapCache(CACHE_NEWS),
                new ConcurrentMapCache(CACHE_PREDICTIONS),
                new ConcurrentMapCache(CACHE_TEAM_STATS),
                new ConcurrentMapCache(CACHE_H2H_INSIGHTS),
                new ConcurrentMapCache(CACHE_TRENDING_INSIGHTS)
        ));
        return cacheManager;
    }
}

