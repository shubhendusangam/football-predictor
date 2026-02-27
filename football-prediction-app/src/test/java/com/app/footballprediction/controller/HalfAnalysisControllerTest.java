package com.app.footballprediction.controller;

import com.app.footballprediction.dto.HalfAnalysisDTO;
import com.app.footballprediction.service.HalfAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HalfAnalysisController.
 * Tests REST endpoint behavior for half analysis.
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HalfAnalysisController Unit Tests")
class HalfAnalysisControllerTest {

    @Mock
    private HalfAnalysisService halfAnalysisService;

    @InjectMocks
    private HalfAnalysisController halfAnalysisController;

    // ══════════════════════════════════════════════════════════════════════
    // GET /api/teams/{teamName}/half-analysis
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/teams/{teamName}/half-analysis")
    class GetHalfAnalysisTests {

        @Test
        @DisplayName("Should return 200 with analysis for valid team")
        void shouldReturn200WithAnalysisForValidTeam() {
            // Given
            String teamName = "Arsenal";
            HalfAnalysisDTO mockDto = HalfAnalysisDTO.builder()
                    .teamName(teamName)
                    .matchesAnalyzed(20)
                    .firstHalfGoalsAvg(0.75)
                    .secondHalfGoalsAvg(1.25)
                    .firstHalfPercentage(37.5)
                    .secondHalfPercentage(62.5)
                    .strongerHalf("Second Half")
                    .pattern("Strong Finisher")
                    .winRateWhenLeadingHT(85.0)
                    .winRateWhenDrawingHT(40.0)
                    .winRateWhenLosingHT(10.0)
                    .comebackRate(10.0)
                    .matchesLeadingHT(10)
                    .matchesDrawingHT(5)
                    .matchesTrailingHT(5)
                    .confidence(0.9)
                    .build();

            when(halfAnalysisService.analyzeByHalf(teamName)).thenReturn(mockDto);

            // When
            ResponseEntity<?> response = halfAnalysisController.getHalfAnalysis(teamName);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isInstanceOf(HalfAnalysisDTO.class);
            HalfAnalysisDTO body = (HalfAnalysisDTO) response.getBody();
            assertThat(body.getTeamName()).isEqualTo(teamName);
            assertThat(body.getMatchesAnalyzed()).isEqualTo(20);
            assertThat(body.getFirstHalfPercentage()).isEqualTo(37.5);
            assertThat(body.getSecondHalfPercentage()).isEqualTo(62.5);
            assertThat(body.getPattern()).isEqualTo("Strong Finisher");
            assertThat(body.getComebackRate()).isEqualTo(10.0);

            verify(halfAnalysisService, times(1)).analyzeByHalf(teamName);
        }

        @Test
        @DisplayName("Should return 404 when team has no data")
        void shouldReturn404WhenTeamHasNoData() {
            // Given
            String teamName = "NonExistentFC";
            HalfAnalysisDTO emptyDto = HalfAnalysisDTO.empty(teamName);

            when(halfAnalysisService.analyzeByHalf(teamName)).thenReturn(emptyDto);

            // When
            ResponseEntity<?> response = halfAnalysisController.getHalfAnalysis(teamName);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("error")).isEqualTo("Not Found");
            assertThat(body.get("teamName")).isEqualTo(teamName);

            verify(halfAnalysisService, times(1)).analyzeByHalf(teamName);
        }

        @Test
        @DisplayName("Should return 400 for blank team name")
        void shouldReturn400ForBlankTeamName() {
            // When
            ResponseEntity<?> response = halfAnalysisController.getHalfAnalysis("   ");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("error")).isEqualTo("Bad Request");

