package com.app.footballprediction.service;

import com.app.common.model.League;
import com.app.common.model.LeagueStanding;
import com.app.common.model.Match;
import com.app.common.repository.LeagueRepository;
import com.app.common.repository.LeagueStandingRepository;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.external.FootballApiResponse;
import com.app.footballprediction.dto.external.StandingsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApiDataSyncService.
 */
@ExtendWith(MockitoExtension.class)
class ApiDataSyncServiceTest {

    @Mock
    private FootballDataApiService apiService;

    @Mock
    private LeagueStandingRepository standingRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private LeagueRepository leagueRepository;

    @Mock
    private LeagueStandingService standingService;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache mockCache;

    @InjectMocks
    private ApiDataSyncService apiDataSyncService;

    private League testLeague;
    private StandingsResponse mockStandingsResponse;
    private FootballApiResponse mockMatchesResponse;

    @BeforeEach
    void setUp() {
        testLeague = League.builder()
                .id(1L)
                .code("PL")
                .name("Premier League")
                .currentSeason("2025-26")
                .build();

        // Create mock standings response
        mockStandingsResponse = createMockStandingsResponse();
        mockMatchesResponse = createMockMatchesResponse();
    }

    @Test
    void syncStandings_ShouldSyncTeamsSuccessfully() {
        // Arrange
        when(apiService.getStandings("PL")).thenReturn(mockStandingsResponse);
        when(leagueRepository.findByCode("PL")).thenReturn(Optional.of(testLeague));
        when(apiService.normalizeTeamName("Arsenal FC")).thenReturn("Arsenal");
        doNothing().when(standingRepository).deleteByLeagueIdAndSeason(anyLong(), anyString());
        doNothing().when(standingService).clearStandingsCache();
        doNothing().when(apiService).clearStandingsCache("PL");

        // Act
        int count = apiDataSyncService.syncStandings("PL");

        // Assert
        assertEquals(1, count);
        verify(standingRepository, times(1)).save(any(LeagueStanding.class));
        verify(standingRepository, times(2)).deleteByLeagueIdAndSeason(eq(1L), anyString());
    }

    @Test
    void syncStandings_ShouldCreateLeagueIfNotExists() {
        // Arrange
        when(apiService.getStandings("PL")).thenReturn(mockStandingsResponse);
        when(leagueRepository.findByCode("PL")).thenReturn(Optional.empty());
        when(leagueRepository.save(any(League.class))).thenReturn(testLeague);
        when(apiService.normalizeTeamName("Arsenal FC")).thenReturn("Arsenal");
        doNothing().when(standingService).clearStandingsCache();
        doNothing().when(apiService).clearStandingsCache("PL");

        // Act
        int count = apiDataSyncService.syncStandings("PL");

        // Assert
        assertEquals(1, count);
        verify(leagueRepository, times(1)).save(any(League.class));
    }

    @Test
    void syncFinishedMatches_ShouldSyncNewMatches() {
        // Arrange
        when(apiService.getFinishedMatches("PL")).thenReturn(mockMatchesResponse);
        when(apiService.normalizeTeamName("Arsenal FC")).thenReturn("Arsenal");
        when(apiService.normalizeTeamName("Chelsea FC")).thenReturn("Chelsea");
        when(matchRepository.findByMatchDateAndHomeTeamAndAwayTeam(any(), anyString(), anyString()))
                .thenReturn(null);
        doNothing().when(apiService).clearMatchesCache();

        // Act
        int[] result = apiDataSyncService.syncFinishedMatches("PL");

        // Assert
        assertEquals(1, result[0]); // 1 new match
        assertEquals(0, result[1]); // 0 updated matches
        verify(matchRepository, times(1)).save(any(Match.class));
    }

    @Test
    void syncFinishedMatches_ShouldUpdateExistingMatch() {
        // Arrange
        Match existingMatch = Match.builder()
                .id(1L)
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .matchDate(LocalDate.of(2026, 2, 15))
                .fullTimeHomeGoals(0)
                .fullTimeAwayGoals(0)
                .build();

        when(apiService.getFinishedMatches("PL")).thenReturn(mockMatchesResponse);
        when(apiService.normalizeTeamName("Arsenal FC")).thenReturn("Arsenal");
        when(apiService.normalizeTeamName("Chelsea FC")).thenReturn("Chelsea");
        when(matchRepository.findByMatchDateAndHomeTeamAndAwayTeam(any(), eq("Arsenal"), eq("Chelsea")))
                .thenReturn(existingMatch);
        doNothing().when(apiService).clearMatchesCache();

        // Act
        int[] result = apiDataSyncService.syncFinishedMatches("PL");

        // Assert
        assertEquals(0, result[0]); // 0 new matches
        assertEquals(1, result[1]); // 1 updated match
        verify(matchRepository, times(1)).save(existingMatch);
        assertEquals(2, existingMatch.getFullTimeHomeGoals());
        assertEquals(1, existingMatch.getFullTimeAwayGoals());
    }

