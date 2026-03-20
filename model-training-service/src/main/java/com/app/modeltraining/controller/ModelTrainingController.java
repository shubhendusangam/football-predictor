package com.app.modeltraining.controller;

import com.app.modeltraining.dto.ModelInfoResponse;
import com.app.modeltraining.dto.TestResponse;
import com.app.modeltraining.dto.TrainingResponse;
import com.app.modeltraining.service.ModelTrainingService;
import com.app.modeltraining.service.PoissonModelTrainingService;
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
 * - POST /api/training/train - Train the outcome model
 * - POST /api/training/test - Test the outcome model
 * - POST /api/training/train-poisson - Train the Poisson score model
 * - POST /api/training/test-poisson - Test the Poisson score model
 * - GET /api/training/model-info - Get model information
 */
@RestController
@RequestMapping("/api/training")
@RequiredArgsConstructor
@Slf4j
public class ModelTrainingController {

    private final ModelTrainingService modelTrainingService;
    private final PoissonModelTrainingService poissonModelTrainingService;

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

    // ── Poisson Score Model ───────────────────────────────────────────

    /**
     * Train the Poisson score prediction model (Dixon-Coles method).
     *
     * @return TrainingResponse with training results
     */
    @PostMapping("/train-poisson")
    public ResponseEntity<TrainingResponse> trainPoissonModel() {
        try {
            log.info("Received request to train Poisson score model");
            long startTime = System.currentTimeMillis();

            String report = poissonModelTrainingService.trainPoissonModel();

            long duration = System.currentTimeMillis() - startTime;

            TrainingResponse response = TrainingResponse.builder()
                    .success(true)
                    .message("Poisson score model training completed successfully")
                    .report(report)
                    .trainingTimeMs(duration)
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            log.error("Poisson training failed due to insufficient data: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(TrainingResponse.builder()
                            .success(false)
                            .message("Poisson training failed: " + e.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("Poisson training failed with error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(TrainingResponse.builder()
                            .success(false)
                            .message("Poisson training failed: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Test the trained Poisson score prediction model.
     *
     * @return TestResponse with test results
     */
    @PostMapping("/test-poisson")
    public ResponseEntity<TestResponse> testPoissonModel() {
        try {
            log.info("Received request to test Poisson score model");

            String report = poissonModelTrainingService.testPoissonModel();

            TestResponse response = TestResponse.builder()
                    .success(true)
                    .message("Poisson score model testing completed successfully")
                    .report(report)
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            log.error("Poisson testing failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(TestResponse.builder()
                            .success(false)
                            .message("Poisson testing failed: " + e.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("Poisson testing failed with error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(TestResponse.builder()
                            .success(false)
                            .message("Poisson testing failed: " + e.getMessage())
                            .build());
        }
    }

    // ── Model Information ─────────────────────────────────────────────

    /**
     * Get model information (outcome + Poisson)
     *
     * @return ModelInfoResponse with model details
     */
    @GetMapping("/model-info")
    public ResponseEntity<ModelInfoResponse> getModelInfo() {
        try {
            log.info("Received request for model info");

            Map<String, Object> modelInfo = modelTrainingService.getModelInfo();
            modelInfo.put("poissonModel", poissonModelTrainingService.getModelInfo());

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

