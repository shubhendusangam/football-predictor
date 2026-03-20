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
        awayWinValue: document.getElementById('awayWinValue'),

        // Score Prediction Elements
        scorePredictionSection: document.getElementById('scorePredictionSection'),
        predictedScoreValue: document.getElementById('predictedScoreValue'),
        predictedScoreProb: document.getElementById('predictedScoreProb'),
        scoreHomeTeamName: document.getElementById('scoreHomeTeamName'),
        scoreAwayTeamName: document.getElementById('scoreAwayTeamName'),
        topScoresList: document.getElementById('topScoresList'),
        over15Bar: document.getElementById('over15Bar'),
        over25Bar: document.getElementById('over25Bar'),
        over35Bar: document.getElementById('over35Bar'),
        over15Value: document.getElementById('over15Value'),
        over25Value: document.getElementById('over25Value'),
        over35Value: document.getElementById('over35Value'),
        bttsValue: document.getElementById('bttsValue'),
        bttsBar: document.getElementById('bttsBar'),
        csHomeValue: document.getElementById('csHomeValue'),
        csHomeBar: document.getElementById('csHomeBar'),
        csAwayValue: document.getElementById('csAwayValue'),
        csAwayBar: document.getElementById('csAwayBar'),
        scoreMatrixTable: document.getElementById('scoreMatrixTable')
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

        // Update expected goals from features (or from Poisson model if available)
        // Prefer Poisson λ values which are more accurate
        const features = data.features || {};
        const scorePred = data.scorePrediction || {};
        const expectedHome = parseFloat(scorePred.homeExpectedGoals) || parseFloat(features.homeGoalsScoredAvg) || 0;
        const expectedAway = parseFloat(scorePred.awayExpectedGoals) || parseFloat(features.awayGoalsScoredAvg) || 0;
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

        // Update Score Prediction Section (Poisson model)
        displayScorePrediction(data, homeTeam, awayTeam);

        // Update Player Availability Section (Phase 10)
        displayPlayerAvailability(data, homeTeam, awayTeam);

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

    // =====================================================
    // Score Prediction Display (Poisson Model)
    // =====================================================

    /**
     * Display score prediction data from the scorePrediction field.
     * The main /api/predict response now includes a scorePrediction object
     * from the Poisson Dixon-Coles model.
     */
    function displayScorePrediction(data, homeTeam, awayTeam) {
        const section = elements.scorePredictionSection;
        if (!section) return;

        const sp = data.scorePrediction;
        if (!sp || !sp.scorePrediction) {
            section.classList.add('hidden');
            return;
        }

        const pred = sp.scorePrediction;

        // Team names
        if (elements.scoreHomeTeamName) elements.scoreHomeTeamName.textContent = homeTeam;
        if (elements.scoreAwayTeamName) elements.scoreAwayTeamName.textContent = awayTeam;

        // Predicted score
        if (elements.predictedScoreValue && pred.mostLikelyScore) {
            const parts = pred.mostLikelyScore.split('-');
            elements.predictedScoreValue.textContent = parts[0] + ' - ' + parts[1];
        }
        if (elements.predictedScoreProb) {
            const pct = Math.round((pred.probability || 0) * 100);
            elements.predictedScoreProb.textContent = pct + '% probability';
        }

        // Top 3 Scores
        if (elements.topScoresList && pred.top3Scores) {
            elements.topScoresList.innerHTML = '';
            pred.top3Scores.forEach(function(scoreObj, idx) {
                var score = Object.keys(scoreObj)[0];
                var prob = scoreObj[score];
                var pct = Math.round(prob * 100);
                var parts = score.split('-');
                var displayScore = parts[0] + ' - ' + parts[1];

                var chip = document.createElement('div');
                chip.className = 'top-score-chip';
                chip.innerHTML =
                    '<span class="chip-rank">' + (idx + 1) + '</span>' +
                    '<span class="chip-score">' + displayScore + '</span>' +
                    '<span class="chip-prob">' + pct + '%</span>';
                elements.topScoresList.appendChild(chip);
            });
        }

        // Goals Market Bars
        animateMarketBar(elements.over15Bar, elements.over15Value, pred.over15Prob);
        animateMarketBar(elements.over25Bar, elements.over25Value, pred.over25Prob);
        animateMarketBar(elements.over35Bar, elements.over35Value, pred.over35Prob);

        // BTTS & Clean Sheet
        animateStatBar(elements.bttsBar, elements.bttsValue, pred.bttsProb);
        animateStatBar(elements.csHomeBar, elements.csHomeValue, pred.cleanSheetHome);
        animateStatBar(elements.csAwayBar, elements.csAwayValue, pred.cleanSheetAway);

        // Score Matrix Heatmap
        renderScoreMatrix(sp.scoreMatrix, sp.homeExpectedGoals, sp.awayExpectedGoals);

        // Show the section with animation
        section.classList.remove('hidden');
    }

    /**
     * Animate a market probability bar.
     */
    function animateMarketBar(barEl, valueEl, prob) {
        if (!barEl || prob == null) return;
        var pct = Math.round(prob * 100);
        if (valueEl) valueEl.textContent = pct + '%';
        requestAnimationFrame(function() {
            setTimeout(function() {
                barEl.style.width = pct + '%';
            }, 150);
        });
    }

    /**
     * Animate a stat mini-bar.
     */
    function animateStatBar(barEl, valueEl, prob) {
        if (!barEl || prob == null) return;
        var pct = Math.round(prob * 100);
        if (valueEl) valueEl.textContent = pct + '%';
        requestAnimationFrame(function() {
            setTimeout(function() {
                barEl.style.width = pct + '%';
            }, 200);
        });
    }

    /**
     * Render the score probability matrix as a heatmap table.
     * @param {Object} scoreMatrix - Map of "i-j" → probability
     * @param {number} homeXG - Home expected goals (lambda)
     * @param {number} awayXG - Away expected goals (lambda)
     */
    function renderScoreMatrix(scoreMatrix, homeXG, awayXG) {
        var table = elements.scoreMatrixTable;
        if (!table || !scoreMatrix) return;

        // Determine maxGoals from the matrix keys
        var maxGoals = 0;
        Object.keys(scoreMatrix).forEach(function(key) {
            var parts = key.split('-');
            maxGoals = Math.max(maxGoals, parseInt(parts[0]), parseInt(parts[1]));
        });

        // Find max probability for heatmap scaling
        var maxProb = 0;
        Object.values(scoreMatrix).forEach(function(p) {
            if (p > maxProb) maxProb = p;
        });

        // Build table HTML
        var html = '<thead><tr>';
        html += '<th class="matrix-corner">Home ↓ Away →</th>';
        for (var j = 0; j <= maxGoals; j++) {
            html += '<th class="matrix-col-header">' + j + '</th>';
        }
        html += '</tr></thead><tbody>';

        for (var i = 0; i <= maxGoals; i++) {
            html += '<tr>';
            html += '<th class="matrix-row-header">' + i + '</th>';
            for (var jj = 0; jj <= maxGoals; jj++) {
                var key = i + '-' + jj;
                var prob = scoreMatrix[key] || 0;
                var pct = (prob * 100).toFixed(1);
                var intensity = getHeatmapIntensity(prob, maxProb);

                html += '<td class="matrix-cell-' + intensity + '" title="' + key + ': ' + pct + '%">';
                html += pct + '%';
                html += '</td>';
            }
            html += '</tr>';
        }

        html += '</tbody>';
        table.innerHTML = html;
    }

    /**
     * Map a probability to a heatmap intensity level (0-6).
     */
    function getHeatmapIntensity(prob, maxProb) {
        if (!maxProb || maxProb <= 0 || prob <= 0) return 0;
        var ratio = prob / maxProb;
        if (ratio < 0.10) return 0;
        if (ratio < 0.20) return 1;
        if (ratio < 0.35) return 2;
        if (ratio < 0.50) return 3;
        if (ratio < 0.70) return 4;
        if (ratio < 0.90) return 5;
        return 6;
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

        // Availability Impact (Phase 10)
        if (explanation.availabilityImpact) {
            const isPositive = explanation.availabilityImpact.startsWith('+');
            const icon = isPositive ? '🏥' : '🤕';
            html += `
                <div class="explain-factor ${isPositive ? 'positive' : 'negative'}">
                    <span class="factor-icon">${icon}</span>
                    <span class="factor-label">Squad Fitness:</span>
                    <span class="factor-value">${explanation.availabilityImpact}</span>
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
     * Display player availability information in the prediction view.
     * Shows a compact panel with absent players and squad strength for each team.
     */
    function displayPlayerAvailability(data, homeTeam, awayTeam) {
        const section = document.getElementById('playerAvailabilitySection');
        if (!section) return;

        const homeAvail = data.homeAvailability;
        const awayAvail = data.awayAvailability;

        if (!homeAvail && !awayAvail) {
            section.style.display = 'none';
            return;
        }

        section.style.display = 'block';

        function renderTeamAvailability(avail, teamName) {
            if (!avail) return `<div style="color: var(--text-muted); text-align: center; padding: 1rem;">No data for ${teamName}</div>`;

            const strengthPct = Math.round(avail.squadStrength * 100);
            const ratingColors = {
                'FULL_STRENGTH': '#27ae60',
                'MINOR_CONCERNS': '#f39c12',
                'WEAKENED': '#e67e22',
                'SEVERELY_WEAKENED': '#e74c3c'
            };
            const color = ratingColors[avail.availabilityRating] || '#95a5a6';
            const ratingLabels = {
                'FULL_STRENGTH': 'Full Strength',
                'MINOR_CONCERNS': 'Minor Concerns',
                'WEAKENED': 'Weakened',
                'SEVERELY_WEAKENED': 'Severely Weakened'
            };
            const label = ratingLabels[avail.availabilityRating] || avail.availabilityRating;
            const absentPlayers = avail.absentPlayers || [];

            let html = `
                <div style="flex: 1; min-width: 250px;">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem;">
                        <strong>${teamName}</strong>
                        <span style="background: ${color}20; color: ${color}; padding: 0.2rem 0.6rem; border-radius: 10px; font-size: 0.75rem; font-weight: 600;">${label}</span>
                    </div>
                    <div style="margin-bottom: 0.5rem;">
                        <div style="display: flex; justify-content: space-between; font-size: 0.8rem; margin-bottom: 0.2rem;">
                            <span style="color: var(--text-muted);">Squad Strength</span>
                            <span style="font-weight: 600;">${strengthPct}%</span>
                        </div>
                        <div style="height: 5px; background: var(--bg-tertiary); border-radius: 3px; overflow: hidden;">
                            <div style="height: 100%; width: ${strengthPct}%; background: ${color}; border-radius: 3px;"></div>
                        </div>
                    </div>
            `;

            if (absentPlayers.length > 0) {
                html += `<div style="font-size: 0.8rem;">`;
                for (const p of absentPlayers.slice(0, 5)) {
                    const statusColors = { 'INJURED': '#e74c3c', 'SUSPENDED': '#e67e22', 'DOUBTFUL': '#f39c12' };
                    const sColor = statusColors[p.status] || '#95a5a6';
                    html += `
                        <div style="display: flex; justify-content: space-between; align-items: center; padding: 0.2rem 0;">
                            <span>${p.keyStar ? '⭐ ' : ''}${p.playerName} <span style="color: var(--text-muted);">${p.position || ''}</span></span>
                            <span style="color: ${sColor}; font-size: 0.7rem; font-weight: 500;">${p.status}</span>
                        </div>
                    `;
                }
                if (absentPlayers.length > 5) {
                    html += `<div style="color: var(--text-muted); font-size: 0.75rem; margin-top: 0.25rem;">+${absentPlayers.length - 5} more</div>`;
                }
                html += '</div>';
            } else {
                html += `<div style="color: #27ae60; font-size: 0.8rem; text-align: center; padding: 0.5rem 0;">✅ Full squad available</div>`;
            }

            html += '</div>';
            return html;
        }

        let noteHtml = '';
        if (data.availabilityNote) {
            noteHtml = `<div style="margin-top: 0.75rem; padding: 0.5rem; background: var(--bg-tertiary); border-radius: 8px; font-size: 0.8rem; color: var(--text-muted);">
                🏥 ${data.availabilityNote}
            </div>`;
        }

        section.innerHTML = `
            <div class="card" style="padding: 1.25rem;">
                <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 1rem;">
                    <span style="font-size: 1.2rem;">🏥</span>
                    <h3 style="margin: 0; font-size: 1rem;">Player Availability</h3>
                </div>
                <div style="display: flex; gap: 2rem; flex-wrap: wrap;">
                    ${renderTeamAvailability(homeAvail, homeTeam)}
                    ${renderTeamAvailability(awayAvail, awayTeam)}
                </div>
                ${noteHtml}
            </div>
        `;
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

        // Clear score prediction section
        if (elements.scorePredictionSection) elements.scorePredictionSection.classList.add('hidden');

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

