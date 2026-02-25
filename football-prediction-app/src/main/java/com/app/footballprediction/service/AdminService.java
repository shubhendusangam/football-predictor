package com.app.footballprediction.service;

import com.app.common.model.AdminAuditLog;
import com.app.common.model.League;
import com.app.common.model.SystemSettings;
import com.app.common.model.Match;
import com.app.common.model.Team;
import com.app.common.repository.AdminAuditLogRepository;
import com.app.common.repository.LeagueRepository;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.SystemSettingsRepository;
import com.app.common.repository.TeamRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for admin operations including system settings,
 * audit logging, league management, and match overrides.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminService {

    private final SystemSettingsRepository systemSettingsRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final LeagueRepository leagueRepository;
    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;
    private final CsvIngestionService csvIngestionService;

    /**
     * Initialize default settings and leagues on startup.
     */
    @PostConstruct
    @Transactional
    public void initializeDefaults() {
        initializeSystemSettings();
        initializeDefaultLeagues();
    }

    /**
     * Initialize system settings with defaults if not exists.
     */
    private void initializeSystemSettings() {
        if (systemSettingsRepository.count() == 0) {
            log.info("Initializing default system settings...");
            SystemSettings settings = SystemSettings.builder()
                    .predictionEngineEnabled(true)
                    .autoRetrainEnabled(true)
                    .autoFetchEnabled(true)
                    .minConfidenceThreshold(60)
                    .formWindowSize(5)
                    .defaultLeague("PL")
                    .maintenanceMode(false)
                    .cacheTtlMinutes(60)
                    .totalPredictions(0L)
                    .build();
            systemSettingsRepository.save(settings);
            log.info("Default system settings created");
        }
    }

    /**
     * Initialize default leagues if not exists.
     */
    private void initializeDefaultLeagues() {
        if (leagueRepository.count() == 0) {
            log.info("Initializing default leagues...");
            List<League> defaultLeagues = List.of(
                League.builder().code("PL").name("Premier League").countryCode("ENG")
                        .countryName("England").enabled(true).displayOrder(1).currentSeason("2025-26").build(),
                League.builder().code("PD").name("La Liga").countryCode("ESP")
                        .countryName("Spain").enabled(false).displayOrder(2).currentSeason("2025-26").build(),
                League.builder().code("BL1").name("Bundesliga").countryCode("GER")
                        .countryName("Germany").enabled(false).displayOrder(3).currentSeason("2025-26").build(),
                League.builder().code("SA").name("Serie A").countryCode("ITA")
                        .countryName("Italy").enabled(false).displayOrder(4).currentSeason("2025-26").build(),
                League.builder().code("FL1").name("Ligue 1").countryCode("FRA")
                        .countryName("France").enabled(false).displayOrder(5).currentSeason("2025-26").build()
            );
            leagueRepository.saveAll(defaultLeagues);
            log.info("Default leagues created: {}", defaultLeagues.size());
        }
    }

    // ======================= SYSTEM SETTINGS =======================

    /**
     * Get current system settings.
     */
    public SystemSettings getSettings() {
        return systemSettingsRepository.getSettings()
                .orElseThrow(() -> new IllegalStateException("System settings not initialized"));
    }

    /**
     * Update system settings.
     */
    @Transactional
    public SystemSettings updateSettings(SystemSettings newSettings, String adminUsername) {
        SystemSettings current = getSettings();

        // Update fields
        if (newSettings.getPredictionEngineEnabled() != null) {
            current.setPredictionEngineEnabled(newSettings.getPredictionEngineEnabled());
        }
        if (newSettings.getAutoRetrainEnabled() != null) {
            current.setAutoRetrainEnabled(newSettings.getAutoRetrainEnabled());
        }
        if (newSettings.getAutoFetchEnabled() != null) {
            current.setAutoFetchEnabled(newSettings.getAutoFetchEnabled());
        }
        if (newSettings.getMinConfidenceThreshold() != null) {
            current.setMinConfidenceThreshold(newSettings.getMinConfidenceThreshold());
        }
        if (newSettings.getFormWindowSize() != null) {
            current.setFormWindowSize(newSettings.getFormWindowSize());
        }
        if (newSettings.getDefaultLeague() != null) {
            current.setDefaultLeague(newSettings.getDefaultLeague());
        }
        if (newSettings.getMaintenanceMode() != null) {
            current.setMaintenanceMode(newSettings.getMaintenanceMode());
        }
        if (newSettings.getCacheTtlMinutes() != null) {
            current.setCacheTtlMinutes(newSettings.getCacheTtlMinutes());
        }

        current.setUpdatedBy(adminUsername);

        SystemSettings saved = systemSettingsRepository.save(current);

        logAuditAction(adminUsername, AdminAuditLog.ActionType.UPDATE_SETTINGS,
                "Updated system settings", "SystemSettings", String.valueOf(saved.getId()),
                null, null, true, null);

        log.info("System settings updated by {}", adminUsername);
        return saved;
    }

    /**
     * Toggle prediction engine on/off.
     */
    @Transactional
    public SystemSettings togglePredictionEngine(boolean enabled, String adminUsername) {
        SystemSettings settings = getSettings();
        boolean previousState = settings.getPredictionEngineEnabled();
        settings.setPredictionEngineEnabled(enabled);
        settings.setUpdatedBy(adminUsername);

        SystemSettings saved = systemSettingsRepository.save(settings);

        logAuditAction(adminUsername, AdminAuditLog.ActionType.TOGGLE_ENGINE,
                "Toggled prediction engine: " + (enabled ? "ON" : "OFF"),
                "SystemSettings", String.valueOf(saved.getId()),
                String.valueOf(previousState), String.valueOf(enabled), true, null);

        log.info("Prediction engine toggled to {} by {}", enabled, adminUsername);
        return saved;
    }

    /**
     * Record model retraining.
     */
    @Transactional
    public void recordModelRetraining(String adminUsername, Double accuracy) {
        SystemSettings settings = getSettings();
        settings.setLastModelTraining(LocalDateTime.now());
        if (accuracy != null) {
            settings.setModelAccuracy(accuracy);
        }
        settings.setUpdatedBy(adminUsername);
        systemSettingsRepository.save(settings);

        logAuditAction(adminUsername, AdminAuditLog.ActionType.RETRAIN_MODEL,
                "Model retrained with accuracy: " + accuracy + "%",
                "Model", null, null, String.valueOf(accuracy), true, null);

        log.info("Model retraining recorded by {} with accuracy {}", adminUsername, accuracy);
    }

    /**
     * Increment total predictions counter.
     */
    @Transactional
    public void incrementPredictionCount() {
        SystemSettings settings = getSettings();
        settings.setTotalPredictions(settings.getTotalPredictions() + 1);
        systemSettingsRepository.save(settings);
    }

    // ======================= LEAGUE MANAGEMENT =======================

    /**
     * Get all leagues.
     */
    public List<League> getAllLeagues() {
        return leagueRepository.findAllByOrderByDisplayOrderAsc();
    }

    /**
     * Get enabled leagues.
     */
    public List<League> getEnabledLeagues() {
        return leagueRepository.findByEnabledTrueOrderByDisplayOrderAsc();
    }

    /**
     * Toggle league enabled status.
     */
    @Transactional
    public League toggleLeague(String leagueCode, boolean enabled, String adminUsername) {
        League league = leagueRepository.findByCode(leagueCode)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueCode));

        boolean previousState = league.getEnabled();
        league.setEnabled(enabled);

        League saved = leagueRepository.save(league);

        logAuditAction(adminUsername, AdminAuditLog.ActionType.LEAGUE_UPDATE,
                "Toggled league " + leagueCode + ": " + (enabled ? "ENABLED" : "DISABLED"),
                "League", String.valueOf(saved.getId()),
                String.valueOf(previousState), String.valueOf(enabled), true, null);

        log.info("League {} toggled to {} by {}", leagueCode, enabled, adminUsername);
        return saved;
    }

    /**
     * Update league details.
     */
    @Transactional
    public League updateLeague(Long leagueId, League updates, String adminUsername) {
        League league = leagueRepository.findById(leagueId)
                .orElseThrow(() -> new IllegalArgumentException("League not found: " + leagueId));

        if (updates.getName() != null) {
            league.setName(updates.getName());
        }
        if (updates.getEnabled() != null) {
            league.setEnabled(updates.getEnabled());
        }
        if (updates.getDisplayOrder() != null) {
            league.setDisplayOrder(updates.getDisplayOrder());
        }
        if (updates.getLogoUrl() != null) {
            league.setLogoUrl(updates.getLogoUrl());
        }
        if (updates.getCurrentSeason() != null) {
            league.setCurrentSeason(updates.getCurrentSeason());
        }

        League saved = leagueRepository.save(league);

        logAuditAction(adminUsername, AdminAuditLog.ActionType.LEAGUE_UPDATE,
                "Updated league: " + saved.getCode(),
                "League", String.valueOf(saved.getId()), null, null, true, null);

        log.info("League {} updated by {}", saved.getCode(), adminUsername);
        return saved;
    }

    // ======================= MATCH OVERRIDE =======================

    /**
     * Override match result.
     */
    @Transactional
    public Match overrideMatchResult(Long matchId, String result, Integer homeGoals, Integer awayGoals, String adminUsername) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchId));

        String previousValue = String.format("Result=%s, Home=%d, Away=%d",
                match.getFullTimeResult(), match.getFullTimeHomeGoals(), match.getFullTimeAwayGoals());

        match.setFullTimeResult(result);
        match.setFullTimeHomeGoals(homeGoals);
        match.setFullTimeAwayGoals(awayGoals);

        Match saved = matchRepository.save(match);

        String newValue = String.format("Result=%s, Home=%d, Away=%d", result, homeGoals, awayGoals);

        logAuditAction(adminUsername, AdminAuditLog.ActionType.MATCH_OVERRIDE,
                "Overrode match result: " + match.getHomeTeam() + " vs " + match.getAwayTeam(),
                "Match", String.valueOf(matchId), previousValue, newValue, true, null);

        log.info("Match {} result overridden by {} to {}", matchId, adminUsername, newValue);
        return saved;
    }

    // ======================= TEAM LOGO MANAGEMENT =======================

    /**
     * Update team logo URL.
     */
    @Transactional
    public Team updateTeamLogo(Long teamId, String logoUrl, String adminUsername) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + teamId));

        String previousLogo = team.getLogoUrl();
        team.setLogoUrl(logoUrl);

        Team saved = teamRepository.save(team);

        logAuditAction(adminUsername, AdminAuditLog.ActionType.LOGO_UPDATE,
                "Updated logo for team: " + team.getName(),
                "Team", String.valueOf(teamId), previousLogo, logoUrl, true, null);

        log.info("Team {} logo updated by {}", team.getName(), adminUsername);
        return saved;
    }

    /**
     * Get teams with missing logos.
     */
    public List<Team> getTeamsWithMissingLogos() {
        return teamRepository.findAll().stream()
                .filter(t -> t.getLogoUrl() == null || t.getLogoUrl().isEmpty())
                .toList();
    }

    // ======================= DASHBOARD STATS =======================

    /**
     * Get admin dashboard statistics.
     */
    public Map<String, Object> getDashboardStats() {
        SystemSettings settings = getSettings();
        Map<String, Object> stats = new HashMap<>();

        // System status
        stats.put("predictionEngineEnabled", settings.getPredictionEngineEnabled());
        stats.put("autoRetrainEnabled", settings.getAutoRetrainEnabled());
        stats.put("autoFetchEnabled", settings.getAutoFetchEnabled());
        stats.put("maintenanceMode", settings.getMaintenanceMode());

        // Model stats
        stats.put("lastModelTraining", settings.getLastModelTraining());
        stats.put("modelAccuracy", settings.getModelAccuracy());
        stats.put("totalPredictions", settings.getTotalPredictions());

        // Data stats
        stats.put("totalMatches", matchRepository.count());
        stats.put("totalTeams", teamRepository.count());
        stats.put("totalLeagues", leagueRepository.count());
        stats.put("enabledLeagues", leagueRepository.findByEnabledTrueOrderByDisplayOrderAsc().size());

        // Teams without logos
        stats.put("teamsWithoutLogos", getTeamsWithMissingLogos().size());

        // Recent audit logs count
        stats.put("recentAuditLogs", auditLogRepository.count());

        return stats;
    }

    // ======================= AUDIT LOGGING =======================

    /**
     * Log an admin action.
     */
    @Transactional
    public void logAuditAction(String username, AdminAuditLog.ActionType actionType,
                               String description, String targetEntity, String targetId,
                               String previousValue, String newValue, boolean success, String errorMessage) {
        AdminAuditLog log = AdminAuditLog.builder()
                .username(username)
                .actionType(actionType)
                .actionDescription(description)
                .targetEntity(targetEntity)
                .targetId(targetId)
                .previousValue(previousValue)
                .newValue(newValue)
                .success(success)
                .errorMessage(errorMessage)
                .build();
        auditLogRepository.save(log);
    }

    /**
     * Get recent audit logs.
     */
    public Page<AdminAuditLog> getRecentAuditLogs(int page, int size) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    /**
     * Get audit logs for a specific user.
     */
    public List<AdminAuditLog> getAuditLogsForUser(String username) {
        return auditLogRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    /**
     * Update existing matches with fouls data from CSV files.
     * Delegates to CsvIngestionService.
     *
     * @return Number of matches updated
     */
    public int updateFoulsData() {
        log.info("Starting fouls data update...");
        return csvIngestionService.updateFoulsData();
    }
}

