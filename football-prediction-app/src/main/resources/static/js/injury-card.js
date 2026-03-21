/**
 * Injury Card Component
 * =====================
 *
 * Renders an injury/suspension availability card for a team.
 * Shows: availability rating badge, injured/suspended player lists,
 * impact summary, and attack/defence impact progress bars.
 *
 * Usage:
 *   window.InjuryCard.render(container, teamAvailabilityDTO)
 *   window.InjuryCard.renderMatchContext(container, matchInjuryContextDTO)
 *   window.InjuryCard.fetchAndRender(containerId, fixtureId, homeTeamId, awayTeamId)
 *
 * @version 1.0.0
 */

(function() {
    'use strict';

    // Rating badge colour mapping
    const RATING_STYLES = {
        FULL_STRENGTH:      { cls: 'injury-card__badge--green',  label: 'Full Strength',      icon: '✅' },
        WEAKENED:           { cls: 'injury-card__badge--amber',  label: 'Weakened',            icon: '⚠️' },
        SEVERELY_WEAKENED:  { cls: 'injury-card__badge--red',    label: 'Severely Weakened',   icon: '🔴' }
    };

    /**
     * Escape HTML to prevent XSS
     */
    function escapeHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    /**
     * Create a player list (injured or suspended)
     */
    function createPlayerList(players, listType) {
        const list = document.createElement('div');
        list.className = 'injury-card__player-list';

        if (!players || players.length === 0) {
            const empty = document.createElement('div');
            empty.className = 'injury-card__no-players';
            empty.textContent = listType === 'suspended' ? 'No suspensions' : 'No injuries reported';
            list.appendChild(empty);
            return list;
        }

        players.forEach(function(player) {
            const row = document.createElement('div');
            row.className = 'injury-card__player-row';

            const name = document.createElement('span');
            name.className = 'injury-card__player-name';
            if (player.keyStar) name.className += ' injury-card__player-name--key';
            name.textContent = escapeHtml(player.playerName);

            const pill = document.createElement('span');
            pill.className = listType === 'suspended'
                ? 'injury-card__pill injury-card__pill--suspended'
                : 'injury-card__pill injury-card__pill--injury';
            pill.textContent = escapeHtml(player.injuryType || player.reason || listType);

            row.appendChild(name);
            row.appendChild(pill);
            list.appendChild(row);
        });

        return list;
    }

    /**
     * Create an impact progress bar
     */
    function createImpactBar(label, value, maxValue) {
        const wrapper = document.createElement('div');
        wrapper.className = 'injury-card__impact-bar-wrapper';

        const labelEl = document.createElement('div');
        labelEl.className = 'injury-card__impact-label';
        labelEl.textContent = label;

        const barOuter = document.createElement('div');
        barOuter.className = 'injury-card__impact-bar-outer';

        const barInner = document.createElement('div');
        barInner.className = 'injury-card__impact-bar-inner';

        var pct = Math.min(100, (value / maxValue) * 100);
        // Colour based on severity
        if (pct > 60) barInner.classList.add('injury-card__impact-bar-inner--high');
        else if (pct > 30) barInner.classList.add('injury-card__impact-bar-inner--medium');
        else barInner.classList.add('injury-card__impact-bar-inner--low');

        // Animate width
        requestAnimationFrame(function() {
            setTimeout(function() { barInner.style.width = pct + '%'; }, 80);
        });

        const valueLabel = document.createElement('span');
        valueLabel.className = 'injury-card__impact-value';
        valueLabel.textContent = '-' + Math.round(value * 100) + '%';

        barOuter.appendChild(barInner);
        wrapper.appendChild(labelEl);
        wrapper.appendChild(barOuter);
        wrapper.appendChild(valueLabel);
        return wrapper;
    }

    /**
     * Render a single team availability card.
     */
    function render(container, data) {
        if (typeof container === 'string') container = document.getElementById(container);
        if (!container) return;
        container.innerHTML = '';

        // ── No data placeholder ──
        if (!data || !data.dataAvailable) {
            var placeholder = document.createElement('div');
            placeholder.className = 'injury-card injury-card--no-data';
            placeholder.innerHTML =
                '<div class="injury-card__header">' +
                '  <span class="injury-card__title">🏥 Injury Report</span>' +
                '</div>' +
                '<div class="injury-card__body">' +
                '  <p class="injury-card__placeholder-text">Injury data unavailable</p>' +
                '</div>';
            container.appendChild(placeholder);
            return;
        }

        var ratingStyle = RATING_STYLES[data.availabilityRating] || RATING_STYLES.FULL_STRENGTH;

        var card = document.createElement('div');
        card.className = 'injury-card';

        // Header
        var header = document.createElement('div');
        header.className = 'injury-card__header';

        var title = document.createElement('span');
        title.className = 'injury-card__title';
        title.textContent = '🏥 ' + escapeHtml(data.teamName || 'Team');

        var badge = document.createElement('span');
        badge.className = 'injury-card__badge ' + ratingStyle.cls;
        badge.textContent = ratingStyle.icon + ' ' + ratingStyle.label;

        header.appendChild(title);
        header.appendChild(badge);
        card.appendChild(header);

        // Body
        var body = document.createElement('div');
        body.className = 'injury-card__body';

        // Summary
        if (data.impactSummary) {
            var summary = document.createElement('div');
            summary.className = 'injury-card__summary';
            summary.textContent = data.impactSummary;
            body.appendChild(summary);
        }

        // Injured players
        if (data.injuredPlayers && data.injuredPlayers.length > 0) {
            var injuredHeader = document.createElement('div');
            injuredHeader.className = 'injury-card__section-label';
            injuredHeader.textContent = 'Injured (' + data.injuredPlayers.length + ')';
            body.appendChild(injuredHeader);
            body.appendChild(createPlayerList(data.injuredPlayers, 'injury'));
        }

        // Suspended players
        if (data.suspendedPlayers && data.suspendedPlayers.length > 0) {
            var suspendedHeader = document.createElement('div');
            suspendedHeader.className = 'injury-card__section-label injury-card__section-label--suspended';
            suspendedHeader.textContent = 'Suspended (' + data.suspendedPlayers.length + ')';
            body.appendChild(suspendedHeader);
            body.appendChild(createPlayerList(data.suspendedPlayers, 'suspended'));
        }

        // Impact bars
        if (data.attackImpactReduction > 0 || data.defenceImpactReduction > 0) {
            var impactSection = document.createElement('div');
            impactSection.className = 'injury-card__impact-section';

            if (data.attackImpactReduction > 0) {
                impactSection.appendChild(createImpactBar('Attack Impact', data.attackImpactReduction, 0.30));
            }
            if (data.defenceImpactReduction > 0) {
                impactSection.appendChild(createImpactBar('Defence Impact', data.defenceImpactReduction, 0.25));
            }
            body.appendChild(impactSection);
        }

        card.appendChild(body);
        container.appendChild(card);
    }

    /**
     * Render both home and away injury cards side by side.
     */
    function renderMatchContext(container, matchContext) {
        if (typeof container === 'string') container = document.getElementById(container);
        if (!container) return;
        container.innerHTML = '';

        var wrapper = document.createElement('div');
        wrapper.className = 'injury-card__match-wrapper';

        var homeDiv = document.createElement('div');
        homeDiv.className = 'injury-card__team-column';
        render(homeDiv, matchContext.homeAvailability);

        var awayDiv = document.createElement('div');
        awayDiv.className = 'injury-card__team-column';
        render(awayDiv, matchContext.awayAvailability);

        wrapper.appendChild(homeDiv);
        wrapper.appendChild(awayDiv);
        container.appendChild(wrapper);

        // Adjustment note
        if (matchContext.probabilitiesAdjusted && matchContext.adjustmentNote) {
            var note = document.createElement('div');
            note.className = 'injury-card__adjustment-note';
            note.textContent = matchContext.adjustmentNote;
            container.appendChild(note);
        }
    }

    /**
     * Fetch injury data from the API and render into the given container.
     */
    function fetchAndRender(containerId, fixtureId, homeTeamId, awayTeamId) {
        var container = document.getElementById(containerId);
        if (!container) return;

        // Show loading
        container.innerHTML = '<div class="injury-card injury-card--loading">' +
            '<div class="injury-card__spinner"></div>' +
            '<p>Loading injury data…</p></div>';

        var url = '/api/injuries/fixture/' + fixtureId +
            '?homeTeamId=' + (homeTeamId || 0) +
            '&awayTeamId=' + (awayTeamId || 0);

        fetch(url)
            .then(function(res) { return res.json(); })
            .then(function(data) {
                renderMatchContext(container, data);
            })
            .catch(function(err) {
                console.warn('Failed to load injury data:', err);
                container.innerHTML = '<div class="injury-card injury-card--no-data">' +
                    '<p class="injury-card__placeholder-text">Injury data unavailable</p></div>';
            });
    }

    // ── Inject CSS ──────────────────────────────────────────────

    var style = document.createElement('style');
    style.textContent = [
        '.injury-card { background: var(--card-bg, #1e1e2e); border-radius: 12px; padding: 16px; margin-bottom: 12px; border: 1px solid var(--border-color, #2a2a3e); }',
        '.injury-card--no-data { opacity: 0.6; }',
        '.injury-card--loading { text-align: center; padding: 32px; color: var(--text-muted, #888); }',
        '.injury-card__spinner { width: 24px; height: 24px; border: 3px solid var(--border-color, #2a2a3e); border-top-color: var(--accent, #6c5ce7); border-radius: 50%; animation: injury-spin 0.8s linear infinite; margin: 0 auto 8px; }',
        '@keyframes injury-spin { to { transform: rotate(360deg); } }',
        '.injury-card__header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }',
        '.injury-card__title { font-weight: 600; font-size: 1rem; color: var(--text-primary, #fff); }',
        '.injury-card__badge { font-size: 0.75rem; padding: 4px 10px; border-radius: 20px; font-weight: 600; }',
        '.injury-card__badge--green { background: rgba(0, 200, 83, 0.15); color: #00c853; }',
        '.injury-card__badge--amber { background: rgba(255, 171, 0, 0.15); color: #ffab00; }',
        '.injury-card__badge--red { background: rgba(255, 61, 0, 0.15); color: #ff3d00; }',
        '.injury-card__body { font-size: 0.875rem; color: var(--text-secondary, #ccc); }',
        '.injury-card__summary { margin-bottom: 10px; font-style: italic; color: var(--text-muted, #999); }',
        '.injury-card__section-label { font-weight: 600; margin: 10px 0 4px; color: var(--text-primary, #fff); font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.5px; }',
        '.injury-card__section-label--suspended { color: #ff3d00; }',
        '.injury-card__player-row { display: flex; justify-content: space-between; align-items: center; padding: 4px 0; border-bottom: 1px solid var(--border-color, #2a2a3e); }',
        '.injury-card__player-name { color: var(--text-primary, #fff); }',
        '.injury-card__player-name--key { font-weight: 700; }',
        '.injury-card__pill { font-size: 0.7rem; padding: 2px 8px; border-radius: 12px; font-weight: 500; }',
        '.injury-card__pill--injury { background: rgba(255, 171, 0, 0.15); color: #ffab00; }',
        '.injury-card__pill--suspended { background: rgba(255, 61, 0, 0.2); color: #ff3d00; }',
        '.injury-card__no-players { color: var(--text-muted, #666); font-size: 0.8rem; padding: 4px 0; }',
        '.injury-card__placeholder-text { text-align: center; color: var(--text-muted, #666); padding: 16px 0; }',
        '.injury-card__impact-section { margin-top: 12px; }',
        '.injury-card__impact-bar-wrapper { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }',
        '.injury-card__impact-label { min-width: 100px; font-size: 0.8rem; color: var(--text-secondary, #aaa); }',
        '.injury-card__impact-bar-outer { flex: 1; height: 8px; background: var(--border-color, #2a2a3e); border-radius: 4px; overflow: hidden; }',
        '.injury-card__impact-bar-inner { height: 100%; border-radius: 4px; width: 0; transition: width 0.6s ease-out; }',
        '.injury-card__impact-bar-inner--low { background: #00c853; }',
        '.injury-card__impact-bar-inner--medium { background: #ffab00; }',
        '.injury-card__impact-bar-inner--high { background: #ff3d00; }',
        '.injury-card__impact-value { font-size: 0.8rem; min-width: 40px; text-align: right; color: var(--text-secondary, #aaa); }',
        '.injury-card__match-wrapper { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }',
        '@media (max-width: 768px) { .injury-card__match-wrapper { grid-template-columns: 1fr; } }',
        '.injury-card__adjustment-note { text-align: center; padding: 8px; margin-top: 8px; font-size: 0.85rem; color: var(--text-muted, #999); background: var(--card-bg, #1e1e2e); border-radius: 8px; border: 1px dashed var(--border-color, #2a2a3e); }'
    ].join('\n');
    document.head.appendChild(style);

    // ── Export ───────────────────────────────────────────────────

    window.InjuryCard = {
        render: render,
        renderMatchContext: renderMatchContext,
        fetchAndRender: fetchAndRender
    };
})();

