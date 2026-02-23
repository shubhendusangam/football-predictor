/**
 * FOOTBALL FORECASTER - LAYOUT JAVASCRIPT
 * Handles sidebar toggle, navigation state, and interactive elements
 */

class LayoutManager {
    constructor() {
        this.sidebar = document.getElementById('sidebar');
        this.sidebarToggle = document.getElementById('sidebarToggle');
        this.mainContent = document.getElementById('mainContent');
        this.navLinks = document.querySelectorAll('.nav-link');
        this.sidebarLinks = document.querySelectorAll('.sidebar-link');
        this.body = document.body;

        this.init();
    }

    init() {
        this.setupSidebarToggle();
        this.setupNavigationSync();
        this.setupResponsiveHandling();
        this.restoreState();
    }

    /**
     * Setup sidebar toggle functionality with smooth animation
     */
    setupSidebarToggle() {
        if (!this.sidebarToggle || !this.sidebar) return;

        this.sidebarToggle.addEventListener('click', (e) => {
            e.stopPropagation();
            this.toggleSidebar();
        });

        // Close sidebar when clicking outside on mobile (handled by overlay)
        // Keep escape key support
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && window.innerWidth <= 1024) {
                if (this.sidebar.classList.contains('active')) {
                    this.sidebar.classList.remove('active');
                    this.sidebarToggle.classList.remove('active');
                    this.toggleMobileOverlay(false);
                }
            }
        });
    }

    /**
     * Toggle sidebar state with smooth animation
     */
    toggleSidebar() {
        const isMobile = window.innerWidth <= 1024;

        if (isMobile) {
            // On mobile, toggle visibility with overlay
            const isActive = this.sidebar.classList.toggle('active');
            this.sidebarToggle.classList.toggle('active', isActive);

            // Toggle mobile overlay
            this.toggleMobileOverlay(isActive);
        } else {
            // On desktop, toggle collapsed state with smooth animation
            const isCollapsed = this.sidebar.classList.toggle('collapsed');
            this.body.classList.toggle('sidebar-collapsed', isCollapsed);
            this.saveState();

            // Announce state change for accessibility
            const state = isCollapsed ? 'collapsed' : 'expanded';
            this.announceToScreenReader(`Sidebar ${state}`);
        }
    }

    /**
     * Toggle mobile overlay
     */
    toggleMobileOverlay(show) {
        let overlay = document.querySelector('.mobile-overlay');

        if (show) {
            if (!overlay) {
                overlay = document.createElement('div');
                overlay.className = 'mobile-overlay';
                overlay.addEventListener('click', () => {
                    this.sidebar.classList.remove('active');
                    this.sidebarToggle.classList.remove('active');
                    this.toggleMobileOverlay(false);
                });
                document.body.appendChild(overlay);
            }
            // Use requestAnimationFrame for smooth animation
            requestAnimationFrame(() => {
                overlay.classList.add('active');
            });
        } else if (overlay) {
            overlay.classList.remove('active');
        }
    }

    /**
     * Announce to screen readers
     */
    announceToScreenReader(message) {
        const announcement = document.createElement('div');
        announcement.setAttribute('role', 'status');
        announcement.setAttribute('aria-live', 'polite');
        announcement.setAttribute('aria-atomic', 'true');
        announcement.className = 'sr-only';
        announcement.textContent = message;
        announcement.style.cssText = 'position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0;';

        document.body.appendChild(announcement);
        setTimeout(() => announcement.remove(), 1000);
    }

    /**
     * Sync navigation between navbar and sidebar
     */
    setupNavigationSync() {
        // Handle navbar link clicks
        this.navLinks.forEach(link => {
            link.addEventListener('click', (e) => {
                const href = link.getAttribute('href');
                this.setActiveNavigation(href);
                // Don't prevent default - let the hash change naturally
                // The router will handle the actual navigation
            });
        });

        // Handle sidebar link clicks
        this.sidebarLinks.forEach(link => {
            link.addEventListener('click', (e) => {
                const href = link.getAttribute('href');
                this.setActiveNavigation(href);
                // Don't prevent default - let the hash change naturally
                // The router will handle the actual navigation

                // Close sidebar on mobile after navigation
                if (window.innerWidth <= 1024) {
                    this.sidebar.classList.remove('active');
                }
            });
        });

        // Listen for hash changes to sync navigation state
        window.addEventListener('hashchange', () => {
            const hash = window.location.hash || '#dashboard';
            this.setActiveNavigation(hash);
        });
    }

    /**
     * Set active state for navigation links
     */
    setActiveNavigation(href) {
        // Update navbar links
        this.navLinks.forEach(link => {
            if (link.getAttribute('href') === href) {
                link.classList.add('active');
            } else {
                link.classList.remove('active');
            }
        });

        // Update sidebar links
        this.sidebarLinks.forEach(link => {
            if (link.getAttribute('href') === href) {
                link.classList.add('active');
            } else {
                link.classList.remove('active');
            }
        });
    }

    /**
     * Handle responsive behavior
     */
    setupResponsiveHandling() {
        // Debounced resize handler to prevent excessive calls
        let resizeTimer;

        window.addEventListener('resize', () => {
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(() => {
                this.handleResize();
            }, 150);
        });

        // Initial check
        this.handleResize();
    }

    /**
     * Handle window resize
     * Manages sidebar state across breakpoints
     */
    handleResize() {
        if (!this.sidebar) return;

        const isMobile = window.innerWidth <= 1024;
        const isSmallMobile = window.innerWidth <= 768;

        if (isMobile) {
            // On mobile, remove collapsed class and active class
            this.sidebar.classList.remove('collapsed');
            this.sidebar.classList.remove('active');
            this.sidebarToggle?.classList.remove('active');
            if (this.body) {
                this.body.classList.remove('sidebar-collapsed');
            }
            // Remove mobile overlay if exists
            this.toggleMobileOverlay(false);
        } else {
            // On desktop, restore saved state
            this.restoreState();
            this.sidebar.classList.remove('active');
            // Clean up any mobile overlay
            const overlay = document.querySelector('.mobile-overlay');
            if (overlay) overlay.remove();
        }

        // Dispatch event for other components to react
        window.dispatchEvent(new CustomEvent('layout-resize', {
            detail: { isMobile, isSmallMobile }
        }));
    }

    /**
     * Save sidebar state to localStorage
     */
    saveState() {
        if (!this.sidebar) return;

        try {
            const isCollapsed = this.sidebar.classList.contains('collapsed');
            localStorage.setItem('sidebarCollapsed', isCollapsed.toString());
        } catch (e) {
            // localStorage might be unavailable
            console.warn('[Layout] Unable to save sidebar state:', e);
        }
    }

    /**
     * Restore sidebar state from localStorage
     */
    restoreState() {
        if (window.innerWidth > 1024 && this.sidebar) {
            try {
                const isCollapsed = localStorage.getItem('sidebarCollapsed') === 'true';
                if (isCollapsed) {
                    this.sidebar.classList.add('collapsed');
                    if (this.body) {
                        this.body.classList.add('sidebar-collapsed');
                    }
                } else {
                    this.sidebar.classList.remove('collapsed');
                    if (this.body) {
                        this.body.classList.remove('sidebar-collapsed');
                    }
                }
            } catch (e) {
                // localStorage might be unavailable
                console.warn('[Layout] Unable to restore sidebar state:', e);
            }
        }

        // Sync with current hash
        const currentHash = window.location.hash || '#dashboard';
        this.setActiveNavigation(currentHash);
    }
}

