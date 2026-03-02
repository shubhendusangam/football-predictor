/**
 * Historical Season Explorer
 * Football Forecaster - Season-based historical data viewer
 *
 * Features:
 * - Season dropdown selector
 * - Data table with Team, Matches, Win % statistics
 * - Line chart per team (expandable rows)
 * - Sorting & filtering
 * - Pagination support
 */

/**
 * Normalize season format to standard format (YYYY-YY with dash).
 * Converts "2025/26" -> "2025-26"
 * @param {string} season - Season string in any format
 * @returns {string} - Normalized season in YYYY-YY format
 */
function normalizeSeasonFormat(season) {
    if (!season) return season;
    return season.replace('/', '-');
}

/**
 * Format season for display (YYYY/YY with slash).
 * Converts "2025-26" -> "2025/26"
 * @param {string} season - Season string in standard format
 * @returns {string} - Display format with slash
 */
function formatSeasonForDisplay(season) {
    if (!season) return season;
    return season.replace('-', '/');
}

class HistoricalExplorer {
    constructor() {
        this.api = window.api || window.apiClient;
        this.container = null;
        this.seasons = [];
        this.currentSeason = null;
        this.data = null;
        this.charts = {};

        // Pagination state
        this.page = 0;
        this.pageSize = 10;

        // Sorting state
        this.sortBy = 'points';
        this.sortDir = 'desc';

        // Filter state
        this.teamFilter = '';

        // Expanded rows
        this.expandedRows = new Set();

        // Debounce timer for filter
        this.filterDebounce = null;
    }

