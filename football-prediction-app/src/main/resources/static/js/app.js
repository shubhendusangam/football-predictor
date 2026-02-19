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
    teamStats: `${API_BASE}/teams`, // Base for team stats: /api/teams/{name}/stats
    modelStatus: `${API_BASE}/model/status`,
    trainModel: `${API_BASE}/model/train`,
    trainAdvanced: `${API_BASE}/model/train/advanced`,
    trainCV: `${API_BASE}/model/train/cv`,
    trainBoosting: `${API_BASE}/model/train/boosting`,
    trainEnsemble: `${API_BASE}/model/train/ensemble`,
    gridSearch: `${API_BASE}/model/grid-search`,
    compareModels: `${API_BASE}/model/compare`,
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
    newsTeam: `${API_BASE}/news/team`,
    // Trending insights endpoint
    trendingInsights: `${API_BASE}/insights/trending`,
    // Admin endpoints
    adminVerify: `${API_BASE}/admin/verify`,
    adminLogout: `${API_BASE}/admin/logout`
};

// Responsive utilities
const RESPONSIVE = {
    breakpoints: {
        sm: 640,
        md: 768,
        lg: 1024,
        xl: 1280
    },

    isMobile: () => window.innerWidth < RESPONSIVE.breakpoints.md,
    isTablet: () => window.innerWidth >= RESPONSIVE.breakpoints.md && window.innerWidth < RESPONSIVE.breakpoints.lg,
    isDesktop: () => window.innerWidth >= RESPONSIVE.breakpoints.lg,

    // Detect touch device
    isTouchDevice: () => 'ontouchstart' in window || navigator.maxTouchPoints > 0,

    // Get current breakpoint
    getCurrentBreakpoint: () => {
        const width = window.innerWidth;
        if (width < RESPONSIVE.breakpoints.sm) return 'xs';
        if (width < RESPONSIVE.breakpoints.md) return 'sm';
        if (width < RESPONSIVE.breakpoints.lg) return 'md';
        if (width < RESPONSIVE.breakpoints.xl) return 'lg';
        return 'xl';
    },

    // Debounced resize handler
    onResize: (callback, delay = 250) => {
        let timeoutId;
        return (...args) => {
            clearTimeout(timeoutId);
            timeoutId = setTimeout(() => callback.apply(null, args), delay);
        };
    }
};

// ═══════════════════════════════════════════════════════════════════════════
// DOM Elements
// ═══════════════════════════════════════════════════════════════════════════

const elements = {
    // Form elements
    predictionForm: document.getElementById('predictionForm'),
    predictionCard: document.getElementById('predictionCard'),
    homeTeamSelect: document.getElementById('homeTeam'),
    awayTeamSelect: document.getElementById('awayTeam'),
    predictBtn: document.getElementById('predictBtn'),

    // Mode toggle elements
    modeBtns: document.querySelectorAll('.mode-btn'),
    quickMatches: document.getElementById('quickMatches'),
    quickMatchesGrid: document.getElementById('quickMatchesGrid'),

    // Advanced options
    toggleAdvanced: document.getElementById('toggleAdvanced'),
    advancedOptions: document.querySelector('.options-content'),
    includeWeather: document.getElementById('includeWeather'),
    includeInjuries: document.getElementById('includeInjuries'),
    includeRecent: document.getElementById('includeRecent'),

    // Status elements
    modelStatus: document.getElementById('modelStatus'),
    statusIndicator: document.getElementById('statusIndicator'),
    statusText: document.getElementById('statusText'),
    lastUpdated: document.getElementById('lastUpdated'),

    // Header stats
    totalPredictions: document.getElementById('totalPredictions'),

    // Team form indicators
    homeForm: document.getElementById('homeForm'),
    awayForm: document.getElementById('awayForm'),

    // Results elements
    resultsCard: document.getElementById('resultsCard'),
    resultHomeTeam: document.getElementById('resultHomeTeam'),
    resultAwayTeam: document.getElementById('resultAwayTeam'),
    resultMatchup: document.getElementById('resultMatchup'),
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

    // Admin Modal elements
    adminToggleBtn: document.getElementById('adminToggleBtn'),
    adminModalOverlay: document.getElementById('adminModalOverlay'),
    adminModal: document.getElementById('adminModal'),
    adminModalClose: document.getElementById('adminModalClose'),
    adminLoginSection: document.getElementById('adminLoginSection'),
    adminControlsSection: document.getElementById('adminControlsSection'),
    adminLoginForm: document.getElementById('adminLoginForm'),
    adminUsername: document.getElementById('adminUsername'),
    adminPassword: document.getElementById('adminPassword'),
    adminLoginBtn: document.getElementById('adminLoginBtn'),
    adminLogoutBtn: document.getElementById('adminLogoutBtn'),
    authBadge: document.getElementById('authBadge'),
    loginError: document.getElementById('loginError'),
    loginErrorText: document.getElementById('loginErrorText'),
    trainModelBtn: document.getElementById('trainModelBtn'),
    reloadDataBtn: document.getElementById('reloadDataBtn'),
    updateDataBtn: document.getElementById('updateDataBtn'),
    checkStatusBtn: document.getElementById('checkStatusBtn'),
    trainAdvancedBtn: document.getElementById('trainAdvancedBtn'),
    trainCVBtn: document.getElementById('trainCVBtn'),
    trainBoostingBtn: document.getElementById('trainBoostingBtn'),
    trainEnsembleBtn: document.getElementById('trainEnsembleBtn'),
    gridSearchBtn: document.getElementById('gridSearchBtn'),
    compareModelsBtn: document.getElementById('compareModelsBtn'),
    adminOutput: document.getElementById('adminOutput'),
    adminOutputText: document.getElementById('adminOutputText'),

    // External API elements
    fetchUpcomingBtn: document.getElementById('fetchUpcomingBtn'),
    upcomingCard: document.getElementById('upcomingCard'),
    upcomingResults: document.getElementById('upcomingResults'),
    competitionName: document.getElementById('competitionName'),
    matchdayInfo: document.getElementById('matchdayInfo'),
    matchesList: document.getElementById('matchesList'),
    standingsSection: document.getElementById('standingsSection'),
    standingsBody: document.getElementById('standingsBody'),

    // Calendar elements
    calendarDateInput: document.getElementById('calendarDateInput'),
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

    // H2H Insights elements
    h2hInsightsSection: document.getElementById('h2hInsightsSection'),
    h2hRecordSummary: document.getElementById('h2hRecordSummary'),
    h2hTotalMeetings: document.getElementById('h2hTotalMeetings'),
    h2hHomeWins: document.getElementById('h2hHomeWins'),
    h2hHomeLabel: document.getElementById('h2hHomeLabel'),
    h2hDraws: document.getElementById('h2hDraws'),
    h2hAwayWins: document.getElementById('h2hAwayWins'),
    h2hAwayLabel: document.getElementById('h2hAwayLabel'),
    h2hTimelineList: document.getElementById('h2hTimelineList'),
    h2hAvgGoals: document.getElementById('h2hAvgGoals'),
    h2hBtts: document.getElementById('h2hBtts'),
    h2hHomeGoalsLabel: document.getElementById('h2hHomeGoalsLabel'),
    h2hHomeAvgGoals: document.getElementById('h2hHomeAvgGoals'),
    h2hAwayGoalsLabel: document.getElementById('h2hAwayGoalsLabel'),
    h2hAwayAvgGoals: document.getElementById('h2hAwayAvgGoals'),
    h2hMostCommonScore: document.getElementById('h2hMostCommonScore'),
    h2hMostCommonOutcome: document.getElementById('h2hMostCommonOutcome'),
    h2hHomeVenueTeam: document.getElementById('h2hHomeVenueTeam'),
    h2hHomeVenuePct: document.getElementById('h2hHomeVenuePct'),
    h2hAwayVenueTeam: document.getElementById('h2hAwayVenueTeam'),
    h2hAwayVenuePct: document.getElementById('h2hAwayVenuePct'),
    h2hVenueNote: document.getElementById('h2hVenueNote'),

    // Trending Insights elements
    trendingCard: document.getElementById('trendingCard'),
    refreshTrendingBtn: document.getElementById('refreshTrendingBtn'),
    trendingLoading: document.getElementById('trendingLoading'),
    trendingGrid: document.getElementById('trendingGrid'),
    trendingMeta: document.getElementById('trendingMeta'),
    trendingUpdatedAt: document.getElementById('trendingUpdatedAt'),
    trendingTeamsCount: document.getElementById('trendingTeamsCount'),
    hotTeamsList: document.getElementById('hotTeamsList'),
    coldTeamsList: document.getElementById('coldTeamsList'),
    topScorersList: document.getElementById('topScorersList'),
    defensiveWallsList: document.getElementById('defensiveWallsList'),
    upsetAlertsList: document.getElementById('upsetAlertsList'),
    goalFestList: document.getElementById('goalFestList'),

    // Toast container
    toastContainer: document.getElementById('toastContainer')
};

// ═══════════════════════════════════════════════════════════════════════════
// Utility Functions
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Escape HTML special characters to prevent XSS and attribute parsing issues
 */
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

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
 * Set button loading state
 */
function setButtonLoading(button, isLoading) {
    if (!button) return;

    const btnText = button.querySelector('.btn-text');
    const btnLoader = button.querySelector('.btn-loader') || button.querySelector('.spinner-border');

    if (isLoading) {
        if (btnText) btnText.style.display = 'none';
        if (btnLoader) btnLoader.style.display = 'inline-block';
        button.disabled = true;
    } else {
        if (btnText) btnText.style.display = 'inline';
        if (btnLoader) btnLoader.style.display = 'none';
        button.disabled = false;
    }
}

/**
 * Format percentage
 */
function formatPercent(value) {
    return `${Math.round(Number(value) * 100)}%`;
}

// ═══════════════════════════════════════════════════════════════════════════
// Admin Authentication
// ═══════════════════════════════════════════════════════════════════════════

// Admin authentication state
let adminAuth = {
    isAuthenticated: false,
    credentials: null
};

/**
 * Get admin credentials from session storage
 */
function getStoredAdminCredentials() {
    try {
        const stored = sessionStorage.getItem('adminCredentials');
        if (stored) {
            return JSON.parse(stored);
        }
    } catch (e) {
        console.error('Failed to parse stored credentials');
    }
    return null;
}

/**
 * Store admin credentials in session storage
 */
function storeAdminCredentials(username, password) {
    const credentials = btoa(`${username}:${password}`);
    sessionStorage.setItem('adminCredentials', JSON.stringify({ credentials }));
    adminAuth.credentials = credentials;
    adminAuth.isAuthenticated = true;
}

/**
 * Clear admin credentials
 */
function clearAdminCredentials() {
    sessionStorage.removeItem('adminCredentials');
    adminAuth.credentials = null;
    adminAuth.isAuthenticated = false;
}

/**
 * Make an authenticated API request (for admin endpoints)
 */
async function adminApiRequest(url, options = {}) {
    if (!adminAuth.credentials) {
        throw { status: 401, error: 'Not authenticated' };
    }

    return apiRequest(url, {
        ...options,
        headers: {
            ...options.headers,
            'Authorization': `Basic ${adminAuth.credentials}`
        }
    });
}

/**
 * Verify admin credentials with the server
 */
