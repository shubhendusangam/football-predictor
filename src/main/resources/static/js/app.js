/**
 * ⚽ Football Match Predictor - Frontend Application
 *
 * This script handles all UI interactions and API calls for the
 * Football Prediction application.
 */

// ═══════════════════════════════════════════════════════════════════════════
// Configuration
// ═══════════════════════════════════════════════════════════════════════════

const API_BASE = '/api';

const ENDPOINTS = {
    predict: `${API_BASE}/predict`,
    teams: `${API_BASE}/teams`,
    modelStatus: `${API_BASE}/model/status`,
    trainModel: `${API_BASE}/model/train`,
    reloadData: `${API_BASE}/data/reload`,
    updateData: `${API_BASE}/data/update`,
    // External API endpoints
    externalPredict: `${API_BASE}/external/predict`,
    externalStandings: `${API_BASE}/external/standings`,
    externalUpcoming: `${API_BASE}/external/upcoming`,
    matchesByDate: `${API_BASE}/external/matches-by-date`,
    // News endpoints
    newsPL: `${API_BASE}/news/premier-league`,
    newsFootball: `${API_BASE}/news/football`,
    newsTeam: `${API_BASE}/news/team`
};

// ═══════════════════════════════════════════════════════════════════════════
// DOM Elements
// ═══════════════════════════════════════════════════════════════════════════

const elements = {
    // Form elements
    predictionForm: document.getElementById('predictionForm'),
    homeTeamSelect: document.getElementById('homeTeam'),
    awayTeamSelect: document.getElementById('awayTeam'),
    predictBtn: document.getElementById('predictBtn'),

    // Status elements
    modelStatus: document.getElementById('modelStatus'),
    statusIndicator: document.getElementById('statusIndicator'),
    statusText: document.getElementById('statusText'),

    // Results elements
    resultsCard: document.getElementById('resultsCard'),
    resultHomeTeam: document.getElementById('resultHomeTeam'),
    resultAwayTeam: document.getElementById('resultAwayTeam'),
    predictionBadge: document.getElementById('predictionBadge'),
    predictionValue: document.getElementById('predictionValue'),
    confidenceBadge: document.getElementById('confidenceBadge'),

    // Probability elements
    probHomeValue: document.getElementById('probHomeValue'),
    probDrawValue: document.getElementById('probDrawValue'),
    probAwayValue: document.getElementById('probAwayValue'),
    probHomeFill: document.getElementById('probHomeFill'),
    probDrawFill: document.getElementById('probDrawFill'),
    probAwayFill: document.getElementById('probAwayFill'),

    // Feature elements
    homeFormPoints: document.getElementById('homeFormPoints'),
    awayFormPoints: document.getElementById('awayFormPoints'),
    homeGoalsAvg: document.getElementById('homeGoalsAvg'),
    awayGoalsAvg: document.getElementById('awayGoalsAvg'),
    h2hHomeWin: document.getElementById('h2hHomeWin'),
    h2hDraw: document.getElementById('h2hDraw'),
    h2hAwayWin: document.getElementById('h2hAwayWin'),

    // Error elements
    errorCard: document.getElementById('errorCard'),
    errorMessage: document.getElementById('errorMessage'),
    errorHint: document.getElementById('errorHint'),

    // Admin elements
    trainModelBtn: document.getElementById('trainModelBtn'),
    reloadDataBtn: document.getElementById('reloadDataBtn'),
    updateDataBtn: document.getElementById('updateDataBtn'),
    checkStatusBtn: document.getElementById('checkStatusBtn'),
    adminOutput: document.getElementById('adminOutput'),
    adminOutputText: document.getElementById('adminOutputText'),

    // External API elements
    fetchUpcomingBtn: document.getElementById('fetchUpcomingBtn'),
    upcomingResults: document.getElementById('upcomingResults'),
    competitionName: document.getElementById('competitionName'),
    matchdayInfo: document.getElementById('matchdayInfo'),
    matchesList: document.getElementById('matchesList'),
    standingsSection: document.getElementById('standingsSection'),
    standingsBody: document.getElementById('standingsBody'),

    // Calendar elements
    matchDate: document.getElementById('matchDate'),
    fetchByDateBtn: document.getElementById('fetchByDateBtn'),
    todayBtn: document.getElementById('todayBtn'),
    tomorrowBtn: document.getElementById('tomorrowBtn'),
    weekendBtn: document.getElementById('weekendBtn'),
    calendarResults: document.getElementById('calendarResults'),
    selectedDateDisplay: document.getElementById('selectedDateDisplay'),
    matchCount: document.getElementById('matchCount'),
    calendarMatchesList: document.getElementById('calendarMatchesList'),
    noMatchesMessage: document.getElementById('noMatchesMessage'),

    // News elements
    refreshNewsBtn: document.getElementById('refreshNewsBtn'),
    newsList: document.getElementById('newsList'),
    newsLoading: document.getElementById('newsLoading'),
    newsEmpty: document.getElementById('newsEmpty'),
    newsTabs: document.querySelectorAll('.news-tab'),

    // Toast container
    toastContainer: document.getElementById('toastContainer')
};

