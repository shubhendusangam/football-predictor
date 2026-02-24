package com.app.footballprediction.ingestion.orchestrator;

import com.app.common.ingestion.dto.InternalMatchDto;
import com.app.common.ingestion.provider.MatchDataProvider;
import com.app.footballprediction.ingestion.config.FeatureFlagService;
import com.app.footballprediction.ingestion.service.IngestionMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Routes ingestion requests between legacy and new pipelines.
 * Implements the Strangler Pattern for gradual migration.
 *
 * <p>Routing is controlled by feature flags:
 * <ul>
 *   <li>{@code ingestion.use.legacy.pipeline} - Use existing services</li>
 *   <li>{@code ingestion.use.new.pipeline} - Use new provider abstraction</li>
 * </ul>
 *
 * <p>When both are enabled, can run in parallel comparison mode
 * for shadow validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionRouter {

    private final List<MatchDataProvider> providers;
    private final FeatureFlagService featureFlags;
    private final IngestionMetricsService metricsService;

    /**
     * Fetch matches using the appropriate pipeline based on feature flags.
     *
     * @param competition Competition code (e.g., "PL")
     * @param season Season identifier (e.g., "2025-26")
     * @return Matches in canonical format
     */
    public List<InternalMatchDto> fetchMatches(String competition, String season) {

        // Determine which pipeline to use
        if (featureFlags.useLegacyPipeline()) {
            log.debug("Using LEGACY pipeline for {} {}", competition, season);
            metricsService.recordPipelineUsage("LEGACY");
            return fetchFromLegacyProviders(competition, season);
        }

        if (featureFlags.useNewPipeline()) {
            log.debug("Using NEW pipeline for {} {}", competition, season);
            metricsService.recordPipelineUsage("NEW");
            return fetchFromAllProviders(competition, season);
        }

        // Fallback to legacy if nothing is explicitly enabled
        log.warn("No pipeline explicitly enabled, falling back to LEGACY");
        metricsService.recordPipelineUsage("LEGACY_FALLBACK");
        return fetchFromLegacyProviders(competition, season);
    }

    /**
     * Fetch scheduled (upcoming) matches.
     *
     * @param competition Competition code
     * @return Scheduled matches
     */
    public List<InternalMatchDto> fetchScheduledMatches(String competition) {
        return getAvailableProviders(competition).stream()
            .filter(MatchDataProvider::isAvailable)
            .sorted(Comparator.comparingInt(MatchDataProvider::getPriority))
            .map(p -> {
                try {
                    return p.getScheduledMatches(competition);
                } catch (Exception e) {
                    log.warn("Provider {} failed for scheduled matches: {}",
                        p.getProviderName(), e.getMessage());
                    return Collections.<InternalMatchDto>emptyList();
                }
            })
            .filter(list -> !list.isEmpty())
            .findFirst()
            .orElse(Collections.emptyList());
    }

    /**
     * Fetch recent completed matches.
     *
     * @param competition Competition code
     * @param limit Maximum number of matches
     * @return Recent completed matches
     */
    public List<InternalMatchDto> fetchRecentMatches(String competition, int limit) {
        return getAvailableProviders(competition).stream()
            .filter(MatchDataProvider::isAvailable)
            .sorted(Comparator.comparingInt(MatchDataProvider::getPriority))
            .map(p -> {
                try {
                    return p.getRecentCompletedMatches(competition, limit);
                } catch (Exception e) {
                    log.warn("Provider {} failed for recent matches: {}",
                        p.getProviderName(), e.getMessage());
                    return Collections.<InternalMatchDto>emptyList();
                }
            })
            .filter(list -> !list.isEmpty())
            .findFirst()
            .orElse(Collections.emptyList());
    }

    /**
     * Fetch from legacy providers only.
     * This preserves existing behavior exactly.
     */
    private List<InternalMatchDto> fetchFromLegacyProviders(String competition, String season) {
        // Find legacy providers
        List<MatchDataProvider> legacyProviders = providers.stream()
            .filter(p -> p.getProviderName().startsWith("LEGACY"))
            .filter(p -> p.supportsCompetition(competition))
            .sorted(Comparator.comparingInt(MatchDataProvider::getPriority))
            .collect(Collectors.toList());

        // Try each legacy provider with fallback
        for (MatchDataProvider provider : legacyProviders) {
            try {
                if (!provider.isAvailable()) {
                    log.debug("Provider {} not available, trying next", provider.getProviderName());
                    continue;
                }

                List<InternalMatchDto> matches = provider.getMatchesBySeason(season);

                if (!matches.isEmpty()) {
                    log.info("Fetched {} matches from {} for {} {}",
                        matches.size(), provider.getProviderName(), competition, season);
                    metricsService.recordProviderUsage(provider.getProviderName());
                    return matches;
                }

            } catch (Exception e) {
                log.warn("Provider {} failed, trying next: {}",
                    provider.getProviderName(), e.getMessage());
            }
        }

        log.warn("No legacy providers returned data for {} {}", competition, season);
        return Collections.emptyList();
    }

    /**
     * Fetch from all available providers with priority-based fallback.
     */
    private List<InternalMatchDto> fetchFromAllProviders(String competition, String season) {
        List<MatchDataProvider> availableProviders = getAvailableProviders(competition);

        if (availableProviders.isEmpty()) {
            log.warn("No available providers for competition {}", competition);
            return Collections.emptyList();
        }

        // Try providers in priority order
        for (MatchDataProvider provider : availableProviders) {
            try {
                List<InternalMatchDto> matches = provider.getMatchesBySeason(season);

                if (!matches.isEmpty()) {
                    log.info("Fetched {} matches from {} for {} {}",
                        matches.size(), provider.getProviderName(), competition, season);
                    metricsService.recordProviderUsage(provider.getProviderName());
                    return matches;
                }

            } catch (Exception e) {
                log.warn("Provider {} failed, trying next: {}",
                    provider.getProviderName(), e.getMessage());
            }
        }

        log.warn("All providers failed for {} {}", competition, season);
        return Collections.emptyList();
    }

    /**
     * Get available providers for a competition, sorted by priority.
     */
    private List<MatchDataProvider> getAvailableProviders(String competition) {
        return providers.stream()
            .filter(p -> p.supportsCompetition(competition))
            .filter(MatchDataProvider::isAvailable)
            .sorted(Comparator.comparingInt(MatchDataProvider::getPriority))
            .collect(Collectors.toList());
    }

    /**
     * Get all registered providers.
     */
    public List<MatchDataProvider> getAllProviders() {
        return providers;
    }

    /**
     * Get provider by name.
     */
    public MatchDataProvider getProvider(String name) {
        return providers.stream()
            .filter(p -> p.getProviderName().equals(name))
            .findFirst()
            .orElse(null);
    }
}

