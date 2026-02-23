/**
 * League Standings Panel Component
 * =================================
 * Displays league table with position zones, form, and controls.
 * Features:
 * - League and season selection dropdowns
 * - Zone highlighting (Champions League, Europa, Relegation)
 * - Form display with color-coded badges
 * - Position change indicators
 * - Responsive horizontal scrolling
 */

class LeagueStandingsPanel {
    constructor(api) {
        this.api = api || window.api;
        this.container = null;
        this.currentLeagueId = null;
        this.currentSeason = null;
        this.leagues = [];
        this.seasons = [];
        this.isLoading = false;
    }

    /**
     * Initialize the panel in a container
     * @param {HTMLElement} container - Parent container element
     * @param {number} leagueId - Optional initial league ID
     */
    async init(container, leagueId = null) {
        this.container = container;
        this.currentLeagueId = leagueId;

        // Create panel structure
        this.render();

        // Load data
        await this.loadStandings();
    }

    /**
     * Render the panel structure
     */
    render() {
        if (!this.container) return;

        this.container.innerHTML = `
            <div class="league-standings-container">
                <div class="league-standings-header">
                    <div class="league-standings-title">
                        <span class="icon">🏆</span>
                        <span class="title-text">League Standings</span>
                        <span class="league-standings-season"></span>
                    </div>
                    <div class="league-standings-controls">
                        <select class="league-standings-select" id="leagueSelect" title="Select League">
                            <option value="">Loading...</option>
                        </select>
                        <select class="league-standings-select" id="seasonSelect" title="Select Season">
                            <option value="">Current Season</option>
                        </select>
                        <button class="league-standings-refresh" id="standingsRefresh" title="Refresh Standings">
                            🔄
                        </button>
                    </div>
                </div>
                <div class="league-standings-content">
                    <div class="league-standings-loading">
                        <div class="spinner"></div>
                        <span>Loading standings...</span>
                    </div>
                </div>
                <div class="league-standings-legend">
                    <div class="legend-item">
                        <span class="legend-color champions"></span>
                        <span>Champions League</span>
                    </div>
                    <div class="legend-item">
                        <span class="legend-color europa"></span>
                        <span>Europa League</span>
                    </div>
                    <div class="legend-item">
                        <span class="legend-color conference"></span>
                        <span>Conference League</span>
                    </div>
                    <div class="legend-item">
                        <span class="legend-color relegation"></span>
                        <span>Relegation</span>
                    </div>
                </div>
            </div>
        `;

        // Attach event listeners
        this.attachEventListeners();
    }

