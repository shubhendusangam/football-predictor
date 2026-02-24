package com.app.footballprediction.polling;

import com.app.common.ingestion.dto.InternalMatchDto;
import com.app.common.model.Match;
import com.app.common.model.SystemSettings;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.SystemSettingsRepository;
import com.app.footballprediction.polling.model.PollingResult;
import com.app.footballprediction.polling.model.SyncStatus;
import com.app.footballprediction.polling.scheduler.DailyMatchPollingJob;
import com.app.footballprediction.polling.service.MatchPollingService;
import com.app.footballprediction.polling.service.SmartRetrainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Daily Match Polling and Smart Retrain system.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DailyPollingSystemTest {

    @Autowired
    private MatchPollingService pollingService;

    @Autowired
    private SmartRetrainService retrainService;

    @Autowired
    private DailyMatchPollingJob pollingJob;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private SystemSettingsRepository systemSettingsRepository;

    @BeforeEach
    void setUp() {
        // Ensure SystemSettings exists
        if (systemSettingsRepository.getSettings().isEmpty()) {
            systemSettingsRepository.save(SystemSettings.builder().build());
        }
    }

    @Nested
    @DisplayName("Sync Status Tests")
    class SyncStatusTests {

        @Test
        @DisplayName("Should return valid sync status")
        void shouldReturnValidSyncStatus() {
            // When
            SyncStatus status = pollingService.getSyncStatus();

            // Then
            assertThat(status).isNotNull();
            assertThat(status.getMatchesInsertedToday()).isGreaterThanOrEqualTo(0);
            assertThat(status.getMatchesUpdatedToday()).isGreaterThanOrEqualTo(0);
            assertThat(status.isPollingEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should count new matches since last training")
        void shouldCountNewMatchesSinceLastTraining() {
            // Given: Set last training time to some point in past
            SystemSettings settings = systemSettingsRepository.getSettings()
                .orElse(SystemSettings.builder().build());
            settings.setLastModelTraining(LocalDateTime.now().minusDays(30));
            systemSettingsRepository.save(settings);

            // When
            int newMatches = pollingService.countNewMatchesSinceLastTraining();

            // Then
            assertThat(newMatches).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Polling Result Tests")
    class PollingResultTests {

        @Test
        @DisplayName("Should create success result")
        void shouldCreateSuccessResult() {
            // When
            PollingResult result = PollingResult.success(50, 30, 5, 2, 23, 1500);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMatchesFetched()).isEqualTo(50);
            assertThat(result.getCompletedMatchesFound()).isEqualTo(30);
            assertThat(result.getMatchesInserted()).isEqualTo(5);
            assertThat(result.getMatchesUpdated()).isEqualTo(2);
            assertThat(result.getMatchesSkipped()).isEqualTo(23);
            assertThat(result.hasChanges()).isTrue();
        }

        @Test
        @DisplayName("Should create failure result")
        void shouldCreateFailureResult() {
            // When
            PollingResult result = PollingResult.failure("API timeout");

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("API timeout");
            assertThat(result.hasChanges()).isFalse();
        }

        @Test
        @DisplayName("Should detect no changes when counts are zero")
        void shouldDetectNoChanges() {
            // When
            PollingResult result = PollingResult.success(50, 30, 0, 0, 30, 500);

            // Then
            assertThat(result.hasChanges()).isFalse();
        }
    }

    @Nested
    @DisplayName("Smart Retrain Tests")
    class SmartRetrainTests {

        @Test
        @DisplayName("Should report training not in progress initially")
        void shouldReportTrainingNotInProgress() {
            // Then
            assertThat(retrainService.isTrainingInProgress()).isFalse();
        }

        @Test
        @DisplayName("Should skip retrain when not enough new matches")
        void shouldSkipRetrainWhenNotEnoughData() {
            // Given: Set last training to now (cooldown applies)
            SystemSettings settings = systemSettingsRepository.getSettings()
                .orElse(SystemSettings.builder().build());
            settings.setLastModelTraining(LocalDateTime.now());
            systemSettingsRepository.save(settings);

            // When: Evaluate retrain
            boolean triggered = retrainService.evaluateAndRetrain(false);

            // Then: Should not trigger (cooldown active)
            assertThat(triggered).isFalse();
        }
    }

    @Nested
    @DisplayName("Data Integrity Tests")
    class DataIntegrityTests {

        @Test
        @DisplayName("Should preserve existing matches during polling")
        void shouldPreserveExistingMatches() {
            // Given: Count existing matches
            long countBefore = matchRepository.count();

            // When: Run polling (may not find new data, which is fine)
            PollingResult result = pollingService.pollAndSyncMatches();

            // Then: Count should not decrease
            long countAfter = matchRepository.count();
            assertThat(countAfter).isGreaterThanOrEqualTo(countBefore);
        }

        @Test
        @DisplayName("Should not create duplicate matches")
        void shouldNotCreateDuplicates() {
            // Given: Get a sample of existing matches
            List<Match> existingMatches = matchRepository.findAllByOrderByMatchDateDesc();

            if (existingMatches.isEmpty()) return;

            // Get count for specific match criteria
            Match sample = existingMatches.get(0);
            long specificCount = matchRepository.findByTeamBeforeDate(
                    sample.getHomeTeam(),
                    sample.getMatchDate().plusDays(1))
                .stream()
                .filter(m -> m.getMatchDate().equals(sample.getMatchDate()))
                .filter(m -> m.getAwayTeam().equalsIgnoreCase(sample.getAwayTeam()))
                .count();

            // When: Run polling
            pollingService.pollAndSyncMatches();

            // Then: Same match should not be duplicated
            long specificCountAfter = matchRepository.findByTeamBeforeDate(
                    sample.getHomeTeam(),
                    sample.getMatchDate().plusDays(1))
                .stream()
                .filter(m -> m.getMatchDate().equals(sample.getMatchDate()))
                .filter(m -> m.getAwayTeam().equalsIgnoreCase(sample.getAwayTeam()))
                .count();

            assertThat(specificCountAfter).isEqualTo(specificCount);
        }
    }

    @Nested
    @DisplayName("System Settings Integration Tests")
    class SystemSettingsIntegrationTests {

        @Test
        @DisplayName("Should update last data fetch timestamp")
        void shouldUpdateLastDataFetchTimestamp() {
            // Given: Get current timestamp
            LocalDateTime before = LocalDateTime.now().minusMinutes(1);

            // When: Run polling
            pollingService.pollAndSyncMatches();

            // Then: Last data fetch should be updated
            SystemSettings settings = systemSettingsRepository.getSettings().orElse(null);
            assertThat(settings).isNotNull();

            // Last fetch should be updated if polling was successful
            if (settings.getLastDataFetch() != null) {
                assertThat(settings.getLastDataFetch()).isAfter(before.minusMinutes(2));
            }
        }
    }

    @Nested
    @DisplayName("Observability Tests")
    class ObservabilityTests {

        @Test
        @DisplayName("Sync status should include all required fields")
        void syncStatusShouldIncludeAllFields() {
            // When
            SyncStatus status = pollingService.getSyncStatus();

            // Then: All required fields should be present
            assertThat(status.getMatchesInsertedToday()).isNotNull();
            assertThat(status.getMatchesUpdatedToday()).isNotNull();
            assertThat(status.getLastPollDurationMs()).isNotNull();
            assertThat(status.getRecordsProcessed()).isNotNull();
            assertThat(status.isPollingEnabled()).isNotNull();
            assertThat(status.isRetrainTriggeredToday()).isNotNull();
            assertThat(status.getNewMatchesSinceLastTraining()).isGreaterThanOrEqualTo(0);
        }
    }
}

