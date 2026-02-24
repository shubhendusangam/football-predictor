package com.app.footballprediction.polling;

import com.app.footballprediction.polling.controller.SyncStatusController;
import com.app.footballprediction.polling.dto.SystemStatusResponse;
import com.app.footballprediction.polling.model.SyncStatus;
import com.app.footballprediction.polling.service.MatchPollingService;
import com.app.footballprediction.polling.service.SmartRetrainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the System Status API endpoint used by the UI dashboard.
 */
@SpringBootTest
@ActiveProfiles("test")
class SystemStatusApiTest {

    @Autowired
    private SyncStatusController syncStatusController;

    @Autowired
    private MatchPollingService pollingService;

    @Autowired
    private SmartRetrainService retrainService;

    @Nested
    @DisplayName("GET /admin/system-status")
    class SystemStatusEndpoint {

        @Test
        @DisplayName("Should return system status with all required fields")
        void shouldReturnSystemStatusWithAllFields() {
            // When
            ResponseEntity<SystemStatusResponse> response = syncStatusController.getSystemStatus();

            // Then
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isNotNull();

            SystemStatusResponse status = response.getBody();

            // Sync Status fields
            assertThat(status.getSyncStatus()).isIn("SUCCESS", "FAILED", "IN_PROGRESS", "PENDING");
            assertThat(status.getMatchesInsertedToday()).isGreaterThanOrEqualTo(0);
            assertThat(status.getMatchesUpdatedToday()).isGreaterThanOrEqualTo(0);
            assertThat(status.getMatchesFetchedToday()).isGreaterThanOrEqualTo(0);

            // Model Status fields - just check not null
            assertThat(status.getDaysSinceLastMatch()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Should return valid sync status value")
        void shouldReturnValidSyncStatusValue() {
            // When
            ResponseEntity<SystemStatusResponse> response = syncStatusController.getSystemStatus();

            // Then
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSyncStatus()).isIn("SUCCESS", "FAILED", "IN_PROGRESS", "PENDING");
        }
    }

    @Nested
    @DisplayName("GET /admin/sync-status")
    class SyncStatusEndpoint {

        @Test
        @DisplayName("Should return sync status")
        void shouldReturnSyncStatus() {
            // When
            ResponseEntity<SyncStatus> response = syncStatusController.getSyncStatus();

            // Then
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMatchesInsertedToday()).isGreaterThanOrEqualTo(0);
            assertThat(response.getBody().getMatchesUpdatedToday()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("GET /admin/sync-status/detailed")
    class DetailedSyncStatusEndpoint {

        @Test
        @DisplayName("Should return detailed sync status")
        void shouldReturnDetailedSyncStatus() {
            // When
            ResponseEntity<Map<String, Object>> response = syncStatusController.getDetailedSyncStatus();

            // Then
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsKey("matchesInsertedToday");
            assertThat(response.getBody()).containsKey("matchesUpdatedToday");
            assertThat(response.getBody()).containsKey("totalChangesToday");
            assertThat(response.getBody()).containsKey("pollingEnabled");
            assertThat(response.getBody()).containsKey("trainingInProgress");
        }
    }

    @Nested
    @DisplayName("Backward Compatibility")
    class BackwardCompatibility {

        @Test
        @DisplayName("Service layer returns valid sync status")
        void serviceShouldReturnValidSyncStatus() {
            // When
            SyncStatus status = pollingService.getSyncStatus();

            // Then
            assertThat(status).isNotNull();
            assertThat(status.getMatchesInsertedToday()).isGreaterThanOrEqualTo(0);
            assertThat(status.getMatchesUpdatedToday()).isGreaterThanOrEqualTo(0);
            assertThat(status.getNewMatchesSinceLastTraining()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Smart retrain service is accessible")
        void smartRetrainServiceShouldBeAccessible() {
            // Then - should not throw
            assertThat(retrainService.isTrainingInProgress()).isNotNull();
        }
    }
}