    @Test
    void syncScheduledMatches_ShouldSyncNewFixtures() {
        // Arrange
        FootballApiResponse scheduledResponse = createMockScheduledMatchesResponse();
        when(apiService.getScheduledMatches("PL")).thenReturn(scheduledResponse);
        when(apiService.normalizeTeamName("Liverpool FC")).thenReturn("Liverpool");
        when(apiService.normalizeTeamName("Man City FC")).thenReturn("Man City");
        when(matchRepository.existsByMatchDateAndHomeTeamAndAwayTeam(any(), anyString(), anyString()))
                .thenReturn(false);
        doNothing().when(apiService).clearMatchesCache();

        // Act
        int count = apiDataSyncService.syncScheduledMatches("PL");

        // Assert
        assertEquals(1, count);
        verify(matchRepository, times(1)).save(any(Match.class));
    }

    @Test
    void syncAll_ShouldCallAllSyncMethods() {
        // Arrange
        when(apiService.getStandings("PL")).thenReturn(mockStandingsResponse);
        when(apiService.getFinishedMatches("PL")).thenReturn(mockMatchesResponse);
        when(apiService.getScheduledMatches("PL")).thenReturn(new FootballApiResponse());
        when(leagueRepository.findByCode("PL")).thenReturn(Optional.of(testLeague));
        when(apiService.normalizeTeamName(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            return name.replace(" FC", "");
        });
        doNothing().when(standingRepository).deleteByLeagueIdAndSeason(anyLong(), anyString());
        doNothing().when(standingService).clearStandingsCache();
        doNothing().when(apiService).clearStandingsCache("PL");
        doNothing().when(apiService).clearMatchesCache();

        // Mock cache manager for cache clearing
        when(cacheManager.getCache(anyString())).thenReturn(mockCache);
        doNothing().when(mockCache).clear();

        // Act & Assert - should not throw
        assertDoesNotThrow(() -> apiDataSyncService.syncAll("PL"));
    }