/**
 * Utility class for managing cards and components
 */
class ComponentManager {
    constructor() {
        this.cards = document.querySelectorAll('.card');
        this.init();
    }

    init() {
        this.addCardInteractions();
    }

    /**
     * Add interactive effects to cards
     */
    addCardInteractions() {
        this.cards.forEach(card => {
            // Add ripple effect on click (optional enhancement)
            card.addEventListener('click', (e) => {
                this.createRipple(e, card);
            });
        });
    }

    /**
     * Create ripple effect on element
     */
    createRipple(event, element) {
        const ripple = document.createElement('span');
        const rect = element.getBoundingClientRect();
        const size = Math.max(rect.width, rect.height);
        const x = event.clientX - rect.left - size / 2;
        const y = event.clientY - rect.top - size / 2;

        ripple.style.width = ripple.style.height = size + 'px';
        ripple.style.left = x + 'px';
        ripple.style.top = y + 'px';
        ripple.classList.add('ripple');

        // Remove existing ripples
        const existingRipple = element.querySelector('.ripple');
        if (existingRipple) {
            existingRipple.remove();
        }

        element.appendChild(ripple);

        // Remove ripple after animation
        setTimeout(() => {
            ripple.remove();
        }, 600);
    }
}

/**
 * Theme manager for future theme switching
 */
