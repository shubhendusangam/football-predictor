package com.app.modeltraining.scheduler;

import com.app.common.model.ModelTrainingHistory;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.ModelTrainingHistoryRepository;
import com.app.modeltraining.service.ModelTrainingService;
import com.app.modeltraining.service.PoissonModelTrainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Scheduled task to automatically train models twice monthly.
 * Trains both the outcome model (Random Forest) and the Poisson score model.
 * Runs on 1st and 15th of each month at 3:00 AM
 */
@Component
@ConditionalOnProperty(name = "training.schedule.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ModelTrainingScheduler {

    private final ModelTrainingService modelTrainingService;
    private final PoissonModelTrainingService poissonModelTrainingService;
    private final ModelTrainingHistoryRepository trainingHistoryRepository;
    private final MatchRepository matchRepository;

    /**
     * Scheduled training task
     * Cron: 0 0 3 1,15 * * = At 3:00 AM on day 1 and 15 of every month
     */
    @Scheduled(cron = "${training.schedule.cron:0 0 3 1,15 * *}")
    public void scheduledTraining() {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("  SCHEDULED MODEL TRAINING STARTED");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        long startTime = System.currentTimeMillis();
        ModelTrainingHistory history = ModelTrainingHistory.builder()
                .trainingTime(LocalDateTime.now())
                .triggerReason("SCHEDULED_SERVICE")
                .matchesUsed((int) matchRepository.count())
                .build();

        try {
            String report = modelTrainingService.trainModel();
            long duration = System.currentTimeMillis() - startTime;

            history.setTrainingDurationMs(duration);
            history.setSuccess(true);
            history.setModelVersion("v" + System.currentTimeMillis());

            log.info("Scheduled outcome model training completed successfully in {} ms", duration);
            log.info("Training Report:\n{}", report);

            // Train Poisson score model
            try {
                String poissonReport = poissonModelTrainingService.trainPoissonModel();
                log.info("Scheduled Poisson model training completed.\n{}", poissonReport);
            } catch (Exception pe) {
                log.error("Scheduled Poisson model training failed (outcome model OK)", pe);
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            history.setTrainingDurationMs(duration);
            history.setSuccess(false);
            history.setErrorMessage(e.getMessage());
            log.error("Scheduled training failed", e);
        }

        trainingHistoryRepository.save(history);

        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("  SCHEDULED MODEL TRAINING COMPLETED");
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}

