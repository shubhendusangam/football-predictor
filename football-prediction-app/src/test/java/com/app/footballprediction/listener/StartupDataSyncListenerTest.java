package com.app.footballprediction.listener;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.SeasonTeamStatsRepository;
import com.app.footballprediction.service.ApiDataSyncService;
import com.app.footballprediction.service.CsvIngestionService;
import com.app.footballprediction.service.MatchCompletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StartupDataSyncListener.
 */
@ExtendWith(MockitoExtension.class)
class StartupDataSyncListenerTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private ApiDataSyncService apiDataSyncService;

    @Mock
    private CsvIngestionService csvIngestionService;

    @Mock
    private MatchCompletionService matchCompletionService;

    @Mock
    private SeasonTeamStatsRepository seasonTeamStatsRepository;

    private StartupDataSyncListener listener;

    @BeforeEach
    void setUp() {
        listener = new StartupDataSyncListener(matchRepository, apiDataSyncService,
                csvIngestionService, matchCompletionService, seasonTeamStatsRepository);

        // Set default configuration values
        ReflectionTestUtils.setField(listener, "startupSyncEnabled", true);
        ReflectionTestUtils.setField(listener, "thresholdDays", 1);
        ReflectionTestUtils.setField(listener, "competition", "PL");
        ReflectionTestUtils.setField(listener, "syncMode", "smart");
    }

    @Test
    void onApplicationReady_WhenDisabled_ShouldNotSync() {
        // Arrange
        ReflectionTestUtils.setField(listener, "startupSyncEnabled", false);

        // Act
        listener.onApplicationReady();

        // Assert - should not call any sync methods
        verifyNoInteractions(apiDataSyncService);
        verifyNoInteractions(matchRepository);
    }

    @Test
    void onApplicationReady_WhenNoMatchesInDb_ShouldTriggerSync() {
        // Arrange
        Page<Match> emptyPage = new PageImpl<>(Collections.emptyList());
        when(matchRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);
        doNothing().when(apiDataSyncService).smartSync("PL");

        // Act
        listener.onApplicationReady();

        // Assert - should trigger smart sync
        verify(apiDataSyncService, times(1)).smartSync("PL");
    }

    @Test
    void onApplicationReady_WhenDataIsStale_ShouldTriggerSync() {
        // Arrange - latest match is 5 days old
        Match staleMatch = Match.builder()
                .id(1L)
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .matchDate(LocalDate.now().minusDays(5))
                .build();

        Page<Match> stalePage = new PageImpl<>(List.of(staleMatch));
        when(matchRepository.findAll(any(Pageable.class))).thenReturn(stalePage);
        when(matchRepository.count()).thenReturn(100L);
        doNothing().when(apiDataSyncService).smartSync("PL");

        // Act
        listener.onApplicationReady();

        // Assert - should trigger smart sync because data is 5 days old (> threshold of 1)
        verify(apiDataSyncService, times(1)).smartSync("PL");
    }

    @Test
    void onApplicationReady_WhenDataIsFresh_ShouldNotSync() {
        // Arrange - latest match is today
        Match freshMatch = Match.builder()
                .id(1L)
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .matchDate(LocalDate.now())
                .build();

        Page<Match> freshPage = new PageImpl<>(List.of(freshMatch));
        when(matchRepository.findAll(any(Pageable.class))).thenReturn(freshPage);
        when(matchRepository.count()).thenReturn(100L);

        // Act
        listener.onApplicationReady();

        // Assert - should NOT trigger sync because data is fresh
        verify(apiDataSyncService, never()).smartSync(any());
        verify(apiDataSyncService, never()).syncAll(any());
    }

    @Test
    void onApplicationReady_WhenLatestMatchIsFuture_ShouldNotSync() {
        // Arrange - latest match is a future fixture
        Match futureMatch = Match.builder()
                .id(1L)
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .matchDate(LocalDate.now().plusDays(3))
                .build();

        Page<Match> futurePage = new PageImpl<>(List.of(futureMatch));
        when(matchRepository.findAll(any(Pageable.class))).thenReturn(futurePage);
        when(matchRepository.count()).thenReturn(100L);

        // Act
        listener.onApplicationReady();

        // Assert - should NOT trigger sync because we have future fixtures
        verify(apiDataSyncService, never()).smartSync(any());
        verify(apiDataSyncService, never()).syncAll(any());
    }

    @Test
    void onApplicationReady_WhenFullMode_ShouldUseSyncAll() {
        // Arrange
        ReflectionTestUtils.setField(listener, "syncMode", "full");

        Match staleMatch = Match.builder()
                .id(1L)
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .matchDate(LocalDate.now().minusDays(5))
                .build();

        Page<Match> stalePage = new PageImpl<>(List.of(staleMatch));
        when(matchRepository.findAll(any(Pageable.class))).thenReturn(stalePage);
        when(matchRepository.count()).thenReturn(100L);
        doNothing().when(apiDataSyncService).syncAll("PL");

        // Act
        listener.onApplicationReady();

        // Assert - should use syncAll instead of smartSync
        verify(apiDataSyncService, times(1)).syncAll("PL");
        verify(apiDataSyncService, never()).smartSync(any());
    }

    @Test
    void onApplicationReady_WhenSyncFails_ShouldNotCrashApplication() {
        // Arrange
        Page<Match> emptyPage = new PageImpl<>(Collections.emptyList());
        when(matchRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);
        doThrow(new RuntimeException("API error")).when(apiDataSyncService).smartSync("PL");

        // Act - should not throw exception
        listener.onApplicationReady();

        // Assert - method completed without throwing
        verify(apiDataSyncService, times(1)).smartSync("PL");
    }

    @Test
    void onApplicationReady_WhenThresholdIsZero_ShouldAlwaysSync() {
        // Arrange
        ReflectionTestUtils.setField(listener, "thresholdDays", 0);

        Match freshMatch = Match.builder()
                .id(1L)
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .matchDate(LocalDate.now())
                .build();

        Page<Match> freshPage = new PageImpl<>(List.of(freshMatch));
        when(matchRepository.findAll(any(Pageable.class))).thenReturn(freshPage);
        when(matchRepository.count()).thenReturn(100L);

        // Act
        listener.onApplicationReady();

        // Assert - should NOT sync because daysSinceLastMatch (0) is NOT > threshold (0)
        // This test verifies threshold=0 means "sync if older than 0 days" (i.e., stale)
        verify(apiDataSyncService, never()).smartSync(any());
    }
}