async function verifyAdminCredentials(username, password) {
    const credentials = btoa(`${username}:${password}`);

    try {
        const response = await fetch(ENDPOINTS.adminVerify, {
            method: 'GET',
            headers: {
                'Authorization': `Basic ${credentials}`,
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            storeAdminCredentials(username, password);
            return { success: true };
        } else {
            return { success: false, error: 'Invalid credentials' };
        }
    } catch (error) {
        return { success: false, error: 'Network error' };
    }
}

/**
 * Handle admin login form submission
 */
async function handleAdminLogin(event) {
    event.preventDefault();

    const username = elements.adminUsername.value.trim();
    const password = elements.adminPassword.value;

    if (!username || !password) {
        showLoginError('Please enter username and password');
        return;
    }

    setButtonLoading(elements.adminLoginBtn, true);
    hideLoginError();

    const result = await verifyAdminCredentials(username, password);

    setButtonLoading(elements.adminLoginBtn, false);

    if (result.success) {
        showAdminControls();
        showToast('Admin authenticated successfully', 'success');
        // Clear the form
        elements.adminLoginForm.reset();
    } else {
        showLoginError(result.error || 'Authentication failed');
    }
}

/**
 * Handle admin logout
 */
function handleAdminLogout() {
    clearAdminCredentials();
    hideAdminControls();
    showToast('Logged out successfully', 'info');
}

/**
 * Handle Escape key press to close modal
 */
function handleModalKeyDown(event) {
    if (event.key === 'Escape') {
        hideAdminModal();
    }
}

// Bootstrap modal instance
let adminModalInstance = null;

/**
 * Show admin modal using Bootstrap
 */
function showAdminModal() {
    if (elements.adminModalOverlay) {
        if (!adminModalInstance) {
            adminModalInstance = new bootstrap.Modal(elements.adminModalOverlay);
        }
        adminModalInstance.show();
    }
}

/**
 * Hide admin modal using Bootstrap
 */
function hideAdminModal() {
    if (adminModalInstance) {
        adminModalInstance.hide();
    }
}

/**
 * Show admin controls (after successful login)
 */
function showAdminControls() {
    if (elements.adminLoginSection) {
        elements.adminLoginSection.style.display = 'none';
    }
    if (elements.adminControlsSection) {
        elements.adminControlsSection.style.display = 'flex';
    }
    if (elements.authBadge) {
        elements.authBadge.textContent = '🔓 Authenticated';
        elements.authBadge.classList.remove('locked');
        elements.authBadge.classList.add('unlocked');
    }
}

/**
 * Hide admin controls (show login overlay)
 */
function hideAdminControls() {
    if (elements.adminLoginSection) {
        elements.adminLoginSection.style.display = 'flex';
    }
    if (elements.adminControlsSection) {
        elements.adminControlsSection.style.display = 'none';
    }
    if (elements.authBadge) {
        elements.authBadge.textContent = '🔒 Admin Only';
        elements.authBadge.classList.remove('unlocked');
        elements.authBadge.classList.add('locked');
    }
}

/**
 * Show login error message
 */
function showLoginError(message) {
    if (elements.loginErrorText) elements.loginErrorText.textContent = message;
    if (elements.loginError) elements.loginError.style.display = 'flex';
}

/**
 * Hide login error message
 */
function hideLoginError() {
    if (elements.loginError) {
        elements.loginError.style.display = 'none';
    }
}

/**
 * Initialize admin authentication
 * Check if there are stored credentials and verify them
 */
async function initAdminAuth() {
    const stored = getStoredAdminCredentials();
    if (stored && stored.credentials) {
        adminAuth.credentials = stored.credentials;

        // Verify the stored credentials are still valid
        try {
            const response = await fetch(ENDPOINTS.adminVerify, {
                method: 'GET',
                headers: {
                    'Authorization': `Basic ${stored.credentials}`,
                    'Content-Type': 'application/json'
                }
            });

            if (response.ok) {
                adminAuth.isAuthenticated = true;
                showAdminControls();
            } else {
                clearAdminCredentials();
            }
        } catch (error) {
            clearAdminCredentials();
        }
    }
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
        updateModelStatus(data.modelLoaded, data.lastUpdated);
        updateHeaderStats(data);
        return data;
    } catch (error) {
        console.error('Failed to check model status:', error);
        updateModelStatus(false, null);
        return { modelLoaded: false };
    }
}

/**
 * Update header statistics from model status data
 */
function updateHeaderStats(data) {
    // Update total matches
    const totalMatchesEl = document.getElementById('totalMatches');
    if (totalMatchesEl && data.totalMatches !== undefined) {
        totalMatchesEl.textContent = formatNumber(data.totalMatches);
    }

    // Update total teams
    const totalTeamsEl = document.getElementById('totalTeams');
    if (totalTeamsEl && data.totalTeams !== undefined) {
        totalTeamsEl.textContent = data.totalTeams;
    }

    // Update total features
    const totalFeaturesEl = document.getElementById('totalFeatures');
    if (totalFeaturesEl && data.totalFeatures !== undefined) {
        totalFeaturesEl.textContent = data.totalFeatures;
    }
}

/**
 * Format number with commas for thousands
 */
function formatNumber(num) {
    if (num === null || num === undefined) return '--';
    return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

/**
 * Update model status UI
 */
function updateModelStatus(isLoaded, lastUpdated) {
    elements.statusIndicator.className = 'status-indicator ' + (isLoaded ? 'ready' : 'not-ready');
    elements.statusText.textContent = isLoaded ? 'Model Ready' : 'Model Not Loaded';
    if (lastUpdated) {
        // Format timestamp based on user's locale and timezone
        const date = new Date(lastUpdated);
        const formattedDate = date.toLocaleString(undefined, {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            timeZoneName: 'short'
        });
        elements.lastUpdated.textContent = 'Last updated: ' + formattedDate;
    } else {
        elements.lastUpdated.textContent = 'Last updated: --';
    }
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
 * Initialize results card state
 */
function initializeResultsCard() {
    console.log('Initializing results card state...');

    // Ensure results card is properly hidden initially
    if (elements.resultsCard) {
        elements.resultsCard.style.display = 'none';
        elements.resultsCard.style.visibility = 'hidden';
        elements.resultsCard.style.opacity = '0';
        elements.resultsCard.classList.remove('fade-in');
    }

    // Ensure placeholder is visible initially
    const resultsPlaceholder = document.getElementById('resultsPlaceholder');
    if (resultsPlaceholder) {
        resultsPlaceholder.style.display = 'block';
        resultsPlaceholder.style.visibility = 'visible';
    }

    console.log('Results card initialization complete');
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
        const data = await adminApiRequest(ENDPOINTS.trainModel, { method: 'POST' });
        showAdminOutput(data.report || 'Model trained successfully!');
        showToast('Model trained successfully!', 'success');
        checkModelStatus();
    } catch (error) {
        if (error.status === 401) {
            handleAdminLogout();
            showToast('Session expired. Please login again.', 'error');
        } else {
            showAdminOutput(`Error: ${error.error || 'Training failed'}`);
            showToast('Model training failed', 'error');
        }
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
        const data = await adminApiRequest(ENDPOINTS.reloadData, { method: 'POST' });
        showAdminOutput(data.status || 'Data reloaded successfully!');
        showToast('Data reloaded!', 'success');
        loadTeams(); // Refresh team list
    } catch (error) {
        if (error.status === 401) {
            handleAdminLogout();
            showToast('Session expired. Please login again.', 'error');
        } else {
            showAdminOutput(`Error: ${error.error || 'Data reload failed'}`);
            showToast('Data reload failed', 'error');
        }
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
        const data = await adminApiRequest(ENDPOINTS.updateData, { method: 'POST' });
        showAdminOutput(data.result || 'Data updated successfully!');
        showToast('Data updated!', 'success');
        loadTeams(); // Refresh team list
        checkModelStatus(); // Refresh model status
    } catch (error) {
        if (error.status === 401) {
            handleAdminLogout();
            showToast('Session expired. Please login again.', 'error');
        } else {
            showAdminOutput(`Error: ${error.error || 'Data update failed'}`);
            showToast('Data update failed', 'error');
        }
    } finally {
        setButtonLoading(elements.updateDataBtn, false);
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Advanced Training Functions
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Advanced training with cross-validation, grid search, and ensemble
 */
async function trainAdvanced() {
    setButtonLoading(elements.trainAdvancedBtn, true);
    showAdminOutput('⚡ Starting ADVANCED training...\nThis performs grid search + cross-validation + ensemble.\nMay take 3-5 minutes.');

    try {
        const data = await adminApiRequest(ENDPOINTS.trainAdvanced, { method: 'POST' });
        showAdminOutput(data.report || 'Advanced training completed!');
        showToast('Advanced training completed!', 'success');
        checkModelStatus();
    } catch (error) {
        if (error.status === 401) {
            handleAdminLogout();
            showToast('Session expired. Please login again.', 'error');
        } else {
            showAdminOutput(`Error: ${error.error || 'Advanced training failed'}`);
            showToast('Advanced training failed', 'error');
        }
    } finally {
        setButtonLoading(elements.trainAdvancedBtn, false);
    }
}

/**
 * Train with k-fold cross-validation
 */
async function trainWithCrossValidation() {
    setButtonLoading(elements.trainCVBtn, true);
    showAdminOutput('📈 Starting Cross-Validation training (10-fold)...\nThis provides more reliable accuracy estimates.');

    try {
        const data = await adminApiRequest(ENDPOINTS.trainCV, { method: 'POST' });
        showAdminOutput(data.report || 'Cross-validation training completed!');
        showToast('Cross-validation training completed!', 'success');
        checkModelStatus();
    } catch (error) {
        if (error.status === 401) {
            handleAdminLogout();
            showToast('Session expired. Please login again.', 'error');
        } else {
            showAdminOutput(`Error: ${error.error || 'Cross-validation training failed'}`);
            showToast('Cross-validation training failed', 'error');
        }
    } finally {
        setButtonLoading(elements.trainCVBtn, false);
    }
}

/**
 * Train using Gradient Boosting (AdaBoost)
 */
async function trainGradientBoosting() {
    setButtonLoading(elements.trainBoostingBtn, true);
    showAdminOutput('📊 Starting Gradient Boosting training...\nAdaBoost often performs better than Random Forest for tabular data.');

    try {
        const data = await adminApiRequest(ENDPOINTS.trainBoosting, { method: 'POST' });
        showAdminOutput(data.report || 'Gradient Boosting training completed!');
        showToast('Gradient Boosting training completed!', 'success');
        checkModelStatus();
    } catch (error) {
        if (error.status === 401) {
            handleAdminLogout();
            showToast('Session expired. Please login again.', 'error');
        } else {
            showAdminOutput(`Error: ${error.error || 'Gradient Boosting training failed'}`);
            showToast('Gradient Boosting training failed', 'error');
        }
    } finally {
        setButtonLoading(elements.trainBoostingBtn, false);
    }
}

/**
 * Train ensemble model combining multiple classifiers
 */
async function trainEnsemble() {
    setButtonLoading(elements.trainEnsembleBtn, true);
    showAdminOutput('🤝 Starting Ensemble training...\nCombining Random Forest + AdaBoost + J48 Decision Tree.');

    try {
        const data = await adminApiRequest(ENDPOINTS.trainEnsemble, { method: 'POST' });
        showAdminOutput(data.report || 'Ensemble training completed!');
        showToast('Ensemble training completed!', 'success');
        checkModelStatus();
    } catch (error) {
        if (error.status === 401) {
            handleAdminLogout();
            showToast('Session expired. Please login again.', 'error');
        } else {
            showAdminOutput(`Error: ${error.error || 'Ensemble training failed'}`);
            showToast('Ensemble training failed', 'error');
        }
    } finally {
        setButtonLoading(elements.trainEnsembleBtn, false);
    }
}

/**
 * Perform hyperparameter grid search
 */
async function performGridSearch() {
    setButtonLoading(elements.gridSearchBtn, true);
    showAdminOutput('🔍 Starting Grid Search...\nTesting different hyperparameter combinations for Random Forest and AdaBoost.\nThis may take 2-4 minutes.');

    try {
        const data = await adminApiRequest(ENDPOINTS.gridSearch, { method: 'POST' });
        showAdminOutput(data.report || 'Grid search completed!');
        showToast('Grid search completed!', 'success');
    } catch (error) {
        if (error.status === 401) {
            handleAdminLogout();
            showToast('Session expired. Please login again.', 'error');
        } else {
            showAdminOutput(`Error: ${error.error || 'Grid search failed'}`);
            showToast('Grid search failed', 'error');
        }
    } finally {
        setButtonLoading(elements.gridSearchBtn, false);
    }
}

/**
 * Compare all available models using cross-validation
 */
async function compareModels() {
    setButtonLoading(elements.compareModelsBtn, true);
    showAdminOutput('📋 Comparing models...\nEvaluating Random Forest, AdaBoost, J48, and Ensemble using 10-fold cross-validation.');

    try {
        const data = await adminApiRequest(ENDPOINTS.compareModels, { method: 'GET' });
        showAdminOutput(data.report || 'Model comparison completed!');
        showToast('Model comparison completed!', 'success');
    } catch (error) {
        if (error.status === 401) {
            handleAdminLogout();
            showToast('Session expired. Please login again.', 'error');
        } else {
            showAdminOutput(`Error: ${error.error || 'Model comparison failed'}`);
            showToast('Model comparison failed', 'error');
        }
    } finally {
        setButtonLoading(elements.compareModelsBtn, false);
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// UI Update Functions
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Display prediction results
 */
function displayResults(data) {
    console.log('Displaying prediction results:', data);

    // Update match header
    if (elements.resultHomeTeam) elements.resultHomeTeam.textContent = data.homeTeam;
    if (elements.resultAwayTeam) elements.resultAwayTeam.textContent = data.awayTeam;
    if (elements.resultMatchup) elements.resultMatchup.textContent = `${data.homeTeam} vs ${data.awayTeam}`;

    // Update prediction
    if (elements.predictionValue) {
        elements.predictionValue.textContent = data.prediction;
        // Remove any existing prediction classes and add the appropriate color
        elements.predictionValue.className = 'text-primary';
        if (data.predictionCode === 'H') elements.predictionValue.className = 'text-primary';
        else if (data.predictionCode === 'D') elements.predictionValue.className = 'text-warning';
        else if (data.predictionCode === 'A') elements.predictionValue.className = 'text-success';
    }

    // Update confidence with Bootstrap badge styling
    if (elements.confidenceBadge) {
        elements.confidenceBadge.textContent = data.confidence;
        elements.confidenceBadge.className = 'badge ';
        if (data.confidence === 'HIGH') elements.confidenceBadge.className += 'bg-success';
        else if (data.confidence === 'MEDIUM') elements.confidenceBadge.className += 'bg-warning text-dark';
        else elements.confidenceBadge.className += 'bg-secondary';
    }

    // Update probabilities
    const probHome = Math.round(Number(data.probHomeWin) * 100);
    const probDraw = Math.round(Number(data.probDraw) * 100);
    const probAway = Math.round(Number(data.probAwayWin) * 100);

    if (elements.probHomeValue) elements.probHomeValue.textContent = `${probHome}%`;
    if (elements.probDrawValue) elements.probDrawValue.textContent = `${probDraw}%`;
    if (elements.probAwayValue) elements.probAwayValue.textContent = `${probAway}%`;

    // Update Bootstrap progress bars
    if (elements.probHomeFill) {
        elements.probHomeFill.style.width = `${probHome}%`;
        elements.probHomeFill.setAttribute('aria-valuenow', probHome);
    }
    if (elements.probDrawFill) {
        elements.probDrawFill.style.width = `${probDraw}%`;
        elements.probDrawFill.setAttribute('aria-valuenow', probDraw);
    }
    if (elements.probAwayFill) {
        elements.probAwayFill.style.width = `${probAway}%`;
        elements.probAwayFill.setAttribute('aria-valuenow', probAway);
    }

    // Update features (with null checks)
    if (data.features) {
        if (elements.homeFormPoints) elements.homeFormPoints.textContent = data.features.homeFormPoints != null ? Number(data.features.homeFormPoints).toFixed(2) : '-';
        if (elements.awayFormPoints) elements.awayFormPoints.textContent = data.features.awayFormPoints != null ? Number(data.features.awayFormPoints).toFixed(2) : '-';
        if (elements.homeGoalsAvg) elements.homeGoalsAvg.textContent = data.features.homeGoalsScoredAvg != null ? Number(data.features.homeGoalsScoredAvg).toFixed(2) : '-';
        if (elements.awayGoalsAvg) elements.awayGoalsAvg.textContent = data.features.awayGoalsScoredAvg != null ? Number(data.features.awayGoalsScoredAvg).toFixed(2) : '-';
        if (elements.h2hHomeWin) elements.h2hHomeWin.textContent = formatPercent(data.features.h2hHomeWinRate || 0);
        if (elements.h2hDraw) elements.h2hDraw.textContent = formatPercent(data.features.h2hDrawRate || 0);
        if (elements.h2hAwayWin) elements.h2hAwayWin.textContent = formatPercent(data.features.h2hAwayWinRate || 0);
    }

    // Update H2H Insights
    if (data.h2hInsights && data.h2hInsights.totalMeetings > 0) {
        console.log('H2H insights data available, displaying...');
        displayH2HInsights(data.h2hInsights, data.homeTeam, data.awayTeam);
        if (elements.h2hInsightsSection) {
            elements.h2hInsightsSection.style.display = 'block';
        }
    } else {
        console.log('No H2H insights data available');
        if (elements.h2hInsightsSection) {
            elements.h2hInsightsSection.style.display = 'none';
        }
    }

    // Show results card with Bootstrap classes, hide placeholder
    if (elements.resultsCard) {
        // Remove any existing animation classes first
        elements.resultsCard.classList.remove('fade-in');

        // Force display and ensure visibility
        elements.resultsCard.style.display = 'block';
        elements.resultsCard.style.visibility = 'visible';
        elements.resultsCard.style.opacity = '1';

        // Add smooth scroll with Bootstrap utility with a small delay
        setTimeout(() => {
            elements.resultsCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }, 100);

        // Add fade-in animation after a small delay
        setTimeout(() => {
            elements.resultsCard.classList.add('fade-in');
        }, 50);
    }

    // Hide placeholder with force
    const resultsPlaceholder = document.getElementById('resultsPlaceholder');
    if (resultsPlaceholder) {
        resultsPlaceholder.style.display = 'none';
        resultsPlaceholder.style.visibility = 'hidden';
    }
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
 * Display enhanced H2H insights
 */
function displayH2HInsights(h2h, homeTeam, awayTeam) {
    console.log('Displaying H2H insights:', h2h);

    // Historical Record
    if (elements.h2hRecordSummary) {
        elements.h2hRecordSummary.textContent = h2h.historicalRecord || `${homeTeam} vs ${awayTeam} - No previous meetings`;
    }
    if (elements.h2hTotalMeetings) elements.h2hTotalMeetings.textContent = h2h.totalMeetings || 0;
    if (elements.h2hHomeWins) elements.h2hHomeWins.textContent = h2h.homeTeamWins || 0;
    if (elements.h2hHomeLabel) elements.h2hHomeLabel.textContent = `${homeTeam} Wins`;
    if (elements.h2hDraws) elements.h2hDraws.textContent = h2h.draws || 0;
    if (elements.h2hAwayWins) elements.h2hAwayWins.textContent = h2h.awayTeamWins || 0;
    if (elements.h2hAwayLabel) elements.h2hAwayLabel.textContent = `${awayTeam} Wins`;

    // Recent H2H Timeline with better error handling
    if (elements.h2hTimelineList) {
        if (h2h.recentMeetings && Array.isArray(h2h.recentMeetings) && h2h.recentMeetings.length > 0) {
            elements.h2hTimelineList.innerHTML = h2h.recentMeetings.map(match => {
                const scoreClass = getScoreClass(match.winner, match.homeTeamInMatch, match.awayTeamInMatch, homeTeam, awayTeam);
                const formattedDate = formatH2HDate(match.date);
                const homeTeamName = escapeHtml(match.homeTeamInMatch || 'Unknown');
                const awayTeamName = escapeHtml(match.awayTeamInMatch || 'Unknown');
                const score = escapeHtml(match.score || 'N/A');

                return `
                    <div class="h2h-timeline-item">
                        <span class="h2h-timeline-date">${formattedDate}</span>
                        <span class="h2h-timeline-home">${homeTeamName}</span>
                        <span class="h2h-timeline-score ${scoreClass}">${score}</span>
                        <span class="h2h-timeline-away">${awayTeamName}</span>
                    </div>
                `;
            }).join('');
        } else {
            elements.h2hTimelineList.innerHTML = '<div class="h2h-no-data">No recent meetings found</div>';
        }
    }

    // Goal Stats with proper number formatting
    if (elements.h2hAvgGoals) {
        const avgGoals = h2h.avgGoalsPerMatch;
        elements.h2hAvgGoals.textContent = avgGoals != null ? Number(avgGoals).toFixed(1) : '0.0';
    }
    if (elements.h2hBtts) {
        const bttsPercentage = h2h.bttsPercentage;
        elements.h2hBtts.textContent = bttsPercentage != null ? `${Number(bttsPercentage).toFixed(0)}%` : '0%';
    }
    if (elements.h2hHomeGoalsLabel) elements.h2hHomeGoalsLabel.textContent = `${homeTeam} Avg`;
    if (elements.h2hHomeAvgGoals) {
        const homeAvgGoals = h2h.avgHomeTeamGoals;
        elements.h2hHomeAvgGoals.textContent = homeAvgGoals != null ? Number(homeAvgGoals).toFixed(1) : '0.0';
    }
    if (elements.h2hAwayGoalsLabel) elements.h2hAwayGoalsLabel.textContent = `${awayTeam} Avg`;
    if (elements.h2hAwayAvgGoals) {
        const awayAvgGoals = h2h.avgAwayTeamGoals;
        elements.h2hAwayAvgGoals.textContent = awayAvgGoals != null ? Number(awayAvgGoals).toFixed(1) : '0.0';
    }

    // Common Results
    if (elements.h2hMostCommonScore) {
        elements.h2hMostCommonScore.textContent = h2h.mostCommonScore || 'N/A';
    }
    if (elements.h2hMostCommonOutcome) {
        elements.h2hMostCommonOutcome.textContent = formatOutcome(h2h.mostCommonOutcome);
    }

    // Venue Advantage
    if (elements.h2hHomeVenueTeam) elements.h2hHomeVenueTeam.textContent = homeTeam;
    if (elements.h2hHomeVenuePct) {
        const homeVenuePct = h2h.homeTeamHomeWinPct;
        elements.h2hHomeVenuePct.textContent = homeVenuePct != null ? `${Number(homeVenuePct).toFixed(0)}%` : '0%';
    }
    if (elements.h2hAwayVenueTeam) elements.h2hAwayVenueTeam.textContent = awayTeam;
    if (elements.h2hAwayVenuePct) {
        const awayVenuePct = h2h.awayTeamHomeWinPct;
        elements.h2hAwayVenuePct.textContent = awayVenuePct != null ? `${Number(awayVenuePct).toFixed(0)}%` : '0%';
    }
    if (elements.h2hVenueNote) {
        elements.h2hVenueNote.textContent = h2h.venueAdvantageNote || 'Historical venue advantage data not available';
    }

    console.log('H2H insights displayed successfully');
}

/**
 * Get CSS class for H2H score based on winner
 */
function getScoreClass(winner, homeTeamInMatch, awayTeamInMatch, queryHomeTeam, queryAwayTeam) {
    if (winner === 'Draw') return 'draw';
    if (winner === homeTeamInMatch) {
        // Home team in that match won
        return winner.toLowerCase() === queryHomeTeam.toLowerCase() ? 'home-win' : 'away-win';
    } else {
        // Away team in that match won
        return winner.toLowerCase() === queryHomeTeam.toLowerCase() ? 'home-win' : 'away-win';
    }
}

/**
 * Format H2H date for display
 */
function formatH2HDate(dateStr) {
    if (!dateStr) return 'N/A';
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: '2-digit' });
}

/**
 * Format outcome text
 */
function formatOutcome(outcome) {
    switch (outcome) {
        case 'HOME_WIN': return '🏠 Home Win';
        case 'AWAY_WIN': return '✈️ Away Win';
        case 'DRAW': return '🤝 Draw';
        default: return outcome || 'N/A';
    }
}

/**
 * Hide results card
 */
function hideResults() {
    if (elements.resultsCard) {
        elements.resultsCard.style.display = 'none';
        elements.resultsCard.style.visibility = 'hidden';
        elements.resultsCard.style.opacity = '0';
        elements.resultsCard.classList.remove('fade-in');
    }
    // Show placeholder again
    const resultsPlaceholder = document.getElementById('resultsPlaceholder');
    if (resultsPlaceholder) {
        resultsPlaceholder.style.display = 'block';
        resultsPlaceholder.style.visibility = 'visible';
    }
}

/**
 * Display error message
 */
function displayError(message, hint = '') {
    // Use toast notification instead of error card if card doesn't exist
    if (elements.errorCard) {
        if (elements.errorMessage) elements.errorMessage.textContent = message;
        if (elements.errorHint) elements.errorHint.textContent = hint || '';
        elements.errorCard.style.display = 'block';
    } else {
        // Fallback to toast notification
        showToast(message, 'error');
    }
}

/**
 * Hide error card
 */
function hideError() {
    if (elements.errorCard) {
        elements.errorCard.style.display = 'none';
    }
}

/**
 * Show admin output
 */
function showAdminOutput(text) {
    if (elements.adminOutput) {
        elements.adminOutput.style.display = 'block';
    }
    if (elements.adminOutputText) {
        elements.adminOutputText.textContent = text;
    }
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
        parts.push(`<span class="form-ppg">(${Number(teamForm.pointsPerGame).toFixed(2)} ppg)</span>`);
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
 * Format match date for display (uses user's locale and timezone)
 */
function formatMatchDate(dateString) {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleDateString(undefined, {
        weekday: 'short',
        day: 'numeric',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
        timeZoneName: 'short'
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
 * Format date for display (uses user's locale)
 */
function formatDateForDisplay(dateStr) {
    const date = new Date(dateStr);
    return date.toLocaleDateString(undefined, {
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
    elements.calendarDateInput.value = formatDateForApi(date);
}

/**
 * Fetch matches for selected date
 */
async function fetchMatchesByDate() {
    const dateValue = elements.calendarDateInput.value;

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
 * Format match time (uses user's locale and timezone)
 */
function formatMatchTime(dateString) {
    if (!dateString) return 'TBD';
    const date = new Date(dateString);
    return date.toLocaleTimeString(undefined, {
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

/**
 * Fetch football news
 */
async function fetchNews() {
    elements.newsLoading.style.display = 'block';
    elements.newsEmpty.style.display = 'none';
    elements.newsList.querySelectorAll('.news-article').forEach(el => el.remove());

    try {
        const data = await apiRequest(ENDPOINTS.newsFootball);

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

    return date.toLocaleDateString(undefined, {
        day: 'numeric',
        month: 'short'
    });
}

/**
 * Initialize news - fetch football news on load
 */
function initNews() {
    fetchNews();
}

// ═══════════════════════════════════════════════════════════════════════════
// Responsive Utilities
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Initialize responsive features
 */
function initResponsive() {
    // Add body classes based on device/viewport
    updateResponsiveClasses();

    // Listen for resize events with debouncing
    window.addEventListener('resize', RESPONSIVE.onResize(() => {
        updateResponsiveClasses();
    }));

    // Detect virtual keyboard on mobile
    if (RESPONSIVE.isMobile()) {
        detectVirtualKeyboard();
    }
}

/**
 * Update body classes based on current viewport
 */
function updateResponsiveClasses() {
    const breakpoint = RESPONSIVE.getCurrentBreakpoint();
    const isMobile = RESPONSIVE.isMobile();
    const isTouch = RESPONSIVE.isTouchDevice();

    // Remove old breakpoint classes
    document.body.classList.remove('bp-xs', 'bp-sm', 'bp-md', 'bp-lg', 'bp-xl');
    document.body.classList.remove('is-mobile', 'is-desktop', 'is-touch');

    // Add current breakpoint class
    document.body.classList.add(`bp-${breakpoint}`);

    // Add device type classes
    document.body.classList.add(isMobile ? 'is-mobile' : 'is-desktop');
    if (isTouch) {
        document.body.classList.add('is-touch');
    }
}

/**
 * Detect virtual keyboard open/close on mobile
 */
function detectVirtualKeyboard() {
    const initialHeight = window.innerHeight;

    window.addEventListener('resize', () => {
        const currentHeight = window.innerHeight;
        const heightDiff = initialHeight - currentHeight;

        // If height reduced significantly, keyboard is likely open
        if (heightDiff > 150) {
            document.body.classList.add('keyboard-open');
        } else {
            document.body.classList.remove('keyboard-open');
        }
    });
}

/**
 * Load standings data (placeholder for lazy loading)
 */
function loadStandings() {
    // Fetch standings if not already loaded
    const competition = 'PL';
    fetchStandings(competition);
}

/**
 * Load news data (placeholder for lazy loading)
 */
function loadNews() {
    // Fetch football news
    fetchNews();
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
    if (elements.predictionForm) elements.predictionForm.addEventListener('submit', handlePredictionSubmit);
    if (elements.trainModelBtn) elements.trainModelBtn.addEventListener('click', trainModel);
    if (elements.reloadDataBtn) elements.reloadDataBtn.addEventListener('click', reloadData);
    if (elements.updateDataBtn) elements.updateDataBtn.addEventListener('click', updateData);
    if (elements.checkStatusBtn) elements.checkStatusBtn.addEventListener('click', handleCheckStatus);

    // Admin Modal event listeners - Bootstrap handles the modal behavior
    if (elements.adminToggleBtn) {
        elements.adminToggleBtn.addEventListener('click', showAdminModal);
    }
    // Bootstrap modal close is handled automatically via data-bs-dismiss attribute

    // Admin authentication event listeners
    if (elements.adminLoginForm) elements.adminLoginForm.addEventListener('submit', handleAdminLogin);
    if (elements.adminLogoutBtn) elements.adminLogoutBtn.addEventListener('click', handleAdminLogout);

    // Admin tabs are handled by Bootstrap via data-bs-toggle="tab"

    // Advanced training event listeners
    if (elements.trainAdvancedBtn) elements.trainAdvancedBtn.addEventListener('click', trainAdvanced);
    if (elements.trainCVBtn) elements.trainCVBtn.addEventListener('click', trainWithCrossValidation);
    if (elements.trainBoostingBtn) elements.trainBoostingBtn.addEventListener('click', trainGradientBoosting);
    if (elements.trainEnsembleBtn) elements.trainEnsembleBtn.addEventListener('click', trainEnsemble);
    if (elements.gridSearchBtn) elements.gridSearchBtn.addEventListener('click', performGridSearch);
    if (elements.compareModelsBtn) elements.compareModelsBtn.addEventListener('click', compareModels);

    // External API event listeners
    if (elements.fetchUpcomingBtn) elements.fetchUpcomingBtn.addEventListener('click', fetchUpcomingPredictions);

    // Calendar event listeners
    if (elements.fetchByDateBtn) elements.fetchByDateBtn.addEventListener('click', fetchMatchesByDate);
    if (elements.calendarDateInput) elements.calendarDateInput.addEventListener('change', fetchMatchesByDate);
    if (elements.todayBtn) elements.todayBtn.addEventListener('click', () => {
        setDateInput(new Date());
        fetchMatchesByDate();
    });
    if (elements.tomorrowBtn) elements.tomorrowBtn.addEventListener('click', () => {
        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        setDateInput(tomorrow);
        fetchMatchesByDate();
    });
    if (elements.weekendBtn) elements.weekendBtn.addEventListener('click', () => {
        setDateInput(getNextWeekend());
        fetchMatchesByDate();
    });

    // News event listeners
    if (elements.refreshNewsBtn) elements.refreshNewsBtn.addEventListener('click', () => fetchNews());

    // Initialize admin authentication
    await initAdminAuth();

    // Initialize calendar with today's date
    initCalendar();

    // Initialize news
    initNews();

    // Initialize responsive features
    initResponsive();

    // Initialize results card state
    initializeResultsCard();

    // Load initial data
    await Promise.all([
        checkModelStatus(),
        loadTeams()
    ]);

    // Initialize modern UI enhancements
    initModernUI();
}

// ═══════════════════════════════════════════════════════════════════════════
// Modern UI Enhancements (2026)
// ═══════════════════════════════════════════════════════════════════════════

// Initialize enhanced UI features
function initModernUI() {
    initPredictionModeToggle();
    initAdvancedOptions();
    initQuickMatches();
    initTeamFormIndicators();
    updatePredictionCounter();
    initAccessibilityFeatures();
    initPerformanceOptimizations();
}

// Prediction mode toggle (Manual vs Upcoming)
function initPredictionModeToggle() {
    // Use Bootstrap radio buttons (btn-check)
    const modeManual = document.getElementById('modeManual');
    const modeUpcoming = document.getElementById('modeUpcoming');

    if (modeManual) {
        modeManual.addEventListener('change', () => {
            if (modeManual.checked) {
                hideUpcomingMatches();
            }
        });
    }

    if (modeUpcoming) {
        modeUpcoming.addEventListener('change', () => {
            if (modeUpcoming.checked) {
                showUpcomingMatches();
            }
        });
    }

    // Fallback: Also support old mode-btn class elements
    elements.modeBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            // Update active state
            elements.modeBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            const mode = btn.dataset.mode;
            if (mode === 'upcoming') {
                showUpcomingMatches();
            } else {
                hideUpcomingMatches();
            }
        });
    });
}

// Advanced options toggle
function initAdvancedOptions() {
    if (elements.toggleAdvanced) {
        elements.toggleAdvanced.addEventListener('click', () => {
            const isExpanded = elements.toggleAdvanced.getAttribute('aria-expanded') === 'true';
            elements.toggleAdvanced.setAttribute('aria-expanded', !isExpanded);

            if (elements.advancedOptions) {
                elements.advancedOptions.style.display = isExpanded ? 'none' : 'block';
            }

            // Rotate icon
            const icon = elements.toggleAdvanced.querySelector('.toggle-icon');
            if (icon) {
                icon.style.transform = isExpanded ? 'rotate(0deg)' : 'rotate(180deg)';
            }
        });
    }
}

// Quick match suggestions
async function initQuickMatches() {
    if (!elements.quickMatchesGrid) return;

    // Show loading state
    elements.quickMatchesGrid.innerHTML = '<p class="loading-text">Loading upcoming matches...</p>';

    try {
        const upcomingMatches = await fetchUpcomingMatches();
        if (upcomingMatches && upcomingMatches.length > 0) {
            renderQuickMatches(upcomingMatches.slice(0, 4)); // Show top 4
        } else {
            // No matches, use fallback
            renderQuickMatches(getFallbackMatches().slice(0, 4));
        }
    } catch (error) {
        console.warn('Could not load quick matches:', error);
        // Use fallback matches
        renderQuickMatches(getFallbackMatches().slice(0, 4));
    }
}

function renderQuickMatches(matches) {
    if (!elements.quickMatchesGrid) return;

    if (!matches || matches.length === 0) {
        elements.quickMatchesGrid.innerHTML = '<p class="no-matches">No upcoming matches available</p>';
        return;
    }

    elements.quickMatchesGrid.innerHTML = matches.map((match, index) => {
        console.log(`Rendering quick match ${index}:`, { homeTeam: match.homeTeam, awayTeam: match.awayTeam });

        const predictionClass = match.predictionCode ? getPredictionClass(match.predictionCode) : '';
        const homeFlag = match.homeTeamCrest ? `<img src="${match.homeTeamCrest}" alt="${escapeHtml(match.homeTeam)}" class="quick-match-crest">` : '';
        const awayFlag = match.awayTeamCrest ? `<img src="${match.awayTeamCrest}" alt="${escapeHtml(match.awayTeam)}" class="quick-match-crest">` : '';

        // Build team form HTML (last 5 matches)
        const homeFormHtml = buildQuickMatchFormHtml(match.homeTeamForm);
        const awayFormHtml = buildQuickMatchFormHtml(match.awayTeamForm);

        // Escape team names for safe HTML attribute usage
        const homeTeamEscaped = escapeHtml(match.homeTeam);
        const awayTeamEscaped = escapeHtml(match.awayTeam);

        return `
        <div class="quick-match-item" data-index="${index}" data-home="${homeTeamEscaped}" data-away="${awayTeamEscaped}">
            <div class="quick-match-teams">
                <div class="quick-match-team-wrapper">
                    ${homeFlag}
                    <div class="quick-match-team-info">
                        <span class="quick-match-team">${homeTeamEscaped}</span>
                        ${homeFormHtml}
                    </div>
                </div>
                <span class="quick-match-vs">vs</span>
                <div class="quick-match-team-wrapper away">
                    <div class="quick-match-team-info">
                        <span class="quick-match-team">${awayTeamEscaped}</span>
                        ${awayFormHtml}
                    </div>
                    ${awayFlag}
                </div>
            </div>
            <div class="quick-match-info">
                <span class="match-date">${formatDate(match.date)}</span>
                ${match.prediction ? `<span class="quick-match-prediction ${predictionClass}">${match.prediction}</span>` : ''}
                <span class="match-confidence ${(match.confidence || '').toLowerCase()}">${match.confidence || 'Medium'}</span>
            </div>
        </div>
    `;
    }).join('');

    // Add click event listeners to quick match items
    const quickMatchItems = elements.quickMatchesGrid.querySelectorAll('.quick-match-item');
    quickMatchItems.forEach(item => {
        item.addEventListener('click', (e) => {
            e.stopPropagation(); // Prevent event bubbling

            // Ensure we get the quick-match-item element even if a child was clicked
            const matchItem = e.currentTarget;
            const homeTeam = matchItem.getAttribute('data-home');
            const awayTeam = matchItem.getAttribute('data-away');

            console.log('Quick match clicked - raw attributes:', {
                'data-home': matchItem.getAttribute('data-home'),
                'data-away': matchItem.getAttribute('data-away'),
                homeTeam,
                awayTeam
            });

            if (homeTeam && awayTeam) {
                selectQuickMatch(homeTeam, awayTeam);
            } else {
                console.error('Missing team data in quick match item:', matchItem);
            }
        });
    });
}

/**
 * Build form HTML for quick match cards (detailed version)
 * Shows position, points, ppg, W/D/L record, and form indicator
 */
function buildQuickMatchFormHtml(teamForm) {
    if (!teamForm) return '';

    let html = '<div class="quick-form-stats">';

    // First row: Position, Points, PPG
    html += '<span class="quick-form-summary">';

    if (teamForm.position) {
        html += `<span class="quick-form-position">#${teamForm.position}</span>`;
    }

    if (teamForm.points !== undefined) {
        html += `<span class="quick-form-points">${teamForm.points} pts</span>`;
    }

    if (teamForm.pointsPerGame !== undefined || teamForm.ppg !== undefined) {
        const ppg = teamForm.pointsPerGame || teamForm.ppg;
        html += `<span class="quick-form-ppg">(${Number(ppg).toFixed(2)} ppg)</span>`;
    }

    // W/D/L Record
    if (teamForm.wins !== undefined || teamForm.draws !== undefined || teamForm.losses !== undefined) {
        const wins = teamForm.wins || 0;
        const draws = teamForm.draws || 0;
        const losses = teamForm.losses || 0;
        html += `<span class="quick-form-record"><span class="record-w">${wins}W</span> <span class="record-d">${draws}D</span> <span class="record-l">${losses}L</span></span>`;
    }

    html += '</span>';

    // Form indicator (dots or W/D/L badges)
    let formString = teamForm.recentForm || teamForm.form;
    if (formString) {
        html += createQuickFormIndicator(formString);
    }

    html += '</div>';

    return html;
}

/**
 * Create form indicator with colored dots or badges
 */
function createQuickFormIndicator(formString) {
    if (!formString) return '';

    // Handle both comma-separated and continuous formats
    let results;
    if (formString.includes(',')) {
        results = formString.split(',').map(r => r.trim());
    } else {
        results = formString.split('');
    }

    // Filter to only W, D, L and take last 5
    results = results.filter(r => ['W', 'D', 'L'].includes(r.toUpperCase())).slice(-5);

    if (results.length === 0) return '';

    // Calculate form quality for indicator class
    const wins = results.filter(r => r.toUpperCase() === 'W').length;
    let formClass = 'poor';
    if (wins >= 4) formClass = 'excellent';
    else if (wins >= 3) formClass = 'good';
    else if (wins >= 2) formClass = 'average';
    else if (wins >= 1) formClass = 'below-average';

    const badges = results.map(r => {
        const result = r.toUpperCase();
        return `<span class="quick-form-char ${result}">${result}</span>`;
    }).join('');

    return `<span class="quick-form-indicator ${formClass}">${badges}</span>`;
}

function selectQuickMatch(homeTeam, awayTeam) {
    console.log('selectQuickMatch called with:', { homeTeam, awayTeam });

    // Set home team
    if (elements.homeTeamSelect) {
        // Find the matching option (case-insensitive partial match)
        const homeOption = findTeamOption(elements.homeTeamSelect, homeTeam);
        console.log('Home team option found:', homeOption?.value);
        if (homeOption) {
            elements.homeTeamSelect.value = homeOption.value;
        } else {
            console.warn(`Home team "${homeTeam}" not found in dropdown`);
            elements.homeTeamSelect.value = ''; // Clear selection if not found
            showToast(`Team "${homeTeam}" not found in database`, 'warning');
        }
    }

    // Set away team
    if (elements.awayTeamSelect) {
        const awayOption = findTeamOption(elements.awayTeamSelect, awayTeam);
        console.log('Away team option found:', awayOption?.value);
        if (awayOption) {
            elements.awayTeamSelect.value = awayOption.value;
        } else {
            console.warn(`Away team "${awayTeam}" not found in dropdown`);
            elements.awayTeamSelect.value = ''; // Clear selection if not found
            showToast(`Team "${awayTeam}" not found in database`, 'warning');
        }
    }

    // Update form indicators
    updateTeamFormIndicator(homeTeam, 'home');
    updateTeamFormIndicator(awayTeam, 'away');

    // Scroll to prediction form
    elements.predictionForm?.scrollIntoView({ behavior: 'smooth', block: 'center' });

    // Show success feedback only if both teams were found
    if (elements.homeTeamSelect?.value && elements.awayTeamSelect?.value) {
        showToast(`Selected: ${homeTeam} vs ${awayTeam}`, 'success');

        // Automatically trigger prediction after a short delay
        setTimeout(() => {
            makePrediction(elements.homeTeamSelect.value, elements.awayTeamSelect.value);
        }, 500);
    }
}

/**
 * Find a team option in a select element by name (case-insensitive partial match)
 */
function findTeamOption(selectElement, teamName) {
    if (!selectElement || !teamName) return null;

    const normalizedName = teamName.toLowerCase().trim();
    const options = Array.from(selectElement.options).filter(opt => opt.value); // Exclude empty options

    console.log(`Finding team option for: "${normalizedName}"`);
    console.log(`Available options (${options.length}):`, options.slice(0, 10).map(o => o.value)); // Log first 10

    // Try exact match first (highest priority)
    let match = options.find(opt => opt.value.toLowerCase().trim() === normalizedName);
    if (match) {
        console.log(`Exact match found: "${match.value}"`);
        return match;
    }

    // Try matching with common abbreviations/expansions
    const abbreviations = {
        'man united': ['manchester united', 'man utd', 'manchester utd'],
        'manchester united': ['man united', 'man utd'],
        'man city': ['manchester city'],
        'manchester city': ['man city'],
        'spurs': ['tottenham', 'tottenham hotspur'],
        'tottenham': ['spurs', 'tottenham hotspur'],
        'wolves': ['wolverhampton', 'wolverhampton wanderers'],
        'wolverhampton': ['wolves'],
        'leeds united': ['leeds'],
        'leeds': ['leeds united'],
        'leicester city': ['leicester'],
        'leicester': ['leicester city'],
        'west ham united': ['west ham'],
        'west ham': ['west ham united'],
        'newcastle united': ['newcastle'],
        'newcastle': ['newcastle united'],
        'nottingham forest': ["nott'm forest", 'forest'],
        "nott'm forest": ['nottingham forest', 'forest'],
        'brighton and hove albion': ['brighton', 'brighton & hove albion'],
        'brighton': ['brighton and hove albion', 'brighton & hove albion'],
        'crystal palace fc': ['crystal palace'],
        'crystal palace': ['crystal palace fc'],
        'afc bournemouth': ['bournemouth'],
        'bournemouth': ['afc bournemouth'],
        'ipswich town': ['ipswich'],
        'ipswich': ['ipswich town']
    };

    // Check if team name matches any known abbreviation
    const alternates = abbreviations[normalizedName] || [];
    for (const alt of alternates) {
        match = options.find(opt => opt.value.toLowerCase().trim() === alt);
        if (match) {
            console.log(`Abbreviation match found: "${match.value}" for "${normalizedName}"`);
            return match;
        }
    }

    // Try partial match only if one string starts with the other (stricter matching)
    match = options.find(opt => {
        const optValue = opt.value.toLowerCase().trim();
        // Only match if one starts with the other, or they share significant overlap
        return optValue.startsWith(normalizedName) || normalizedName.startsWith(optValue);
    });
    if (match) {
        console.log(`Partial match (starts with) found: "${match.value}"`);
        return match;
    }

    // Last resort: check if option contains the full search term (but not vice versa to avoid false positives)
    match = options.find(opt => {
        const optValue = opt.value.toLowerCase().trim();
        return optValue.includes(normalizedName) && normalizedName.length >= 4;
    });
    if (match) {
        console.log(`Contains match found: "${match.value}"`);
        return match;
    }

    console.log(`No match found for: "${normalizedName}"`);
    return null;
}

function hideQuickMatches() {
    if (elements.quickMatches) {
        elements.quickMatches.style.display = 'none';
    }
}


// Team form indicators
function initTeamFormIndicators() {
    if (elements.homeTeamSelect) {
        elements.homeTeamSelect.addEventListener('change', (e) => {
            updateTeamFormIndicator(e.target.value, 'home');
        });
    }

    if (elements.awayTeamSelect) {
        elements.awayTeamSelect.addEventListener('change', (e) => {
            updateTeamFormIndicator(e.target.value, 'away');
        });
    }
}

async function updateTeamFormIndicator(teamName, position) {
    if (!teamName) return;

    const indicator = position === 'home' ? elements.homeForm : elements.awayForm;
    if (!indicator) return;

    try {
        // Mock team form data - replace with actual API call
        const formData = await getTeamForm(teamName);
        renderTeamForm(indicator, formData);
    } catch (error) {
        console.warn(`Could not load form for ${teamName}:`, error);
        indicator.innerHTML = '';
    }
}

function renderTeamForm(indicator, form) {
    indicator.innerHTML = form.map(result =>
        `<div class="form-dot ${result.toLowerCase()}"></div>`
    ).join('');
}

// Mock function - replace with actual API
async function getTeamForm(teamName) {
    // Return last 5 results: W (win), D (draw), L (loss)
    const mockForms = {
        'Arsenal': ['win', 'win', 'draw', 'win', 'loss'],
        'Chelsea': ['loss', 'win', 'win', 'draw', 'win'],
        'Liverpool': ['win', 'win', 'win', 'draw', 'win'],
        'Manchester City': ['win', 'win', 'win', 'win', 'draw'],
        // Add more teams as needed
    };

    return mockForms[teamName] || ['draw', 'draw', 'draw', 'draw', 'draw'];
}

// Update prediction counter
function updatePredictionCounter() {
    if (elements.totalPredictions) {
        // Get from localStorage or use default
        const storedCount = localStorage.getItem('predictionCount');
        let count = 0;

        if (storedCount !== null && storedCount !== undefined && storedCount !== '') {
            const parsed = parseInt(storedCount, 10);
            count = isNaN(parsed) ? 0 : parsed;
        }

        // Display the count (0 if no predictions made yet)
        if (count === 0) {
            elements.totalPredictions.textContent = '0';
        } else {
            animateCounter(elements.totalPredictions, count);
        }
    }
}

function animateCounter(element, target) {
    // Validate target is a valid number
    if (isNaN(target) || target === null || target === undefined) {
        element.textContent = '0';
        return;
    }

    const start = 0;
    const duration = 2000;
    const startTime = Date.now();

    function update() {
        const elapsed = Date.now() - startTime;
        const progress = Math.min(elapsed / duration, 1);
        const current = Math.floor(start + (target - start) * easeOutCubic(progress));

        element.textContent = isNaN(current) ? '0' : current.toLocaleString();

        if (progress < 1) {
            requestAnimationFrame(update);
        }
    }

    requestAnimationFrame(update);
}

function easeOutCubic(t) {
    return 1 - Math.pow(1 - t, 3);
}

// Accessibility improvements
function initAccessibilityFeatures() {
    // Enhanced focus management
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Tab') {
            document.body.classList.add('keyboard-navigation');
        }
    });

    document.addEventListener('mousedown', () => {
        document.body.classList.remove('keyboard-navigation');
    });

    // Screen reader announcements
    const announcements = document.createElement('div');
    announcements.setAttribute('aria-live', 'polite');
    announcements.setAttribute('aria-atomic', 'true');
    announcements.className = 'sr-only';
    announcements.id = 'announcements';
    document.body.appendChild(announcements);
}

function announceToScreenReader(message) {
    const announcements = document.getElementById('announcements');
    if (announcements) {
        announcements.textContent = message;
    }
}

// Performance optimizations
function initPerformanceOptimizations() {
    // Lazy load non-critical features
    if ('IntersectionObserver' in window) {
        const lazyElements = document.querySelectorAll('[data-lazy]');
        const observer = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    loadLazyContent(entry.target);
                    observer.unobserve(entry.target);
                }
            });
        });

        lazyElements.forEach(el => observer.observe(el));
    }

    // Debounce resize handlers
    window.addEventListener('resize', RESPONSIVE.onResize(() => {
        updateResponsiveFeatures();
    }));
}

function loadLazyContent(element) {
    const feature = element.dataset.lazy;
    switch (feature) {
        case 'standings':
            loadStandings();
            break;
        case 'news':
            loadNews();
            break;
        default:
            console.warn('Unknown lazy feature:', feature);
    }
}

function updateResponsiveFeatures() {
    const breakpoint = RESPONSIVE.getCurrentBreakpoint();
    document.body.className = `bp-${breakpoint} ${RESPONSIVE.isMobile() ? 'is-mobile' : 'is-desktop'} ${RESPONSIVE.isTouchDevice() ? 'is-touch' : ''}`;
}

// Enhanced toast notifications using Bootstrap Toast
function showToast(message, type = 'info', duration = 4000) {
    const toastContainer = getToastContainer();

    // Map type to Bootstrap color class and icon
    const typeMap = {
        success: { bg: 'bg-success', icon: 'bi-check-circle-fill', textClass: 'text-white' },
        error: { bg: 'bg-danger', icon: 'bi-exclamation-triangle-fill', textClass: 'text-white' },
        warning: { bg: 'bg-warning', icon: 'bi-exclamation-circle-fill', textClass: 'text-dark' },
        info: { bg: 'bg-info', icon: 'bi-info-circle-fill', textClass: 'text-white' }
    };

    const config = typeMap[type] || typeMap.info;

    const toastEl = document.createElement('div');
    toastEl.className = `toast align-items-center ${config.bg} ${config.textClass} border-0`;
    toastEl.setAttribute('role', 'alert');
    toastEl.setAttribute('aria-live', 'assertive');
    toastEl.setAttribute('aria-atomic', 'true');
    toastEl.innerHTML = `
        <div class="d-flex">
            <div class="toast-body d-flex align-items-center gap-2">
                <i class="bi ${config.icon}"></i>
                <span>${message}</span>
            </div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
        </div>
    `;

    toastContainer.appendChild(toastEl);

    // Initialize and show Bootstrap toast
    const bsToast = new bootstrap.Toast(toastEl, { delay: duration });
    bsToast.show();

    // Remove from DOM after hidden
    toastEl.addEventListener('hidden.bs.toast', () => {
        toastEl.remove();
    });

    // Announce to screen readers
    announceToScreenReader(message);
}

function getToastIcon(type) {
    const icons = {
        success: 'bi-check-circle-fill',
        error: 'bi-exclamation-triangle-fill',
        warning: 'bi-exclamation-circle-fill',
        info: 'bi-info-circle-fill'
    };
    return icons[type] || icons.info;
}

function getToastContainer() {
    let container = document.getElementById('toastContainer');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toastContainer';
        container.className = 'toast-container position-fixed bottom-0 end-0 p-3';
        document.body.appendChild(container);
    }
    return container;
}

// Utility functions
function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        weekday: 'short'
    });
}

function formatDateForInput(date) {
    return date.toISOString().split('T')[0];
}

async function fetchUpcomingMatches() {
    try {
        // Use the actual API to fetch upcoming matches with predictions
        const data = await apiRequest(`${ENDPOINTS.externalPredict}?competition=PL&limit=4`);
        console.log('fetchUpcomingMatches API response:', data);

        if (data.predictions && data.predictions.length > 0) {
            const mappedMatches = data.predictions.map(match => {
                console.log('Mapping match:', { homeTeam: match.homeTeam, awayTeam: match.awayTeam });
                return {
                    homeTeam: match.homeTeam,
                    awayTeam: match.awayTeam,
                    homeTeamCrest: match.homeTeamCrest,
                    awayTeamCrest: match.awayTeamCrest,
                    homeTeamForm: match.homeTeamForm,
                    awayTeamForm: match.awayTeamForm,
                    date: match.matchDate,
                    confidence: match.confidence || 'Medium',
                    prediction: match.prediction,
                    predictionCode: match.predictionCode
                };
            });
            console.log('Mapped matches for quick matches:', mappedMatches);
            return mappedMatches;
        }

        // Fallback to mock data if API fails
        return getFallbackMatches();
    } catch (error) {
        console.warn('Could not fetch upcoming matches from API:', error);
        // Return fallback data
        return getFallbackMatches();
    }
}

function getFallbackMatches() {
    // Fallback mock data when API is unavailable
    const today = new Date();
    return [
        {
            homeTeam: 'Arsenal',
            awayTeam: 'Chelsea',
            date: new Date(today.getTime() + 2 * 24 * 60 * 60 * 1000).toISOString(),
            confidence: 'High'
        },
        {
            homeTeam: 'Liverpool',
            awayTeam: 'Manchester City',
            date: new Date(today.getTime() + 4 * 24 * 60 * 60 * 1000).toISOString(),
            confidence: 'Medium'
        },
        {
            homeTeam: 'Man United',
            awayTeam: 'Tottenham',
            date: new Date(today.getTime() + 5 * 24 * 60 * 60 * 1000).toISOString(),
            confidence: 'High'
        },
        {
            homeTeam: 'Newcastle',
            awayTeam: 'Brighton',
            date: new Date(today.getTime() + 6 * 24 * 60 * 60 * 1000).toISOString(),
            confidence: 'Medium'
        }
    ];
}

function showUpcomingMatches() {
    // Hide manual prediction form content but keep card visible
    const predictionForm = document.getElementById('predictionForm');
    const quickMatches = document.getElementById('quickMatches');
    const resultsPlaceholder = document.getElementById('resultsPlaceholder');

    if (predictionForm) predictionForm.style.display = 'none';
    if (quickMatches) quickMatches.style.display = 'none';
    if (resultsPlaceholder) resultsPlaceholder.style.display = 'none';
    if (elements.resultsCard) elements.resultsCard.style.display = 'none';

    // Show upcoming matches section inside the prediction card body
    const upcomingCard = elements.upcomingCard || document.getElementById('upcomingCard');
    if (upcomingCard) {
        upcomingCard.style.display = 'block';

        // Auto-fetch upcoming matches
        const upcomingResults = elements.upcomingResults || document.getElementById('upcomingResults');
        if (upcomingResults && (upcomingResults.style.display === 'none' || !upcomingResults.innerHTML.trim())) {
            fetchUpcomingPredictions();
        }
    } else {
        console.error('upcomingCard element not found');
    }

    console.log('Switched to upcoming matches mode');
}

function hideUpcomingMatches() {
    // Show manual prediction form content
    const predictionForm = document.getElementById('predictionForm');
    const quickMatches = document.getElementById('quickMatches');
    const resultsPlaceholder = document.getElementById('resultsPlaceholder');

    if (predictionForm) predictionForm.style.display = 'block';
    if (quickMatches) quickMatches.style.display = 'block';
    if (resultsPlaceholder && (!elements.resultsCard || elements.resultsCard.style.display === 'none')) {
        resultsPlaceholder.style.display = 'block';
    }

    // Hide upcoming matches section
    const upcomingCard = elements.upcomingCard || document.getElementById('upcomingCard');
    if (upcomingCard) {
        upcomingCard.style.display = 'none';
    }

    console.log('Switched to manual mode');
}

// ═══════════════════════════════════════════════════════════════════════════
// Team Stats Section
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Initialize team stats functionality
 */
function initTeamStats() {
    const teamStatsSelect = document.getElementById('teamStatsSelect');
    const fetchTeamStatsBtn = document.getElementById('fetchTeamStatsBtn');
    const statsTabs = document.querySelectorAll('.stats-tab');

    // Populate team dropdown (reuse existing teams data)
    populateTeamStatsDropdown();

    // Event listeners
    if (fetchTeamStatsBtn) {
        fetchTeamStatsBtn.addEventListener('click', fetchTeamStats);
    }

    // Tab switching
    statsTabs.forEach(tab => {
        tab.addEventListener('click', () => switchStatsTab(tab.dataset.tab));
    });

    // Allow Enter key to fetch stats
    if (teamStatsSelect) {
        teamStatsSelect.addEventListener('change', () => {
            if (teamStatsSelect.value) {
                fetchTeamStats();
            }
        });
    }
}

/**
 * Populate team stats dropdown with available teams
 */
async function populateTeamStatsDropdown() {
    const teamStatsSelect = document.getElementById('teamStatsSelect');
    if (!teamStatsSelect) return;

    try {
        const response = await fetch('/api/teams');
        if (response.ok) {
            const data = await response.json();
            const teams = data.teams || data;

            teamStatsSelect.innerHTML = '<option value="">Choose a team...</option>';

            if (Array.isArray(teams)) {
                teams.forEach(team => {
                    const option = document.createElement('option');
                    option.value = team;
                    option.textContent = team;
                    teamStatsSelect.appendChild(option);
                });
            } else if (typeof teams === 'object') {
                // Handle Set serialized as object
                Object.values(teams).forEach(team => {
                    const option = document.createElement('option');
                    option.value = team;
                    option.textContent = team;
                    teamStatsSelect.appendChild(option);
                });
            }
        }
    } catch (error) {
        console.error('Failed to populate team stats dropdown:', error);
    }
}

/**
 * Fetch and display team statistics
 */
async function fetchTeamStats() {
    const teamStatsSelect = document.getElementById('teamStatsSelect');
    const fetchBtn = document.getElementById('fetchTeamStatsBtn');
    const resultsSection = document.getElementById('teamStatsResults');

    const teamName = teamStatsSelect?.value;
    if (!teamName) {
        showToast('Please select a team', 'warning');
        return;
    }

    // Show loading state
    const btnText = fetchBtn?.querySelector('.btn-text');
    const btnLoader = fetchBtn?.querySelector('.btn-loader') || fetchBtn?.querySelector('.spinner-border');
    if (btnText) btnText.style.display = 'none';
    if (btnLoader) btnLoader.style.display = 'inline-block';
    if (fetchBtn) fetchBtn.disabled = true;

    try {
        const response = await fetch(`/api/teams/${encodeURIComponent(teamName)}/stats`);

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'Failed to fetch team stats');
        }

        const stats = await response.json();
        displayTeamStats(stats);
        if (resultsSection) resultsSection.style.display = 'block';

        // Scroll to results
        if (resultsSection) resultsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });

    } catch (error) {
        console.error('Failed to fetch team stats:', error);
        showToast(`Error: ${error.message}`, 'error');
    } finally {
        // Reset button
        if (btnText) btnText.style.display = 'inline';
        if (btnLoader) btnLoader.style.display = 'none';
        if (fetchBtn) fetchBtn.disabled = false;
    }
}

