package com.app.footballprediction.modeltraining;

import com.app.common.model.Match;
import com.app.common.model.MatchFeatures;
import com.app.common.repository.MatchRepository;
import com.app.common.service.FeatureEngineeringService;
import com.app.common.util.PredictionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.meta.Vote;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SelectedTag;

import java.io.*;
import java.util.*;
import java.util.stream.Stream;


@Service
@Slf4j
public class ModelTrainingService {

   private final MatchRepository matchRepository;
   private final FeatureEngineeringService featureEngineeringService;
   private final EnsembleModelService ensembleModelService;

   @Autowired
   private StackedEnsembleService stackedEnsembleService;

   @Value("${model.stacked.ensemble.enabled:true}")
   private boolean stackedEnsembleEnabled;

   @Value("${model.output.path}")
   private String modelOutputPath;

   /** Reserved for future model type selection (RANDOM_FOREST, GRADIENT_BOOSTING, etc.) */
   @SuppressWarnings("unused")
   @Value("${model.type:RANDOM_FOREST}")
   private String modelType;

   /** Reserved for enabling/disabling cross-validation during training */
   @SuppressWarnings("unused")
   @Value("${model.crossvalidation.enabled:true}")
   private boolean crossValidationEnabled;

   @Value("${model.crossvalidation.folds:10}")
   private int crossValidationFolds;

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
         EnsembleModelService ensembleModelService) {
      this.matchRepository = matchRepository;
      this.featureEngineeringService = featureEngineeringService;
      this.ensembleModelService = ensembleModelService;
   }


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
   // Phase 5 features (Possession Proxy)
   private static final int IDX_HOME_POSSESSION   = 25;
   private static final int IDX_AWAY_POSSESSION   = 26;
   private static final int IDX_LABEL             = 27;

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
         return trainStackedEnsemble(trainData, testData, trainSet, testSet);
      } else {
         log.info("Stacked ensemble disabled, using simple RandomForest");
         return trainSimpleRandomForest(trainData, testData, trainSet, testSet);
      }
   }

   /**
    * Train stacked ensemble (RF + GB + LogisticRegression)
    */
   private String trainStackedEnsemble(Instances trainData, Instances testData,
         List<MatchFeatures> trainSet, List<MatchFeatures> testSet) throws Exception {

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
         List<MatchFeatures> trainSet, List<MatchFeatures> testSet) throws Exception {

      long start = System.currentTimeMillis();

      // ── Step 1: Train Random Forest ────────────────────────────
      RandomForest rf = new RandomForest();
      rf.setNumIterations(100);   // 100 trees
      rf.setNumFeatures(5);       // sqrt(25) ≈ 5 features per split
      rf.setSeed(42);
      rf.buildClassifier(trainData);

      log.info("Random Forest trained.");

      // ── Step 2: Evaluate ───────────────────────────────────────
      Evaluation eval = new Evaluation(trainData);
      eval.evaluateModel(rf, testData);

      String report = buildEvaluationReport(eval,
            trainSet.size(),
            testSet.size());
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

      Vote ensembleModel = buildOptimizedEnsemble(fullData, rfGridResult, abGridResult);
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
              results.get(0).getModelName(),
              results.get(0).getAccuracy()));
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

      // Random Forest
      RandomForest rf = new RandomForest();
      rf.setNumIterations(100);
      rf.setNumFeatures(5);
      rf.setSeed(42);
      classifiers.put("Random Forest", rf);

      // AdaBoost
      AdaBoostM1 adaBoost = new AdaBoostM1();
      adaBoost.setNumIterations(50);
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

      // Voting Ensemble
      Vote vote = new Vote();
      RandomForest rfVote = new RandomForest();
      rfVote.setNumIterations(50);
      rfVote.setSeed(42);
      AdaBoostM1 abVote = new AdaBoostM1();
      abVote.setNumIterations(25);
      abVote.setSeed(42);
      vote.setClassifiers(new Classifier[]{rfVote, abVote, new J48()});
      vote.setCombinationRule(new SelectedTag(Vote.AVERAGE_RULE, Vote.TAGS_RULES));
      classifiers.put("Voting Ensemble", vote);

      return classifiers;
   }

   private Vote buildOptimizedEnsemble(Instances data, GridSearchResult rfResult, GridSearchResult abResult) throws Exception {
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
      return trainedModel != null && trainingHeader != null;
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

      // Phase 5 numeric features - Possession Proxy (indices 25-26)
      attrs.add(new Attribute("homePossessionProxy"));   // 25
      attrs.add(new Attribute("awayPossessionProxy"));   // 26

      // Nominal label (index 27)
      ArrayList<String> labels = new ArrayList<>(List.of("H", "D", "A"));
      attrs.add(new Attribute("result", labels));        // 27

      return attrs;
   }

   /**
    * Converts a list of MatchFeatures into a Weka Instances dataset.
    */
   private Instances toWekaInstances(List<MatchFeatures> featuresList, ArrayList<Attribute> attributes, String name) {
      Instances dataset = new Instances(name, attributes, featuresList.size());
      // CRITICAL: Set class index BEFORE adding any instances
      // This ensures all instances use the correct class attribute structure
      dataset.setClassIndex(IDX_LABEL);

      for (MatchFeatures f : featuresList) {
          Instance inst = toWekaInstance(f, dataset);
          dataset.add(inst);
      }

      return dataset;
   }

   /**
    * Converts a single MatchFeatures into a Weka Instance.
    * Uses PredictionUtils.safe() to guard against NaN/Infinity from edge cases.
    */
   private Instance toWekaInstance(MatchFeatures f, Instances dataset) {
      Instance inst = new DenseInstance(28); // 27 features + 1 label
      inst.setDataset(dataset);

      // Phase 1 & 2 features
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

      // Phase 3 features
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
}
