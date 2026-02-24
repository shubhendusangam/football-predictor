/**
 * System Status Module with SSE-based One-Time Auto Refresh
 * NO interval polling. NO periodic refresh. NO timers.
 * Uses SSE to receive ONE notification when all matches complete.
 */
class SystemStatusManager {
    constructor() {
        this.lastStatus = null;
        this.container = null;
        this.initialized = false;
        this.eventSource = null;
        this.refreshTriggeredToday = false;
        this.todayDate = null;
    }

    init() {
        if (this.initialized) return;
        this.initialized = true;
        this.todayDate = new Date().toISOString().split('T')[0];
        console.log('[SystemStatusManager] Initialized with SSE-based refresh');
    }

    async fetchStatus() {
        try {
            const response = await fetch('/admin/system-status');
            if (response.ok) {
                this.lastStatus = await response.json();
                return this.lastStatus;
            }
            return null;
        } catch (error) {
            console.error('[SystemStatusManager] Fetch error:', error);
            return null;
        }
    }

    formatDateTime(dateTimeStr) {
        if (!dateTimeStr) return 'Never';
        try {
            const date = new Date(dateTimeStr);
            return date.toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
        } catch (e) { return dateTimeStr; }
    }

    getSyncStatusBadgeClass(status) {
        switch (status) {
            case 'SUCCESS': return 'status-badge-success';
            case 'FAILED': return 'status-badge-error';
            case 'IN_PROGRESS': return 'status-badge-progress';
            default: return 'status-badge-pending';
        }
    }

    getTrainingStatusBadgeClass(running) {
        return running ? 'status-badge-warning' : 'status-badge-success';
    }

    renderStatusPanel(container, status) {
        if (!status) {
            container.innerHTML = '<div class="system-status-panel"><div class="status-loading"><div class="admin-spinner"></div><p>Loading...</p></div></div>';
            return;
        }
        const syncBadgeClass = this.getSyncStatusBadgeClass(status.syncStatus);
        const trainingBadgeClass = this.getTrainingStatusBadgeClass(status.trainingRunning);
        const matchDayIndicator = this.getMatchDayIndicatorHTML(status);
        let html = '<div class="system-status-panel"><div class="status-header"><h3>📡 Data Sync & Model Status</h3>' + matchDayIndicator + '</div><div class="status-grid">';
        html += '<div class="status-section"><h4>🔄 Sync Status</h4><div class="status-items">';
        html += '<div class="status-item"><span class="status-label">Last Sync</span><span class="status-value">' + this.formatDateTime(status.lastSyncTime) + '</span></div>';
        html += '<div class="status-item"><span class="status-label">Inserted</span><span class="status-value">' + (status.matchesInsertedToday || 0) + '</span></div>';
        html += '<div class="status-item"><span class="status-label">Updated</span><span class="status-value">' + (status.matchesUpdatedToday || 0) + '</span></div>';
        html += '<div class="status-item"><span class="status-label">Status</span><span class="status-badge ' + syncBadgeClass + '">' + (status.syncStatus || 'PENDING') + '</span></div></div></div>';
        html += '<div class="status-section"><h4>🧠 Model</h4><div class="status-items">';
        html += '<div class="status-item"><span class="status-label">Version</span><span class="status-value">' + (status.modelVersion || 'N/A') + '</span></div>';
        html += '<div class="status-item"><span class="status-label">Training</span><span class="status-badge ' + trainingBadgeClass + '">' + (status.trainingRunning ? 'RUNNING' : 'IDLE') + '</span></div></div></div>';
        html += '<div class="status-section"><h4>⚽ Today</h4><div class="status-items">';
        html += '<div class="status-item"><span class="status-label">Match Day</span><span class="status-value">' + (status.matchDay ? 'Yes' : 'No') + '</span></div>';
        if (status.matchDay) {
            html += '<div class="status-item"><span class="status-label">Completed</span><span class="status-value">' + (status.completedMatchesToday||0) + '/' + (status.totalMatchesToday||0) + '</span></div>';
            html += '<div class="status-item"><span class="status-label">All Done</span><span class="status-badge ' + (status.allMatchesCompleted ? 'status-badge-success' : 'status-badge-pending') + '">' + (status.allMatchesCompleted ? 'YES' : 'WAITING') + '</span></div>';
        }
        html += '</div></div></div>';
        html += '<div class="status-footer"><span>Updated: ' + new Date().toLocaleTimeString() + '</span>';
        html += '<button class="refresh-btn" onclick="window.systemStatusManager.manualRefresh()">🔄 Refresh</button></div></div>';
        container.innerHTML = html;
    }

