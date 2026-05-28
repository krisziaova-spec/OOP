package com.pdts.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portal")
public class PublicTrackingApiController {

    @PersistenceContext
    private EntityManager em;

    /**
     * Public document tracking endpoint.
     *
     * Privacy rule:
     * - The public page must only expose document-level status.
     * - Do not expose applicant name, application number, program, campus, email,
     *   contact number, or complete application information.
     * - Application reference numbers are intentionally not searchable here.
     */
    @GetMapping("/public-track")
    public ResponseEntity<?> publicTrack(@RequestParam String trackingNumber) {
        String tracking = trackingNumber == null ? "" : trackingNumber.trim();

        if (tracking.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Document tracking number is required."
            ));
        }

        try {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("""
                    SELECT
                        r.requirement_tracking_no,
                        rt.requirement_type_name,
                        rs.requirement_status_name,
                        rs.requirement_status_color,
                        r.requirement_upload_date,
                        r.requirement_processed_at
                    FROM requirement r
                    JOIN requirement_type rt
                      ON rt.type_id = r.requirement_type_id
                    JOIN requirement_status rs
                      ON rs.status_id = r.requirement_status_id
                    WHERE r.requirement_tracking_no = :tracking
                    ORDER BY rt.requirement_type_name ASC
                    """)
                    .setParameter("tracking", tracking)
                    .getResultList();

            if (rows.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "error", "No document record found. For privacy, the public tracker accepts document tracking numbers only. Application records are available only to authorized Registrar staff."
                ));
            }

            String currentStatus = summarizeStatus(rows);
            Object[] first = rows.get(0);
            String latestDate = latestDate(rows);

            List<Map<String, String>> documents = new ArrayList<>();
            for (Object[] row : rows) {
                documents.add(Map.of(
                        "documentType", value(row[1]),
                        "currentStatus", value(row[2]),
                        "statusColor", row[3] != null ? row[3].toString() : "#800000",
                        "uploadedDate", row[4] != null ? row[4].toString() : "",
                        "processedDate", row[5] != null ? row[5].toString() : ""
                ));
            }

            return ResponseEntity.ok(Map.ofEntries(
                    Map.entry("success", true),
                    Map.entry("trackingNumber", value(first[0])),
                    Map.entry("currentStatus", currentStatus),
                    Map.entry("statusColor", rowStatusColor(rows, currentStatus)),
                    Map.entry("uploadedDate", first[4] != null ? first[4].toString() : ""),
                    Map.entry("processedDate", latestDate),
                    Map.entry("documentCount", rows.size()),
                    Map.entry("documents", documents),
                    Map.entry("publicMessage", publicMessage(currentStatus, rows.size()))
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Unable to load document tracking status. Please contact the Registrar's Office."
            ));
        }
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private String summarizeStatus(List<Object[]> rows) {
        boolean hasRejected = false;
        boolean hasResubmission = false;
        boolean hasReview = false;
        boolean hasPending = false;
        boolean allVerified = true;

        for (Object[] row : rows) {
            String clean = value(row[2]).trim().toLowerCase();
            hasRejected = hasRejected || clean.contains("reject");
            hasResubmission = hasResubmission || clean.contains("resubmission");
            hasReview = hasReview || clean.contains("review");
            hasPending = hasPending || clean.contains("pending");
            allVerified = allVerified && (clean.contains("verified") || clean.contains("received"));
        }

        if (hasRejected) return "Rejected";
        if (hasResubmission) return "For Resubmission";
        if (hasReview) return "Under Review";
        if (hasPending) return "Pending";
        if (allVerified) return "Verified/Received";
        return value(rows.get(0)[2]);
    }

    private String latestDate(List<Object[]> rows) {
        String latest = "";
        for (Object[] row : rows) {
            String processed = value(row[5]);
            String uploaded = value(row[4]);
            String candidate = !processed.isBlank() ? processed : uploaded;
            if (candidate.compareTo(latest) > 0) {
                latest = candidate;
            }
        }
        return latest;
    }

    private String rowStatusColor(List<Object[]> rows, String status) {
        String cleanStatus = status == null ? "" : status.trim().toLowerCase();
        for (Object[] row : rows) {
            String rowStatus = value(row[2]).trim().toLowerCase();
            if (rowStatus.equals(cleanStatus)) {
                return row[3] != null ? row[3].toString() : "#800000";
            }
        }
        return "#800000";
    }

    private String publicMessage(String status, int documentCount) {
        String prefix = documentCount > 1
                ? "This tracking number covers " + documentCount + " submitted documents. "
                : "";

        if (status == null) {
            return prefix + "Your document status is being updated. Please check again later.";
        }

        String clean = status.trim().toLowerCase();

        if (clean.contains("pending")) {
            return prefix + "Your submitted document(s) have been received and are awaiting Registrar review.";
        }
        if (clean.contains("under review")) {
            return prefix + "Your submitted document(s) are currently being evaluated by the Registrar's Office.";
        }
        if (clean.contains("verified") || clean.contains("received")) {
            return prefix + "Your submitted document(s) have been verified and received by the Registrar's Office.";
        }
        if (clean.contains("resubmission")) {
            return prefix + "One or more submitted documents need resubmission. Please check your registered email or contact the Registrar's Office for instructions.";
        }
        if (clean.contains("reject")) {
            return prefix + "One or more submitted documents were not accepted. Please check your registered email or contact the Registrar's Office for the next step.";
        }

        return prefix + "Your document status has been updated. Please check your registered email or contact the Registrar's Office if you need assistance.";
    }
}
