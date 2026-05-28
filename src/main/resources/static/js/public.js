let scanner = null;
let scannerRunning = false;

function escapeHtml(value) {
    return String(value || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function formatDate(value) {
    if (!value) return 'Pending';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleDateString('en-PH', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
    });
}

function normalizeStatus(status) {
    return String(status || '').toLowerCase();
}

function statusClass(status) {
    const clean = normalizeStatus(status);
    if (clean.includes('reject')) return 'rejected';
    if (clean.includes('resubmission')) return 'review';
    if (clean.includes('review') || clean.includes('process')) return 'review';
    if (clean.includes('verified') || clean.includes('received') || clean.includes('approved')) return 'active';
    return 'pending';
}

function showResult() {
    const resultCard = document.getElementById('statusResult');
    resultCard.classList.remove('hidden');
    resultCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function renderError(message) {
    document.getElementById('resultTitle').textContent = 'Document Status';
    document.getElementById('resultSubtitle').textContent = 'No document status can be displayed.';
    document.getElementById('timeline').innerHTML = `<div class="error-state">${escapeHtml(message)}</div>`;
    document.getElementById('summaryPanel').innerHTML = '';
    showResult();
}

function renderLoading() {
    const resultCard = document.getElementById('statusResult');
    const timeline = document.getElementById('timeline');
    document.getElementById('resultTitle').textContent = 'Document Status';
    document.getElementById('resultSubtitle').textContent = 'Checking document tracking number...';
    document.getElementById('summaryPanel').innerHTML = '';
    resultCard.classList.remove('hidden');
    timeline.innerHTML = '<p class="loading-text">Verifying document status...</p>';
}

function buildTimeline(data) {
    const status = data.currentStatus || 'Pending';
    const clean = normalizeStatus(status);
    const uploaded = data.uploadedDate;
    const processed = data.processedDate;

    const steps = [
        {
            title: 'Document Received',
            date: formatDate(uploaded),
            description: 'The document was logged in the PDTS system.',
            className: uploaded ? 'active' : 'pending'
        }
    ];

    if (clean.includes('pending')) {
        steps.push({
            title: 'Awaiting Registrar Review',
            date: 'Pending',
            description: 'The Registrar has not completed the review yet.',
            className: 'pending'
        });
    } else if (clean.includes('under review')) {
        steps.push({
            title: 'Processing Evaluation',
            date: formatDate(processed),
            description: 'The document is currently being evaluated.',
            className: 'review'
        });
    } else if (clean.includes('verified') || clean.includes('received')) {
        steps.push({
            title: 'Verified / Received',
            date: formatDate(processed),
            description: 'The document has been accepted by the Registrar\'s Office.',
            className: 'active'
        });
    } else if (clean.includes('resubmission')) {
        steps.push({
            title: 'For Resubmission',
            date: formatDate(processed),
            description: 'Please check your registered email or contact the Registrar\'s Office for instructions.',
            className: 'review'
        });
    } else if (clean.includes('reject')) {
        steps.push({
            title: 'Document Not Accepted',
            date: formatDate(processed),
            description: 'Please check your registered email or contact the Registrar\'s Office for the next step.',
            className: 'rejected'
        });
    } else {
        steps.push({
            title: 'Status Updated',
            date: formatDate(processed),
            description: 'The document status has been updated by the Registrar\'s Office.',
            className: statusClass(status)
        });
    }

    return steps.map(step => `
        <div class="step ${step.className}">
            <strong>${escapeHtml(step.title)}</strong>
            <small class="step-date">${escapeHtml(step.date)}</small>
            <small class="step-notes">${escapeHtml(step.description)}</small>
        </div>
    `).join('');
}


function buildDocumentList(data) {
    const documents = Array.isArray(data.documents) ? data.documents : [];
    if (documents.length <= 1) return '';

    const rows = documents.map(doc => `
        <tr>
            <td>${escapeHtml(doc.documentType || 'Document')}</td>
            <td><strong class="public-status ${statusClass(doc.currentStatus)}">${escapeHtml(doc.currentStatus || 'Pending')}</strong></td>
            <td>${escapeHtml(formatDate(doc.processedDate || doc.uploadedDate || ''))}</td>
        </tr>
    `).join('');

    return `
        <div class="privacy-note">
            <strong>Documents in this submission</strong>
            <table style="width:100%;border-collapse:collapse;margin-top:10px;">
                <thead>
                    <tr>
                        <th style="text-align:left;padding:8px;border-bottom:1px solid #eee;">Document</th>
                        <th style="text-align:left;padding:8px;border-bottom:1px solid #eee;">Status</th>
                        <th style="text-align:left;padding:8px;border-bottom:1px solid #eee;">Latest Update</th>
                    </tr>
                </thead>
                <tbody>${rows}</tbody>
            </table>
        </div>
    `;
}

function renderResult(data) {
    const status = data.currentStatus || 'Pending';
    const latestDate = data.processedDate || data.uploadedDate || '';

    document.getElementById('resultTitle').textContent = 'Document Status';
    document.getElementById('resultSubtitle').textContent = 'Applicant identity and application details are hidden for privacy.';

    document.getElementById('summaryPanel').innerHTML = `
        <div class="summary-item">
            <span>Tracking Number</span>
            <strong>${escapeHtml(data.trackingNumber || '—')}</strong>
        </div>
        <div class="summary-item">
            <span>Current Status</span>
            <strong class="public-status ${statusClass(status)}">${escapeHtml(status)}</strong>
        </div>
        <div class="summary-item">
            <span>Latest Update</span>
            <strong>${escapeHtml(formatDate(latestDate))}</strong>
        </div>
        ${Number(data.documentCount || 0) > 1 ? `
        <div class="summary-item">
            <span>Documents Submitted</span>
            <strong>${escapeHtml(data.documentCount)}</strong>
        </div>` : ''}
    `;

    document.getElementById('timeline').innerHTML = `
        <div class="privacy-note">${escapeHtml(data.publicMessage || 'Only document status is shown on this public page.')}</div>
        ${buildDocumentList(data)}
        ${buildTimeline(data)}
    `;

    showResult();
}

async function handleTracking() {
    const trackNum = document.getElementById('trackingNumber').value.trim();
    if (!trackNum) return alert('Please input or scan a document tracking number.');

    renderLoading();

    try {
        const response = await fetch('/api/portal/public-track?' + new URLSearchParams({ trackingNumber: trackNum }));
        const data = await response.json().catch(() => ({}));
        if (!response.ok || data.success === false) {
            renderError(data.error || 'No document record found for this tracking number.');
            return;
        }
        renderResult(data);
    } catch (error) {
        renderError('Could not connect to the local PDTS server.');
    }
}

async function toggleScanner() {
    const reader = document.getElementById('reader');
    const scanBtn = document.getElementById('scanBtn');

    if (typeof Html5Qrcode === 'undefined') {
        alert('QR scanner library is not available. You may still enter the tracking number manually.');
        return;
    }

    if (!scanner) scanner = new Html5Qrcode('reader');

    if (scannerRunning) {
        await scanner.stop();
        scannerRunning = false;
        reader.classList.add('hidden');
        scanBtn.textContent = 'Scan QR Code';
        return;
    }

    reader.classList.remove('hidden');
    scanBtn.textContent = 'Stop Scanner';

    scanner.start({ facingMode: 'environment' }, { fps: 10, qrbox: 250 }, async (text) => {
        document.getElementById('trackingNumber').value = text;
        await handleTracking();
        await scanner.stop();
        scannerRunning = false;
        reader.classList.add('hidden');
        scanBtn.textContent = 'Scan QR Code';
    }).then(() => {
        scannerRunning = true;
    }).catch(() => {
        scannerRunning = false;
        reader.classList.add('hidden');
        scanBtn.textContent = 'Scan QR Code';
        alert('Camera access denied.');
    });
}

document.addEventListener('DOMContentLoaded', () => {
    const input = document.getElementById('trackingNumber');
    if (input) {
        input.addEventListener('keydown', event => {
            if (event.key === 'Enter') handleTracking();
        });
    }

    const params = new URLSearchParams(window.location.search);
    const trackingFromLink = params.get('trackingNumber') || params.get('trackingNo');

    if (input && trackingFromLink) {
        input.value = trackingFromLink;
        handleTracking();
    }
});