class ThemeManager {
    constructor() {
        this.currentTheme = localStorage.getItem('theme') || 'dark';
        this.init();
    }

    init() {
        this.applyTheme();
    }

    applyTheme() {
        document.body.setAttribute('data-theme', this.currentTheme);
    }

    toggleTheme() {
        this.currentTheme = this.currentTheme === 'dark' ? 'light' : 'dark';
        this.applyTheme();
        localStorage.setItem('theme', this.currentTheme);
    }
}

/**
 * Initialize layout on DOM ready
 */
document.addEventListener('DOMContentLoaded', () => {
    // Initialize layout manager
    const layoutManager = new LayoutManager();

    // Initialize component manager
    const componentManager = new ComponentManager();

    // Initialize theme manager
    const themeManager = new ThemeManager();

    // Make managers available globally for external access if needed
    window.layoutManager = layoutManager;
    window.componentManager = componentManager;
    window.themeManager = themeManager;

    // Log initialization
    console.log('Football Forecaster UI initialized');
});

/**
 * Utility functions for component injection
 */
const UIUtils = {
    /**
     * Create a card element
     */
    createCard(title, content, badge = null) {
        const card = document.createElement('div');
        card.className = 'card';

        const header = document.createElement('div');
        header.className = 'card-header';

        const titleEl = document.createElement('h3');
        titleEl.className = 'card-title';
        titleEl.textContent = title;
        header.appendChild(titleEl);

        if (badge) {
            const badgeEl = document.createElement('span');
            badgeEl.className = `badge badge-${badge.type}`;
            badgeEl.textContent = badge.text;
            header.appendChild(badgeEl);
        }

        const body = document.createElement('div');
        body.className = 'card-body';

        if (typeof content === 'string') {
            body.innerHTML = content;
        } else {
            body.appendChild(content);
        }

        card.appendChild(header);
        card.appendChild(body);

        return card;
    },

    /**
     * Create a badge element
     */
    createBadge(text, type = 'info') {
        const badge = document.createElement('span');
        badge.className = `badge badge-${type}`;
        badge.textContent = text;
        return badge;
    },

    /**
     * Create a button element
     */
    createButton(text, type = 'primary', onClick = null) {
        const button = document.createElement('button');
        button.className = `btn btn-${type}`;
        button.textContent = text;

        if (onClick) {
            button.addEventListener('click', onClick);
        }

        return button;
    },

    /**
     * Inject content into dashboard grid
     */
    injectDashboardContent(cards) {
        const grid = document.querySelector('.dashboard-grid');
        if (!grid) return;

        grid.innerHTML = '';
        cards.forEach(cardData => {
            const card = this.createCard(cardData.title, cardData.content, cardData.badge);
            grid.appendChild(card);
        });
    },

    /**
     * Show loading state
     */
    showLoading(container) {
        container.innerHTML = '<div class="loading">Loading...</div>';
    },

    /**
     * Show error message
     */
    showError(container, message) {
        container.innerHTML = `<div class="error-message">${message}</div>`;
    }
};

// Export utilities
window.UIUtils = UIUtils;

