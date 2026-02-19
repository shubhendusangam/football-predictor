/**
 * Theme Toggle Utility - Football Match Predictor
 * Handles auto-switching between light/dark themes with system preference detection
 * Inspired by: Stripe, Linear, Vercel theme systems
 */

(function() {
    'use strict';

    const THEME_KEY = 'football-predictor-theme';
    const THEMES = {
        LIGHT: 'light',
        DARK: 'dark',
        SYSTEM: 'system'
    };

    /**
     * Get the current system theme preference
     */
    function getSystemTheme() {
        return window.matchMedia('(prefers-color-scheme: dark)').matches
            ? THEMES.DARK
            : THEMES.LIGHT;
    }

    /**
     * Get stored theme preference or default to system
     */
    function getStoredTheme() {
        try {
            return localStorage.getItem(THEME_KEY) || THEMES.SYSTEM;
        } catch (e) {
            return THEMES.SYSTEM;
        }
    }

    /**
     * Get the effective theme (resolving 'system' to actual theme)
     */
    function getEffectiveTheme() {
        const stored = getStoredTheme();
        if (stored === THEMES.SYSTEM) {
            return getSystemTheme();
        }
        return stored;
    }

    /**
     * Apply theme to document
     */
    function applyTheme(theme) {
        const effectiveTheme = theme === THEMES.SYSTEM ? getSystemTheme() : theme;

        // Update data attributes
        document.documentElement.setAttribute('data-theme', effectiveTheme);
        document.documentElement.setAttribute('data-bs-theme', effectiveTheme);

        // Update body class for additional styling hooks
        document.body.classList.remove('light', 'dark');
        document.body.classList.add(effectiveTheme);

        // Update meta theme-color for mobile browsers
        const metaThemeColor = document.querySelector('meta[name="theme-color"]');
        if (metaThemeColor) {
            metaThemeColor.setAttribute('content', effectiveTheme === 'dark' ? '#0f172a' : '#ffffff');
        }

        // Update toggle button icon if exists
        updateToggleIcon(effectiveTheme);

        // Dispatch custom event for other components
        window.dispatchEvent(new CustomEvent('themechange', {
            detail: { theme: effectiveTheme, preference: theme }
        }));
    }

    /**
     * Update theme toggle button icon
     */
    function updateToggleIcon(theme) {
        const toggleBtn = document.getElementById('themeToggle');
        if (!toggleBtn) return;

        const icon = toggleBtn.querySelector('i');
        if (!icon) return;

        icon.className = theme === 'dark'
            ? 'bi bi-sun-fill'
            : 'bi bi-moon-fill';

        toggleBtn.setAttribute('aria-label',
            theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'
        );
    }

    /**
     * Save theme preference
     */
    function saveTheme(theme) {
        try {
            localStorage.setItem(THEME_KEY, theme);
        } catch (e) {
            console.warn('Could not save theme preference:', e);
        }
    }

    /**
     * Toggle between light and dark themes
     */
    function toggleTheme() {
        const current = getEffectiveTheme();
        const next = current === THEMES.DARK ? THEMES.LIGHT : THEMES.DARK;
        saveTheme(next);
        applyTheme(next);
        return next;
    }

    /**
     * Set specific theme
     */
    function setTheme(theme) {
        if (!Object.values(THEMES).includes(theme)) {
            console.warn('Invalid theme:', theme);
            return;
        }
        saveTheme(theme);
        applyTheme(theme);
    }

    /**
     * Initialize theme system
     */
    function init() {
        // Apply theme immediately to prevent flash
        const theme = getStoredTheme();
        applyTheme(theme);

        // Listen for system theme changes
        const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
        mediaQuery.addEventListener('change', (e) => {
            if (getStoredTheme() === THEMES.SYSTEM) {
                applyTheme(THEMES.SYSTEM);
            }
        });

        // Setup toggle button when DOM is ready
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', setupToggleButton);
        } else {
            setupToggleButton();
        }
    }

    /**
     * Setup theme toggle button
     */
    function setupToggleButton() {
        const toggleBtn = document.getElementById('themeToggle');
        if (toggleBtn) {
            toggleBtn.addEventListener('click', (e) => {
                e.preventDefault();
                toggleTheme();
            });

            // Set initial icon
            updateToggleIcon(getEffectiveTheme());
        }
    }

    // Export to window for global access
    window.ThemeManager = {
        toggle: toggleTheme,
        set: setTheme,
        get: getEffectiveTheme,
        getPreference: getStoredTheme,
        THEMES: THEMES
    };

    // Initialize
    init();

})();

