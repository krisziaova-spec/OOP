let scanner = null;
let scannerRunning = false;

function qs(id) {
    return document.getElementById(id);
}

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

function statusClass(status) {
    const clean = String(status || '').toLowerCase();
    if (clean.includes('verified') || clean.includes('received') || clean.includes('approved')) return 'active';
    if (clean.includes('reject')) return 'rejected';
    if (clean.includes('review')) return 'review';
    return 'pending';
}

function showResult() {
    qs('statusResult').classList.remove('hidden');
    qs('statusResult').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function renderError(message) {
    qs('applicationBadge').textContent = 'Not Found';
    qs('resultTitle').textContent = 'Unable to Track Document';
    qs('applicantSummary').innerHTML = '';
    qs('timeline').innerHTML = '<div class="error-state"><strong>Error:</strong> ' + escapeHtml(message) + '</div>';
    showResult();
}

function renderLoading() {
    qs('applicationBadge').textContent = 'Checking';
    qs('resultTitle').textContent = 'Checking Tracking Number';
    qs('applicantSummary').innerHTML = '';
    qs('timeline').innerHTML = '<div class="empty-state">Verifying tracking number...</div>';
    qs('statusResult').classList.remove('hidden');
}

function renderResult(data) {
    qs('applicationBadge').textContent = data.applicationStatusName || 'Status Available';
    qs('resultTitle').textContent = data.applicantFullName || 'Document Status';

    qs('applicantSummary').innerHTML = [
        ['Application Reference', data.applicationReferenceNumber],
        ['Program', data.programName],
        ['Campus', data.campusName],
        ['Semester / AY', [data.applicationSemester, data.applicationAcademicYear].filter(Boolean).join(' · ')]
    ].map(([label, value]) => `
        <div class="summary-item">
            <span class="summary-label">${escapeHtml(label)}</span>
            <span class="summary-value">${escapeHtml(value || '—')}</span>
        </div>
    `).join('');

    const documents = Array.isArray(data.documents) ? data.documents : [];
    if (documents.length === 0) {
        qs('timeline').innerHTML = '<div class="empty-state">No document records found for this application.</div>';
        showResult();
        return;
    }

    qs('timeline').innerHTML = documents.map(doc => {
        const status = doc.requirementStatusName || 'Pending';
        const notes = doc.rejectionReasonDescription || doc.resubmissionNotes || '';
        return `
            <div class="step ${statusClass(status)}">
                <span class="step-title">${escapeHtml(doc.requirementTypeName || 'Document')}</span>
                <span class="step-tracking">Tracking No.: ${escapeHtml(doc.requirementTrackingNo || '—')}</span>
                <span class="step-date">Status: ${escapeHtml(status)} · Uploaded: ${escapeHtml(formatDate(doc.requirementUploadDate))} · Processed: ${escapeHtml(formatDate(doc.requirementProcessedAt))}</span>
                ${notes ? '<span class="step-notes">' + escapeHtml(notes) + '</span>' : ''}
            </div>
        `;
    }).join('');

    showResult();
}

async function handleTracking() {
    const trackNum = qs('trackingNumber').value.trim();
    if (!trackNum) {
        alert('Please input or scan a tracking/reference number.');
        return;
    }

    renderLoading();

    try {
        const response = await fetch('/api/portal/public-track?' + new URLSearchParams({ trackingNumber: trackNum }));
        const data = await response.json().catch(() => ({}));

        if (!response.ok || data.success === false) {
            renderError(data.error || 'No record found for this tracking number.');
            return;
        }

        renderResult(data);
    } catch (error) {
        renderError('Could not connect to the local PDTS server.');
    }
}

async function toggleScanner() {
    const reader = qs('reader');

    if (typeof Html5Qrcode === 'undefined') {
        alert('QR scanner library is not available. You may still enter the tracking number manually.');
        return;
    }

    if (!scanner) scanner = new Html5Qrcode('reader');

    if (scannerRunning) {
        await scanner.stop();
        scannerRunning = false;
        reader.classList.add('hidden');
        qs('scanBtn').textContent = 'Scan QR Code';
        return;
    }

    reader.classList.remove('hidden');
    qs('scanBtn').textContent = 'Stop Scanner';

    scanner.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: 250 },
        async (text) => {
            qs('trackingNumber').value = text;
            await handleTracking();
            await scanner.stop();
            scannerRunning = false;
            reader.classList.add('hidden');
            qs('scanBtn').textContent = 'Scan QR Code';
        }
    ).then(() => {
        scannerRunning = true;
    }).catch(() => {
        scannerRunning = false;
        reader.classList.add('hidden');
        qs('scanBtn').textContent = 'Scan QR Code';
        alert('Camera access denied or unavailable.');
    });
}

async function handleQRUpload(event) {
    const file = event.target.files[0];
    if (!file) return;

    if (typeof Html5Qrcode === 'undefined') {
        alert('QR scanner library is not available.');
        return;
    }

    try {
        const qrScanner = new Html5Qrcode('reader');
        const text = await qrScanner.scanFile(file, true);
        qs('trackingNumber').value = text;
        await handleTracking();
    } catch (error) {
        alert('No valid QR code found in image.');
    } finally {
        event.target.value = '';
    }
}

document.addEventListener('DOMContentLoaded', () => {
    qs('trackBtn').addEventListener('click', handleTracking);
    qs('scanBtn').addEventListener('click', toggleScanner);
    qs('qrUpload').addEventListener('change', handleQRUpload);
    qs('trackingNumber').addEventListener('keydown', event => {
        if (event.key === 'Enter') handleTracking();
    });
});
