// Path: src/main/resources/static/js/login.js

(function () {
    'use strict';

    // ── Particle generator ──────────────────────────────────────
    function spawnParticles() {
        const container = document.getElementById('particles');
        if (!container) return;

        const count = 22;
        for (let i = 0; i < count; i++) {
            const p = document.createElement('div');
            p.className = 'particle';

            const size     = Math.random() * 5 + 2;
            const left     = Math.random() * 100;
            const duration = Math.random() * 18 + 10;
            const delay    = Math.random() * 15;
            const opacity  = Math.random() * 0.5 + 0.1;

            p.style.cssText = `
                width: ${size}px;
                height: ${size}px;
                left: ${left}%;
                bottom: -10px;
                animation-duration: ${duration}s;
                animation-delay: -${delay}s;
                opacity: ${opacity};
            `;
            container.appendChild(p);
        }
    }

    // ── Tab switching ───────────────────────────────────────────
    window.switchTab = function (tab) {
        const staffTab  = document.getElementById('tab-staff');
        const trackTab  = document.getElementById('tab-track');
        const staffForm = document.getElementById('form-staff');
        const trackForm = document.getElementById('form-track');

        if (tab === 'staff') {
            staffTab.classList.add('active');
            trackTab.classList.remove('active');
            staffForm.classList.remove('hidden');
            trackForm.classList.add('hidden');
        } else {
            trackTab.classList.add('active');
            staffTab.classList.remove('active');
            trackForm.classList.remove('hidden');
            staffForm.classList.add('hidden');
        }
    };

    // ── Password visibility toggle ──────────────────────────────
    window.togglePassword = function () {
        const input   = document.getElementById('password');
        const eyeShow = document.getElementById('eye-show');
        const eyeHide = document.getElementById('eye-hide');

        if (!input) return;

        if (input.type === 'password') {
            input.type = 'text';
            eyeShow.style.display = 'none';
            eyeHide.style.display = 'block';
        } else {
            input.type = 'password';
            eyeShow.style.display = 'block';
            eyeHide.style.display = 'none';
        }
    };

    // ── Token verification ──────────────────────────────────────
    window.verifyToken = async function () {
        const refInput   = document.getElementById('ref-no');
        const tokenInput = document.getElementById('token-val');
        const errorBox   = document.getElementById('track-error');
        const infoBox    = document.getElementById('track-info');
        const btn        = document.querySelector('#form-track .btn-primary');

        const refNo = refInput ? refInput.value.trim() : '';
        const token = tokenInput ? tokenInput.value.trim() : '';

        clearAlerts();

        if (!refNo || !token) {
            showError('Please fill in both the Reference Number and Access Token.');
            return;
        }

        if (!/^STU-\d{4}-\d{5}$/.test(refNo)) {
            showError('Reference number format must be STU-YYYY-NNNNN (e.g., STU-2025-00001).');
            return;
        }

        btn.classList.add('loading');
        btn.disabled = true;

        try {
            const response = await fetch('/api/portal/verify', {
                method:  'POST',
                headers: { 'Content-Type': 'application/json' },
                body:    JSON.stringify({ referenceNo: refNo, token: token })
            });

            const data = await response.json();

            if (response.ok && data.success) {
                showInfo('Token verified. Loading your document status…');
                setTimeout(() => {
                    window.location.href = '/portal/status?ref=' + encodeURIComponent(refNo)
                                         + '&token=' + encodeURIComponent(token);
                }, 900);
            } else {
                showError(data.error || 'Invalid reference number or access token.');
                btn.classList.remove('loading');
                btn.disabled = false;
            }
        } catch (err) {
            showError('Unable to connect to the server. Please try again.');
            btn.classList.remove('loading');
            btn.disabled = false;
        }
    };

    function clearAlerts() {
        const e = document.getElementById('track-error');
        const i = document.getElementById('track-info');
        if (e) e.style.display = 'none';
        if (i) i.style.display = 'none';
    }

    function showError(msg) {
        const el = document.getElementById('track-error');
        if (el) { el.textContent = msg; el.style.display = 'flex'; }
    }

    function showInfo(msg) {
        const el = document.getElementById('track-info');
        if (el) { el.textContent = msg; el.style.display = 'flex'; }
    }

    // ── Login form submit loading state ─────────────────────────
    function attachLoginLoader() {
        const form = document.querySelector('#form-staff form');
        if (!form) return;
        form.addEventListener('submit', function () {
            const btn = form.querySelector('.btn-primary');
            if (btn) btn.classList.add('loading');
        });
    }

    // ── Enter key support for token form ────────────────────────
    function attachEnterKey() {
        ['ref-no', 'token-val'].forEach(function (id) {
            const el = document.getElementById(id);
            if (el) {
                el.addEventListener('keydown', function (e) {
                    if (e.key === 'Enter') window.verifyToken();
                });
            }
        });
    }

    // ── Init ────────────────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', function () {
        spawnParticles();
        attachLoginLoader();
        attachEnterKey();

        // Auto-focus username field
        const usernameInput = document.getElementById('username');
        if (usernameInput) usernameInput.focus();
    });

}());
