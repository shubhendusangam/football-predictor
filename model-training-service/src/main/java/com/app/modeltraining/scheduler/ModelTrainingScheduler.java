package com.app.modeltraining.scheduler;

import com.app.modeltraining.service.ModelTrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task to automatically train the model twice monthly
 * Runs on 1st and 15th of each month at 3:00 AM
 */
@Component
@ConditionalOnProperty(name = "training.schedule.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ModelTrainingScheduler {

    private final ModelTrainingService modelTrainingService;

    /**
     * Scheduled training task
     * Cron: 0 0 3 1,15 * * = At 3:00 AM on day 1 and 15 of every month
     */
    @Scheduled(cron = "${training.schedule.cron:0 0 3 1,15 * *}")
    public void scheduledTraining() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("  SCHEDULED MODEL TRAINING STARTED");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        try {
            long startTime = System.currentTimeMillis();
            String report = modelTrainingService.trainModel();
            long duration = System.currentTimeMillis() - startTime;

            log.info("Scheduled training completed successfully in {} ms", duration);
            log.info("Training Report:\n{}", report);

        } catch (Exception e) {
            log.error("Scheduled training failed", e);
        }

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("  SCHEDULED MODEL TRAINING COMPLETED");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}

