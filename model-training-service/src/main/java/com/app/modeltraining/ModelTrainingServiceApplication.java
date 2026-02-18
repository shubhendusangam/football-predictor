package com.app.modeltraining;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ModelTrainingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModelTrainingServiceApplication.class, args);
    }
}

