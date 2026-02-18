package com.app.footballprediction.modeltraining;

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

import java.io.*;
import java.util.*;

/**
 * Advanced ensemble model combining RandomForest, Gradient Boosting (AdaBoost),
 * and Logistic Regression.
 *
 * Architecture:
 * 1. RandomForest - 100 trees
 * 2. AdaBoostM1 - Gradient boosting with decision trees (simulates XGBoost)
 * 3. Logistic Regression - Meta-model that combines predictions
 *
 * The meta-model (Logistic Regression) learns to optimally weight the predictions
 * from the base models through stacking.
 */
@Service
@Slf4j
public class StackedEnsembleService implements Serializable {

    private static final long serialVersionUID = 1L;

    // Base models
    private RandomForest randomForest;
    private AdaBoostM1 gradientBoosting;

    // Meta model
    private Logistic logisticRegression;

    // Training header for predictions
    private Instances trainingHeader;

    /**
     * Train the stacked ensemble model.
     *
     * @param trainData Training dataset
     * @param validationData Validation dataset for stacking
     * @throws Exception if training fails
     */
    public void trainStackedEnsemble(Instances trainData, Instances validationData) throws Exception {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("Training Stacked Ensemble:");
        log.info("  - RandomForest (100 trees)");
        log.info("  - Gradient Boosting (AdaBoostM1, 100 iterations)");
        log.info("  - Logistic Regression (meta-model)");
        log.info("═══════════════════════════════════════════════════════════");

        this.trainingHeader = new Instances(trainData, 0);

        // Step 1: Train RandomForest
        log.info("Step 1/3: Training RandomForest...");
        randomForest = new RandomForest();
        randomForest.setNumIterations(100);  // 100 trees
        randomForest.setNumFeatures(5);      // sqrt(25) ≈ 5 features per split
        randomForest.setSeed(42);
        randomForest.buildClassifier(trainData);
        log.info("  ✓ RandomForest trained with 100 trees");

        // Step 2: Train Gradient Boosting (AdaBoostM1 simulates XGBoost)
        log.info("Step 2/3: Training Gradient Boosting (AdaBoostM1)...");
        gradientBoosting = new AdaBoostM1();

        // Use REPTree as base learner (similar to gradient boosting trees)
        REPTree baseTree = new REPTree();
        baseTree.setMaxDepth(6);  // Similar to XGBoost max_depth
        baseTree.setMinNum(2);
        baseTree.setNoPruning(false);

        gradientBoosting.setClassifier(baseTree);
        gradientBoosting.setNumIterations(100);  // 100 boosting iterations
        gradientBoosting.setSeed(42);
        gradientBoosting.buildClassifier(trainData);
        log.info("  ✓ Gradient Boosting trained with 100 iterations");

        // Step 3: Train Logistic Regression meta-model
        log.info("Step 3/3: Training Logistic Regression meta-model...");
        Instances metaData = createMetaDataset(validationData);
        logisticRegression = new Logistic();
        logisticRegression.setMaxIts(100);  // Maximum iterations
        logisticRegression.buildClassifier(metaData);
        log.info("  ✓ Logistic Regression meta-model trained");

        log.info("═══════════════════════════════════════════════════════════");
        log.info("✓ Stacked ensemble training complete!");
        log.info("═══════════════════════════════════════════════════════════");
    }

    /**
     * Create meta-dataset with predictions from base models.
     */
    private Instances createMetaDataset(Instances originalData) throws Exception {
        // Create attributes for meta-dataset
        ArrayList<Attribute> metaAttributes = new ArrayList<>();

        // RandomForest predictions (3 probabilities)
        metaAttributes.add(new Attribute("rf_prob_home"));
        metaAttributes.add(new Attribute("rf_prob_draw"));
        metaAttributes.add(new Attribute("rf_prob_away"));

        // Gradient Boosting predictions (3 probabilities)
        metaAttributes.add(new Attribute("gb_prob_home"));
        metaAttributes.add(new Attribute("gb_prob_draw"));
        metaAttributes.add(new Attribute("gb_prob_away"));

        // Class attribute (same as original)
        metaAttributes.add(originalData.classAttribute());

        // Create meta-dataset
        Instances metaData = new Instances("MetaData", metaAttributes, originalData.numInstances());
        metaData.setClassIndex(metaAttributes.size() - 1);

        // Generate predictions from base models
        for (int i = 0; i < originalData.numInstances(); i++) {
            Instance original = originalData.instance(i);

            // Get RandomForest predictions
            double[] rfProbs = randomForest.distributionForInstance(original);

            // Get Gradient Boosting predictions
            double[] gbProbs = gradientBoosting.distributionForInstance(original);

            // Create meta-instance
            double[] metaValues = new double[7];
            metaValues[0] = rfProbs[0];  // RF prob H
            metaValues[1] = rfProbs[1];  // RF prob D
            metaValues[2] = rfProbs[2];  // RF prob A
            metaValues[3] = gbProbs[0];  // GB prob H
            metaValues[4] = gbProbs[1];  // GB prob D
            metaValues[5] = gbProbs[2];  // GB prob A
            metaValues[6] = original.classValue(); // Actual label

            Instance metaInstance = new DenseInstance(1.0, metaValues);
            metaData.add(metaInstance);
        }

        return metaData;
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

        // Get RandomForest predictions
        double[] rfProbs = randomForest.distributionForInstance(instance);

        // Get Gradient Boosting predictions
        double[] gbProbs = gradientBoosting.distributionForInstance(instance);

        // Create meta-instance
        ArrayList<Attribute> metaAttributes = new ArrayList<>();
        metaAttributes.add(new Attribute("rf_prob_home"));
        metaAttributes.add(new Attribute("rf_prob_draw"));
        metaAttributes.add(new Attribute("rf_prob_away"));
        metaAttributes.add(new Attribute("gb_prob_home"));
        metaAttributes.add(new Attribute("gb_prob_draw"));
        metaAttributes.add(new Attribute("gb_prob_away"));

        // Add class attribute
        ArrayList<String> classValues = new ArrayList<>();
        classValues.add("H");
        classValues.add("D");
        classValues.add("A");
        metaAttributes.add(new Attribute("result", classValues));

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

        // Get final prediction from logistic regression
        return logisticRegression.distributionForInstance(metaInstance);
    }

    /**
     * Get the predicted class label.
     */
    public String predictClass(Instance instance) throws Exception {
        double[] probs = predictProbabilities(instance);

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