/**
 * Display team statistics in the UI
 */
function displayTeamStats(stats) {
    // Team header
    document.getElementById('statsTeamName').textContent = stats.teamName;

    // Current form badges
    const currentFormEl = document.getElementById('statsCurrentForm');
    if (stats.formStats?.last5Form) {
        currentFormEl.innerHTML = createFormBadgesHtml(stats.formStats.last5Form);
    }

    // Overview tab
    displayOverviewStats(stats);

    // Goals tab
    displayGoalStats(stats);

    // Form tab
    displayFormStats(stats);

    // Recent matches tab
    displayRecentMatches(stats.recentMatches || []);

    // Rivals tab
    displayRivals(stats.topRivals || []);
}

/**
 * Display overview statistics
 */
function displayOverviewStats(stats) {
    const overall = stats.overall || {};
    const home = stats.homeStats || {};
    const away = stats.awayStats || {};
    const season = stats.currentSeason || {};

    // Overall stats
    document.getElementById('statsTotalMatches').textContent = overall.totalMatches || 0;
    document.getElementById('statsWDL').textContent = `${overall.wins || 0}-${overall.draws || 0}-${overall.losses || 0}`;
    document.getElementById('statsWinPct').textContent = `${overall.winPercentage || 0}%`;
    document.getElementById('statsPPG').textContent = Number(overall.pointsPerGame || 0).toFixed(2);
    document.getElementById('statsGD').textContent = formatGoalDifference(overall.goalDifference || 0);

    // Home stats
    document.getElementById('statsHomeWinPct').textContent = `${home.winPercentage || 0}%`;
    document.getElementById('statsHomeGoals').textContent = Number(home.avgGoalsScored || 0).toFixed(2);
    document.getElementById('statsHomeCS').textContent = home.cleanSheets || 0;

    // Away stats
    document.getElementById('statsAwayWinPct').textContent = `${away.winPercentage || 0}%`;
    document.getElementById('statsAwayGoals').textContent = Number(away.avgGoalsScored || 0).toFixed(2);
    document.getElementById('statsAwayCS').textContent = away.cleanSheets || 0;

    // Current season
    document.getElementById('statsSeason').textContent = season.season || '2025/26';
    document.getElementById('statsSeasonPlayed').textContent = season.matchesPlayed || 0;
    document.getElementById('statsSeasonWins').textContent = season.wins || 0;
    document.getElementById('statsSeasonDraws').textContent = season.draws || 0;
    document.getElementById('statsSeasonLosses').textContent = season.losses || 0;
    document.getElementById('statsSeasonPoints').textContent = season.points || 0;
    document.getElementById('statsSeasonGD').textContent = formatGoalDifference(season.goalDifference || 0);
}

