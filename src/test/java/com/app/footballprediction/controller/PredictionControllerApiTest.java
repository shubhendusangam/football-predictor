package com.app.footballprediction.controller;

import com.app.footballprediction.featureengineering.FeatureEngineeringService;
import com.app.footballprediction.model.MatchFeatures;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import com.app.footballprediction.service.CsvIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * API Tests for PredictionController using MockMvc.
 */
@WebMvcTest(PredictionController.class)
@ExtendWith(MockitoExtension.class)
@DisplayName("PredictionController API Tests")
class PredictionControllerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeatureEngineeringService featureEngineeringService;

    @MockitoBean
    private ModelTrainingService modelTrainingService;

    @MockitoBean
    private CsvIngestionService csvIngestionService;

    @Nested
    @DisplayName("POST /api/predict")
    class PredictEndpointTests {

        @Test
        @DisplayName("returns prediction for valid request")
        void returnsValidPrediction() throws Exception {
            // Given
            MatchFeatures features = MatchFeatures.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .homeFormPoints(2.4)
                    .awayFormPoints(1.8)
                    .h2hHomeWinRate(0.45)
                    .h2hDrawRate(0.30)
                    .h2hAwayWinRate(0.25)
                    .build();

            double[] probabilities = {0.55, 0.25, 0.20};

            when(featureEngineeringService.buildFeaturesForPrediction("Arsenal", "Chelsea"))
                    .thenReturn(features);
            when(modelTrainingService.predict(any())).thenReturn(probabilities);
            when(modelTrainingService.getPredictedLabel(probabilities)).thenReturn("H");

            String requestJson = """
                    {
                        "homeTeam": "Arsenal",
                        "awayTeam": "Chelsea"
                    }
                    """;

            // When/Then
            mockMvc.perform(post("/api/predict")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.homeTeam").value("Arsenal"))
                    .andExpect(jsonPath("$.awayTeam").value("Chelsea"))
                    .andExpect(jsonPath("$.prediction").value("HOME_WIN"))
                    .andExpect(jsonPath("$.predictionCode").value("H"))
                    .andExpect(jsonPath("$.probHomeWin").value(0.55))
                    .andExpect(jsonPath("$.probDraw").value(0.25))
                    .andExpect(jsonPath("$.probAwayWin").value(0.20))
                    .andExpect(jsonPath("$.confidence").exists())
                    .andExpect(jsonPath("$.features").exists());
        }

        @Test
        @DisplayName("returns 400 when homeTeam is missing")
        void returns400WhenHomeTeamMissing() throws Exception {
            String requestJson = """
                    {
                        "awayTeam": "Chelsea"
                    }
                    """;

            mockMvc.perform(post("/api/predict")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("homeTeam is required"));
        }

        @Test
        @DisplayName("returns 400 when awayTeam is missing")
        void returns400WhenAwayTeamMissing() throws Exception {
            String requestJson = """
                    {
                        "homeTeam": "Arsenal"
                    }
                    """;

            mockMvc.perform(post("/api/predict")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("awayTeam is required"));
        }

        @Test
        @DisplayName("returns 400 when homeTeam and awayTeam are the same")
        void returns400WhenSameTeam() throws Exception {
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
        @DisplayName("returns 400 when model not loaded")
        void returns400WhenModelNotLoaded() throws Exception {
            MatchFeatures features = MatchFeatures.builder()
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .build();

            when(featureEngineeringService.buildFeaturesForPrediction(any(), any()))
                    .thenReturn(features);
            when(modelTrainingService.predict(any()))
                    .thenThrow(new IllegalStateException("Model not loaded"));

            String requestJson = """
                    {
                        "homeTeam": "Arsenal",
                        "awayTeam": "Chelsea"
                    }
                    """;

            mockMvc.perform(post("/api/predict")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Model not loaded"));
        }

        @Test
        @DisplayName("returns 400 when homeTeam is blank")
        void returns400WhenHomeTeamBlank() throws Exception {
            String requestJson = """
                    {
                        "homeTeam": "   ",
                        "awayTeam": "Chelsea"
                    }
                    """;

            mockMvc.perform(post("/api/predict")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("homeTeam is required"));
        }
    }

    @Nested
    @DisplayName("GET /api/model/status")
    class ModelStatusTests {

        @Test
        @DisplayName("returns loaded status when model is ready")
        void returnsLoadedStatus() throws Exception {
            when(modelTrainingService.isModelLoaded()).thenReturn(true);

            mockMvc.perform(get("/api/model/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.modelLoaded").value(true))
                    .andExpect(jsonPath("$.hint").value("Ready to predict. Call POST /api/predict"));
        }

        @Test
        @DisplayName("returns not loaded status when model is missing")
        void returnsNotLoadedStatus() throws Exception {
            when(modelTrainingService.isModelLoaded()).thenReturn(false);

            mockMvc.perform(get("/api/model/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.modelLoaded").value(false))
                    .andExpect(jsonPath("$.hint").value("Model not loaded. Call POST /api/model/train"));
        }
    }

    @Nested
    @DisplayName("POST /api/model/train")
    class ModelTrainTests {

        @Test
        @DisplayName("returns success with report on successful training")
        void returnsSuccessOnTraining() throws Exception {
            String report = "Training complete. Accuracy: 55%";
            when(modelTrainingService.trainAndEvaluate()).thenReturn(report);

            mockMvc.perform(post("/api/model/train"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.report").value(report));
        }

        @Test
        @DisplayName("returns 400 when not enough data for training")
        void returns400WhenNotEnoughData() throws Exception {
            when(modelTrainingService.trainAndEvaluate())
                    .thenThrow(new IllegalStateException("Not enough data to train"));

            mockMvc.perform(post("/api/model/train"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Not enough data to train"));
        }

        @Test
        @DisplayName("returns 500 on unexpected error")
        void returns500OnError() throws Exception {
            when(modelTrainingService.trainAndEvaluate())
                    .thenThrow(new RuntimeException("Unexpected error"));

            mockMvc.perform(post("/api/model/train"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("Unexpected error"));
        }
    }

    @Nested
    @DisplayName("POST /api/data/reload")
    class DataReloadTests {

        @Test
        @DisplayName("returns success on data reload")
        void returnsSuccessOnReload() throws Exception {
            mockMvc.perform(post("/api/data/reload"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CSV data reloaded successfully"));
        }
    }
}
