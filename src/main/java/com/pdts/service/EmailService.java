package com.pdts.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

@Service
public class EmailService {

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from-email:onboarding@resend.dev}")
    private String fromEmail;

    @Value("${pdts.token-expiry-days:30}")
    private int tokenExpiryDays;

    @Async
    public void sendDocumentStatusEmail(
            String toEmail,
            String studentName,
            String docType,
            String trackingNo,
            String status,
            String rejectionReason
    ) {
        String safeEmail = Objects.requireNonNullElse(toEmail, "").trim();
        String safeName = Objects.requireNonNullElse(studentName, "Student");
        String safeDoc = Objects.requireNonNullElse(docType, "Document");
        String safeTrack = Objects.requireNonNullElse(trackingNo, "—");
        String safeStatus = Objects.requireNonNullElse(status, "Updated");
        String safeRejection = Objects.requireNonNullElse(rejectionReason, "");

        if (safeEmail.isBlank()) {
            System.err.println("[EmailService] Status email skipped: recipient email is blank.");
            return;
        }

        try {
            String htmlBody = buildStatusEmailBody(
                    safeName,
                    safeDoc,
                    safeTrack,
                    safeStatus,
                    safeRejection
            );

            sendViaResend(
                    safeEmail,
                    "[PDTS] Document Status Update — " + safeDoc,
                    htmlBody
            );

            System.out.println("[EmailService] Status email sent to: " + safeEmail);

        } catch (Exception e) {
            logEmailError("status email", safeEmail, e);
        }
    }

    @Async
    public void sendTokenEmail(
            String toEmail,
            String studentName,
            String referenceNo,
            String plainToken
    ) {
        String safeEmail = Objects.requireNonNullElse(toEmail, "").trim();
        String safeName = Objects.requireNonNullElse(studentName, "Student");
        String safeRef = Objects.requireNonNullElse(referenceNo, "—");
        String safeToken = Objects.requireNonNullElse(plainToken, "—");

        if (safeEmail.isBlank()) {
            System.err.println("[EmailService] Token email skipped: recipient email is blank.");
            return;
        }

        try {
            String htmlBody = buildTokenEmailBody(safeName, safeRef, safeToken);

            sendViaResend(
                    safeEmail,
                    "[PDTS] Your Document Tracking Access Token",
                    htmlBody
            );

            System.out.println("[EmailService] Token email sent to: " + safeEmail);

        } catch (Exception e) {
            logEmailError("token email", safeEmail, e);
        }
    }

    @Async
    public void sendDeadlineReminderEmail(
            String toEmail,
            String studentName,
            String docType,
            long daysLeft
    ) {
        String safeEmail = Objects.requireNonNullElse(toEmail, "").trim();
        String safeName = Objects.requireNonNullElse(studentName, "Student");
        String safeDoc = Objects.requireNonNullElse(docType, "Document");

        if (safeEmail.isBlank()) {
            System.err.println("[EmailService] Reminder email skipped: recipient email is blank.");
            return;
        }

        try {
            String urgency = daysLeft <= 1
                    ? "TODAY IS THE DEADLINE."
                    : daysLeft + " day(s) remaining.";

            String content =
                    "<p>Reminder: your <strong>" + escapeHtml(safeDoc) + "</strong> deadline is approaching.</p>" +
                    "<p style='font-size:1.1em;color:#c62828;font-weight:bold;'>" + escapeHtml(urgency) + "</p>" +
                    "<p>Please submit immediately to the OUS Registrar's Office.</p>";

            String htmlBody = buildShell(
                    "Dear <strong>" + escapeHtml(safeName) + "</strong>,",
                    content
            );

            sendViaResend(
                    safeEmail,
                    "[PDTS] Document Submission Reminder — " + daysLeft + " day(s) left",
                    htmlBody
            );

            System.out.println("[EmailService] Reminder email sent to: " + safeEmail);

        } catch (Exception e) {
            logEmailError("reminder email", safeEmail, e);
        }
    }

    public void sendManualEmail(
            String toEmail,
            String subject,
            String body,
            String remarks
    ) {
        String safeEmail = Objects.requireNonNullElse(toEmail, "").trim();
        String safeSubject = Objects.requireNonNullElse(subject, "[PDTS] Email Notification").trim();
        String safeBody = Objects.requireNonNullElse(body, "").trim();
        String safeRemarks = Objects.requireNonNullElse(remarks, "").trim();

        if (safeEmail.isBlank()) {
            throw new IllegalArgumentException("Recipient email is required.");
        }

        String remarksBlock = safeRemarks.isBlank()
                ? ""
                : "<div style='background:#f9f9f9;border-left:4px solid #8B0000;padding:12px;margin-top:20px;'>" +
                  "<strong>Remarks:</strong><br>" +
                  escapeHtml(safeRemarks).replace("\n", "<br>") +
                  "</div>";

        String htmlBody = buildShell(
                "",
                "<div style='line-height:1.6;'>" +
                escapeHtml(safeBody).replace("\n", "<br>") +
                "</div>" +
                remarksBlock
        );

        sendViaResend(safeEmail, safeSubject, htmlBody);

        System.out.println("[EmailService] Manual email sent to: " + safeEmail);
    }

    private void sendViaResend(String toEmail, String subject, String htmlBody) {
        validateResendConfig();

        Map<String, Object> payload = Map.of(
                "from", fromEmail,
                "to", List.of(toEmail),
                "subject", subject,
                "html", htmlBody
        );

        try {
            String response = RestClient.create()
                    .post()
                    .uri("https://api.resend.com/emails")
                    .header("Authorization", "Bearer " + resendApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            System.out.println("[EmailService] Resend response: " + response);

        } catch (HttpStatusCodeException e) {
            throw new RuntimeException(
                    "Resend API error " + e.getStatusCode() + ": " + e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to send email through Resend API: " + e.getMessage(),
                    e
            );
        }
    }

    private void validateResendConfig() {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new IllegalStateException("RESEND_API_KEY is missing in Render Environment Variables.");
        }

        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("RESEND_FROM_EMAIL is missing in Render Environment Variables.");
        }
    }

    private void logEmailError(String emailType, String recipient, Exception e) {
        String message = e.getMessage();

        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }

        System.err.println("[EmailService] Failed to send " + emailType + " to " + recipient + ": " + message);
        e.printStackTrace();
    }

    private String buildStatusEmailBody(
            String name,
            String docType,
            String trackingNo,
            String status,
            String rejectionReason
    ) {
        String statusColor = getStatusColor(status);

        String rejectionBlock = "";
        if (rejectionReason != null && !rejectionReason.isBlank()) {
            rejectionBlock =
                    "<div style='background:#fff8f8;border-left:4px solid #dc3545;padding:12px;margin:16px 0;'>" +
                    "<strong>Rejection Reason:</strong><br>" +
                    escapeHtml(rejectionReason) +
                    "</div>";
        }

        String content =
                "<p>Your document status has been updated:</p>" +

                "<table style='width:100%;border-collapse:collapse;margin:16px 0;'>" +

                "<tr>" +
                "<td style='padding:8px;background:#f5f5f5;width:40%;'><strong>Document</strong></td>" +
                "<td style='padding:8px;'>" + escapeHtml(docType) + "</td>" +
                "</tr>" +

                "<tr>" +
                "<td style='padding:8px;background:#f5f5f5;'><strong>Tracking No.</strong></td>" +
                "<td style='padding:8px;font-family:monospace;'>" + escapeHtml(trackingNo) + "</td>" +
                "</tr>" +

                "<tr>" +
                "<td style='padding:8px;background:#f5f5f5;'><strong>Status</strong></td>" +
                "<td style='padding:8px;'>" +
                "<span style='background:" + statusColor + ";color:#fff;padding:4px 10px;border-radius:4px;font-size:.9em;'>" +
                escapeHtml(status) +
                "</span>" +
                "</td>" +
                "</tr>" +

                "</table>" +

                rejectionBlock +

                "<p>Visit the PUP OUS Document Status Portal to view your full status.</p>";

        return buildShell("Dear <strong>" + escapeHtml(name) + "</strong>,", content);
    }

    private String buildTokenEmailBody(
            String name,
            String referenceNo,
            String plainToken
    ) {
        String content =
                "<p>Your application has been received. Use the details below to track your documents:</p>" +

                "<div style='background:#f9f9f9;border:1px solid #ddd;border-radius:8px;padding:20px;margin:16px 0;text-align:center;'>" +

                "<p style='margin:0 0 8px;color:#555;'>Application Reference Number</p>" +
                "<p style='font-size:1.4em;font-weight:bold;font-family:monospace;color:#333;margin:0 0 16px;'>" +
                escapeHtml(referenceNo) +
                "</p>" +

                "<p style='margin:0 0 8px;color:#555;'>Access Token</p>" +
                "<p style='font-size:1.1em;font-weight:bold;font-family:monospace;color:#8B0000;margin:0;word-break:break-all;'>" +
                escapeHtml(plainToken) +
                "</p>" +

                "</div>" +

                "<p><strong>Important:</strong> Keep this token private. It expires in " + tokenExpiryDays + " days.</p>";

        return buildShell("Dear <strong>" + escapeHtml(name) + "</strong>,", content);
    }

    private String buildShell(String greeting, String content) {
        String greetingBlock = greeting == null || greeting.isBlank()
                ? ""
                : "<p>" + greeting + "</p>";

        return "<div style='font-family:Arial,sans-serif;max-width:600px;margin:auto;'>" +

                "<div style='background:#8B0000;padding:20px;text-align:center;'>" +
                "<h2 style='color:#fff;margin:0;'>PUP Open University System</h2>" +
                "<p style='color:#ffcdd2;margin:4px 0 0;'>Office of the University Registrar — PDTS</p>" +
                "</div>" +

                "<div style='padding:24px;'>" +
                greetingBlock +
                content +
                "<br>" +
                "<p style='color:#555;font-size:.9em;'>PUP OUS — Document Tracking System</p>" +
                "</div>" +

                "</div>";
    }

    private String getStatusColor(String status) {
        return switch (status) {
            case "Verified/Received" -> "#28a745";
            case "Rejected" -> "#dc3545";
            case "Under Review" -> "#2e75b6";
            case "For Resubmission" -> "#c8a951";
            default -> "#ffa500";
        };
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