/**
 * Display goal statistics
 */
function displayGoalStats(stats) {
    const goals = stats.goalStats || {};
    const overall = stats.overall || {};

    // Scoring
    document.getElementById('statsGoalsScored').textContent = overall.goalsScored || 0;
    document.getElementById('statsAvgScored').textContent = Number(goals.avgGoalsScored || 0).toFixed(2);
    document.getElementById('statsFailedToScore').textContent = goals.failedToScore || 0;

    // Defending
    document.getElementById('statsGoalsConceded').textContent = overall.goalsConceded || 0;
    document.getElementById('statsAvgConceded').textContent = Number(goals.avgGoalsConceded || 0).toFixed(2);
    document.getElementById('statsCleanSheetPct').textContent = `${goals.cleanSheetPercentage || 0}%`;

    // Goal timing
    const firstHalfPct = Number(goals.firstHalfScoringRate || 50);
    const secondHalfPct = Number(goals.secondHalfScoringRate || 50);

    document.getElementById('stats1HGoalsBar').style.width = `${firstHalfPct}%`;
    document.getElementById('stats2HGoalsBar').style.width = `${secondHalfPct}%`;
    document.getElementById('stats1HGoals').textContent = `${goals.firstHalfGoals || 0} (${firstHalfPct.toFixed(0)}%)`;
    document.getElementById('stats2HGoals').textContent = `${goals.secondHalfGoals || 0} (${secondHalfPct.toFixed(0)}%)`;
}

