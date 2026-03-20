/**
 * Centralized API Service Module
 * ===============================
 * Provides a unified fetch wrapper with:
 * - Global error handling
 * - Loading state management
 * - Request deduplication
 * - Retry logic
 * - Defensive response handling
 */

(function() {
    'use strict';

    // =====================================================
    // Configuration
    // =====================================================
    const API_CONFIG = {
        baseUrl: window.location.origin + '/api',
        timeout: 30000,
        retryAttempts: 2,
        retryDelay: 1000
    };

    // Track pending requests to prevent duplicates
    const pendingRequests = new Map();

    // Global loading state subscribers
    const loadingSubscribers = new Set();
    let activeRequests = 0;

    // =====================================================
    // Loading State Management
    // =====================================================

    /**
     * Subscribe to loading state changes
     * @param {Function} callback - Called with (isLoading: boolean)
     * @returns {Function} Unsubscribe function
     */
    function subscribeToLoading(callback) {
        loadingSubscribers.add(callback);
        return () => loadingSubscribers.delete(callback);
    }

    /**
     * Notify all subscribers of loading state change
     */
    function notifyLoadingChange() {
        const isLoading = activeRequests > 0;
        loadingSubscribers.forEach(cb => {
            try {
                cb(isLoading);
            } catch (e) {
                console.error('Loading subscriber error:', e);
            }
        });
    }

    /**
     * Increment active request count
     */
    function startLoading() {
        activeRequests++;
        notifyLoadingChange();
    }

    /**
     * Decrement active request count
     */
    function stopLoading() {
        activeRequests = Math.max(0, activeRequests - 1);
        notifyLoadingChange();
    }

    // =====================================================
    // Error Handling
    // =====================================================

    /**
     * API Error class for better error handling
     */
    class ApiError extends Error {
        constructor(message, status, endpoint, details = null) {
            super(message);
            this.name = 'ApiError';
            this.status = status;
            this.endpoint = endpoint;
            this.details = details;
        }
    }

    /**
     * Global error handler
     * @param {ApiError|Error} error
     */
    function handleGlobalError(error) {
        console.error('[API Error]', {
            message: error.message,
            status: error.status,
            endpoint: error.endpoint,
            details: error.details
        });

        // Dispatch custom event for UI to handle
        window.dispatchEvent(new CustomEvent('api-error', {
            detail: {
                message: error.message,
                status: error.status,
                endpoint: error.endpoint
            }
        }));
    }

    // =====================================================
    // Core Fetch Wrapper
    // =====================================================

    /**
     * Core fetch wrapper with error handling, timeout, and retry logic
     * @param {string} endpoint - API endpoint (without base URL)
     * @param {Object} options - Fetch options
     * @returns {Promise<any>} - Parsed response data
     */
    async function apiFetch(endpoint, options = {}) {
        const url = `${API_CONFIG.baseUrl}${endpoint}`;
        const requestKey = `${options.method || 'GET'}:${url}:${JSON.stringify(options.body || '')}`;

        // Prevent duplicate concurrent requests
        if (pendingRequests.has(requestKey)) {
            console.log('[API] Deduplicating request:', endpoint);
            return pendingRequests.get(requestKey);
        }

        const config = {
            method: options.method || 'GET',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json',
                ...options.headers
            },
            ...options
        };

        // Create abort controller for timeout
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), API_CONFIG.timeout);
        config.signal = controller.signal;

        startLoading();

        const requestPromise = (async () => {
            let lastError;

            for (let attempt = 0; attempt <= API_CONFIG.retryAttempts; attempt++) {
                try {
                    if (attempt > 0) {
                        console.log(`[API] Retry attempt ${attempt} for ${endpoint}`);
                        await sleep(API_CONFIG.retryDelay * attempt);
                    }

                    const response = await fetch(url, config);

                    if (!response.ok) {
                        const errorData = await response.json().catch(() => ({}));
                        throw new ApiError(
                            errorData.message || errorData.error || `HTTP ${response.status}: ${response.statusText}`,
                            response.status,
                            endpoint,
                            errorData
                        );
                    }

                    // Handle empty responses
                    const text = await response.text();
                    if (!text) {
                        return null;
                    }

                    try {
                        return JSON.parse(text);
                    } catch {
                        return text;
                    }

                } catch (error) {
                    lastError = error;

                    // Don't retry on client errors (4xx) or abort
                    if (error.name === 'AbortError') {
                        throw new ApiError('Request timeout', 408, endpoint);
                    }
                    if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
                        throw error;
                    }
                }
            }

            throw lastError;
        })();

        pendingRequests.set(requestKey, requestPromise);

        try {
            const result = await requestPromise;
            return result;
        } catch (error) {
            handleGlobalError(error instanceof ApiError ? error : new ApiError(error.message, 0, endpoint));
            throw error;
        } finally {
            clearTimeout(timeoutId);
            pendingRequests.delete(requestKey);
            stopLoading();
        }
    }

    /**
     * Sleep utility for retry delays
     */
    function sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    // =====================================================
    // API Methods
    // =====================================================

    const api = {
        /**
         * GET request
         */
        get: (endpoint) => apiFetch(endpoint, { method: 'GET' }),

        /**
         * POST request
         */
        post: (endpoint, data) => apiFetch(endpoint, {
            method: 'POST',
            body: JSON.stringify(data)
        }),

        /**
         * PUT request
         */
        put: (endpoint, data) => apiFetch(endpoint, {
            method: 'PUT',
            body: JSON.stringify(data)
        }),

        /**
         * DELETE request
         */
        delete: (endpoint) => apiFetch(endpoint, { method: 'DELETE' }),

        // =====================================================
        // Specific API Endpoints
        // =====================================================

        /**
         * Predict match outcome
         * POST /api/predict
         */
        predict: async (homeTeam, awayTeam, matchDate = null) => {
            const payload = { homeTeam, awayTeam };
            if (matchDate) payload.matchDate = matchDate;
            return api.post('/predict', payload);
        },

        /**
         * Predict match scoreline (Poisson model)
         * GET /api/predict/score?home=X&away=Y
         */
        predictScore: async (homeTeam, awayTeam) => {
            const params = `home=${encodeURIComponent(homeTeam)}&away=${encodeURIComponent(awayTeam)}`;
            return api.get(`/predict/score?${params}`);
        },

        /**
         * Get upcoming matches
         * GET /api/matches/upcoming?limit=N
         */
        getUpcomingMatches: async (limit = 10) => {
            const response = await api.get(`/matches/upcoming?limit=${limit}`);
            // Defensive: ensure matches array exists
            return {
                matches: Array.isArray(response?.matches) ? response.matches : [],
                count: response?.count || 0,
                competition: response?.competition || 'Premier League'
            };
        },

        /**
         * Get team form insights
         * GET /api/teams/form?team=NAME
         */
        getTeamForm: async (teamName) => {
            if (!teamName) {
                throw new ApiError('Team name is required', 400, '/teams/form');
            }
            return api.get(`/teams/form?team=${encodeURIComponent(teamName)}`);
        },

        /**
         * Get all teams
         * GET /api/teams
         */
        getAllTeams: async () => {
            const response = await api.get('/teams');
            return Array.isArray(response) ? response : [];
        },

        /**
         * Get team summaries
         * GET /api/teams/summary?limit=N
         */
        getTeamSummaries: async (limit = 10) => {
            const response = await api.get(`/teams/summary?limit=${limit}`);
            return Array.isArray(response) ? response : [];
        },

        /**
         * Get trending insights
         * GET /api/insights/trending
         * GET /api/insights/trending?season=2024-25
         * @param {string} season - Optional season filter (e.g., "2024-25")
         */
        getTrendingInsights: async (season = null) => {
            const params = season ? `?season=${encodeURIComponent(season)}` : '';
            return api.get(`/insights/trending${params}`);
        },

        /**
         * Get available seasons for insights
         * GET /api/insights/seasons
         */
        getInsightsSeasons: async () => {
            return api.get('/insights/seasons');
        },

        /**
         * Get match history
         * GET /api/matches/history?team=NAME&limit=N
         */
        getMatchHistory: async (team = null, limit = 50) => {
            const params = new URLSearchParams();
            if (team) params.append('team', team);
            params.append('limit', limit);
            const response = await api.get(`/matches/history?${params.toString()}`);
            return {
                matches: Array.isArray(response?.matches) ? response.matches : [],
                count: response?.count || 0,
                filter: response?.filter || 'all'
            };
        },

        /**
         * Get all predictions
         * GET /api/predictions
         */
        getAllPredictions: async () => {
            const response = await api.get('/predictions');
            return {
                predictions: Array.isArray(response?.predictions) ? response.predictions :
                             Array.isArray(response) ? response : []
            };
        },

        /**
         * Get model accuracy
         * GET /api/dashboard/accuracy
         */
        getModelAccuracy: async () => {
            try {
                return await api.get('/dashboard/accuracy');
            } catch {
                // Return default values on error
                return { overall: 0, totalPredictions: 0, correctPredictions: 0, winRate: 0 };
            }
        },

        /**
         * Get pre-match insights for a fixture
         * GET /api/analytics/pre-match?homeTeam=X&awayTeam=Y
         */
        getPreMatchInsights: async (homeTeam, awayTeam) => {
            if (!homeTeam || !awayTeam) {
                throw new ApiError('Both team names are required', 400, '/analytics/pre-match');
            }
            return api.get(`/analytics/pre-match?homeTeam=${encodeURIComponent(homeTeam)}&awayTeam=${encodeURIComponent(awayTeam)}`);
        },

        /**
         * Get H2H insights for a fixture
         * GET /api/analytics/h2h?homeTeam=X&awayTeam=Y
         */
        getH2HInsights: async (homeTeam, awayTeam) => {
            if (!homeTeam || !awayTeam) {
                throw new ApiError('Both team names are required', 400, '/analytics/h2h');
            }
            return api.get(`/analytics/h2h?homeTeam=${encodeURIComponent(homeTeam)}&awayTeam=${encodeURIComponent(awayTeam)}`);
        },

        /**
         * Get complete match analysis (pre-match + H2H combined)
         * GET /api/analytics/match?homeTeam=X&awayTeam=Y
         */
        getMatchAnalysis: async (homeTeam, awayTeam) => {
            if (!homeTeam || !awayTeam) {
                throw new ApiError('Both team names are required', 400, '/analytics/match');
            }
            return api.get(`/analytics/match?homeTeam=${encodeURIComponent(homeTeam)}&awayTeam=${encodeURIComponent(awayTeam)}`);
        },

        /**
         * Health check
         */
        healthCheck: async () => {
            try {
                await api.get('/model/status');
                return true;
            } catch {
                return false;
            }
        },

        /**
         * Get Top 4 (Champions League) race analysis
         * GET /api/league/top4-race?season=SEASON
         */
        getTop4Race: async (season = null) => {
            const params = new URLSearchParams();
            if (season) params.append('season', season);
            const queryString = params.toString();
            const endpoint = queryString ? `/league/top4-race?${queryString}` : '/league/top4-race';
            return api.get(endpoint);
        },

        /**
         * Get relegation battle analysis
         * GET /api/league/relegation-battle?season=SEASON
         */
        getRelegationBattle: async (season = null) => {
            const params = new URLSearchParams();
            if (season) params.append('season', season);
            const queryString = params.toString();
            const endpoint = queryString ? `/league/relegation-battle?${queryString}` : '/league/relegation-battle';
            return api.get(endpoint);
        },

        /**
         * Get league standings
         * GET /api/dashboard/league-standings?leagueId=N&season=SEASON
         */
        getLeagueStandings: async (leagueId = null, season = null) => {
            const params = new URLSearchParams();
            if (leagueId) params.append('leagueId', leagueId);
            if (season) params.append('season', season);
            const queryString = params.toString();
            const endpoint = queryString ? `/dashboard/league-standings?${queryString}` : '/dashboard/league-standings';
            return api.get(endpoint);
        },

        /**
         * Get available leagues for standings dropdown
         * GET /api/dashboard/available-leagues
         */
        getAvailableLeagues: async () => {
            const response = await api.get('/dashboard/available-leagues');
            return {
                leagues: Array.isArray(response?.leagues) ? response.leagues : [],
                count: response?.count || 0
            };
        },

        /**
         * Get available seasons for a league
         * GET /api/dashboard/available-seasons?leagueId=N
         */
        getAvailableSeasons: async (leagueId = null) => {
            const params = new URLSearchParams();
            if (leagueId) params.append('leagueId', leagueId);
            const queryString = params.toString();
            const endpoint = queryString ? `/dashboard/available-seasons?${queryString}` : '/dashboard/available-seasons';
            const response = await api.get(endpoint);
            return {
                seasons: Array.isArray(response?.seasons) ? response.seasons : [],
                count: response?.count || 0
            };
        },

        /**
         * Get form guide for a team
         * GET /api/teams/{teamName}/form-guide?matches=N
         */
        getFormGuide: async (teamName, matches = 10) => {
            if (!teamName) {
                throw new ApiError('Team name is required', 400, '/teams/form-guide');
            }
            return api.get(`/teams/${encodeURIComponent(teamName)}/form-guide?matches=${matches}`);
        },

        /**
         * Refresh league standings
         * POST /api/dashboard/league-standings/refresh?leagueId=N
         */
        refreshLeagueStandings: async (leagueId = null) => {
            const params = new URLSearchParams();
            if (leagueId) params.append('leagueId', leagueId);
            const queryString = params.toString();
            const endpoint = queryString ? `/dashboard/league-standings/refresh?${queryString}` : '/dashboard/league-standings/refresh';
            return api.post(endpoint, {});
        },

        // =====================================================
        // Utilities
        // =====================================================

        subscribeToLoading,
        isLoading: () => activeRequests > 0
    };

    // =====================================================
    // Export to global scope
    // =====================================================
    window.api = api;

    console.log('[API] Centralized API service initialized');
    console.log('[API] Base URL:', API_CONFIG.baseUrl);

})();

