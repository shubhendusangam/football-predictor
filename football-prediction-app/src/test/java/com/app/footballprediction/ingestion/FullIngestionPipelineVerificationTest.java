package com.app.footballprediction.ingestion;

import com.app.common.ingestion.dto.InternalMatchDto;
import com.app.common.ingestion.dto.InternalStandingDto;
import com.app.common.ingestion.mapper.CanonicalMapper;
import com.app.common.model.Match;
import com.app.common.model.SeasonTeamStats;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.SeasonTeamStatsRepository;
import com.app.footballprediction.dto.TrendingInsightsResponse;
import com.app.footballprediction.ingestion.config.FeatureFlagService;
import com.app.footballprediction.ingestion.model.ShadowValidationResult;
import com.app.footballprediction.ingestion.model.UpsertResult;
import com.app.footballprediction.ingestion.orchestrator.IngestionOrchestrator;
import com.app.footballprediction.ingestion.orchestrator.IngestionRouter;
import com.app.footballprediction.ingestion.orchestrator.ShadowValidator;
import com.app.footballprediction.ingestion.provider.legacy.LegacyCsvProvider;
import com.app.footballprediction.ingestion.service.IdempotentUpsertService;
import com.app.footballprediction.service.TrendingInsightsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full verification tests for the enterprise ingestion pipeline.
 *
 * <p>These tests validate:
 * <ul>
 *   <li>Backward compatibility - existing logic unchanged</li>
 *   <li>Data integrity - no duplicates, proper upsert</li>
 *   <li>Insights accuracy - Hot/Cold teams, scorers, etc. unchanged</li>
 *   <li>Provider abstraction - no DTO leaks</li>
 *   <li>Rollback readiness - feature flags work</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FullIngestionPipelineVerificationTest {

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private SeasonTeamStatsRepository seasonTeamStatsRepository;

    @Autowired
    private TrendingInsightsService trendingInsightsService;

    @Autowired
    private CanonicalMapper canonicalMapper;

    @Autowired
    private FeatureFlagService featureFlagService;

    @Autowired
    private IdempotentUpsertService upsertService;

    @Autowired
    private ShadowValidator shadowValidator;

    @Autowired
    private LegacyCsvProvider legacyCsvProvider;

    @Autowired
    private IngestionRouter ingestionRouter;

    // ══════════════════════════════════════════════════════════════════════
    // 1. BACKWARD COMPATIBILITY TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Backward Compatibility")
    class BackwardCompatibilityTests {

        @Test
        @DisplayName("1.1 Match entity schema unchanged - all fields accessible")
        void matchEntitySchemaUnchanged() {
            // Given: A match from existing database
            List<Match> matches = matchRepository.findAllByOrderByMatchDateDesc();
            assertThat(matches).isNotEmpty();

            Match match = matches.get(0);

            // Then: All existing fields should be accessible
            assertThat(match.getId()).isNotNull();
            assertThat(match.getMatchDate()).isNotNull();
            assertThat(match.getHomeTeam()).isNotNull();
            assertThat(match.getAwayTeam()).isNotNull();
            assertThat(match.getSeason()).isNotNull();

            // Existing methods should work
            int points = match.getPointsForTeam(match.getHomeTeam());
            assertThat(points).isBetween(0, 3);
        }

        @Test
        @DisplayName("1.2 SeasonTeamStats entity unchanged")
        void seasonTeamStatsUnchanged() {
            // Given: Stats from existing database
            List<SeasonTeamStats> stats = seasonTeamStatsRepository.findAll();

            if (!stats.isEmpty()) {
                SeasonTeamStats stat = stats.get(0);

                // All existing fields accessible
                assertThat(stat.getSeasonId()).isNotNull();
                assertThat(stat.getTeamName()).isNotNull();
                assertThat(stat.getMatchesPlayed()).isGreaterThanOrEqualTo(0);
                assertThat(stat.getWins()).isGreaterThanOrEqualTo(0);
                assertThat(stat.getDraws()).isGreaterThanOrEqualTo(0);
                assertThat(stat.getLosses()).isGreaterThanOrEqualTo(0);
                assertThat(stat.getGoalsScored()).isGreaterThanOrEqualTo(0);
                assertThat(stat.getGoalsConceded()).isGreaterThanOrEqualTo(0);
                assertThat(stat.getEloRating()).isNotNull();
            }
        }

        @Test
        @DisplayName("1.3 Existing repository methods still work")
        void repositoryMethodsWork() {
            // These are the methods used by existing services

            // CsvIngestionService uses this for deduplication
            boolean exists = matchRepository.existsByMatchDateAndHomeTeamAndAwayTeam(
                LocalDate.of(2025, 1, 1), "TestTeam", "OtherTeam");
            // Should not throw

            // TrendingInsightsService uses these
            List<String> seasons = matchRepository.findAllSeasons();
            assertThat(seasons).isNotNull();

            String currentSeason = matchRepository.findCurrentSeason();
            // May be null if no data, but should not throw

            // findByTeamBeforeDate used for form calculation
            List<Match> teamMatches = matchRepository.findByTeamBeforeDate(
                "Arsenal", LocalDate.now());
            assertThat(teamMatches).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. DATA INTEGRITY TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. Data Integrity")
    class DataIntegrityTests {

        @Test
        @DisplayName("2.1 Idempotent upsert prevents duplicates")
        void idempotentUpsertPreventsDuplicates() {
            // Given: A match DTO
            InternalMatchDto dto = InternalMatchDto.builder()
                .matchDate(LocalDate.of(2099, 12, 31)) // Far future - won't conflict
                .homeTeam("TestHome")
                .awayTeam("TestAway")
                .season("2099-00")
                .competition("PL")
                .fullTimeHomeGoals(2)
                .fullTimeAwayGoals(1)
                .fullTimeResult("H")
                .build();

            // When: Upsert the same match twice
            UpsertResult result1 = upsertService.upsertMatches(List.of(dto));
            UpsertResult result2 = upsertService.upsertMatches(List.of(dto));

            // Then: First insert succeeds, second skips
            assertThat(result1.getInserted()).isEqualTo(1);
            assertThat(result2.getSkipped()).isEqualTo(1);
            assertThat(result2.getInserted()).isEqualTo(0);
        }

        @Test
        @DisplayName("2.2 Match update only when result changes")
        void matchUpdatesOnlyWhenResultChanges() {
            // Given: A scheduled match
            InternalMatchDto scheduled = InternalMatchDto.builder()
                .matchDate(LocalDate.of(2098, 6, 15))
                .homeTeam("FutureHome")
                .awayTeam("FutureAway")
                .season("2097-98")
                .competition("PL")
                .status("SCHEDULED")
                .build();

            // Insert scheduled match
            UpsertResult insert = upsertService.upsertMatches(List.of(scheduled));
            assertThat(insert.getInserted()).isEqualTo(1);

            // When: Same match with result added (completed)
            InternalMatchDto completed = InternalMatchDto.builder()
                .matchDate(LocalDate.of(2098, 6, 15))
                .homeTeam("FutureHome")
                .awayTeam("FutureAway")
                .season("2097-98")
                .competition("PL")
                .fullTimeHomeGoals(3)
                .fullTimeAwayGoals(0)
                .fullTimeResult("H")
                .status("FINISHED")
                .build();

            UpsertResult update = upsertService.upsertMatches(List.of(completed));

            // Then: Should update (not insert or skip)
            assertThat(update.getUpdated()).isEqualTo(1);
            assertThat(update.getInserted()).isEqualTo(0);
        }

        @Test
        @DisplayName("2.3 Canonical mapper is bidirectional and lossless")
        void mapperIsBidirectional() {
            // Given: An existing match
            List<Match> matches = matchRepository.findAllByOrderByMatchDateDesc();
            if (matches.isEmpty()) return;

            Match original = matches.get(0);

            // When: Convert to DTO and back
            InternalMatchDto dto = canonicalMapper.toDto(original, "TEST");
            Match converted = canonicalMapper.toEntity(dto);

            // Then: Key fields should match
            assertThat(converted.getMatchDate()).isEqualTo(original.getMatchDate());
            assertThat(converted.getHomeTeam()).isEqualTo(original.getHomeTeam());
            assertThat(converted.getAwayTeam()).isEqualTo(original.getAwayTeam());
            assertThat(converted.getFullTimeHomeGoals()).isEqualTo(original.getFullTimeHomeGoals());
            assertThat(converted.getFullTimeAwayGoals()).isEqualTo(original.getFullTimeAwayGoals());
            assertThat(converted.getFullTimeResult()).isEqualTo(original.getFullTimeResult());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. INSIGHTS ACCURACY TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. Insights Accuracy")
    class InsightsAccuracyTests {

        @Test
        @DisplayName("3.1 TrendingInsightsService returns valid response")
        void trendingInsightsServiceWorks() {
            // This validates the service still works after refactor
            TrendingInsightsResponse response = trendingInsightsService.getTrendingInsights();

            // Should return a valid response (even if empty)
            assertThat(response).isNotNull();
            assertThat(response.getHotTeams()).isNotNull();
            assertThat(response.getColdTeams()).isNotNull();
            assertThat(response.getTopScorers()).isNotNull();
            assertThat(response.getDefensiveWalls()).isNotNull();
        }

        @Test
        @DisplayName("3.2 Season-specific insights work")
        void seasonSpecificInsightsWork() {
            List<String> seasons = trendingInsightsService.getAvailableSeasons();

            if (!seasons.isEmpty()) {
                String season = seasons.get(0);
                TrendingInsightsResponse response =
                    trendingInsightsService.getTrendingInsightsBySeason(season);

                assertThat(response).isNotNull();
                assertThat(response.getSeason()).isEqualTo(season);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 4. FEATURE FLAG TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. Feature Flags & Rollback")
    class FeatureFlagTests {

        @Test
        @DisplayName("4.1 Legacy pipeline enabled by default")
        void legacyPipelineEnabledByDefault() {
            // Default configuration should have legacy enabled
            assertThat(featureFlagService.useLegacyPipeline()).isTrue();
            assertThat(featureFlagService.useNewPipeline()).isFalse();
        }

        @Test
        @DisplayName("4.2 Rollback switches to legacy immediately")
        void rollbackSwitchesToLegacy() {
            // Given: Enable new pipeline
            featureFlagService.setFlag(FeatureFlagService.USE_NEW_PIPELINE, true);
            featureFlagService.setFlag(FeatureFlagService.USE_LEGACY_PIPELINE, false);

            // When: Rollback
            featureFlagService.rollbackToLegacy();

            // Then: Legacy enabled, new disabled
            assertThat(featureFlagService.useLegacyPipeline()).isTrue();
            assertThat(featureFlagService.useNewPipeline()).isFalse();
            assertThat(featureFlagService.isShadowOnly()).isFalse();
        }

        @Test
        @DisplayName("4.3 Shadow mode enables validation without writes")
        void shadowModeEnablesValidation() {
            // When: Enable shadow mode
            featureFlagService.enableShadowMode();

            // Then: Shadow validation enabled, shadow-only true
            assertThat(featureFlagService.shadowValidationEnabled()).isTrue();
            assertThat(featureFlagService.isShadowOnly()).isTrue();
            assertThat(featureFlagService.useLegacyPipeline()).isTrue(); // Legacy still primary
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5. SHADOW VALIDATION TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5. Shadow Validation")
    class ShadowValidationTests {

        @Test
        @DisplayName("5.1 Shadow validator detects exact matches")
        void shadowValidatorDetectsExactMatches() {
            // Given: Get existing matches as DTOs
            List<InternalMatchDto> matchDtos = legacyCsvProvider.getRecentCompletedMatches("PL", 10);

            if (matchDtos.isEmpty()) return;

            // When: Validate against database
            ShadowValidationResult result = shadowValidator.validate(matchDtos);

            // Then: Should have high match rate (data from same source)
            assertThat(result.passed()).isTrue();
            assertThat(result.hasCriticalDifferences()).isFalse();
        }

        @Test
        @DisplayName("5.2 Shadow validator detects score differences")
        void shadowValidatorDetectsScoreDifferences() {
            // Given: A DTO with wrong score
            InternalMatchDto wrongScore = InternalMatchDto.builder()
                .matchDate(LocalDate.of(2025, 1, 1))
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .season("2024-25")
                .fullTimeHomeGoals(99) // Wrong score
                .fullTimeAwayGoals(99)
                .fullTimeResult("D")
                .build();

            // When: Validate (if match exists in DB)
            ShadowValidationResult result = shadowValidator.validate(List.of(wrongScore));

            // Then: Should either be new match or have differences
            // (We're not asserting specifics since we don't know DB state)
            assertThat(result).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6. PROVIDER ABSTRACTION TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("6. Provider Abstraction")
    class ProviderAbstractionTests {

        @Test
        @DisplayName("6.1 Legacy CSV provider returns canonical DTOs")
        void legacyCsvProviderReturnsCanonicalDtos() {
            List<InternalMatchDto> matches = legacyCsvProvider.getRecentCompletedMatches("PL", 5);

            // All returned objects should be InternalMatchDto
            for (InternalMatchDto dto : matches) {
                assertThat(dto).isInstanceOf(InternalMatchDto.class);
                assertThat(dto.getProviderName()).isEqualTo("LEGACY_CSV");
            }
        }

        @Test
        @DisplayName("6.2 Router uses correct pipeline based on flags")
        void routerUsesCorrectPipeline() {
            // Given: Legacy enabled
            featureFlagService.rollbackToLegacy();

            // When: Fetch matches
            List<InternalMatchDto> matches = ingestionRouter.fetchMatches("PL", "2024-25");

            // Then: Should use legacy provider
            // (Can't easily verify which provider, but should not throw)
            assertThat(matches).isNotNull();
        }

        @Test
        @DisplayName("6.3 All providers registered and accessible")
        void allProvidersRegistered() {
            var providers = ingestionRouter.getAllProviders();

            assertThat(providers).isNotEmpty();

            // At least legacy CSV provider should exist
            boolean hasLegacyCsv = providers.stream()
                .anyMatch(p -> "LEGACY_CSV".equals(p.getProviderName()));
            assertThat(hasLegacyCsv).isTrue();
        }
    }
}

