/**
 * Prediction View JavaScript
 * Handles match prediction form submission, API calls, and result display
 */

(function() {
    'use strict';

    // DOM Elements
    const predictionForm = document.getElementById('predictionForm');
    const homeTeamInput = document.getElementById('homeTeam');
    const awayTeamInput = document.getElementById('awayTeam');
    const matchDateInput = document.getElementById('matchDate');
    const predictBtn = document.getElementById('predictBtn');
    const resetBtn = document.getElementById('resetBtn');
    const retryBtn = document.getElementById('retryBtn');
    const newPredictionBtn = document.getElementById('newPredictionBtn');
    const explainToggle = document.getElementById('explainToggle');
    const expandIcon = document.getElementById('expandIcon');
    const explainContent = document.getElementById('explainContent');

    // Sections
    const loadingSection = document.getElementById('loadingSection');
    const errorSection = document.getElementById('errorSection');
    const predictionResults = document.getElementById('predictionResults');
    const errorMessage = document.getElementById('errorMessage');

    // Result Elements
    const homeTeamName = document.getElementById('homeTeamName');
    const awayTeamName = document.getElementById('awayTeamName');
    const matchDateDisplay = document.getElementById('matchDateDisplay');
    const confidenceBadge = document.getElementById('confidenceBadge');
    const confidenceValue = document.getElementById('confidenceValue');
    const homeGoals = document.getElementById('homeGoals');
    const awayGoals = document.getElementById('awayGoals');
    const homeWinSegment = document.getElementById('homeWinSegment');
    const drawSegment = document.getElementById('drawSegment');
    const awayWinSegment = document.getElementById('awayWinSegment');
    const homeWinValue = document.getElementById('homeWinValue');
    const drawValue = document.getElementById('drawValue');
    const awayWinValue = document.getElementById('awayWinValue');

    // State
    let isLoading = false;
    let lastPredictionData = null;

    /**
     * Initialize the prediction view
     */
    function init() {
        // Set default date to today
        const today = new Date().toISOString().split('T')[0];
        matchDateInput.value = today;

        // Event listeners
        predictionForm.addEventListener('submit', handleFormSubmit);
        resetBtn.addEventListener('click', handleReset);
        retryBtn.addEventListener('click', handleRetry);
        newPredictionBtn.addEventListener('click', handleNewPrediction);
        explainToggle.addEventListener('click', toggleExplainSection);
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


        // Show results
        if (elements.predictionResults) {
            elements.predictionResults.classList.remove('hidden');
        }

        // Load team form insights
        if (typeof window.loadTeamFormData === 'function') {
            window.loadTeamFormData(homeTeam, awayTeam);
        }

        // Scroll to results
        if (elements.predictionResults) {
            elements.predictionResults.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
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

