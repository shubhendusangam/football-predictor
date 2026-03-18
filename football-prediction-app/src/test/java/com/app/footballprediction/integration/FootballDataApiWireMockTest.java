package com.app.footballprediction.integration;

import com.app.footballprediction.dto.external.FootballApiResponse;
import com.app.footballprediction.dto.external.StandingsResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Integration tests for Football Data API sync using WireMock.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Successful fixture and result fetching</li>
 *   <li>Retry logic with exponential backoff on 429 Too Many Requests</li>
 *   <li>Retry on 503 Service Unavailable</li>
 *   <li>Timeout handling</li>
 *   <li>Malformed JSON response handling</li>
 *   <li>Empty response handling</li>
 * </ul>
 */
class FootballDataApiWireMockTest {

    private static WireMockServer wireMockServer;
    private WebClient webClient;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        webClient = WebClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port())
                .defaultHeader("X-Auth-Token", "test-api-key")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();
    }

    // ══════════════════════════════════════════════════════════════
    // Successful API Calls
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should fetch finished matches successfully")
    void fetchFinishedMatches_Success() {
        wireMockServer.stubFor(get(urlPathEqualTo("/competitions/PL/matches"))
                .withQueryParam("status", equalTo("FINISHED"))
                .withHeader("X-Auth-Token", equalTo("test-api-key"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FINISHED_MATCHES_JSON)));

        Mono<FootballApiResponse> result = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/competitions/{code}/matches")
                        .queryParam("status", "FINISHED")
                        .build("PL"))
                .retrieve()
                .bodyToMono(FootballApiResponse.class);

        StepVerifier.create(result)
                .assertNext(response -> {
                    Assertions.assertNotNull(response);
                    Assertions.assertNotNull(response.getMatches());
                    Assertions.assertEquals(1, response.getMatches().size());
                    Assertions.assertEquals("FINISHED", response.getMatches().get(0).getStatus());
                    Assertions.assertEquals("Arsenal FC", response.getMatches().get(0).getHomeTeam().getName());
                    Assertions.assertEquals("Chelsea FC", response.getMatches().get(0).getAwayTeam().getName());
                    Assertions.assertEquals(2, response.getMatches().get(0).getScore().getFullTime().getHome());
                    Assertions.assertEquals(1, response.getMatches().get(0).getScore().getFullTime().getAway());
                })
                .verifyComplete();

        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/competitions/PL/matches")));
    }

    @Test
    @DisplayName("Should fetch scheduled matches successfully")
    void fetchScheduledMatches_Success() {
        wireMockServer.stubFor(get(urlPathEqualTo("/competitions/PL/matches"))
                .withQueryParam("status", equalTo("SCHEDULED,TIMED"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SCHEDULED_MATCHES_JSON)));

        Mono<FootballApiResponse> result = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/competitions/{code}/matches")
                        .queryParam("status", "SCHEDULED,TIMED")
                        .build("PL"))
                .retrieve()
                .bodyToMono(FootballApiResponse.class);

        StepVerifier.create(result)
                .assertNext(response -> {
                    Assertions.assertNotNull(response);
                    Assertions.assertNotNull(response.getMatches());
                    Assertions.assertEquals(1, response.getMatches().size());
                    Assertions.assertEquals("TIMED", response.getMatches().get(0).getStatus());
                    Assertions.assertNull(response.getMatches().get(0).getScore().getFullTime().getHome());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fetch standings successfully")
    void fetchStandings_Success() {
        wireMockServer.stubFor(get(urlPathEqualTo("/competitions/PL/standings"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(STANDINGS_JSON)));

        Mono<StandingsResponse> result = webClient.get()
                .uri("/competitions/{code}/standings", "PL")
                .retrieve()
                .bodyToMono(StandingsResponse.class);

        StepVerifier.create(result)
                .assertNext(response -> {
                    Assertions.assertNotNull(response);
                    Assertions.assertNotNull(response.getCompetition());
                    Assertions.assertEquals("Premier League", response.getCompetition().getName());
                })
                .verifyComplete();
    }

    // ══════════════════════════════════════════════════════════════
    // Retry Logic — 429 Too Many Requests
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should retry on 429 Too Many Requests with exponential backoff")
    void retryOn429_TooManyRequests() {
        // First two calls return 429, third succeeds
        wireMockServer.stubFor(get(urlPathEqualTo("/competitions/PL/matches"))
                .withQueryParam("status", equalTo("FINISHED"))
                .inScenario("429-retry")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(429).withBody("Rate limit exceeded"))
                .willSetStateTo("first-retry"));

        wireMockServer.stubFor(get(urlPathEqualTo("/competitions/PL/matches"))
                .withQueryParam("status", equalTo("FINISHED"))
                .inScenario("429-retry")
                .whenScenarioStateIs("first-retry")
                .willReturn(aResponse().withStatus(429).withBody("Rate limit exceeded"))
                .willSetStateTo("second-retry"));

        wireMockServer.stubFor(get(urlPathEqualTo("/competitions/PL/matches"))
                .withQueryParam("status", equalTo("FINISHED"))
                .inScenario("429-retry")
                .whenScenarioStateIs("second-retry")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FINISHED_MATCHES_JSON)));

        Mono<FootballApiResponse> result = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/competitions/{code}/matches")
                        .queryParam("status", "FINISHED")
                        .build("PL"))
                .retrieve()
                .bodyToMono(FootballApiResponse.class)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                        .maxBackoff(Duration.ofSeconds(1))
                        .filter(t -> t instanceof WebClientResponseException.TooManyRequests));

        StepVerifier.create(result)
                .assertNext(response -> {
                    Assertions.assertNotNull(response);
                    Assertions.assertEquals(1, response.getMatches().size());
                })
                .verifyComplete();

        // Verify 3 total requests were made (2 retries + 1 success)
        wireMockServer.verify(3, getRequestedFor(urlPathEqualTo("/competitions/PL/matches")));
    }

    @Test
    @DisplayName("Should exhaust retries on persistent 429 and propagate error")
    void exhaustRetriesOn429() {
        wireMockServer.stubFor(get(urlPathEqualTo("/competitions/PL/matches"))
                .withQueryParam("status", equalTo("FINISHED"))
                .willReturn(aResponse().withStatus(429).withBody("Rate limit exceeded")));

        Mono<FootballApiResponse> result = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/competitions/{code}/matches")
                        .queryParam("status", "FINISHED")
                        .build("PL"))
                .retrieve()
                .bodyToMono(FootballApiResponse.class)
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100))
                        .maxBackoff(Duration.ofMillis(500))
                        .filter(t -> t instanceof WebClientResponseException.TooManyRequests)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()));

        StepVerifier.create(result)
                .expectError(WebClientResponseException.TooManyRequests.class)
                .verify(Duration.ofSeconds(10));

        // 1 original + 2 retries = 3 total
        wireMockServer.verify(3, getRequestedFor(urlPathEqualTo("/competitions/PL/matches")));
    }

    // ══════════════════════════════════════════════════════════════
    // Retry Logic — 503 Service Unavailable
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should retry on 503 Service Unavailable")
    void retryOn503_ServiceUnavailable() {
        wireMockServer.stubFor(get(urlPathEqualTo("/competitions/PL/matches"))
                .withQueryParam("status", equalTo("FINISHED"))
                .inScenario("503-retry")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(503).withBody("Service Unavailable"))
                .willSetStateTo("recovered"));

        wireMockServer.stubFor(get(urlPathEqualTo("/competitions/PL/matches"))
                .withQueryParam("status", equalTo("FINISHED"))
                .inScenario("503-retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FINISHED_MATCHES_JSON)));

        Mono<FootballApiResponse> result = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/competitions/{code}/matches")
                        .queryParam("status", "FINISHED")
                        .build("PL"))
                .retrieve()
                .bodyToMono(FootballApiResponse.class)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                        .maxBackoff(Duration.ofSeconds(1))
                        .filter(t -> t instanceof WebClientResponseException.ServiceUnavailable
                                || t instanceof WebClientResponseException.TooManyRequests));

        StepVerifier.create(result)
                .assertNext(response -> {
                    Assertions.assertNotNull(response);
                    Assertions.assertEquals(1, response.getMatches().size());
                })
                .verifyComplete();

        wireMockServer.verify(2, getRequestedFor(urlPathEqualTo("/competitions/PL/matches")));
    }

    // ══════════════════════════════════════════════════════════════
    // Timeout Handling
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should timeout on slow response")
    void timeoutOnSlowResponse() {
        wireMockServer.stubFor(get(urlPathEqualTo("/competitions/PL/matches"))
                .withQueryParam("status", equalTo("FINISHED"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(5000) // 5 second delay
                        .withHeader("Content-Type", "application/json")
                        .withBody(FINISHED_MATCHES_JSON)));

        Mono<FootballApiResponse> result = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/competitions/{code}/matches")
                        .queryParam("status", "FINISHED")
                        .build("PL"))
                .retrieve()
                .bodyToMono(FootballApiResponse.class)
                .timeout(Duration.ofSeconds(2));

        StepVerifier.create(result)
                .expectError(java.util.concurrent.TimeoutException.class)
                .verify(Duration.ofSeconds(10));
    }

    // ══════════════════════════════════════════════════════════════
    // Error Responses
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should handle 404 Not Found")
    void handle404_NotFound() {
        wireMockServer.stubFor(get(urlPathEqualTo("/competitions/INVALID/matches"))
                .withQueryParam("status", equalTo("FINISHED"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("{\"message\":\"Competition not found\"}")));

        Mono<FootballApiResponse> result = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/competitions/{code}/matches")
                        .queryParam("status", "FINISHED")
                        .build("INVALID"))
                .retrieve()
                .bodyToMono(FootballApiResponse.class);

        StepVerifier.create(result)
                .expectError(WebClientResponseException.NotFound.class)
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Should handle 403 Forbidden (invalid API key)")
    void handle403_Forbidden() {
        wireMockServer.stubFor(get(urlPathEqualTo("/competitions/PL/matches"))
                .withQueryParam("status", equalTo("FINISHED"))
                .willReturn(aResponse()
                        .withStatus(403)
                        .withBody("{\"message\":\"Your API token is invalid\"}")));

        Mono<FootballApiResponse> result = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/competitions/{code}/matches")
                        .queryParam("status", "FINISHED")
                        .build("PL"))
                .retrieve()
                .bodyToMono(FootballApiResponse.class);

        StepVerifier.create(result)
                .expectError(WebClientResponseException.Forbidden.class)
                .verify(Duration.ofSeconds(5));
    }

    // ══════════════════════════════════════════════════════════════
    // Malformed Response Handling
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should handle malformed JSON response gracefully")
    void handleMalformedJson() {
        wireMockServer.stubFor(get(urlPathEqualTo("/competitions/PL/matches"))
                .withQueryParam("status", equalTo("FINISHED"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ invalid json }")));

        Mono<FootballApiResponse> result = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/competitions/{code}/matches")
                        .queryParam("status", "FINISHED")
                        .build("PL"))
                .retrieve()
                .bodyToMono(FootballApiResponse.class);

        StepVerifier.create(result)
                .expectError()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Should handle empty matches array")
    void handleEmptyMatchesArray() {
        String emptyJson = """
                {
                  "resultSet": {"count": 0},
                  "competition": {"name": "Premier League", "code": "PL"},
                  "matches": []
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/competitions/PL/matches"))
                .withQueryParam("status", equalTo("FINISHED"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(emptyJson)));

        Mono<FootballApiResponse> result = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/competitions/{code}/matches")
                        .queryParam("status", "FINISHED")
                        .build("PL"))
                .retrieve()
                .bodyToMono(FootballApiResponse.class);

        StepVerifier.create(result)
                .assertNext(response -> {
                    Assertions.assertNotNull(response);
                    Assertions.assertTrue(response.getMatches().isEmpty());
                })
                .verifyComplete();
    }

    // ══════════════════════════════════════════════════════════════
    // Auth Header Verification
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Should send X-Auth-Token header with every request")
    void sendsAuthHeader() {
        wireMockServer.stubFor(get(urlPathEqualTo("/competitions/PL/matches"))
                .withQueryParam("status", equalTo("FINISHED"))
                .withHeader("X-Auth-Token", equalTo("test-api-key"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(FINISHED_MATCHES_JSON)));

        webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/competitions/{code}/matches")
                        .queryParam("status", "FINISHED")
                        .build("PL"))
                .retrieve()
                .bodyToMono(FootballApiResponse.class)
                .block();

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/competitions/PL/matches"))
                .withHeader("X-Auth-Token", equalTo("test-api-key")));
    }

    // ══════════════════════════════════════════════════════════════
    // Test Data (JSON fixtures)
    // ══════════════════════════════════════════════════════════════

    private static final String FINISHED_MATCHES_JSON = """
            {
              "resultSet": {"count": 1, "first": "2026-03-01", "last": "2026-03-01", "played": 1},
              "competition": {"id": 2021, "name": "Premier League", "code": "PL"},
              "matches": [
                {
                  "id": 12345,
                  "utcDate": "2026-03-01T15:00:00Z",
                  "status": "FINISHED",
                  "matchday": 28,
                  "stage": "REGULAR_SEASON",
                  "homeTeam": {"id": 57, "name": "Arsenal FC", "shortName": "Arsenal", "tla": "ARS", "crest": "https://crests.football-data.org/57.png"},
                  "awayTeam": {"id": 61, "name": "Chelsea FC", "shortName": "Chelsea", "tla": "CHE", "crest": "https://crests.football-data.org/61.png"},
                  "score": {
                    "winner": "HOME_TEAM",
                    "duration": "REGULAR",
                    "fullTime": {"home": 2, "away": 1},
                    "halfTime": {"home": 1, "away": 0}
                  }
                }
              ]
            }
            """;

    private static final String SCHEDULED_MATCHES_JSON = """
            {
              "resultSet": {"count": 1},
              "competition": {"id": 2021, "name": "Premier League", "code": "PL"},
              "matches": [
                {
                  "id": 12346,
                  "utcDate": "2026-03-20T20:00:00Z",
                  "status": "TIMED",
                  "matchday": 30,
                  "stage": "REGULAR_SEASON",
                  "homeTeam": {"id": 65, "name": "Manchester City FC", "shortName": "Man City", "tla": "MCI", "crest": "https://crests.football-data.org/65.png"},
                  "awayTeam": {"id": 66, "name": "Manchester United FC", "shortName": "Man United", "tla": "MUN", "crest": "https://crests.football-data.org/66.png"},
                  "score": {
                    "winner": null,
                    "duration": "REGULAR",
                    "fullTime": {"home": null, "away": null},
                    "halfTime": {"home": null, "away": null}
                  }
                }
              ]
            }
            """;

    private static final String STANDINGS_JSON = """
            {
              "competition": {"id": 2021, "name": "Premier League", "code": "PL"},
              "season": {"id": 2250, "startDate": "2025-08-16", "endDate": "2026-05-24"},
              "standings": [
                {
                  "type": "TOTAL",
                  "table": [
                    {
                      "position": 1,
                      "team": {"id": 57, "name": "Arsenal FC", "shortName": "Arsenal", "tla": "ARS", "crest": "https://crests.football-data.org/57.png"},
                      "playedGames": 28,
                      "form": "W,W,D,W,W",
                      "won": 22,
                      "draw": 3,
                      "lost": 3,
                      "points": 69,
                      "goalsFor": 65,
                      "goalsAgainst": 20,
                      "goalDifference": 45
                    }
                  ]
                }
              ]
            }
            """;
}

