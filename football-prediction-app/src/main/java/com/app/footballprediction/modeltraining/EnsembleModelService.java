package com.app.footballprediction.modeltraining;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.meta.Stacking;
import weka.classifiers.meta.Vote;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SelectedTag;

import java.io.*;
import java.util.*;

/**
 * Ensemble Model Service providing:
 * - K-fold cross-validation
 * - Gradient Boosting (AdaBoost)
 * - Model ensembling (Voting, Stacking)
 * - Hyperparameter grid search
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnsembleModelService {

    @Value("${model.ensemble.path:./data/ensemble_model.model}")
    private String ensembleModelPath;

    @Value("${model.crossvalidation.folds:10}")
    private int kFolds;

    private Classifier ensembleModel;
    private Instances trainingHeader;

    // ══════════════════════════════════════════════════════════════════════
    // K-Fold Cross-Validation
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Performs k-fold cross-validation on the given dataset.
     *
     * @param data       The dataset to evaluate
     * @param classifier The classifier to evaluate
     * @param folds      Number of folds
     * @return CrossValidationResult containing metrics
     */
    public CrossValidationResult performCrossValidation(Instances data, Classifier classifier, int folds) throws Exception {
        log.info("Starting {}-fold cross-validation...", folds);

        Evaluation eval = new Evaluation(data);
        eval.crossValidateModel(classifier, data, folds, new Random(42));

        CrossValidationResult result = CrossValidationResult.builder()
                .accuracy(eval.pctCorrect())
                .kappa(eval.kappa())
                .meanAbsoluteError(eval.meanAbsoluteError())
                .rootMeanSquaredError(eval.rootMeanSquaredError())
                .fMeasure(eval.weightedFMeasure())
                .precision(eval.weightedPrecision())
                .recall(eval.weightedRecall())
                .areaUnderROC(eval.weightedAreaUnderROC())
                .confusionMatrix(eval.confusionMatrix())
                .folds(folds)
                .classDetails(eval.toClassDetailsString("  "))
                .build();

        log.info("Cross-validation complete. Accuracy: {}%", String.format("%.2f", result.getAccuracy()));
        return result;
    }

    /**
     * Performs default k-fold cross-validation (uses configured folds).
     */
    public CrossValidationResult performCrossValidation(Instances data, Classifier classifier) throws Exception {
        return performCrossValidation(data, classifier, kFolds);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Gradient Boosting (AdaBoost)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Creates and trains a Gradient Boosting classifier using AdaBoost.
     *
     * @param data            Training data
     * @param numIterations   Number of boosting iterations
     * @param baseClassifier  Base classifier to boost (null for default J48)
     * @return Trained AdaBoost classifier
     */
    public AdaBoostM1 trainGradientBoosting(Instances data, int numIterations, Classifier baseClassifier) throws Exception {
        log.info("Training Gradient Boosting with {} iterations...", numIterations);

        AdaBoostM1 adaBoost = new AdaBoostM1();
        adaBoost.setNumIterations(numIterations);
        adaBoost.setSeed(42);

        if (baseClassifier != null) {
            adaBoost.setClassifier(baseClassifier);
        } else {
            // Default: use J48 decision tree as base
            J48 j48 = new J48();
            j48.setUnpruned(false);
            j48.setConfidenceFactor(0.25f);
            adaBoost.setClassifier(j48);
        }

        adaBoost.buildClassifier(data);
        log.info("Gradient Boosting model trained successfully.");

        return adaBoost;
    }

    /**
     * Creates Gradient Boosting with default settings.
     */
    public AdaBoostM1 trainGradientBoosting(Instances data) throws Exception {
        return trainGradientBoosting(data, 100, null);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Ensemble Methods
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Creates a voting ensemble combining multiple classifiers.
     * Uses average probability combination.
     *
     * @param data        Training data
     * @param classifiers Array of classifiers to combine
     * @return Trained Vote ensemble
     */
    public Vote trainVotingEnsemble(Instances data, Classifier[] classifiers) throws Exception {
        log.info("Training Voting Ensemble with {} classifiers...", classifiers.length);

        Vote vote = new Vote();
        vote.setClassifiers(classifiers);
        // Use average of probabilities
        vote.setCombinationRule(new SelectedTag(Vote.AVERAGE_RULE, Vote.TAGS_RULES));
        vote.setSeed(42);

        vote.buildClassifier(data);
        log.info("Voting Ensemble trained successfully.");

        return vote;
    }

    /**
     * Creates a stacking ensemble with a meta-classifier.
     *
     * @param data           Training data
     * @param baseClassifiers Base-level classifiers
     * @param metaClassifier  Meta-classifier (null for default LogisticRegression)
     * @return Trained Stacking ensemble
     */
    public Stacking trainStackingEnsemble(Instances data, Classifier[] baseClassifiers, Classifier metaClassifier) throws Exception {
        log.info("Training Stacking Ensemble with {} base classifiers...", baseClassifiers.length);

        Stacking stacking = new Stacking();
        stacking.setClassifiers(baseClassifiers);
        stacking.setSeed(42);
        stacking.setNumFolds(5); // Internal CV for meta-classifier training

        if (metaClassifier != null) {
            stacking.setMetaClassifier(metaClassifier);
        }

        stacking.buildClassifier(data);
        log.info("Stacking Ensemble trained successfully.");

        return stacking;
    }

    /**
     * Creates a default ensemble combining Random Forest, AdaBoost, and J48.
     */
    public Vote trainDefaultEnsemble(Instances data) throws Exception {
        // Random Forest
        RandomForest rf = new RandomForest();
        rf.setNumIterations(100);
        rf.setNumFeatures(5);
        rf.setSeed(42);

        // AdaBoost with J48
        AdaBoostM1 adaBoost = new AdaBoostM1();
        adaBoost.setNumIterations(50);
        adaBoost.setSeed(42);
        J48 j48Base = new J48();
        j48Base.setConfidenceFactor(0.25f);
        adaBoost.setClassifier(j48Base);

        // J48 Decision Tree
        J48 j48 = new J48();
        j48.setConfidenceFactor(0.25f);
        j48.setMinNumObj(5);

        Classifier[] classifiers = {rf, adaBoost, j48};
        return trainVotingEnsemble(data, classifiers);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Hyperparameter Grid Search
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Performs grid search for Random Forest hyperparameters.
     *
     * @param data Training data
     * @return Best configuration with metrics
     */
    public GridSearchResult gridSearchRandomForest(Instances data) throws Exception {
        log.info("Starting Grid Search for Random Forest...");

        int[] numTreesOptions = {50, 100, 150, 200};
        int[] numFeaturesOptions = {3, 4, 5, 6};
        int[] maxDepthOptions = {0, 10, 20}; // 0 = unlimited

        GridSearchResult bestResult = null;
        double bestAccuracy = 0;

        int totalCombinations = numTreesOptions.length * numFeaturesOptions.length * maxDepthOptions.length;
        int current = 0;

        for (int numTrees : numTreesOptions) {
            for (int numFeatures : numFeaturesOptions) {
                for (int maxDepth : maxDepthOptions) {
                    current++;
                    log.debug("Testing combination {}/{}: trees={}, features={}, depth={}",
                            current, totalCombinations, numTrees, numFeatures, maxDepth);

                    RandomForest rf = new RandomForest();
                    rf.setNumIterations(numTrees);
                    rf.setNumFeatures(numFeatures);
                    rf.setMaxDepth(maxDepth);
                    rf.setSeed(42);

                    Evaluation eval = new Evaluation(data);
                    eval.crossValidateModel(rf, data, 5, new Random(42));

                    double accuracy = eval.pctCorrect();
                    if (accuracy > bestAccuracy) {
                        bestAccuracy = accuracy;

                        Map<String, Object> params = new HashMap<>();
                        params.put("numTrees", numTrees);
                        params.put("numFeatures", numFeatures);
                        params.put("maxDepth", maxDepth);

                        bestResult = GridSearchResult.builder()
                                .classifierName("RandomForest")
                                .bestParams(params)
                                .accuracy(accuracy)
                                .kappa(eval.kappa())
                                .fMeasure(eval.weightedFMeasure())
                                .build();
                    }
                }
            }
        }

        if (bestResult != null) {
            log.info("Grid Search (RandomForest) complete. Best accuracy: {}% with params: {}",
                    String.format("%.2f", bestAccuracy), bestResult.getBestParams());
        }
        return bestResult;
    }

    /**
     * Performs grid search for AdaBoost hyperparameters.
     *
     * @param data Training data
     * @return Best configuration with metrics
     */
    public GridSearchResult gridSearchAdaBoost(Instances data) throws Exception {
        log.info("Starting Grid Search for AdaBoost...");

        int[] numIterationsOptions = {25, 50, 100, 150};
        float[] confidenceFactorOptions = {0.1f, 0.25f, 0.5f};
        int[] minNumObjOptions = {2, 5, 10};

        GridSearchResult bestResult = null;
        double bestAccuracy = 0;

        for (int numIterations : numIterationsOptions) {
            for (float confidenceFactor : confidenceFactorOptions) {
                for (int minNumObj : minNumObjOptions) {
                    J48 j48 = new J48();
                    j48.setConfidenceFactor(confidenceFactor);
                    j48.setMinNumObj(minNumObj);

                    AdaBoostM1 adaBoost = new AdaBoostM1();
                    adaBoost.setNumIterations(numIterations);
                    adaBoost.setClassifier(j48);
                    adaBoost.setSeed(42);

                    Evaluation eval = new Evaluation(data);
                    eval.crossValidateModel(adaBoost, data, 5, new Random(42));

                    double accuracy = eval.pctCorrect();
                    if (accuracy > bestAccuracy) {
                        bestAccuracy = accuracy;

                        Map<String, Object> params = new HashMap<>();
                        params.put("numIterations", numIterations);
                        params.put("confidenceFactor", confidenceFactor);
                        params.put("minNumObj", minNumObj);

                        bestResult = GridSearchResult.builder()
                                .classifierName("AdaBoostM1")
                                .bestParams(params)
                                .accuracy(accuracy)
                                .kappa(eval.kappa())
                                .fMeasure(eval.weightedFMeasure())
                                .build();
                    }
                }
            }
        }

        if (bestResult != null) {
            log.info("Grid Search (AdaBoost) complete. Best accuracy: {}% with params: {}",
                    String.format("%.2f", bestAccuracy), bestResult.getBestParams());
        }
        return bestResult;
    }

    /**
     * Builds the best classifier based on grid search results.
     */
    public Classifier buildBestClassifier(GridSearchResult result) {
        switch (result.getClassifierName()) {
            case "RandomForest":
                RandomForest rf = new RandomForest();
                rf.setNumIterations((Integer) result.getBestParams().get("numTrees"));
                rf.setNumFeatures((Integer) result.getBestParams().get("numFeatures"));
                rf.setMaxDepth((Integer) result.getBestParams().get("maxDepth"));
                rf.setSeed(42);
                return rf;

            case "AdaBoostM1":
                J48 j48 = new J48();
                j48.setConfidenceFactor((Float) result.getBestParams().get("confidenceFactor"));
                j48.setMinNumObj((Integer) result.getBestParams().get("minNumObj"));

                AdaBoostM1 adaBoost = new AdaBoostM1();
                adaBoost.setNumIterations((Integer) result.getBestParams().get("numIterations"));
                adaBoost.setClassifier(j48);
                adaBoost.setSeed(42);
                return adaBoost;

            default:
                throw new IllegalArgumentException("Unknown classifier: " + result.getClassifierName());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Model Comparison
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Compares multiple classifiers using cross-validation.
     *
     * @param data        Training data
     * @param classifiers Map of classifier names to classifiers
     * @return List of comparison results sorted by accuracy
     */
    public List<ModelComparisonResult> compareModels(Instances data, Map<String, Classifier> classifiers) throws Exception {
        log.info("Comparing {} models using {}-fold cross-validation...", classifiers.size(), kFolds);

        List<ModelComparisonResult> results = new ArrayList<>();

        for (Map.Entry<String, Classifier> entry : classifiers.entrySet()) {
            String name = entry.getKey();
            Classifier classifier = entry.getValue();

            log.info("Evaluating {}...", name);

            Evaluation eval = new Evaluation(data);
            long startTime = System.currentTimeMillis();
            eval.crossValidateModel(classifier, data, kFolds, new Random(42));
            long duration = System.currentTimeMillis() - startTime;

            ModelComparisonResult result = ModelComparisonResult.builder()
                    .modelName(name)
                    .accuracy(eval.pctCorrect())
                    .kappa(eval.kappa())
                    .fMeasure(eval.weightedFMeasure())
                    .precision(eval.weightedPrecision())
                    .recall(eval.weightedRecall())
                    .areaUnderROC(eval.weightedAreaUnderROC())
                    .trainingTimeMs(duration)
                    .build();

            results.add(result);
            log.info("{}: Accuracy={}%, Time={}ms", name, String.format("%.2f", result.getAccuracy()), duration);
        }

        // Sort by accuracy descending
        results.sort((a, b) -> Double.compare(b.getAccuracy(), a.getAccuracy()));

        return results;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Prediction
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Make prediction using the ensemble model.
     */
    public double[] predict(Instance instance) throws Exception {
        if (ensembleModel == null || trainingHeader == null) {
            throw new IllegalStateException("Ensemble model not loaded.");
        }

        instance.setDataset(trainingHeader);
        return ensembleModel.distributionForInstance(instance);
    }

    public boolean isModelLoaded() {
        return ensembleModel != null && trainingHeader != null;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Persistence
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Save the ensemble model to disk.
     */
    public void saveModel(Classifier model, Instances header) throws IOException {
        this.ensembleModel = model;
        this.trainingHeader = header;

        File file = new File(ensembleModelPath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            log.warn("Failed to create directory: {}", parentDir.getAbsolutePath());
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(model);
            oos.writeObject(header);
        }

        log.info("Ensemble model saved to {}", ensembleModelPath);
    }

    /**
     * Load the ensemble model from disk.
     */
    public void loadModel() throws IOException, ClassNotFoundException {
        File file = new File(ensembleModelPath);
        if (!file.exists()) {
            throw new IllegalStateException("Ensemble model not found at " + ensembleModelPath);
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            this.ensembleModel = (Classifier) ois.readObject();
            this.trainingHeader = (Instances) ois.readObject();
        }

        log.info("Ensemble model loaded from {}", ensembleModelPath);
    }

    public Classifier getEnsembleModel() {
        return ensembleModel;
    }

    public Instances getTrainingHeader() {
        return trainingHeader;
    }

    public void setEnsembleModel(Classifier model) {
        this.ensembleModel = model;
    }

    public void setTrainingHeader(Instances header) {
        this.trainingHeader = header;
    }
}

