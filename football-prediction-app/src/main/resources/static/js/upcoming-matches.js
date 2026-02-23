/**
 * Upcoming Matches Panel Module
 * Displays upcoming football matches with predict functionality
 */

class UpcomingMatchesPanel {
    constructor(apiClient) {
        this.api = apiClient;
        this.container = null;
        this.matches = [];
        this.isLoading = false;
    }

    /**
     * Initialize and render the panel
     * @param {HTMLElement} container - Container element to render into
     * @param {number} limit - Number of matches to display
     */
    async init(container, limit = 6) {
        this.container = container;
        await this.loadMatches(limit);
    }

    /**
     * Load upcoming matches from API
     */
    async loadMatches(limit = 6) {
        this.isLoading = true;
        this.renderLoading();

        try {
            let response;

            // Support both centralized API (api.get/api.getUpcomingMatches) and legacy APIClient (api.fetch)
            if (this.api && typeof this.api.getUpcomingMatches === 'function') {
                response = await this.api.getUpcomingMatches(limit);
            } else if (this.api && typeof this.api.get === 'function') {
                response = await this.api.get(`/matches/upcoming?limit=${limit}`);
            } else if (this.api && typeof this.api.fetch === 'function') {
                response = await this.api.fetch(`/matches/upcoming?limit=${limit}`);
            } else {
                throw new Error('No compatible API client available');
            }

            this.matches = response?.matches || [];

            if (this.matches.length === 0) {
                this.renderEmptyState();
            } else {
                this.renderMatches();
            }
        } catch (error) {
            console.error('Failed to load upcoming matches:', error);
            this.renderErrorState(error.message);
        } finally {
            this.isLoading = false;
        }
    }

    /**
     * Render loading skeleton
     */
    renderLoading() {
        if (!this.container) return;

        this.container.innerHTML = `
            <div class="upcoming-matches-panel fade-in">
                <div class="panel-header">
                    <h3 class="panel-title">
                        <span class="panel-icon">⚽</span>
                        Upcoming Matches
                    </h3>
                    <span class="panel-badge badge-pulse">Loading...</span>
                </div>
                <div class="matches-grid">
                    ${this.createLoadingSkeleton(3)}
                </div>
            </div>
        `;
    }

    /**
     * Create loading skeleton cards
     */
    createLoadingSkeleton(count = 3) {
        return Array(count).fill(0).map((_, index) => `
            <div class="match-card skeleton-card fade-in stagger-${Math.min(index + 1, 6)}">
                <div class="skeleton skeleton-text" style="width: 40%; height: 0.75rem;"></div>
                <div class="match-teams" style="margin: 1rem 0;">
                    <div class="skeleton skeleton-text" style="width: 35%;"></div>
                    <div class="skeleton" style="width: 2rem; height: 1.5rem; border-radius: 0.25rem;"></div>
                    <div class="skeleton skeleton-text" style="width: 35%;"></div>
                </div>
                <div class="skeleton skeleton-text" style="width: 50%; height: 0.75rem;"></div>
                <div class="match-actions" style="margin-top: 1rem;">
                    <div class="skeleton" style="width: 100px; height: 36px; border-radius: 0.5rem;"></div>
                </div>
            </div>
        `).join('');
    }

    /**
     * Render matches list
     */
    renderMatches() {
        if (!this.container) return;

        const matchCount = this.matches.length;

        this.container.innerHTML = `
            <div class="upcoming-matches-panel fade-in">
                <div class="panel-header">
                    <h3 class="panel-title">
                        <span class="panel-icon">⚽</span>
                        Upcoming Matches
                    </h3>
                    <span class="panel-badge badge-success">${matchCount} ${matchCount === 1 ? 'Match' : 'Matches'}</span>
                </div>
                <div class="matches-grid">
                    ${this.matches.map((match, index) => this.createMatchCard(match, index)).join('')}
                </div>
            </div>
        `;

        // Add event listeners
        this.attachEventListeners();
    }

