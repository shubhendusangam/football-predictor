package com.app.footballprediction;

import com.app.footballprediction.controller.PredictionController;
import com.app.footballprediction.featureengineering.FeatureEngineeringService;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import com.app.footballprediction.repository.MatchRepository;
import com.app.footballprediction.service.CsvIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Main application context tests.
 * Verifies that all beans are properly configured and wired.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Application Context Tests")
class FootballPredictionApplicationTests {

    @Autowired(required = false)
    private PredictionController predictionController;

    @Autowired(required = false)
    private FeatureEngineeringService featureEngineeringService;

    @Autowired(required = false)
    private ModelTrainingService modelTrainingService;

    @Autowired(required = false)
    private CsvIngestionService csvIngestionService;

    @Autowired(required = false)
    private MatchRepository matchRepository;

    @Test
    @DisplayName("application context loads successfully")
    void contextLoads() {
        // Context loads without errors
    }

    @Test
    @DisplayName("PredictionController bean is created")
    void predictionControllerBeanCreated() {
        assertThat(predictionController).isNotNull();
    }

    @Test
    @DisplayName("FeatureEngineeringService bean is created")
    void featureEngineeringServiceBeanCreated() {
        assertThat(featureEngineeringService).isNotNull();
    }

    @Test
    @DisplayName("ModelTrainingService bean is created")
    void modelTrainingServiceBeanCreated() {
        assertThat(modelTrainingService).isNotNull();
    }

    @Test
    @DisplayName("CsvIngestionService bean is created")
    void csvIngestionServiceBeanCreated() {
        assertThat(csvIngestionService).isNotNull();
    }

    @Test
    @DisplayName("MatchRepository bean is created")
    void matchRepositoryBeanCreated() {
        assertThat(matchRepository).isNotNull();
    }
}
