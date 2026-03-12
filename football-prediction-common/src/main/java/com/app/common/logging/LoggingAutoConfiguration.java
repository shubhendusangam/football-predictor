package com.app.common.logging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Auto-configuration for the centralized logging framework.
 * <p>
 * Registers:
 * <ul>
 *   <li>{@link MdcLoggingFilter} — populates SLF4J MDC on every request</li>
 *   <li>{@link PerformanceLoggingAspect} — AOP method-level timing</li>
 * </ul>
 * <p>
 * Imported automatically by any module that scans {@code com.app.common}.
 */
@Configuration
public class LoggingAutoConfiguration {

    @Value("${spring.application.name:app}")
    private String appName;

    /**
     * Register the MDC filter with the highest priority so MDC data
     * is available for all downstream filters and handlers.
     */
    @Bean
    public FilterRegistrationBean<MdcLoggingFilter> mdcLoggingFilterRegistration() {
        FilterRegistrationBean<MdcLoggingFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new MdcLoggingFilter());
        reg.addUrlPatterns("/*");
        reg.setName("mdcLoggingFilter");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }

    /**
     * Performance logging aspect — logs method entry/exit/duration
     * for controllers and services.
     */
    @Bean
    public PerformanceLoggingAspect performanceLoggingAspect() {
        return new PerformanceLoggingAspect();
    }
}

