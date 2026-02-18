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

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(
                new ConcurrentMapCache(CACHE_STANDINGS),
                new ConcurrentMapCache(CACHE_MATCHES),
                new ConcurrentMapCache(CACHE_NEWS),
                new ConcurrentMapCache(CACHE_PREDICTIONS)
        ));
        return cacheManager;
    }
}