/**
 * Display form statistics
 */
function displayFormStats(stats) {
    const form = stats.formStats || {};

    // Recent form
    document.getElementById('statsLast5Form').innerHTML = createFormBadgesHtml(form.last5Form || '');
    document.getElementById('statsLast10Form').innerHTML = createFormBadgesHtml(form.last10Form || '');
    document.getElementById('statsLast5Pts').textContent = `${Number(form.last5FormPoints || 0).toFixed(2)} ppg`;
    document.getElementById('statsLast10Pts').textContent = `${Number(form.last10FormPoints || 0).toFixed(2)} ppg`;

    // Streaks
    document.getElementById('statsWinStreak').textContent = form.currentWinStreak || 0;
    document.getElementById('statsUnbeatenStreak').textContent = form.currentUnbeatenStreak || 0;
    document.getElementById('statsLongestWin').textContent = form.longestWinStreak || 0;
    document.getElementById('statsLongestUnbeaten').textContent = form.longestUnbeatenStreak || 0;

    // Shot stats
    document.getElementById('statsAvgSOT').textContent = Number(form.avgShotsOnTarget || 0).toFixed(1);
    document.getElementById('statsAvgCorners').textContent = Number(form.avgCorners || 0).toFixed(1);
    document.getElementById('statsConversion').textContent = `${Number(form.shotConversionRate || 0).toFixed(1)}%`;
}

