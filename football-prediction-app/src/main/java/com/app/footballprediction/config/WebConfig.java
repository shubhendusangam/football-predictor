package com.app.footballprediction.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for the Football Prediction application.
 *
 * Configures URL path handling to allow encoded slashes in path variables.
 * This is needed because season identifiers can contain "/" (e.g., "2025/26").
 *
 * The controller has been updated to handle both "2023-24" and "2025/26" formats
 * by using multiple path patterns.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    // URL path configuration is handled via application.properties
    // and the controller's multiple path mappings
}

