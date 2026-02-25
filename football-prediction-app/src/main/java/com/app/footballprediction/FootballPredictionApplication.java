package com.app.footballprediction;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.beans.factory.annotation.Value;

import com.app.footballprediction.modeltraining.ModelTrainingService;
import com.app.footballprediction.service.CsvIngestionService;

@SpringBootApplication(scanBasePackages = {"com.app.footballprediction", "com.app.common"})
@EnableJpaRepositories(basePackages = {"com.app.footballprediction", "com.app.common"})
@EntityScan(basePackages = {"com.app.footballprediction", "com.app.common"})
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class FootballPredictionApplication implements ApplicationRunner {

   private final CsvIngestionService csvIngestionService;
   private final ModelTrainingService modelTrainingService;

   @Value("${model.training.enabled:true}")
   private boolean modelTrainingEnabled;

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
      printStartupBanner();

      // ── Step 1: Ingest CSV data ────────────────────────────────
      log.info("📂 Loading historical match data...");
      csvIngestionService.ingestAll();
      log.info("   ✓ Match data loaded successfully");

      // ── Step 1.5: Update fouls data for existing matches ───────
      log.info("📊 Updating fouls/discipline data...");
      int foulsUpdated = csvIngestionService.updateFoulsData();
      if (foulsUpdated > 0) {
         log.info("   ✓ Updated {} matches with fouls data", foulsUpdated);
      } else {
         log.info("   ✓ Fouls data already up to date");
      }

      // ── Step 2: Model loading / training ──────────────────────
      log.info("🤖 Initializing ML model...");

      if (!modelTrainingEnabled) {
         log.info("   ⚠ Model training disabled (test mode)");
      } else if (modelTrainingService.isModelLoaded()) {
         log.info("   ✓ Model loaded successfully");
      } else {
         log.info("   ⏳ Training new model (30-60s)...");
         modelTrainingService.trainAndEvaluate();
         log.info("   ✓ Model trained");
      }

      printReadyBanner();
   }

   // ── Private helpers ───────────────────────────────────────────

   private void printStartupBanner() {
      log.info("");
      log.info("╔══════════════════════════════════════════════════╗");
      log.info("║  ⚽ AI Football Match Predictor                  ║");
      log.info("║  Version 2.0 | Spring Boot + Weka ML             ║");
      log.info("╚══════════════════════════════════════════════════╝");
      log.info("");
   }

   private void printReadyBanner() {
      log.info("");
      log.info("┌──────────────────────────────────────────────────┐");
      log.info("│  ✅ Application Ready                            │");
      log.info("├──────────────────────────────────────────────────┤");
      log.info("│  🌐 http://localhost:8080                        │");
      log.info("│  📊 http://localhost:8080/h2-console             │");
      log.info("└──────────────────────────────────────────────────┘");
      log.info("");
   }
}