            // Service should not be called for blank name
            verify(halfAnalysisService, never()).analyzeByHalf(any());
        }

        @Test
        @DisplayName("Should return 400 when service throws IllegalArgumentException")
        void shouldReturn400WhenServiceThrowsIllegalArgument() {
            // Given
            String teamName = "Invalid";
            when(halfAnalysisService.analyzeByHalf(teamName))
                    .thenThrow(new IllegalArgumentException("Team name cannot be null or empty"));

            // When
            ResponseEntity<?> response = halfAnalysisController.getHalfAnalysis(teamName);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("error")).isEqualTo("Bad Request");
        }

        @Test
        @DisplayName("Should handle team names with spaces")
        void shouldHandleTeamNamesWithSpaces() {
            // Given
            String teamName = "Man City";
            HalfAnalysisDTO mockDto = HalfAnalysisDTO.builder()
                    .teamName(teamName)
                    .matchesAnalyzed(15)
                    .firstHalfPercentage(40.0)
                    .secondHalfPercentage(60.0)
                    .strongerHalf("Second Half")
                    .pattern("Strong Finisher")
                    .build();

            when(halfAnalysisService.analyzeByHalf(teamName)).thenReturn(mockDto);

            // When
            ResponseEntity<?> response = halfAnalysisController.getHalfAnalysis(teamName);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isInstanceOf(HalfAnalysisDTO.class);
            HalfAnalysisDTO body = (HalfAnalysisDTO) response.getBody();
            assertThat(body.getTeamName()).isEqualTo(teamName);
            assertThat(body.getPattern()).isEqualTo("Strong Finisher");
        }

        @Test
        @DisplayName("Should return 500 on internal error")
        void shouldReturn500OnInternalError() {
            // Given
            String teamName = "Arsenal";
            when(halfAnalysisService.analyzeByHalf(teamName))
                    .thenThrow(new RuntimeException("Database connection failed"));

            // When
            ResponseEntity<?> response = halfAnalysisController.getHalfAnalysis(teamName);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("error")).isEqualTo("Internal Server Error");
        }

        @Test
        @DisplayName("Should include all DTO fields in response")
        void shouldIncludeAllDtoFieldsInResponse() {
            // Given
            String teamName = "Liverpool";
            HalfAnalysisDTO fullDto = HalfAnalysisDTO.builder()
                    .teamName(teamName)
                    .dataScope("Last 20 Matches")
                    .matchesAnalyzed(20)
                    .firstHalfGoalsAvg(0.85)
                    .secondHalfGoalsAvg(1.15)
                    .totalFirstHalfGoals(17)
                    .totalSecondHalfGoals(23)
                    .totalGoals(40)
                    .firstHalfPercentage(42.5)
                    .secondHalfPercentage(57.5)
                    .strongerHalf("Second Half")
                    .pattern("Balanced")
                    .winRateWhenLeadingHT(90.0)
                    .winRateWhenDrawingHT(50.0)
                    .winRateWhenLosingHT(15.0)
                    .comebackRate(15.0)
                    .matchesLeadingHT(10)
                    .matchesDrawingHT(6)
                    .matchesTrailingHT(4)
                    .firstHalfConcededAvg(0.35)
                    .secondHalfConcededAvg(0.55)
                    .confidence(0.95)
                    .firstHalfGoalDifferential(0.5)
                    .secondHalfGoalDifferential(0.6)
                    .anomalyDetected(false)
                    .build();

            when(halfAnalysisService.analyzeByHalf(teamName)).thenReturn(fullDto);

            // When
            ResponseEntity<?> response = halfAnalysisController.getHalfAnalysis(teamName);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isInstanceOf(HalfAnalysisDTO.class);
            HalfAnalysisDTO body = (HalfAnalysisDTO) response.getBody();

            assertThat(body.getTeamName()).isEqualTo(teamName);
            assertThat(body.getDataScope()).isEqualTo("Last 20 Matches");
            assertThat(body.getMatchesAnalyzed()).isEqualTo(20);
            assertThat(body.getFirstHalfGoalsAvg()).isEqualTo(0.85);
            assertThat(body.getSecondHalfGoalsAvg()).isEqualTo(1.15);
            assertThat(body.getTotalFirstHalfGoals()).isEqualTo(17);
            assertThat(body.getTotalSecondHalfGoals()).isEqualTo(23);
            assertThat(body.getTotalGoals()).isEqualTo(40);
            assertThat(body.getWinRateWhenLeadingHT()).isEqualTo(90.0);
            assertThat(body.getWinRateWhenDrawingHT()).isEqualTo(50.0);
            assertThat(body.getWinRateWhenLosingHT()).isEqualTo(15.0);
            assertThat(body.getComebackRate()).isEqualTo(15.0);
            assertThat(body.getConfidence()).isEqualTo(0.95);
            assertThat(body.isAnomalyDetected()).isFalse();
        }
    }
}