    /**
     * Create a match card
     */
    createMatchCard(match, index = 0) {
        // Handle both object (TeamInfo) and string team names
        const homeTeam = typeof match.homeTeam === 'object' ? (match.homeTeam?.name || match.homeTeam?.shortName || 'TBD') : (match.homeTeam || 'TBD');
        const awayTeam = typeof match.awayTeam === 'object' ? (match.awayTeam?.name || match.awayTeam?.shortName || 'TBD') : (match.awayTeam || 'TBD');
        const league = match.league || match.competition?.name || 'EPL';
        const matchDate = match.matchDate || match.utcDate;

        // Get team logos - handle multiple API response formats:
        // 1. Nested in team object: match.homeTeam.crest
        // 2. Top-level property: match.homeTeamCrest
        // 3. In separate crest object: match.homeCrest
        // 4. Use TeamLogos utility as fallback
        let homeLogoCrest = (typeof match.homeTeam === 'object' ? match.homeTeam?.crest : null)
            || match.homeTeamCrest
            || match.homeCrest
            || null;
        let awayLogoCrest = (typeof match.awayTeam === 'object' ? match.awayTeam?.crest : null)
            || match.awayTeamCrest
            || match.awayCrest
            || null;

        // Use TeamLogos utility as fallback if no crest URL available
        if (!homeLogoCrest && window.TeamLogos?.getLogoUrl) {
            homeLogoCrest = window.TeamLogos.getLogoUrl(homeTeam);
        }
        if (!awayLogoCrest && window.TeamLogos?.getLogoUrl) {
            awayLogoCrest = window.TeamLogos.getLogoUrl(awayTeam);
        }

        const { formattedDate, formattedTime, relativeTime } = this.formatDateTime(matchDate);
        const staggerClass = `stagger-${Math.min(index + 1, 6)}`;

        // Default fallback logo
        const defaultLogo = 'https://cdn-icons-png.flaticon.com/512/861/861512.png';

        // Generate logo HTML with proper fallback chain
        const homeLogoHtml = this.createLogoHtml(homeLogoCrest, homeTeam, defaultLogo, '🏠');
        const awayLogoHtml = this.createLogoHtml(awayLogoCrest, awayTeam, defaultLogo, '✈️');

        return `
            <article class="match-card fade-in-up ${staggerClass}" data-home="${homeTeam}" data-away="${awayTeam}" role="article" aria-label="Match: ${homeTeam} vs ${awayTeam}">
                <header class="match-league">
                    <span class="league-badge">${league}</span>
                    <span class="match-time-relative">${relativeTime}</span>
                </header>

                <div class="match-teams">
                    <div class="team home-team">
                        <span class="team-logo" aria-hidden="true">${homeLogoHtml}</span>
                        <span class="team-name" title="${homeTeam}">${homeTeam}</span>
                    </div>

                    <div class="match-vs" aria-hidden="true">
                        <span class="vs-text">VS</span>
                    </div>

                    <div class="team away-team">
                        <span class="team-name" title="${awayTeam}">${awayTeam}</span>
                        <span class="team-logo" aria-hidden="true">${awayLogoHtml}</span>
                    </div>
                </div>

                <div class="match-datetime">
                    <time class="match-date" datetime="${matchDate || ''}">${formattedDate}</time>
                    <span class="match-time">${formattedTime}</span>
                </div>

                <footer class="match-actions">
                    <button class="btn-predict" data-home="${homeTeam}" data-away="${awayTeam}" aria-label="Predict match outcome for ${homeTeam} vs ${awayTeam}">
                        <span class="btn-icon" aria-hidden="true">🎯</span>
                        <span class="btn-text">Predict Match</span>
                    </button>
                </footer>
            </article>
        `;
    }

    /**
     * Create logo HTML with fallback chain
     * @param {string|null} logoUrl - Primary logo URL
     * @param {string} teamName - Team name for alt text
     * @param {string} defaultLogo - Default fallback logo URL
     * @param {string} emoji - Emoji fallback if all else fails
     * @returns {string} HTML string for logo
     */
    createLogoHtml(logoUrl, teamName, defaultLogo, emoji) {
        if (logoUrl && logoUrl !== defaultLogo) {
            // Has a logo URL - use image with fallback chain
            return `<img src="${logoUrl}"
                         alt="${teamName}"
                         class="team-logo-img"
                         loading="lazy"
                         crossorigin="anonymous"
                         onerror="this.onerror=null; this.removeAttribute('crossorigin'); this.src='${defaultLogo}'; this.classList.add('fallback-logo');" /><span class="team-logo-fallback" style="display:none;">${emoji}</span>`;
        } else if (defaultLogo) {
            // Use default logo
            return `<img src="${defaultLogo}"
                         alt="${teamName}"
                         class="team-logo-img fallback-logo"
                         loading="lazy"
                         onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';" /><span class="team-logo-fallback" style="display:none;">${emoji}</span>`;
        } else {
            // Final fallback - emoji
            return `<span class="team-logo-emoji">${emoji}</span>`;
        }
    }

