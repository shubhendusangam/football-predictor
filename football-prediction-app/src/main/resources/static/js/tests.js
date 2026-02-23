/**
 * Simple Unit Tests for UI Logic
 * ================================
 * No framework required - runs in browser console
 *
 * Usage: Open index.html in browser, then run in console:
 * runTests();
 */

(function() {
    'use strict';

    const tests = [];
    let passed = 0;
    let failed = 0;

    function test(name, fn) {
        tests.push({ name, fn });
    }

    function assert(condition, message) {
        if (!condition) {
            throw new Error(message || 'Assertion failed');
        }
    }

    function assertEqual(actual, expected, message) {
        if (actual !== expected) {
            throw new Error(message || `Expected ${expected}, got ${actual}`);
        }
    }

    function assertDefined(value, message) {
        if (value === undefined || value === null) {
            throw new Error(message || 'Value is undefined or null');
        }
    }

    // =====================================================
    // API Module Tests
    // =====================================================

    test('API module is loaded', () => {
        assertDefined(window.api, 'window.api should be defined');
    });

    test('API has required methods', () => {
        assertDefined(window.api.get, 'api.get should be defined');
        assertDefined(window.api.post, 'api.post should be defined');
        assertDefined(window.api.predict, 'api.predict should be defined');
        assertDefined(window.api.getUpcomingMatches, 'api.getUpcomingMatches should be defined');
        assertDefined(window.api.getTeamForm, 'api.getTeamForm should be defined');
    });

    // =====================================================
    // UI Utils Tests
    // =====================================================

    test('UI module is loaded', () => {
        assertDefined(window.UI, 'window.UI should be defined');
    });

    test('UI has required methods', () => {
        assertDefined(window.UI.showToast, 'UI.showToast should be defined');
        assertDefined(window.UI.debounce, 'UI.debounce should be defined');
        assertDefined(window.UI.throttle, 'UI.throttle should be defined');
        assertDefined(window.UI.escapeHtml, 'UI.escapeHtml should be defined');
        assertDefined(window.UI.formatDate, 'UI.formatDate should be defined');
    });

    test('UI.escapeHtml escapes HTML characters', () => {
        const escaped = window.UI.escapeHtml('<script>alert("xss")</script>');
        assert(!escaped.includes('<script>'), 'Should escape script tags');
        assert(escaped.includes('&lt;'), 'Should contain escaped lt');
    });

    test('UI.debounce returns a function', () => {
        const debounced = window.UI.debounce(() => {}, 100);
        assertEqual(typeof debounced, 'function', 'Should return a function');
    });

    test('UI.formatDate formats dates correctly', () => {
        const formatted = window.UI.formatDate('2026-02-21');
        assert(formatted.includes('Feb') || formatted.includes('2026'), 'Should contain month or year');
    });

    test('UI.formatRelativeTime handles future dates', () => {
        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        const result = window.UI.formatRelativeTime(tomorrow);
        assert(result === 'Tomorrow' || result.includes('In'), 'Should return Tomorrow or In X');
    });

    // =====================================================
    // Layout Manager Tests
    // =====================================================

    test('LayoutManager is loaded', () => {
        assertDefined(window.layoutManager, 'window.layoutManager should be defined');
    });

    // =====================================================
    // Dashboard Manager Tests
    // =====================================================

    test('DashboardManager is loaded', () => {
        assertDefined(window.dashboardManager, 'window.dashboardManager should be defined');
    });

    // =====================================================
    // UpcomingMatchesPanel Tests
    // =====================================================

    test('UpcomingMatchesPanel is loaded', () => {
        assertDefined(window.UpcomingMatchesPanel, 'window.UpcomingMatchesPanel should be defined');
    });

    test('UpcomingMatchesPanel can be instantiated', () => {
        const panel = new window.UpcomingMatchesPanel();
        assertDefined(panel, 'Panel should be instantiated');
        assertDefined(panel.loadMatches, 'Panel should have loadMatches method');
    });

    // =====================================================
    // TeamFormComponent Tests
    // =====================================================

    test('TeamFormComponent is loaded', () => {
        assertDefined(window.TeamFormComponent, 'window.TeamFormComponent should be defined');
    });

    test('TeamFormComponent can be instantiated', () => {
        const component = new window.TeamFormComponent('testContainer');
        assertDefined(component, 'Component should be instantiated');
        assertDefined(component.loadTeamForm, 'Component should have loadTeamForm method');
    });

    // =====================================================
    // Router Tests
    // =====================================================

    test('Router is loaded', () => {
        assertDefined(window.router, 'window.router should be defined');
    });

    // =====================================================
    // Test Runner
    // =====================================================

    function runTests() {
        console.log('🧪 Running UI Module Tests...\n');

        passed = 0;
        failed = 0;

        tests.forEach(t => {
            try {
                t.fn();
                passed++;
                console.log(`✅ ${t.name}`);
            } catch (e) {
                failed++;
                console.error(`❌ ${t.name}`);
                console.error(`   ${e.message}`);
            }
        });

        console.log(`\n📊 Results: ${passed} passed, ${failed} failed`);

        if (failed === 0) {
            console.log('🎉 All tests passed!');
        }

        return { passed, failed, total: tests.length };
    }

    // Expose test runner
    window.runTests = runTests;

    console.log('[Tests] Test module loaded. Run runTests() in console to execute tests.');

})();

