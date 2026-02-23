/**
 * UI Utilities Module
 * ===================
 * Provides reusable UI components:
 * - Toast notification system
 * - Global loading spinner
 * - Skeleton loaders for API calls
 * - Error component
 * - Debounce/throttle utilities
 * - Safe DOM query helpers
 * - Animation utilities
 *
 * ENHANCED WITH:
 * - Smooth micro-animations
 * - Skeleton loaders for all API calls
 * - Improved toast notifications
 * - Page transition effects
 */

(function() {
    'use strict';

    // =====================================================
    // Toast Notification System
    // =====================================================

    const TOAST_CONFIG = {
        duration: 4000,
        maxToasts: 5,
        position: 'bottom-right',
        animationDuration: 300
    };

    let toastContainer = null;
    const activeToasts = [];

    /**
     * Initialize toast container
     */
    function initToastContainer() {
        if (toastContainer) return;

        toastContainer = document.createElement('div');
        toastContainer.id = 'toastContainer';
        toastContainer.className = 'toast-container';
        toastContainer.setAttribute('aria-live', 'polite');
        toastContainer.setAttribute('aria-label', 'Notifications');

        // Add styles if not already present
        if (!document.getElementById('uiUtilsStyles')) {
            const styles = document.createElement('style');
            styles.id = 'uiUtilsStyles';
            styles.textContent = getStyles();
            document.head.appendChild(styles);
        }

        document.body.appendChild(toastContainer);
    }

    /**
     * Show a toast notification
     * @param {string} message - Toast message
     * @param {string} type - Type: 'success' | 'error' | 'warning' | 'info'
     * @param {number} duration - Duration in ms (optional)
     */
    function showToast(message, type = 'info', duration = TOAST_CONFIG.duration) {
        initToastContainer();

        // Remove oldest toast if max reached
        if (activeToasts.length >= TOAST_CONFIG.maxToasts) {
            const oldest = activeToasts.shift();
            if (oldest && oldest.parentNode) {
                oldest.remove();
            }
        }

        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.setAttribute('role', 'alert');

        const icons = {
            success: '✓',
            error: '✕',
            warning: '⚠',
            info: 'ℹ'
        };

        toast.innerHTML = `
            <span class="toast-icon">${icons[type] || icons.info}</span>
            <span class="toast-message">${escapeHtml(message)}</span>
            <button class="toast-close" aria-label="Close notification">×</button>
        `;

        // Close button handler
        const closeBtn = toast.querySelector('.toast-close');
        closeBtn.addEventListener('click', () => removeToast(toast));

        toastContainer.appendChild(toast);
        activeToasts.push(toast);

        // Trigger enter animation
        requestAnimationFrame(() => {
            toast.classList.add('toast-enter');
        });

        // Auto remove after duration
        if (duration > 0) {
            setTimeout(() => removeToast(toast), duration);
        }

        return toast;
    }

    /**
     * Remove a toast notification
     */
    function removeToast(toast) {
        if (!toast || !toast.parentNode) return;

        toast.classList.add('toast-exit');

        setTimeout(() => {
            if (toast.parentNode) {
                toast.remove();
            }
            const index = activeToasts.indexOf(toast);
            if (index > -1) {
                activeToasts.splice(index, 1);
            }
        }, 300);
    }

    // =====================================================
    // Global Loading Spinner
    // =====================================================

    let globalSpinner = null;

    /**
     * Show global loading spinner
     * @param {string} message - Optional loading message
     */
    function showGlobalLoading(message = 'Loading...') {
        if (!globalSpinner) {
            globalSpinner = document.createElement('div');
            globalSpinner.id = 'globalLoadingSpinner';
            globalSpinner.className = 'global-spinner-overlay';
            globalSpinner.innerHTML = `
                <div class="global-spinner-content">
                    <div class="global-spinner"></div>
                    <p class="global-spinner-text">${escapeHtml(message)}</p>
                </div>
            `;
            document.body.appendChild(globalSpinner);
        } else {
            globalSpinner.querySelector('.global-spinner-text').textContent = message;
            globalSpinner.classList.remove('hidden');
        }

        globalSpinner.classList.add('visible');
    }

    /**
     * Hide global loading spinner
     */
    function hideGlobalLoading() {
        if (globalSpinner) {
            globalSpinner.classList.remove('visible');
        }
    }

    /**
     * Create an inline loading spinner
     * @param {string} size - 'small' | 'medium' | 'large'
     * @returns {HTMLElement}
     */
    function createSpinner(size = 'medium') {
        const spinner = document.createElement('div');
        spinner.className = `inline-spinner inline-spinner-${size}`;
        return spinner;
    }

    // =====================================================
    // Error Component
    // =====================================================

    /**
     * Create an error display component
     * @param {Object} options
     * @param {string} options.message - Error message
     * @param {Function} options.onRetry - Retry callback (optional)
     * @param {string} options.icon - Custom icon (optional)
     * @returns {HTMLElement}
     */
    function createErrorComponent(options = {}) {
        const { message = 'An error occurred', onRetry = null, icon = '⚠️' } = options;

        const container = document.createElement('div');
        container.className = 'error-component';
        container.innerHTML = `
            <div class="error-icon">${icon}</div>
            <h4 class="error-title">Something went wrong</h4>
            <p class="error-message">${escapeHtml(message)}</p>
            ${onRetry ? '<button class="btn btn-primary error-retry-btn">Try Again</button>' : ''}
        `;

        if (onRetry) {
            const retryBtn = container.querySelector('.error-retry-btn');
            retryBtn.addEventListener('click', debounce(onRetry, 500));
        }

        return container;
    }

    /**
     * Create an empty state component
     * @param {Object} options
     * @param {string} options.title - Empty state title
     * @param {string} options.message - Empty state message
     * @param {string} options.icon - Icon (optional)
     * @returns {HTMLElement}
     */
    function createEmptyState(options = {}) {
        const { title = 'No Data', message = 'No data available', icon = '📭' } = options;

        const container = document.createElement('div');
        container.className = 'empty-state-component';
        container.innerHTML = `
            <div class="empty-icon">${icon}</div>
            <h4 class="empty-title">${escapeHtml(title)}</h4>
            <p class="empty-message">${escapeHtml(message)}</p>
        `;

        return container;
    }

    // =====================================================
    // Skeleton Loaders
    // =====================================================

    /**
     * Create a skeleton card loader
     * @param {Object} options
     * @param {number} options.lines - Number of text lines (default: 3)
     * @param {boolean} options.showBadge - Show skeleton badge (default: true)
     * @param {boolean} options.showAvatar - Show skeleton avatar (default: false)
     * @returns {HTMLElement}
     */
    function createSkeletonCard(options = {}) {
        const { lines = 3, showBadge = true, showAvatar = false } = options;

        const card = document.createElement('div');
        card.className = 'skeleton-card fade-in';

        let linesHtml = '';
        for (let i = 0; i < lines; i++) {
            const width = i === lines - 1 ? '60%' : (80 + Math.random() * 20) + '%';
            linesHtml += `<div class="skeleton skeleton-text" style="width: ${width}"></div>`;
        }

        card.innerHTML = `
            <div class="skeleton-header">
                <div class="skeleton skeleton-title"></div>
                ${showBadge ? '<div class="skeleton skeleton-badge"></div>' : ''}
            </div>
            <div class="skeleton-body">
                ${showAvatar ? '<div class="skeleton skeleton-avatar mb-2"></div>' : ''}
                ${linesHtml}
            </div>
        `;

        return card;
    }

    /**
     * Create a skeleton row loader (for tables/lists)
     * @param {Object} options
     * @param {boolean} options.showCircle - Show circle avatar (default: true)
     * @param {number} options.columns - Number of content columns (default: 2)
     * @returns {HTMLElement}
     */
    function createSkeletonRow(options = {}) {
        const { showCircle = true, columns = 2 } = options;

        const row = document.createElement('div');
        row.className = 'skeleton-row fade-in';

        let columnsHtml = '';
        for (let i = 0; i < columns; i++) {
            const width = (60 + Math.random() * 30) + '%';
            columnsHtml += `<div class="skeleton skeleton-text" style="width: ${width}"></div>`;
        }

        row.innerHTML = `
            ${showCircle ? '<div class="skeleton skeleton-circle"></div>' : ''}
            <div class="skeleton-content">
                ${columnsHtml}
            </div>
        `;

        return row;
    }

    /**
     * Create multiple skeleton cards in a grid
     * @param {number} count - Number of cards
     * @param {Object} options - Options passed to createSkeletonCard
     * @returns {DocumentFragment}
     */
    function createSkeletonGrid(count = 4, options = {}) {
        const fragment = document.createDocumentFragment();
        for (let i = 0; i < count; i++) {
            const card = createSkeletonCard(options);
            card.classList.add(`stagger-${Math.min(i + 1, 6)}`);
            fragment.appendChild(card);
        }
        return fragment;
    }

    /**
     * Create multiple skeleton rows
     * @param {number} count - Number of rows
     * @param {Object} options - Options passed to createSkeletonRow
     * @returns {DocumentFragment}
     */
    function createSkeletonList(count = 5, options = {}) {
        const fragment = document.createDocumentFragment();
        const container = document.createElement('div');
        container.className = 'skeleton-list fade-in';

        for (let i = 0; i < count; i++) {
            const row = createSkeletonRow(options);
            row.classList.add(`stagger-${Math.min(i + 1, 6)}`);
            container.appendChild(row);
        }

        fragment.appendChild(container);
        return fragment;
    }

    /**
     * Show skeleton loading state in a container
     * @param {HTMLElement} container - Target container
     * @param {string} type - 'grid' | 'list' | 'card'
     * @param {Object} options - Additional options
     */
    function showSkeletonLoading(container, type = 'grid', options = {}) {
        if (!container) return;

        container.innerHTML = '';

        const count = options.count || (type === 'grid' ? 4 : type === 'list' ? 5 : 1);

        switch (type) {
            case 'grid':
                container.appendChild(createSkeletonGrid(count, options));
                break;
            case 'list':
                container.appendChild(createSkeletonList(count, options));
                break;
            case 'card':
            default:
                container.appendChild(createSkeletonCard(options));
                break;
        }
    }

    // =====================================================
    // Utility Functions
    // =====================================================

    /**
     * Debounce function to prevent rapid-fire calls
     * @param {Function} func - Function to debounce
     * @param {number} wait - Wait time in ms
     * @returns {Function}
     */
    function debounce(func, wait = 300) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func.apply(this, args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }

    /**
     * Throttle function to limit call frequency
     * @param {Function} func - Function to throttle
     * @param {number} limit - Minimum time between calls in ms
     * @returns {Function}
     */
    function throttle(func, limit = 300) {
        let inThrottle;
        return function executedFunction(...args) {
            if (!inThrottle) {
                func.apply(this, args);
                inThrottle = true;
                setTimeout(() => (inThrottle = false), limit);
            }
        };
    }

    /**
     * Safe DOM query with null check
     * @param {string} selector - CSS selector
     * @param {HTMLElement} parent - Parent element (optional)
     * @returns {HTMLElement|null}
     */
    function $(selector, parent = document) {
        try {
            return parent.querySelector(selector);
        } catch {
            console.warn('Invalid selector:', selector);
            return null;
        }
    }

    /**
     * Safe DOM query all with null check
     * @param {string} selector - CSS selector
     * @param {HTMLElement} parent - Parent element (optional)
     * @returns {NodeList}
     */
    function $$(selector, parent = document) {
        try {
            return parent.querySelectorAll(selector);
        } catch {
            console.warn('Invalid selector:', selector);
            return [];
        }
    }

    /**
     * Escape HTML to prevent XSS
     * @param {string} str - String to escape
     * @returns {string}
     */
    function escapeHtml(str) {
        if (typeof str !== 'string') return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    /**
     * Format date for display
     * @param {string|Date} date - Date to format
     * @param {Object} options - Intl.DateTimeFormat options
     * @returns {string}
     */
    function formatDate(date, options = {}) {
        try {
            const d = typeof date === 'string' ? new Date(date) : date;
            if (isNaN(d.getTime())) return 'Invalid Date';

            const defaultOptions = {
                year: 'numeric',
                month: 'short',
                day: 'numeric',
                ...options
            };

            return d.toLocaleDateString('en-US', defaultOptions);
        } catch {
            return 'Invalid Date';
        }
    }

    /**
     * Format relative time (e.g., "2 hours ago", "Tomorrow")
     * @param {string|Date} date - Date to format
     * @returns {string}
     */
    function formatRelativeTime(date) {
        try {
            const d = typeof date === 'string' ? new Date(date) : date;
            if (isNaN(d.getTime())) return '';

            const now = new Date();
            const diffMs = d - now;
            const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
            const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
            const diffMinutes = Math.floor(diffMs / (1000 * 60));

            if (diffMinutes < -60 * 24) return `${Math.abs(diffDays)} days ago`;
            if (diffMinutes < -60) return `${Math.abs(diffHours)} hours ago`;
            if (diffMinutes < 0) return `${Math.abs(diffMinutes)} minutes ago`;
            if (diffMinutes < 1) return 'Just now';
            if (diffMinutes < 60) return `In ${diffMinutes} minutes`;
            if (diffHours < 24) return `In ${diffHours} hours`;
            if (diffDays === 0) return 'Today';
            if (diffDays === 1) return 'Tomorrow';
            if (diffDays < 7) return `In ${diffDays} days`;
            return 'Upcoming';
        } catch {
            return '';
        }
    }

    // =====================================================
    // CSS Styles
    // =====================================================

    function getStyles() {
        return `
            /* Toast Container */
            .toast-container {
                position: fixed;
                bottom: 1.5rem;
                right: 1.5rem;
                z-index: 10000;
                display: flex;
                flex-direction: column-reverse;
                gap: 0.75rem;
                pointer-events: none;
                max-width: 400px;
            }

            /* Toast */
            .toast {
                display: flex;
                align-items: center;
                gap: 0.75rem;
                padding: 1rem 1.25rem;
                background: var(--bg-secondary, #1e293b);
                border: 1px solid var(--border-color, #334155);
                border-radius: 0.5rem;
                box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
                color: var(--text-primary, #f1f5f9);
                pointer-events: auto;
                opacity: 0;
                transform: translateX(100%);
                transition: all 0.3s ease;
            }

            .toast-enter {
                opacity: 1;
                transform: translateX(0);
            }

            .toast-exit {
                opacity: 0;
                transform: translateX(100%);
            }

            .toast-success { border-left: 4px solid #22c55e; }
            .toast-error { border-left: 4px solid #ef4444; }
            .toast-warning { border-left: 4px solid #fbbf24; }
            .toast-info { border-left: 4px solid #3b82f6; }

            .toast-icon {
                font-size: 1.25rem;
                flex-shrink: 0;
            }

            .toast-success .toast-icon { color: #22c55e; }
            .toast-error .toast-icon { color: #ef4444; }
            .toast-warning .toast-icon { color: #fbbf24; }
            .toast-info .toast-icon { color: #3b82f6; }

            .toast-message {
                flex: 1;
                font-size: 0.9375rem;
            }

            .toast-close {
                background: none;
                border: none;
                color: var(--text-muted, #94a3b8);
                font-size: 1.25rem;
                cursor: pointer;
                padding: 0;
                line-height: 1;
                transition: color 0.2s;
            }

            .toast-close:hover {
                color: var(--text-primary, #f1f5f9);
            }

            /* Global Spinner Overlay */
            .global-spinner-overlay {
                position: fixed;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
                background: rgba(15, 23, 42, 0.8);
                display: flex;
                align-items: center;
                justify-content: center;
                z-index: 9999;
                opacity: 0;
                visibility: hidden;
                transition: opacity 0.3s, visibility 0.3s;
            }

            .global-spinner-overlay.visible {
                opacity: 1;
                visibility: visible;
            }

            .global-spinner-content {
                text-align: center;
            }

            .global-spinner {
                width: 48px;
                height: 48px;
                border: 4px solid var(--border-color, #334155);
                border-top-color: var(--accent-green, #22c55e);
                border-radius: 50%;
                animation: spin 1s linear infinite;
                margin: 0 auto 1rem;
            }

            .global-spinner-text {
                color: var(--text-primary, #f1f5f9);
                font-size: 1rem;
            }

            /* Inline Spinner */
            .inline-spinner {
                border: 2px solid var(--border-color, #334155);
                border-top-color: var(--accent-green, #22c55e);
                border-radius: 50%;
                animation: spin 0.8s linear infinite;
            }

            .inline-spinner-small { width: 16px; height: 16px; }
            .inline-spinner-medium { width: 24px; height: 24px; }
            .inline-spinner-large { width: 40px; height: 40px; }

            @keyframes spin {
                to { transform: rotate(360deg); }
            }

            /* Error Component */
            .error-component {
                text-align: center;
                padding: 2rem;
                background: var(--bg-secondary, #1e293b);
                border: 1px solid rgba(239, 68, 68, 0.3);
                border-radius: 0.75rem;
            }

            .error-component .error-icon {
                font-size: 3rem;
                margin-bottom: 1rem;
            }

            .error-component .error-title {
                font-size: 1.25rem;
                color: #f87171;
                margin-bottom: 0.5rem;
            }

            .error-component .error-message {
                color: var(--text-secondary, #cbd5e1);
                margin-bottom: 1.5rem;
            }

            /* Empty State Component */
            .empty-state-component {
                text-align: center;
                padding: 3rem 2rem;
            }

            .empty-state-component .empty-icon {
                font-size: 3rem;
                margin-bottom: 1rem;
            }

            .empty-state-component .empty-title {
                font-size: 1.25rem;
                color: var(--text-primary, #f1f5f9);
                margin-bottom: 0.5rem;
            }

            .empty-state-component .empty-message {
                color: var(--text-muted, #94a3b8);
            }

            /* Responsive */
            @media (max-width: 480px) {
                .toast-container {
                    left: 1rem;
                    right: 1rem;
                    bottom: 1rem;
                    max-width: none;
                }
            }
        `;
    }

    // =====================================================
    // Top Loading Bar
    // =====================================================

    let topLoadingBar = null;

    /**
     * Show top loading bar
     */
    function showTopLoading() {
        topLoadingBar = document.getElementById('topLoadingBar');
        if (topLoadingBar) {
            topLoadingBar.classList.remove('hidden');
        }
    }

    /**
     * Hide top loading bar
     */
    function hideTopLoading() {
        topLoadingBar = document.getElementById('topLoadingBar');
        if (topLoadingBar) {
            topLoadingBar.classList.add('hidden');
        }
    }

    // =====================================================
    // Animation Utilities
    // =====================================================

    /**
     * Add fade-in animation to element
     * @param {HTMLElement} element - Target element
     * @param {string} direction - 'up' | 'down' | 'left' | 'right' | '' (default)
     * @param {number} delay - Animation delay in ms
     */
    function fadeIn(element, direction = '', delay = 0) {
        if (!element) return;

        const className = direction ? `fade-in-${direction}` : 'fade-in';
        element.style.opacity = '0';
        element.style.animationDelay = delay + 'ms';

        requestAnimationFrame(() => {
            element.classList.add(className);
            element.style.opacity = '';
        });
    }

    /**
     * Add stagger animation to children
     * @param {HTMLElement} container - Parent container
     * @param {string} childSelector - CSS selector for children
     * @param {string} animationClass - Animation class to apply
     */
    function staggerChildren(container, childSelector = '*', animationClass = 'fade-in-up') {
        if (!container) return;

        const children = container.querySelectorAll(childSelector);
        children.forEach((child, index) => {
            child.style.opacity = '0';
            child.classList.add(animationClass);
            child.classList.add(`stagger-${Math.min(index + 1, 6)}`);

            requestAnimationFrame(() => {
                child.style.opacity = '';
            });
        });
    }

    /**
     * Animate content replacement with fade
     * @param {HTMLElement} container - Target container
     * @param {string|HTMLElement} newContent - New content
     * @param {number} duration - Fade duration in ms
     */
    function fadeReplace(container, newContent, duration = 300) {
        if (!container) return Promise.resolve();

        return new Promise(resolve => {
            container.style.transition = `opacity ${duration}ms ease`;
            container.style.opacity = '0';

            setTimeout(() => {
                if (typeof newContent === 'string') {
                    container.innerHTML = newContent;
                } else {
                    container.innerHTML = '';
                    container.appendChild(newContent);
                }

                container.style.opacity = '1';

                setTimeout(() => {
                    container.style.transition = '';
                    resolve();
                }, duration);
            }, duration);
        });
    }

    // =====================================================
    // Listen for API errors and show toasts
    // =====================================================
    window.addEventListener('api-error', (event) => {
        const { message, status } = event.detail;

        // Don't show toast for certain errors
        if (status === 401 || status === 403) {
            showToast('Session expired. Please refresh.', 'warning');
        } else if (status === 404) {
            // Often expected, don't show toast
        } else if (status >= 500) {
            showToast('Server error. Please try again later.', 'error');
        } else if (message) {
            showToast(message, 'error');
        }
    });

// =====================================================
    // Export to global scope
    // =====================================================
    window.UI = {
        // Toast notifications
        showToast,
        removeToast,

        // Loading indicators
        showGlobalLoading,
        hideGlobalLoading,
        showTopLoading,
        hideTopLoading,
        createSpinner,

        // Skeleton loaders
        createSkeletonCard,
        createSkeletonRow,
        createSkeletonGrid,
        createSkeletonList,
        showSkeletonLoading,

        // Components
        createErrorComponent,
        createEmptyState,

        // Utilities
        debounce,
        throttle,
        $,
        $$,
        escapeHtml,
        formatDate,
        formatRelativeTime,

        // Animations
        fadeIn,
        staggerChildren,
        fadeReplace
    };

    console.log('[UI] UI utilities module initialized');

})();