// ═══════════════════════════════════════════════════════════════════════════
// Utility Functions
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Make an API request with error handling
 */
async function apiRequest(url, options = {}) {
    try {
        const response = await fetch(url, {
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            },
            ...options
        });

        const data = await response.json();

        if (!response.ok) {
            throw { status: response.status, ...data };
        }

        return data;
    } catch (error) {
        if (error.status) {
            throw error;
        }
        throw { error: 'Network error. Please check your connection.' };
    }
}

/**
 * Show a toast notification
 */
function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;

    elements.toastContainer.appendChild(toast);

    setTimeout(() => {
        toast.style.animation = 'slideIn 0.3s ease reverse';
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

/**
 * Set button loading state
 */
function setButtonLoading(button, isLoading) {
    const btnText = button.querySelector('.btn-text');
    const btnLoader = button.querySelector('.btn-loader');

    if (isLoading) {
        btnText.style.display = 'none';
        btnLoader.style.display = 'inline';
        button.disabled = true;
    } else {
        btnText.style.display = 'inline';
        btnLoader.style.display = 'none';
        button.disabled = false;
    }
}

/**
 * Format percentage
 */
function formatPercent(value) {
    return `${Math.round(value * 100)}%`;
}

// ═══════════════════════════════════════════════════════════════════════════
// API Functions
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Check model status
 */
async function checkModelStatus() {
    try {
        const data = await apiRequest(ENDPOINTS.modelStatus);
        updateModelStatus(data.modelLoaded);
        return data;
    } catch (error) {
        console.error('Failed to check model status:', error);
        updateModelStatus(false);
        return { modelLoaded: false };
    }
}

/**
 * Update model status UI
 */
function updateModelStatus(isLoaded) {
    elements.statusIndicator.className = 'status-indicator ' + (isLoaded ? 'ready' : 'not-ready');
    elements.statusText.textContent = isLoaded ? 'Model Ready' : 'Model Not Loaded';
}

/**
 * Load teams into dropdowns
 */
async function loadTeams() {
    try {
        const teams = await apiRequest(ENDPOINTS.teams);

        // Clear existing options (except first placeholder)
        elements.homeTeamSelect.innerHTML = '<option value="">Select Home Team</option>';
        elements.awayTeamSelect.innerHTML = '<option value="">Select Away Team</option>';

        // Add team options
        teams.forEach(team => {
            const homeOption = document.createElement('option');
            homeOption.value = team;
            homeOption.textContent = team;
            elements.homeTeamSelect.appendChild(homeOption);

            const awayOption = document.createElement('option');
            awayOption.value = team;
            awayOption.textContent = team;
            elements.awayTeamSelect.appendChild(awayOption);
        });

        showToast(`Loaded ${teams.length} teams`, 'success');
    } catch (error) {
        console.error('Failed to load teams:', error);
        showToast('Failed to load teams. Make sure data is loaded.', 'error');
    }
}

/**
 * Make a prediction
 */
async function makePrediction(homeTeam, awayTeam) {
    setButtonLoading(elements.predictBtn, true);
    hideResults();
    hideError();

    try {
        const data = await apiRequest(ENDPOINTS.predict, {
            method: 'POST',
            body: JSON.stringify({ homeTeam, awayTeam })
        });

        displayResults(data);
        showToast('Prediction completed!', 'success');
    } catch (error) {
        displayError(error.error || 'Prediction failed', error.hint);
    } finally {
        setButtonLoading(elements.predictBtn, false);
    }
}

/**
 * Train the model
 */
async function trainModel() {
    setButtonLoading(elements.trainModelBtn, true);
    showAdminOutput('Training model... This may take 30-60 seconds.');

    try {
        const data = await apiRequest(ENDPOINTS.trainModel, { method: 'POST' });
        showAdminOutput(data.report || 'Model trained successfully!');
        showToast('Model trained successfully!', 'success');
        checkModelStatus();
    } catch (error) {
        showAdminOutput(`Error: ${error.error || 'Training failed'}`);
        showToast('Model training failed', 'error');
    } finally {
        setButtonLoading(elements.trainModelBtn, false);
    }
}

/**
 * Reload data from CSV files
 */
async function reloadData() {
    setButtonLoading(elements.reloadDataBtn, true);
    showAdminOutput('Reloading data from CSV files...');

    try {
        const data = await apiRequest(ENDPOINTS.reloadData, { method: 'POST' });
        showAdminOutput(data.status || 'Data reloaded successfully!');
        showToast('Data reloaded!', 'success');
        loadTeams(); // Refresh team list
    } catch (error) {
        showAdminOutput(`Error: ${error.error || 'Data reload failed'}`);
        showToast('Data reload failed', 'error');
    } finally {
        setButtonLoading(elements.reloadDataBtn, false);
    }
}

/**
 * Update data from football-data.co.uk and retrain model
 */
async function updateData() {
    setButtonLoading(elements.updateDataBtn, true);
    showAdminOutput('🔄 Downloading latest data from football-data.co.uk...\nThis may take 1-2 minutes if retraining is needed.');

    try {
        const data = await apiRequest(ENDPOINTS.updateData, { method: 'POST' });
        showAdminOutput(data.result || 'Data updated successfully!');
        showToast('Data updated!', 'success');
        loadTeams(); // Refresh team list
        checkModelStatus(); // Refresh model status
    } catch (error) {
        showAdminOutput(`Error: ${error.error || 'Data update failed'}`);
        showToast('Data update failed', 'error');
    } finally {
        setButtonLoading(elements.updateDataBtn, false);
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// UI Update Functions
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Display prediction results
 */
function displayResults(data) {
    // Update match header
    elements.resultHomeTeam.textContent = data.homeTeam;
    elements.resultAwayTeam.textContent = data.awayTeam;

    // Update prediction
    elements.predictionValue.textContent = data.prediction;
    elements.predictionValue.className = 'prediction-value ' + getPredictionClass(data.predictionCode);

    // Update confidence
    elements.confidenceBadge.textContent = data.confidence;
    elements.confidenceBadge.className = 'confidence-badge ' + data.confidence.toLowerCase();

    // Update probabilities
    const probHome = data.probHomeWin;
    const probDraw = data.probDraw;
    const probAway = data.probAwayWin;

    elements.probHomeValue.textContent = formatPercent(probHome);
    elements.probDrawValue.textContent = formatPercent(probDraw);
    elements.probAwayValue.textContent = formatPercent(probAway);

    elements.probHomeFill.style.width = `${probHome * 100}%`;
    elements.probDrawFill.style.width = `${probDraw * 100}%`;
    elements.probAwayFill.style.width = `${probAway * 100}%`;

    // Update features
    if (data.features) {
        elements.homeFormPoints.textContent = data.features.homeFormPoints?.toFixed(2) || '-';
        elements.awayFormPoints.textContent = data.features.awayFormPoints?.toFixed(2) || '-';
        elements.homeGoalsAvg.textContent = data.features.homeGoalsScoredAvg?.toFixed(2) || '-';
        elements.awayGoalsAvg.textContent = data.features.awayGoalsScoredAvg?.toFixed(2) || '-';
        elements.h2hHomeWin.textContent = formatPercent(data.features.h2hHomeWinRate || 0);
        elements.h2hDraw.textContent = formatPercent(data.features.h2hDrawRate || 0);
        elements.h2hAwayWin.textContent = formatPercent(data.features.h2hAwayWinRate || 0);
    }

    // Show results card
    elements.resultsCard.style.display = 'block';
    elements.resultsCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

/**
 * Get CSS class for prediction type
 */
function getPredictionClass(code) {
    switch (code) {
        case 'H': return 'home-win';
        case 'D': return 'draw';
        case 'A': return 'away-win';
        default: return '';
    }
}

/**
 * Hide results card
 */
function hideResults() {
    elements.resultsCard.style.display = 'none';
}

/**
 * Display error message
 */
function displayError(message, hint = '') {
    elements.errorMessage.textContent = message;
    elements.errorHint.textContent = hint || '';
    elements.errorCard.style.display = 'block';
}

/**
 * Hide error card
 */
function hideError() {
    elements.errorCard.style.display = 'none';
}

/**
 * Show admin output
 */
function showAdminOutput(text) {
    elements.adminOutput.style.display = 'block';
    elements.adminOutputText.textContent = text;
}

// ═══════════════════════════════════════════════════════════════════════════
// Event Handlers
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Handle prediction form submission
 */
function handlePredictionSubmit(e) {
    e.preventDefault();

    const homeTeam = elements.homeTeamSelect.value;
    const awayTeam = elements.awayTeamSelect.value;

    if (!homeTeam || !awayTeam) {
        showToast('Please select both teams', 'warning');
        return;
    }

    if (homeTeam === awayTeam) {
        showToast('Home and Away teams cannot be the same', 'warning');
        return;
    }

    makePrediction(homeTeam, awayTeam);
}

/**
 * Handle check status button click
 */
async function handleCheckStatus() {
    try {
        const data = await checkModelStatus();
        showAdminOutput(JSON.stringify(data, null, 2));
        showToast(data.modelLoaded ? 'Model is ready!' : 'Model not loaded', data.modelLoaded ? 'success' : 'warning');
    } catch (error) {
        showToast('Failed to check status', 'error');
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// External API Functions
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Fetch and predict upcoming matches using external API
 * Currently only supports Premier League (PL)
 */
async function fetchUpcomingPredictions() {
    const competition = 'PL'; // Premier League only
    setButtonLoading(elements.fetchUpcomingBtn, true);

    try {
        const data = await apiRequest(`${ENDPOINTS.externalPredict}?competition=${competition}&limit=10`);
        displayUpcomingPredictions(data);

        // Also fetch standings
        await fetchStandings(competition);

        showToast(`Loaded ${data.predictions?.length || 0} upcoming Premier League matches`, 'success');
    } catch (error) {
        console.error('Failed to fetch upcoming predictions:', error);
        showToast(error.error || 'Failed to fetch upcoming matches', 'error');

        if (error.hint) {
            showToast(error.hint, 'warning');
        }
    } finally {
        setButtonLoading(elements.fetchUpcomingBtn, false);
    }
}

/**
 * Fetch current standings
 */
async function fetchStandings(competition) {
    try {
        const data = await apiRequest(`${ENDPOINTS.externalStandings}?competition=${competition}`);
        displayStandings(data);
    } catch (error) {
        console.error('Failed to fetch standings:', error);
    }
}

/**
 * Display upcoming match predictions
 */
function displayUpcomingPredictions(data) {
    elements.upcomingResults.style.display = 'block';

    // Update header info
    elements.competitionName.textContent = data.competitionName || data.competition;
    elements.matchdayInfo.textContent = data.currentMatchday ? `Matchday ${data.currentMatchday}` : '';

    // Clear and populate matches list
    elements.matchesList.innerHTML = '';

    if (!data.predictions || data.predictions.length === 0) {
        elements.matchesList.innerHTML = '<p class="no-matches">No upcoming matches found</p>';
        return;
    }

    data.predictions.forEach(match => {
        const matchCard = createMatchCard(match);
        elements.matchesList.appendChild(matchCard);
    });
}

/**
 * Create a match card element
 */
function createMatchCard(match) {
    const card = document.createElement('div');
    card.className = 'match-card';

    const predictionClass = match.error ? 'error' : getPredictionClass(match.predictionCode);
    const formattedDate = formatMatchDate(match.matchDate);

    // Build home team form display
    const homeFormHtml = buildTeamFormHtml(match.homeTeamForm);
    const awayFormHtml = buildTeamFormHtml(match.awayTeamForm);

    card.innerHTML = `
        <div class="team home">
            ${match.homeTeamCrest ? `<img src="${match.homeTeamCrest}" alt="${match.homeTeam}" class="team-crest">` : ''}
            <div class="team-info">
                <span class="team-name">${match.homeTeam}</span>
                ${homeFormHtml}
            </div>
        </div>
        <div class="prediction-info">
            <span class="match-date">${formattedDate}</span>
            <span class="prediction-result ${predictionClass}">
                ${match.error ? '❓ Unknown' : match.prediction}
            </span>
            ${!match.error ? `
                <span class="confidence-text">${match.confidence} confidence</span>
                <span class="probs">
                    H: ${formatPercent(match.probHomeWin)} |
                    D: ${formatPercent(match.probDraw)} |
                    A: ${formatPercent(match.probAwayWin)}
                </span>
            ` : `<span class="confidence-text">${match.error}</span>`}
        </div>
        <div class="team away">
            <div class="team-info">
                <span class="team-name">${match.awayTeam}</span>
                ${awayFormHtml}
            </div>
            ${match.awayTeamCrest ? `<img src="${match.awayTeamCrest}" alt="${match.awayTeam}" class="team-crest">` : ''}
        </div>
    `;

    return card;
}

/**
 * Build team form HTML with position, points and recent form
 */
function buildTeamFormHtml(teamForm) {
    if (!teamForm) return '<span class="form-stats no-data">-</span>';

    const parts = [];

    // Position and points
    if (teamForm.position) {
        parts.push(`<span class="form-position">#${teamForm.position}</span>`);
    }
    if (teamForm.points !== null && teamForm.points !== undefined) {
        parts.push(`<span class="form-points">${teamForm.points} pts</span>`);
    }
    if (teamForm.pointsPerGame) {
        parts.push(`<span class="form-ppg">(${teamForm.pointsPerGame.toFixed(2)} ppg)</span>`);
    }

    // Win/Draw/Loss record
    if (teamForm.won !== undefined && teamForm.draw !== undefined && teamForm.lost !== undefined) {
        parts.push(`<span class="form-record">${teamForm.won}W ${teamForm.draw}D ${teamForm.lost}L</span>`);
    }

    // Recent form badges (W, D, L) or generate indicator
    let formBadge = '';
    if (teamForm.recentForm) {
        formBadge = createFormBadge(teamForm.recentForm);
    } else if (teamForm.won !== undefined && teamForm.played) {
        // Generate indicator when actual form not available
        formBadge = generateFormIndicator(teamForm.won, teamForm.draw, teamForm.lost, teamForm.played);
    }

    // If we have no parts and no form badge, show dash
    if (parts.length === 0 && !formBadge) {
        return '<span class="form-stats no-data">-</span>';
    }

    return `
        <div class="form-stats">
            ${parts.length > 0 ? `<span class="form-summary">${parts.join(' ')}</span>` : ''}
            ${formBadge}
        </div>
    `;
}

/**
 * Create form badge HTML (W, D, L indicators)
 * Handles both "W,W,D,L,W" and "WWDLW" formats
 */
function createFormBadge(formString) {
    if (!formString) return '';

    // Handle both comma-separated and continuous formats
    let results;
    if (formString.includes(',')) {
        results = formString.split(',').map(r => r.trim());
    } else {
        // Split individual characters (WWDLW -> ['W', 'W', 'D', 'L', 'W'])
        results = formString.split('');
    }

    // Filter to only W, D, L
    results = results.filter(r => ['W', 'D', 'L'].includes(r.toUpperCase()));

    if (results.length === 0) return '';

    const badges = results.map(r => {
        const result = r.toUpperCase();
        return `<span class="form-item ${result}">${result}</span>`;
    }).join('');

    return `<div class="form-badge">${badges}</div>`;
}

/**
 * Display standings table
 */
function displayStandings(data) {
    elements.standingsSection.style.display = 'block';
    elements.standingsBody.innerHTML = '';

    console.log('Standings data received:', data);

    if (!data.standings || data.standings.length === 0) {
        elements.standingsSection.style.display = 'none';
        return;
    }

    // Find TOTAL standings
    const totalStandings = data.standings.find(s => s.type === 'TOTAL');
    console.log('Total standings:', totalStandings);

    if (!totalStandings || !totalStandings.table) {
        elements.standingsSection.style.display = 'none';
        return;
    }

    // Log first entry to see form field
    if (totalStandings.table.length > 0) {
        console.log('First table entry:', totalStandings.table[0]);
        console.log('Form field value:', totalStandings.table[0].form);
    }

    totalStandings.table.forEach(entry => {
        const row = document.createElement('tr');

        // Try to get form, or generate a simple indicator from W/D/L ratio
        let formDisplay = '-';
        if (entry.form) {
            formDisplay = createFormBadge(entry.form);
        } else if (entry.won !== undefined && entry.draw !== undefined && entry.lost !== undefined) {
            // Generate simple form indicator based on recent performance
            formDisplay = generateFormIndicator(entry.won, entry.draw, entry.lost, entry.playedGames);
        }

        row.innerHTML = `
            <td class="position">${entry.position}</td>
            <td>
                <div class="team-cell">
                    ${entry.team?.crest ? `<img src="${entry.team.crest}" alt="${entry.team.name}" class="team-crest">` : ''}
                    <span>${entry.team?.shortName || entry.team?.name || 'Unknown'}</span>
                </div>
            </td>
            <td>${entry.playedGames || 0}</td>
            <td>${entry.won || 0}</td>
            <td>${entry.draw || 0}</td>
            <td>${entry.lost || 0}</td>
            <td>${entry.goalsFor || 0}</td>
            <td>${entry.goalsAgainst || 0}</td>
            <td class="points">${entry.points || 0}</td>
            <td>${formDisplay}</td>
        `;
        elements.standingsBody.appendChild(row);
    });
}

/**
 * Generate a form indicator based on win rate when actual form string is not available
 */
function generateFormIndicator(won, draw, lost, played) {
    if (!played || played === 0) return '-';

    const winRate = won / played;
    const drawRate = draw / played;

    // Create a visual indicator based on performance
    let indicator = '';
    if (winRate >= 0.6) {
        indicator = '<span class="form-indicator good">●●●●●</span>';
    } else if (winRate >= 0.4) {
        indicator = '<span class="form-indicator decent">●●●○○</span>';
    } else if (winRate >= 0.2) {
        indicator = '<span class="form-indicator average">●●○○○</span>';
    } else {
        indicator = '<span class="form-indicator poor">●○○○○</span>';
    }

    return indicator;
}

/**
 * Format match date for display
 */
function formatMatchDate(dateString) {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleDateString('en-GB', {
        weekday: 'short',
        day: 'numeric',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit'
    });
}

// ═══════════════════════════════════════════════════════════════════════════
// Calendar Functions
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Format date as YYYY-MM-DD for API
 */
function formatDateForApi(date) {
    return date.toISOString().split('T')[0];
}

/**
 * Format date for display
 */
function formatDateForDisplay(dateStr) {
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-GB', {
        weekday: 'long',
        day: 'numeric',
        month: 'short',
        year: 'numeric'
    });
}

/**
 * Get next weekend date (Saturday)
 */
function getNextWeekend() {
    const today = new Date();
    const dayOfWeek = today.getDay();
    const daysUntilSaturday = (6 - dayOfWeek + 7) % 7 || 7;
    const saturday = new Date(today);
    saturday.setDate(today.getDate() + daysUntilSaturday);
    return saturday;
}

/**
 * Set date input value
 */
function setDateInput(date) {
    elements.matchDate.value = formatDateForApi(date);
}

/**
 * Fetch matches for selected date
 */
async function fetchMatchesByDate() {
    const dateValue = elements.matchDate.value;

    if (!dateValue) {
        showToast('Please select a date', 'warning');
        return;
    }

    setButtonLoading(elements.fetchByDateBtn, true);
    elements.calendarResults.style.display = 'none';
    elements.noMatchesMessage.style.display = 'none';

    try {
        const data = await apiRequest(`${ENDPOINTS.matchesByDate}?date=${dateValue}&competition=PL`);

        if (data.matchCount === 0) {
            // No matches found
            elements.noMatchesMessage.style.display = 'block';
            showToast('No matches found for this date', 'info');
        } else {
            // Display matches
            displayCalendarMatches(data);
            showToast(`Found ${data.matchCount} match${data.matchCount > 1 ? 'es' : ''}`, 'success');
        }
    } catch (error) {
        console.error('Failed to fetch matches by date:', error);
        showToast(error.error || 'Failed to fetch matches', 'error');
        elements.noMatchesMessage.style.display = 'block';
    } finally {
        setButtonLoading(elements.fetchByDateBtn, false);
    }
}

/**
 * Display calendar matches
 */
function displayCalendarMatches(data) {
    elements.calendarResults.style.display = 'block';
    elements.noMatchesMessage.style.display = 'none';

    // Update header
    elements.selectedDateDisplay.textContent = formatDateForDisplay(data.date);
    elements.matchCount.textContent = `${data.matchCount} match${data.matchCount > 1 ? 'es' : ''}`;

    // Clear and populate matches
    elements.calendarMatchesList.innerHTML = '';

    data.matches.forEach(match => {
        const matchCard = createCalendarMatchCard(match);
        elements.calendarMatchesList.appendChild(matchCard);
    });
}

/**
 * Create a calendar match card (similar to createMatchCard but with match time emphasis)
 */
function createCalendarMatchCard(match) {
    const card = document.createElement('div');
    card.className = 'match-card calendar-match';

    const predictionClass = match.error ? 'error' : getPredictionClass(match.predictionCode);
    const matchTime = formatMatchTime(match.matchDate);
    const matchStatus = getMatchStatus(match);

    // Build home team form display
    const homeFormHtml = buildTeamFormHtml(match.homeTeamForm);
    const awayFormHtml = buildTeamFormHtml(match.awayTeamForm);

    card.innerHTML = `
        <div class="team home">
            ${match.homeTeamCrest ? `<img src="${match.homeTeamCrest}" alt="${match.homeTeam}" class="team-crest">` : ''}
            <div class="team-info">
                <span class="team-name">${match.homeTeam}</span>
                ${homeFormHtml}
            </div>
        </div>
        <div class="prediction-info">
            <span class="match-time">${matchTime}</span>
            ${matchStatus ? `<span class="match-status ${matchStatus.toLowerCase()}">${matchStatus}</span>` : ''}
            <span class="prediction-result ${predictionClass}">
                ${match.error ? '❓ Unknown' : match.prediction}
            </span>
            ${!match.error ? `
                <span class="confidence-text">${match.confidence} confidence</span>
                <span class="probs">
                    H: ${formatPercent(match.probHomeWin)} |
                    D: ${formatPercent(match.probDraw)} |
                    A: ${formatPercent(match.probAwayWin)}
                </span>
            ` : `<span class="confidence-text">${match.error}</span>`}
        </div>
        <div class="team away">
            <div class="team-info">
                <span class="team-name">${match.awayTeam}</span>
                ${awayFormHtml}
            </div>
            ${match.awayTeamCrest ? `<img src="${match.awayTeamCrest}" alt="${match.awayTeam}" class="team-crest">` : ''}
        </div>
    `;

    return card;
}

/**
 * Format match time
 */
function formatMatchTime(dateString) {
    if (!dateString) return 'TBD';
    const date = new Date(dateString);
    return date.toLocaleTimeString('en-GB', {
        hour: '2-digit',
        minute: '2-digit'
    });
}

/**
 * Get match status (for finished matches)
 */
function getMatchStatus(match) {
    // Check if match date is in the past
    if (!match.matchDate) return null;
    const matchDate = new Date(match.matchDate);
    const now = new Date();

    if (matchDate < now) {
        return 'FINISHED';
    }
    return null;
}

/**
 * Initialize calendar with today's date
 */
function initCalendar() {
    const today = new Date();
    setDateInput(today);
}

// ═══════════════════════════════════════════════════════════════════════════
// News Functions
// ═══════════════════════════════════════════════════════════════════════════

let currentNewsTab = 'pl';

/**
 * Fetch news based on current tab
 */
async function fetchNews(tab = currentNewsTab) {
    currentNewsTab = tab;

    elements.newsLoading.style.display = 'block';
    elements.newsEmpty.style.display = 'none';
    elements.newsList.querySelectorAll('.news-article').forEach(el => el.remove());

    try {
        const endpoint = tab === 'pl' ? ENDPOINTS.newsPL : ENDPOINTS.newsFootball;
        const data = await apiRequest(endpoint);

        elements.newsLoading.style.display = 'none';

        if (!data.articles || data.articles.length === 0) {
            elements.newsEmpty.style.display = 'block';
            return;
        }

        displayNews(data.articles);

    } catch (error) {
        console.error('Failed to fetch news:', error);
        elements.newsLoading.style.display = 'none';
        elements.newsEmpty.style.display = 'block';
    }
}

/**
 * Display news articles
 */
function displayNews(articles) {
    elements.newsList.querySelectorAll('.news-article').forEach(el => el.remove());

    articles.forEach(article => {
        const articleEl = createNewsArticle(article);
        elements.newsList.appendChild(articleEl);
    });
}

/**
 * Create a news article element
 */
function createNewsArticle(article) {
    const el = document.createElement('article');
    el.className = 'news-article';

    const publishedDate = article.publishedAt ? formatNewsDate(article.publishedAt) : '';
    const imageUrl = article.urlToImage || '';

    el.innerHTML = `
        ${imageUrl ? `<img src="${imageUrl}" alt="" class="news-article-image" onerror="this.style.display='none'">` : ''}
        <div class="news-article-content">
            <h3 class="news-article-title">
                <a href="${article.url}" target="_blank" rel="noopener noreferrer">${article.title || 'No title'}</a>
            </h3>
            <div class="news-article-meta">
                <span class="news-article-source">${article.source?.name || 'Unknown source'}</span>
                <span class="news-article-date">${publishedDate}</span>
            </div>
            <p class="news-article-description">${article.description || ''}</p>
        </div>
    `;

    return el;
}

/**
 * Format news date - handles various date formats from RSS feeds
 */
function formatNewsDate(dateString) {
    if (!dateString) return '';

    // Try to parse the date
    let date;
    try {
        date = new Date(dateString);
        // Check if date is valid
        if (isNaN(date.getTime())) {
            return dateString; // Return original if can't parse
        }
    } catch (e) {
        return dateString;
    }

    const now = new Date();
    const diffMs = now - date;
    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

    if (diffMs < 0) return 'Just now'; // Future date, show as now
    if (diffHours < 1) return 'Just now';
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;

    return date.toLocaleDateString('en-GB', {
        day: 'numeric',
        month: 'short'
    });
}

/**
 * Handle news tab click
 */
function handleNewsTabClick(e) {
    const tab = e.target.dataset.tab;
    if (!tab) return;

    // Update active tab
    elements.newsTabs.forEach(t => t.classList.remove('active'));
    e.target.classList.add('active');

    // Fetch news for selected tab
    fetchNews(tab);
}

/**
 * Initialize news
 */
function initNews() {
    // Fetch initial news
    fetchNews('pl');
}

// ═══════════════════════════════════════════════════════════════════════════
// Initialization
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Initialize the application
 */
async function init() {
    console.log('⚽ Football Match Predictor initialized');

    // Attach event listeners
    elements.predictionForm.addEventListener('submit', handlePredictionSubmit);
    elements.trainModelBtn.addEventListener('click', trainModel);
    elements.reloadDataBtn.addEventListener('click', reloadData);
    elements.updateDataBtn.addEventListener('click', updateData);
    elements.checkStatusBtn.addEventListener('click', handleCheckStatus);

    // External API event listeners
    elements.fetchUpcomingBtn.addEventListener('click', fetchUpcomingPredictions);

    // Calendar event listeners
    elements.fetchByDateBtn.addEventListener('click', fetchMatchesByDate);
    elements.matchDate.addEventListener('change', fetchMatchesByDate);
    elements.todayBtn.addEventListener('click', () => {
        setDateInput(new Date());
        fetchMatchesByDate();
    });
    elements.tomorrowBtn.addEventListener('click', () => {
        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        setDateInput(tomorrow);
        fetchMatchesByDate();
    });
    elements.weekendBtn.addEventListener('click', () => {
        setDateInput(getNextWeekend());
        fetchMatchesByDate();
    });

    // News event listeners
    elements.refreshNewsBtn.addEventListener('click', () => fetchNews(currentNewsTab));
    elements.newsTabs.forEach(tab => tab.addEventListener('click', handleNewsTabClick));

    // Initialize calendar with today's date
    initCalendar();

    // Initialize news
    initNews();

    // Load initial data
    await Promise.all([
        checkModelStatus(),
        loadTeams()
    ]);
}

// Start the application when DOM is ready
document.addEventListener('DOMContentLoaded', init);