    @Test
    void smartSync_WhenNoExistingMatches_ShouldPerformFullSync() {
        // Arrange
        when(apiService.getStandings("PL")).thenReturn(mockStandingsResponse);
        when(apiService.getFinishedMatches("PL")).thenReturn(mockMatchesResponse);
        when(apiService.getScheduledMatches("PL")).thenReturn(new FootballApiResponse());
        when(leagueRepository.findByCode("PL")).thenReturn(Optional.of(testLeague));
        when(apiService.normalizeTeamName(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            return name.replace(" FC", "");
        });

        // Return empty page for existing matches query
        org.springframework.data.domain.Page<Match> emptyPage =
            new org.springframework.data.domain.PageImpl<>(java.util.Collections.emptyList());
        when(matchRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(emptyPage);

        doNothing().when(standingRepository).deleteByLeagueIdAndSeason(anyLong(), anyString());
        doNothing().when(standingService).clearStandingsCache();
        doNothing().when(apiService).clearStandingsCache("PL");
        doNothing().when(apiService).clearMatchesCache();

        // Mock cache manager
        when(cacheManager.getCache(anyString())).thenReturn(mockCache);
        doNothing().when(mockCache).clear();

        // Act & Assert - should not throw
        assertDoesNotThrow(() -> apiDataSyncService.smartSync("PL"));
    }

    @Test
    void smartSync_WhenExistingMatches_ShouldSyncSinceLatestDate() {
        // Arrange
        Match existingMatch = Match.builder()
                .id(1L)
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .matchDate(LocalDate.now().minusDays(5))
                .build();

        org.springframework.data.domain.Page<Match> matchPage =
            new org.springframework.data.domain.PageImpl<>(List.of(existingMatch));
        when(matchRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(matchPage);

        when(apiService.getStandings("PL")).thenReturn(mockStandingsResponse);
        when(apiService.getFinishedMatches("PL")).thenReturn(mockMatchesResponse);
        when(apiService.getScheduledMatches("PL")).thenReturn(new FootballApiResponse());
        when(leagueRepository.findByCode("PL")).thenReturn(Optional.of(testLeague));
        when(apiService.normalizeTeamName(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            return name.replace(" FC", "");
        });

        doNothing().when(standingRepository).deleteByLeagueIdAndSeason(anyLong(), anyString());
        doNothing().when(standingService).clearStandingsCache();
        doNothing().when(apiService).clearStandingsCache("PL");
        doNothing().when(apiService).clearMatchesCache();

        // Mock cache manager
        when(cacheManager.getCache(anyString())).thenReturn(mockCache);
        doNothing().when(mockCache).clear();

        // Act & Assert
        assertDoesNotThrow(() -> apiDataSyncService.smartSync("PL"));
    }

    @Test
    void syncMatchesSinceDate_ShouldOnlySyncMatchesAfterDate() {
        // Arrange
        LocalDate sinceDate = LocalDate.of(2026, 2, 20);

        when(apiService.getFinishedMatches("PL")).thenReturn(mockMatchesResponse);
        doNothing().when(apiService).clearMatchesCache();

        // Act
        int[] result = apiDataSyncService.syncMatchesSinceDate("PL", sinceDate);

        // Assert - the mock match is dated 2026-02-15, which is before sinceDate, so it should be skipped
        assertEquals(0, result[0]); // No new matches (all filtered out)
        assertEquals(0, result[1]); // No updated matches
    }

    // ══════════════════════════════════════════════════════════════
    // Helper Methods for Creating Mock Data
    // ══════════════════════════════════════════════════════════════

    private StandingsResponse createMockStandingsResponse() {
        StandingsResponse response = new StandingsResponse();

        StandingsResponse.Competition competition = new StandingsResponse.Competition();
        competition.setId(2021L);
        competition.setName("Premier League");
        competition.setCode("PL");
        response.setCompetition(competition);

        StandingsResponse.Season season = new StandingsResponse.Season();
        season.setId(1L);
        season.setStartDate("2025-08-15");
        season.setEndDate("2026-05-20");
        response.setSeason(season);

        StandingsResponse.TeamInfo teamInfo = new StandingsResponse.TeamInfo();
        teamInfo.setId(57L);
        teamInfo.setName("Arsenal FC");
        teamInfo.setShortName("Arsenal");

        StandingsResponse.TableEntry entry = new StandingsResponse.TableEntry();
        entry.setPosition(1);
        entry.setTeam(teamInfo);
        entry.setPlayedGames(25);
        entry.setWon(18);
        entry.setDraw(4);
        entry.setLost(3);
        entry.setPoints(58);
        entry.setGoalsFor(55);
        entry.setGoalsAgainst(20);
        entry.setGoalDifference(35);
        entry.setForm("W,W,D,W,W");

        StandingsResponse.StandingType standingType = new StandingsResponse.StandingType();
        standingType.setType("TOTAL");
        standingType.setTable(List.of(entry));

        response.setStandings(List.of(standingType));

        return response;
    }

    private FootballApiResponse createMockMatchesResponse() {
        FootballApiResponse response = new FootballApiResponse();

        FootballApiResponse.TeamInfo homeTeam = new FootballApiResponse.TeamInfo();
        homeTeam.setId(57L);
        homeTeam.setName("Arsenal FC");

        FootballApiResponse.TeamInfo awayTeam = new FootballApiResponse.TeamInfo();
        awayTeam.setId(61L);
        awayTeam.setName("Chelsea FC");

        FootballApiResponse.ScoreDetail fullTime = new FootballApiResponse.ScoreDetail();
        fullTime.setHome(2);
        fullTime.setAway(1);

        FootballApiResponse.ScoreDetail halfTime = new FootballApiResponse.ScoreDetail();
        halfTime.setHome(1);
        halfTime.setAway(0);

        FootballApiResponse.Score score = new FootballApiResponse.Score();
        score.setWinner("HOME_TEAM");
        score.setFullTime(fullTime);
        score.setHalfTime(halfTime);

        FootballApiResponse.ApiMatch match = new FootballApiResponse.ApiMatch();
        match.setId(1L);
        match.setUtcDate("2026-02-15T15:00:00Z");
        match.setStatus("FINISHED");
        match.setMatchday(25);
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        match.setScore(score);

        response.setMatches(List.of(match));

        return response;
    }

    private FootballApiResponse createMockScheduledMatchesResponse() {
        FootballApiResponse response = new FootballApiResponse();

        FootballApiResponse.TeamInfo homeTeam = new FootballApiResponse.TeamInfo();
        homeTeam.setId(64L);
        homeTeam.setName("Liverpool FC");

        FootballApiResponse.TeamInfo awayTeam = new FootballApiResponse.TeamInfo();
        awayTeam.setId(65L);
        awayTeam.setName("Man City FC");

        FootballApiResponse.ApiMatch match = new FootballApiResponse.ApiMatch();
        match.setId(2L);
        match.setUtcDate("2026-03-01T17:30:00Z");
        match.setStatus("SCHEDULED");
        match.setMatchday(26);
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        match.setScore(null);

        response.setMatches(List.of(match));

        return response;
    }
}

