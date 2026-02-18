package com.app.footballprediction.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

/**
 * Security configuration for the Football Prediction application.
 *
 * Admin endpoints are protected with HTTP Basic Authentication.
 * Public endpoints remain accessible without authentication.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${admin.username:admin}")
    private String adminUsername;

    @Value("${admin.password:admin123}")
    private String adminPassword;

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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for API endpoints
            .csrf(AbstractHttpConfigurer::disable)

            // Configure session management
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Allow H2 console to work with frames
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
            )

            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - no authentication required
                .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/assets/**", "/manifest.json").permitAll()
                .requestMatchers("/api/predict", "/api/teams", "/api/model/status").permitAll()
                .requestMatchers("/api/external/**").permitAll()
                .requestMatchers("/api/news/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()

                // Admin authentication check endpoint
                .requestMatchers("/api/admin/verify").authenticated()

                // Admin-only endpoints - require ADMIN role
                .requestMatchers("/api/model/train/**", "/api/model/train").hasRole("ADMIN")
                .requestMatchers("/api/model/grid-search").hasRole("ADMIN")
                .requestMatchers("/api/model/compare").hasRole("ADMIN")
                .requestMatchers("/api/data/reload").hasRole("ADMIN")
                .requestMatchers("/api/data/update").hasRole("ADMIN")
                .requestMatchers("/api/cache/clear").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // All other requests require authentication
                .anyRequest().permitAll()
            )

            // Enable HTTP Basic Authentication for admin endpoints
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}

