package com.app.footballprediction.modeltraining;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import com.app.footballprediction.featureengineering.FeatureEngineeringService;
import com.app.footballprediction.model.Match;
import com.app.footballprediction.model.MatchFeatures;
import com.app.footballprediction.repository.MatchRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelTrainingService {

   private final MatchRepository matchRepository;
   private final FeatureEngineeringService featureEngineeringService;

   @Value("${model.output.path}")
   private String modelOutputPath;

   @Autowired(required = false)
   @Qualifier("trainedModel")
   private RandomForest trainedModel;

   @Autowired(required = false)
   @Qualifier("trainingHeader")
   private Instances trainingHeader;

   // ── Weka column indices (must match buildAttributes() order exactly) ──
   private static final int IDX_HOME_FORM         = 0;
   private static final int IDX_AWAY_FORM         = 1;
   private static final int IDX_HOME_GOALS_SCR    = 2;
   private static final int IDX_HOME_GOALS_CON    = 3;
   private static final int IDX_AWAY_GOALS_SCR    = 4;
   private static final int IDX_AWAY_GOALS_CON    = 5;
   private static final int IDX_HOME_TOTAL_GOALS  = 6;
   private static final int IDX_AWAY_TOTAL_GOALS  = 7;
   private static final int IDX_H2H_HOME_WIN      = 8;
   private static final int IDX_H2H_DRAW          = 9;
   private static final int IDX_H2H_AWAY_WIN      = 10;
   private static final int IDX_HOME_SHOTS        = 11;
   private static final int IDX_AWAY_SHOTS        = 12;
   private static final int IDX_HOME_CORNERS      = 13;
   private static final int IDX_AWAY_CORNERS      = 14;
   // Phase 3 features
   private static final int IDX_HOME_GOAL_DIFF    = 15;
   private static final int IDX_AWAY_GOAL_DIFF    = 16;
   private static final int IDX_HOME_OVERALL_FORM = 17;
   private static final int IDX_AWAY_OVERALL_FORM = 18;
   private static final int IDX_HOME_WIN_STREAK   = 19;
   private static final int IDX_AWAY_WIN_STREAK   = 20;
   private static final int IDX_HOME_UNBEATEN     = 21;
   private static final int IDX_AWAY_UNBEATEN     = 22;
   private static final int IDX_HOME_DAYS_REST    = 23;
   private static final int IDX_AWAY_DAYS_REST    = 24;
   private static final int IDX_LABEL             = 25;

   // ── Public API ────────────────────────────────────────────────────────

   /**
    * Full pipeline:
    * 1. Load all matches from DB
    * 2. Build feature vectors
    * 3. Temporal split — 80% train, 20% test (most recent)
    * 4. Build Weka Instances
    * 5. Train Random Forest
    * 6. Evaluate on test set
    * 7. Save model to disk
    */
   public String trainAndEvaluate() throws Exception {
      long start = System.currentTimeMillis();
      log.info("Starting model training pipeline...");

      List<Match> allMatches = matchRepository.findAllByOrderByMatchDateAsc();
      log.info("Training started. Matches in DB: {}", allMatches.size());

      if (allMatches.size() < 100) {
         throw new IllegalStateException(
               "Not enough data to train. Need at least 100 matches, " +
                     "found: " + allMatches.size() +
                     ". Make sure your CSVs are loaded.");
      }

      // ── Step 1: Build feature vectors ─────────────────────────
      List<MatchFeatures> allFeatures = new ArrayList<>();
      int skipped = 0;

      for (Match match : allMatches) {
         try {
            MatchFeatures features = featureEngineeringService.buildFeaturesForTraining(match);

            // Skip matches with zero history — start of season
            if (features.getHomeFormPoints() == 0.0 && features.getHomeGoalsScoredAvg() == 0.0) {
               skipped++;
               continue;
            }

            allFeatures.add(features);

         } catch (Exception e) {
            log.warn("Skipping match {}: {}", match.getId(), e.getMessage());
            skipped++;
         }
      }

      log.info("Feature vectors: {} usable, {} skipped",
            allFeatures.size(), skipped);

      // ── Step 2: Temporal split ─────────────────────────────────
      int splitIdx = (int) (allFeatures.size() * 0.8);
      List<MatchFeatures> trainSet = allFeatures.subList(0, splitIdx);
      List<MatchFeatures> testSet  = allFeatures.subList(splitIdx,
            allFeatures.size());

      log.info("Temporal split → train: {}, test: {}",
            trainSet.size(), testSet.size());

      // ── Step 3: Build Weka datasets ────────────────────────────
      ArrayList<Attribute> attributes = buildAttributes();
      Instances trainData = toWekaInstances(trainSet, attributes, "FootballTrain");
      Instances testData  = toWekaInstances(testSet,  attributes, "FootballTest");

      trainData.setClassIndex(IDX_LABEL);
      testData.setClassIndex(IDX_LABEL);

      // ── Step 4: Train Random Forest ────────────────────────────
      RandomForest rf = new RandomForest();
      rf.setNumIterations(100);   // 100 trees
      rf.setNumFeatures(4);       // sqrt(15) ≈ 4 features per split
      rf.setSeed(42);
      rf.buildClassifier(trainData);

      log.info("Random Forest trained.");

      // ── Step 5: Evaluate ───────────────────────────────────────
      Evaluation eval = new Evaluation(trainData);
      eval.evaluateModel(rf, testData);

      String report = buildEvaluationReport(eval,
            trainSet.size(),
            testSet.size());
      log.info("\n{}", report);

      // ── Step 6: Save to disk and cache in memory ───────────────
      saveModel(rf, trainData);
      this.trainedModel   = rf;       // update in-memory bean directly
      this.trainingHeader = trainData;

      long duration = System.currentTimeMillis() - start;
      log.info("Training complete in {} ms. Accuracy: {} .1f%", duration, eval.pctCorrect());

      return report;
   }

   /**
    * Predict outcome probabilities for a single match.
    * Returns double[3] → [P(HomeWin), P(Draw), P(AwayWin)]
    */
   public double[] predict(MatchFeatures features) throws Exception {
      if (trainedModel == null || trainingHeader == null) {
         throw new IllegalStateException(
               "Model not loaded. Call POST /api/model/train first.");
      }

      Instance instance = toWekaInstance(features, trainingHeader);
      instance.setDataset(trainingHeader);

      return trainedModel.distributionForInstance(instance);
   }

   /**
    * Converts probability array to a label.
    * Picks the class with the highest probability.
    */
   public String getPredictedLabel(double[] probes) {
      if (probes[0] >= probes[1] && probes[0] >= probes[2]) return "H";
      if (probes[1] >= probes[0] && probes[1] >= probes[2]) return "D";
      return "A";
   }

   public boolean isModelLoaded() {
      return trainedModel != null && trainingHeader != null;
   }

   // ── Weka dataset builders ─────────────────────────────────────────────

   /**
    * Defines the schema of the Weka dataset.
    * ORDER MATTERS — indices must match the IDX_ constants above exactly.
    */
   private ArrayList<Attribute> buildAttributes() {
      ArrayList<Attribute> attrs = new ArrayList<>();

      // Phase 1 & 2 numeric features (indices 0–14)
      attrs.add(new Attribute("homeFormPoints"));        // 0
      attrs.add(new Attribute("awayFormPoints"));        // 1
      attrs.add(new Attribute("homeGoalsScoredAvg"));    // 2
      attrs.add(new Attribute("homeGoalsConcededAvg"));  // 3
      attrs.add(new Attribute("awayGoalsScoredAvg"));    // 4
      attrs.add(new Attribute("awayGoalsConcededAvg"));  // 5
      attrs.add(new Attribute("homeTotalGoalsAvg"));     // 6
      attrs.add(new Attribute("awayTotalGoalsAvg"));     // 7
      attrs.add(new Attribute("h2hHomeWinRate"));        // 8
      attrs.add(new Attribute("h2hDrawRate"));           // 9
      attrs.add(new Attribute("h2hAwayWinRate"));        // 10
      attrs.add(new Attribute("homeShotsOnTargetAvg"));  // 11
      attrs.add(new Attribute("awayShotsOnTargetAvg"));  // 12
      attrs.add(new Attribute("homeCornersAvg"));        // 13
      attrs.add(new Attribute("awayCornersAvg"));        // 14

      // Phase 3 numeric features (indices 15–24)
      attrs.add(new Attribute("homeGoalDifference"));    // 15
      attrs.add(new Attribute("awayGoalDifference"));    // 16
      attrs.add(new Attribute("homeOverallFormPoints")); // 17
      attrs.add(new Attribute("awayOverallFormPoints")); // 18
      attrs.add(new Attribute("homeWinStreak"));         // 19
      attrs.add(new Attribute("awayWinStreak"));         // 20
      attrs.add(new Attribute("homeUnbeatenStreak"));    // 21
      attrs.add(new Attribute("awayUnbeatenStreak"));    // 22
      attrs.add(new Attribute("homeDaysRest"));          // 23
      attrs.add(new Attribute("awayDaysRest"));          // 24

      // Nominal label (index 25)
      ArrayList<String> labels = new ArrayList<>(List.of("H", "D", "A"));
      attrs.add(new Attribute("result", labels));        // 25

      return attrs;
   }

   /**
    * Converts a list of MatchFeatures into a Weka Instances dataset.
    */
   private Instances toWekaInstances(List<MatchFeatures> featuresList, ArrayList<Attribute> attributes, String name) {
      Instances dataset = new Instances(name, attributes, featuresList.size());
      dataset.setClassIndex(IDX_LABEL);

      for (MatchFeatures f : featuresList) {
         dataset.add(toWekaInstance(f, dataset));
      }

      return dataset;
   }

   /**
    * Converts a single MatchFeatures into a Weka Instance.
    * Uses safe() to guard against NaN/Infinity from edge cases.
    */
   private Instance toWekaInstance(MatchFeatures f, Instances dataset) {
      Instance inst = new DenseInstance(26); // 25 features + 1 label
      inst.setDataset(dataset);

      // Phase 1 & 2 features
      inst.setValue(IDX_HOME_FORM,        safe(f.getHomeFormPoints()));
      inst.setValue(IDX_AWAY_FORM,        safe(f.getAwayFormPoints()));
      inst.setValue(IDX_HOME_GOALS_SCR,   safe(f.getHomeGoalsScoredAvg()));
      inst.setValue(IDX_HOME_GOALS_CON,   safe(f.getHomeGoalsConcededAvg()));
      inst.setValue(IDX_AWAY_GOALS_SCR,   safe(f.getAwayGoalsScoredAvg()));
      inst.setValue(IDX_AWAY_GOALS_CON,   safe(f.getAwayGoalsConcededAvg()));
      inst.setValue(IDX_HOME_TOTAL_GOALS, safe(f.getHomeTotalGoalsAvg()));
      inst.setValue(IDX_AWAY_TOTAL_GOALS, safe(f.getAwayTotalGoalsAvg()));
      inst.setValue(IDX_H2H_HOME_WIN,     safe(f.getH2hHomeWinRate()));
      inst.setValue(IDX_H2H_DRAW,         safe(f.getH2hDrawRate()));
      inst.setValue(IDX_H2H_AWAY_WIN,     safe(f.getH2hAwayWinRate()));
      inst.setValue(IDX_HOME_SHOTS,       safe(f.getHomeShotsOnTargetAvg()));
      inst.setValue(IDX_AWAY_SHOTS,       safe(f.getAwayShotsOnTargetAvg()));
      inst.setValue(IDX_HOME_CORNERS,     safe(f.getHomeCornersAvg()));
      inst.setValue(IDX_AWAY_CORNERS,     safe(f.getAwayCornersAvg()));

      // Phase 3 features
      inst.setValue(IDX_HOME_GOAL_DIFF,    safe(f.getHomeGoalDifference()));
      inst.setValue(IDX_AWAY_GOAL_DIFF,    safe(f.getAwayGoalDifference()));
      inst.setValue(IDX_HOME_OVERALL_FORM, safe(f.getHomeOverallFormPoints()));
      inst.setValue(IDX_AWAY_OVERALL_FORM, safe(f.getAwayOverallFormPoints()));
      inst.setValue(IDX_HOME_WIN_STREAK,   safe(f.getHomeWinStreak()));
      inst.setValue(IDX_AWAY_WIN_STREAK,   safe(f.getAwayWinStreak()));
      inst.setValue(IDX_HOME_UNBEATEN,     safe(f.getHomeUnbeatenStreak()));
      inst.setValue(IDX_AWAY_UNBEATEN,     safe(f.getAwayUnbeatenStreak()));
      inst.setValue(IDX_HOME_DAYS_REST,    safe(f.getHomeDaysSinceLastMatch()));
      inst.setValue(IDX_AWAY_DAYS_REST,    safe(f.getAwayDaysSinceLastMatch()));

      // Label only set during training — null at prediction time
      if (f.getActualResult() != null) {
         inst.setValue(IDX_LABEL, f.getActualResult());
      }

      return inst;
   }

   // ── Model persistence ─────────────────────────────────────────────────

   private void saveModel(RandomForest model,
                          Instances header) throws IOException {
      File file = new File(modelOutputPath);
      file.getParentFile().mkdirs();

      try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
         oos.writeObject(model);
         oos.writeObject(header);  // schema saved alongside model
      }

      log.info("Model saved to {}", modelOutputPath);
   }

   public void loadModelFromDisk() throws IOException,
         ClassNotFoundException {
      File file = new File(modelOutputPath);
      if (!file.exists()) {
         throw new IllegalStateException(
               "Model file not found at " + modelOutputPath +
                     ". Call POST /api/model/train first.");
      }

      try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
         this.trainedModel   = (RandomForest) ois.readObject();
         this.trainingHeader = (Instances)    ois.readObject();
      }

      log.info("Model loaded from {}", modelOutputPath);
   }

   // ── Evaluation report ─────────────────────────────────────────────────

   private String buildEvaluationReport(Evaluation eval, int trainSize, int testSize) throws Exception {
      return "\n══════════════════════════════════════════\n" +
            "   MATCH OUTCOME PREDICTOR — EVALUATION   \n" +
            "══════════════════════════════════════════\n" +
            String.format("  Train set : %d matches%n", trainSize) +
            String.format("  Test set  : %d matches (most recent)%n",
                  testSize) +
            String.format("  Accuracy  : %.1f%%%n",
                  eval.pctCorrect()) +
            String.format("  Baseline  : ~45%% (always predict " +
                  "Home Win)%n") +
            "\n  Per-class breakdown:\n" +
            eval.toClassDetailsString("  ") +
            "\n  Confusion Matrix:\n" +
            eval.toMatrixString("  ") +
            "══════════════════════════════════════════\n";
   }

   // ── Utility ───────────────────────────────────────────────────────────

   private double safe(double val) {
      return Double.isNaN(val) || Double.isInfinite(val) ? 0.0 : val;
   }
}
