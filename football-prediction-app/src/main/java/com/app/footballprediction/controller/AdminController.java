package com.app.footballprediction.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API Controller for Admin operations.
 *
 * All endpoints in this controller require ADMIN role authentication.
 */
@RestController
@RequestMapping("/api/admin")
@Slf4j
public class AdminController {

    /**
     * Verify admin credentials.
     * Returns success if the provided credentials are valid.
     *
     * GET /api/admin/verify
     */
    @GetMapping("/verify")
    public ResponseEntity<?> verifyAdmin(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            log.info("Admin verified: {}", authentication.getName());
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Admin authenticated successfully",
                "username", authentication.getName()
            ));
        }
        return ResponseEntity.status(401).body(Map.of(
            "status", "error",
            "message", "Authentication required"
        ));
    }

    /**
     * Logout admin (client should clear credentials).
     *
     * POST /api/admin/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Logged out successfully"
        ));
    }
}

