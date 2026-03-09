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
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.beans.factory.annotation.Value;

import com.app.footballprediction.modeltraining.ModelTrainingService;
import com.app.footballprediction.service.CsvIngestionService;

@SpringBootApplication(scanBasePackages = {"com.app.footballprediction", "com.app.common"})
@EnableJpaRepositories(basePackages = {"com.app.footballprediction", "com.app.common"})
@EntityScan(basePackages = {"com.app.footballprediction", "com.app.common"})
@EnableScheduling
@EnableAsync
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

      // ── Step 1.5: Update fouls data for existing matches ───────
      int foulsUpdated = csvIngestionService.updateFoulsData();
      log.debug("Fouls data update: {} matches updated", foulsUpdated);

      // ── Step 1.6: Enrich matches with missing statistics ───────
      // Matches inserted via API polling lack detailed stats (shots, corners, etc.)
      // This re-reads CSV files to fill in any missing statistics
      int statsEnriched = csvIngestionService.enrichMissingStats();
      log.debug("Stats enrichment: {} matches enriched", statsEnriched);

      // ── Step 2: Model loading / training ──────────────────────
      log.info("🤖 Initializing ML model...");

      if (!modelTrainingEnabled) {
         log.info("   ⚠ Model training disabled (test mode)");
      } else if (modelTrainingService.isModelLoaded()) {
         log.info("   ✓ Model loaded from disk");
      } else {
         log.info("   ⏳ Training new model (30-60s)...");
         modelTrainingService.trainAndEvaluate();
         log.info("   ✓ Model trained");
      }

      printReadyBanner();
   }

   // ── Private helpers ───────────────────────────────────────────

   private void printStartupBanner() {
      log.info("⚽ AI Football Match Predictor v2.0 — Starting...");
   }

   private void printReadyBanner() {
      log.info("✅ Application Ready — http://localhost:8080");
   }
}