package com.app.footballprediction.modeltraining;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import weka.classifiers.functions.Logistic;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.trees.RandomForest;
import weka.classifiers.trees.REPTree;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;
import weka.filters.Filter;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SpreadSubsample;

import java.io.*;
import java.util.*;

/**
 * Advanced ensemble model combining RandomForest, AdaBoost with REPTree,
 * and Logistic Regression using proper stacking with out-of-fold (OOF) predictions.
 *
 * <p><strong>Architecture:</strong></p>
 * <ol>
 *   <li>RandomForest - 200 trees (base model 1)</li>
 *   <li>AdaBoostM1 with REPTree - 100 iterations (base model 2)</li>
 *   <li>Logistic Regression - Meta-model trained on OOF predictions</li>
 * </ol>
 *
 * <p><strong>Stacking via OOF:</strong> Instead of a simple holdout split,
 * k-fold cross-validation generates predictions for each training instance
 * using models that never saw that instance, eliminating data leakage
 * and utilizing all training data for the meta-model.</p>
 */
@Service
@Slf4j
public class StackedEnsembleService implements Serializable {

    private static final long serialVersionUID = 3L;

    /** Number of folds for out-of-fold prediction generation */
    private static final int STACKING_FOLDS = 5;

    /**
     * Draw probability threshold boost.
     * When draw probability exceeds (max(H,A) - threshold), predict Draw.
     * Default 0.05 — effectively gives draw a slight edge to improve recall.
     */
    @Getter @Setter
    private double drawThreshold = 0.05;

    // Base models (trained on full data after OOF generation)
    private RandomForest randomForest;
    private AdaBoostM1 gradientBoosting;

    // Meta model (trained on OOF predictions)
    private Logistic logisticRegression;

    // Training header for predictions
    private Instances trainingHeader;

    /**
     * Train the stacked ensemble model using out-of-fold predictions.
     *
     * <p>Process:
     * <ol>
     *   <li>Merge trainData and validationData (OOF replaces holdout)</li>
     *   <li>Generate OOF predictions via k-fold CV for meta-model training</li>
     *   <li>Train final base models on ALL training data</li>
     *   <li>Train Logistic Regression meta-model on OOF predictions</li>
     * </ol>
     *
     * @param trainData Training dataset
     * @param validationData Additional data (merged with trainData since OOF replaces holdout)
     * @throws Exception if training fails
     */
    public void trainStackedEnsemble(Instances trainData, Instances validationData) throws Exception {
        // Merge trainData + validationData since we use OOF instead of holdout
        Instances fullTrainData = new Instances(trainData);
        for (int i = 0; i < validationData.numInstances(); i++) {
            fullTrainData.add(validationData.instance(i));
        }

        log.info("═══════════════════════════════════════════════════════════");
        log.info("Training Stacked Ensemble (with {}-fold OOF predictions):", STACKING_FOLDS);
        log.info("  - RandomForest (200 trees)");
        log.info("  - AdaBoost with REPTree (100 iterations)");
        log.info("  - Logistic Regression (meta-model on OOF predictions)");
        log.info("  - Total training instances: {}", fullTrainData.numInstances());
        log.info("═══════════════════════════════════════════════════════════");

        this.trainingHeader = new Instances(fullTrainData, 0);

        // Step 1: Generate out-of-fold predictions via k-fold CV
        log.info("Step 1/3: Generating out-of-fold predictions ({}-fold CV)...", STACKING_FOLDS);
        Instances metaData = generateOutOfFoldPredictions(fullTrainData);
        log.info("  ✓ Generated {} out-of-fold meta-instances", metaData.numInstances());

        // Step 2: Train final base models on ALL training data (with SMOTE + CostSensitive)
        log.info("Step 2/3: Training final base models on full training data (cost-sensitive)...");
        Instances balancedFull = applySMOTE(fullTrainData);

        randomForest = createRandomForest();
        randomForest.buildClassifier(balancedFull);
        log.info("  ✓ RandomForest trained with 200 trees, {} features/split, maxDepth=20",
                (int) Math.ceil(Math.sqrt(fullTrainData.numAttributes() - 1)));

        gradientBoosting = createAdaBoost();
        gradientBoosting.buildClassifier(balancedFull);
        log.info("  ✓ AdaBoost trained with 100 iterations, REPTree base (maxDepth=6)");

        // Step 3: Train Logistic Regression meta-model on OOF predictions
        log.info("Step 3/3: Training Logistic Regression meta-model on OOF data...");
        logisticRegression = new Logistic();
        logisticRegression.setMaxIts(200);
        logisticRegression.buildClassifier(metaData);
        log.info("  ✓ Logistic Regression meta-model trained on {} OOF instances", metaData.numInstances());

        log.info("═══════════════════════════════════════════════════════════");
        log.info("✓ Stacked ensemble training complete!");
        log.info("═══════════════════════════════════════════════════════════");
    }

