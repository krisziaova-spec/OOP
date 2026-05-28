document.addEventListener('DOMContentLoaded', () => {
    const search = document.querySelector('[data-table-search]');
    const type = document.querySelector('[data-table-type]');
    const table = document.querySelector('[data-filterable-table]');

    const applyTableFilter = () => {
        if (!table) return;
        const q = (search?.value || '').toLowerCase().trim();
        const docType = (type?.value || '').toLowerCase().trim();
        table.querySelectorAll('tbody tr').forEach(row => {
            const text = row.innerText.toLowerCase();
            const matchesSearch = !q || text.includes(q);
            const matchesType = !docType || text.includes(docType);
            row.style.display = matchesSearch && matchesType ? '' : 'none';
        });
    };

    search?.addEventListener('input', applyTableFilter);
    type?.addEventListener('change', applyTableFilter);

    document.querySelectorAll('.role-card input[type="radio"]').forEach(input => {
        input.addEventListener('change', () => {
            document.querySelectorAll('.role-card').forEach(card => card.classList.remove('selected'));
            input.closest('.role-card')?.classList.add('selected');
        });
    });

    const feed = document.querySelector('[data-live-feed]');
    const endpoint = feed?.dataset?.endpoint;
    const refreshNote = document.querySelector('[data-live-refresh-time]');

    let liveFeedItems = [];
    let rollingIndex = 0;

    const badgeClass = (feedClass) => {
        if (feedClass === 'green') return 'green';
        if (feedClass === 'red') return 'red';
        if (feedClass === 'gray') return 'gray';
        return 'gold';
    };

    const escapeHtml = (value) => String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');

    const nowLabel = () => new Date().toLocaleTimeString('en-US', {
        hour: 'numeric',
        minute: '2-digit',
        hour12: true
    });

    const visibleFeedItems = () => {
        if (!liveFeedItems.length) return [];
        const limit = Math.min(5, liveFeedItems.length);
        const visible = [];
        for (let i = 0; i < limit; i += 1) {
            visible.push(liveFeedItems[(rollingIndex + i) % liveFeedItems.length]);
        }
        return visible;
    };

    const renderFeed = () => {
        if (!feed) return;
        const items = visibleFeedItems();

        feed.innerHTML = items.length ? items.map((item, index) => `
            <div class="live-feed-item ${index === 0 ? 'new-update' : ''}">
                <span class="feed-time">${escapeHtml(item.feed_time_display || 'Now')}</span>
                <span class="feed-text">${escapeHtml(item.feed_text || 'Registrar update.')}</span>
                <span class="feed-tag ${badgeClass(item.feed_class)}">${escapeHtml(item.feed_tag || 'Update')}</span>
            </div>
        `).join('') : `
            <div class="live-feed-item">
                <span class="feed-time">Now</span>
                <span class="feed-text">No registrar movement yet. New submissions, overdue items, and status changes will appear here.</span>
                <span class="feed-tag gray">Info</span>
            </div>
        `;
    };

    const setNote = (message) => {
        if (refreshNote) refreshNote.textContent = message;
    };

    const refreshLiveFeed = async () => {
        if (!feed || !endpoint) return;
        try {
            const response = await fetch(`${endpoint}?t=${Date.now()}`, {
                headers: { 'Accept': 'application/json' },
                cache: 'no-store'
            });
            if (!response.ok) return;
            const data = await response.json();
            liveFeedItems = Array.isArray(data) ? data : [];
            if (rollingIndex >= liveFeedItems.length) rollingIndex = 0;
            renderFeed();
         setNote(`refreshed ${nowLabel()}`);
} catch (_) {
    setNote('refresh failed');
}
    };

    const rollLiveFeed = () => {
        if (!feed || liveFeedItems.length <= 1) return;
        rollingIndex = (rollingIndex + 1) % liveFeedItems.length;
        renderFeed();
    };

    if (feed && endpoint) {
        refreshLiveFeed();
        setInterval(rollLiveFeed, 3000);
        setInterval(refreshLiveFeed, 30000);
    }
});
