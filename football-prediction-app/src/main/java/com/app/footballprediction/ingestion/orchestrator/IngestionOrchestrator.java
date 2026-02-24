package com.app.footballprediction.ingestion.orchestrator;

import com.app.common.ingestion.dto.InternalMatchDto;
import com.app.common.ingestion.event.CacheInvalidationEvent;
import com.app.common.ingestion.event.IngestionCompletedEvent;
import com.app.common.ingestion.event.MatchUpdatedEvent;
import com.app.common.ingestion.event.StatsRefreshEvent;
import com.app.common.model.Match;
import com.app.footballprediction.ingestion.config.FeatureFlagService;
import com.app.footballprediction.ingestion.model.IngestionResult;
import com.app.footballprediction.ingestion.model.IngestionResult.IngestionStatus;
import com.app.footballprediction.ingestion.model.ShadowValidationResult;
import com.app.footballprediction.ingestion.model.UpsertResult;
import com.app.footballprediction.ingestion.service.IdempotentUpsertService;
import com.app.footballprediction.ingestion.service.IngestionMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Main orchestrator for the enterprise ingestion pipeline.
 *
 * <p>Coordinates between:
 * <ul>
 *   <li>Ingestion Router - fetches data from providers</li>
 *   <li>Shadow Validator - validates new pipeline output</li>
 *   <li>Idempotent Upsert Service - safely persists data</li>
 *   <li>Event Publisher - triggers downstream updates</li>
 *   <li>Metrics Service - records observability data</li>
 * </ul>
 *
 * <p>Respects feature flags for:
 * <ul>
 *   <li>Pipeline selection (legacy vs new)</li>
 *   <li>Shadow validation mode</li>
 *   <li>Event publishing</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionOrchestrator {

    private final IngestionRouter router;
    private final ShadowValidator shadowValidator;
    private final IdempotentUpsertService upsertService;
    private final FeatureFlagService featureFlags;
    private final IngestionMetricsService metricsService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Execute ingestion for a competition and season.
     *
     * @param competition Competition code (e.g., "PL")
     * @param season Season identifier (e.g., "2025-26")
     * @return Detailed ingestion result
     */
    @Transactional
    public IngestionResult ingest(String competition, String season) {
        long startTime = System.currentTimeMillis();

        log.info("Starting ingestion for {} {}", competition, season);

        IngestionResult.IngestionResultBuilder resultBuilder = IngestionResult.defaultBuilder()
            .competition(competition)
            .season(season)
            .pipeline(featureFlags.useLegacyPipeline() ? "LEGACY" : "NEW");

        try {
            // Step 1: Fetch data from providers
            List<InternalMatchDto> matches = router.fetchMatches(competition, season);
            resultBuilder.totalFetched(matches.size());

            if (matches.isEmpty()) {
                log.info("No matches returned for {} {}", competition, season);
                return resultBuilder
                    .status(IngestionStatus.NO_DATA)
                    .completedAt(Instant.now())
                    .build();
            }

            log.info("Fetched {} matches for {} {}", matches.size(), competition, season);

            // Step 2: Shadow validation (if enabled)
            if (featureFlags.shadowValidationEnabled()) {
                ShadowValidationResult shadowResult = shadowValidator.validate(matches);
                resultBuilder.shadowValidation(shadowResult);

                if (shadowResult.hasCriticalDifferences()) {
                    log.warn("Shadow validation found {} critical differences - aborting ingestion",
                        shadowResult.getCriticalDifferences().size());
                    metricsService.recordShadowValidationFailure(competition);

                    return resultBuilder
                        .status(IngestionStatus.SHADOW_VALIDATION_FAILED)
                        .completedAt(Instant.now())
                        .build();
                }

                log.info("Shadow validation passed: {}", shadowResult.getSummary());
            }

            // Step 3: Check if we should write (not shadow-only mode)
            if (featureFlags.isShadowOnly()) {
                log.info("Shadow-only mode enabled - skipping database writes");
                return resultBuilder
                    .status(IngestionStatus.SUCCESS)
                    .completedAt(Instant.now())
                    .build();
            }

            // Step 4: Idempotent upsert
            UpsertResult upsertResult = upsertService.upsertMatches(matches);

            resultBuilder
                .inserted(upsertResult.getInserted())
                .updated(upsertResult.getUpdated())
                .skipped(upsertResult.getSkipped())
                .affectedSeasons(upsertResult.getAffectedSeasons())
                .affectedTeams(upsertResult.getAffectedTeams());

            // Record errors
            upsertResult.getErrors().forEach(err ->
                resultBuilder.errors(List.of(err.getErrorMessage())));

            // Step 5: Publish events (if enabled and changes made)
            if (featureFlags.eventsEnabled() && upsertResult.hasChanges()) {
                publishEvents(upsertResult, competition, season);
            }

            // Determine final status
            IngestionStatus status = upsertResult.getErrors().isEmpty()
                ? IngestionStatus.SUCCESS
                : IngestionStatus.PARTIAL_SUCCESS;

            long duration = System.currentTimeMillis() - startTime;

            // Record metrics
            metricsService.recordIngestionSuccess(
                competition,
                duration,
                matches.size(),
                upsertResult.getInserted(),
                upsertResult.getUpdated()
            );

            log.info("Ingestion completed for {} {}: {} inserted, {} updated, {} skipped in {}ms",
                competition, season,
                upsertResult.getInserted(),
                upsertResult.getUpdated(),
                upsertResult.getSkipped(),
                duration);

            return resultBuilder
                .status(status)
                .completedAt(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Ingestion failed for {} {}: {}", competition, season, e.getMessage(), e);
            metricsService.recordIngestionFailure(competition, e);

            return resultBuilder
                .status(IngestionStatus.FAILED)
                .errors(List.of(e.getMessage()))
                .completedAt(Instant.now())
                .build();
        }
    }

    /**
     * Ingest recent completed matches.
     * Useful for updating match results after games are played.
     */
    @Transactional
    public IngestionResult ingestRecentMatches(String competition, int limit) {
        log.info("Ingesting {} recent matches for {}", limit, competition);

        List<InternalMatchDto> matches = router.fetchRecentMatches(competition, limit);

        if (matches.isEmpty()) {
            return IngestionResult.builder()
                .competition(competition)
                .status(IngestionStatus.NO_DATA)
                .totalFetched(0)
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .build();
        }

        // Determine season from first match
        String season = matches.get(0).getSeason();
        if (season == null) {
            season = "current";
        }

        return ingestMatches(competition, season, matches);
    }

    /**
     * Ingest scheduled (upcoming) matches.
     * Useful for predictions and fixture displays.
     */
    @Transactional
    public IngestionResult ingestScheduledMatches(String competition) {
        log.info("Ingesting scheduled matches for {}", competition);

        List<InternalMatchDto> matches = router.fetchScheduledMatches(competition);

        if (matches.isEmpty()) {
            return IngestionResult.builder()
                .competition(competition)
                .status(IngestionStatus.NO_DATA)
                .totalFetched(0)
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .build();
        }

        return ingestMatches(competition, "upcoming", matches);
    }

    /**
     * Internal method to ingest a list of matches.
     */
    private IngestionResult ingestMatches(String competition, String season,
                                          List<InternalMatchDto> matches) {
        long startTime = System.currentTimeMillis();

        IngestionResult.IngestionResultBuilder resultBuilder = IngestionResult.defaultBuilder()
            .competition(competition)
            .season(season)
            .totalFetched(matches.size());

        try {
            // Shadow validation
            if (featureFlags.shadowValidationEnabled()) {
                ShadowValidationResult shadowResult = shadowValidator.validate(matches);
                resultBuilder.shadowValidation(shadowResult);

                if (shadowResult.hasCriticalDifferences()) {
                    return resultBuilder
                        .status(IngestionStatus.SHADOW_VALIDATION_FAILED)
                        .completedAt(Instant.now())
                        .build();
                }
            }

            // Skip writes in shadow-only mode
            if (featureFlags.isShadowOnly()) {
                return resultBuilder
                    .status(IngestionStatus.SUCCESS)
                    .completedAt(Instant.now())
                    .build();
            }

            // Upsert
            UpsertResult upsertResult = upsertService.upsertMatches(matches);

            resultBuilder
                .inserted(upsertResult.getInserted())
                .updated(upsertResult.getUpdated())
                .skipped(upsertResult.getSkipped())
                .affectedSeasons(upsertResult.getAffectedSeasons());

            // Publish events
            if (featureFlags.eventsEnabled() && upsertResult.hasChanges()) {
                publishEvents(upsertResult, competition, season);
            }

            metricsService.recordIngestionSuccess(
                competition,
                System.currentTimeMillis() - startTime,
                matches.size(),
                upsertResult.getInserted(),
                upsertResult.getUpdated()
            );

            return resultBuilder
                .status(IngestionStatus.SUCCESS)
                .completedAt(Instant.now())
                .build();

        } catch (Exception e) {
            log.error("Ingestion failed: {}", e.getMessage(), e);
            metricsService.recordIngestionFailure(competition, e);

            return resultBuilder
                .status(IngestionStatus.FAILED)
                .errors(List.of(e.getMessage()))
                .completedAt(Instant.now())
                .build();
        }
    }

    /**
     * Publish domain events for downstream processing.
     * These events trigger existing update logic via listeners.
     */
    private void publishEvents(UpsertResult upsertResult, String competition, String season) {
        log.debug("Publishing events for {} affected matches",
            upsertResult.getAffectedMatches().size());

        // Publish individual match events
        for (Match match : upsertResult.getAffectedMatches()) {
            MatchUpdatedEvent.UpdateType updateType = upsertResult.getInsertedMatches().contains(match)
                ? MatchUpdatedEvent.UpdateType.INSERTED
                : MatchUpdatedEvent.UpdateType.RESULT_UPDATED;

            eventPublisher.publishEvent(new MatchUpdatedEvent(this, match, updateType));
        }

        // Publish stats refresh event (batched)
        if (!upsertResult.getAffectedSeasons().isEmpty()) {
            eventPublisher.publishEvent(new StatsRefreshEvent(
                this,
                upsertResult.getAffectedSeasons(),
                upsertResult.getAffectedTeams(),
                StatsRefreshEvent.RefreshScope.PARTIAL
            ));
        }

        // Publish cache invalidation event
        Set<String> cacheNames = Set.of(
            "trendingInsights",
            "teamStats",
            "matches",
            "predictions"
        );
        eventPublisher.publishEvent(new CacheInvalidationEvent(
            this,
            cacheNames,
            upsertResult.getAffectedCacheKeys(),
            CacheInvalidationEvent.InvalidationType.SELECTIVE
        ));

        // Publish completion event
        eventPublisher.publishEvent(new IngestionCompletedEvent(
            this,
            competition,
            season,
            upsertResult.getInserted() + upsertResult.getUpdated() + upsertResult.getSkipped(),
            upsertResult.getInserted(),
            upsertResult.getUpdated(),
            upsertResult.getSkipped(),
            new HashSet<>(),
            0,
            IngestionCompletedEvent.IngestionStatus.SUCCESS
        ));
    }
}

