package com.app.footballprediction.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;

import java.io.*;

@Configuration
@Slf4j
public class WekaModelConfig {

   @Value("${model.output.path}")
   private String modelOutputPath;

   /**
    * Loads the trained Random Forest model as a Spring Bean.
    * Returns null if no model file exists yet —
    * ModelTrainingService handles the null case gracefully.
    */
   @Bean(name = "trainedModel")
   public RandomForest trainedModel() {
      File file = new File(modelOutputPath);
      if (!file.exists()) {
         log.info("No saved model found at {}. " +
               "Call POST /api/model/train to train.", modelOutputPath);
         return null;
      }
      try (ObjectInputStream ois = new ObjectInputStream(
            new FileInputStream(file))) {
         RandomForest model = (RandomForest) ois.readObject();
         log.info("✓ Weka model loaded from {}", modelOutputPath);
         return model;
      } catch (Exception e) {
         log.error("Failed to load model from {}: {} - {}",
               modelOutputPath, e.getClass().getSimpleName(), e.getMessage());
         log.error("Model file exists but cannot be deserialized. A new model will be trained.");
         return null;
      }
   }

   /**
    * Loads the Weka attribute schema alongside the model.
    * Required for constructing Instance objects at prediction time.
    */
   @Bean(name = "trainingHeader")
   public Instances trainingHeader() {
      File file = new File(modelOutputPath);
      if (!file.exists()) return null;
      try (ObjectInputStream ois = new ObjectInputStream(
            new FileInputStream(file))) {
         ois.readObject(); // skip the model — read header second
         Instances header = (Instances) ois.readObject();
         log.info("✓ Weka schema loaded from {}", modelOutputPath);
         return header;
      } catch (Exception e) {
         log.error("Failed to load schema from {}: {} - {}",
               modelOutputPath, e.getClass().getSimpleName(), e.getMessage());
         return null;
      }
   }
}