    /**
     * Initialize the historical explorer
     */
    async init() {
        console.log('[Historical] Initializing Historical Season Explorer');

        // Wait for DOM to be ready
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => this.load());
        } else {
            await this.load();
        }
    }

    /**
     * Load the explorer
     */
    async load() {
        this.container = document.getElementById('historicalContent') ||
                        document.querySelector('.main-content');

        if (!this.container) {
            console.warn('[Historical] Container not found');
            return;
        }

        this.showLoading();

        try {
            // Load available seasons
            await this.loadSeasons();

            if (this.seasons.length > 0) {
                this.currentSeason = this.seasons[0];
                await this.loadSeasonData();
            } else {
                this.showEmpty('No seasons available', 'No historical data has been loaded yet.');
            }
        } catch (error) {
            console.error('[Historical] Failed to initialize:', error);
            this.showError('Failed to load historical data');
        }
    }

    /**
     * Load available seasons from API
     */
    async loadSeasons() {
        try {
            const response = await fetch('/api/seasons');
            if (!response.ok) throw new Error('Failed to fetch seasons');

            const data = await response.json();
            this.seasons = data.seasons || [];
            console.log('[Historical] Loaded seasons:', this.seasons);
        } catch (error) {
            console.error('[Historical] Failed to load seasons:', error);
            throw error;
        }
    }

    /**
     * Load season data from API
     */
    async loadSeasonData() {
        if (!this.currentSeason) return;

        try {
            const params = new URLSearchParams({
                page: this.page,
                pageSize: this.pageSize,
                sortBy: this.sortBy,
                sortDir: this.sortDir
            });

            if (this.teamFilter) {
                params.append('team', this.teamFilter);
            }

            // Normalize season format for API call (use dash format)
            // Standard format: "2025-26" (dash), Display format: "2025/26" (slash)
            const normalizedSeason = normalizeSeasonFormat(this.currentSeason);
            const response = await fetch(`/api/seasons/${normalizedSeason}/stats?${params}`);
            if (!response.ok) throw new Error('Failed to fetch season stats');

            this.data = await response.json();
            console.log('[Historical] Loaded season data:', this.data);

            this.render();
        } catch (error) {
            console.error('[Historical] Failed to load season data:', error);
            this.showError('Failed to load season statistics');
        }
    }

    /**
     * Render the full historical view
     */
    render() {
        if (!this.container) return;

        // Destroy existing charts
        Object.values(this.charts).forEach(chart => {
            if (chart && typeof chart.destroy === 'function') {
                chart.destroy();
            }
        });
        this.charts = {};

        const html = `
            <div class="content-header">
                <h2 class="page-title">📈 Historical Season Explorer</h2>
                <p class="page-description">Explore team statistics across different seasons</p>
            </div>

            <div class="historical-header">
                <div class="season-selector">
                    <label for="seasonSelect">Season:</label>
                    <select id="seasonSelect" class="season-select">
                        ${this.seasons.map(s => `
                            <option value="${s}" ${s === this.currentSeason ? 'selected' : ''}>${s}</option>
                        `).join('')}
                    </select>
                </div>

                <div class="historical-controls">
                    <div class="filter-wrapper">
                        <input type="text"
                               id="teamFilter"
                               class="filter-input"
                               placeholder="Filter by team name..."
                               value="${this.teamFilter}">
                    </div>

                    <select id="sortSelect" class="sort-select">
                        <option value="points" ${this.sortBy === 'points' ? 'selected' : ''}>Sort by Points</option>
                        <option value="winRate" ${this.sortBy === 'winRate' ? 'selected' : ''}>Sort by Win %</option>
                        <option value="matches" ${this.sortBy === 'matches' ? 'selected' : ''}>Sort by Matches</option>
                        <option value="team" ${this.sortBy === 'team' ? 'selected' : ''}>Sort by Team</option>
                    </select>

                    <button id="sortDirBtn" class="sort-direction-btn" title="Toggle sort direction">
                        ${this.sortDir === 'desc' ? '↓' : '↑'}
                    </button>
                </div>
            </div>

            ${this.renderSummary()}

            ${this.data && this.data.teamStats && this.data.teamStats.length > 0
                ? this.renderTable()
                : this.renderEmptyState()}

            ${this.renderPagination()}
        `;

        this.container.innerHTML = html;
        this.attachEventListeners();
    }

    /**
     * Render season summary cards
     */
    renderSummary() {
        if (!this.data) return '';

        const avgWinRate = this.calculateAverage('winRate');
        // Total goals scored by all teams / total matches = avg goals per match
        const totalGoals = this.data.teamStats ? this.data.teamStats.reduce((acc, team) => acc + (team.goalsScored || 0), 0) : 0;
        const avgGoalsPerMatch = this.data.totalMatches > 0 ? totalGoals / this.data.totalMatches : 0;

        return `
            <div class="season-summary">
                <div class="summary-card">
                    <div class="summary-card-value">${this.data.totalTeams || 0}</div>
                    <div class="summary-card-label">Teams</div>
                </div>
                <div class="summary-card">
                    <div class="summary-card-value">${this.data.totalMatches || 0}</div>
                    <div class="summary-card-label">Matches</div>
                </div>
                <div class="summary-card">
                    <div class="summary-card-value">${avgWinRate.toFixed(1)}%</div>
                    <div class="summary-card-label">Avg Win Rate</div>
                </div>
                <div class="summary-card">
                    <div class="summary-card-value">${avgGoalsPerMatch.toFixed(2)}</div>
                    <div class="summary-card-label">Avg Goals/Match</div>
                </div>
            </div>
        `;
    }

    /**
     * Calculate average of a field across all teams
     */
    calculateAverage(field) {
        if (!this.data || !this.data.teamStats || this.data.teamStats.length === 0) return 0;
        const sum = this.data.teamStats.reduce((acc, team) => acc + (team[field] || 0), 0);
        return sum / this.data.teamStats.length;
    }

    /**
     * Render the data table
     */
    renderTable() {
        const stats = this.data.teamStats || [];

        return `
            <div class="historical-table-container">
                <table class="historical-table">
                    <thead>
                        <tr>
                            <th></th>
                            <th class="${this.sortBy === 'team' ? 'sorted' : ''}" data-sort="team">
                                Team <span class="sort-indicator">${this.getSortIndicator('team')}</span>
                            </th>
                            <th class="${this.sortBy === 'matches' ? 'sorted' : ''}" data-sort="matches">
                                Matches <span class="sort-indicator">${this.getSortIndicator('matches')}</span>
                            </th>
                            <th class="${this.sortBy === 'points' ? 'sorted' : ''}" data-sort="points">
                                Points <span class="sort-indicator">${this.getSortIndicator('points')}</span>
                            </th>
                            <th class="${this.sortBy === 'winRate' ? 'sorted' : ''}" data-sort="winRate">
                                Win % <span class="sort-indicator">${this.getSortIndicator('winRate')}</span>
                            </th>
                            <th>W/D/L</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${stats.map((team, index) => this.renderTeamRow(team, index)).join('')}
                    </tbody>
                </table>
            </div>
        `;
    }

    /**
     * Get sort indicator for column
     */
    getSortIndicator(field) {
        if (this.sortBy !== field) return '⇅';
        return this.sortDir === 'desc' ? '↓' : '↑';
    }

    /**
     * Render a single team row
     */
    renderTeamRow(team, index) {
        const isExpanded = this.expandedRows.has(team.team);
        const rowId = `team-row-${index}`;

        return `
            <tr class="team-data-row" data-team="${team.team}">
                <td>
                    <button class="expand-btn ${isExpanded ? 'expanded' : ''}"
                            data-team="${team.team}"
                            title="Show performance chart">
                        ▼
                    </button>
                </td>
                <td>
                    <span class="team-name">
                        <span class="team-logo">⚽</span>
                        ${team.team}
                    </span>
                </td>
                <td class="stat-value">${team.matches}</td>
                <td class="stat-value"><strong>${team.points}</strong></td>
                <td>
                    <span class="rate-badge ${this.getRateBadgeClass(team.winRate, 50, 30)}">
                        ${team.winRate.toFixed(1)}%
                    </span>
                </td>
                <td class="stat-value">${team.wins}/${team.draws}/${team.losses}</td>
            </tr>
            ${isExpanded ? this.renderChartRow(team, rowId) : ''}
        `;
    }

    /**
     * Get CSS class for rate badge based on thresholds
     */
    getRateBadgeClass(value, highThreshold, lowThreshold) {
        if (value >= highThreshold) return 'high';
        if (value >= lowThreshold) return 'medium';
        return 'low';
    }

    /**
     * Render the detail row for a team (tabular data instead of chart)
     */
    renderChartRow(team, rowId) {
        const recentForm = team.recentForm || [];

        if (recentForm.length === 0) {
            return `
                <tr class="chart-row" id="${rowId}-chart">
                    <td colspan="6">
                        <div class="team-detail-container">
                            <div class="empty-message">No recent match data available</div>
                        </div>
                    </td>
                </tr>
            `;
        }

        // Calculate cumulative points
        const matchesWithCumulative = [];
        let cumulativePoints = 0;
        const reversedForm = [...recentForm].reverse();
        reversedForm.forEach(m => {
            cumulativePoints += m.points;
            matchesWithCumulative.push({
                ...m,
                cumulativePoints
            });
        });
        // Reverse back to show most recent first
        matchesWithCumulative.reverse();

        return `
            <tr class="chart-row" id="${rowId}-chart">
                <td colspan="6">
                    <div class="team-detail-container">
                        <h4 class="detail-title">${team.team} - Season Match History (${matchesWithCumulative.length} matches)</h4>
                        <div class="detail-table-wrapper">
                            <table class="detail-table">
                                <thead>
                                    <tr>
                                        <th>Date</th>
                                        <th>Opponent</th>
                                        <th>Result</th>
                                        <th>Goals Scored</th>
                                        <th>Goals Conceded</th>
                                        <th>Points</th>
                                        <th>Cumulative Pts</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    ${matchesWithCumulative.map(match => `
                                        <tr>
                                            <td>${match.date || '-'}</td>
                                            <td>${match.opponent || '-'}</td>
                                            <td>
                                                <span class="result-badge result-${(match.result || '').toLowerCase()}">
                                                    ${match.result || '-'}
                                                </span>
                                            </td>
                                            <td class="stat-value">${match.goalsScored ?? '-'}</td>
                                            <td class="stat-value">${match.goalsConceded ?? '-'}</td>
                                            <td class="stat-value">${match.points ?? '-'}</td>
                                            <td class="stat-value"><strong>${match.cumulativePoints}</strong></td>
                                        </tr>
                                    `).join('')}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </td>
            </tr>
        `;
    }

    /**
     * Render empty state
     */
    renderEmptyState() {
        return `
            <div class="historical-empty">
                <div class="empty-icon">📊</div>
                <div class="empty-title">No Data Found</div>
                <div class="empty-message">
                    ${this.teamFilter
                        ? `No teams found matching "${this.teamFilter}". Try a different search.`
                        : 'No statistics available for this season.'}
                </div>
            </div>
        `;
    }

    /**
     * Render pagination controls
     */
    renderPagination() {
        if (!this.data || !this.data.pagination) return '';

        const { page, pageSize, totalItems, totalPages } = this.data.pagination;

        if (totalPages <= 1) return '';

        const startItem = page * pageSize + 1;
        const endItem = Math.min((page + 1) * pageSize, totalItems);

        // Generate page buttons
        let pageButtons = '';
        const maxButtons = 5;
        let startPage = Math.max(0, page - Math.floor(maxButtons / 2));
        let endPage = Math.min(totalPages - 1, startPage + maxButtons - 1);

        if (endPage - startPage < maxButtons - 1) {
            startPage = Math.max(0, endPage - maxButtons + 1);
        }

        for (let i = startPage; i <= endPage; i++) {
            pageButtons += `
                <button class="pagination-btn ${i === page ? 'active' : ''}"
                        data-page="${i}">
                    ${i + 1}
                </button>
            `;
        }

        return `
            <div class="pagination">
                <button class="pagination-btn" data-page="0" ${page === 0 ? 'disabled' : ''}>
                    ««
                </button>
                <button class="pagination-btn" data-page="${page - 1}" ${page === 0 ? 'disabled' : ''}>
                    ‹ Prev
                </button>

                ${pageButtons}

                <button class="pagination-btn" data-page="${page + 1}" ${page >= totalPages - 1 ? 'disabled' : ''}>
                    Next ›
                </button>
                <button class="pagination-btn" data-page="${totalPages - 1}" ${page >= totalPages - 1 ? 'disabled' : ''}>
                    »»
                </button>

                <span class="pagination-info">
                    Showing ${startItem}-${endItem} of ${totalItems}
                </span>

                <div class="page-size-selector">
                    <label for="pageSizeSelect">Per page:</label>
                    <select id="pageSizeSelect" class="page-size-select">
                        <option value="10" ${pageSize === 10 ? 'selected' : ''}>10</option>
                        <option value="20" ${pageSize === 20 ? 'selected' : ''}>20</option>
                        <option value="50" ${pageSize === 50 ? 'selected' : ''}>50</option>
                    </select>
                </div>
            </div>
        `;
    }

    /**
     * Attach event listeners
     */
    attachEventListeners() {
        // Season selector
        const seasonSelect = document.getElementById('seasonSelect');
        if (seasonSelect) {
            seasonSelect.addEventListener('change', (e) => {
                this.currentSeason = e.target.value;
                this.page = 0;
                this.expandedRows.clear();
                this.showLoading();
                this.loadSeasonData();
            });
        }

        // Team filter
        const teamFilter = document.getElementById('teamFilter');
        if (teamFilter) {
            teamFilter.addEventListener('input', (e) => {
                clearTimeout(this.filterDebounce);
                this.filterDebounce = setTimeout(() => {
                    this.teamFilter = e.target.value;
                    this.page = 0;
                    this.loadSeasonData();
                }, 300);
            });
        }

        // Sort selector
        const sortSelect = document.getElementById('sortSelect');
        if (sortSelect) {
            sortSelect.addEventListener('change', (e) => {
                this.sortBy = e.target.value;
                this.page = 0;
                this.loadSeasonData();
            });
        }

        // Sort direction button
        const sortDirBtn = document.getElementById('sortDirBtn');
        if (sortDirBtn) {
            sortDirBtn.addEventListener('click', () => {
                this.sortDir = this.sortDir === 'desc' ? 'asc' : 'desc';
                this.page = 0;
                this.loadSeasonData();
            });
        }

        // Table header sorting
        document.querySelectorAll('.historical-table th[data-sort]').forEach(th => {
            th.addEventListener('click', () => {
                const field = th.dataset.sort;
                if (this.sortBy === field) {
                    this.sortDir = this.sortDir === 'desc' ? 'asc' : 'desc';
                } else {
                    this.sortBy = field;
                    this.sortDir = field === 'team' ? 'asc' : 'desc';
                }
                this.page = 0;
                this.loadSeasonData();
            });
        });

        // Expand buttons
        document.querySelectorAll('.expand-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const team = e.target.dataset.team;
                this.toggleExpand(team);
            });
        });

        // Pagination buttons
        document.querySelectorAll('.pagination-btn[data-page]').forEach(btn => {
            btn.addEventListener('click', () => {
                if (btn.disabled) return;
                this.page = parseInt(btn.dataset.page);
                this.expandedRows.clear();
                this.loadSeasonData();
            });
        });

        // Page size selector
        const pageSizeSelect = document.getElementById('pageSizeSelect');
        if (pageSizeSelect) {
            pageSizeSelect.addEventListener('change', (e) => {
                this.pageSize = parseInt(e.target.value);
                this.page = 0;
                this.expandedRows.clear();
                this.loadSeasonData();
            });
        }

        // Initialize charts for expanded rows
        this.initializeCharts();
    }

    /**
     * Toggle expanded state for a team row
     */
    toggleExpand(team) {
        if (this.expandedRows.has(team)) {
            this.expandedRows.delete(team);
        } else {
            this.expandedRows.add(team);
        }
        this.render();
    }

    /**
     * Initialize detail views for expanded rows
     * (No longer using charts - now using tabular data)
     */
    initializeCharts() {
        // No longer needed since we use tabular data instead of charts
    }

    /**
     * Show loading state with skeleton loaders
     */
    showLoading() {
        if (!this.container) return;

        this.container.innerHTML = `
            <div class="content-header fade-in-down">
                <h2 class="page-title">📈 Historical Season Explorer</h2>
                <p class="page-description">Exploring season statistics and team performance</p>
            </div>
            <div class="historical-controls card mb-3 fade-in stagger-1">
                <div class="flex items-center gap-2">
                    <div class="skeleton" style="width: 200px; height: 40px;"></div>
                    <div class="skeleton" style="width: 250px; height: 40px;"></div>
                </div>
            </div>
            <div class="historical-table card fade-in stagger-2">
                <div class="skeleton-row">
                    <div class="skeleton skeleton-circle"></div>
                    <div class="skeleton-content">
                        <div class="skeleton skeleton-text" style="width: 80%"></div>
                        <div class="skeleton skeleton-text" style="width: 60%"></div>
                    </div>
                </div>
                <div class="skeleton-row">
                    <div class="skeleton skeleton-circle"></div>
                    <div class="skeleton-content">
                        <div class="skeleton skeleton-text" style="width: 75%"></div>
                        <div class="skeleton skeleton-text" style="width: 55%"></div>
                    </div>
                </div>
                <div class="skeleton-row">
                    <div class="skeleton skeleton-circle"></div>
                    <div class="skeleton-content">
                        <div class="skeleton skeleton-text" style="width: 85%"></div>
                        <div class="skeleton skeleton-text" style="width: 65%"></div>
                    </div>
                </div>
                <div class="skeleton-row">
                    <div class="skeleton skeleton-circle"></div>
                    <div class="skeleton-content">
                        <div class="skeleton skeleton-text" style="width: 70%"></div>
                        <div class="skeleton skeleton-text" style="width: 50%"></div>
                    </div>
                </div>
                <div class="skeleton-row">
                    <div class="skeleton skeleton-circle"></div>
                    <div class="skeleton-content">
                        <div class="skeleton skeleton-text" style="width: 80%"></div>
                        <div class="skeleton skeleton-text" style="width: 60%"></div>
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Show error state
     */
    showError(message) {
        if (!this.container) return;

        this.container.innerHTML = `
            <div class="content-header">
                <h2 class="page-title">📈 Historical Season Explorer</h2>
                <p class="page-description">Error loading data</p>
            </div>
            <div class="historical-empty">
                <div class="empty-icon">⚠️</div>
                <div class="empty-title">Error</div>
                <div class="empty-message">${message}</div>
                <button class="btn btn-primary" onclick="window.historicalExplorer.load()" style="margin-top: 1rem;">
                    Retry
                </button>
            </div>
        `;
    }

    /**
     * Show empty state
     */
    showEmpty(title, message) {
        if (!this.container) return;

        this.container.innerHTML = `
            <div class="content-header">
                <h2 class="page-title">📈 Historical Season Explorer</h2>
                <p class="page-description">Explore team statistics across seasons</p>
            </div>
            <div class="historical-empty">
                <div class="empty-icon">📊</div>
                <div class="empty-title">${title}</div>
                <div class="empty-message">${message}</div>
            </div>
        `;
    }

    /**
     * Cleanup when leaving the view
     */
    destroy() {
        // Destroy all charts
        Object.values(this.charts).forEach(chart => {
            if (chart && typeof chart.destroy === 'function') {
                chart.destroy();
            }
        });
        this.charts = {};
        this.expandedRows.clear();

        if (this.filterDebounce) {
            clearTimeout(this.filterDebounce);
        }
    }
}

// Create and export singleton instance
const historicalExplorer = new HistoricalExplorer();
window.historicalExplorer = historicalExplorer;

// Initialize when the document is ready or when navigating to historical view
document.addEventListener('DOMContentLoaded', () => {
    // Check if we're on the historical page
    if (window.location.hash === '#historical') {
        historicalExplorer.init();
    }
});

// Listen for hash changes (SPA navigation)
window.addEventListener('hashchange', () => {
    if (window.location.hash === '#historical') {
        historicalExplorer.init();
    } else {
        historicalExplorer.destroy();
    }
});

console.log('[Historical] Historical Season Explorer loaded');