/**
 * Display recent matches
 */
function displayRecentMatches(matches) {
    const container = document.getElementById('statsRecentMatches');
    if (!container) return;

    if (!matches.length) {
        container.innerHTML = '<p class="no-data">No recent matches found</p>';
        return;
    }

    container.innerHTML = `
        <div class="matches-table-wrapper">
            <table class="matches-table">
                <thead>
                    <tr>
                        <th>Date</th>
                        <th>H/A</th>
                        <th>Opponent</th>
                        <th>Score</th>
                        <th>Result</th>
                    </tr>
                </thead>
                <tbody>
                    ${matches.map(match => `
                        <tr class="match-row ${match.result}">
                            <td class="match-date">${formatDate(match.date)}</td>
                            <td><span class="location-badge ${match.isHome ? 'home' : 'away'}">${match.isHome ? 'H' : 'A'}</span></td>
                            <td class="opponent-name">${escapeHtml(match.opponent)}</td>
                            <td class="match-score">${escapeHtml(match.score)}</td>
                            <td><span class="result-badge ${match.result}">${match.result.toUpperCase()}</span></td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        </div>
    `;
}

/**
 * Display H2H rivals
 */
function displayRivals(rivals) {
    const container = document.getElementById('statsRivals');
    if (!container) return;

    if (!rivals.length) {
        container.innerHTML = '<p class="no-data text-center text-muted py-4">No rivalry data available</p>';
        return;
    }

    container.innerHTML = `
        <div class="rivals-table-wrapper">
            <table class="rivals-table">
                <thead>
                    <tr>
                        <th>Opponent</th>
                        <th>P</th>
                        <th>W</th>
                        <th>D</th>
                        <th>L</th>
                        <th>Win %</th>
                    </tr>
                </thead>
                <tbody>
                    ${rivals.map(rival => `
                        <tr class="rival-row">
                            <td class="rival-name">${escapeHtml(rival.opponent)}</td>
                            <td><span class="rival-played">${rival.totalMatches}</span></td>
                            <td><span class="rival-wins">${rival.wins}</span></td>
                            <td><span class="rival-draws">${rival.draws}</span></td>
                            <td><span class="rival-losses">${rival.losses}</span></td>
                            <td><span class="rival-winpct ${getWinPctClass(rival.winPercentage)}">${rival.winPercentage}%</span></td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        </div>
    `;
}

/**
 * Get CSS class based on win percentage
 */
function getWinPctClass(pct) {
    const percentage = Number(pct);
    if (percentage >= 50) return 'high';
    if (percentage >= 30) return 'medium';
    return 'low';
}

/**
 * Switch between stats tabs
 * NOTE: Now handled by Bootstrap tabs automatically
 */
function switchStatsTab(tabId) {
    // Bootstrap handles tab switching via data-bs-toggle and data-bs-target
    // This function is kept for backward compatibility but is no longer needed
    console.log('Tab switching now handled by Bootstrap:', tabId);
}

/**
 * Create form badges HTML from form string (e.g., "WWDLW")
 */
function createFormBadgesHtml(formString) {
    if (!formString) return '';

    return formString.split('').map(result =>
        `<span class="form-badge ${result}">${result}</span>`
    ).join('');
}

/**
 * Format goal difference with +/- sign
 */
function formatGoalDifference(gd) {
    if (gd > 0) return `+${gd}`;
    return gd.toString();
}

/**
 * Format date for team stats display (compact format)
 */
function formatDateCompact(dateStr) {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' });
}

// ═══════════════════════════════════════════════════════════════════════════
// Trending Insights Functions
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Initialize trending insights section
 */
function initTrendingInsights() {
    // Load trending insights on page load
    loadTrendingInsights();

    // Setup refresh button
    if (elements.refreshTrendingBtn) {
        elements.refreshTrendingBtn.addEventListener('click', () => {
            loadTrendingInsights(true);
        });
    }
}

/**
 * Load trending insights from API
 */
async function loadTrendingInsights(forceRefresh = false) {
    try {
        // Show loading state
        if (elements.trendingLoading) elements.trendingLoading.style.display = 'block';
        if (elements.trendingGrid) elements.trendingGrid.style.display = 'none';
        if (elements.trendingMeta) elements.trendingMeta.style.display = 'none';

        if (elements.refreshTrendingBtn) {
            setButtonLoading(elements.refreshTrendingBtn, true);
        }

        const response = await fetch(ENDPOINTS.trendingInsights);

        if (!response.ok) {
            throw new Error('Failed to load trending insights');
        }

        const data = await response.json();
        displayTrendingInsights(data);

    } catch (error) {
        console.error('Error loading trending insights:', error);
        showTrendingError();
    } finally {
        if (elements.refreshTrendingBtn) {
            setButtonLoading(elements.refreshTrendingBtn, false);
        }
    }
}

/**
 * Display trending insights data
 */
function displayTrendingInsights(data) {
    // Hide loading, show content
    if (elements.trendingLoading) elements.trendingLoading.style.display = 'none';
    if (elements.trendingGrid) elements.trendingGrid.style.display = 'flex'; // Bootstrap row uses flex
    if (elements.trendingMeta) elements.trendingMeta.style.display = 'flex';

    // Update metadata
    if (elements.trendingUpdatedAt) {
        elements.trendingUpdatedAt.textContent = data.generatedAt || 'Just now';
    }
    if (elements.trendingTeamsCount) {
        elements.trendingTeamsCount.textContent = data.totalTeamsAnalyzed || '0';
    }

    // Display each widget
    displayHotTeams(data.hotTeams || []);
    displayColdTeams(data.coldTeams || []);
    displayTopScorers(data.topScorers || []);
    displayDefensiveWalls(data.defensiveWalls || []);
    displayUpsetAlerts(data.upsetAlerts || []);
    displayGoalFestMatches(data.goalFestMatches || []);
}

/**
 * Render a form string (e.g., "DWWWW") with color-coded characters
 */
function renderColoredFormString(formString) {
    if (!formString) return '';
    return formString.split('').map(char => {
        const upperChar = char.toUpperCase();
        if (upperChar === 'W' || upperChar === 'D' || upperChar === 'L') {
            return `<span class="form-char ${upperChar}">${upperChar}</span>`;
        }
        return char;
    }).join('');
}

/**
 * Display hot teams (winning streaks)
 */
function displayHotTeams(teams) {
    if (!elements.hotTeamsList) return;

    if (teams.length === 0) {
        elements.hotTeamsList.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🔥</div><div class="empty-state-text">No teams on winning streaks</div></div>';
        return;
    }

    elements.hotTeamsList.innerHTML = teams.map((team, index) => {
        const form = team.recentForm ? team.recentForm.slice(-5) : '';
        const coloredForm = renderColoredFormString(form);
        return `
            <div class="insight-item">
                <span class="insight-item-rank">${index + 1}</span>
                <span class="insight-item-name">${escapeHtml(team.teamName)}</span>
                <span class="insight-item-value">🔥 ${team.winStreak}W</span>
                <span class="insight-item-secondary">${coloredForm}</span>
            </div>
        `;
    }).join('');
}

/**
 * Display cold teams (struggling for wins)
 */
function displayColdTeams(teams) {
    if (!elements.coldTeamsList) return;

    if (teams.length === 0) {
        elements.coldTeamsList.innerHTML = '<div class="empty-state"><div class="empty-state-icon">❄️</div><div class="empty-state-text">No teams on losing streaks</div></div>';
        return;
    }

    elements.coldTeamsList.innerHTML = teams.map((team, index) => {
        const form = team.recentForm ? team.recentForm.slice(-5) : '';
        const coloredForm = renderColoredFormString(form);
        return `
            <div class="insight-item">
                <span class="insight-item-rank">${index + 1}</span>
                <span class="insight-item-name">${escapeHtml(team.teamName)}</span>
                <span class="insight-item-value">❄️ ${team.matchesWithoutWin}</span>
                <span class="insight-item-secondary">${coloredForm}</span>
            </div>
        `;
    }).join('');
}

/**
 * Display top scorers
 */
function displayTopScorers(teams) {
    if (!elements.topScorersList) return;

    if (teams.length === 0) {
        elements.topScorersList.innerHTML = '<div class="empty-state"><div class="empty-state-icon">⚽</div><div class="empty-state-text">No data available</div></div>';
        return;
    }

    elements.topScorersList.innerHTML = teams.map((team, index) => `
        <div class="insight-item">
            <span class="insight-item-rank">${index + 1}</span>
            <span class="insight-item-name">${escapeHtml(team.teamName)}</span>
            <span class="insight-item-value">⚽ ${team.goalsScored}</span>
            <span class="insight-item-secondary">${team.avgGoalsPerMatch}/g</span>
        </div>
    `).join('');
}

/**
 * Display defensive walls (clean sheets)
 */
function displayDefensiveWalls(teams) {
    if (!elements.defensiveWallsList) return;

    if (teams.length === 0) {
        elements.defensiveWallsList.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🛡️</div><div class="empty-state-text">No data available</div></div>';
        return;
    }

    elements.defensiveWallsList.innerHTML = teams.map((team, index) => `
        <div class="insight-item">
            <span class="insight-item-rank">${index + 1}</span>
            <span class="insight-item-name">${escapeHtml(team.teamName)}</span>
            <span class="insight-item-value">🛡️ ${team.cleanSheets}</span>
            <span class="insight-item-secondary">${team.cleanSheetPercentage}%</span>
        </div>
    `).join('');
}

/**
 * Display upset alerts (away team favorites)
 */
function displayUpsetAlerts(matches) {
    if (!elements.upsetAlertsList) return;

    if (matches.length === 0) {
        elements.upsetAlertsList.innerHTML = '<div class="empty-state"><div class="empty-state-icon">⚠️</div><div class="empty-state-text">No upset predictions</div></div>';
        return;
    }

    elements.upsetAlertsList.innerHTML = matches.map(match => `
        <div class="insight-item upset-alert-item">
            <div class="upset-alert-header">
                <div class="upset-alert-teams">
                    <span class="upset-home-team">${escapeHtml(match.homeTeam)}</span>
                    <span class="upset-vs">vs</span>
                    <span class="upset-away-team">${escapeHtml(match.awayTeam)}</span>
                </div>
                <span class="insight-item-value upset-probability">${match.awayWinProbability}%</span>
            </div>
            ${match.reason ? `<div class="upset-alert-reason">${escapeHtml(match.reason)}</div>` : ''}
        </div>
    `).join('');
}

/**
 * Display goal fest matches (high scoring potential)
 */
function displayGoalFestMatches(matches) {
    if (!elements.goalFestList) return;

    if (matches.length === 0) {
        elements.goalFestList.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🎯</div><div class="empty-state-text">No high-scoring matches predicted</div></div>';
        return;
    }

    elements.goalFestList.innerHTML = matches.map(match => `
        <div class="insight-item goal-fest-item">
            <div class="goal-fest-header">
                <span class="goal-fest-teams">${escapeHtml(match.homeTeam)} vs ${escapeHtml(match.awayTeam)}</span>
                <span class="insight-item-value">🎯 ${match.expectedTotalGoals}</span>
            </div>
            <div class="goal-fest-stats">
                <span class="goal-fest-stat"><span class="stat-label">O2.5:</span> <span class="stat-value">${match.over25Probability}%</span></span>
                <span class="goal-fest-stat"><span class="stat-label">BTTS:</span> <span class="stat-value">${match.bttsPercentage}%</span></span>
            </div>
        </div>
    `).join('');
}

/**
 * Show error state for trending insights
 */
function showTrendingError() {
    if (elements.trendingLoading) elements.trendingLoading.style.display = 'none';
    if (elements.trendingGrid) {
        elements.trendingGrid.style.display = 'flex'; // Bootstrap row uses flex
        // Show error in each widget
        const widgets = ['hotTeamsList', 'coldTeamsList', 'topScorersList',
                         'defensiveWallsList', 'upsetAlertsList', 'goalFestList'];
        widgets.forEach(widgetId => {
            const el = document.getElementById(widgetId);
            if (el) {
                el.innerHTML = '<div class="widget-empty">Failed to load data</div>';
            }
        });
    }
}

// Start the application when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    init();
    initTeamStats();
    initTrendingInsights();
    initDynamicUI();
});

