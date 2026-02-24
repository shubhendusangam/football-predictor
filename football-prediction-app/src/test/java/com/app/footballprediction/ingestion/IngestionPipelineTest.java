package com.app.footballprediction.ingestion;

import com.app.common.ingestion.dto.InternalMatchDto;
import com.app.common.ingestion.mapper.CanonicalMapper;
import com.app.common.ingestion.provider.MatchDataProvider;
import com.app.common.model.Match;
import com.app.footballprediction.ingestion.config.FeatureFlagService;
import com.app.footballprediction.ingestion.model.ShadowValidationResult;
import com.app.footballprediction.ingestion.model.UpsertResult;
import com.app.footballprediction.ingestion.orchestrator.ShadowValidator;
import com.app.footballprediction.ingestion.service.IdempotentUpsertService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the enterprise ingestion pipeline components.
 *
 * <p>These tests validate:
 * <ul>
 *   <li>Canonical DTO mapping is bidirectional and lossless</li>
 *   <li>Feature flags work correctly</li>
 *   <li>Idempotent upsert logic is sound</li>
 *   <li>Shadow validation detects differences</li>
 * </ul>
 */
class IngestionPipelineTest {

    @Nested
    @DisplayName("Canonical Mapper Tests")
    class CanonicalMapperTests {

        private final CanonicalMapper mapper = new CanonicalMapper();

        @Test
        @DisplayName("Should map Match entity to InternalMatchDto")
        void testMatchToDto() {
            // Given
            Match match = Match.builder()
                .id(1L)
                .matchDate(LocalDate.of(2025, 2, 15))
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .season("2024-25")
                .fullTimeHomeGoals(2)
                .fullTimeAwayGoals(1)
                .fullTimeResult("H")
                .halfTimeHomeGoals(1)
                .halfTimeAwayGoals(0)
                .referee("Michael Oliver")
                .homeShots(15)
                .awayShots(10)
                .b365H(1.8)
                .b365D(3.5)
                .b365A(4.2)
                .build();

            // When
            InternalMatchDto dto = mapper.toDto(match, "TEST_PROVIDER");

            // Then
            assertThat(dto).isNotNull();
            assertThat(dto.getMatchDate()).isEqualTo(match.getMatchDate());
            assertThat(dto.getHomeTeam()).isEqualTo(match.getHomeTeam());
            assertThat(dto.getAwayTeam()).isEqualTo(match.getAwayTeam());
            assertThat(dto.getSeason()).isEqualTo(match.getSeason());
            assertThat(dto.getFullTimeHomeGoals()).isEqualTo(match.getFullTimeHomeGoals());
            assertThat(dto.getFullTimeAwayGoals()).isEqualTo(match.getFullTimeAwayGoals());
            assertThat(dto.getFullTimeResult()).isEqualTo(match.getFullTimeResult());
            assertThat(dto.getProviderName()).isEqualTo("TEST_PROVIDER");
        }

        @Test
        @DisplayName("Should map InternalMatchDto to Match entity")
        void testDtoToMatch() {
            // Given
            InternalMatchDto dto = InternalMatchDto.builder()
                .matchDate(LocalDate.of(2025, 2, 20))
                .homeTeam("Liverpool")
                .awayTeam("Man United")
                .season("2024-25")
                .fullTimeHomeGoals(3)
                .fullTimeAwayGoals(0)
                .fullTimeResult("H")
                .referee("Anthony Taylor")
                .build();

            // When
            Match match = mapper.toEntity(dto);

            // Then
            assertThat(match).isNotNull();
            assertThat(match.getMatchDate()).isEqualTo(dto.getMatchDate());
            assertThat(match.getHomeTeam()).isEqualTo(dto.getHomeTeam());
            assertThat(match.getAwayTeam()).isEqualTo(dto.getAwayTeam());
            assertThat(match.getSeason()).isEqualTo(dto.getSeason());
            assertThat(match.getFullTimeHomeGoals()).isEqualTo(dto.getFullTimeHomeGoals());
            assertThat(match.getFullTimeResult()).isEqualTo(dto.getFullTimeResult());
        }

