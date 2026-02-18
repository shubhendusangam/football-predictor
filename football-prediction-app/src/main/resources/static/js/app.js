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
    homeTeamSelect: document.getElementById('homeTeam'),
    awayTeamSelect: document.getElementById('awayTeam'),
    predictBtn: document.getElementById('predictBtn'),
    matchDateInput: document.getElementById('matchDateInput'),

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
    predictionBadge: document.getElementById('predictionBadge'),
    predictionValue: document.getElementById('predictionValue'),
    confidenceBadge: document.getElementById('confidenceBadge'),
    predictionConfidence: document.getElementById('predictionConfidence'),
    confidenceValue: document.getElementById('confidenceValue'),

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
    adminCard: document.getElementById('adminCard'),
    adminLoginOverlay: document.getElementById('adminLoginOverlay'),
    adminControls: document.getElementById('adminControls'),
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
 * Show admin controls (after successful login)
 */
function showAdminControls() {
    elements.adminLoginOverlay.style.display = 'none';
    elements.adminControls.style.display = 'block';
    elements.authBadge.textContent = '🔓 Admin';
    elements.authBadge.classList.remove('locked');
    elements.authBadge.classList.add('unlocked');
    elements.adminLogoutBtn.style.display = 'inline-flex';
}

/**
 * Hide admin controls (show login overlay)
 */
function hideAdminControls() {
    elements.adminLoginOverlay.style.display = 'block';
    elements.adminControls.style.display = 'none';
    elements.authBadge.textContent = '🔒 Admin Only';
    elements.authBadge.classList.remove('unlocked');
    elements.authBadge.classList.add('locked');
    elements.adminLogoutBtn.style.display = 'none';
}

/**
 * Show login error message
 */
function showLoginError(message) {
    elements.loginErrorText.textContent = message;
    elements.loginError.style.display = 'flex';
}

/**
 * Hide login error message
 */
function hideLoginError() {
    elements.loginError.style.display = 'none';
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
        return data;
    } catch (error) {
        console.error('Failed to check model status:', error);
        updateModelStatus(false, null);
        return { modelLoaded: false };
    }
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
    elements.predictionForm.addEventListener('submit', handlePredictionSubmit);
    elements.trainModelBtn.addEventListener('click', trainModel);
    elements.reloadDataBtn.addEventListener('click', reloadData);
    elements.updateDataBtn.addEventListener('click', updateData);
    elements.checkStatusBtn.addEventListener('click', handleCheckStatus);

    // Admin authentication event listeners
    elements.adminLoginForm.addEventListener('submit', handleAdminLogin);
    elements.adminLogoutBtn.addEventListener('click', handleAdminLogout);

    // Advanced training event listeners
    elements.trainAdvancedBtn.addEventListener('click', trainAdvanced);
    elements.trainCVBtn.addEventListener('click', trainWithCrossValidation);
    elements.trainBoostingBtn.addEventListener('click', trainGradientBoosting);
    elements.trainEnsembleBtn.addEventListener('click', trainEnsemble);
    elements.gridSearchBtn.addEventListener('click', performGridSearch);
    elements.compareModelsBtn.addEventListener('click', compareModels);

    // External API event listeners
    elements.fetchUpcomingBtn.addEventListener('click', fetchUpcomingPredictions);

    // Calendar event listeners
    elements.fetchByDateBtn.addEventListener('click', fetchMatchesByDate);
    elements.calendarDateInput.addEventListener('change', fetchMatchesByDate);
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
    elements.refreshNewsBtn.addEventListener('click', () => fetchNews());

    // Initialize admin authentication
    await initAdminAuth();

    // Initialize calendar with today's date
    initCalendar();

    // Initialize news
    initNews();

    // Initialize responsive features
    initResponsive();

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
    initDateInput();
    initTeamFormIndicators();
    updatePredictionCounter();
    initAccessibilityFeatures();
    initPerformanceOptimizations();
}

// Prediction mode toggle (Manual vs Upcoming)
function initPredictionModeToggle() {
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
        const predictionClass = match.predictionCode ? getPredictionClass(match.predictionCode) : '';
        const homeFlag = match.homeTeamCrest ? `<img src="${match.homeTeamCrest}" alt="${match.homeTeam}" class="quick-match-crest">` : '';
        const awayFlag = match.awayTeamCrest ? `<img src="${match.awayTeamCrest}" alt="${match.awayTeam}" class="quick-match-crest">` : '';

        // Build team form HTML (last 5 matches)
        const homeFormHtml = buildQuickMatchFormHtml(match.homeTeamForm);
        const awayFormHtml = buildQuickMatchFormHtml(match.awayTeamForm);

        return `
        <div class="quick-match-item" data-index="${index}" data-home="${match.homeTeam}" data-away="${match.awayTeam}">
            <div class="quick-match-teams">
                <div class="quick-match-team-wrapper">
                    ${homeFlag}
                    <div class="quick-match-team-info">
                        <span class="quick-match-team">${match.homeTeam}</span>
                        ${homeFormHtml}
                    </div>
                </div>
                <span class="quick-match-vs">vs</span>
                <div class="quick-match-team-wrapper away">
                    <div class="quick-match-team-info">
                        <span class="quick-match-team">${match.awayTeam}</span>
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
        item.addEventListener('click', () => {
            const homeTeam = item.dataset.home;
            const awayTeam = item.dataset.away;
            selectQuickMatch(homeTeam, awayTeam);
        });
    });
}

/**
 * Build form HTML for quick match cards (simplified version)
 * Shows compact W/D/L badges or position info
 */
function buildQuickMatchFormHtml(teamForm) {
    if (!teamForm) return '';

    // Try to get recent form string first (e.g., "W,W,D,L,W" or "WWDLW")
    let formString = teamForm.recentForm || teamForm.form;

    if (formString) {
        return createQuickFormBadge(formString);
    }

    // If no form string available, show compact position and points instead
    if (teamForm.position || teamForm.points !== undefined) {
        let info = '';
        if (teamForm.position) {
            info += `<span class="quick-form-position">#${teamForm.position}</span>`;
        }
        if (teamForm.points !== undefined) {
            info += `<span class="quick-form-pts">${teamForm.points}pts</span>`;
        }
        return `<div class="quick-form-info">${info}</div>`;
    }

    return '';
}

