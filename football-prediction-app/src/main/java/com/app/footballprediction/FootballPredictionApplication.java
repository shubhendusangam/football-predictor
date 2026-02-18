package com.app.footballprediction;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.app.footballprediction.modeltraining.ModelTrainingService;
import com.app.footballprediction.service.CsvIngestionService;

@SpringBootApplication(scanBasePackages = {"com.app.footballprediction", "com.app.common"})
@EnableJpaRepositories(basePackages = {"com.app.footballprediction", "com.app.common"})
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class FootballPredictionApplication implements ApplicationRunner {

   private final CsvIngestionService csvIngestionService;
   private final ModelTrainingService modelTrainingService;

   public static void main(String[] args) {
      SpringApplication.run(FootballPredictionApplication.class, args);
   }

   /**
    * Runs after Spring context is fully loaded.
    * <p>
    * Order of execution:
    * 1. WekaModelConfig @Beans fire first (Spring handles this)
    * 2. This ApplicationRunner fires second
    * <p>
    * So by the time we reach run(), trainedModel and trainingHeader
    * are already injected into ModelTrainingService if a saved
    * model file exists on disk.
    */
   @Override
   public void run(@NonNull ApplicationArguments args) throws Exception {
      printBanner();

      // ── Step 1: Ingest CSV data ────────────────────────────────
      log.info("► Step 1: Ingesting CSV data...");
      csvIngestionService.ingestAll();

      // ── Step 2: Model loading / training ──────────────────────
      log.info("► Step 2: Checking model...");

      if (modelTrainingService.isModelLoaded()) {
         // WekaModelConfig already loaded it from disk at startup
         log.info("  ✓ Model loaded by Spring at startup. Ready to predict!");

      } else {
         // No saved model file exists — train from scratch
         log.info("  No saved model found. Training now...");
         log.info("  This may take 30–60 seconds...");

         String report = modelTrainingService.trainAndEvaluate();
         log.info(report);
      }

      printReadyBanner();
   }

   // ── Private helpers ───────────────────────────────────────────

   private void printBanner() {
      log.info("═══════════════════════════════════════════════════");
      log.info("  ⚽  Football Match Outcome Predictor             ");
      log.info("      Java + Spring Boot + Weka Random Forest      ");
      log.info("═══════════════════════════════════════════════════");
   }

   private void printReadyBanner() {
      log.info("═══════════════════════════════════════════════════");
      log.info("  ✓  Application ready                            ");
      log.info("─────────────────────────────────────────────────  ");
      log.info("  Endpoints:                                       ");
      log.info("  POST /api/predict       → predict a match       ");
      log.info("  POST /api/model/train   → retrain the model     ");
      log.info("  GET  /api/model/status  → check model status    ");
      log.info("  POST /api/data/reload   → re-ingest CSV files   ");
      log.info("─────────────────────────────────────────────────  ");
      log.info("  Tools:                                           ");
      log.info("  http://localhost:8080/h2-console  → view DB     ");
      log.info("═══════════════════════════════════════════════════");
   }
}