/**
 * Team Logo Utilities
 * Provides helper functions for displaying team logos with fallback support.
 * Uses online logo URLs from football-data.org (CORS-friendly).
 */
(function() {
    'use strict';

    // Default fallback logo - online CDN football icon (CORS-friendly)
    const DEFAULT_LOGO = 'https://cdn-icons-png.flaticon.com/512/861/861512.png';
    const LOGO_CACHE = new Map();

    /**
     * TeamLogoManager - Singleton for managing team logos
     */
    class TeamLogoManager {
        constructor() {
            this.logoMap = new Map();
            this.loaded = false;
            this.loadPromise = null;
        }

        /**
         * Load all team logos from the API
         */
        async loadLogos() {
            if (this.loaded) return this.logoMap;

            if (this.loadPromise) return this.loadPromise;

            this.loadPromise = fetch('/api/teams/logos')
                .then(response => {
                    if (!response.ok) throw new Error('Failed to load logos');
                    return response.json();
                })
                .then(data => {
                    Object.entries(data).forEach(([team, url]) => {
                        this.logoMap.set(team.toLowerCase(), url);
                    });
                    this.loaded = true;
                    console.log('[TeamLogoManager] Loaded', this.logoMap.size, 'team logos');
                    return this.logoMap;
                })
                .catch(error => {
                    console.warn('[TeamLogoManager] Failed to load logos:', error.message);
                    return this.logoMap;
                });

            return this.loadPromise;
        }

        /**
         * Get logo URL for a team
         */
        getLogoUrl(teamName) {
            if (!teamName) return DEFAULT_LOGO;
            const key = teamName.toLowerCase();
            return this.logoMap.get(key) || DEFAULT_LOGO;
        }

        /**
         * Check if logos are loaded
         */
        isLoaded() {
            return this.loaded;
        }
    }

    // Create singleton instance
    const logoManager = new TeamLogoManager();

    /**
     * Create a team logo HTML element with fallback support
     * @param {string} teamName - The team name
     * @param {string} size - Size variant: 'sm', 'md', 'lg', 'xl'
     * @param {string} [logoUrl] - Optional explicit logo URL
     * @returns {string} HTML string for the team logo
     */
    function createTeamLogoHTML(teamName, size = 'md', logoUrl = null) {
        const url = logoUrl || logoManager.getLogoUrl(teamName) || DEFAULT_LOGO;
        const sizeClass = `team-logo--${size}`;
        const escapedName = escapeHtml(teamName || 'Team');

        // Debug logging
        if (!logoUrl || logoUrl === DEFAULT_LOGO) {
            console.log('[TeamLogos] Using URL for', teamName, ':', url);
        }

        return `
            <div class="team-logo ${sizeClass}" title="${escapedName}">
                <img src="${url}"
                     alt="${escapedName} logo"
                     crossorigin="anonymous"
                     onerror="this.onerror=null; this.removeAttribute('crossorigin'); this.src='${DEFAULT_LOGO}';"
                     loading="lazy">
            </div>`;
    }

    /**
     * Create a team logo element (DOM node) with fallback support
     * @param {string} teamName - The team name
     * @param {string} size - Size variant
     * @param {string} [logoUrl] - Optional explicit logo URL
     * @returns {HTMLElement} The team logo element
     */
    function createTeamLogoElement(teamName, size = 'md', logoUrl = null) {
        const url = logoUrl || logoManager.getLogoUrl(teamName) || DEFAULT_LOGO;

        const container = document.createElement('div');
        container.className = `team-logo team-logo--${size}`;
        container.title = teamName || 'Team';

        const img = document.createElement('img');
        img.src = url;
        img.alt = `${teamName || 'Team'} logo`;
        img.loading = 'lazy';
        img.crossOrigin = 'anonymous';
        img.onerror = function() {
            this.onerror = null;
            this.removeAttribute('crossorigin');
            this.src = DEFAULT_LOGO;
        };

        container.appendChild(img);
        return container;
    }

    /**
     * Create HTML for team cell with logo and name
     * @param {string} teamName - The team name
     * @param {string} [logoUrl] - Optional explicit logo URL
     * @returns {string} HTML string for team cell
     */
    function createTeamCellHTML(teamName, logoUrl = null) {
        const logo = createTeamLogoHTML(teamName, 'sm', logoUrl);
        const escapedName = escapeHtml(teamName || 'Unknown Team');

        return `
            <div class="team-cell">
                ${logo}
                <span class="team-name">${escapedName}</span>
            </div>
        `;
    }

    /**
     * Create HTML for match display with home and away logos
     * @param {Object} match - Match object with homeTeam and awayTeam
     * @returns {string} HTML string for match teams display
     */
    function createMatchTeamsHTML(match) {
        const homeTeam = typeof match.homeTeam === 'object' ?
            (match.homeTeam.name || match.homeTeam.shortName || 'Home') :
            (match.homeTeam || 'Home');
        const awayTeam = typeof match.awayTeam === 'object' ?
            (match.awayTeam.name || match.awayTeam.shortName || 'Away') :
            (match.awayTeam || 'Away');

        const homeLogo = match.homeTeam?.logoUrl || logoManager.getLogoUrl(homeTeam);
        const awayLogo = match.awayTeam?.logoUrl || logoManager.getLogoUrl(awayTeam);

        return `
            <div class="match-teams">
                <div class="match-team">
                    ${createTeamLogoHTML(homeTeam, 'md', homeLogo)}
                    <span class="team-name">${escapeHtml(homeTeam)}</span>
                </div>
                <span class="match-vs">VS</span>
                <div class="match-team">
                    ${createTeamLogoHTML(awayTeam, 'md', awayLogo)}
                    <span class="team-name">${escapeHtml(awayTeam)}</span>
                </div>
            </div>
        `;
    }

    /**
     * Escape HTML special characters
     */
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Preload team logos for a list of teams
     * @param {Array<string>} teamNames - Array of team names
     */
    function preloadTeamLogos(teamNames) {
        teamNames.forEach(teamName => {
            const url = logoManager.getLogoUrl(teamName);
            if (url && !LOGO_CACHE.has(url)) {
                const img = new Image();
                img.src = url;
                LOGO_CACHE.set(url, img);
            }
        });
    }

    /**
     * Update all team logos on the page that use a specific team name
     * @param {string} teamName - The team name to update
     * @param {string} logoUrl - The new logo URL
     */
    function updateTeamLogos(teamName, logoUrl) {
        const normalizedName = teamName.toLowerCase();
        logoManager.logoMap.set(normalizedName, logoUrl);

        // Update any existing logos on the page
        document.querySelectorAll(`.team-logo[title="${teamName}"] img`).forEach(img => {
            img.src = logoUrl;
        });
    }

    // Export to global scope
    window.TeamLogos = {
        manager: logoManager,
        loadLogos: () => logoManager.loadLogos(),
        getLogoUrl: (teamName) => logoManager.getLogoUrl(teamName),
        createLogoHTML: createTeamLogoHTML,
        createLogoElement: createTeamLogoElement,
        createTeamCellHTML: createTeamCellHTML,
        createMatchTeamsHTML: createMatchTeamsHTML,
        preloadLogos: preloadTeamLogos,
        updateLogo: updateTeamLogos,
        DEFAULT_LOGO: DEFAULT_LOGO
    };

    // Auto-load logos when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => logoManager.loadLogos());
    } else {
        logoManager.loadLogos();
    }

    console.log('[TeamLogos] Module initialized');
})();

