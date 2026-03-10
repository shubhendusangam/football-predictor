package com.app.footballprediction.controller;

import com.app.footballprediction.dto.Top4RaceAnalysisDTO;
import com.app.footballprediction.dto.Top4RaceAnalysisDTO.TitleRaceSummary;
import com.app.footballprediction.dto.Top4RaceDTO;
import com.app.footballprediction.service.Top4RaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LeagueController endpoints.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LeagueController Unit Tests")
class LeagueControllerTest {

    @Mock
    private Top4RaceService top4RaceService;

    @InjectMocks
    private LeagueController leagueController;

    @Nested
    @DisplayName("GET /api/league/top4-race")
    class GetTop4RaceTests {

        @Test
        @DisplayName("Returns 200 with race analysis")
        void returnsRaceAnalysis() {
            // Given
            Top4RaceAnalysisDTO mockAnalysis = createMockAnalysis();
            when(top4RaceService.analyzeTop4Race(anyString(), any(LocalDate.class)))
                    .thenReturn(mockAnalysis);

            // When
            ResponseEntity<?> response = leagueController.getTop4Race(null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isInstanceOf(Top4RaceAnalysisDTO.class);
            Top4RaceAnalysisDTO body = (Top4RaceAnalysisDTO) response.getBody();
            assertThat(body.getSeason()).isEqualTo("2025-26");
            assertThat(body.getTeamsInRace()).hasSize(2);
        }

        @Test
        @DisplayName("Accepts season parameter")
        void acceptsSeasonParameter() {
            // Given
            Top4RaceAnalysisDTO mockAnalysis = createMockAnalysis();
            when(top4RaceService.analyzeTop4Race(anyString(), any(LocalDate.class)))
                    .thenReturn(mockAnalysis);

            // When
            ResponseEntity<?> response = leagueController.getTop4Race("2025-26");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Returns 500 on service error")
        @SuppressWarnings("unchecked")
        void returns500OnError() {
            // Given
            when(top4RaceService.analyzeTop4Race(anyString(), any(LocalDate.class)))
                    .thenThrow(new RuntimeException("Service error"));

            // When
            ResponseEntity<?> response = leagueController.getTop4Race(null);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isInstanceOf(Map.class);
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKey("error");
        }
    }

    @Nested
    @DisplayName("GET /api/league/top4-race/historical")
    class GetTop4RaceHistoricalTests {

        @Test
        @DisplayName("Returns historical analysis for specific date")
        void returnsHistoricalAnalysis() {
            // Given
            Top4RaceAnalysisDTO mockAnalysis = createMockAnalysis();
            when(top4RaceService.analyzeTop4Race(anyString(), any(LocalDate.class)))
                    .thenReturn(mockAnalysis);

            // When
            ResponseEntity<?> response = leagueController.getTop4RaceHistorical("2025-26", "2026-02-15");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isInstanceOf(Top4RaceAnalysisDTO.class);
        }

        @Test
        @DisplayName("Returns 500 on invalid date format")
        @SuppressWarnings("unchecked")
        void returns500OnInvalidDate() {
            // When
            ResponseEntity<?> response = leagueController.getTop4RaceHistorical("2025-26", "invalid-date");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKey("error");
        }
    }

    private Top4RaceAnalysisDTO createMockAnalysis() {
        List<Top4RaceDTO> teams = List.of(
                Top4RaceDTO.builder()
                        .teamName("Arsenal")
                        .currentPosition(1)
                        .points(70)
                        .gapToFirst(0)
                        .gapToFourth(-10)
                        .top4Probability(98.0)
                        .status("Champion")
                        .motivation("High")
                        .build(),
                Top4RaceDTO.builder()
                        .teamName("Liverpool")
                        .currentPosition(2)
                        .points(68)
                        .gapToFirst(2)
                        .gapToFourth(-8)
                        .top4Probability(95.0)
                        .status("UCL Safe")
                        .motivation("High")
                        .build()
        );

        TitleRaceSummary titleRace = TitleRaceSummary.builder()
                .leader("Arsenal")
                .contenders(3)
                .gapFirstToSecond(2)
                .decided(false)
                .intensity("Close Race")
                .build();

        return Top4RaceAnalysisDTO.builder()
                .season("2025-26")
                .asOfDate(LocalDate.of(2026, 3, 10))
                .teamsInRace(teams)
                .pointsForSafety(72)
                .totalMatchesInSeason(38)
                .matchdaysCompleted(30)
                .seasonProgressPercent(78.9)
                .titleRace(titleRace)
                .lastUpdated("2026-03-10T00:00:00")
                .build();
    }
}

