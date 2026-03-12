/**
 * Prediction View JavaScript
 * Handles match prediction form submission, API calls, and result display
 */

(function() {
    'use strict';

    // DOM Elements - wrapped in object for consistent access
    const elements = {
        // Form elements
        predictionForm: document.getElementById('predictionForm'),
        homeTeamInput: document.getElementById('homeTeam'),
        awayTeamInput: document.getElementById('awayTeam'),
        matchDateInput: document.getElementById('matchDate'),
        predictBtn: document.getElementById('predictBtn'),
        resetBtn: document.getElementById('resetBtn'),
        retryBtn: document.getElementById('retryBtn'),
        newPredictionBtn: document.getElementById('newPredictionBtn'),
        explainToggle: document.getElementById('explainToggle'),
        expandIcon: document.getElementById('expandIcon'),
        explainContent: document.getElementById('explainContent'),

        // Sections
        loadingSection: document.getElementById('loadingSection'),
        errorSection: document.getElementById('errorSection'),
        predictionResults: document.getElementById('predictionResults'),
        errorMessage: document.getElementById('errorMessage'),

        // Result Elements
        homeTeamName: document.getElementById('homeTeamName'),
        awayTeamName: document.getElementById('awayTeamName'),
        matchDateDisplay: document.getElementById('matchDateDisplay'),
        confidenceBadge: document.getElementById('confidenceBadge'),
        confidenceValue: document.getElementById('confidenceValue'),
        homeGoals: document.getElementById('homeGoals'),
        awayGoals: document.getElementById('awayGoals'),
        homeWinSegment: document.getElementById('homeWinSegment'),
        drawSegment: document.getElementById('drawSegment'),
        awayWinSegment: document.getElementById('awayWinSegment'),
        homeWinValue: document.getElementById('homeWinValue'),
        drawValue: document.getElementById('drawValue'),
        awayWinValue: document.getElementById('awayWinValue')
    };

    // State
    let isLoading = false;
    let lastPredictionData = null;

    /**
     * Initialize the prediction view
     */
    function init() {
        // Set default date to today
        const today = new Date().toISOString().split('T')[0];
        if (elements.matchDateInput) elements.matchDateInput.value = today;

        // Event listeners
        if (elements.predictionForm) elements.predictionForm.addEventListener('submit', handleFormSubmit);
        if (elements.resetBtn) elements.resetBtn.addEventListener('click', handleReset);
        if (elements.retryBtn) elements.retryBtn.addEventListener('click', handleRetry);
        if (elements.newPredictionBtn) elements.newPredictionBtn.addEventListener('click', handleNewPrediction);
        if (elements.explainToggle) elements.explainToggle.addEventListener('click', toggleExplainSection);
    }

    // =====================================================
    // Form Handling
    // =====================================================

    /**
     * Handle form submission
     */
    async function handleFormSubmit(event) {
        if (event) event.preventDefault();

        if (isLoading) {
            console.log('[PredictionView] Already loading, ignoring submit');
            return;
        }

        // Get and validate inputs
        const homeTeam = elements.homeTeamInput?.value?.trim() || '';
        const awayTeam = elements.awayTeamInput?.value?.trim() || '';
        const matchDate = elements.matchDateInput?.value || '';

        if (!homeTeam || !awayTeam) {
            showError('Please enter both home and away team names');
            return;
        }

        if (!matchDate) {
            showError('Please select a match date');
            return;
        }

        lastPredictionData = { homeTeam, awayTeam, matchDate };
        await fetchPrediction(homeTeam, awayTeam, matchDate);
    }

    /**
     * Fetch prediction from API
     */
    async function fetchPrediction(homeTeam, awayTeam, matchDate) {
        showLoading();

        try {
            let data;

            // Use centralized API if available
            if (window.api && typeof window.api.predict === 'function') {
                data = await window.api.predict(homeTeam, awayTeam, matchDate);
            } else {
                // Fallback to direct fetch
                const response = await fetch(`${window.location.origin}/api/predict`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        homeTeam: homeTeam,
                        awayTeam: awayTeam,
                        matchDate: matchDate
                    })
                });

                if (!response.ok) {
                    const errorData = await response.json().catch(() => ({}));
                    throw new Error(errorData.message || `HTTP ${response.status}: ${response.statusText}`);
                }

                data = await response.json();
            }

            displayResults(data, homeTeam, awayTeam, matchDate);

            // Show success toast
            if (window.UI && typeof window.UI.showToast === 'function') {
                window.UI.showToast('Prediction generated successfully!', 'success');
            }

        } catch (error) {
            console.error('[PredictionView] Prediction error:', error);
            showError(error.message || 'Failed to get prediction. Please try again.');
        }
    }

    // =====================================================
    // Loading State Management
    // =====================================================

    /**
     * Show loading state
     */
    function showLoading() {
        isLoading = true;

        // Update button state
        if (elements.predictBtn) {
            const btnText = elements.predictBtn.querySelector('.btn-text');
            const btnLoading = elements.predictBtn.querySelector('.btn-loading');
            if (btnText) btnText.classList.add('hidden');
            if (btnLoading) btnLoading.classList.remove('hidden');
            elements.predictBtn.disabled = true;
        }

        // Show loading section
        hideAllSections();
        if (elements.loadingSection) {
            elements.loadingSection.classList.remove('hidden');
        }
    }

    /**
     * Hide loading state
     */
    function hideLoading() {
        isLoading = false;

        // Update button state
        if (elements.predictBtn) {
            const btnText = elements.predictBtn.querySelector('.btn-text');
            const btnLoading = elements.predictBtn.querySelector('.btn-loading');
            if (btnText) btnText.classList.remove('hidden');
            if (btnLoading) btnLoading.classList.add('hidden');
            elements.predictBtn.disabled = false;
        }
    }

    /**
     * Show error state
     */
    function showError(message) {
        hideLoading();
        hideAllSections();

        if (elements.errorMessage) {
            elements.errorMessage.textContent = message || 'An error occurred';
        }
        if (elements.errorSection) {
            elements.errorSection.classList.remove('hidden');
        }

        // Show toast notification
        if (window.UI && typeof window.UI.showToast === 'function') {
            window.UI.showToast(message, 'error');
        }
    }

    /**
     * Hide all result sections
     */
    function hideAllSections() {
        if (elements.loadingSection) elements.loadingSection.classList.add('hidden');
        if (elements.errorSection) elements.errorSection.classList.add('hidden');
        if (elements.predictionResults) elements.predictionResults.classList.add('hidden');
    }

    // =====================================================
    // Display Results
    // =====================================================

    /**
     * Display prediction results
     */
    function displayResults(data, homeTeam, awayTeam, matchDate) {
        hideLoading();
        hideAllSections();

        // Defensive: Validate data object
        if (!data) {
            showError('Invalid prediction response');
            return;
        }

        // Update match header
        if (elements.homeTeamName) elements.homeTeamName.textContent = homeTeam;
        if (elements.awayTeamName) elements.awayTeamName.textContent = awayTeam;
        if (elements.matchDateDisplay) elements.matchDateDisplay.textContent = formatDate(matchDate);

        // Update confidence badge
        // API returns confidence as string: "HIGH", "MEDIUM", "LOW" or as number 0-1
        const confidenceStr = data.confidence || 'MEDIUM';
        if (elements.confidenceValue) {
            elements.confidenceValue.textContent = confidenceStr;
        }
        updateConfidenceBadge(confidenceStr);

        // Update expected goals from features
        // Use homeGoalsScoredAvg and awayGoalsScoredAvg as expected goals proxy
        const features = data.features || {};
        const expectedHome = parseFloat(features.homeGoalsScoredAvg) || 0;
        const expectedAway = parseFloat(features.awayGoalsScoredAvg) || 0;
        if (elements.homeGoals) elements.homeGoals.textContent = expectedHome.toFixed(2);
        if (elements.awayGoals) elements.awayGoals.textContent = expectedAway.toFixed(2);

        // Update 1X2 probabilities
        // API returns probabilities as decimals (0-1)
        const homeWinProb = Math.round((parseFloat(data.probHomeWin) || 0) * 100);
        const drawProb = Math.round((parseFloat(data.probDraw) || 0) * 100);
        const awayWinProb = Math.round((parseFloat(data.probAwayWin) || 0) * 100);

        // Normalize to ensure they sum to 100%
        const total = homeWinProb + drawProb + awayWinProb;
        let normalizedHome, normalizedDraw, normalizedAway;

        if (total > 0) {
            normalizedHome = Math.round((homeWinProb / total) * 100);
            normalizedDraw = Math.round((drawProb / total) * 100);
            normalizedAway = 100 - normalizedHome - normalizedDraw;
        } else {
            normalizedHome = 33;
            normalizedDraw = 34;
            normalizedAway = 33;
        }

        // Update segment widths with animation
        requestAnimationFrame(() => {
            setTimeout(() => {
                if (elements.homeWinSegment) elements.homeWinSegment.style.width = `${normalizedHome}%`;
                if (elements.drawSegment) elements.drawSegment.style.width = `${normalizedDraw}%`;
                if (elements.awayWinSegment) elements.awayWinSegment.style.width = `${normalizedAway}%`;
            }, 100);
        });

        if (elements.homeWinValue) elements.homeWinValue.textContent = `${homeWinProb}%`;
        if (elements.drawValue) elements.drawValue.textContent = `${drawProb}%`;
        if (elements.awayWinValue) elements.awayWinValue.textContent = `${awayWinProb}%`;

        // Update Elo Rating Display
        updateEloDisplay(data, homeTeam, awayTeam);

        // Update Upset Alert
        updateUpsetAlert(data);

        // Update Explainability Section
        updateExplainSection(data, homeTeam, awayTeam);

        // Show results
        if (elements.predictionResults) {
            elements.predictionResults.classList.remove('hidden');
        }

        // Load team form insights — prefer the merged Form Comparison Widget
        const fcSection = document.getElementById('formComparisonSection');
        if (window.FormComparisonWidget) {
            if (fcSection) fcSection.classList.remove('hidden');
            window.FormComparisonWidget.render('formComparisonContainer', homeTeam, awayTeam, 10);
        } else if (typeof window.loadTeamFormData === 'function') {
            window.loadTeamFormData(homeTeam, awayTeam);
        }

        // Scroll to results
        if (elements.predictionResults) {
            elements.predictionResults.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }

    /**
     * Update Elo rating display with strength bar visualization
     */
    function updateEloDisplay(data, homeTeam, awayTeam) {
        const eloSection = document.getElementById('eloSection');
        if (!eloSection) return;

        const homeElo = data.homeElo || 1500;
        const awayElo = data.awayElo || 1500;
        const eloDiff = data.eloDifference || 0;

        // Update Elo values
        const homeEloEl = document.getElementById('homeEloValue');
        const awayEloEl = document.getElementById('awayEloValue');
        const eloDiffEl = document.getElementById('eloDifferenceValue');

        if (homeEloEl) homeEloEl.textContent = Math.round(homeElo);
        if (awayEloEl) awayEloEl.textContent = Math.round(awayElo);
        if (eloDiffEl) {
            const sign = eloDiff > 0 ? '+' : '';
            eloDiffEl.textContent = `${sign}${Math.round(eloDiff)}`;
            eloDiffEl.className = 'elo-diff-value ' + (eloDiff > 0 ? 'positive' : eloDiff < 0 ? 'negative' : 'neutral');
        }

        // Update strength bar
        const homeBar = document.getElementById('homeStrengthBar');
        const awayBar = document.getElementById('awayStrengthBar');

        if (homeBar && awayBar) {
            // Calculate bar widths based on Elo (normalize to 0-100%)
            // Aligned with backend EloRatingService classification:
            // < 1450 = Weak, 1450-1600 = Competitive, 1600-1750 = Strong, 1750+ = Elite
            const minElo = 1350, maxElo = 1850; // Extended range for visualization
            const normalizedHome = Math.min(100, Math.max(0, ((homeElo - minElo) / (maxElo - minElo)) * 100));
            const normalizedAway = Math.min(100, Math.max(0, ((awayElo - minElo) / (maxElo - minElo)) * 100));

            requestAnimationFrame(() => {
                setTimeout(() => {
                    homeBar.style.width = `${normalizedHome}%`;
                    awayBar.style.width = `${normalizedAway}%`;
                }, 100);
            });
        }

        eloSection.classList.remove('hidden');
    }

    /**
     * Update upset alert badge display
     */
    function updateUpsetAlert(data) {
        const upsetBadge = document.getElementById('upsetAlertBadge');
        if (!upsetBadge) return;

        if (data.upsetAlert) {
            const upsetTeam = data.upsetTeam || 'Underdog';
            upsetBadge.innerHTML = `<span class="upset-icon">⚠️</span> Upset Potential: ${upsetTeam}`;
            upsetBadge.classList.remove('hidden');
            upsetBadge.classList.add('show');
        } else {
            upsetBadge.classList.add('hidden');
            upsetBadge.classList.remove('show');
        }
    }

    /**
     * Update explainability section with prediction factors
     */
    function updateExplainSection(data, homeTeam, awayTeam) {
        const explainContent = document.getElementById('explainContent');
        if (!explainContent) return;

        const explanation = data.explanation || {};

        // Build explanation HTML
        let html = '<div class="explain-factors">';

        // Elo Impact
        if (explanation.eloImpact) {
            const isPositive = explanation.eloImpact.startsWith('+');
            const icon = isPositive ? '📈' : (explanation.eloImpact.startsWith('-') ? '📉' : '➡️');
            html += `
                <div class="explain-factor ${isPositive ? 'positive' : 'negative'}">
                    <span class="factor-icon">${icon}</span>
                    <span class="factor-label">Elo Advantage:</span>
                    <span class="factor-value">${explanation.eloImpact}</span>
                </div>
            `;
        }

        // Form Impact
        if (explanation.formImpact) {
            const isPositive = explanation.formImpact.startsWith('+');
            const icon = isPositive ? '🔥' : (explanation.formImpact.startsWith('-') ? '❄️' : '➡️');
            html += `
                <div class="explain-factor ${isPositive ? 'positive' : 'negative'}">
                    <span class="factor-icon">${icon}</span>
                    <span class="factor-label">Recent Form:</span>
                    <span class="factor-value">${explanation.formImpact}</span>
                </div>
            `;
        }

        // Goal Trend Impact
        if (explanation.goalTrendImpact) {
            const isPositive = explanation.goalTrendImpact.startsWith('+');
            const icon = isPositive ? '⚽' : (explanation.goalTrendImpact.startsWith('-') ? '🛡️' : '➡️');
            html += `
                <div class="explain-factor ${isPositive ? 'positive' : 'negative'}">
                    <span class="factor-icon">${icon}</span>
                    <span class="factor-label">Goal Trend:</span>
                    <span class="factor-value">${explanation.goalTrendImpact}</span>
                </div>
            `;
        }

        // Home Advantage Impact
        if (explanation.homeAdvantageImpact) {
            html += `
                <div class="explain-factor positive">
                    <span class="factor-icon">🏟️</span>
                    <span class="factor-label">Home Advantage:</span>
                    <span class="factor-value">${explanation.homeAdvantageImpact}</span>
                </div>
            `;
        }

        html += '</div>';

        // Summary
        if (explanation.summary) {
            html += `<div class="explain-summary">${explanation.summary}</div>`;
        }

        // Fallback if no explanation data
        if (!explanation.eloImpact && !explanation.formImpact && !explanation.goalTrendImpact) {
            html = '<p class="explain-empty">Prediction based on historical data and team statistics.</p>';
        }

        explainContent.innerHTML = html;
    }

    /**
     * Update confidence badge styling based on confidence level
     * FIX: Handle both string ("HIGH", "MEDIUM", "LOW") and number (0-1) formats
     */
    function updateConfidenceBadge(confidence) {
        if (!elements.confidenceBadge) return;

        // Remove existing classes
        elements.confidenceBadge.classList.remove('low', 'medium', 'high');

        // Handle different confidence formats
        let level;

        if (typeof confidence === 'string') {
            // String format: "HIGH", "MEDIUM", "LOW"
            level = confidence.toLowerCase();
        } else if (typeof confidence === 'number') {
            // Number format: 0-1
            if (confidence < 0.55) {
                level = 'low';
            } else if (confidence <= 0.70) {
                level = 'medium';
            } else {
                level = 'high';
            }
        } else {
            level = 'medium'; // Default
        }

        // Add appropriate class
        if (level === 'high') {
            elements.confidenceBadge.classList.add('high');
        } else if (level === 'low') {
            elements.confidenceBadge.classList.add('low');
        } else {
            elements.confidenceBadge.classList.add('medium');
        }
    }

    // =====================================================
    // Utility Functions
    // =====================================================

    /**
     * Format date for display
     */
    function formatDate(dateString) {
        if (!dateString) return 'Date TBD';

        try {
            const options = { year: 'numeric', month: 'short', day: 'numeric' };
            const date = new Date(dateString + 'T00:00:00');
            return date.toLocaleDateString('en-US', options);
        } catch (e) {
            console.warn('[PredictionView] Invalid date format:', dateString);
            return dateString;
        }
    }

    /**
     * Handle form reset
     */
    function handleReset() {
        if (elements.predictionForm) {
            elements.predictionForm.reset();
        }
        hideAllSections();

        // Clear team form panels
        if (typeof window.clearTeamFormPanels === 'function') {
            window.clearTeamFormPanels();
        }

        // Clear form comparison section
        const fcSection = document.getElementById('formComparisonSection');
        if (fcSection) fcSection.classList.add('hidden');
        const fcContainer = document.getElementById('formComparisonContainer');
        if (fcContainer) fcContainer.innerHTML = '';

        // Reset date to today
        if (elements.matchDateInput) {
            const today = new Date().toISOString().split('T')[0];
            elements.matchDateInput.value = today;
        }

        // Reset explain section
        if (elements.explainContent) elements.explainContent.classList.add('hidden');
        if (elements.expandIcon) elements.expandIcon.classList.remove('rotated');

        // Focus on home team input
        if (elements.homeTeamInput) {
            elements.homeTeamInput.focus();
        }

        // Clear last prediction data
        lastPredictionData = null;
    }

    /**
     * Handle retry button click
     */
    async function handleRetry() {
        if (lastPredictionData) {
            await fetchPrediction(
                lastPredictionData.homeTeam,
                lastPredictionData.awayTeam,
                lastPredictionData.matchDate
            );
        } else {
            hideAllSections();
            if (elements.homeTeamInput) {
                elements.homeTeamInput.focus();
            }
        }
    }

    /**
     * Handle new prediction button click
     */
    function handleNewPrediction() {
        handleReset();

        // Scroll to form
        const formSection = document.querySelector('.prediction-form-section');
        if (formSection) {
            formSection.scrollIntoView({
                behavior: 'smooth',
                block: 'start'
            });
        }
    }

    /**
     * Toggle explain prediction section
     */
    function toggleExplainSection() {
        if (!elements.explainContent) return;

        const isHidden = elements.explainContent.classList.contains('hidden');

        if (isHidden) {
            elements.explainContent.classList.remove('hidden');
            if (elements.expandIcon) elements.expandIcon.classList.add('rotated');
        } else {
            elements.explainContent.classList.add('hidden');
            if (elements.expandIcon) elements.expandIcon.classList.remove('rotated');
        }
    }

    // =====================================================
    // Initialization
    // =====================================================

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // Expose init for manual re-initialization (e.g., when routed to this view)
    window.initPredictionView = init;

})();

