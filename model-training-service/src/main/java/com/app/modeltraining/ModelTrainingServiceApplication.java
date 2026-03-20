package com.app.modeltraining;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.app.modeltraining"})
@EnableScheduling
@EnableAsync
@EntityScan(basePackages = {"com.app.common.model"})
@EnableJpaRepositories(basePackages = {"com.app.common.repository"})
public class ModelTrainingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModelTrainingServiceApplication.class, args);
    }
}

