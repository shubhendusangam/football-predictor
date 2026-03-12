/**
 * Football Forecaster Dashboard Manager
 * Handles the new 2-row dashboard layout with 5 sections
 *
 * Layout:
 * Row 1: Upcoming Matches (50%) | League Standings (50%)
 * Row 2: Today's Predictions (33%) | Top Teams (33%) | Model Accuracy (33%)
 */

class DashboardManager {
    constructor() {
        this.api = window.api || window.apiClient;
        this.dashboardContainer = null;
        this.refreshInterval = null;
        this.isLoading = false;
        this.topTeamsView = 'points'; // points, gd, form
    }

    /**
     * Initialize dashboard
     */
    async init() {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => this.loadDashboard());
        } else {
            this.loadDashboard();
        }
    }

    /**
     * Load dashboard with new 2-row layout
     */
    async loadDashboard() {
        if (this.isLoading) {
            console.log('[Dashboard] Already loading, skipping...');
            return;
        }

        const mainContent = document.getElementById('mainContent');
        if (!mainContent) {
            console.warn('[Dashboard] Main content not found');
            return;
        }

        this.isLoading = true;

        // Create dashboard structure
        mainContent.innerHTML = `
            <div class="content-header">
                <h2 class="page-title">Dashboard</h2>
                <p class="page-description">Overview of match predictions and team statistics</p>
            </div>
            <div class="dashboard-container" id="dashboardContainer">
                <!-- Row 1: Upcoming Matches + Combined League Table with UCL Race -->
                <div class="dashboard-row-1">
                    <div class="dashboard-card" id="upcomingMatchesCard">
                        ${this.renderLoading()}
                    </div>
                    <div class="dashboard-card dashboard-card-featured" id="leagueStandingsCard">
                        ${this.renderLoading()}
                    </div>
                </div>

                <!-- Row 2: Today's Predictions + Top Teams + Model Accuracy (33/33/33) -->
                <div class="dashboard-row-2">
                    <div class="dashboard-card" id="todaysPredictionsCard">
                        ${this.renderLoading()}
                    </div>
                    <div class="dashboard-card" id="topTeamsCard">
                        ${this.renderLoading()}
                    </div>
                    <div class="dashboard-card" id="modelAccuracyCard">
                        ${this.renderLoading()}
                    </div>
                </div>
            </div>
        `;

        this.dashboardContainer = document.getElementById('dashboardContainer');

        // Check API health
        let isHealthy = false;
        try {
            if (this.api && typeof this.api.healthCheck === 'function') {
                isHealthy = await this.api.healthCheck();
            }
        } catch (e) {
            console.warn('[Dashboard] Health check failed:', e);
        }

        if (!isHealthy) {
            this.showOfflineMode();
            this.isLoading = false;
            return;
        }

        // Ensure team logos are loaded before rendering matches
        try {
            if (window.TeamLogos && typeof window.TeamLogos.loadLogos === 'function') {
                await window.TeamLogos.loadLogos();
                console.log('[Dashboard] Team logos loaded');
            }
        } catch (e) {
            console.warn('[Dashboard] Failed to load team logos:', e);
        }

        // Load all sections in parallel
        try {
            await Promise.allSettled([
                this.loadUpcomingMatches(),
                this.loadLeagueStandingsWithUCL(),
                this.loadTodaysPredictions(),
                this.loadTopTeams(),
                this.loadModelAccuracy()
            ]);
        } catch (error) {
            console.error('[Dashboard] Failed to load dashboard:', error);
        } finally {
            this.isLoading = false;
        }
    }

    /**
     * Render loading skeleton
     */
    renderLoading() {
        return `
            <div class="dashboard-card-loading">
                <div class="dashboard-skeleton title"></div>
                <div class="dashboard-skeleton card"></div>
                <div class="dashboard-skeleton card"></div>
                <div class="dashboard-skeleton card"></div>
            </div>
        `;
    }

    /**
     * Render error state
     */
    renderError(message, retryFn) {
        return `
            <div class="dashboard-error">
                <div class="dashboard-error-icon">⚠️</div>
                <p class="dashboard-error-message">${message}</p>
                <button class="dashboard-error-retry" onclick="${retryFn}">Retry</button>
            </div>
        `;
    }

    /**
     * Load Upcoming Matches section
     */
    async loadUpcomingMatches() {
        const card = document.getElementById('upcomingMatchesCard');
        if (!card) return;

        try {
            const response = await this.api.get('/dashboard/upcoming-matches');
            const matches = response?.matches || [];

            card.innerHTML = `
                <div class="dashboard-card-header">
                    <h3 class="dashboard-card-title">
                        <span class="dashboard-card-title-icon">⚽</span>
                        ${response?.matchDayHeader || 'Upcoming Matches'}
                    </h3>
                    <span class="dashboard-card-badge badge badge-info">${matches.length} Matches</span>
                </div>
                <div class="dashboard-card-body">
                    ${matches.length > 0 ? matches.map(match => this.renderUpcomingMatch(match)).join('') :
                      '<p style="color: var(--text-muted); text-align: center;">No upcoming matches</p>'}
                </div>
            `;
        } catch (error) {
            console.error('[Dashboard] Failed to load upcoming matches:', error);
            card.innerHTML = `
                <div class="dashboard-card-header">
                    <h3 class="dashboard-card-title">
                        <span class="dashboard-card-title-icon">⚽</span>
                        Upcoming Matches
                    </h3>
                </div>
                <div class="dashboard-card-body">
                    ${this.renderError('Failed to load matches', 'dashboardManager.loadUpcomingMatches()')}
                </div>
            `;
        }
    }

    renderUpcomingMatch(match) {
        // Get team logos - prioritize API response fields, then TeamLogos utility, then default
        const defaultLogo = window.TeamLogos?.DEFAULT_LOGO || 'https://cdn-icons-png.flaticon.com/512/861/861512.png';
        const homeLogoUrl = match.homeTeamLogo || window.TeamLogos?.getLogoUrl(match.homeTeam) || defaultLogo;
        const awayLogoUrl = match.awayTeamLogo || window.TeamLogos?.getLogoUrl(match.awayTeam) || defaultLogo;

        return `
            <div class="upcoming-match-item">
                <div class="upcoming-match-teams">
                    <div class="upcoming-match-teams-row">
                        <div class="upcoming-match-team home">
                            <div class="team-logo team-logo--sm">
                                <img src="${homeLogoUrl}"
                                     alt="${match.homeTeam} logo"
                                     onerror="this.onerror=null; this.src='${defaultLogo}';"
                                     loading="lazy">
                            </div>
                            <span class="upcoming-match-team-name">${match.homeTeam}</span>
                            <span class="home-away-badge home-badge" title="Home Team">🏠</span>
                        </div>
                        <span class="upcoming-match-vs">vs</span>
                        <div class="upcoming-match-team away">
                            <span class="home-away-badge away-badge" title="Away Team">✈️</span>
                            <span class="upcoming-match-team-name">${match.awayTeam}</span>
                            <div class="team-logo team-logo--sm">
                                <img src="${awayLogoUrl}"
                                     alt="${match.awayTeam} logo"
                                     onerror="this.onerror=null; this.src='${defaultLogo}';"
                                     loading="lazy">
                            </div>
                        </div>
                    </div>
                </div>
                <div class="upcoming-match-meta">
                    <span class="upcoming-match-date">${match.formattedDate || 'TBD'}</span>
                    <span class="upcoming-match-time">${match.matchTime || ''}</span>
                    ${match.canPredict ? `
                        <button class="upcoming-match-predict-btn"
                                onclick="predictMatch('${match.homeTeam}', '${match.awayTeam}')">
                            🎯 Predict
                        </button>
                    ` : ''}
                </div>
            </div>
        `;
    }

    /**
     * Load Combined League Standings with UCL Race and Relegation Battle data
     */
    async loadLeagueStandingsWithUCL() {
        const card = document.getElementById('leagueStandingsCard');
        if (!card) return;

        try {
            // Load standings, UCL race, and relegation battle data in parallel
            const [standingsResponse, uclResponse, relegationResponse] = await Promise.all([
                this.api.get('/dashboard/league-standings'),
                this.api.getTop4Race(),
                this.api.getRelegationBattle()
            ]);

            const standings = standingsResponse?.standings || [];
            const uclData = uclResponse?.teamsInRace || [];
            const titleRace = uclResponse?.titleRace || {};
            const relegationData = relegationResponse?.teamsInBattle || [];
            const relegationSummary = relegationResponse?.summary || {};

            // Create maps for quick lookup
            const uclMap = {};
            uclData.forEach(team => {
                uclMap[team.teamName] = {
                    probability: team.top4Probability,
                    status: team.status,
                    gapToFourth: team.gapToFourth
                };
            });

            const relegationMap = {};
            relegationData.forEach(team => {
                relegationMap[team.teamName] = {
                    survivalProbability: team.survivalProbability,
                    status: team.status,
                    gapToSafety: team.gapToSafety,
                    desperationLevel: team.desperationLevel
                };
            });

            // Store data for tab switching
            this._standingsData = { standings, uclMap, relegationMap, titleRace, relegationSummary,
                season: standingsResponse?.season, leagueName: standingsResponse?.leagueName,
                uclResponse, relegationResponse };

            card.innerHTML = `
                <div class="dashboard-card-header">
                    <h3 class="dashboard-card-title">
                        <span class="dashboard-card-title-icon">🏆</span>
                        ${standingsResponse?.leagueName || 'Premier League'}
                    </h3>
                    <div class="standings-header-meta">
                        <span class="dashboard-card-badge badge badge-success">${standingsResponse?.season || ''}</span>
                    </div>
                </div>
                <div class="standings-tabs">
                    <button class="standings-tab active" data-tab="full" onclick="dashboardManager.switchStandingsTab('full')">
                        📊 Full Table
                    </button>
                    <button class="standings-tab" data-tab="ucl" onclick="dashboardManager.switchStandingsTab('ucl')">
                        🏆 UCL Race
                    </button>
                    <button class="standings-tab" data-tab="relegation" onclick="dashboardManager.switchStandingsTab('relegation')">
                        ⚠️ Relegation
                    </button>
                </div>
                <div class="dashboard-card-body standings-scrollable" id="standingsTabContent">
                    ${standings.length > 0 ? this.renderCombinedStandingsTable(standings, uclMap, relegationMap) :
                      '<p style="color: var(--text-muted); text-align: center;">No standings available</p>'}
                </div>
            `;
        } catch (error) {
            console.error('[Dashboard] Failed to load standings:', error);
            card.innerHTML = `
                <div class="dashboard-card-header">
                    <h3 class="dashboard-card-title">
                        <span class="dashboard-card-title-icon">🏆</span>
                        League Standings
                    </h3>
                </div>
                <div class="dashboard-card-body">
                    ${this.renderError('Failed to load standings', 'dashboardManager.loadLeagueStandingsWithUCL()')}
                </div>
            `;
        }
    }

    /**
     * Switch between standings tabs (Full Table, UCL Race, Relegation Battle)
     */
    switchStandingsTab(tabName) {
        const data = this._standingsData;
        if (!data) return;

        // Update tab buttons
        document.querySelectorAll('.standings-tab').forEach(tab => {
            tab.classList.toggle('active', tab.dataset.tab === tabName);
        });

        const contentDiv = document.getElementById('standingsTabContent');
        if (!contentDiv) return;

        switch (tabName) {
            case 'ucl':
                contentDiv.innerHTML = this.renderUCLRaceView(data);
                break;
            case 'relegation':
                contentDiv.innerHTML = this.renderRelegationBattleView(data);
                this.animateRelegationBars();
                break;
            default:
                contentDiv.innerHTML = this.renderCombinedStandingsTable(data.standings, data.uclMap, data.relegationMap);
        }
    }

    /**
     * Render UCL Race focused view
     */
    renderUCLRaceView(data) {
        const teams = data.uclResponse?.teamsInRace?.slice(0, 7) || [];
        const titleRace = data.titleRace || {};

        return `
            <div class="ucl-race-view">
                <div class="ucl-race-header-summary">
                    <span class="title-race-leader">Leader: ${titleRace.leader || 'TBD'}</span>
                    <span class="title-race-badge ${(titleRace.intensity || '').toLowerCase().replace(/\s+/g, '-')}">${titleRace.intensity || ''}</span>
                </div>
                <div class="ucl-race-teams">
                    ${teams.map((team, index) => this.renderUCLTeamRow(team, index)).join('')}
                </div>
                <div class="ucl-race-footer">
                    <span>Target: ${data.uclResponse?.pointsForSafety || 72} pts</span>
                    <span>${data.uclResponse?.matchdaysCompleted || 0}/${data.uclResponse?.totalMatchesInSeason || 38} played</span>
                </div>
            </div>
        `;
    }

    /**
     * Render UCL team row
     */
    renderUCLTeamRow(team, index) {
        const position = team.currentPosition || (index + 1);
        const isTop4 = position <= 4;
        const probClass = team.top4Probability >= 80 ? 'prob-safe' :
                         team.top4Probability >= 50 ? 'prob-fighting' :
                         team.top4Probability >= 25 ? 'prob-possible' : 'prob-unlikely';

        return `
            <div class="ucl-team-row ${isTop4 ? 'in-top4' : ''}">
                <span class="ucl-position">${position}</span>
                <div class="ucl-team-info">
                    ${team.teamLogo ? `<img src="${team.teamLogo}" class="ucl-team-logo" alt="">` : ''}
                    <span class="ucl-team-name">${team.teamName}</span>
                </div>
                <span class="ucl-points">${team.points} pts</span>
                <span class="ucl-gap ${team.gapToFourth > 0 ? 'gap-behind' : 'gap-ahead'}">${team.gapToFourth > 0 ? '+' : ''}${team.gapToFourth}</span>
                <div class="ucl-prob-container">
                    <div class="ucl-prob-bar ${probClass}" style="width: ${team.top4Probability}%"></div>
                    <span class="ucl-prob-text">${Math.round(team.top4Probability)}%</span>
                </div>
            </div>
        `;
    }

    /**
     * Render Relegation Battle focused view (enhanced)
     */
    renderRelegationBattleView(data) {
        const teams = data.relegationResponse?.teamsInBattle || [];
        const summary = data.relegationSummary || {};

        // Split teams into above safety and in relegation zone
        const aboveSafety = teams.filter(t => (t.currentPosition || 99) < 18);
        const inDanger = teams.filter(t => (t.currentPosition || 99) >= 18);
        const teamsInDangerCount = inDanger.length || 3;
        const bottomTeam = teams.length > 0 ? teams[teams.length - 1] : null;
        const maxPossibleBottom = bottomTeam ? (bottomTeam.points || 0) + ((bottomTeam.remainingMatches || 0) * 3) : 0;

        return `
            <div class="relegation-battle-view">
                <!-- Enhanced Summary -->
                <div class="relegation-summary-header">
                    <div class="relegation-summary-stat">
                        <span class="stat-value">${summary.safetyLinePoints || 0}</span>
                        <span class="stat-label">17th pts</span>
                    </div>
                    <div class="relegation-gap-indicator ${(summary.gapAtRelegationLine || 0) <= 2 ? 'gap-tight' : ''}">
                        <span class="gap-number">${summary.gapAtRelegationLine || 0}</span>
                        <span class="gap-label">pt gap</span>
                    </div>
                    <div class="relegation-summary-stat danger">
                        <span class="stat-value">${summary.relegationLinePoints || 0}</span>
                        <span class="stat-label">18th pts</span>
                    </div>
                    <div class="relegation-summary-extra">
                        <span class="relegation-danger-count">${teamsInDangerCount} in zone</span>
                        ${maxPossibleBottom > 0 ? `<span class="relegation-max-pts" title="Max possible pts for bottom team">Max: ${maxPossibleBottom}</span>` : ''}
                    </div>
                    <span class="relegation-intensity-badge ${(summary.intensity || 'Calm').toLowerCase()}">${summary.intensity || 'Calm'}</span>
                </div>

                <!-- Column Headers -->
                <div class="relegation-header-row">
                    <span class="rh-pos">#</span>
                    <span class="rh-team">Team</span>
                    <span class="rh-form">Form</span>
                    <span class="rh-pts">Pts</span>
                    <span class="rh-gd hide-mobile-rel">GD</span>
                    <span class="rh-gap">Gap</span>
                    <span class="rh-left hide-mobile-rel">Left</span>
                    <span class="rh-surv">Survival</span>
                </div>

                <!-- Teams above safety line -->
                <div class="relegation-teams">
                    ${aboveSafety.map((team, index) => this.renderRelegationTeamRow(team, index)).join('')}
                </div>

                <!-- Safety Line Separator -->
                <div class="relegation-safety-line">
                    <span class="safety-line-text">── SAFETY LINE ──</span>
                </div>

                <!-- Teams in relegation zone -->
                <div class="relegation-teams relegation-zone-teams">
                    ${inDanger.map((team, index) => this.renderRelegationTeamRow(team, index)).join('')}
                </div>

                <!-- Footer -->
                <div class="relegation-battle-footer">
                    <span>🎯 Target: ${data.relegationResponse?.survivalPointsTarget || 38} pts</span>
                    <span>📅 ${data.relegationResponse?.matchdaysCompleted || 0}/${data.relegationResponse?.totalMatchesInSeason || 38} played</span>
                </div>
            </div>
        `;
    }

    /**
     * Render form dots (W=green, D=gray, L=red)
     */
    renderFormDots(form) {
        if (!form) return '<span class="form-empty">—</span>';
        const colorMap = { 'W': '#34d399', 'D': '#94a3b8', 'L': '#f87171' };
        const labelMap = { 'W': 'Win', 'D': 'Draw', 'L': 'Loss' };
        return form.split('').map(ch => {
            const color = colorMap[ch] || '#4b5563';
            const label = labelMap[ch] || ch;
            return `<span class="form-dot" style="background:${color}" title="${label}"></span>`;
        }).join('');
    }

    /**
     * Render Relegation team row (enhanced with form, GD, remaining matches, tooltip)
     */
    renderRelegationTeamRow(team, index) {
        const position = team.currentPosition || (14 + index);
        const isInRelegationZone = position >= 18;
        const isOnSafetyLine = position === 17;
        const probClass = team.survivalProbability >= 85 ? 'prob-safe' :
                         team.survivalProbability >= 50 ? 'prob-fighting' :
                         team.survivalProbability >= 25 ? 'prob-danger' : 'prob-relegated';

        const desperationIcon = {
            'Low': '',
            'Medium': '⚡',
            'High': '🔥',
            'Extreme': '💀'
        };

        const statusIcon = {
            'Safe': '✓',
            'Fighting': '⚔️',
            'Danger': '🔥',
            'Relegated': '💀'
        };

        const gd = team.goalDifference || 0;
        const gdClass = gd > 0 ? 'gd-positive' : gd < 0 ? 'gd-negative' : 'gd-neutral';
        const ppg = team.pointsPerGame ? team.pointsPerGame.toFixed(2) : '—';
        const needPts = team.pointsNeededForSafety > 0 ? team.pointsNeededForSafety : 0;

        return `
            <div class="relegation-team-row ${isInRelegationZone ? 'in-relegation-zone' : ''} ${isOnSafetyLine ? 'on-safety-line' : ''}">
                <span class="relegation-position">
                    <span class="position-icon ${team.status?.toLowerCase() || ''}">${statusIcon[team.status] || position}</span>
                </span>
                <div class="relegation-team-info">
                    ${team.teamLogo ? `<img src="${team.teamLogo}" class="relegation-team-logo" alt="">` : ''}
                    <span class="relegation-team-name">${team.teamName}</span>
                    ${desperationIcon[team.desperationLevel] ? `<span class="desperation-icon">${desperationIcon[team.desperationLevel]}</span>` : ''}
                </div>
                <div class="relegation-form">${this.renderFormDots(team.form)}</div>
                <span class="relegation-points">${team.points} pts</span>
                <span class="relegation-gd ${gdClass} hide-mobile-rel">${gd > 0 ? '+' : ''}${gd}</span>
                <span class="relegation-gap ${team.gapToSafety >= 0 ? 'gap-safe' : 'gap-danger'}">
                    ${team.gapToSafety >= 0 ? '+' : ''}${team.gapToSafety}
                </span>
                <div class="relegation-remaining hide-mobile-rel">
                    <span class="remaining-count">${team.remainingMatches || '—'}</span>
                    ${needPts > 0 ? `<span class="need-pts-label">need ${needPts}</span>` : ''}
                </div>
                <div class="relegation-prob-container">
                    <div class="relegation-prob-bar ${probClass}" data-probability="${team.survivalProbability || 0}" style="width: 0%"></div>
                    <span class="relegation-prob-text">${Math.round(team.survivalProbability)}%</span>
                </div>
                <!-- Hover tooltip with detailed stats -->
                <div class="relegation-tooltip">
                    <div class="tooltip-row"><span>PPG</span><strong>${ppg}</strong></div>
                    <div class="tooltip-row"><span>Win Rate</span><strong>${team.winRate ? team.winRate.toFixed(1) + '%' : '—'}</strong></div>
                    <div class="tooltip-row"><span>Played</span><strong>${team.played || '—'}</strong></div>
                    <div class="tooltip-row"><span>Gap to 18th</span><strong>${team.gapToRelegation !== undefined ? (team.gapToRelegation >= 0 ? '+' : '') + team.gapToRelegation : '—'}</strong></div>
                    ${needPts > 0 ? `<div class="tooltip-row danger"><span>Pts Needed</span><strong>${needPts}</strong></div>` : ''}
                </div>
            </div>
        `;
    }

    /**
     * Animate relegation probability bars (smooth width transition)
     */
    animateRelegationBars() {
        setTimeout(() => {
            document.querySelectorAll('.relegation-prob-bar[data-probability]').forEach(bar => {
                bar.style.width = `${bar.dataset.probability}%`;
            });
        }, 100);
    }

    /**
     * Render combined standings table with UCL probability and relegation status
     */
    renderCombinedStandingsTable(standings, uclMap, relegationMap) {
        return `
            <table class="standings-table standings-with-ucl">
                <thead>
                    <tr>
                        <th>#</th>
                        <th>Team</th>
                        <th class="text-center">P</th>
                        <th class="text-center hide-mobile">W</th>
                        <th class="text-center hide-mobile">D</th>
                        <th class="text-center hide-mobile">L</th>
                        <th class="text-center hide-mobile">GD</th>
                        <th class="text-center">Pts</th>
                        <th class="text-center">Prob%</th>
                    </tr>
                </thead>
                <tbody>
                    ${standings.map((team, index) => {
                        let rowClass = '';
                        if (index < 4) rowClass = 'top-4';
                        else if (index < 6) rowClass = 'europa';
                        else if (index >= standings.length - 3) rowClass = 'relegation';

                        const ucl = uclMap[team.teamName] || {};
                        const relegation = relegationMap ? relegationMap[team.teamName] : {};

                        // Show UCL prob for top 10, survival prob for bottom 7
                        let prob = 0;
                        let probLabel = '';
                        let probClass = '';

                        if (index < 10 && ucl.probability !== undefined) {
                            prob = ucl.probability;
                            probLabel = 'UCL';
                            probClass = prob >= 80 ? 'ucl-safe' : prob >= 50 ? 'ucl-fighting' : prob >= 20 ? 'ucl-possible' : 'ucl-unlikely';
                        } else if (index >= standings.length - 7 && relegation?.survivalProbability !== undefined) {
                            prob = relegation.survivalProbability;
                            probLabel = 'Surv';
                            probClass = prob >= 85 ? 'surv-safe' : prob >= 50 ? 'surv-fighting' : prob >= 25 ? 'surv-danger' : 'surv-relegated';
                        }

                        return `
                            <tr class="${rowClass}">
                                <td class="standings-position">${team.position || index + 1}</td>
                                <td>
                                    <div class="standings-team">
                                        ${team.teamLogo ? `<img src="${team.teamLogo}" class="standings-team-logo" alt="">` : ''}
                                        <span>${team.teamName}</span>
                                    </div>
                                </td>
                                <td class="text-center">${team.P || team.played || 0}</td>
                                <td class="text-center hide-mobile">${team.W || team.won || 0}</td>
                                <td class="text-center hide-mobile">${team.D || team.drawn || 0}</td>
                                <td class="text-center hide-mobile">${team.L || team.lost || 0}</td>
                                <td class="text-center hide-mobile">${team.GD || team.goalDifference || 0}</td>
                                <td class="text-center standings-pts">${team.Pts || team.points || 0}</td>
                                <td class="text-center">
                                    ${prob > 0 ? `<span class="prob-badge ${probClass}" title="${probLabel}">${Math.round(prob)}%</span>` : '-'}
                                </td>
                            </tr>
                        `;
                    }).join('')}
                </tbody>
            </table>
        `;
    }

    /**
     * @deprecated Use loadLeagueStandingsWithUCL instead
     */
    async loadLeagueStandings() {
        return this.loadLeagueStandingsWithUCL();
    }

    renderForm(form) {
        if (!form) return '-';
        return `
            <div class="standings-form">
                ${form.split('').slice(0, 5).map(result => `
                    <span class="form-indicator ${result}">${result}</span>
                `).join('')}
            </div>
        `;
    }

    /**
     * Load Top 4 Race section (Champions League battle)
     */
    async loadTop4Race() {
        const card = document.getElementById('top4RaceCard');
        if (!card) return;

        try {
            // Initialize the Top4RaceWidget if available
            if (window.Top4RaceWidget) {
                const widget = new window.Top4RaceWidget('top4RaceCard');
                await widget.init();
            } else {
                // Fallback: load data directly
                const response = await this.api.getTop4Race();
                card.innerHTML = this.renderTop4RaceCard(response);
            }
        } catch (error) {
            console.error('[Dashboard] Failed to load Top 4 race:', error);
            card.innerHTML = `
                <div class="dashboard-card-header">
                    <h3 class="dashboard-card-title">
                        <span class="dashboard-card-title-icon">🏆</span>
                        Champions League Race
                    </h3>
                </div>
                <div class="dashboard-card-body">
                    ${this.renderError('Failed to load Top 4 race', 'dashboardManager.loadTop4Race()')}
                </div>
            `;
        }
    }

    /**
     * Fallback renderer for Top 4 Race
     */
    renderTop4RaceCard(data) {
        const teams = data?.teamsInRace?.slice(0, 7) || [];
        return `
            <div class="dashboard-card-header">
                <h3 class="dashboard-card-title">
                    <span class="dashboard-card-title-icon">🏆</span>
                    Champions League Race
                </h3>
                <span class="dashboard-card-badge badge badge-info">${data?.season || ''}</span>
            </div>
            <div class="dashboard-card-body">
                ${teams.length > 0 ? this.renderTop4RaceTeams(teams) :
                  '<p style="color: var(--text-muted); text-align: center;">No race data available</p>'}
            </div>
        `;
    }

    renderTop4RaceTeams(teams) {
        return `
            <div class="top4-race-simple">
                ${teams.map((team, idx) => `
                    <div class="top4-team-row ${team.currentPosition <= 4 ? 'in-top4' : ''}">
                        <span class="top4-position">${team.currentPosition}</span>
                        <span class="top4-team-name">${team.teamName}</span>
                        <span class="top4-points">${team.points} pts</span>
                        <span class="top4-probability" style="color: ${team.top4Probability >= 70 ? '#34d399' : team.top4Probability >= 40 ? '#fbbf24' : '#f87171'}">
                            ${Math.round(team.top4Probability)}%
                        </span>
                    </div>
                `).join('')}
            </div>
        `;
    }

    /**
     * Load Title Race Timeline (projection chart)
     */
    async loadTitleTimeline() {
        const card = document.getElementById('titleTimelineCard');
        if (!card) return;

        try {
            // Initialize the TitleRaceTimeline if available
            if (window.TitleRaceTimeline) {
                const timeline = new window.TitleRaceTimeline('titleTimelineCard');
                await timeline.init();
            } else {
                // Fallback: show simple projection
                const response = await this.api.getTop4Race();
                card.innerHTML = this.renderTitleTimelineCard(response);
            }
        } catch (error) {
            console.error('[Dashboard] Failed to load title timeline:', error);
            card.innerHTML = `
                <div class="dashboard-card-header">
                    <h3 class="dashboard-card-title">
                        <span class="dashboard-card-title-icon">📈</span>
                        Title Race Projection
                    </h3>
                </div>
                <div class="dashboard-card-body">
                    ${this.renderError('Failed to load projection', 'dashboardManager.loadTitleTimeline()')}
                </div>
            `;
        }
    }

    /**
     * Fallback renderer for Title Timeline
     */
    renderTitleTimelineCard(data) {
        const titleRace = data?.titleRace || {};
        const topTeams = data?.teamsInRace?.slice(0, 3) || [];
        return `
            <div class="dashboard-card-header">
                <h3 class="dashboard-card-title">
                    <span class="dashboard-card-title-icon">📈</span>
                    Title Race
                </h3>
                <span class="dashboard-card-badge badge badge-${titleRace.intensity === 'Wide Open' ? 'info' : 'success'}">
                    ${titleRace.intensity || 'Unknown'}
                </span>
            </div>
            <div class="dashboard-card-body">
                <div class="title-race-summary">
                    <div class="title-leader">
                        <span class="title-leader-label">Leader:</span>
                        <span class="title-leader-name">${titleRace.leader || 'TBD'}</span>
                    </div>
                    ${titleRace.gapFirstToSecond !== undefined ? `
                        <div class="title-gap">
                            <span class="title-gap-label">Gap:</span>
                            <span class="title-gap-value">${titleRace.gapFirstToSecond} pts</span>
                        </div>
                    ` : ''}
                    <div class="title-contenders">
                        <span class="title-contenders-label">Contenders:</span>
                        <span class="title-contenders-value">${titleRace.contenders || 0}</span>
                    </div>
                </div>
                <div class="title-progress">
                    <div class="progress-bar">
                        <div class="progress-fill" style="width: ${data?.seasonProgressPercent || 0}%"></div>
                    </div>
                    <span class="progress-text">${data?.matchdaysCompleted || 0}/${data?.totalMatchesInSeason || 38} matches</span>
                </div>
            </div>
        `;
    }

    /**
     * Load Today's Predictions section
     */
    async loadTodaysPredictions() {
        const card = document.getElementById('todaysPredictionsCard');
        if (!card) return;

        try {
            const response = await this.api.get('/dashboard/todays-predictions');
            const predictions = response?.predictions || [];

            card.innerHTML = `
                <div class="dashboard-card-header">
                    <h3 class="dashboard-card-title">
                        <span class="dashboard-card-title-icon">🎯</span>
                        Predictions
                    </h3>
                    <span class="dashboard-card-badge badge badge-${response?.wonCount > response?.lostCount ? 'success' : 'warning'}">
                        ${response?.wonCount || 0}W/${response?.lostCount || 0}L
                    </span>
                </div>
                <div class="dashboard-card-body compact-body">
                    ${predictions.length > 0 ? predictions.slice(0, 5).map(pred => this.renderCompactPrediction(pred)).join('') :
                      '<p style="color: var(--text-muted); text-align: center; font-size: 0.8rem;">No predictions today</p>'}
                </div>
            `;
        } catch (error) {
            console.error('[Dashboard] Failed to load predictions:', error);
            card.innerHTML = `
                <div class="dashboard-card-header">
                    <h3 class="dashboard-card-title">
                        <span class="dashboard-card-title-icon">🎯</span>
                        Predictions
                    </h3>
                </div>
                <div class="dashboard-card-body">
                    ${this.renderError('Failed to load', 'dashboardManager.loadTodaysPredictions()')}
                </div>
            `;
        }
    }

    renderCompactPrediction(pred) {
        const statusClass = pred.status?.toLowerCase() || 'pending';
        return `
            <div class="prediction-item-compact ${statusClass}">
                <div class="prediction-teams-compact">
                    <span class="pred-match">${pred.homeTeam} v ${pred.awayTeam}</span>
                </div>
                <div class="prediction-result-compact">
                    <span class="pred-winner">${pred.predictedWinner}</span>
                    <span class="pred-conf">${Math.round(pred.confidence)}%</span>
                </div>
            </div>
        `;
    }

    renderPrediction(pred) {
        const statusClass = pred.status?.toLowerCase() || 'pending';
        const winnerClass = pred.predictedWinner?.toLowerCase().includes('home') ? 'home' :
                           pred.predictedWinner?.toLowerCase().includes('away') ? 'away' : 'draw';

        return `
            <div class="prediction-item ${statusClass}">
                <div class="prediction-teams">
                    <span class="prediction-match-name">${pred.homeTeam} vs ${pred.awayTeam}</span>
                    <span class="prediction-match-date">${pred.matchDate || ''}</span>
                </div>
                <div class="prediction-result">
                    <span class="prediction-winner ${winnerClass}">${pred.predictedWinner}</span>
                    <span class="prediction-confidence">${Math.round(pred.confidence)}% confidence</span>
                    <span class="prediction-status ${statusClass}">${pred.status}</span>
                </div>
            </div>
        `;
    }

    /**
     * Load Top Teams section
     */
    async loadTopTeams() {
        const card = document.getElementById('topTeamsCard');
        if (!card) return;

        try {
            const response = await this.api.get('/dashboard/top-teams');

            card.innerHTML = `
                <div class="dashboard-card-header">
                    <h3 class="dashboard-card-title">
                        <span class="dashboard-card-title-icon">⭐</span>
                        Top 5
                    </h3>
                    <div class="top-teams-tabs-compact">
                        <button class="tab-btn ${this.topTeamsView === 'points' ? 'active' : ''}"
                                onclick="dashboardManager.switchTopTeamsView('points')">Pts</button>
                        <button class="tab-btn ${this.topTeamsView === 'gd' ? 'active' : ''}"
                                onclick="dashboardManager.switchTopTeamsView('gd')">GD</button>
                        <button class="tab-btn ${this.topTeamsView === 'form' ? 'active' : ''}"
                                onclick="dashboardManager.switchTopTeamsView('form')">Form</button>
                    </div>
                </div>
                <div class="dashboard-card-body compact-body" id="topTeamsContent">
                    ${this.renderCompactTopTeamsList(response)}
                </div>
            `;

            // Store response for tab switching
            this.topTeamsData = response;
        } catch (error) {
            console.error('[Dashboard] Failed to load top teams:', error);
            card.innerHTML = `
                <div class="dashboard-card-header">
                    <h3 class="dashboard-card-title">
                        <span class="dashboard-card-title-icon">⭐</span>
                        Top 5
                    </h3>
                </div>
                <div class="dashboard-card-body">
                    ${this.renderError('Failed to load', 'dashboardManager.loadTopTeams()')}
                </div>
            `;
        }
    }

    switchTopTeamsView(view) {
        this.topTeamsView = view;

        // Update tabs
        document.querySelectorAll('.tab-btn').forEach(tab => {
            tab.classList.remove('active');
        });
        event.target.classList.add('active');

        // Update content
        const content = document.getElementById('topTeamsContent');
        if (content && this.topTeamsData) {
            content.innerHTML = this.renderCompactTopTeamsList(this.topTeamsData);
        }
    }

    renderCompactTopTeamsList(data) {
        let teams = [];
        let metricKey = 'points';

        switch (this.topTeamsView) {
            case 'points':
                teams = data?.teamsByPoints || [];
                metricKey = 'points';
                break;
            case 'gd':
                teams = data?.teamsByGoalDifference || [];
                metricKey = 'goalDifference';
                break;
            case 'form':
                teams = data?.teamsByForm || [];
                metricKey = 'form';
                break;
        }

        if (teams.length === 0) {
            return '<p style="color: var(--text-muted); text-align: center; font-size: 0.8rem;">No data</p>';
        }

        return teams.slice(0, 5).map((team, index) => {
            const rankClass = index === 0 ? 'gold' : index === 1 ? 'silver' : index === 2 ? 'bronze' : '';
            const metricValue = metricKey === 'form' ? (team.form || '-').substring(0, 5) :
                               (metricKey === 'goalDifference' && team[metricKey] > 0 ? '+' : '') + (team[metricKey] || 0);

            return `
                <div class="top-team-row">
                    <span class="team-rank ${rankClass}">${index + 1}</span>
                    <span class="team-name">${team.teamName}</span>
                    <span class="team-metric">${metricValue}</span>
                </div>
            `;
        }).join('');
    }

    renderTopTeamsList(data) {
        let teams = [];
        let metricLabel = 'Pts';
        let metricKey = 'points';

        switch (this.topTeamsView) {
            case 'points':
                teams = data?.teamsByPoints || [];
                metricLabel = 'Pts';
                metricKey = 'points';
                break;
            case 'gd':
                teams = data?.teamsByGoalDifference || [];
                metricLabel = 'GD';
                metricKey = 'goalDifference';
                break;
            case 'form':
                teams = data?.teamsByForm || [];
                metricLabel = 'Form';
                metricKey = 'form';
                break;
        }

        if (teams.length === 0) {
            return '<p style="color: var(--text-muted); text-align: center;">No data available</p>';
        }

        return teams.map((team, index) => {
            const rankClass = index === 0 ? 'gold' : index === 1 ? 'silver' : index === 2 ? 'bronze' : '';
            const metricValue = metricKey === 'form' ? team.form || '-' :
                               (metricKey === 'goalDifference' && team[metricKey] > 0 ? '+' : '') + (team[metricKey] || 0);

            return `
                <div class="top-team-card">
                    <span class="top-team-rank ${rankClass}">${index + 1}</span>
                    <div class="top-team-info">
                        <span class="top-team-name">${team.teamName}</span>
                        <span class="top-team-stats">${team.won || 0}W ${team.drawn || 0}D ${team.lost || 0}L</span>
                    </div>
                    <div class="top-team-metric">
                        <span class="top-team-metric-value">${metricValue}</span>
                        <span class="top-team-metric-label">${metricLabel}</span>
                    </div>
                </div>
            `;
        }).join('');
    }

    /**
     * Load Model Accuracy section
     */
    async loadModelAccuracy() {
        const card = document.getElementById('modelAccuracyCard');
        if (!card) return;

        try {
            const response = await this.api.get('/dashboard/model-accuracy');

            const total = (response?.correctPredictions || 0) + (response?.incorrectPredictions || 0) + (response?.pendingPredictions || 0);
            const correctPct = total > 0 ? ((response?.correctPredictions || 0) / total * 100) : 0;
            const incorrectPct = total > 0 ? ((response?.incorrectPredictions || 0) / total * 100) : 0;

            card.innerHTML = `
                <div class="dashboard-card-header">
                    <h3 class="dashboard-card-title">
                        <span class="dashboard-card-title-icon">🤖</span>
                        AI Model
                    </h3>
                    <span class="dashboard-card-badge badge badge-${response?.overallAccuracy >= 70 ? 'success' : response?.overallAccuracy >= 50 ? 'warning' : 'danger'}">
                        ${response?.modelLoaded ? '✓' : '✗'}
                    </span>
                </div>
                <div class="dashboard-card-body compact-body">
                    <div class="accuracy-main-compact">
                        <div class="accuracy-big">${response?.overallAccuracy || 0}%</div>
                        <div class="accuracy-label-small">Accuracy</div>
                    </div>

                    <div class="accuracy-stats-compact">
                        <div class="acc-stat">
                            <span class="acc-val green">${response?.correctPredictions || 0}</span>
                            <span class="acc-lbl">Won</span>
                        </div>
                        <div class="acc-stat">
                            <span class="acc-val red">${response?.incorrectPredictions || 0}</span>
                            <span class="acc-lbl">Lost</span>
                        </div>
                        <div class="acc-stat">
                            <span class="acc-val">${response?.last10Accuracy || 0}%</span>
                            <span class="acc-lbl">L10</span>
                        </div>
                    </div>

                    <div class="accuracy-bar-compact">
                        <div class="bar-fill correct" style="width: ${correctPct}%"></div>
                        <div class="bar-fill incorrect" style="width: ${incorrectPct}%"></div>
                    </div>

                    <div class="model-info-compact">
                        <span>${response?.modelType || 'N/A'}</span>
                        <span>${response?.totalPredictions || 0} predictions</span>
                    </div>
                </div>
            `;
        } catch (error) {
            console.error('[Dashboard] Failed to load accuracy:', error);
            card.innerHTML = `
                <div class="dashboard-card-header">
                    <h3 class="dashboard-card-title">
                        <span class="dashboard-card-title-icon">🤖</span>
                        AI Model
                    </h3>
                </div>
                <div class="dashboard-card-body">
                    ${this.renderError('Failed to load', 'dashboardManager.loadModelAccuracy()')}
                </div>
            `;
        }
    }

    /**
     * Show offline mode
     */
    showOfflineMode() {
        if (!this.dashboardContainer) return;

        this.dashboardContainer.innerHTML = `
            <div style="grid-column: 1 / -1; background: rgba(251, 191, 36, 0.2); border: 1px solid #fbbf24; border-radius: 0.5rem; padding: 1.5rem; text-align: center;">
                <span style="font-size: 2rem;">⚠️</span>
                <h3 style="color: #fbbf24; margin: 0.5rem 0;">Demo Mode</h3>
                <p style="color: var(--text-muted);">Backend API is not available. Please start the server.</p>
                <button class="btn btn-primary" onclick="dashboardManager.loadDashboard()" style="margin-top: 1rem;">
                    Retry Connection
                </button>
            </div>
        `;
    }

    /**
     * Enable auto-refresh
     */
    enableAutoRefresh(intervalSeconds = 60) {
        this.refreshInterval = setInterval(() => {
            console.log('[Dashboard] Auto-refreshing...');
            this.loadDashboard();
        }, intervalSeconds * 1000);
    }

    /**
     * Disable auto-refresh
     */
    disableAutoRefresh() {
        if (this.refreshInterval) {
            clearInterval(this.refreshInterval);
            this.refreshInterval = null;
        }
    }
}

// Initialize dashboard manager
const dashboardManager = new DashboardManager();
window.dashboardManager = dashboardManager;

/**
 * Global function to predict a match from dashboard
 */
window.predictMatch = function(homeTeam, awayTeam) {
    sessionStorage.setItem('predictHomeTeam', homeTeam);
    sessionStorage.setItem('predictAwayTeam', awayTeam);
    window.location.hash = '#predictions';
};

console.log('[Dashboard] Football Forecaster Dashboard Manager v2 initialized');

