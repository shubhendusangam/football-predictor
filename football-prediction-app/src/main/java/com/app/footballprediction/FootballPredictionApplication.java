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

import com.app.common.repository.MatchRepository;
import com.app.common.repository.SeasonTeamStatsRepository;
import com.app.footballprediction.modeltraining.ModelTrainingService;
import com.app.footballprediction.service.CsvIngestionService;
import com.app.footballprediction.service.MatchCompletionService;
import com.app.footballprediction.service.ModelSelfTrainingService;
import com.app.footballprediction.service.PoissonSelfTrainingService;

import java.util.List;

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
   private final ModelSelfTrainingService modelSelfTrainingService;
   private final PoissonSelfTrainingService poissonSelfTrainingService;
   private final MatchCompletionService matchCompletionService;
   private final MatchRepository matchRepository;
   private final SeasonTeamStatsRepository seasonTeamStatsRepository;

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

      // ── Step 1.5: Backfill NULL season values ─────────────────
      // Matches loaded before season-extraction was added, or via older
      // API sync code, may have season = NULL. Derive from matchDate.
      int seasonsBackfilled = csvIngestionService.backfillMissingSeasons();
      if (seasonsBackfilled > 0) {
         log.info("📅 Backfilled season for {} matches", seasonsBackfilled);
      }

      // ── Step 1.6: Update fouls data for existing matches ───────
      int foulsUpdated = csvIngestionService.updateFoulsData();
      log.debug("Fouls data update: {} matches updated", foulsUpdated);

      // ── Step 1.7: Enrich matches with missing statistics ───────
      // Matches inserted via API polling lack detailed stats (shots, corners, etc.)
      // This re-reads CSV files to fill in any missing statistics
      int statsEnriched = csvIngestionService.enrichMissingStats();
      log.debug("Stats enrichment: {} matches enriched", statsEnriched);

      // ── Step 1.8: Compute Season Team Stats / Elo ratings ─────
      computeSeasonStatsIfNeeded();

      // ── Step 2: Model loading / training ──────────────────────
      log.info("🤖 Initializing ML model...");

      if (!modelTrainingEnabled) {
         log.info("   ⚠ Model training disabled (test mode)");
      } else if (modelTrainingService.isModelLoaded()) {
         log.info("   ✓ Model loaded from disk");
      } else {
         log.info("   ⏳ Training new model (30-60s)...");
         modelSelfTrainingService.trainWithHistory("STARTUP_INITIAL");
         log.info("   ✓ Model trained");
      }

      // ── Step 3: Poisson score model ───────────────────────────
      if (modelTrainingEnabled) {
         log.info("🎯 Initializing Poisson score model...");
         poissonSelfTrainingService.trainIfMissing();
      }

      printReadyBanner();
   }

   // ── Private helpers ───────────────────────────────────────────

   /**
    * Compute Elo ratings and season team stats for all seasons if the
    * season_team_stats table is empty. Skips if already populated
    * (idempotent across restarts).
    */
   private void computeSeasonStatsIfNeeded() {
      long existingStats = seasonTeamStatsRepository.count();
      if (existingStats > 0) {
         log.info("📊 Season team stats already computed ({} records). Skipping.", existingStats);
         return;
      }

      List<String> seasons = matchRepository.findAllSeasons();
      if (seasons.isEmpty()) {
         log.warn("No seasons found in database — skipping stats computation.");
         return;
      }

      log.info("📊 Computing Elo ratings & team stats for {} seasons...", seasons.size());
      long start = System.currentTimeMillis();

      // Process seasons in chronological order (oldest first) so Elo flows correctly
      List<String> chronological = seasons.stream().sorted().toList();
      int processed = 0;
      for (String season : chronological) {
         try {
            matchCompletionService.recalculateSeasonStats(season);
            processed++;
            if (processed % 5 == 0) {
               log.info("   ... processed {}/{} seasons", processed, chronological.size());
            }
         } catch (Exception e) {
            log.error("Failed to compute stats for season {}: {}", season, e.getMessage());
         }
      }

      long duration = System.currentTimeMillis() - start;
      long totalStats = seasonTeamStatsRepository.count();
      log.info("✅ Season team stats computed: {} records across {} seasons in {}ms",
            totalStats, processed, duration);
   }


   private void printStartupBanner() {
      log.info("⚽ AI Football Match Predictor v2.0 — Starting...");
   }

   private void printReadyBanner() {
      log.info("✅ Application Ready — http://localhost:8080");
   }
}