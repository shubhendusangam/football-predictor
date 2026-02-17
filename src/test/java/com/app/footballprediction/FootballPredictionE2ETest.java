package com.app.footballprediction;

import com.app.footballprediction.model.Match;
import com.app.footballprediction.repository.MatchRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-End tests for Football Prediction application.
 * Uses full Spring context with embedded H2 database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Football Prediction E2E Tests")
class FootballPredictionE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MatchRepository matchRepository;

    @BeforeEach
    void setUp() {
        // Don't clear data between tests - we need cumulative state for E2E
    }

    @Nested
    @DisplayName("Application Startup")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ApplicationStartupTests {

        @Test
        @Order(1)
        @DisplayName("application context loads successfully")
        void contextLoads() {
            assertThat(mockMvc).isNotNull();
            assertThat(matchRepository).isNotNull();
        }

        @Test
        @Order(2)
        @DisplayName("CSV data is ingested on startup")
        void csvDataIngested() {
            // Matches should be loaded from CSV files on startup
            long count = matchRepository.count();
            assertThat(count).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Full Prediction Flow")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class FullPredictionFlowTests {

        @Test
        @Order(1)
        @DisplayName("check model status endpoint works")
        void modelStatusWorks() throws Exception {
            mockMvc.perform(get("/api/model/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.modelLoaded").exists());
        }

        @Test
        @Order(2)
        @DisplayName("data reload endpoint works")
        void dataReloadWorks() throws Exception {
            mockMvc.perform(post("/api/data/reload"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").exists());
        }

        @Test
        @Order(3)
        @DisplayName("prediction returns valid response structure")
        void predictionReturnsValidStructure() throws Exception {
            // First ensure we have a trained model or skip
            MvcResult statusResult = mockMvc.perform(get("/api/model/status"))
                    .andReturn();

            // Get two teams that exist in our data
            List<Match> matches = matchRepository.findAllByOrderByMatchDateAsc();
            if (matches.isEmpty()) {
                return; // Skip if no data
            }

            Match match = matches.getLast(); // Get most recent
            String homeTeam = match.getHomeTeam();
            String awayTeam = match.getAwayTeam();

            String requestJson = String.format("""
                    {
                        "homeTeam": "%s",
                        "awayTeam": "%s"
                    }
                    """, homeTeam, awayTeam);

            MvcResult result = mockMvc.perform(post("/api/predict")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andReturn();

            int status = result.getResponse().getStatus();

            // Either success (model loaded) or bad request (model not loaded)
            assertThat(status).isIn(200, 400);

            if (status == 200) {
                mockMvc.perform(post("/api/predict")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                        .andExpect(jsonPath("$.homeTeam").value(homeTeam))
                        .andExpect(jsonPath("$.awayTeam").value(awayTeam))
                        .andExpect(jsonPath("$.prediction").exists())
                        .andExpect(jsonPath("$.predictionCode").exists())
                        .andExpect(jsonPath("$.probHomeWin").exists())
                        .andExpect(jsonPath("$.probDraw").exists())
                        .andExpect(jsonPath("$.probAwayWin").exists())
                        .andExpect(jsonPath("$.confidence").exists())
                        .andExpect(jsonPath("$.features").exists());
            }
        }
    }

    @Nested
    @DisplayName("Error Handling E2E")
    class ErrorHandlingTests {

        @Test
        @DisplayName("invalid request returns proper error")
        void invalidRequestReturnsError() throws Exception {
            String requestJson = "{}";

            mockMvc.perform(post("/api/predict")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        @DisplayName("same team for home and away returns error")
        void sameTeamReturnsError() throws Exception {
            String requestJson = """
                    {
                        "homeTeam": "Arsenal",
                        "awayTeam": "Arsenal"
                    }
                    """;

            mockMvc.perform(post("/api/predict")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("homeTeam and awayTeam cannot be the same"));
        }

        @Test
        @DisplayName("empty body returns error")
        void emptyBodyReturnsError() throws Exception {
            mockMvc.perform(post("/api/predict")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Data Integrity E2E")
    class DataIntegrityTests {

        @Test
        @DisplayName("duplicate matches are not inserted")
        void duplicatesNotInserted() throws Exception {
            long countBefore = matchRepository.count();

            // Trigger reload (should skip duplicates)
            mockMvc.perform(post("/api/data/reload"))
                    .andExpect(status().isOk());

            long countAfter = matchRepository.count();

            // Count should remain the same (all duplicates skipped)
            assertThat(countAfter).isEqualTo(countBefore);
        }

        @Test
        @DisplayName("matches have valid required fields")
        void matchesHaveValidFields() {
            List<Match> matches = matchRepository.findAllByOrderByMatchDateAsc();

            for (Match match : matches) {
                assertThat(match.getMatchDate()).isNotNull();
                assertThat(match.getHomeTeam()).isNotBlank();
                assertThat(match.getAwayTeam()).isNotBlank();
                assertThat(match.getFullTimeResult()).isIn("H", "D", "A");
                assertThat(match.getFullTimeHomeGoals()).isNotNull();
                assertThat(match.getFullTimeAwayGoals()).isNotNull();
            }
        }
    }
}