    getMatchDayIndicatorHTML(status) {
        if (!status.matchDay) return '<span class="match-day-indicator no-matches">📅 No matches</span>';
        if (status.allMatchesCompleted) return '<span class="match-day-indicator completed">✅ All done</span>';
        return '<span class="match-day-indicator waiting"><span class="pulse-dot"></span>' + (status.completedMatchesToday||0) + '/' + (status.totalMatchesToday||0) + '</span>';
    }

    startSSE(container) {
        this.container = container;
        this.fetchStatus().then(status => {
            this.renderStatusPanel(container, status);
            if (status && status.matchDay && !status.allMatchesCompleted) this.connectSSE();
            else console.log('[SystemStatusManager] SSE not needed');
        });
    }

    connectSSE() {
        if (this.eventSource) return;
        console.log('[SystemStatusManager] Connecting to SSE...');
        this.eventSource = new EventSource('/api/events/match-completion');
        this.eventSource.addEventListener('connected', (e) => console.log('[SSE] Connected:', e.data));
        this.eventSource.addEventListener('ALL_MATCHES_COMPLETED', (e) => this.handleMatchCompletion(JSON.parse(e.data)));
        this.eventSource.addEventListener('MATCH_RESULT_UPDATED', (e) => this.handleMatchCompletion(JSON.parse(e.data)));
        this.eventSource.onerror = (e) => console.error('[SSE] Error:', e);
    }

    handleMatchCompletion(eventData) {
        console.log('[SystemStatusManager] Completion:', eventData);
        this.disconnectSSE();
        this.refreshTriggeredToday = true;
        this.fetchStatus().then(status => { if (this.container) this.renderStatusPanel(this.container, status); });
        this.triggerDashboardRefresh();
        this.showNotification(eventData);
    }

    triggerDashboardRefresh() {
        console.log('[SystemStatusManager] ONE-TIME refresh');
        window.dispatchEvent(new CustomEvent('all-matches-completed', { detail: { date: this.todayDate } }));
        window.dispatchEvent(new CustomEvent('refresh-dashboard-data'));
        if (window.dashboardAPI && window.dashboardAPI.refresh) window.dashboardAPI.refresh();
    }

    showNotification(eventData) {
        const n = document.createElement('div');
        n.className = 'match-completion-notification';
        n.innerHTML = '<span>🎉</span><span>' + (eventData.message || 'All matches completed!') + '</span><button onclick="this.parentElement.remove()">×</button>';
        document.body.appendChild(n);
        setTimeout(() => n.remove(), 5000);
    }

    disconnectSSE() { if (this.eventSource) { this.eventSource.close(); this.eventSource = null; } }
    async manualRefresh() { const s = await this.fetchStatus(); if (this.container && s) this.renderStatusPanel(this.container, s); }
    cleanup() { this.disconnectSSE(); }
    startAutoRefresh(container) { this.startSSE(container); }
    stopAutoRefresh() { this.cleanup(); }
    renderCompactIndicator() { const s = this.lastStatus; if (!s) return '⏳'; if (s.trainingRunning) return '🔄'; if (s.syncStatus === 'FAILED') return '❌'; if (s.matchDay && !s.allMatchesCompleted) return '⚽'; return '✅'; }
}

window.systemStatusManager = new SystemStatusManager();
window.systemStatusManager.init();
