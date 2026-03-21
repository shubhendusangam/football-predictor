package com.app.footballprediction.service;

import com.app.common.dto.*;
import com.app.common.exception.ApiQuotaExceededException;
import com.app.footballprediction.client.ApiFootballClient;
import com.app.footballprediction.ratelimit.ApiFootballRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InjuryDataService (mock ApiFootballClient).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InjuryDataService Unit Tests")
class InjuryDataServiceTest {

    @Mock
    private ApiFootballClient apiFootballClient;

    @Mock
    private ApiFootballRateLimiter rateLimiter;

    @InjectMocks
    private InjuryDataService injuryDataService;

    @BeforeEach
    void setUp() {
        // Set the self-reference so that self.fetchRawInjuries() and self.getTeamAvailability()
        // route to the same test instance (no Spring proxy in unit tests).
        ReflectionTestUtils.setField(injuryDataService, "self", injuryDataService);
    }

    @Test
    @DisplayName("quotaExhausted: ApiQuotaExceededException → returns FULL_STRENGTH, dataAvailable=false")
    void quotaExhausted() {
        when(apiFootballClient.getInjuriesByFixture(anyLong()))
                .thenReturn(Mono.error(new ApiQuotaExceededException("Quota exhausted")));

        TeamAvailabilityDTO result = injuryDataService.getTeamAvailability(12345L, 100);

        assertThat(result.isDataAvailable()).isFalse();
        assertThat(result.getAvailabilityRating()).isEqualTo("FULL_STRENGTH");
        assertThat(result.getImpactSummary()).contains("unavailable");
    }

    @Test
    @DisplayName("apiTimeout: mock timeout → returns FULL_STRENGTH, dataAvailable=false")
    void apiTimeout() {
        when(apiFootballClient.getInjuriesByFixture(anyLong()))
                .thenReturn(Mono.error(new RuntimeException("Connection timeout")));

        TeamAvailabilityDTO result = injuryDataService.getTeamAvailability(12345L, 100);

        assertThat(result.isDataAvailable()).isFalse();
        assertThat(result.getAvailabilityRating()).isEqualTo("FULL_STRENGTH");
    }

    @Test
    @DisplayName("emptyResponse: results=0 → returns fallback, dataAvailable=false")
    void emptyResponse() {
        InjuryApiResponse empty = InjuryApiResponse.builder()
                .results(0)
                .response(List.of())
                .build();
        when(apiFootballClient.getInjuriesByFixture(anyLong()))
                .thenReturn(Mono.just(empty));

        TeamAvailabilityDTO result = injuryDataService.getTeamAvailability(12345L, 100);

        assertThat(result.isDataAvailable()).isFalse();
    }

    @Test
    @DisplayName("strikerInjury: striker in response → attackImpactReduction > 0")
    void strikerInjury() {
        InjuryEntry entry = InjuryEntry.builder()
                .player(InjuryEntry.InjuryPlayerInfo.builder().id(10).name("Saka").build())
                .team(InjuryEntry.InjuryTeamInfo.builder().id(42).name("Arsenal").build())
                .fixture(InjuryEntry.InjuryFixtureInfo.builder().id(99).build())
                .type("Hamstring")
                .reason("Muscle injury")
                .build();

        InjuryApiResponse response = InjuryApiResponse.builder()
                .results(1)
                .response(List.of(entry))
                .build();
        when(apiFootballClient.getInjuriesByFixture(anyLong()))
                .thenReturn(Mono.just(response));

        TeamAvailabilityDTO result = injuryDataService.getTeamAvailability(99L, 42);

        assertThat(result.isDataAvailable()).isTrue();
        assertThat(result.getAttackImpactReduction()).isGreaterThan(0.0);
        assertThat(result.getInjuredPlayers()).hasSize(1);
        assertThat(result.getInjuredPlayers().get(0).getPlayerName()).isEqualTo("Saka");
    }