    /**
     * Format date and time
     */
    formatDateTime(dateString) {
        if (!dateString) {
            return {
                formattedDate: 'Date TBD',
                formattedTime: '',
                relativeTime: 'Scheduled'
            };
        }

        try {
            const date = new Date(dateString);
            const now = new Date();

            // Format date (e.g., "Mar 15, 2026")
            const formattedDate = date.toLocaleDateString('en-US', {
                month: 'short',
                day: 'numeric',
                year: 'numeric'
            });

            // Format time (e.g., "6:30 PM")
            const formattedTime = date.toLocaleTimeString('en-US', {
                hour: 'numeric',
                minute: '2-digit',
                hour12: true
            });

            // Calculate relative time
            const diffMs = date - now;
            const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
            const diffHours = Math.floor(diffMs / (1000 * 60 * 60));

            let relativeTime;
            if (diffDays < 0) {
                relativeTime = 'Past';
            } else if (diffDays === 0) {
                if (diffHours < 1) {
                    relativeTime = 'Starting Soon';
                } else {
                    relativeTime = 'Today';
                }
            } else if (diffDays === 1) {
                relativeTime = 'Tomorrow';
            } else if (diffDays < 7) {
                relativeTime = `In ${diffDays} days`;
            } else {
                relativeTime = 'Upcoming';
            }

            return { formattedDate, formattedTime, relativeTime };
        } catch (error) {
            console.warn('Invalid date format:', dateString);
            return {
                formattedDate: 'Date TBD',
                formattedTime: '',
                relativeTime: 'Scheduled'
            };
        }
    }

    /**
     * Render empty state
     */
    renderEmptyState() {
        if (!this.container) return;

        this.container.innerHTML = `
            <div class="upcoming-matches-panel">
                <div class="panel-header">
                    <h3 class="panel-title">
                        <span class="panel-icon">⚽</span>
                        Upcoming Matches
                    </h3>
                    <span class="panel-badge">0 Matches</span>
                </div>
                <div class="empty-state">
                    <div class="empty-icon">📅</div>
                    <h4 class="empty-title">No Upcoming Matches</h4>
                    <p class="empty-message">
                        There are no scheduled matches at the moment.
                        Check back later for new fixtures!
                    </p>
                </div>
            </div>
        `;
    }

    /**
     * Render error state
     */
    renderErrorState(errorMessage = 'Failed to load matches') {
        if (!this.container) return;

        this.container.innerHTML = `
            <div class="upcoming-matches-panel">
                <div class="panel-header">
                    <h3 class="panel-title">
                        <span class="panel-icon">⚽</span>
                        Upcoming Matches
                    </h3>
                    <span class="panel-badge error">Error</span>
                </div>
                <div class="error-state">
                    <div class="error-icon">⚠️</div>
                    <h4 class="error-title">Unable to Load Matches</h4>
                    <p class="error-message">${errorMessage}</p>
                    <button class="btn-retry" onclick="window.upcomingMatchesPanel?.loadMatches()">
                        <span class="btn-icon">🔄</span>
                        <span class="btn-text">Try Again</span>
                    </button>
                </div>
            </div>
        `;
    }

    /**
     * Attach event listeners to predict buttons
     */
    attachEventListeners() {
        const predictButtons = this.container.querySelectorAll('.btn-predict');

        predictButtons.forEach(button => {
            button.addEventListener('click', (e) => {
                e.preventDefault();
                const homeTeam = button.dataset.home;
                const awayTeam = button.dataset.away;
                this.handlePredictClick(homeTeam, awayTeam);
            });
        });

        // Add hover effect listeners
        const matchCards = this.container.querySelectorAll('.match-card');
        matchCards.forEach(card => {
            card.addEventListener('mouseenter', () => {
                card.style.transform = 'translateY(-4px)';
            });
            card.addEventListener('mouseleave', () => {
                card.style.transform = 'translateY(0)';
            });
        });
    }

    /**
     * Handle predict button click
     */
    handlePredictClick(homeTeam, awayTeam) {
        console.log(`[UpcomingMatches] Navigate to prediction: ${homeTeam} vs ${awayTeam}`);

        try {
            // Store teams in sessionStorage
            sessionStorage.setItem('predictHomeTeam', homeTeam);
            sessionStorage.setItem('predictAwayTeam', awayTeam);

            // Show toast notification if available
            if (window.UI && typeof window.UI.showToast === 'function') {
                window.UI.showToast(`Predicting: ${homeTeam} vs ${awayTeam}`, 'info', 2000);
            }

            // Navigate to predictions page
            window.location.hash = '#predictions';

        } catch (error) {
            console.error('[UpcomingMatches] Error handling predict click:', error);
            // Fallback: Try direct navigation
            window.location.hash = '#predictions';
        }
    }

    /**
     * Refresh matches
     */
    async refresh(limit = 6) {
        await this.loadMatches(limit);
    }

    /**
     * Destroy/cleanup panel
     */
    destroy() {
        if (this.container) {
            this.container.innerHTML = '';
        }
        this.matches = [];
        this.initialized = false;
    }
}

// Export for use in other modules
window.UpcomingMatchesPanel = UpcomingMatchesPanel;

console.log('[UpcomingMatches] Upcoming Matches Panel module loaded');

