package com.app.footballprediction.service;

import com.app.common.model.SyncStatusEntry;
import com.app.common.model.SyncStatusEntry.SyncType;
import com.app.common.repository.SyncStatusEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DataSyncService — the audited sync orchestrator.
 */
@ExtendWith(MockitoExtension.class)
class DataSyncServiceTest {

    @Mock
    private ApiDataSyncService apiDataSyncService;

    @Mock
    private SyncStatusEntryRepository syncStatusRepo;

    @InjectMocks
    private DataSyncService dataSyncService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dataSyncService, "defaultCompetition", "PL");
        ReflectionTestUtils.setField(dataSyncService, "syncEnabled", true);

        // Make save() return the entity passed to it (simulating auto-ID) — lenient since not all tests trigger saves
        lenient().when(syncStatusRepo.save(any(SyncStatusEntry.class)))
                .thenAnswer(invocation -> {
                    SyncStatusEntry e = invocation.getArgument(0);
                    if (e.getId() == null) e.setId(1L);
                    return e;
                });
    }

    @Test
    @DisplayName("triggerFullSync should record success when apiDataSyncService.syncAll succeeds")
    void triggerFullSync_Success() {
        doNothing().when(apiDataSyncService).syncAll("PL");

        SyncStatusEntry result = dataSyncService.triggerFullSync("admin-user");

        assertNotNull(result);
        assertTrue(result.getSuccess());
        assertEquals(SyncType.FULL, result.getSyncType());
        assertEquals("PL", result.getCompetition());
        assertEquals("admin-user", result.getTriggeredBy());
        assertNotNull(result.getFinishedAt());
        assertNotNull(result.getDurationMs());

        // save called twice: once at start, once at end
        verify(syncStatusRepo, times(2)).save(any(SyncStatusEntry.class));
    }

    @Test
    @DisplayName("triggerSync FIXTURES should record inserted count")
    void triggerSync_Fixtures() {
        when(apiDataSyncService.syncScheduledMatches("PL")).thenReturn(5);

        SyncStatusEntry result = dataSyncService.triggerSync(SyncType.FIXTURES, "PL", "scheduler");

        assertTrue(result.getSuccess());
        assertEquals(SyncType.FIXTURES, result.getSyncType());
        assertEquals(5, result.getRecordsInserted());
        assertEquals(5, result.getRecordsFetched());
    }

    @Test
    @DisplayName("triggerSync RESULTS should record new and updated counts")
    void triggerSync_Results() {
        when(apiDataSyncService.syncFinishedMatches("PL")).thenReturn(new int[]{3, 2});

        SyncStatusEntry result = dataSyncService.triggerSync(SyncType.RESULTS, "PL", "scheduler");

        assertTrue(result.getSuccess());
        assertEquals(SyncType.RESULTS, result.getSyncType());
        assertEquals(3, result.getRecordsInserted());
        assertEquals(2, result.getRecordsUpdated());
        assertEquals(5, result.getRecordsFetched());
    }

    @Test
    @DisplayName("triggerSync STANDINGS should record count")
    void triggerSync_Standings() {
        when(apiDataSyncService.syncStandings("PL")).thenReturn(20);

        SyncStatusEntry result = dataSyncService.triggerSync(SyncType.STANDINGS, "PL", "admin");

        assertTrue(result.getSuccess());
        assertEquals(20, result.getRecordsFetched());
        assertEquals(20, result.getRecordsInserted());
    }

    @Test
    @DisplayName("Should record failure when sync throws exception")
    void triggerSync_Failure() {
        when(apiDataSyncService.syncFinishedMatches("PL"))
                .thenThrow(new RuntimeException("API connection refused"));

        SyncStatusEntry result = dataSyncService.triggerSync(SyncType.RESULTS, "PL", "scheduler");

        assertFalse(result.getSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("API connection refused"));
        assertNotNull(result.getFinishedAt());
    }

    @Test
    @DisplayName("getLatestSyncStatus delegates to repository")
    void getLatestSyncStatus() {
        SyncStatusEntry expected = SyncStatusEntry.builder()
                .id(1L)
                .syncType(SyncType.FULL)
                .success(true)
                .startedAt(LocalDateTime.now())
                .build();
        when(syncStatusRepo.findTopByOrderByStartedAtDesc()).thenReturn(Optional.of(expected));

        SyncStatusEntry result = dataSyncService.getLatestSyncStatus();
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("getRecentHistory returns last 20 entries")
    void getRecentHistory() {
        List<SyncStatusEntry> entries = List.of(
                SyncStatusEntry.builder().id(1L).syncType(SyncType.FULL).build(),
                SyncStatusEntry.builder().id(2L).syncType(SyncType.RESULTS).build()
        );
        when(syncStatusRepo.findTop20ByOrderByStartedAtDesc()).thenReturn(entries);

        List<SyncStatusEntry> result = dataSyncService.getRecentHistory();
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Error message should be truncated to 2000 chars")
    void errorMessageTruncation() {
        String longError = "X".repeat(3000);
        when(apiDataSyncService.syncFinishedMatches("PL"))
                .thenThrow(new RuntimeException(longError));

        SyncStatusEntry result = dataSyncService.triggerSync(SyncType.RESULTS, "PL", "test");

        assertFalse(result.getSuccess());
        assertNotNull(result.getErrorMessage());
        assertEquals(2000, result.getErrorMessage().length());
    }
}


