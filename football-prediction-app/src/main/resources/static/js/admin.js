/**
 * Admin Dashboard JavaScript Module
 * Handles admin authentication, dashboard rendering, and admin operations
 */

class AdminManager {
    constructor() {
        this.isAuthenticated = false;
        this.adminUsername = null;
        this.credentials = null;
        this.dashboardData = null;
        this.initialized = false;
    }

    /**
     * Initialize admin manager
     */
    init() {
        if (this.initialized) return;
        this.initialized = true;

        // Check for stored credentials
        this.loadStoredCredentials();

        console.log('[AdminManager] Initialized');
    }

    /**
     * Load stored credentials from session storage
     */
    loadStoredCredentials() {
        const stored = sessionStorage.getItem('adminCredentials');
        if (stored) {
            try {
                this.credentials = JSON.parse(stored);
                this.verifyCredentials();
            } catch (e) {
                console.warn('[AdminManager] Invalid stored credentials');
                this.clearCredentials();
            }
        }
    }

    /**
     * Get authorization header
     */
    getAuthHeader() {
        if (!this.credentials) return {};
        const encoded = btoa(`${this.credentials.username}:${this.credentials.password}`);
        return { 'Authorization': `Basic ${encoded}` };
    }

    /**
     * Login with credentials
     */
    async login(username, password) {
        try {
            const encoded = btoa(`${username}:${password}`);
            const response = await fetch('/api/admin/verify', {
                headers: { 'Authorization': `Basic ${encoded}` }
            });

            if (response.ok) {
                const data = await response.json();
                this.credentials = { username, password };
                this.adminUsername = data.username;
                this.isAuthenticated = true;

                // Store credentials in session
                sessionStorage.setItem('adminCredentials', JSON.stringify(this.credentials));

                return { success: true, username: data.username };
            } else {
                return { success: false, error: 'Invalid credentials' };
            }
        } catch (error) {
            console.error('[AdminManager] Login error:', error);
            return { success: false, error: 'Network error' };
        }
    }

    /**
     * Verify stored credentials
     */
    async verifyCredentials() {
        if (!this.credentials) return false;

        try {
            const response = await fetch('/api/admin/verify', {
                headers: this.getAuthHeader()
            });

            if (response.ok) {
                const data = await response.json();
                this.adminUsername = data.username;
                this.isAuthenticated = true;
                return true;
            } else {
                this.clearCredentials();
                return false;
            }
        } catch (error) {
            console.error('[AdminManager] Verify error:', error);
            return false;
        }
    }

    /**
     * Logout
     */
    async logout() {
        try {
            await fetch('/api/admin/logout', {
                method: 'POST',
                headers: this.getAuthHeader()
            });
        } catch (e) {
            // Ignore logout errors
        }

        this.clearCredentials();

        // Redirect to dashboard
        window.location.hash = '#dashboard';
    }

    /**
     * Clear credentials
     */
    clearCredentials() {
        this.credentials = null;
        this.adminUsername = null;
        this.isAuthenticated = false;
        sessionStorage.removeItem('adminCredentials');
    }

    /**
     * Fetch admin dashboard data
     */
    async fetchDashboardData() {
        try {
            const response = await fetch('/api/admin/dashboard', {
                headers: this.getAuthHeader()
            });

            if (response.ok) {
                this.dashboardData = await response.json();
                return this.dashboardData;
            } else if (response.status === 401) {
                this.clearCredentials();
                throw new Error('Authentication required');
            } else {
                throw new Error('Failed to fetch dashboard data');
            }
        } catch (error) {
            console.error('[AdminManager] Dashboard fetch error:', error);
            throw error;
        }
    }

    /**
     * Toggle prediction engine
     */
    async toggleEngine(enabled) {
        try {
            const response = await fetch('/api/admin/toggle-engine', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    ...this.getAuthHeader()
                },
                body: JSON.stringify({ enabled })
            });

