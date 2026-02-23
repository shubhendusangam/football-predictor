/**
 * Football Forecaster API Client
 * Handles all backend communication with the Spring Boot API
 */

const API_BASE_URL = window.location.origin + '/api';

class APIClient {
    /**
     * Generic fetch wrapper with error handling
     */
    async fetch(endpoint, options = {}) {
        const url = `${API_BASE_URL}${endpoint}`;
        const config = {
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            },
            ...options
        };

        try {
            const response = await fetch(url, config);

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.message || `HTTP ${response.status}: ${response.statusText}`);
            }

            return await response.json();
        } catch (error) {
            console.error(`API Error [${endpoint}]:`, error);
            throw error;
        }
    }

    // ==================== Predictions ====================

    /**
     * Predict match outcome
     */
    async predict(homeTeam, awayTeam) {
        return this.fetch('/predict', {
            method: 'POST',
            body: JSON.stringify({
                homeTeam: homeTeam,
                awayTeam: awayTeam
            })
        });
    }

    /**
     * Get all predictions
     */
    async getAllPredictions() {
        return this.fetch('/predictions');
    }

    /**
     * Get prediction for a specific match
     */
    async getPredictionById(matchId) {
        return this.fetch(`/predictions/match/${matchId}`);
    }

    /**
     * Create a new prediction
     */
    async createPrediction(predictionData) {
        return this.fetch('/predictions/predict', {
            method: 'POST',
            body: JSON.stringify(predictionData)
        });
    }

    /**
     * Get today's predictions
     */
    async getTodaysPredictions() {
        return this.fetch('/predictions/today');
    }

    // ==================== Teams ====================

    /**
     * Get all teams
     */
    async getAllTeams() {
        return this.fetch('/teams');
    }

    /**
     * Get team summaries with basic stats (W/D/L/Points)
     */
    async getTeamSummaries(limit = 10) {
        return this.fetch(`/teams/summary?limit=${limit}`);
    }

    /**
     * Get team by ID
     */
    async getTeamById(teamId) {
        return this.fetch(`/teams/${teamId}`);
    }

    /**
     * Get team statistics
     */
    async getTeamStats(teamId) {
        return this.fetch(`/teams/${teamId}/stats`);
    }

    /**
     * Get team form (recent performance)
     */
    async getTeamForm(teamId) {
        return this.fetch(`/teams/${teamId}/form`);
    }

    // ==================== Matches ====================

    /**
     * Get upcoming matches
     */
    async getUpcomingMatches(limit = 10) {
        return this.fetch(`/matches/upcoming?limit=${limit}`);
    }

    /**
     * Get match history
     */
    async getMatchHistory(params = {}) {
        const queryString = new URLSearchParams(params).toString();
        return this.fetch(`/matches/history${queryString ? '?' + queryString : ''}`);
    }

    /**
     * Get match by ID
     */
    async getMatchById(matchId) {
        return this.fetch(`/matches/${matchId}`);
    }

    /**
     * Get matches by date range
     */
    async getMatchesByDateRange(startDate, endDate) {
        return this.fetch(`/matches?startDate=${startDate}&endDate=${endDate}`);
    }

    // ==================== Dashboard ====================

    /**
     * Get dashboard statistics
     */
    async getDashboardStats() {
        return this.fetch('/dashboard/stats');
    }

    /**
     * Get model accuracy metrics
     */
    async getModelAccuracy() {
        return this.fetch('/dashboard/accuracy');
    }

    /**
     * Get recent activity
     */
    async getRecentActivity() {
        return this.fetch('/dashboard/activity');
    }

    // ==================== Insights ====================

    /**
     * Get trending insights for a specific season
     * @param {string} season - Optional season filter (e.g., "2024-25")
     */
    async getTrendingInsights(season = null) {
        const params = season ? `?season=${encodeURIComponent(season)}` : '';
        return this.fetch(`/insights/trending${params}`);
    }

    /**
     * Get available seasons for insights
     */
    async getInsightsSeasons() {
        return this.fetch('/insights/seasons');
    }

    // ==================== Seasons ====================

    /**
     * Get all available seasons
     */
    async getAllSeasons() {
        return this.fetch('/seasons');
    }

    /**
     * Get statistics for a specific season
     * @param {string} season Season identifier (e.g., "2023-24")
     * @param {object} params Query parameters (page, pageSize, sortBy, sortDir, team)
     */
    async getSeasonStats(season, params = {}) {
        const queryString = new URLSearchParams(params).toString();
        return this.fetch(`/seasons/${encodeURIComponent(season)}/stats${queryString ? '?' + queryString : ''}`);
    }

    // ==================== Health Check ====================

    /**
     * Check API health
     */
    async healthCheck() {
        try {
            // Use /api/model/status as health check endpoint since actuator is not enabled
            const response = await fetch(`${window.location.origin}/api/model/status`);
            return response.ok;
        } catch (error) {
            console.error('Health check failed:', error);
            return false;
        }
    }
}

// Export singleton instance
const apiClient = new APIClient();
window.apiClient = apiClient;

// Log initialization
console.log('Football Forecaster API Client initialized');
console.log('API Base URL:', API_BASE_URL);

