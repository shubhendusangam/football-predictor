package com.app.footballprediction.modeltraining;

import com.app.common.model.Match;
import com.app.common.model.MatchFeatures;
import com.app.common.repository.MatchRepository;
import com.app.common.service.FeatureEngineeringService;
import com.app.common.weka.WekaSchemaBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.functions.Logistic;
import weka.classifiers.functions.SMO;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.meta.CostSensitiveClassifier;
import weka.classifiers.meta.Vote;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SelectedTag;
import weka.filters.Filter;
import weka.filters.supervised.instance.Resample;

import java.io.*;
import java.util.*;
import java.util.stream.Stream;


@Service
@Slf4j
public class ModelTrainingService {

   private final MatchRepository matchRepository;
   private final FeatureEngineeringService featureEngineeringService;
   private final EnsembleModelService ensembleModelService;
   private final StackedEnsembleService stackedEnsembleService;

   @Value("${model.stacked.ensemble.enabled:true}")
   private boolean stackedEnsembleEnabled;

   @Value("${model.output.path}")
   private String modelOutputPath;

   @Value("${model.crossvalidation.folds:10}")
   private int crossValidationFolds;

   @Value("${model.smote.enabled:true}")
   private boolean smoteEnabled;

   @Value("${model.smote.percentage:100}")
   private int smotePercentage;

   @Autowired(required = false)
   @Qualifier("trainedModel")
   private RandomForest trainedModel;

   @Autowired(required = false)
   @Qualifier("trainingHeader")
   private Instances trainingHeader;

   // Constructor
   public ModelTrainingService(
         MatchRepository matchRepository,
         FeatureEngineeringService featureEngineeringService,
         EnsembleModelService ensembleModelService,
         @Autowired(required = false) StackedEnsembleService stackedEnsembleService) {
      this.matchRepository = matchRepository;
      this.featureEngineeringService = featureEngineeringService;
      this.ensembleModelService = ensembleModelService;
      this.stackedEnsembleService = stackedEnsembleService;
   }


   // ── Weka column indices — only those used directly in this class ──
   private static final int IDX_LABEL = WekaSchemaBuilder.IDX_LABEL;

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

      // ── Step 3: Validate result values ─────────────────────────
      // Ensure all features have valid result values (H, D, or A)
      long invalidResults = Stream.concat(trainSet.stream(), testSet.stream())
            .filter(f -> f.getActualResult() != null)
            .filter(f -> !f.getActualResult().equals("H")
                      && !f.getActualResult().equals("D")
                      && !f.getActualResult().equals("A"))
            .count();

      if (invalidResults > 0) {
          log.error("Found {} features with invalid result values. Expected: H, D, or A", invalidResults);
          throw new IllegalStateException(
                String.format("Dataset contains %d instances with invalid result values. " +
                      "Only 'H', 'D', or 'A' are allowed.", invalidResults));
      }

      // ── Step 4: Build Weka datasets ────────────────────────────
      ArrayList<Attribute> attributes = buildAttributes();
      Instances trainData = toWekaInstances(trainSet, attributes, "FootballTrain");
      Instances testData  = toWekaInstances(testSet,  attributes, "FootballTest");

      // Class index is already set inside toWekaInstances()

