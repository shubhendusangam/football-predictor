package com.app.footballprediction.integration;

import com.app.footballprediction.dto.PredictRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for all API endpoints using RestTemplate.
 * These tests run with the full Spring context and a real HTTP server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("API Endpoints Integration Tests")
class ApiEndpointsIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
    }

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api";
    }

    // ==================== Prediction Endpoints ====================

    @Test
    @DisplayName("POST /api/predict - should validate required home team")
    void testPredictRequiresHomeTeam() {
        PredictRequest request = new PredictRequest();
        request.setAwayTeam("Chelsea");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<PredictRequest> entity = new HttpEntity<>(request, headers);

        assertThatThrownBy(() ->
                restTemplate.postForEntity(getBaseUrl() + "/predict", entity, Map.class)
        ).isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    @DisplayName("POST /api/predict - should validate required away team")
    void testPredictRequiresAwayTeam() {
        PredictRequest request = new PredictRequest();
        request.setHomeTeam("Arsenal");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<PredictRequest> entity = new HttpEntity<>(request, headers);

        assertThatThrownBy(() ->
                restTemplate.postForEntity(getBaseUrl() + "/predict", entity, Map.class)
        ).isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    @DisplayName("GET /api/model/status - should return model status")
    void testModelStatus() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/model/status",
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("modelLoaded");
        assertThat(response.getBody()).containsKey("hint");
    }

    @Test
    @DisplayName("GET /api/teams - should return teams list")
    void testGetAllTeams() {
        ResponseEntity<Object[]> response = restTemplate.getForEntity(
                getBaseUrl() + "/teams",
                Object[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("GET /api/predictions - should return predictions")
    void testGetAllPredictions() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/predictions",
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("predictions");
        assertThat(response.getBody()).containsKey("count");
    }

    @Test
    @DisplayName("GET /api/predictions/today - should return today's predictions")
    void testGetTodaysPredictions() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/predictions/today",
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("predictions");
    }

    @Test
    @DisplayName("GET /api/matches/upcoming - should return upcoming matches")
    void testGetUpcomingMatches() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/matches/upcoming",
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("matches");
        assertThat(response.getBody()).containsKey("count");
    }

    @Test
    @DisplayName("GET /api/dashboard/stats - should return dashboard statistics")
    void testGetDashboardStats() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/dashboard/stats",
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("totalMatches");
    }

    @Test
    @DisplayName("GET /api/dashboard/accuracy - should return model accuracy metrics")
    void testGetModelAccuracy() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                getBaseUrl() + "/dashboard/accuracy",
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("overall");
        assertThat(response.getBody()).containsKey("totalPredictions");
    }

    @Test
    @DisplayName("GET /api/teams/{teamName}/stats - should handle invalid team name")
    void testGetTeamStatsInvalidTeam() {
        assertThatThrownBy(() ->
                restTemplate.getForEntity(
                        getBaseUrl() + "/teams/NonExistentTeam/stats",
                        Map.class
                )
        ).isInstanceOf(HttpClientErrorException.BadRequest.class)
         .hasMessageContaining("400");
    }

    @Test
    @DisplayName("POST /api/external/clear-cache - should clear external API cache")
    void testClearExternalCache() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/external/clear-cache",
                null,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("message");
    }

    @Test
    @DisplayName("POST /api/cache/clear - should require admin authentication")
    void testClearAllCaches() {
        // This endpoint requires admin authentication, so it should return 401
        assertThatThrownBy(() ->
                restTemplate.postForEntity(
                        getBaseUrl() + "/cache/clear",
                        null,
                        Map.class
                )
        ).isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    @Test
    @DisplayName("POST /api/cache/warmup - should warm up caches")
    void testWarmCaches() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getBaseUrl() + "/cache/warmup",
                null,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("status");
    }
}

