package com.app.footballprediction.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for the Football Prediction application.
 *
 * <p>Features:
 * <ul>
 *   <li>HTTP Basic Authentication for admin endpoints</li>
 *   <li>CORS configuration for frontend origins</li>
 *   <li>Security headers (CSP, HSTS, X-Content-Type-Options, Referrer-Policy, Permissions-Policy)</li>
 *   <li>Stateless session management (no server-side sessions)</li>
 *   <li>CSRF disabled for stateless REST API</li>
 *   <li>Separate filter chain for H2 console (dev) with relaxed frame options</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:changeme}")
    private String adminPassword;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String allowedOrigins;

    @Value("${cors.max-age-seconds:3600}")
    private long corsMaxAgeSeconds;

    @Value("${security.hsts.enabled:false}")
    private boolean hstsEnabled;

    @Value("${security.csp.enabled:true}")
    private boolean cspEnabled;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.builder()
                .username(adminUsername)
                .password(passwordEncoder().encode(adminPassword))
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(admin);
    }

    // ── CORS ────────────────────────────────────────────────────

    /**
     * CORS configuration source — allows frontend origins to access the API.
     * Origins are configurable via {@code cors.allowed-origins} property.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Parse allowed origins from comma-separated property
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        configuration.setAllowedOrigins(origins);

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Origin",
                "X-Requested-With", "Cache-Control", "If-None-Match"
        ));
        configuration.setExposedHeaders(List.of(
                "X-RateLimit-Remaining", "X-RateLimit-Limit",
                "ETag", "Cache-Control", "Content-Disposition"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(corsMaxAgeSeconds);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    // ── H2 Console Filter Chain (dev only, relaxed headers) ────

    /**
     * Separate security filter chain for the H2 console.
     * H2 console requires frames (X-Frame-Options: SAMEORIGIN) and no CSRF.
     * This chain matches ONLY /h2-console/** paths.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/h2-console/**")
            .csrf(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
            )
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    // ── Main Application Filter Chain ──────────────────────────

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Enable CORS with the configured source
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Disable CSRF for stateless REST API
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless sessions — no server-side session storage
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // ── Security Headers ────────────────────────────────────
            .headers(headers -> {
                // X-Content-Type-Options: nosniff — prevents MIME-type sniffing
                headers.contentTypeOptions(Customizer.withDefaults());

                // X-Frame-Options: DENY — clickjacking protection
                headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny);

                // X-XSS-Protection: 0 — modern browsers use CSP instead
                // (disabled per OWASP recommendation for modern apps)
                headers.xssProtection(xss -> xss.disable());

                // Referrer-Policy: strict-origin-when-cross-origin
                headers.referrerPolicy(referrer -> referrer
                    .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                            .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                );

                // Permissions-Policy: restrict sensitive browser APIs
                @SuppressWarnings("removal")
                var unused = headers.permissionsPolicy(permissions -> permissions
                    .policy("camera=(), microphone=(), geolocation=(), payment=()")
                );

                // HSTS — only enable in production (behind HTTPS)
                if (hstsEnabled) {
                    headers.httpStrictTransportSecurity(hsts -> hsts
                        .maxAgeInSeconds(31536000)
                        .includeSubDomains(true)
                        .preload(true)
                    );
                }

                // Content-Security-Policy — controls what resources the browser can load
                if (cspEnabled) {
                    headers.contentSecurityPolicy(csp -> csp
                        .policyDirectives(
                            "default-src 'self'; " +
                            "script-src 'self' 'unsafe-inline' 'unsafe-eval' " +
                                "https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                            "style-src 'self' 'unsafe-inline' " +
                                "https://cdn.jsdelivr.net https://cdnjs.cloudflare.com https://fonts.googleapis.com; " +
                            "font-src 'self' https://fonts.gstatic.com " +
                                "https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                            "img-src 'self' data: https: blob:; " +
                            "connect-src 'self'; " +
                            "frame-src 'self'; " +
                            "frame-ancestors 'self'"
                        )
                    );
                }
            })

            // ── Authorization Rules ─────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Static resources
                .requestMatchers("/", "/index.html", "/components-demo.html", "/season-team.html",
                        "/css/**", "/js/**", "/assets/**", "/images/**",
                        "/manifest.json", "/README.md", "/favicon.ico").permitAll()

                // Public API endpoints
                .requestMatchers("/api/predict", "/api/teams", "/api/teams/**", "/api/model/status").permitAll()
                .requestMatchers("/api/teams/logo-status").permitAll()
                .requestMatchers("/api/predictions", "/api/predictions/**").permitAll()
                .requestMatchers("/api/matches/**").permitAll()
                .requestMatchers("/api/dashboard/**").permitAll()
                .requestMatchers("/api/league/**").permitAll()
                .requestMatchers("/api/external/**").permitAll()
                .requestMatchers("/api/news/**").permitAll()
                .requestMatchers("/api/betting/**").permitAll()
                .requestMatchers("/api/analytics/**").permitAll()
                .requestMatchers("/api/insights/**").permitAll()
                .requestMatchers("/api/seasons/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/season/**").permitAll()

                // Cache monitoring — public read-only
                .requestMatchers("/api/cache/status", "/api/cache/stats", "/api/cache/stats/**").permitAll()

                // Actuator health — public
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                // Admin verification
                .requestMatchers("/api/admin/verify").authenticated()

                // Admin-only endpoints
                .requestMatchers("/api/model/train/**", "/api/model/train").hasRole("ADMIN")
                .requestMatchers("/api/model/grid-search", "/api/model/compare").hasRole("ADMIN")
                .requestMatchers("/api/data/reload", "/api/data/update").hasRole("ADMIN")
                .requestMatchers("/api/cache/clear", "/api/cache/clear/**").hasRole("ADMIN")
                .requestMatchers("/api/cache/invalidate/**").hasRole("ADMIN")
                .requestMatchers("/api/cache/warmup").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/teams/cache").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/teams/analytics/cache").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/teams/*/analytics/cache").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/teams/seed-logos").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/season/*/recalculate").hasRole("ADMIN")

                // All other requests — permit
                .anyRequest().permitAll()
            )

            // Enable HTTP Basic Authentication for admin endpoints
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