/**
 * Create compact form badge for quick matches
 */
function createQuickFormBadge(formString) {
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

    const badges = results.map(r => {
        const result = r.toUpperCase();
        return `<span class="quick-form-item ${result}">${result}</span>`;
    }).join('');

    return `<div class="quick-form-badge">${badges}</div>`;
}

function selectQuickMatch(homeTeam, awayTeam) {
    // Set home team
    if (elements.homeTeamSelect) {
        // Find the matching option (case-insensitive partial match)
        const homeOption = findTeamOption(elements.homeTeamSelect, homeTeam);
        if (homeOption) {
            elements.homeTeamSelect.value = homeOption.value;
        } else {
            console.warn(`Home team "${homeTeam}" not found in dropdown`);
        }
    }

    // Set away team
    if (elements.awayTeamSelect) {
        const awayOption = findTeamOption(elements.awayTeamSelect, awayTeam);
        if (awayOption) {
            elements.awayTeamSelect.value = awayOption.value;
        } else {
            console.warn(`Away team "${awayTeam}" not found in dropdown`);
        }
    }

    // Update form indicators
    updateTeamFormIndicator(homeTeam, 'home');
    updateTeamFormIndicator(awayTeam, 'away');

    // Scroll to prediction form
    elements.predictionForm?.scrollIntoView({ behavior: 'smooth', block: 'center' });

    // Show success feedback
    showToast(`Selected: ${homeTeam} vs ${awayTeam}`, 'success');

    // Automatically trigger prediction after a short delay
    setTimeout(() => {
        if (elements.homeTeamSelect?.value && elements.awayTeamSelect?.value) {
            makePrediction(elements.homeTeamSelect.value, elements.awayTeamSelect.value);
        }
    }, 500);
}

/**
 * Find a team option in a select element by name (case-insensitive partial match)
 */
function findTeamOption(selectElement, teamName) {
    if (!selectElement || !teamName) return null;

    const normalizedName = teamName.toLowerCase().trim();
    const options = Array.from(selectElement.options);

    // Try exact match first
    let match = options.find(opt => opt.value.toLowerCase() === normalizedName);
    if (match) return match;

    // Try partial match (for names like "Man United" vs "Manchester United")
    match = options.find(opt => {
        const optValue = opt.value.toLowerCase();
        return optValue.includes(normalizedName) || normalizedName.includes(optValue);
    });
    if (match) return match;

    // Try matching common abbreviations
    const abbreviations = {
        'man united': 'manchester united',
        'man city': 'manchester city',
        'spurs': 'tottenham',
        'wolves': 'wolverhampton'
    };

    const expanded = abbreviations[normalizedName] || normalizedName;
    match = options.find(opt => opt.value.toLowerCase().includes(expanded));

    return match;
}

function hideQuickMatches() {
    if (elements.quickMatches) {
        elements.quickMatches.style.display = 'none';
    }
}

// Enhanced date input
function initDateInput() {
    if (elements.matchDateInput) {
        // Set default to tomorrow
        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        elements.matchDateInput.value = formatDateForInput(tomorrow);

        // Set min date to today
        const today = new Date();
        elements.matchDateInput.min = formatDateForInput(today);
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
        // Get from localStorage or API
        const count = localStorage.getItem('predictionCount') || 1247;
        animateCounter(elements.totalPredictions, parseInt(count));
    }
}

function animateCounter(element, target) {
    const start = 0;
    const duration = 2000;
    const startTime = Date.now();

    function update() {
        const elapsed = Date.now() - startTime;
        const progress = Math.min(elapsed / duration, 1);
        const current = Math.floor(start + (target - start) * easeOutCubic(progress));

        element.textContent = current.toLocaleString();

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

// Enhanced toast notifications
function showToast(message, type = 'info', duration = 4000) {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `
        <div class="toast-content">
            <span class="toast-icon">${getToastIcon(type)}</span>
            <span class="toast-message">${message}</span>
            <button class="toast-close" onclick="this.parentElement.parentElement.remove()">×</button>
        </div>
    `;

    const container = getToastContainer();
    container.appendChild(toast);

    // Auto remove
    setTimeout(() => {
        if (toast.parentElement) {
            toast.remove();
        }
    }, duration);

    // Announce to screen readers
    announceToScreenReader(message);
}

function getToastIcon(type) {
    const icons = {
        success: '✅',
        error: '❌',
        warning: '⚠️',
        info: 'ℹ️'
    };
    return icons[type] || icons.info;
}

function getToastContainer() {
    let container = document.querySelector('.toast-container');
    if (!container) {
        container = document.createElement('div');
        container.className = 'toast-container';
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

        if (data.predictions && data.predictions.length > 0) {
            return data.predictions.map(match => ({
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
            }));
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
    // Implementation for upcoming matches mode
    console.log('Switching to upcoming matches mode');
}

function hideUpcomingMatches() {
    // Implementation for manual mode
    console.log('Switching to manual mode');
}

// Start the application when DOM is ready
document.addEventListener('DOMContentLoaded', init);