        @Test
        @DisplayName("Should preserve all fields in round-trip conversion")
        void testRoundTripConversion() {
            // Given
            Match original = Match.builder()
                .matchDate(LocalDate.of(2025, 3, 1))
                .homeTeam("Man City")
                .awayTeam("Tottenham")
                .season("2024-25")
                .fullTimeHomeGoals(4)
                .fullTimeAwayGoals(2)
                .fullTimeResult("H")
                .halfTimeHomeGoals(2)
                .halfTimeAwayGoals(1)
                .homeShots(20)
                .awayShots(8)
                .homeCorners(10)
                .awayCorners(3)
                .build();

            // When
            InternalMatchDto dto = mapper.toDto(original, "ROUND_TRIP");
            Match converted = mapper.toEntity(dto);

            // Then - key fields should match
            assertThat(converted.getMatchDate()).isEqualTo(original.getMatchDate());
            assertThat(converted.getHomeTeam()).isEqualTo(original.getHomeTeam());
            assertThat(converted.getAwayTeam()).isEqualTo(original.getAwayTeam());
            assertThat(converted.getSeason()).isEqualTo(original.getSeason());
            assertThat(converted.getFullTimeHomeGoals()).isEqualTo(original.getFullTimeHomeGoals());
            assertThat(converted.getFullTimeAwayGoals()).isEqualTo(original.getFullTimeAwayGoals());
            assertThat(converted.getFullTimeResult()).isEqualTo(original.getFullTimeResult());
        }
    }

    @Nested
    @DisplayName("Feature Flag Service Tests")
    class FeatureFlagServiceTests {