    /**
     * Generate out-of-fold predictions using k-fold cross-validation.
     * Each instance gets predictions from models that were NOT trained on it,
     * preventing data leakage in the stacking meta-model.
     */
    private Instances generateOutOfFoldPredictions(Instances data) throws Exception {
        int numInstances = data.numInstances();
        int foldSize = numInstances / STACKING_FOLDS;

        // Storage for OOF predictions
        double[][] rfOofProbs = new double[numInstances][3];
        double[][] gbOofProbs = new double[numInstances][3];

        for (int fold = 0; fold < STACKING_FOLDS; fold++) {
            int foldStart = fold * foldSize;
            int foldEnd = (fold == STACKING_FOLDS - 1) ? numInstances : (fold + 1) * foldSize;

            // Split into fold-train and fold-validation
            Instances foldTrain = new Instances(data, 0);
            Instances foldVal = new Instances(data, 0);

            for (int i = 0; i < numInstances; i++) {
                if (i >= foldStart && i < foldEnd) {
                    foldVal.add(data.instance(i));
                } else {
                    foldTrain.add(data.instance(i));
                }
            }

            // Train fold-level base models (with SMOTE applied to fold training data)
            Instances balancedFoldTrain = applySMOTE(foldTrain);

            RandomForest foldRF = createRandomForest();
            foldRF.buildClassifier(balancedFoldTrain);

            AdaBoostM1 foldGB = createAdaBoost();
            foldGB.buildClassifier(balancedFoldTrain);

            // Generate OOF predictions for the held-out fold
            for (int i = foldStart; i < foldEnd; i++) {
                Instance instance = data.instance(i);
                rfOofProbs[i] = foldRF.distributionForInstance(instance);
                gbOofProbs[i] = foldGB.distributionForInstance(instance);
            }

            log.debug("  Fold {}/{}: train={}, val={}", fold + 1, STACKING_FOLDS,
                    foldTrain.numInstances(), foldVal.numInstances());
        }

        // Build meta-dataset from all OOF predictions
        return buildMetaDataset(data, rfOofProbs, gbOofProbs);
    }

    /**
     * Build the meta-dataset from out-of-fold predictions.
     */
    private Instances buildMetaDataset(Instances originalData, double[][] rfProbs, double[][] gbProbs) {
        ArrayList<Attribute> metaAttributes = createMetaAttributes(originalData);

        Instances metaData = new Instances("MetaData", metaAttributes, originalData.numInstances());
        metaData.setClassIndex(metaAttributes.size() - 1);

        for (int i = 0; i < originalData.numInstances(); i++) {
            double[] metaValues = new double[7];
            metaValues[0] = rfProbs[i][0];
            metaValues[1] = rfProbs[i][1];
            metaValues[2] = rfProbs[i][2];
            metaValues[3] = gbProbs[i][0];
            metaValues[4] = gbProbs[i][1];
            metaValues[5] = gbProbs[i][2];
            metaValues[6] = originalData.instance(i).classValue();

            Instance metaInstance = new DenseInstance(1.0, metaValues);
            metaData.add(metaInstance);
        }

        return metaData;
    }

    /**
     * Create meta-attributes for the stacking meta-dataset.
     *
     * <p><strong>Important:</strong> The class attribute must be a fresh copy,
     * NOT the same object from the original dataset. Sharing the Attribute
     * object between two Instances causes Weka's internal string store to
     * become corrupted, leading to {@code IndexOutOfBoundsException} when
     * RandomForest later calls {@code getRandomNumberGenerator()} on the
     * original data.</p>
     */
    private ArrayList<Attribute> createMetaAttributes(Instances originalData) {
        ArrayList<Attribute> metaAttributes = new ArrayList<>();
        metaAttributes.add(new Attribute("rf_prob_home"));
        metaAttributes.add(new Attribute("rf_prob_draw"));
        metaAttributes.add(new Attribute("rf_prob_away"));
        metaAttributes.add(new Attribute("gb_prob_home"));
        metaAttributes.add(new Attribute("gb_prob_draw"));
        metaAttributes.add(new Attribute("gb_prob_away"));

        // Create a FRESH copy of the class attribute to avoid sharing the
        // same Attribute object between the meta-dataset and the original
        // training data. Weka's Attribute maintains an internal string store
        // that can get corrupted when the same object is used in multiple
        // Instances datasets.
        Attribute originalClass = originalData.classAttribute();
        ArrayList<String> classValues = new ArrayList<>();
        for (int i = 0; i < originalClass.numValues(); i++) {
            classValues.add(originalClass.value(i));
        }
        metaAttributes.add(new Attribute(originalClass.name(), classValues));
        return metaAttributes;
    }