    /**
     * Attach event listeners to controls
     */
    attachEventListeners() {
        const leagueSelect = this.container.querySelector('#leagueSelect');
        const seasonSelect = this.container.querySelector('#seasonSelect');
        const refreshBtn = this.container.querySelector('#standingsRefresh');

        if (leagueSelect) {
            leagueSelect.addEventListener('change', (e) => {
                this.currentLeagueId = e.target.value ? parseInt(e.target.value) : null;
                this.currentSeason = null;
                this.loadSeasons();
                this.loadStandings();
            });
        }

        if (seasonSelect) {
            seasonSelect.addEventListener('change', (e) => {
                this.currentSeason = e.target.value || null;
                this.loadStandings();
            });
        }

        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => this.refreshStandings());
        }
    }

    /**
     * Load league standings data
     */
    async loadStandings() {
        if (this.isLoading) return;

        this.isLoading = true;
        this.showLoading();

        try {
            // Load leagues if not already loaded
            if (this.leagues.length === 0) {
                await this.loadLeagues();
            }

            // Fetch standings
            const response = await this.api.getLeagueStandings(this.currentLeagueId, this.currentSeason);

            if (response && response.standings) {
                this.renderStandings(response);
            } else {
                this.showEmpty();
            }
        } catch (error) {
            console.error('[LeagueStandings] Failed to load standings:', error);
            this.showError(error.message || 'Failed to load standings');
        } finally {
            this.isLoading = false;
        }
    }

    /**
     * Load available leagues
     */
    async loadLeagues() {
        try {
            const response = await this.api.getAvailableLeagues();
            this.leagues = response.leagues || [];
            this.updateLeagueSelect();

            // Set default league if not set
            if (!this.currentLeagueId && this.leagues.length > 0) {
                this.currentLeagueId = this.leagues[0].id;
            }
        } catch (error) {
            console.error('[LeagueStandings] Failed to load leagues:', error);
            this.leagues = [];
        }
    }

    /**
     * Load available seasons for current league
     */
    async loadSeasons() {
        try {
            const response = await this.api.getAvailableSeasons(this.currentLeagueId);
            this.seasons = response.seasons || [];
            this.updateSeasonSelect();
        } catch (error) {
            console.error('[LeagueStandings] Failed to load seasons:', error);
            this.seasons = [];
        }
    }

    /**
     * Update league dropdown
     */
    updateLeagueSelect() {
        const select = this.container.querySelector('#leagueSelect');
        if (!select) return;

        select.innerHTML = this.leagues.map(league =>
            `<option value="${league.id}" ${league.id === this.currentLeagueId ? 'selected' : ''}>
                ${league.name}
            </option>`
        ).join('');

        if (this.leagues.length === 0) {
            select.innerHTML = '<option value="">No leagues available</option>';
        }
    }

    /**
     * Update season dropdown
     */
    updateSeasonSelect() {
        const select = this.container.querySelector('#seasonSelect');
        if (!select) return;

        let options = '<option value="">Current Season</option>';
        options += this.seasons.map(season =>
            `<option value="${season}" ${season === this.currentSeason ? 'selected' : ''}>
                ${season}
            </option>`
        ).join('');

        select.innerHTML = options;
    }

    /**
     * Render standings table
     */
    renderStandings(data) {
        const content = this.container.querySelector('.league-standings-content');
        const titleText = this.container.querySelector('.title-text');
        const seasonSpan = this.container.querySelector('.league-standings-season');

        if (titleText && data.leagueName) {
            titleText.textContent = data.leagueName;
        }

        if (seasonSpan && data.season) {
            seasonSpan.textContent = `— ${data.season}`;
        }

        if (!data.standings || data.standings.length === 0) {
            this.showEmpty();
            return;
        }

        content.innerHTML = `
            <div class="league-standings-table-wrapper">
                <table class="league-standings-table">
                    <thead>
                        <tr>
                            <th class="position-col">Pos</th>
                            <th class="team-col">Team</th>
                            <th>P</th>
                            <th>W</th>
                            <th>D</th>
                            <th>L</th>
                            <th>GF</th>
                            <th>GA</th>
                            <th>GD</th>
                            <th>Pts</th>
                            <th class="form-col">Form</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${data.standings.map(team => this.renderTeamRow(team)).join('')}
                    </tbody>
                </table>
            </div>
        `;
    }

    /**
     * Render a single team row
     */
    renderTeamRow(team) {
        const zoneClass = team.zone ? `zone-${team.zone}` : '';
        const gdClass = team.goalDifference > 0 ? 'gd-positive' : team.goalDifference < 0 ? 'gd-negative' : '';
        const gdDisplay = team.goalDifference > 0 ? `+${team.goalDifference}` : team.goalDifference;

        // Position change indicator
        let positionChangeHtml = '';
        if (team.positionChange !== 0) {
            if (team.positionChange > 0) {
                positionChangeHtml = `<span class="position-change up">↑${team.positionChange}</span>`;
            } else {
                positionChangeHtml = `<span class="position-change down">↓${Math.abs(team.positionChange)}</span>`;
            }
        }

        // Team logo
        const logoHtml = team.teamLogo
            ? `<img src="${team.teamLogo}" alt="${team.teamName}" class="team-logo" onerror="this.outerHTML='<span class=\\'team-logo-placeholder\\'>⚽</span>'">`
            : '<span class="team-logo-placeholder">⚽</span>';

        // Form badges
        const formHtml = this.renderForm(team.form);

        return `
            <tr class="${zoneClass}">
                <td>
                    <div class="position-cell">
                        <span class="position-indicator"></span>
                        ${team.position}
                        ${positionChangeHtml}
                    </div>
                </td>
                <td>
                    <div class="team-cell">
                        ${logoHtml}
                        <span class="team-name">${team.teamName}</span>
                    </div>
                </td>
                <td class="stat-cell">${team.played}</td>
                <td class="stat-cell">${team.won}</td>
                <td class="stat-cell">${team.drawn}</td>
                <td class="stat-cell">${team.lost}</td>
                <td class="stat-cell">${team.goalsFor}</td>
                <td class="stat-cell">${team.goalsAgainst}</td>
                <td class="stat-cell ${gdClass}">${gdDisplay}</td>
                <td class="stat-cell points">${team.points}</td>
                <td>
                    <div class="form-cell">${formHtml}</div>
                </td>
            </tr>
        `;
    }

    /**
     * Render form badges
     */
    renderForm(form) {
        if (!form) return '<span class="form-badge">-</span>';

        const results = form.split(' ').slice(0, 5);
        return results.map(result => {
            let cls = '';
            let label = result;

            switch (result.toUpperCase()) {
                case 'W':
                    cls = 'win';
                    label = 'W';
                    break;
                case 'D':
                    cls = 'draw';
                    label = 'D';
                    break;
                case 'L':
                    cls = 'loss';
                    label = 'L';
                    break;
                default:
                    cls = '';
                    label = '-';
            }

            return `<span class="form-badge ${cls}">${label}</span>`;
        }).join('');
    }

    /**
     * Show loading state
     */
    showLoading() {
        const content = this.container.querySelector('.league-standings-content');
        if (content) {
            content.innerHTML = `
                <div class="league-standings-loading">
                    <div class="spinner"></div>
                    <span>Loading standings...</span>
                </div>
            `;
        }
    }

    /**
     * Show empty state
     */
    showEmpty() {
        const content = this.container.querySelector('.league-standings-content');
        if (content) {
            content.innerHTML = `
                <div class="league-standings-empty">
                    <span class="icon">📋</span>
                    <span class="message">No standings data available for this league and season.</span>
                </div>
            `;
        }
    }

    /**
     * Show error state
     */
    showError(message) {
        const content = this.container.querySelector('.league-standings-content');
        if (content) {
            content.innerHTML = `
                <div class="league-standings-error">
                    <span class="icon">⚠️</span>
                    <span class="message">${message}</span>
                    <button class="retry-btn" onclick="window.leagueStandingsPanel.loadStandings()">
                        Retry
                    </button>
                </div>
            `;
        }
    }

    /**
     * Refresh standings (force recalculation)
     */
    async refreshStandings() {
        const refreshBtn = this.container.querySelector('#standingsRefresh');
        if (refreshBtn) {
            refreshBtn.classList.add('loading');
        }

        try {
            await this.api.refreshLeagueStandings(this.currentLeagueId);
            await this.loadStandings();
        } catch (error) {
            console.error('[LeagueStandings] Failed to refresh standings:', error);
            this.showError(error.message || 'Failed to refresh standings');
        } finally {
            if (refreshBtn) {
                refreshBtn.classList.remove('loading');
            }
        }
    }

    /**
     * Destroy the panel
     */
    destroy() {
        if (this.container) {
            this.container.innerHTML = '';
        }
        this.container = null;
        this.leagues = [];
        this.seasons = [];
    }
}

// Export to global scope
window.LeagueStandingsPanel = LeagueStandingsPanel;

console.log('[LeagueStandings] League Standings Panel component loaded');

