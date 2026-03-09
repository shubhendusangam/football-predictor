package com.app.footballprediction.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RateLimitFilter logic.
 */
@DisplayName("RateLimitFilter Unit Tests")
class RateLimitFilterTest {

    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter();
    }

    @Nested
    @DisplayName("getStatistics()")
    class StatisticsTests {

        @Test
        @DisplayName("returns statistics map with expected keys")
        void returnsStatisticsMap() {
            Map<String, Object> stats = rateLimitFilter.getStatistics();
            assertThat(stats).containsKeys("enabled", "activeBuckets", "topBlockedIPs");
        }

        @Test
        @DisplayName("initial active buckets is zero")
        void initialActiveBucketsIsZero() {
            Map<String, Object> stats = rateLimitFilter.getStatistics();
            assertThat(stats.get("activeBuckets")).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("cleanupStaleBuckets()")
    class CleanupTests {

        @Test
        @DisplayName("cleanup does not throw on empty buckets")
        void cleanupOnEmptyBuckets() {
            // Should not throw
            rateLimitFilter.cleanupStaleBuckets();
            assertThat(rateLimitFilter.getStatistics().get("activeBuckets")).isEqualTo(0);
        }
    }
}