    /**
     * Create a configured RandomForest instance (reusable factory).
     */
    private RandomForest createRandomForest() {
        RandomForest rf = new RandomForest();
        rf.setNumIterations(200);
        rf.setNumFeatures(8);       // sqrt(51) ≈ 7.1 → 8 features per split
        rf.setMaxDepth(20);
        rf.setSeed(42);
        return rf;
    }

    /**
     * Create a configured AdaBoost instance with REPTree base learner (reusable factory).
     */
    private AdaBoostM1 createAdaBoost() {
        AdaBoostM1 gb = new AdaBoostM1();
        REPTree baseTree = new REPTree();
        baseTree.setMaxDepth(6);
        baseTree.setMinNum(2);
        baseTree.setNoPruning(false);
        gb.setClassifier(baseTree);
        gb.setNumIterations(100);
        gb.setSeed(42);
        return gb;
    }

    /**
     * Apply class balancing (SpreadSubsample + Resample) to equalize class distribution.
     * This is a self-contained SMOTE-like rebalancing to improve draw recall.
     *
     * @param data Training data (potentially imbalanced)
     * @return Balanced dataset, or original if balancing fails
     */
    private Instances applySMOTE(Instances data) {
        try {
            // Stage 1: SpreadSubsample to equalize class counts (downsample majority)
            SpreadSubsample spreadFilter = new SpreadSubsample();
            spreadFilter.setDistributionSpread(1.0);
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
        } catch (Exception e) {
            log.debug("Class balancing failed in stacked ensemble, using original data: {}", e.getMessage());
            return data;
        }
    }

    /**
     * Make prediction using the stacked ensemble.
     *
     * @param instance Input instance
     * @return Probability distribution [H, D, A]
     */
    public double[] predictProbabilities(Instance instance) throws Exception {
        if (randomForest == null || gradientBoosting == null || logisticRegression == null) {
            throw new IllegalStateException("Model not trained. Call trainStackedEnsemble() first.");
        }

        // Get base model predictions
        double[] rfProbs = randomForest.distributionForInstance(instance);
        double[] gbProbs = gradientBoosting.distributionForInstance(instance);

        // Create meta-instance
        ArrayList<Attribute> metaAttributes = createMetaAttributes(trainingHeader);

        Instances metaHeader = new Instances("MetaPrediction", metaAttributes, 0);
        metaHeader.setClassIndex(metaAttributes.size() - 1);

        double[] metaValues = new double[7];
        metaValues[0] = rfProbs[0];
        metaValues[1] = rfProbs[1];
        metaValues[2] = rfProbs[2];
        metaValues[3] = gbProbs[0];
        metaValues[4] = gbProbs[1];
        metaValues[5] = gbProbs[2];
        metaValues[6] = Utils.missingValue(); // Unknown class

        Instance metaInstance = new DenseInstance(1.0, metaValues);
        metaInstance.setDataset(metaHeader);

        // Get final prediction from logistic regression meta-model
        return logisticRegression.distributionForInstance(metaInstance);
    }

    /**
     * Get the predicted class label with draw threshold tuning.
     * When the draw probability is close to the highest class,
     * the threshold gives draw a slight edge to improve draw recall.
     */
    public String predictClass(Instance instance) throws Exception {
        double[] probs = predictProbabilities(instance);
        // probs[0]=H, probs[1]=D, probs[2]=A

        double maxNonDraw = Math.max(probs[0], probs[2]);

        // If draw prob + threshold exceeds the max non-draw prob, predict Draw
        if (probs[1] + drawThreshold >= maxNonDraw) {
            return "D";
        }

        int maxIdx = 0;
        for (int i = 1; i < probs.length; i++) {
            if (probs[i] > probs[maxIdx]) {
                maxIdx = i;
            }
        }

        String[] labels = {"H", "D", "A"};
        return labels[maxIdx];
    }

    /**
     * Save the ensemble model to disk.
     */
    public void saveModel(String outputPath) throws Exception {
        log.info("Saving stacked ensemble model to: {}", outputPath);

        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(outputPath))) {
            oos.writeObject(this);
        }

        log.info("✓ Stacked ensemble model saved successfully");
    }

    /**
     * Load the ensemble model from disk.
     */
    public static StackedEnsembleService loadModel(String path) throws Exception {
        log.info("Loading stacked ensemble model from: {}", path);

        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(path))) {
            StackedEnsembleService model = (StackedEnsembleService) ois.readObject();
            log.info("✓ Stacked ensemble model loaded successfully");
            return model;
        }
    }

    // Getters
    public RandomForest getRandomForest() {
        return randomForest;
    }

    public AdaBoostM1 getGradientBoosting() {
        return gradientBoosting;
    }

    public Logistic getLogisticRegression() {
        return logisticRegression;
    }

    public boolean isModelTrained() {
        return randomForest != null && gradientBoosting != null && logisticRegression != null;
    }
}