// ═══════════════════════════════════════════════════════════════════════════
// Dynamic UI Enhancements
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Initialize all dynamic UI behaviors
 */
function initDynamicUI() {
    // Add animated background to body
    document.body.classList.add('animated-bg');

    // Add particle background effect
    addParticleBackground();

    // Initialize scroll animations (Intersection Observer)
    initScrollAnimations();

    // Initialize counter animations
    initCounterAnimations();

    // Initialize card hover effects
    initCardEffects();

    // Initialize ripple effects on buttons
    initRippleEffects();

    // Initialize tooltips
    initTooltips();

    // Initialize smooth scrolling
    initSmoothScroll();

    // Initialize dynamic number updates
    initDynamicNumbers();

    // Apply stagger animations to grids
    initStaggerAnimations();

    // Initialize interactive stat cards
    initInteractiveStats();

    // Initialize progress bar animations
    initProgressAnimations();

    console.log('✨ Dynamic UI initialized');
}

/**
 * Add particle background effect
 */
function addParticleBackground() {
    if (document.querySelector('.particles')) return;

    const particles = document.createElement('div');
    particles.className = 'particles';
    document.body.prepend(particles);
}

/**
 * Initialize scroll-triggered animations using Intersection Observer
 */
function initScrollAnimations() {
    const observerOptions = {
        root: null,
        rootMargin: '0px',
        threshold: 0.1
    };

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const element = entry.target;
                const animationType = element.dataset.animate || 'slide-in-up';
                element.classList.add(animationType);
                element.style.opacity = '1';
                observer.unobserve(element);
            }
        });
    }, observerOptions);

    // Observe all dashboard cards
    document.querySelectorAll('.dashboard-card, .stat-card, .card').forEach((card, index) => {
        card.style.opacity = '0';
        card.dataset.animate = 'slide-in-up';
        card.classList.add(`stagger-${Math.min(index % 6 + 1, 6)}`);
        observer.observe(card);
    });

    // Observe stat items
    document.querySelectorAll('.stat-item, .season-stat, .shot-stat').forEach((item, index) => {
        item.style.opacity = '0';
        item.dataset.animate = 'zoom-in';
        item.classList.add(`stagger-${Math.min(index % 6 + 1, 6)}`);
        observer.observe(item);
    });
}

