package com.app.footballprediction.config;

import com.app.common.repository.PredictionEvaluationRepository;
import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Custom Micrometer metrics for Prometheus observability.
 *
 * <p>Registers the following custom metrics:
 * <ul>
 *   <li><b>prediction.requests.total</b> — Counter tagged by outcome (HOME/DRAW/AWAY)</li>
 *   <li><b>prediction.latency</b> — Timer for ML inference duration</li>
 *   <li><b>model.accuracy.current</b> — Gauge showing last-30-day accuracy</li>
 *   <li><b>cache.hits</b> / <b>cache.misses</b> — Counters bridging Caffeine stats to Prometheus</li>
 * </ul>
 */
@Configuration
@Slf4j
public class MetricsConfig {

    // ── Prediction request counter (tagged by outcome) ────────────────────

    @Bean
    public Counter predictionHomeCounter(MeterRegistry registry) {
        return Counter.builder("prediction.requests.total")
                .description("Total prediction requests")
                .tag("outcome", "HOME")
                .register(registry);
    }

    @Bean
    public Counter predictionDrawCounter(MeterRegistry registry) {
        return Counter.builder("prediction.requests.total")
                .description("Total prediction requests")
                .tag("outcome", "DRAW")
                .register(registry);
    }

    @Bean
    public Counter predictionAwayCounter(MeterRegistry registry) {
        return Counter.builder("prediction.requests.total")
                .description("Total prediction requests")
                .tag("outcome", "AWAY")
                .register(registry);
    }

    // ── Prediction latency timer ──────────────────────────────────────────

    @Bean
    public Timer predictionLatencyTimer(MeterRegistry registry) {
        return Timer.builder("prediction.latency")
                .description("ML inference latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);
    }

    // ── Model accuracy gauge (last 30 days) ──────────────────────────────

    @Bean
    public MeterBinder modelAccuracyGauge(PredictionEvaluationRepository evaluationRepository) {
        return registry -> Gauge.builder("model.accuracy.current", evaluationRepository, repo -> {
                    try {
                        LocalDateTime since = LocalDateTime.now().minusDays(30);
                        long total = repo.countEvaluationsSince(since);
                        if (total == 0) return 0.0;
                        long correct = repo.countCorrectWinnerSince(since);
                        return (double) correct / total;
                    } catch (Exception e) {
                        log.debug("Could not compute model accuracy gauge: {}", e.getMessage());
                        return 0.0;
                    }
                })
                .description("Model winner-accuracy over the last 30 days")
                .register(registry);
    }

    // ── Cache hit / miss counters (bridged from Caffeine stats) ──────────

    @Bean
    public MeterBinder caffeineCacheMetrics(CacheManager cacheManager) {
        return registry -> {
            Collection<String> cacheNames = cacheManager.getCacheNames();
            for (String cacheName : cacheNames) {
                org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);
                if (springCache instanceof CaffeineCache caffeineCache) {
                    Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();

                    FunctionCounter.builder("cache.hits", nativeCache,
                                    c -> c.stats().hitCount())
                            .description("Caffeine cache hit count")
                            .tag("cache", cacheName)
                            .register(registry);

                    FunctionCounter.builder("cache.misses", nativeCache,
                                    c -> c.stats().missCount())
                            .description("Caffeine cache miss count")
                            .tag("cache", cacheName)
                            .register(registry);

                    Gauge.builder("cache.size", nativeCache,
                                    Cache::estimatedSize)
                            .description("Caffeine cache estimated size")
                            .tag("cache", cacheName)
                            .register(registry);
                }
            }
        };
    }
}



