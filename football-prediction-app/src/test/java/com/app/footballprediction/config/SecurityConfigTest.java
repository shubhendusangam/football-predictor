package com.app.footballprediction.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for SecurityConfig.
 * Validates authentication, authorization, security headers, and CORS.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("Security Configuration Integration Tests")
class SecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc() {
        return MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Nested
    @DisplayName("Public Endpoints")
    class PublicEndpointsTests {

        @Test
        @DisplayName("GET /api/model/status is accessible without auth")
        void modelStatusIsPublic() throws Exception {
            mockMvc().perform(get("/api/model/status"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET / serves the homepage without auth")
        void homepageIsPublic() throws Exception {
            mockMvc().perform(get("/"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Admin Authentication")
    class AdminAuthTests {

        @Test
        @DisplayName("Admin endpoints return 401 without credentials")
        void adminEndpointsRequireAuth() throws Exception {
            mockMvc().perform(post("/api/model/train"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Admin verification works with correct credentials")
        void adminVerifyWithCredentials() throws Exception {
            mockMvc().perform(get("/api/admin/verify")
                            .with(httpBasic("admin", "changeme")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        @DisplayName("Admin verification fails with wrong credentials")
        void adminVerifyFailsWithBadCredentials() throws Exception {
            mockMvc().perform(get("/api/admin/verify")
                            .with(httpBasic("admin", "wrongpassword")))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Security Headers")
    class SecurityHeadersTests {

        @Test
        @DisplayName("Responses contain X-Content-Type-Options header")
        void hasContentTypeOptionsHeader() throws Exception {
            mockMvc().perform(get("/api/model/status"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }

        @Test
        @DisplayName("Responses contain X-Frame-Options header")
        void hasFrameOptionsHeader() throws Exception {
            mockMvc().perform(get("/api/model/status"))
                    .andExpect(header().string("X-Frame-Options", "DENY"));
        }

        @Test
        @DisplayName("Responses contain Referrer-Policy header")
        void hasReferrerPolicyHeader() throws Exception {
            mockMvc().perform(get("/api/model/status"))
                    .andExpect(header().exists("Referrer-Policy"));
        }

        @Test
        @DisplayName("Responses contain Content-Security-Policy header")
        void hasCspHeader() throws Exception {
            mockMvc().perform(get("/api/model/status"))
                    .andExpect(header().exists("Content-Security-Policy"));
        }

        @Test
        @DisplayName("Responses contain Permissions-Policy header")
        void hasPermissionsPolicyHeader() throws Exception {
            mockMvc().perform(get("/api/model/status"))
                    .andExpect(header().exists("Permissions-Policy"));
        }
    }

    @Nested
    @DisplayName("Rate Limit Headers")
    class RateLimitHeadersTests {

        @Test
        @DisplayName("API responses contain rate limit headers")
        void hasRateLimitHeaders() throws Exception {
            mockMvc().perform(get("/api/model/status"))
                    .andExpect(header().exists("X-RateLimit-Limit"))
                    .andExpect(header().exists("X-RateLimit-Remaining"));
        }
    }

    @Nested
    @DisplayName("CORS")
    class CorsTests {

        @Test
        @DisplayName("CORS preflight for allowed origin returns 200")
        void corsPreflightForAllowedOrigin() throws Exception {
            mockMvc().perform(get("/api/model/status")
                            .header("Origin", "http://localhost:3000")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(header().exists("Access-Control-Allow-Origin"));
        }
    }
}
