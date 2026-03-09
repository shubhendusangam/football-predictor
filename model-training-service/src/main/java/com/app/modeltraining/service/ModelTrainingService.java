package com.app.modeltraining.service;

import com.app.common.weka.WekaSchemaBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.Instances;

import java.io.*;
import java.util.*;

import com.app.common.model.Match;
import com.app.common.model.MatchFeatures;
import com.app.common.repository.MatchRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelTrainingService {

   private final MatchRepository matchRepository;
   private final FeatureEngineeringService featureEngineeringService;

   @Value("${model.output.path}")
   private String modelOutputPath;

   @Value("${model.training.min-matches:100}")
   private int minMatches;

   @Value("${model.training.train-split:0.8}")
   private double trainSplit;

   // ── Weka column index — only those used directly in this class ──
   private static final int IDX_LABEL = WekaSchemaBuilder.IDX_LABEL;

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
      rf.setNumIterations(200);
      rf.setNumFeatures(7);
      rf.setMaxDepth(20);
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

   // ── Helper methods — delegate to shared WekaSchemaBuilder ──

   private ArrayList<Attribute> buildAttributes() {
      return WekaSchemaBuilder.buildAttributes();
   }

   private Instances toWekaInstances(List<MatchFeatures> featuresList, ArrayList<Attribute> attributes, String name) {
      return WekaSchemaBuilder.toWekaInstances(featuresList, name);
   }


   private void saveModel(RandomForest model, Instances header) throws IOException {
      File file = new File(modelOutputPath);
      File parentDir = file.getParentFile();
      if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
         log.warn("Failed to create directory: {}", parentDir.getAbsolutePath());
      }

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