/**
 * Initialize counter animations for numbers
 */
function initCounterAnimations() {
    const counters = document.querySelectorAll('[data-count-target]');

    counters.forEach(counter => {
        const target = parseInt(counter.dataset.countTarget) || 0;
        const duration = parseInt(counter.dataset.countDuration) || 2000;

        animateCounter(counter, 0, target, duration);
    });
}

/**
 * Animate a counter from start to end value
 */
function animateCounter(element, start, end, duration) {
    const startTime = performance.now();
    const isDecimal = element.dataset.countDecimals !== undefined;
    const decimals = parseInt(element.dataset.countDecimals) || 0;

    function updateCounter(currentTime) {
        const elapsed = currentTime - startTime;
        const progress = Math.min(elapsed / duration, 1);

        // Easing function (easeOutQuart)
        const easeProgress = 1 - Math.pow(1 - progress, 4);
        const currentValue = start + (end - start) * easeProgress;

        if (isDecimal) {
            element.textContent = currentValue.toFixed(decimals);
        } else {
            element.textContent = Math.round(currentValue);
        }

        if (progress < 1) {
            requestAnimationFrame(updateCounter);
        }
    }

    requestAnimationFrame(updateCounter);
}

/**
 * Initialize card hover effects
 */
function initCardEffects() {
    // Add glow effect to cards
    document.querySelectorAll('.dashboard-card, .card, .stat-card').forEach(card => {
        card.classList.add('card-glow', 'lift-hover');
    });

    // Add tilt effect to match cards
    document.querySelectorAll('.match-card, .quick-match-item').forEach(card => {
        card.classList.add('tilt-card');
    });

    // Add 3D tilt effect on mouse move
    document.querySelectorAll('.tilt-card').forEach(card => {
        card.addEventListener('mousemove', (e) => {
            const rect = card.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;
            const centerX = rect.width / 2;
            const centerY = rect.height / 2;
            const rotateX = (y - centerY) / 20;
            const rotateY = (centerX - x) / 20;

            card.style.transform = `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) translateZ(5px)`;
        });

        card.addEventListener('mouseleave', () => {
            card.style.transform = 'perspective(1000px) rotateX(0) rotateY(0) translateZ(0)';
        });
    });
}

/**
 * Initialize ripple effects on buttons
 */
function initRippleEffects() {
    document.querySelectorAll('.btn').forEach(button => {
        button.classList.add('ripple', 'btn-dynamic');

        button.addEventListener('click', function(e) {
            const ripple = document.createElement('span');
            const rect = this.getBoundingClientRect();
            const size = Math.max(rect.width, rect.height);
            const x = e.clientX - rect.left - size / 2;
            const y = e.clientY - rect.top - size / 2;

            ripple.style.cssText = `
                position: absolute;
                width: ${size}px;
                height: ${size}px;
                left: ${x}px;
                top: ${y}px;
                background: rgba(255, 255, 255, 0.3);
                border-radius: 50%;
                transform: scale(0);
                animation: rippleEffect 0.6s ease-out;
                pointer-events: none;
            `;

            this.appendChild(ripple);

            setTimeout(() => ripple.remove(), 600);
        });
    });

    // Add ripple animation keyframes if not exists
    if (!document.querySelector('#ripple-style')) {
        const style = document.createElement('style');
        style.id = 'ripple-style';
        style.textContent = `
            @keyframes rippleEffect {
                to {
                    transform: scale(4);
                    opacity: 0;
                }
            }
        `;
        document.head.appendChild(style);
    }
}

/**
 * Initialize tooltips
 */
function initTooltips() {
    document.querySelectorAll('[data-tooltip]').forEach(element => {
        element.classList.add('tooltip');
    });
}

/**
 * Initialize smooth scrolling for anchor links
 */
function initSmoothScroll() {
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function(e) {
            e.preventDefault();
            const target = document.querySelector(this.getAttribute('href'));
            if (target) {
                target.scrollIntoView({
                    behavior: 'smooth',
                    block: 'start'
                });
            }
        });
    });
}

/**
 * Initialize dynamic number updates with animation
 */
function initDynamicNumbers() {
    // Store original update functions and wrap them
    const originalUpdateFunctions = {};

    // Observer for stat value changes
    const numberObserver = new MutationObserver((mutations) => {
        mutations.forEach((mutation) => {
            if (mutation.type === 'characterData' || mutation.type === 'childList') {
                const target = mutation.target.parentElement || mutation.target;
                if (target.classList.contains('stat-value') ||
                    target.classList.contains('value') ||
                    target.classList.contains('h2h-stat-value')) {
                    target.classList.add('animated-number', 'updating', 'count-up');
                    setTimeout(() => {
                        target.classList.remove('updating');
                    }, 300);
                }
            }
        });
    });

    // Observe stat values
    document.querySelectorAll('.stat-value, .value, .h2h-stat-value').forEach(el => {
        numberObserver.observe(el, { characterData: true, childList: true, subtree: true });
    });
}

/**
 * Initialize stagger animations for grid items
 */
function initStaggerAnimations() {
    document.querySelectorAll('.stats-grid, .season-grid, .shot-grid, .quick-matches-grid').forEach(grid => {
        const items = grid.children;
        Array.from(items).forEach((item, index) => {
            item.style.animationDelay = `${index * 0.1}s`;
        });
    });
}

/**
 * Initialize interactive stat cards
 */
function initInteractiveStats() {
    document.querySelectorAll('.stat-card, .season-stat, .shot-stat').forEach(card => {
        card.classList.add('interactive-stat', 'gradient-border');
    });
}

/**
 * Initialize progress bar animations
 */
function initProgressAnimations() {
    const progressObserver = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('progress-animated');
                progressObserver.unobserve(entry.target);
            }
        });
    }, { threshold: 0.5 });

    document.querySelectorAll('.timing-bar, .prob-bar').forEach(bar => {
        progressObserver.observe(bar);
    });
}

/**
 * Show success animation on an element
 */
function showSuccessAnimation(element) {
    element.classList.add('success-animation');
    setTimeout(() => element.classList.remove('success-animation'), 500);
}

/**
 * Show error animation (shake) on an element
 */
function showErrorAnimation(element) {
    element.classList.add('shake');
    setTimeout(() => element.classList.remove('shake'), 500);
}

/**
 * Create skeleton loading placeholder
 */
function createSkeletonLoader(count = 3, height = '60px') {
    return Array(count).fill(0).map(() =>
        `<div class="skeleton" style="height: ${height}; margin-bottom: var(--spacing-md);"></div>`
    ).join('');
}

/**
 * Create wave loading animation
 */
function createWaveLoader() {
    return `<div class="wave-loader">
        <span>⚽</span>
        <span>⚽</span>
        <span>⚽</span>
        <span>⚽</span>
        <span>⚽</span>
    </div>`;
}

/**
 * Apply bounce animation to an element
 */
function bounceElement(element) {
    element.classList.add('bounce-in');
    setTimeout(() => element.classList.remove('bounce-in'), 600);
}

/**
 * Apply pulse glow to an element
 */
function pulseElement(element) {
    element.classList.add('pulse-glow');
    setTimeout(() => element.classList.remove('pulse-glow'), 2000);
}

/**
 * Create animated counter element
 */
function createAnimatedCounter(targetValue, options = {}) {
    const {
        duration = 1500,
        decimals = 0,
        prefix = '',
        suffix = ''
    } = options;

    const span = document.createElement('span');
    span.className = 'animated-number';
    span.dataset.countTarget = targetValue;
    span.dataset.countDuration = duration;
    if (decimals > 0) span.dataset.countDecimals = decimals;
    span.textContent = prefix + '0' + suffix;

    setTimeout(() => {
        animateCounter(span, 0, targetValue, duration);
    }, 100);

    return span;
}

/**
 * Refresh dynamic UI after content updates
 */
function refreshDynamicUI() {
    // Re-initialize observers for new content
    initScrollAnimations();
    initCardEffects();
    initProgressAnimations();
    initStaggerAnimations();
}

// Export for use in other parts of the application
window.dynamicUI = {
    showSuccessAnimation,
    showErrorAnimation,
    createSkeletonLoader,
    createWaveLoader,
    bounceElement,
    pulseElement,
    createAnimatedCounter,
    refreshDynamicUI,
    animateCounter
};
