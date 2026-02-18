package com.app.footballprediction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Simple Spring Boot context test to verify application loads correctly.
 */
@SpringBootTest
@ActiveProfiles("test")
class SpringContextTest {

    @Test
    void contextLoads() {
        // This test will pass if the Spring application context loads successfully
    }
}
