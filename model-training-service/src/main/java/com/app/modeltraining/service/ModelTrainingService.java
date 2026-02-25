package com.app.modeltraining.service;

import com.app.common.util.PredictionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.io.*;
import java.util.*;

import com.app.modeltraining.model.Match;
import com.app.modeltraining.model.MatchFeatures;
import com.app.modeltraining.repository.MatchRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelTrainingService {

   private final MatchRepository matchRepository;
   private final FeatureEngineeringService featureEngineeringService;

   @Value("${model.output.path}")
   private String modelOutputPath;

   @Value("${model.crossvalidation.enabled:true}")
   private boolean crossValidationEnabled;

   @Value("${model.crossvalidation.folds:10}")
   private int crossValidationFolds;

   @Value("${model.training.min-matches:100}")
   private int minMatches;

   @Value("${model.training.train-split:0.8}")
   private double trainSplit;

   // ── Weka column indices ──
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
   // Phase 5 features (Possession Proxy)
   private static final int IDX_HOME_POSSESSION   = 25;
   private static final int IDX_AWAY_POSSESSION   = 26;
   private static final int IDX_LABEL             = 27;

   /**
    * Train model with temporal split and evaluation
    */
   public String trainModel() throws Exception {
      long start = System.currentTimeMillis();
      log.info("Starting model training pipeline...");

      List<Match> allMatches = matchRepository.findAllByOrderByMatchDateAsc();
      log.info("Training started. Matches in DB: {}", allMatches.size());

      if (allMatches.size() < minMatches) {
         throw new IllegalStateException(
               "Not enough data to train. Need at least " + minMatches + " matches, " +
                     "found: " + allMatches.size());
      }

      // Build feature vectors
      List<MatchFeatures> allFeatures = new ArrayList<>();
      int skipped = 0;

      for (Match match : allMatches) {
         try {
            MatchFeatures features = featureEngineeringService.buildFeaturesForTraining(match);
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

      log.info("Feature vectors: {} usable, {} skipped", allFeatures.size(), skipped);

      // Temporal split
      int splitIdx = (int) (allFeatures.size() * trainSplit);
      List<MatchFeatures> trainSet = allFeatures.subList(0, splitIdx);
      List<MatchFeatures> testSet  = allFeatures.subList(splitIdx, allFeatures.size());

      log.info("Temporal split → train: {}, test: {}", trainSet.size(), testSet.size());

      // Build Weka datasets
      ArrayList<Attribute> attributes = buildAttributes();
      Instances trainData = toWekaInstances(trainSet, attributes, "FootballTrain");
      Instances testData  = toWekaInstances(testSet,  attributes, "FootballTest");

      trainData.setClassIndex(IDX_LABEL);
      testData.setClassIndex(IDX_LABEL);

      // Train Random Forest
      RandomForest rf = new RandomForest();
      rf.setNumIterations(100);
      rf.setNumFeatures(5);
      rf.setSeed(42);
      rf.buildClassifier(trainData);

      log.info("Random Forest trained.");

      // Evaluate
      Evaluation eval = new Evaluation(trainData);
      eval.evaluateModel(rf, testData);

      String report = buildEvaluationReport(eval, trainSet.size(), testSet.size());
      log.info("\n{}", report);

      // Save model
      saveModel(rf, trainData);

      long duration = System.currentTimeMillis() - start;
      log.info("Training complete in {} ms. Accuracy: {}", duration, String.format("%.1f%%", eval.pctCorrect()));

      return report;
   }

   /**
    * Test model against test dataset
    */
   public String testModel() throws Exception {
      log.info("Testing model...");

      // Load model
      File modelFile = new File(modelOutputPath);
      if (!modelFile.exists()) {
         throw new IllegalStateException("Model file not found. Please train the model first.");
      }

      RandomForest model;
      Instances trainingHeader;

      try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelFile))) {
         model = (RandomForest) ois.readObject();
         trainingHeader = (Instances) ois.readObject();
      }

      // Build test dataset
      List<Match> allMatches = matchRepository.findAllByOrderByMatchDateAsc();
      List<MatchFeatures> allFeatures = new ArrayList<>();

      for (Match match : allMatches) {
         try {
            MatchFeatures features = featureEngineeringService.buildFeaturesForTraining(match);
            if (features.getHomeFormPoints() == 0.0 && features.getHomeGoalsScoredAvg() == 0.0) {
               continue;
            }
            allFeatures.add(features);
         } catch (Exception e) {
            log.warn("Skipping match for testing: {}", e.getMessage());
         }
      }

      // Use last 20% as test set
      int splitIdx = (int) (allFeatures.size() * trainSplit);
      List<MatchFeatures> testSet = allFeatures.subList(splitIdx, allFeatures.size());

      ArrayList<Attribute> attributes = buildAttributes();
      Instances testData = toWekaInstances(testSet, attributes, "FootballTest");
      testData.setClassIndex(IDX_LABEL);

      // Evaluate
      Evaluation eval = new Evaluation(trainingHeader);
      eval.evaluateModel(model, testData);

      String report = buildTestReport(eval, testSet.size());
      log.info("\n{}", report);

      return report;
   }

   /**
    * Get model information
    */
   public Map<String, Object> getModelInfo() {
      Map<String, Object> info = new HashMap<>();

      File modelFile = new File(modelOutputPath);
      info.put("modelExists", modelFile.exists());
      info.put("modelPath", modelOutputPath);

      if (modelFile.exists()) {
         info.put("modelSize", modelFile.length());
         info.put("lastModified", new Date(modelFile.lastModified()));
      }

      List<Match> matches = matchRepository.findAllByOrderByMatchDateAsc();
      info.put("totalMatches", matches.size());

      return info;
   }

   // ── Helper methods ──

   private ArrayList<Attribute> buildAttributes() {
      ArrayList<Attribute> attrs = new ArrayList<>();

      attrs.add(new Attribute("homeFormPoints"));
      attrs.add(new Attribute("awayFormPoints"));
      attrs.add(new Attribute("homeGoalsScoredAvg"));
      attrs.add(new Attribute("homeGoalsConcededAvg"));
      attrs.add(new Attribute("awayGoalsScoredAvg"));
      attrs.add(new Attribute("awayGoalsConcededAvg"));
      attrs.add(new Attribute("homeTotalGoalsAvg"));
      attrs.add(new Attribute("awayTotalGoalsAvg"));
      attrs.add(new Attribute("h2hHomeWinRate"));
      attrs.add(new Attribute("h2hDrawRate"));
      attrs.add(new Attribute("h2hAwayWinRate"));
      attrs.add(new Attribute("homeShotsOnTargetAvg"));
      attrs.add(new Attribute("awayShotsOnTargetAvg"));
      attrs.add(new Attribute("homeCornersAvg"));
      attrs.add(new Attribute("awayCornersAvg"));
      attrs.add(new Attribute("homeGoalDifference"));
      attrs.add(new Attribute("awayGoalDifference"));
      attrs.add(new Attribute("homeOverallFormPoints"));
      attrs.add(new Attribute("awayOverallFormPoints"));
      attrs.add(new Attribute("homeWinStreak"));
      attrs.add(new Attribute("awayWinStreak"));
      attrs.add(new Attribute("homeUnbeatenStreak"));
      attrs.add(new Attribute("awayUnbeatenStreak"));
      attrs.add(new Attribute("homeDaysRest"));
      attrs.add(new Attribute("awayDaysRest"));

      // Phase 5: Possession Proxy
      attrs.add(new Attribute("homePossessionProxy"));
      attrs.add(new Attribute("awayPossessionProxy"));

      ArrayList<String> labels = new ArrayList<>(List.of("H", "D", "A"));
      attrs.add(new Attribute("result", labels));

      return attrs;
   }

   private Instances toWekaInstances(List<MatchFeatures> featuresList, ArrayList<Attribute> attributes, String name) {
      Instances dataset = new Instances(name, attributes, featuresList.size());
      dataset.setClassIndex(IDX_LABEL);

      for (MatchFeatures f : featuresList) {
         dataset.add(toWekaInstance(f, dataset));
      }

      return dataset;
   }

   private Instance toWekaInstance(MatchFeatures f, Instances dataset) {
      Instance inst = new DenseInstance(28); // 27 features + 1 label
      inst.setDataset(dataset);

      inst.setValue(IDX_HOME_FORM,        PredictionUtils.safe(f.getHomeFormPoints()));
      inst.setValue(IDX_AWAY_FORM,        PredictionUtils.safe(f.getAwayFormPoints()));
      inst.setValue(IDX_HOME_GOALS_SCR,   PredictionUtils.safe(f.getHomeGoalsScoredAvg()));
      inst.setValue(IDX_HOME_GOALS_CON,   PredictionUtils.safe(f.getHomeGoalsConcededAvg()));
      inst.setValue(IDX_AWAY_GOALS_SCR,   PredictionUtils.safe(f.getAwayGoalsScoredAvg()));
      inst.setValue(IDX_AWAY_GOALS_CON,   PredictionUtils.safe(f.getAwayGoalsConcededAvg()));
      inst.setValue(IDX_HOME_TOTAL_GOALS, PredictionUtils.safe(f.getHomeTotalGoalsAvg()));
      inst.setValue(IDX_AWAY_TOTAL_GOALS, PredictionUtils.safe(f.getAwayTotalGoalsAvg()));
      inst.setValue(IDX_H2H_HOME_WIN,     PredictionUtils.safe(f.getH2hHomeWinRate()));
      inst.setValue(IDX_H2H_DRAW,         PredictionUtils.safe(f.getH2hDrawRate()));
      inst.setValue(IDX_H2H_AWAY_WIN,     PredictionUtils.safe(f.getH2hAwayWinRate()));
      inst.setValue(IDX_HOME_SHOTS,       PredictionUtils.safe(f.getHomeShotsOnTargetAvg()));
      inst.setValue(IDX_AWAY_SHOTS,       PredictionUtils.safe(f.getAwayShotsOnTargetAvg()));
      inst.setValue(IDX_HOME_CORNERS,     PredictionUtils.safe(f.getHomeCornersAvg()));
      inst.setValue(IDX_AWAY_CORNERS,     PredictionUtils.safe(f.getAwayCornersAvg()));
      inst.setValue(IDX_HOME_GOAL_DIFF,    PredictionUtils.safe(f.getHomeGoalDifference()));
      inst.setValue(IDX_AWAY_GOAL_DIFF,    PredictionUtils.safe(f.getAwayGoalDifference()));
      inst.setValue(IDX_HOME_OVERALL_FORM, PredictionUtils.safe(f.getHomeOverallFormPoints()));
      inst.setValue(IDX_AWAY_OVERALL_FORM, PredictionUtils.safe(f.getAwayOverallFormPoints()));
      inst.setValue(IDX_HOME_WIN_STREAK,   PredictionUtils.safe(f.getHomeWinStreak()));
      inst.setValue(IDX_AWAY_WIN_STREAK,   PredictionUtils.safe(f.getAwayWinStreak()));
      inst.setValue(IDX_HOME_UNBEATEN,     PredictionUtils.safe(f.getHomeUnbeatenStreak()));
      inst.setValue(IDX_AWAY_UNBEATEN,     PredictionUtils.safe(f.getAwayUnbeatenStreak()));
      inst.setValue(IDX_HOME_DAYS_REST,    PredictionUtils.safe(f.getHomeDaysSinceLastMatch()));
      inst.setValue(IDX_AWAY_DAYS_REST,    PredictionUtils.safe(f.getAwayDaysSinceLastMatch()));

      // Phase 5 features (Possession Proxy)
      inst.setValue(IDX_HOME_POSSESSION,   PredictionUtils.safe(f.getHomePossessionProxy()));
      inst.setValue(IDX_AWAY_POSSESSION,   PredictionUtils.safe(f.getAwayPossessionProxy()));

      if (f.getActualResult() != null) {
         inst.setValue(IDX_LABEL, f.getActualResult());
      }

      return inst;
   }

   private void saveModel(RandomForest model, Instances header) throws IOException {
      File file = new File(modelOutputPath);
      file.getParentFile().mkdirs();

      try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
         oos.writeObject(model);
         oos.writeObject(header);
      }

      log.info("Model saved to {}", modelOutputPath);
   }

   private String buildEvaluationReport(Evaluation eval, int trainSize, int testSize) throws Exception {
      return "\n══════════════════════════════════════════\n" +
            "   MATCH OUTCOME PREDICTOR — TRAINING   \n" +
            "══════════════════════════════════════════\n" +
            String.format("  Train set : %d matches%n", trainSize) +
            String.format("  Test set  : %d matches (most recent)%n", testSize) +
            String.format("  Accuracy  : %.1f%%%n", eval.pctCorrect()) +
            String.format("  Kappa     : %.4f%n", eval.kappa()) +
            String.format("  F-Measure : %.4f%n", eval.weightedFMeasure()) +
            "\n  Per-class breakdown:\n" +
            eval.toClassDetailsString("  ") +
            "\n  Confusion Matrix:\n" +
            eval.toMatrixString("  ") +
            "══════════════════════════════════════════\n";
   }

   private String buildTestReport(Evaluation eval, int testSize) throws Exception {
      return "\n══════════════════════════════════════════\n" +
            "   MATCH OUTCOME PREDICTOR — TESTING   \n" +
            "══════════════════════════════════════════\n" +
            String.format("  Test set  : %d matches%n", testSize) +
            String.format("  Accuracy  : %.1f%%%n", eval.pctCorrect()) +
            String.format("  Kappa     : %.4f%n", eval.kappa()) +
            String.format("  F-Measure : %.4f%n", eval.weightedFMeasure()) +
            "\n  Per-class breakdown:\n" +
            eval.toClassDetailsString("  ") +
            "\n  Confusion Matrix:\n" +
            eval.toMatrixString("  ") +
            "══════════════════════════════════════════\n";
   }
}