      // ── Step 5: Check if stacked ensemble is enabled ─────────
      if (stackedEnsembleEnabled && stackedEnsembleService != null) {
         log.info("Using Stacked Ensemble: RandomForest + Gradient Boosting + Logistic Regression meta-model");
         return trainStackedEnsemble(trainData, testData);
      } else {
         log.info("Stacked ensemble disabled, using simple RandomForest");
         return trainSimpleRandomForest(trainData, testData, trainSet.size(), testSet.size());
      }
   }

   /**
    * Train stacked ensemble (RF + GB + LogisticRegression)
    */
   private String trainStackedEnsemble(Instances trainData, Instances testData) throws Exception {

      long start = System.currentTimeMillis();

      // ── Step 1: Split training data for stacking ───────────────
      // Use 80% of training data to train base models
      // Use 20% of training data as validation set for meta-model
      int stackSplitIdx = (int) (trainData.numInstances() * 0.8);

      // Create new datasets with proper structure instead of using constructor subset
      // This ensures class attribute integrity is maintained
      Instances baseTrainData = new Instances(trainData, 0);
      Instances validationData = new Instances(trainData, 0);

      baseTrainData.setClassIndex(trainData.classIndex());
      validationData.setClassIndex(trainData.classIndex());

      // Manually copy instances to ensure proper class value handling
      for (int i = 0; i < trainData.numInstances(); i++) {
          if (i < stackSplitIdx) {
              baseTrainData.add(trainData.instance(i));
          } else {
              validationData.add(trainData.instance(i));
          }
      }

      log.info("Stacking split → base training: {}, validation: {}",
            baseTrainData.numInstances(), validationData.numInstances());

      // ── Step 2: Train Stacked Ensemble ─────────────────────────
      // RandomForest + Gradient Boosting + Logistic Regression meta-model
      log.info("Training stacked ensemble: RandomForest + Gradient Boosting + Logistic Regression");
      stackedEnsembleService.trainStackedEnsemble(baseTrainData, validationData);

      // ── Step 3: Evaluate ───────────────────────────────────────
      log.info("Evaluating stacked ensemble on test set...");

      // Validate test data structure
      if (testData.numInstances() == 0) {
         throw new IllegalStateException("Test data has no instances!");
      }

      Instance firstInstance = testData.instance(0);
      int numAttributes = firstInstance.numAttributes();
      log.info("Test data validation: {} instances, {} attributes per instance",
            testData.numInstances(), numAttributes);

      if (numAttributes <= IDX_LABEL) {
         throw new IllegalStateException(String.format(
               "Test data has insufficient attributes! Expected at least %d attributes (for IDX_LABEL=%d), " +
               "but found only %d attributes. This suggests feature engineering failed during test setup.",
               IDX_LABEL + 1, IDX_LABEL, numAttributes));
      }

      int correct = 0;
      int[][] confusionMatrix = new int[3][3]; // H, D, A
      int skippedInstances = 0;

      for (int i = 0; i < testData.numInstances(); i++) {
          Instance instance = testData.instance(i);
          String predicted = stackedEnsembleService.predictClass(instance);

          // Safely get the actual class value with validation
          String actual;
          try {
              double classValue = instance.classValue();
              int classIdx = (int) classValue;
              Attribute classAttr = instance.classAttribute();

              // Validate the class index is within valid range
              if (classIdx < 0 || classIdx >= classAttr.numValues()) {
                  log.warn("Instance {} has invalid class index {} (valid: 0-{}). Skipping.",
                        i, classIdx, classAttr.numValues() - 1);
                  skippedInstances++;
                  continue;
              }

              actual = classAttr.value(classIdx);
          } catch (Exception e) {
              log.warn("Instance {}: Cannot get class value: {}. Skipping.", i, e.getMessage());
              skippedInstances++;
              continue;
          }

          int predIdx = predicted.equals("H") ? 0 : predicted.equals("D") ? 1 : 2;
          int actualIdx = actual.equals("H") ? 0 : actual.equals("D") ? 1 : 2;

          confusionMatrix[actualIdx][predIdx]++;

          if (predicted.equals(actual)) {
              correct++;
          }
      }

      if (skippedInstances > 0) {
          log.warn("Skipped {} instances with invalid class values during evaluation", skippedInstances);
      }

      double accuracy = (double) correct / testData.numInstances() * 100.0;

      String report = buildStackedEnsembleReport(
            baseTrainData.numInstances(),
            validationData.numInstances(),
            testData.numInstances(),
            accuracy,
            confusionMatrix);
      log.info("\n{}", report);

      // ── Step 4: Save to disk and cache in memory ───────────────
      stackedEnsembleService.saveModel(modelOutputPath);
      // Also save as RandomForest for backward compatibility
      RandomForest rf = stackedEnsembleService.getRandomForest();
      saveModel(rf, trainData);
      this.trainedModel   = rf;       // update in-memory bean directly
      this.trainingHeader = trainData;

      long duration = System.currentTimeMillis() - start;
      log.info("Training complete in {} ms. Stacked Ensemble Accuracy: {}.1f%", duration, accuracy);

      return report;
   }

   /**
    * Train simple RandomForest (fallback when stacked ensemble not available)
    */
   private String trainSimpleRandomForest(Instances trainData, Instances testData,
         int trainSize, int testSize) throws Exception {

      long start = System.currentTimeMillis();

      // ── Step 1: Train Random Forest ────────────────────────────
      RandomForest rf = new RandomForest();
      rf.setNumIterations(200);   // 200 trees for better generalization
      rf.setNumFeatures(7);       // sqrt(42) ≈ 6.5, round up
      rf.setMaxDepth(20);         // Limit depth to prevent overfitting
      rf.setSeed(42);
      rf.buildClassifier(trainData);

      log.info("Random Forest trained.");

      // ── Step 2: Evaluate ───────────────────────────────────────
      Evaluation eval = new Evaluation(trainData);
      eval.evaluateModel(rf, testData);

      String report = buildEvaluationReport(eval,
            trainSize,
            testSize);
      log.info("\n{}", report);

      // ── Step 3: Save to disk and cache in memory ───────────────
      saveModel(rf, trainData);
      this.trainedModel   = rf;       // update in-memory bean directly
      this.trainingHeader = trainData;

      long duration = System.currentTimeMillis() - start;
      log.info("Training complete in {} ms. Accuracy: {}%", duration, String.format("%.1f", eval.pctCorrect()));

      return report;
   }

   /**
    * Build evaluation report for stacked ensemble.
    */
   private String buildStackedEnsembleReport(int baseTrainSize, int validationSize,
         int testSize, double accuracy, int[][] confusionMatrix) {
      StringBuilder sb = new StringBuilder();
      sb.append("\n╔══════════════════════════════════════════════════════════╗\n");
      sb.append("║   STACKED ENSEMBLE MODEL TRAINING REPORT                 ║\n");
      sb.append("╚══════════════════════════════════════════════════════════╝\n\n");

      sb.append("📊 ENSEMBLE ARCHITECTURE\n");
      sb.append("════════════════════════════════════════════════════════════\n");
      sb.append("Base Model 1: RandomForest (100 trees)\n");
      sb.append("Base Model 2: Gradient Boosting (AdaBoostM1, 100 rounds)\n");
      sb.append("Meta Model:   Logistic Regression (combines predictions)\n\n");

      sb.append("📈 DATASET SPLIT\n");
      sb.append("════════════════════════════════════════════════════════════\n");
      sb.append(String.format("Base Training:  %5d instances (64%%)\n", baseTrainSize));
      sb.append(String.format("Validation:     %5d instances (16%%)\n", validationSize));
      sb.append(String.format("Test:           %5d instances (20%%)\n", testSize));
      sb.append(String.format("Total:          %5d instances\n\n",
            baseTrainSize + validationSize + testSize));

      sb.append("🎯 PERFORMANCE METRICS\n");
      sb.append("════════════════════════════════════════════════════════════\n");
      sb.append(String.format("Overall Accuracy: %.2f%%\n\n", accuracy));

      sb.append("📊 CONFUSION MATRIX\n");
      sb.append("════════════════════════════════════════════════════════════\n");
      sb.append("              Predicted\n");
      sb.append("           H     D     A\n");
      sb.append("Actual H | ").append(String.format("%4d  %4d  %4d\n",
            confusionMatrix[0][0], confusionMatrix[0][1], confusionMatrix[0][2]));
      sb.append("       D | ").append(String.format("%4d  %4d  %4d\n",
            confusionMatrix[1][0], confusionMatrix[1][1], confusionMatrix[1][2]));
      sb.append("       A | ").append(String.format("%4d  %4d  %4d\n\n",
            confusionMatrix[2][0], confusionMatrix[2][1], confusionMatrix[2][2]));

      // Calculate per-class metrics
      sb.append("📈 PER-CLASS METRICS\n");
      sb.append("════════════════════════════════════════════════════════════\n");
      String[] classes = {"Home Win", "Draw", "Away Win"};
      for (int i = 0; i < 3; i++) {
          int tp = confusionMatrix[i][i];
          int fp = confusionMatrix[0][i] + confusionMatrix[1][i] + confusionMatrix[2][i] - tp;
          int fn = confusionMatrix[i][0] + confusionMatrix[i][1] + confusionMatrix[i][2] - tp;

          double precision = tp + fp > 0 ? (double) tp / (tp + fp) * 100 : 0;
          double recall = tp + fn > 0 ? (double) tp / (tp + fn) * 100 : 0;
          double f1 = precision + recall > 0 ? 2 * precision * recall / (precision + recall) : 0;

          sb.append(String.format("%s (class %d):\n", classes[i], i));
          sb.append(String.format("  Precision: %.2f%%  Recall: %.2f%%  F1-Score: %.2f%%\n",
                precision, recall, f1));
      }

      sb.append("\n✅ MODEL SAVED TO: ").append(modelOutputPath).append("\n");
      sb.append("════════════════════════════════════════════════════════════\n");

      return sb.toString();
   }

   /**
    * Advanced training with cross-validation, gradient boosting, and ensemble.
    * This is the recommended training method for best accuracy.
    */
   public String trainAdvanced() throws Exception {
      long start = System.currentTimeMillis();
      log.info("Starting ADVANCED model training pipeline...");

      // Build dataset
      Instances fullData = buildTrainingDataset();

      StringBuilder report = new StringBuilder();
      report.append("\n══════════════════════════════════════════\n");
      report.append("   ADVANCED MODEL TRAINING REPORT\n");
      report.append("══════════════════════════════════════════\n");
      report.append(String.format("  Total samples: %d%n", fullData.numInstances()));

      // Step 1: Compare models using cross-validation
      log.info("Step 1: Comparing models...");
      report.append("\n📊 MODEL COMPARISON (10-fold CV):\n");
      report.append("  ──────────────────────────────────────────────────────────────────────────────\n");
      report.append("  Model                 | Accuracy |  Kappa | F-Score |  Prec. | Recall |   Time\n");
      report.append("  ──────────────────────────────────────────────────────────────────────────────\n");

      Map<String, Classifier> classifiers = buildClassifierMap();
      List<ModelComparisonResult> comparisonResults = ensembleModelService.compareModels(fullData, classifiers);

      for (ModelComparisonResult result : comparisonResults) {
         report.append(result.toRow()).append("\n");
      }
      report.append("  ──────────────────────────────────────────────────────────────────────────────\n");

      // Step 2: Grid search for best classifier
      log.info("Step 2: Grid search optimization...");
      report.append("\n🔍 GRID SEARCH OPTIMIZATION:\n");

      GridSearchResult rfGridResult = ensembleModelService.gridSearchRandomForest(fullData);
      report.append(String.format("  Random Forest Best: %.2f%% (trees=%s, features=%s, depth=%s)%n",
              rfGridResult.getAccuracy(),
              rfGridResult.getBestParams().get("numTrees"),
              rfGridResult.getBestParams().get("numFeatures"),
              rfGridResult.getBestParams().get("maxDepth")));

      GridSearchResult abGridResult = ensembleModelService.gridSearchAdaBoost(fullData);
      report.append(String.format("  AdaBoost Best: %.2f%% (iterations=%s, cf=%s, minObj=%s)%n",
              abGridResult.getAccuracy(),
              abGridResult.getBestParams().get("numIterations"),
              abGridResult.getBestParams().get("confidenceFactor"),
              abGridResult.getBestParams().get("minNumObj")));

      // Step 3: Build and evaluate ensemble
      log.info("Step 3: Building ensemble model...");
      report.append("\n🤝 ENSEMBLE MODEL:\n");

      Vote ensembleModel = buildOptimizedEnsemble(rfGridResult, abGridResult);
      CrossValidationResult cvResult = ensembleModelService.performCrossValidation(fullData, ensembleModel, crossValidationFolds);

      report.append(String.format("  Ensemble CV Accuracy: %.2f%%%n", cvResult.getAccuracy()));
      report.append(String.format("  Ensemble Kappa: %.4f%n", cvResult.getKappa()));
      report.append(String.format("  Ensemble F-Measure: %.4f%n", cvResult.getFMeasure()));

      // Step 4: Train final model on full data
      log.info("Step 4: Training final model...");
      ensembleModel.buildClassifier(fullData);

      // Save ensemble model
      ensembleModelService.saveModel(ensembleModel, fullData);

      // Also save as primary model for backward compatibility
      RandomForest rf = (RandomForest) ensembleModelService.buildBestClassifier(rfGridResult);
      rf.buildClassifier(fullData);
      saveModel(rf, fullData);
      this.trainedModel = rf;
      this.trainingHeader = fullData;

      long duration = System.currentTimeMillis() - start;
      report.append(String.format("%n⏱️ Total training time: %.1f seconds%n", duration / 1000.0));
      report.append("══════════════════════════════════════════\n");

      log.info("\n{}", report);
      return report.toString();
   }

   /**
    * Train with cross-validation for better evaluation.
    */
   public String trainWithCrossValidation() throws Exception {
      long start = System.currentTimeMillis();
      log.info("Starting training with {}-fold cross-validation...", crossValidationFolds);

      Instances fullData = buildTrainingDataset();

      // Cross-validation evaluation
      RandomForest rf = new RandomForest();
      rf.setNumIterations(100);
      rf.setNumFeatures(5);
      rf.setSeed(42);

      CrossValidationResult cvResult = ensembleModelService.performCrossValidation(fullData, rf, crossValidationFolds);

      // Train final model on full data
      rf.buildClassifier(fullData);
      saveModel(rf, fullData);
      this.trainedModel = rf;
      this.trainingHeader = fullData;

      long duration = System.currentTimeMillis() - start;

      String report = cvResult.toReport() +
              String.format("%nTraining time: %.1f seconds%n", duration / 1000.0);

      log.info("\n{}", report);
      return report;
   }

   /**
    * Train using Gradient Boosting (AdaBoost).
    */
   public String trainGradientBoosting() throws Exception {
      long start = System.currentTimeMillis();
      log.info("Starting Gradient Boosting training...");

      Instances fullData = buildTrainingDataset();

      // Grid search for best params
      GridSearchResult gridResult = ensembleModelService.gridSearchAdaBoost(fullData);

      // Build best classifier
      AdaBoostM1 adaBoost = (AdaBoostM1) ensembleModelService.buildBestClassifier(gridResult);

      // Cross-validation
      CrossValidationResult cvResult = ensembleModelService.performCrossValidation(fullData, adaBoost, crossValidationFolds);

      // Train on full data
      adaBoost.buildClassifier(fullData);

      // Save as ensemble model
      ensembleModelService.saveModel(adaBoost, fullData);

      long duration = System.currentTimeMillis() - start;

      String report = gridResult.toReport() + cvResult.toReport() +
              String.format("%nTraining time: %.1f seconds%n", duration / 1000.0);

      log.info("\n{}", report);
      return report;
   }

   /**
    * Train ensemble model combining multiple classifiers.
    */
   public String trainEnsemble() throws Exception {
      long start = System.currentTimeMillis();
      log.info("Starting Ensemble model training...");

      Instances fullData = buildTrainingDataset();

      // Build default ensemble
      Vote ensemble = ensembleModelService.trainDefaultEnsemble(fullData);

      // Cross-validation
      CrossValidationResult cvResult = ensembleModelService.performCrossValidation(fullData, ensemble, crossValidationFolds);

      // Save
      ensembleModelService.saveModel(ensemble, fullData);

      long duration = System.currentTimeMillis() - start;

      String report = cvResult.toReport() +
              String.format("%nEnsemble: Random Forest + AdaBoost + J48%n") +
              String.format("Training time: %.1f seconds%n", duration / 1000.0);

      log.info("\n{}", report);
      return report;
   }

   /**
    * Perform hyperparameter grid search and return best configuration.
    */
   public String performGridSearch() throws Exception {
      log.info("Starting hyperparameter grid search...");

      Instances fullData = buildTrainingDataset();

      StringBuilder report = new StringBuilder();
      report.append("\n══════════════════════════════════════════\n");
      report.append("   HYPERPARAMETER GRID SEARCH RESULTS\n");
      report.append("══════════════════════════════════════════\n");

      // Random Forest grid search
      GridSearchResult rfResult = ensembleModelService.gridSearchRandomForest(fullData);
      report.append(rfResult.toReport());

      // AdaBoost grid search
      GridSearchResult abResult = ensembleModelService.gridSearchAdaBoost(fullData);
      report.append(abResult.toReport());

      // Recommendation
      report.append("\n📋 RECOMMENDATION:\n");
      if (rfResult.getAccuracy() > abResult.getAccuracy()) {
         report.append(String.format("  Use Random Forest with accuracy %.2f%%%n", rfResult.getAccuracy()));
         report.append(String.format("  Params: trees=%s, features=%s, depth=%s%n",
                 rfResult.getBestParams().get("numTrees"),
                 rfResult.getBestParams().get("numFeatures"),
                 rfResult.getBestParams().get("maxDepth")));
      } else {
         report.append(String.format("  Use AdaBoost with accuracy %.2f%%%n", abResult.getAccuracy()));
         report.append(String.format("  Params: iterations=%s, cf=%s, minObj=%s%n",
                 abResult.getBestParams().get("numIterations"),
                 abResult.getBestParams().get("confidenceFactor"),
                 abResult.getBestParams().get("minNumObj")));
      }

      report.append("══════════════════════════════════════════\n");

      log.info("\n{}", report);
      return report.toString();
   }

   /**
    * Compare all available models.
    */
   public String compareModels() throws Exception {
      log.info("Starting model comparison...");

      Instances fullData = buildTrainingDataset();

      Map<String, Classifier> classifiers = buildClassifierMap();
      List<ModelComparisonResult> results = ensembleModelService.compareModels(fullData, classifiers);

      StringBuilder report = new StringBuilder();
      report.append("\n══════════════════════════════════════════════════════════════════════════════\n");
      report.append("   MODEL COMPARISON RESULTS (").append(crossValidationFolds).append("-fold CV)\n");
      report.append("══════════════════════════════════════════════════════════════════════════════\n");
      report.append("  Model                 | Accuracy |  Kappa | F-Score |  Prec. | Recall |   Time\n");
      report.append("  ──────────────────────────────────────────────────────────────────────────────\n");

      for (ModelComparisonResult result : results) {
         report.append(result.toRow()).append("\n");
      }

      report.append("  ──────────────────────────────────────────────────────────────────────────────\n");
      report.append(String.format("%n🏆 Best Model: %s (%.2f%% accuracy)%n",
              results.getFirst().getModelName(),
              results.getFirst().getAccuracy()));
      report.append("══════════════════════════════════════════════════════════════════════════════\n");

      log.info("\n{}", report);
      return report.toString();
   }

   // ── Helper methods for advanced training ────────────────────────────────

   private Instances buildTrainingDataset() throws Exception {
      List<Match> allMatches = matchRepository.findAllByOrderByMatchDateAsc();
      log.info("Building dataset from {} matches...", allMatches.size());

      if (allMatches.size() < 100) {
         throw new IllegalStateException(
                 "Not enough data. Need at least 100 matches, found: " + allMatches.size());
      }

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

      ArrayList<Attribute> attributes = buildAttributes();
      Instances dataset = toWekaInstances(allFeatures, attributes, "FootballData");
      dataset.setClassIndex(IDX_LABEL);

      return dataset;
   }

   private Map<String, Classifier> buildClassifierMap() {
      Map<String, Classifier> classifiers = new LinkedHashMap<>();

      // Random Forest (tuned)
      RandomForest rf = new RandomForest();
      rf.setNumIterations(200);
      rf.setNumFeatures(7);
      rf.setMaxDepth(20);
      rf.setSeed(42);
      classifiers.put("Random Forest", rf);

      // AdaBoost
      AdaBoostM1 adaBoost = new AdaBoostM1();
      adaBoost.setNumIterations(100);
      adaBoost.setSeed(42);
      J48 j48Base = new J48();
      try { j48Base.setConfidenceFactor(0.25f); } catch (Exception ignored) {}
      adaBoost.setClassifier(j48Base);
      classifiers.put("AdaBoost (J48)", adaBoost);

      // J48 Decision Tree
      J48 j48 = new J48();
      try {
         j48.setConfidenceFactor(0.25f);
         j48.setMinNumObj(5);
      } catch (Exception ignored) {}
      classifiers.put("J48 Decision Tree", j48);

      // Logistic Regression
      Logistic logistic = new Logistic();
      logistic.setMaxIts(200);
      classifiers.put("Logistic Regression", logistic);

      // SMO (SVM)
      SMO smo = new SMO();
      smo.setRandomSeed(42);
      classifiers.put("SMO (SVM)", smo);

      // Cost-Sensitive Random Forest (boost Draw class)
      try {
         CostSensitiveClassifier csc = new CostSensitiveClassifier();
         RandomForest rfCost = new RandomForest();
         rfCost.setNumIterations(200);
         rfCost.setNumFeatures(7);
         rfCost.setMaxDepth(20);
         rfCost.setSeed(42);
         csc.setClassifier(rfCost);
         // Use string-based cost matrix: penalize Draw misclassification more heavily
         // Format: rows=actual, cols=predicted. H=0, D=1, A=2
         csc.setOptions(new String[]{
               "-cost-matrix", "[0 1.5 1; 1.5 0 1.5; 1 1.5 0]",
               "-S", "42"
         });
         classifiers.put("CostSensitive RF", csc);
      } catch (Exception e) {
         log.warn("Could not create CostSensitiveClassifier: {}", e.getMessage());
      }

      // Voting Ensemble
      Vote vote = new Vote();
      RandomForest rfVote = new RandomForest();
      rfVote.setNumIterations(100);
      rfVote.setNumFeatures(7);
      rfVote.setSeed(42);
      AdaBoostM1 abVote = new AdaBoostM1();
      abVote.setNumIterations(50);
      abVote.setSeed(42);
      vote.setClassifiers(new Classifier[]{rfVote, abVote, new J48()});
      vote.setCombinationRule(new SelectedTag(Vote.AVERAGE_RULE, Vote.TAGS_RULES));
      classifiers.put("Voting Ensemble", vote);

      return classifiers;
   }

   private Vote buildOptimizedEnsemble(GridSearchResult rfResult, GridSearchResult abResult) throws Exception {
      // Build optimized Random Forest
      RandomForest rf = new RandomForest();
      rf.setNumIterations((Integer) rfResult.getBestParams().get("numTrees"));
      rf.setNumFeatures((Integer) rfResult.getBestParams().get("numFeatures"));
      rf.setMaxDepth((Integer) rfResult.getBestParams().get("maxDepth"));
      rf.setSeed(42);

      // Build optimized AdaBoost
      J48 j48 = new J48();
      j48.setConfidenceFactor((Float) abResult.getBestParams().get("confidenceFactor"));
      j48.setMinNumObj((Integer) abResult.getBestParams().get("minNumObj"));

      AdaBoostM1 adaBoost = new AdaBoostM1();
      adaBoost.setNumIterations((Integer) abResult.getBestParams().get("numIterations"));
      adaBoost.setClassifier(j48);
      adaBoost.setSeed(42);

      // Create voting ensemble
      Vote ensemble = new Vote();
      ensemble.setClassifiers(new Classifier[]{rf, adaBoost, new J48()});
      ensemble.setCombinationRule(new SelectedTag(Vote.AVERAGE_RULE, Vote.TAGS_RULES));
      ensemble.setSeed(42);

      return ensemble;
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
      if (trainedModel == null || trainingHeader == null) {
         return false;
      }
      // Schema validation: if the model was trained with a different number of
      // attributes (e.g. before Phase 10 added 3 new features), it's incompatible.
      int expectedAttrs = WekaSchemaBuilder.TOTAL_ATTRIBUTES;
      int actualAttrs = trainingHeader.numAttributes();
      if (actualAttrs != expectedAttrs) {
         log.warn("⚠ Loaded model schema mismatch: model has {} attributes, expected {}. "
                 + "Model will be retrained.", actualAttrs, expectedAttrs);
         this.trainedModel = null;
         this.trainingHeader = null;
         return false;
      }
      return true;
   }

   /**
    * Returns the last updated timestamp of the model file in ISO 8601 format.
    * @return ISO 8601 formatted date string or null if model file doesn't exist
    */
   public String getModelLastUpdated() {
      File file = new File(modelOutputPath);
      if (file.exists()) {
         long lastModified = file.lastModified();
         java.time.Instant instant = java.time.Instant.ofEpochMilli(lastModified);
         return instant.toString(); // ISO 8601 format: 2026-02-19T10:30:45.123Z
      }
      return null;
   }

   /**
    * Returns the model file size in a human-readable format.
    * @return formatted file size string (e.g. "1.5 MB") or null if model file doesn't exist
    */
   public String getModelFileSize() {
      File file = new File(modelOutputPath);
      if (file.exists()) {
         long bytes = file.length();
         if (bytes < 1024) return bytes + " B";
         if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
         return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
      }
      return null;
   }

   /**
    * Returns the number of features used by the model.
    */
   public int getFeatureCount() {
      return WekaSchemaBuilder.FEATURE_COUNT;
   }

   // ── Weka dataset builders — delegate to shared WekaSchemaBuilder ──────

   /**
    * Defines the schema of the Weka dataset.
    * Delegates to WekaSchemaBuilder for single-source-of-truth.
    */
   private ArrayList<Attribute> buildAttributes() {
      return WekaSchemaBuilder.buildAttributes();
   }

   /**
    * Converts a list of MatchFeatures into a Weka Instances dataset.
    * Delegates to shared WekaSchemaBuilder.
    */
   private Instances toWekaInstances(List<MatchFeatures> featuresList, ArrayList<Attribute> attributes, String name) {
      return WekaSchemaBuilder.toWekaInstances(featuresList, name);
   }

   /**
    * Converts a single MatchFeatures into a Weka Instance.
    * Delegates to shared WekaSchemaBuilder.
    */
   private Instance toWekaInstance(MatchFeatures f, Instances dataset) {
      return WekaSchemaBuilder.toWekaInstance(f, dataset);
   }

   // ── Model persistence ─────────────────────────────────────────────────

   private void saveModel(RandomForest model,
                          Instances header) throws IOException {
      File file = new File(modelOutputPath);
      File parentDir = file.getParentFile();
      if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
         log.warn("Failed to create directory: {}", parentDir.getAbsolutePath());
      }

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
            String.format("  Features  : %d%n", IDX_LABEL) +
            String.format("  SMOTE     : %s%n", smoteEnabled ? "ON (" + smotePercentage + "%)" : "OFF") +
            String.format("  Accuracy  : %.1f%%%n",
                  eval.pctCorrect()) +
            String.format("  Kappa     : %.4f%n", eval.kappa()) +
            String.format("  Baseline  : ~45%% (always predict " +
                  "Home Win)%n") +
            "\n  Per-class breakdown:\n" +
            eval.toClassDetailsString("  ") +
            "\n  Confusion Matrix:\n" +
            eval.toMatrixString("  ") +
            "══════════════════════════════════════════\n";
   }

   // ── Class Balancing (SMOTE) ─────────────────────────────────────────

   /**
    * Apply class balancing using Weka's SMOTE filter for synthetic minority oversampling.
    * Falls back to Resample with bias if SMOTE is unavailable.
    *
    * <p>SMOTE generates synthetic minority class instances by interpolating between
    * nearest neighbors, which is superior to simple random oversampling (duplication)
    * because it creates NEW diverse instances rather than exact copies.</p>
    *
    * <p>Draws are typically underrepresented (~25%) vs Home wins (~46%).</p>
    *
    * @param data Training dataset
    * @return Balanced dataset, or original if resampling fails/disabled
    */
   private Instances applySMOTE(Instances data) {
      if (!smoteEnabled) {
         log.debug("Class balancing is disabled");
         return data;
      }

      try {
         // Log class distribution before resampling
         int[] classCounts = new int[data.numClasses()];
         for (int i = 0; i < data.numInstances(); i++) {
            classCounts[(int) data.instance(i).classValue()]++;
         }
         log.info("Class distribution before balancing: H={}, D={}, A={}",
               classCounts[0], classCounts[1], classCounts[2]);

         Instances balanced;

         // Try SMOTE first (creates synthetic instances via nearest-neighbor interpolation)
         try {
            balanced = applySMOTEFilter(data, classCounts);
            log.info("Applied SMOTE (synthetic minority oversampling)");
         } catch (Exception smoteEx) {
            // Fallback: use Resample with bias to uniform distribution
            log.debug("SMOTE filter not available, falling back to Resample: {}", smoteEx.getMessage());
            balanced = applyResampleFallback(data);
            log.info("Applied Resample with uniform bias (fallback)");
         }

         // Log class distribution after resampling
         int[] newCounts = new int[balanced.numClasses()];
         for (int i = 0; i < balanced.numInstances(); i++) {
            newCounts[(int) balanced.instance(i).classValue()]++;
         }
         log.info("Class distribution after balancing: H={}, D={}, A={}",
               newCounts[0], newCounts[1], newCounts[2]);

         return balanced;

      } catch (Exception e) {
         log.warn("Class balancing failed, using original data: {}", e.getMessage());
         return data;
      }
   }

   /**
    * Apply improved class balancing using Weka's SpreadSubsample to equalize classes,
    * then Resample to bring sample count back up.
    *
    * <p>This two-stage approach:
    * 1. SpreadSubsample — reduces majority classes to match minority class count
    * 2. Resample with replacement — brings total back up to original size
    *
    * This is more effective than simple Resample with bias because it ensures
    * exact class balance rather than approximate reweighting.</p>
    */
   private Instances applySMOTEFilter(Instances data, int[] classCounts) throws Exception {
      // Stage 1: Spread subsample to equalize class counts (downsample majority)
      weka.filters.supervised.instance.SpreadSubsample spreadFilter = new weka.filters.supervised.instance.SpreadSubsample();
      spreadFilter.setDistributionSpread(1.0); // Force uniform distribution
      spreadFilter.setRandomSeed(42);
      spreadFilter.setInputFormat(data);
      Instances equalized = Filter.useFilter(data, spreadFilter);

      // Stage 2: Resample with replacement to bring count back up
      int targetSize = data.numInstances();
      double samplePercent = (double) targetSize / equalized.numInstances() * 100.0;

      Resample resample = new Resample();
      resample.setNoReplacement(false);
      resample.setSampleSizePercent(samplePercent);
      resample.setRandomSeed(42);
      resample.setInputFormat(equalized);
      return Filter.useFilter(equalized, resample);
   }

   /**
    * Fallback: use Resample with bias to uniform distribution.
    */
   private Instances applyResampleFallback(Instances data) throws Exception {
      Resample resample = new Resample();
      resample.setBiasToUniformClass(1.0);
      resample.setNoReplacement(false);
      resample.setSampleSizePercent(100.0 + smotePercentage);
      resample.setRandomSeed(42);
      resample.setInputFormat(data);
      return Filter.useFilter(data, resample);
   }

   // ── Feature Importance Analysis ───────────────────────────────────────

   /**
    * Analyze feature importance using a trained RandomForest.
    * Returns a ranked map of feature names to importance scores.
    * Uses the out-of-bag (OOB) error rate increase as importance measure.
    *
    * @return Sorted map (descending) of attribute name → importance score
    */
   public Map<String, Double> getFeatureImportance() throws Exception {
      Instances fullData = buildTrainingDataset();

      RandomForest rf = new RandomForest();
      rf.setNumIterations(200);
      rf.setNumFeatures(7);
      rf.setMaxDepth(20);
      rf.setSeed(42);
      rf.setComputeAttributeImportance(true);
      rf.buildClassifier(fullData);

      Map<String, Double> importanceMap = new LinkedHashMap<>();
      double[] importances = rf.computeAverageImpurityDecreasePerAttribute(null);

      if (importances != null) {
         for (int i = 0; i < importances.length && i < fullData.numAttributes() - 1; i++) {
            importanceMap.put(fullData.attribute(i).name(), importances[i]);
         }
      }

      // Sort by importance descending
      Map<String, Double> sorted = new LinkedHashMap<>();
      importanceMap.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .forEach(e -> sorted.put(e.getKey(), e.getValue()));

      log.info("Feature importance analysis complete ({} features)", sorted.size());
      sorted.entrySet().stream().limit(10)
            .forEach(e -> log.info("  {} → {}", e.getKey(), String.format("%.4f", e.getValue())));

      return sorted;
   }

   // ── Temporal Cross-Validation ─────────────────────────────────────────

   /**
    * Perform time-series aware cross-validation using expanding window.
    * Unlike standard k-fold which shuffles data (causing temporal leakage),
    * this method preserves chronological order:
    *
    * Fold 1: Train on [0..20%], Test on [20..30%]
    * Fold 2: Train on [0..30%], Test on [30..40%]
    * ...
    * Fold N: Train on [0..90%], Test on [90..100%]
    *
    * @return CrossValidationResult with average metrics across folds
    */
   public CrossValidationResult performTemporalCrossValidation() throws Exception {
      log.info("Starting temporal cross-validation...");

      Instances fullData = buildTrainingDataset();
      int numFolds = 5;
      int foldSize = fullData.numInstances() / (numFolds + 1);

      double totalAccuracy = 0;
      double totalKappa = 0;
      double totalFMeasure = 0;
      double totalPrecision = 0;
      double totalRecall = 0;

      for (int fold = 0; fold < numFolds; fold++) {
         int trainEnd = foldSize * (fold + 2); // expanding window
         int testStart = trainEnd;
         int testEnd = Math.min(testStart + foldSize, fullData.numInstances());

         if (testEnd <= testStart) break;

         Instances trainData = new Instances(fullData, 0);
         for (int i = 0; i < trainEnd; i++) {
            trainData.add(fullData.instance(i));
         }

         Instances testData = new Instances(fullData, 0);
         for (int i = testStart; i < testEnd; i++) {
            testData.add(fullData.instance(i));
         }

         trainData.setClassIndex(IDX_LABEL);
         testData.setClassIndex(IDX_LABEL);

         // Apply SMOTE to training data only
         trainData = applySMOTE(trainData);

         RandomForest rf = new RandomForest();
         rf.setNumIterations(200);
         rf.setNumFeatures(7);
         rf.setMaxDepth(20);
         rf.setSeed(42);
         rf.buildClassifier(trainData);

         Evaluation eval = new Evaluation(trainData);
         eval.evaluateModel(rf, testData);

         totalAccuracy += eval.pctCorrect();
         totalKappa += eval.kappa();
         totalFMeasure += eval.weightedFMeasure();
         totalPrecision += eval.weightedPrecision();
         totalRecall += eval.weightedRecall();

         log.info("Temporal fold {}: train={}, test={}, accuracy={}%",
               fold + 1, trainData.numInstances(), testData.numInstances(),
               String.format("%.1f", eval.pctCorrect()));
      }

      CrossValidationResult result = CrossValidationResult.builder()
            .accuracy(totalAccuracy / numFolds)
            .kappa(totalKappa / numFolds)
            .fMeasure(totalFMeasure / numFolds)
            .precision(totalPrecision / numFolds)
            .recall(totalRecall / numFolds)
            .folds(numFolds)
            .build();

      log.info("Temporal CV complete: avg accuracy={}%", String.format("%.2f", result.getAccuracy()));
      return result;
   }

   /**
    * Train with SMOTE applied to the training set for class balancing.
    * This is used by trainSimpleRandomForest and trainStackedEnsemble.
    */
   public String trainWithSMOTE() throws Exception {
      long start = System.currentTimeMillis();
      log.info("Starting training with SMOTE class balancing...");

      List<Match> allMatches = matchRepository.findAllByOrderByMatchDateAsc();
      if (allMatches.size() < 100) {
         throw new IllegalStateException("Not enough data. Need at least 100 matches, found: " + allMatches.size());
      }

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

      // Temporal split
      int splitIdx = (int) (allFeatures.size() * 0.8);
      List<MatchFeatures> trainSet = allFeatures.subList(0, splitIdx);
      List<MatchFeatures> testSet = allFeatures.subList(splitIdx, allFeatures.size());

      ArrayList<Attribute> attributes = buildAttributes();
      Instances trainData = toWekaInstances(trainSet, attributes, "FootballTrain");
      Instances testData = toWekaInstances(testSet, attributes, "FootballTest");

      // Apply SMOTE to training data only (not test data)
      Instances balancedTrainData = applySMOTE(trainData);

      RandomForest rf = new RandomForest();
      rf.setNumIterations(200);
      rf.setNumFeatures(7);
      rf.setMaxDepth(20);
      rf.setSeed(42);
      rf.buildClassifier(balancedTrainData);

      Evaluation eval = new Evaluation(balancedTrainData);
      eval.evaluateModel(rf, testData);

      String report = buildEvaluationReport(eval, balancedTrainData.numInstances(), testData.numInstances());
      log.info("\n{}", report);

      saveModel(rf, trainData); // Save with original header (not SMOTE)
      this.trainedModel = rf;
      this.trainingHeader = trainData;

      long duration = System.currentTimeMillis() - start;
      log.info("SMOTE training complete in {} ms. Accuracy: {}%", duration, String.format("%.1f", eval.pctCorrect()));

      return report;
   }
}
