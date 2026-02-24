package com.app.common.ingestion.event;

import org.springframework.context.ApplicationEvent;

import java.util.Collections;
import java.util.Set;

/**
 * Domain event published when caches need to be invalidated.
 *
 * <p>This event is published AFTER successful database commit
 * to ensure cache invalidation doesn't happen on rollback.
 *
 * <p>Listeners should use EXISTING cache names and configuration.
 * No new cache names should be introduced through this event.
 */
public class CacheInvalidationEvent extends ApplicationEvent {

    private final Set<String> cacheNames;
    private final Set<String> cacheKeys;
    private final InvalidationType type;

    /**
     * Create a cache invalidation event for specific caches.
     *
     * @param source Event source
     * @param cacheNames Names of caches to invalidate
     */
    public CacheInvalidationEvent(Object source, Set<String> cacheNames) {
        this(source, cacheNames, Collections.emptySet(), InvalidationType.FULL);
    }

    /**
     * Create a cache invalidation event with specific keys.
     *
     * @param source Event source
     * @param cacheNames Cache names
     * @param cacheKeys Specific keys to invalidate (for selective invalidation)
     * @param type Type of invalidation
     */
    public CacheInvalidationEvent(Object source, Set<String> cacheNames,
                                  Set<String> cacheKeys, InvalidationType type) {
        super(source);
        this.cacheNames = cacheNames;
        this.cacheKeys = cacheKeys;
        this.type = type;
    }

    /**
     * Get cache names to invalidate.
     */
    public Set<String> getCacheNames() {
        return cacheNames;
    }

    /**
     * Get specific cache keys to invalidate.
     * Empty if full cache invalidation.
     */
    public Set<String> getCacheKeys() {
        return cacheKeys;
    }

    /**
     * Get invalidation type.
     */
    public InvalidationType getType() {
        return type;
    }

    /**
     * Check if this is a full cache clear.
     */
    public boolean isFullInvalidation() {
        return type == InvalidationType.FULL;
    }

    /**
     * Type of cache invalidation.
     */
    public enum InvalidationType {
        /** Clear entire cache */
        FULL,

        /** Invalidate specific keys only */
        SELECTIVE,

        /** Invalidate keys matching pattern */
        PATTERN
    }

    /**
     * Create an event for invalidating all match-related caches.
     */
    public static CacheInvalidationEvent forMatchUpdate(Object source, String season) {
        Set<String> caches = Set.of(
            "trendingInsights",
            "teamStats",
            "matches",
            "predictions",
            "h2hInsights"
        );
        Set<String> keys = Set.of(season, "all");
        return new CacheInvalidationEvent(source, caches, keys, InvalidationType.SELECTIVE);
    }

    /**
     * Create an event for invalidating standings caches.
     */
    public static CacheInvalidationEvent forStandingsUpdate(Object source) {
        return new CacheInvalidationEvent(source, Set.of("standings"));
    }
}