            if (response.ok) {
                return await response.json();
            } else {
                const error = await response.json();
                throw new Error(error.message || 'Failed to toggle engine');
            }
        } catch (error) {
            console.error('[AdminManager] Toggle engine error:', error);
            throw error;
        }
    }

    /**
     * Trigger model retraining
     */
    async retrain() {
        try {
            const response = await fetch('/api/admin/retrain', {
                method: 'POST',
                headers: this.getAuthHeader()
            });

            if (response.ok) {
                return await response.json();
            } else {
                const error = await response.json();
                throw new Error(error.message || 'Failed to start retraining');
            }
        } catch (error) {
            console.error('[AdminManager] Retrain error:', error);
            throw error;
        }
    }

    /**
     * Update settings
     */
    async updateSettings(settings) {
        try {
            const response = await fetch('/api/admin/settings', {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    ...this.getAuthHeader()
                },
                body: JSON.stringify(settings)
            });

            if (response.ok) {
                return await response.json();
            } else {
                const error = await response.json();
                throw new Error(error.message || 'Failed to update settings');
            }
        } catch (error) {
            console.error('[AdminManager] Update settings error:', error);
            throw error;
        }
    }

    /**
     * Toggle league
     */
    async toggleLeague(code, enabled) {
        try {
            const response = await fetch(`/api/admin/leagues/${code}/toggle`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    ...this.getAuthHeader()
                },
                body: JSON.stringify({ enabled })
            });

            if (response.ok) {
                return await response.json();
            } else {
                const error = await response.json();
                throw new Error(error.message || 'Failed to toggle league');
            }
        } catch (error) {
            console.error('[AdminManager] Toggle league error:', error);
            throw error;
        }
    }

    /**
     * Override match result
     */
    async overrideMatch(matchId, result, homeGoals, awayGoals) {
        try {
            const response = await fetch('/api/admin/match-override', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    ...this.getAuthHeader()
                },
                body: JSON.stringify({ matchId, result, homeGoals, awayGoals })
            });

            if (response.ok) {
                return await response.json();
            } else {
                const error = await response.json();
                throw new Error(error.message || 'Failed to override match');
            }
        } catch (error) {
            console.error('[AdminManager] Override match error:', error);
            throw error;
        }
    }

    /**
     * Update team logo
     */
    async updateTeamLogo(teamId, logoUrl) {
        try {
            const response = await fetch(`/api/admin/teams/${teamId}/logo`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                    ...this.getAuthHeader()
                },
                body: JSON.stringify({ logoUrl })
            });

            if (response.ok) {
                return await response.json();
            } else {
                const error = await response.json();
                throw new Error(error.message || 'Failed to update logo');
            }
        } catch (error) {
            console.error('[AdminManager] Update logo error:', error);
            throw error;
        }
    }

    /**
     * Get teams with missing logos
     */
    async getTeamsWithMissingLogos() {
        try {
            const response = await fetch('/api/admin/teams/missing-logos', {
                headers: this.getAuthHeader()
            });

            if (response.ok) {
                return await response.json();
            } else {
                throw new Error('Failed to fetch teams');
            }
        } catch (error) {
            console.error('[AdminManager] Fetch missing logos error:', error);
            throw error;
        }
    }

    /**
     * Seed team logos from online sources
     */
    async seedTeamLogos() {
        try {
            const response = await fetch('/api/teams/seed-logos', {
                method: 'POST',
                headers: this.getAuthHeader()
            });

            if (response.ok) {
                return await response.json();
            } else {
                const error = await response.json();
                throw new Error(error.message || 'Failed to seed team logos');
            }
        } catch (error) {
            console.error('[AdminManager] Seed logos error:', error);
            throw error;
        }
    }

    /**
     * Get audit logs
     */
    async getAuditLogs(page = 0, size = 20) {
        try {
            const response = await fetch(`/api/admin/audit-logs?page=${page}&size=${size}`, {
                headers: this.getAuthHeader()
            });

            if (response.ok) {
                return await response.json();
            } else {
                throw new Error('Failed to fetch audit logs');
            }
        } catch (error) {
            console.error('[AdminManager] Fetch audit logs error:', error);
            throw error;
        }
    }

    /**
     * Render login form
     */
    renderLoginForm(container, onSuccess) {
        container.innerHTML = `
            <div class="admin-login-overlay">
                <div class="admin-login-card">
                    <div class="admin-login-header">
                        <div class="icon">🔐</div>
                        <h3>Admin Login</h3>
                        <p>Enter your credentials to access the admin panel</p>
                    </div>
                    <form class="admin-login-form" id="adminLoginForm">
                        <div id="loginError" class="admin-login-error" style="display: none;"></div>
                        <div class="form-group">
                            <label for="adminUsername">Username</label>
                            <input type="text" id="adminUsername" placeholder="Enter username" required autocomplete="username">
                        </div>
                        <div class="form-group">
                            <label for="adminPassword">Password</label>
                            <input type="password" id="adminPassword" placeholder="Enter password" required autocomplete="current-password">
                        </div>
                        <button type="submit" class="btn-admin btn-admin-primary" style="width: 100%;">
                            <span>🔓</span> Login
                        </button>
                        <button type="button" class="btn-admin btn-admin-secondary" style="width: 100%;" onclick="window.location.hash='#dashboard'">
                            Cancel
                        </button>
                    </form>
                </div>
            </div>
        `;

        const form = document.getElementById('adminLoginForm');
        const errorDiv = document.getElementById('loginError');

        form.addEventListener('submit', async (e) => {
            e.preventDefault();

            const username = document.getElementById('adminUsername').value;
            const password = document.getElementById('adminPassword').value;

            const submitBtn = form.querySelector('button[type="submit"]');
            submitBtn.disabled = true;
            submitBtn.innerHTML = '<span class="admin-spinner" style="width:16px;height:16px;border-width:2px;"></span> Logging in...';

            const result = await this.login(username, password);

            if (result.success) {
                if (onSuccess) onSuccess();
            } else {
                errorDiv.textContent = result.error;
                errorDiv.style.display = 'block';
                submitBtn.disabled = false;
                submitBtn.innerHTML = '<span>🔓</span> Login';
            }
        });
    }

    /**
     * Render admin dashboard
     */
    async renderDashboard(container) {
        // Check authentication first
        if (!this.isAuthenticated) {
            this.renderLoginForm(container, () => this.renderDashboard(container));
            return;
        }

        // Show loading state
        container.innerHTML = `
            <div class="admin-loading">
                <div class="admin-spinner"></div>
                <p>Loading admin dashboard...</p>
            </div>
        `;

        try {
            const data = await this.fetchDashboardData();
            this.renderDashboardContent(container, data);
        } catch (error) {
            if (error.message === 'Authentication required') {
                this.renderLoginForm(container, () => this.renderDashboard(container));
            } else {
                container.innerHTML = `
                    <div class="admin-error">
                        <div class="admin-error-icon">⚠️</div>
                        <p class="admin-error-message">${error.message}</p>
                        <button class="btn-admin btn-admin-primary" onclick="window.adminManager.renderDashboard(document.getElementById('mainContent'))">
                            Retry
                        </button>
                    </div>
                `;
            }
        }
    }

    /**
     * Render dashboard content
     */
    renderDashboardContent(container, data) {
        const { stats, settings, leagues } = data;

        container.innerHTML = `
            <div class="admin-dashboard">
                <div class="admin-header">
                    <h2><span>⚙️</span> Admin Control Panel</h2>
                    <div style="display: flex; align-items: center; gap: 1rem;">
                        <span class="admin-status-badge ${settings.predictionEngineEnabled ? 'online' : 'offline'}">
                            <span class="status-dot"></span>
                            ${settings.predictionEngineEnabled ? 'Engine Online' : 'Engine Offline'}
                        </span>
                        <button class="btn-admin btn-admin-secondary" onclick="window.adminManager.logout()">
                            🚪 Logout
                        </button>
                    </div>
                </div>

                <div class="admin-grid">
                    <!-- System Controls Card -->
                    <div class="admin-card">
                        <div class="admin-card-header">
                            <h3><span class="icon">🎛️</span> System Controls</h3>
                        </div>
                        <div class="admin-card-body">
                            <div class="system-controls">
                                <div class="control-item">
                                    <div class="control-info">
                                        <span class="control-label">Prediction Engine</span>
                                        <span class="control-description">Enable or disable match predictions</span>
                                    </div>
                                    <label class="toggle-switch">
                                        <input type="checkbox" id="toggleEngine" ${settings.predictionEngineEnabled ? 'checked' : ''}>
                                        <span class="toggle-slider"></span>
                                    </label>
                                </div>
                                <div class="control-item">
                                    <div class="control-info">
                                        <span class="control-label">Auto Retrain</span>
                                        <span class="control-description">Automatically retrain model with new data</span>
                                    </div>
                                    <label class="toggle-switch">
                                        <input type="checkbox" id="toggleAutoRetrain" ${settings.autoRetrainEnabled ? 'checked' : ''}>
                                        <span class="toggle-slider"></span>
                                    </label>
                                </div>
                                <div class="control-item">
                                    <div class="control-info">
                                        <span class="control-label">Auto Data Fetch</span>
                                        <span class="control-description">Automatically fetch new match data</span>
                                    </div>
                                    <label class="toggle-switch">
                                        <input type="checkbox" id="toggleAutoFetch" ${settings.autoFetchEnabled ? 'checked' : ''}>
                                        <span class="toggle-slider"></span>
                                    </label>
                                </div>
                                <div class="control-item">
                                    <div class="control-info">
                                        <span class="control-label">Maintenance Mode</span>
                                        <span class="control-description">Disable public API access</span>
                                    </div>
                                    <label class="toggle-switch">
                                        <input type="checkbox" id="toggleMaintenance" ${settings.maintenanceMode ? 'checked' : ''}>
                                        <span class="toggle-slider"></span>
                                    </label>
                                </div>
                            </div>
                            <div class="btn-group" style="margin-top: 1rem;">
                                <button class="btn-admin btn-admin-primary" id="btnRetrain">
                                    🔄 Retrain Model
                                </button>
                                <button class="btn-admin btn-admin-secondary" id="btnClearCache">
                                    🗑️ Clear Cache
                                </button>
                            </div>
                        </div>
                    </div>

                    <!-- Stats Card -->
                    <div class="admin-card">
                        <div class="admin-card-header">
                            <h3><span class="icon">📊</span> System Statistics</h3>
                        </div>
                        <div class="admin-card-body">
                            <div class="stats-grid">
                                <div class="stat-item">
                                    <span class="stat-value info">${stats.totalMatches?.toLocaleString() || 0}</span>
                                    <span class="stat-label">Total Matches</span>
                                </div>
                                <div class="stat-item">
                                    <span class="stat-value success">${stats.totalTeams || 0}</span>
                                    <span class="stat-label">Teams</span>
                                </div>
                                <div class="stat-item">
                                    <span class="stat-value">${stats.enabledLeagues || 0}/${stats.totalLeagues || 0}</span>
                                    <span class="stat-label">Active Leagues</span>
                                </div>
                                <div class="stat-item">
                                    <span class="stat-value ${stats.modelAccuracy >= 70 ? 'success' : stats.modelAccuracy >= 50 ? 'warning' : 'danger'}">${stats.modelAccuracy?.toFixed(1) || 'N/A'}%</span>
                                    <span class="stat-label">Model Accuracy</span>
                                </div>
                                <div class="stat-item">
                                    <span class="stat-value info">${stats.totalPredictions?.toLocaleString() || 0}</span>
                                    <span class="stat-label">Predictions Made</span>
                                </div>
                                <div class="stat-item">
                                    <span class="stat-value ${stats.teamsWithoutLogos > 0 ? 'warning' : 'success'}">${stats.teamsWithoutLogos || 0}</span>
                                    <span class="stat-label">Missing Logos</span>
                                </div>
                            </div>
                            <div style="margin-top: 1rem; font-size: 0.75rem; color: var(--text-tertiary);">
                                ${stats.lastModelTraining ? `Last trained: ${new Date(stats.lastModelTraining).toLocaleString()}` : 'Model not trained yet'}
                            </div>
                        </div>
                    </div>

                    <!-- League Manager Card -->
                    <div class="admin-card admin-grid-wide">
                        <div class="admin-card-header">
                            <h3><span class="icon">🏆</span> League Manager</h3>
                        </div>
                        <div class="admin-card-body" style="padding: 0;">
                            <table class="league-table">
                                <thead>
                                    <tr>
                                        <th>League</th>
                                        <th>Country</th>
                                        <th>Season</th>
                                        <th>Status</th>
                                        <th>Actions</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    ${leagues.map(league => `
                                        <tr data-league-code="${league.code}">
                                            <td>
                                                <div class="league-name">
                                                    ${league.logoUrl ? `<img src="${league.logoUrl}" class="league-logo" alt="">` : ''}
                                                    <span>${league.name}</span>
                                                </div>
                                            </td>
                                            <td>${league.countryName || league.countryCode || '-'}</td>
                                            <td>${league.currentSeason || '-'}</td>
                                            <td>
                                                <span class="league-status ${league.enabled ? 'enabled' : 'disabled'}">
                                                    ${league.enabled ? '✓ Enabled' : '✗ Disabled'}
                                                </span>
                                            </td>
                                            <td>
                                                <button class="btn-admin btn-admin-${league.enabled ? 'danger' : 'success'}"
                                                        onclick="window.adminManager.handleToggleLeague('${league.code}', ${!league.enabled})"
                                                        style="padding: 0.5rem 0.75rem; font-size: 0.75rem;">
                                                    ${league.enabled ? 'Disable' : 'Enable'}
                                                </button>
                                            </td>
                                        </tr>
                                    `).join('')}
                                </tbody>
                            </table>
                        </div>
                    </div>

                    <!-- Match Override Card -->
                    <div class="admin-card">
                        <div class="admin-card-header">
                            <h3><span class="icon">✏️</span> Match Override</h3>
                        </div>
                        <div class="admin-card-body">
                            <form class="override-form" id="matchOverrideForm">
                                <div class="form-row">
                                    <div class="form-group">
                                        <label>Match ID</label>
                                        <input type="number" id="overrideMatchId" placeholder="Enter match ID" required>
                                    </div>
                                    <div class="form-group">
                                        <label>Result</label>
                                        <select id="overrideResult" required>
                                            <option value="">Select Result</option>
                                            <option value="H">Home Win (H)</option>
                                            <option value="D">Draw (D)</option>
                                            <option value="A">Away Win (A)</option>
                                        </select>
                                    </div>
                                </div>
                                <div class="form-row">
                                    <div class="form-group">
                                        <label>Home Goals</label>
                                        <input type="number" id="overrideHomeGoals" min="0" placeholder="0" required>
                                    </div>
                                    <div class="form-group">
                                        <label>Away Goals</label>
                                        <input type="number" id="overrideAwayGoals" min="0" placeholder="0" required>
                                    </div>
                                </div>
                                <button type="submit" class="btn-admin btn-admin-primary">
                                    💾 Save Override
                                </button>
                            </form>
                        </div>
                    </div>

                    <!-- Team Logo Manager Card -->
                    <div class="admin-card">
                        <div class="admin-card-header">
                            <h3><span class="icon">🖼️</span> Team Logo Manager</h3>
                            <button class="btn-admin btn-admin-secondary" onclick="window.adminManager.loadMissingLogos()" style="padding: 0.5rem 0.75rem; font-size: 0.75rem;">
                                🔄 Refresh
                            </button>
                        </div>
                        <div class="admin-card-body">
                            <div id="missingLogosContainer">
                                ${stats.teamsWithoutLogos > 0
                                    ? `<p style="color: var(--text-secondary); margin-bottom: 1rem;">${stats.teamsWithoutLogos} teams missing logos. Click refresh to load details.</p>`
                                    : '<p style="color: #22c55e;">✓ All teams have logos assigned!</p>'
                                }
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        // Attach event listeners
        this.attachEventListeners(settings);
    }

    /**
     * Attach event listeners
     */
    attachEventListeners(settings) {
        // Toggle engine
        const toggleEngine = document.getElementById('toggleEngine');
        if (toggleEngine) {
            toggleEngine.addEventListener('change', (e) => this.handleToggleEngine(e.target.checked));
        }

        // Toggle auto retrain
        const toggleAutoRetrain = document.getElementById('toggleAutoRetrain');
        if (toggleAutoRetrain) {
            toggleAutoRetrain.addEventListener('change', (e) => this.handleUpdateSettings({ autoRetrainEnabled: e.target.checked }));
        }

        // Toggle auto fetch
        const toggleAutoFetch = document.getElementById('toggleAutoFetch');
        if (toggleAutoFetch) {
            toggleAutoFetch.addEventListener('change', (e) => this.handleUpdateSettings({ autoFetchEnabled: e.target.checked }));
        }

        // Toggle maintenance
        const toggleMaintenance = document.getElementById('toggleMaintenance');
        if (toggleMaintenance) {
            toggleMaintenance.addEventListener('change', (e) => this.handleUpdateSettings({ maintenanceMode: e.target.checked }));
        }

        // Retrain button
        const btnRetrain = document.getElementById('btnRetrain');
        if (btnRetrain) {
            btnRetrain.addEventListener('click', () => this.handleRetrain());
        }

        // Clear cache button
        const btnClearCache = document.getElementById('btnClearCache');
        if (btnClearCache) {
            btnClearCache.addEventListener('click', () => this.handleClearCache());
        }

        // Match override form
        const matchOverrideForm = document.getElementById('matchOverrideForm');
        if (matchOverrideForm) {
            matchOverrideForm.addEventListener('submit', (e) => this.handleMatchOverride(e));
        }
    }

    /**
     * Handle toggle engine
     */
    async handleToggleEngine(enabled) {
        try {
            await this.toggleEngine(enabled);
            this.showToast(`Prediction engine ${enabled ? 'enabled' : 'disabled'}`, 'success');

            // Update status badge
            const badge = document.querySelector('.admin-status-badge');
            if (badge) {
                badge.className = `admin-status-badge ${enabled ? 'online' : 'offline'}`;
                badge.innerHTML = `<span class="status-dot"></span>${enabled ? 'Engine Online' : 'Engine Offline'}`;
            }
        } catch (error) {
            this.showToast(error.message, 'error');
            // Revert toggle
            const toggle = document.getElementById('toggleEngine');
            if (toggle) toggle.checked = !enabled;
        }
    }

    /**
     * Handle update settings
     */
    async handleUpdateSettings(settings) {
        try {
            await this.updateSettings(settings);
            this.showToast('Settings updated', 'success');
        } catch (error) {
            this.showToast(error.message, 'error');
        }
    }

    /**
     * Handle retrain
     */
    async handleRetrain() {
        const btn = document.getElementById('btnRetrain');
        if (btn) {
            btn.disabled = true;
            btn.innerHTML = '<span class="admin-spinner" style="width:14px;height:14px;border-width:2px;"></span> Retraining...';
        }

        try {
            await this.retrain();
            this.showToast('Model retraining initiated', 'success');
        } catch (error) {
            this.showToast(error.message, 'error');
        }

        if (btn) {
            btn.disabled = false;
            btn.innerHTML = '🔄 Retrain Model';
        }
    }

    /**
     * Handle clear cache
     */
    async handleClearCache() {
        try {
            const response = await fetch('/api/cache/clear', {
                method: 'POST',
                headers: this.getAuthHeader()
            });

            if (response.ok) {
                this.showToast('Cache cleared successfully', 'success');
            } else {
                throw new Error('Failed to clear cache');
            }
        } catch (error) {
            this.showToast(error.message, 'error');
        }
    }

    /**
     * Handle toggle league
     */
    async handleToggleLeague(code, enabled) {
        try {
            await this.toggleLeague(code, enabled);
            this.showToast(`League ${code} ${enabled ? 'enabled' : 'disabled'}`, 'success');

            // Update table row
            const row = document.querySelector(`tr[data-league-code="${code}"]`);
            if (row) {
                const statusCell = row.querySelector('.league-status');
                const actionBtn = row.querySelector('button');

                if (statusCell) {
                    statusCell.className = `league-status ${enabled ? 'enabled' : 'disabled'}`;
                    statusCell.textContent = enabled ? '✓ Enabled' : '✗ Disabled';
                }

                if (actionBtn) {
                    actionBtn.className = `btn-admin btn-admin-${enabled ? 'danger' : 'success'}`;
                    actionBtn.textContent = enabled ? 'Disable' : 'Enable';
                    actionBtn.onclick = () => this.handleToggleLeague(code, !enabled);
                }
            }
        } catch (error) {
            this.showToast(error.message, 'error');
        }
    }

    /**
     * Handle match override
     */
    async handleMatchOverride(event) {
        event.preventDefault();

        const matchId = document.getElementById('overrideMatchId').value;
        const result = document.getElementById('overrideResult').value;
        const homeGoals = document.getElementById('overrideHomeGoals').value;
        const awayGoals = document.getElementById('overrideAwayGoals').value;

        try {
            await this.overrideMatch(matchId, result, homeGoals, awayGoals);
            this.showToast('Match result overridden successfully', 'success');

            // Clear form
            event.target.reset();
        } catch (error) {
            this.showToast(error.message, 'error');
        }
    }

    /**
     * Load missing logos
     */
    async loadMissingLogos() {
        const container = document.getElementById('missingLogosContainer');
        if (!container) return;

        container.innerHTML = '<div class="admin-loading"><div class="admin-spinner"></div></div>';

        try {
            const data = await this.getTeamsWithMissingLogos();

            if (data.teams.length === 0) {
                container.innerHTML = '<p style="color: #22c55e;">✓ All teams have logos assigned!</p>';
                return;
            }

            container.innerHTML = `
                <div class="logo-grid">
                    ${data.teams.map(team => `
                        <div class="logo-item" data-team-id="${team.id}">
                            <div class="logo-preview">
                                <span class="missing">⚽</span>
                            </div>
                            <div class="logo-info">
                                <div class="logo-team-name">${team.name}</div>
                                <span class="logo-missing-badge">Missing Logo</span>
                            </div>
                            <button class="btn-admin btn-admin-secondary"
                                    onclick="window.adminManager.promptLogoUrl(${team.id}, '${team.name}')"
                                    style="padding: 0.375rem 0.5rem; font-size: 0.75rem;">
                                📎
                            </button>
                        </div>
                    `).join('')}
                </div>
            `;
        } catch (error) {
            container.innerHTML = `<p style="color: #ef4444;">Error: ${error.message}</p>`;
        }
    }

    /**
     * Prompt for logo URL
     */
    promptLogoUrl(teamId, teamName) {
        const url = prompt(`Enter logo URL for ${teamName}:`);
        if (url) {
            this.handleUpdateLogo(teamId, url);
        }
    }

    /**
     * Handle update logo
     */
    async handleUpdateLogo(teamId, logoUrl) {
        try {
            await this.updateTeamLogo(teamId, logoUrl);
            this.showToast('Team logo updated', 'success');
            this.loadMissingLogos(); // Refresh the list
        } catch (error) {
            this.showToast(error.message, 'error');
        }
    }

    /**
     * Show toast notification
     */
    showToast(message, type = 'info') {
        if (window.UI && window.UI.showToast) {
            window.UI.showToast(message, type);
        } else {
            console.log(`[${type.toUpperCase()}] ${message}`);
        }
    }
}

// Create global instance
window.adminManager = new AdminManager();
window.adminManager.init();

// Export for module use
if (typeof module !== 'undefined' && module.exports) {
    module.exports = AdminManager;
}

