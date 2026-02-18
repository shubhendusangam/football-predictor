package com.app.modeltraining.controller;

import com.app.modeltraining.dto.ModelInfoResponse;
import com.app.modeltraining.dto.TestResponse;
import com.app.modeltraining.dto.TrainingResponse;
import com.app.modeltraining.service.ModelTrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API Controller for Model Training and Testing
 *
 * Endpoints:
 * - POST /api/training/train - Train the model
 * - POST /api/training/test - Test the model
 * - GET /api/training/model-info - Get model information
 */
@RestController
@RequestMapping("/api/training")
@RequiredArgsConstructor
@Slf4j
public class ModelTrainingController {

    private final ModelTrainingService modelTrainingService;

    /**
     * Train the machine learning model
     *
     * @return TrainingResponse with training results
     */
    @PostMapping("/train")
    public ResponseEntity<TrainingResponse> trainModel() {
        try {
            log.info("Received request to train model");
            long startTime = System.currentTimeMillis();

            String report = modelTrainingService.trainModel();

            long duration = System.currentTimeMillis() - startTime;

            TrainingResponse response = TrainingResponse.builder()
                    .success(true)
                    .message("Model training completed successfully")
                    .report(report)
                    .trainingTimeMs(duration)
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            log.error("Training failed due to insufficient data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(TrainingResponse.builder()
                            .success(false)
                            .message("Training failed: " + e.getMessage())
                            .build());

        } catch (Exception e) {
            log.error("Training failed with error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(TrainingResponse.builder()
                            .success(false)
                            .message("Training failed: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Test the trained model
     *
     * @return TestResponse with test results
     */
    @PostMapping("/test")
    public ResponseEntity<TestResponse> testModel() {
        try {
            log.info("Received request to test model");

            String report = modelTrainingService.testModel();

            TestResponse response = TestResponse.builder()
                    .success(true)
                    .message("Model testing completed successfully")
                    .report(report)
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            log.error("Testing failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(TestResponse.builder()
                            .success(false)
                            .message("Testing failed: " + e.getMessage())
                            .build());

        } catch (Exception e) {
            log.error("Testing failed with error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(TestResponse.builder()
                            .success(false)
                            .message("Testing failed: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Get model information
     *
     * @return ModelInfoResponse with model details
     */
    @GetMapping("/model-info")
    public ResponseEntity<ModelInfoResponse> getModelInfo() {
        try {
            log.info("Received request for model info");

            Map<String, Object> modelInfo = modelTrainingService.getModelInfo();

            ModelInfoResponse response = ModelInfoResponse.builder()
                    .success(true)
                    .modelInfo(modelInfo)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get model info", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ModelInfoResponse.builder()
                            .success(false)
                            .build());
        }
    }
}

