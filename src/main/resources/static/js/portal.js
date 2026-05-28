// Path: src/main/resources/static/js/portal.js

(function () {
    'use strict';

    let currentRows = [];

    // ── Utility ─────────────────────────────────────────────────
    function qs(id) { return document.getElementById(id); }

    function formatDate(iso) {
        if (!iso) return '—';
        const d = new Date(iso);
        if (Number.isNaN(d.getTime())) return String(iso);
        return d.toLocaleDateString('en-PH', { month: 'short', day: 'numeric', year: 'numeric' });
    }

    function getQueryParam(name) {
        return new URLSearchParams(window.location.search).get(name);
    }

    // ── Show / hide sections ─────────────────────────────────────
    function showContent() {
        qs('portal-loading').style.display = 'none';
        qs('portal-error').style.display   = 'none';
        qs('portal-content').style.display = 'block';
    }

    function showError(msg) {
        qs('portal-loading').style.display = 'none';
        qs('portal-content').style.display = 'none';
        qs('portal-error').style.display   = 'flex';
        const em = qs('portal-error-msg');
        if (em) em.textContent = msg || 'An unexpected error occurred.';
    }

    // ── Render applicant summary ─────────────────────────────────
    function renderSummary(rows) {
        if (!rows || rows.length === 0) return;
        const first = rows[0];

        const name = first.applicantFullName || '—';
        qs('summary-name').textContent     = name;
        qs('summary-initial').textContent  = name.charAt(0).toUpperCase();
        qs('summary-program').textContent  = first.programName || '—';
        qs('summary-ref').textContent      = first.applicationReferenceNumber || '—';
        qs('summary-app-status').textContent = first.applicationStatusName || '—';

        const campusSem = [first.campusName, first.applicationSemester + ' ' + (first.applicationAcademicYear || '')]
            .filter(Boolean).join(' · ');
        qs('summary-campus-sem').textContent = campusSem;
    }

    // ── Render progress bar ──────────────────────────────────────
    function renderProgress(rows) {
        const total    = rows.length;
        const verified = rows.filter(function (r) { return r.requirementStatusName === 'Verified/Received'; }).length;
        const pending  = rows.filter(function (r) { return r.requirementStatusName === 'Pending'; }).length;
        const rejected = rows.filter(function (r) { return r.requirementStatusName === 'Rejected'; }).length;
        const review   = rows.filter(function (r) { return r.requirementStatusName === 'Under Review'; }).length;
        const resub    = rows.filter(function (r) { return r.requirementStatusName === 'For Resubmission'; }).length;

        const pct = total > 0 ? Math.round((verified / total) * 100) : 0;

        qs('progress-pct').textContent   = pct + '%';
        qs('count-verified').textContent = verified + ' Verified';
        qs('count-pending').textContent  = pending  + ' Pending';
        qs('count-rejected').textContent = rejected + ' Rejected';
        qs('count-review').textContent   = review   + ' Under Review';
        qs('count-resub').textContent    = resub    + ' For Resubmission';

        setTimeout(function () {
            qs('progress-fill').style.width = pct + '%';
        }, 100);
    }

    // ── Render documents table ───────────────────────────────────
    function renderDocuments(rows) {
        const tbody = qs('docs-tbody');
        if (!tbody) return;

        if (rows.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;color:#aaa;padding:40px;font-style:italic;">No documents found for this application.</td></tr>';
            return;
        }

        tbody.innerHTML = rows.map(function (doc, index) {
            const statusColor  = doc.requirementStatusColor || '#888';
            const statusName   = doc.requirementStatusName  || 'Unknown';
            const uploadDate   = formatDate(doc.requirementUploadDate);
            const processedAt  = formatDate(doc.requirementProcessedAt);

            let notesHtml = '<span style="color:#ccc;">—</span>';

            if (statusName === 'Rejected' && doc.rejectionReasonDescription) {
                notesHtml = '<div class="doc-notes rejection">' +
                    '<strong>Reason:</strong> ' + escapeHtml(doc.rejectionReasonDescription) +
                    '</div>';
            } else if (statusName === 'For Resubmission' && doc.resubmissionNotes) {
                notesHtml = '<div class="doc-notes resubmission">' +
                    '<strong>Guidance:</strong> ' + escapeHtml(doc.resubmissionNotes) +
                    '</div>';
            }

            return '<tr>' +
                '<td><div class="doc-num">' + (index + 1) + '</div></td>' +
                '<td><span class="doc-type-name">' + escapeHtml(doc.requirementTypeName || '—') + '</span></td>' +
                '<td><span class="doc-tracking">' + escapeHtml(doc.requirementTrackingNo || '—') + '</span></td>' +
                '<td>' +
                    '<span class="doc-status-badge" style="background:' + statusColor + '22;color:' + statusColor + ';border:1.5px solid ' + statusColor + '55;">' +
                        escapeHtml(statusName) +
                    '</span>' +
                '</td>' +
                '<td class="doc-date">' + uploadDate + '</td>' +
                '<td class="doc-date">' + processedAt + '</td>' +
                '<td>' + notesHtml + '</td>' +
            '</tr>';
        }).join('');
    }

    function renderResubmissionForm(rows) {
        const section = qs('resubmission-section');
        const tbody = qs('resubmission-tbody');
        const message = qs('resubmission-message');
        if (!section || !tbody) return;

        const items = rows.filter(function (doc) {
            return doc.requirementId &&
                (doc.requirementStatusName === 'Rejected' || doc.requirementStatusName === 'For Resubmission');
        });

        if (!items.length) {
            section.style.display = 'none';
            return;
        }

        section.style.display = 'block';
        if (message) message.textContent = '';

        tbody.innerHTML = items.map(function (doc) {
            const id = String(doc.requirementId);
            const notes = doc.requirementStatusName === 'Rejected'
                ? (doc.rejectionReasonDescription || '')
                : (doc.resubmissionNotes || 'Please upload the corrected file.');

            return '<tr>' +
                '<td><input type="checkbox" name="requirementIds" value="' + escapeHtml(id) + '"></td>' +
                '<td><span class="doc-type-name">' + escapeHtml(doc.requirementTypeName || 'Document') + '</span></td>' +
                '<td>' + escapeHtml(doc.requirementStatusName || 'For Resubmission') + '</td>' +
                '<td>' + (notes ? escapeHtml(notes) : '<span style="color:#ccc;">—</span>') + '</td>' +
                '<td><input type="file" name="file_' + escapeHtml(id) + '" accept="image/*,.pdf" disabled></td>' +
            '</tr>';
        }).join('');

        tbody.querySelectorAll('input[type="checkbox"]').forEach(function (checkbox) {
            checkbox.addEventListener('change', function () {
                const fileInput = checkbox.closest('tr').querySelector('input[type="file"]');
                fileInput.disabled = !checkbox.checked;
                fileInput.required = checkbox.checked;
                if (!checkbox.checked) fileInput.value = '';
            });
        });
    }

    async function handleResubmissionSubmit(event) {
        event.preventDefault();

        const form = qs('resubmission-form');
        const message = qs('resubmission-message');
        const selected = Array.from(form.querySelectorAll('input[name="requirementIds"]:checked'));

        if (!selected.length) {
            alert('Please select at least one document to resubmit.');
            return;
        }

        const missingFile = selected.some(function (checkbox) {
            const fileInput = checkbox.closest('tr').querySelector('input[type="file"]');
            return !fileInput || !fileInput.files || fileInput.files.length === 0;
        });

        if (missingFile) {
            alert('Please choose a corrected file for each selected document.');
            return;
        }

        const refNo = getQueryParam('ref');
        const token = getQueryParam('token');
        const formData = new FormData();
        formData.append('ref', refNo || '');
        formData.append('token', token || '');

        selected.forEach(function (checkbox) {
            const id = checkbox.value;
            const fileInput = checkbox.closest('tr').querySelector('input[type="file"]');
            formData.append('requirementIds', id);
            formData.append('file_' + id, fileInput.files[0]);
        });

        if (message) message.textContent = 'Uploading corrected document(s)...';

        try {
            const resp = await fetch('/api/portal/resubmit', {
                method: 'POST',
                body: formData
            });
            const data = await resp.json().catch(function () { return {}; });

            if (!resp.ok || data.success === false) {
                if (message) message.textContent = data.error || 'Resubmission failed.';
                alert(data.error || 'Resubmission failed.');
                return;
            }

            if (message) message.textContent = data.message || 'Resubmission received.';
            alert(data.message || 'Resubmission received.');
            await loadPortalData();

        } catch (err) {
            if (message) message.textContent = 'Could not connect to the server.';
            alert('Could not connect to the server.');
        }
    }

    // ── HTML escape ──────────────────────────────────────────────
    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    // ── Load portal data ─────────────────────────────────────────
    async function loadPortalData() {
        const refNo = getQueryParam('ref');
        const token = getQueryParam('token');

        if (!refNo || !token) {
            showError('Missing reference number or access token. Return to the portal and try again.');
            return;
        }

        try {
            const resp = await fetch('/api/portal/status?' +
                new URLSearchParams({ ref: refNo, token: token }));

            if (!resp.ok) {
                const data = await resp.json().catch(function () { return {}; });
                showError(data.error || 'Access denied. Please check your reference number and token.');
                return;
            }

            currentRows = await resp.json();

            renderSummary(currentRows);
            renderProgress(currentRows);
            renderDocuments(currentRows);
            renderResubmissionForm(currentRows);
            showContent();

        } catch (err) {
            showError('Could not connect to the server. Please check your internet connection.');
        }
    }

    // ── Footer year ──────────────────────────────────────────────
    function setFooterYear() {
        const el = qs('footer-year');
        if (el) el.textContent = new Date().getFullYear();
    }

    // ── Init ────────────────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', function () {
        setFooterYear();
        const form = qs('resubmission-form');
        if (form) form.addEventListener('submit', handleResubmissionSubmit);
        loadPortalData();
    });

}());