    @Test
    @DisplayName("suspendedPlayer: type=Suspended → isSuspension=true, in suspendedPlayers list")
    void suspendedPlayer() {
        InjuryEntry entry = InjuryEntry.builder()
                .player(InjuryEntry.InjuryPlayerInfo.builder().id(7).name("Xhaka").build())
                .team(InjuryEntry.InjuryTeamInfo.builder().id(42).name("Arsenal").build())
                .fixture(InjuryEntry.InjuryFixtureInfo.builder().id(99).build())
                .type("Suspended")
                .reason("5 yellow cards")
                .build();

        InjuryApiResponse response = InjuryApiResponse.builder()
                .results(1)
                .response(List.of(entry))
                .build();
        when(apiFootballClient.getInjuriesByFixture(anyLong()))
                .thenReturn(Mono.just(response));

        TeamAvailabilityDTO result = injuryDataService.getTeamAvailability(99L, 42);

        assertThat(result.isDataAvailable()).isTrue();
        assertThat(result.getSuspendedPlayers()).hasSize(1);
        assertThat(result.getSuspendedPlayers().get(0).isSuspension()).isTrue();
        assertThat(result.getInjuredPlayers()).isEmpty();
    }

    @Test
    @DisplayName("getMatchInjuryContext returns combined context for both teams")
    void matchContext() {
        InjuryApiResponse empty = InjuryApiResponse.builder()
                .results(0)
                .response(List.of())
                .build();
        when(apiFootballClient.getInjuriesByFixture(anyLong()))
                .thenReturn(Mono.just(empty));

        MatchInjuryContextDTO context = injuryDataService.getMatchInjuryContext(100L, 1, 2);

        assertThat(context.getFixtureId()).isEqualTo(100L);
        assertThat(context.getHomeAvailability()).isNotNull();
        assertThat(context.getAwayAvailability()).isNotNull();
    }

    @Test
    @DisplayName("multiple players missing → correct availability rating")
    void multiplePlayersMissing() {
        InjuryEntry entry1 = InjuryEntry.builder()
                .player(InjuryEntry.InjuryPlayerInfo.builder().id(1).name("Player1").build())
                .team(InjuryEntry.InjuryTeamInfo.builder().id(42).name("Arsenal").build())
                .fixture(InjuryEntry.InjuryFixtureInfo.builder().id(99).build())
                .type("Knee").reason("ACL injury")
                .build();
        InjuryEntry entry2 = InjuryEntry.builder()
                .player(InjuryEntry.InjuryPlayerInfo.builder().id(2).name("Player2").build())
                .team(InjuryEntry.InjuryTeamInfo.builder().id(42).name("Arsenal").build())
                .fixture(InjuryEntry.InjuryFixtureInfo.builder().id(99).build())
                .type("Hamstring").reason("Muscle strain")
                .build();
        InjuryEntry entry3 = InjuryEntry.builder()
                .player(InjuryEntry.InjuryPlayerInfo.builder().id(3).name("Player3").build())
                .team(InjuryEntry.InjuryTeamInfo.builder().id(42).name("Arsenal").build())
                .fixture(InjuryEntry.InjuryFixtureInfo.builder().id(99).build())
                .type("Suspended").reason("Red card")
                .build();

        InjuryApiResponse response = InjuryApiResponse.builder()
                .results(3)
                .response(List.of(entry1, entry2, entry3))
                .build();
        when(apiFootballClient.getInjuriesByFixture(anyLong()))
                .thenReturn(Mono.just(response));

        TeamAvailabilityDTO result = injuryDataService.getTeamAvailability(99L, 42);

        assertThat(result.isDataAvailable()).isTrue();
        assertThat(result.getTotalMissing()).isEqualTo(3);
        assertThat(result.getAvailabilityRating()).isEqualTo("SEVERELY_WEAKENED");
        assertThat(result.getInjuredPlayers()).hasSize(2);
        assertThat(result.getSuspendedPlayers()).hasSize(1);
    }
}