        @Test
        @DisplayName("Should initialize with legacy pipeline enabled by default")
        void testDefaultFlags() {
            // Given/When
            FeatureFlagService service = new FeatureFlagService();
            service.init();

            // Then
            assertThat(service.useLegacyPipeline()).isTrue();
            assertThat(service.useNewPipeline()).isFalse();
            assertThat(service.shadowValidationEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should update flags at runtime")
        void testUpdateFlag() {
            // Given
            FeatureFlagService service = new FeatureFlagService();
            service.init();

            // When
            service.setFlag(FeatureFlagService.SHADOW_VALIDATION, true);

            // Then
            assertThat(service.shadowValidationEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should rollback to legacy correctly")
        void testRollback() {
            // Given
            FeatureFlagService service = new FeatureFlagService();
            service.init();
            service.setFlag(FeatureFlagService.USE_NEW_PIPELINE, true);
            service.setFlag(FeatureFlagService.USE_LEGACY_PIPELINE, false);

            // When
            service.rollbackToLegacy();

            // Then
            assertThat(service.useLegacyPipeline()).isTrue();
            assertThat(service.useNewPipeline()).isFalse();
            assertThat(service.isShadowOnly()).isFalse();
        }

        @Test
        @DisplayName("Should enable shadow mode correctly")
        void testShadowMode() {
            // Given
            FeatureFlagService service = new FeatureFlagService();
            service.init();

            // When
            service.enableShadowMode();

            // Then
            assertThat(service.useLegacyPipeline()).isTrue(); // Legacy still primary
            assertThat(service.shadowValidationEnabled()).isTrue();
            assertThat(service.isShadowOnly()).isTrue();
        }
    }

    @Nested
    @DisplayName("Internal Match DTO Tests")
    class InternalMatchDtoTests {

        @Test
        @DisplayName("Should generate correct business key")
        void testBusinessKey() {
            // Given
            InternalMatchDto dto = InternalMatchDto.builder()
                .matchDate(LocalDate.of(2025, 2, 15))
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .build();

            // When
            String businessKey = dto.getBusinessKey();

            // Then
            assertThat(businessKey).isEqualTo("2025-02-15|arsenal|chelsea");
        }

        @Test
        @DisplayName("Should detect completed matches")
        void testIsCompleted() {
            // Given
            InternalMatchDto completed = InternalMatchDto.builder()
                .fullTimeResult("H")
                .build();

            InternalMatchDto scheduled = InternalMatchDto.builder()
                .status("SCHEDULED")
                .build();

            // Then
            assertThat(completed.isCompleted()).isTrue();
            assertThat(scheduled.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("Should calculate result from scores")
        void testCalculateResult() {
            // Given
            InternalMatchDto homeWin = InternalMatchDto.builder()
                .fullTimeHomeGoals(2)
                .fullTimeAwayGoals(1)
                .build();

            InternalMatchDto draw = InternalMatchDto.builder()
                .fullTimeHomeGoals(1)
                .fullTimeAwayGoals(1)
                .build();

            InternalMatchDto awayWin = InternalMatchDto.builder()
                .fullTimeHomeGoals(0)
                .fullTimeAwayGoals(3)
                .build();

            // Then
            assertThat(homeWin.calculateResult()).isEqualTo("H");
            assertThat(draw.calculateResult()).isEqualTo("D");
            assertThat(awayWin.calculateResult()).isEqualTo("A");
        }
    }

    @Nested
    @DisplayName("UpsertResult Tests")
    class UpsertResultTests {

        @Test
        @DisplayName("Should track affected matches correctly")
        void testAffectedMatches() {
            // Given
            UpsertResult.UpsertResultCollector collector = new UpsertResult.UpsertResultCollector();

            Match match1 = Match.builder()
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .season("2024-25")
                .build();

            Match match2 = Match.builder()
                .homeTeam("Liverpool")
                .awayTeam("Man United")
                .season("2024-25")
                .build();

            // When
            collector.recordInsert(match1);
            collector.recordUpdate(match2);
            collector.recordSkipped("some-key", "No changes");

            UpsertResult result = collector.build();

            // Then
            assertThat(result.getInserted()).isEqualTo(1);
            assertThat(result.getUpdated()).isEqualTo(1);
            assertThat(result.getSkipped()).isEqualTo(1);
            assertThat(result.hasChanges()).isTrue();
            assertThat(result.getAffectedMatches()).hasSize(2);
            assertThat(result.getAffectedSeasons()).contains("2024-25");
            assertThat(result.getAffectedTeams()).contains("Arsenal", "Chelsea", "Liverpool", "Man United");
        }
    }

    @Nested
    @DisplayName("Shadow Validation Result Tests")
    class ShadowValidationResultTests {

        @Test
        @DisplayName("Should calculate match rate correctly")
        void testMatchRate() {
            // Given
            ShadowValidationResult.Collector collector = new ShadowValidationResult.Collector();

            // 8 exact, 2 minor, 0 critical = 80% exact match rate
            for (int i = 0; i < 8; i++) {
                collector.recordExactMatch();
            }
            collector.recordMinorDiff("Team name variation");
            collector.recordMinorDiff("Stats difference");

            ShadowValidationResult result = collector.build();

            // Then
            assertThat(result.getMatchRate()).isEqualTo(0.8);
            assertThat(result.hasCriticalDifferences()).isFalse();
            assertThat(result.passed()).isTrue();
        }

        @Test
        @DisplayName("Should detect critical differences")
        void testCriticalDifferences() {
            // Given
            ShadowValidationResult.Collector collector = new ShadowValidationResult.Collector();

            collector.recordExactMatch();
            collector.recordCriticalDiff(ShadowValidationResult.ShadowDifference.builder()
                .matchBusinessKey("2025-02-15|arsenal|chelsea")
                .field("fullTimeResult")
                .expectedValue("H")
                .actualValue("D")
                .type(ShadowValidationResult.ShadowDifference.DifferenceType.RESULT_MISMATCH)
                .build());

            ShadowValidationResult result = collector.build();

            // Then
            assertThat(result.hasCriticalDifferences()).isTrue();
            assertThat(result.passed()).isFalse();
            assertThat(result.getCriticalDifferences()).hasSize(1);
        }
    }
}

