package com.pdts.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pdts.model.ApplicantAccessToken;
import com.pdts.service.AuditLogService;
import com.pdts.service.EmailService;
import com.pdts.service.TokenService;
import com.pdts.service.TrackingNumberService;

@RestController
@RequestMapping("/api/portal")
public class PortalStatusController {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TrackingNumberService trackingNumberService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private EmailService emailService;

    @Value("${pdts.upload-dir:uploads}")
    private String uploadDir;

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@RequestParam String ref,
                                       @RequestParam String token) {
        try {
            tokenService.verifyToken(ref.trim(), token.trim());

            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT
                        a.application_reference_number AS "applicationReferenceNumber",
                        CONCAT(ap.applicant_first_name, ' ', ap.applicant_middle_name, ' ', ap.applicant_last_name) AS "applicantFullName",
                        p.program_name AS "programName",
                        c.campus_name AS "campusName",
                        a.application_semester AS "applicationSemester",
                        a.application_academic_year AS "applicationAcademicYear",
                        ast.application_status_name AS "applicationStatusName",
                        r.requirement_id AS "requirementId",
                        r.requirement_tracking_no AS "requirementTrackingNo",
                        rt.requirement_type_name AS "requirementTypeName",
                        rs.requirement_status_name AS "requirementStatusName",
                        rs.requirement_status_color AS "requirementStatusColor",
                        rr.rejection_reason_description AS "rejectionReasonDescription",
                        r.requirement_upload_date AS "requirementUploadDate",
                        r.requirement_processed_at AS "requirementProcessedAt",
                        CASE WHEN rs.status_id = 5 THEN r.requirement_remarks ELSE NULL END AS "resubmissionNotes"
                    FROM application a
                    JOIN applicant ap ON ap.applicant_id = a.applicant_id
                    JOIN program p ON p.program_id = a.program_id
                    JOIN campus c ON c.campus_id = a.campus_id
                    JOIN application_status ast ON ast.application_status_id = a.application_status_id
                    JOIN requirement r ON r.application_id = a.application_id
                    JOIN requirement_type rt ON rt.type_id = r.requirement_type_id
                    JOIN requirement_status rs ON rs.status_id = r.requirement_status_id
                    LEFT JOIN rejection_reason rr ON rr.rejection_reason_id = r.rejection_reason_id
                    WHERE a.application_reference_number = ?
                      AND COALESCE(ap.applicant_is_deleted, 0) = 0
                    ORDER BY r.requirement_upload_date DESC, r.requirement_id DESC
                    """, ref.trim());

          List<Map<String, Object>> result = rows.stream().map(row -> {
    Map<String, Object> item = new java.util.LinkedHashMap<>();

    item.put("applicationReferenceNumber", value(row.get("applicationReferenceNumber")));
    item.put("applicantFullName", value(row.get("applicantFullName")).replaceAll("\\s+", " ").trim());
    item.put("programName", value(row.get("programName")));
    item.put("campusName", value(row.get("campusName")));
    item.put("applicationSemester", value(row.get("applicationSemester")));
    item.put("applicationAcademicYear", value(row.get("applicationAcademicYear")));
    item.put("applicationStatusName", value(row.get("applicationStatusName")));
    item.put("requirementId", toInteger(row.get("requirementId")));
    item.put("requirementTrackingNo", value(row.get("requirementTrackingNo")));
    item.put("requirementTypeName", value(row.get("requirementTypeName")));
    item.put("requirementStatusName", value(row.get("requirementStatusName")));
    item.put("requirementStatusColor", value(row.get("requirementStatusColor"), "#888"));
    item.put("rejectionReasonDescription", value(row.get("rejectionReasonDescription")));
    item.put("requirementUploadDate", value(row.get("requirementUploadDate")));
    item.put("requirementProcessedAt", value(row.get("requirementProcessedAt")));
    item.put("resubmissionNotes", value(row.get("resubmissionNotes")));

    return item;
}).toList();

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Invalid or expired token. Please contact the OUS Registrar's Office."));
        }
    }

    @PostMapping(value = "/resubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> resubmitDocuments(@RequestParam String ref,
                                               @RequestParam String token,
                                               @RequestParam(value = "requirementIds", required = false) List<Integer> requirementIds,
                                               @RequestParam MultiValueMap<String, MultipartFile> fileParams) {
        try {
            ApplicantAccessToken accessToken = tokenService.verifyToken(ref.trim(), token.trim());
            Integer applicationId = accessToken.getApplicationId();

            if (requirementIds == null || requirementIds.isEmpty()) {
                throw new IllegalArgumentException("Please select at least one document for resubmission.");
            }

            List<Integer> cleanIds = requirementIds.stream()
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .toList();

            if (cleanIds.isEmpty()) {
                throw new IllegalArgumentException("Please select at least one valid document for resubmission.");
            }

            String newTrackingNo = trackingNumberService.generateDocumentNumber();
            List<String> documentNames = new ArrayList<>();

            for (Integer requirementId : cleanIds) {
                MultipartFile file = firstFile(fileParams, "file_" + requirementId);
                if (file == null || file.isEmpty()) {
                    throw new IllegalArgumentException("Please choose a replacement file for every selected document.");
                }

                Map<String, Object> req = jdbc.queryForMap("""
                        SELECT
                            r.requirement_id,
                            r.application_id,
                            r.requirement_type_id,
                            r.requirement_status_id,
                            r.requirement_storage_path,
                            rt.requirement_type_name,
                            rs.requirement_status_name
                        FROM requirement r
                        JOIN requirement_type rt ON rt.type_id = r.requirement_type_id
                        JOIN requirement_status rs ON rs.status_id = r.requirement_status_id
                        WHERE r.requirement_id = ?
                          AND r.application_id = ?
                        """, requirementId, applicationId);

                Integer currentStatusId = toInteger(req.get("requirement_status_id"));
                if (currentStatusId != 4 && currentStatusId != 5) {
                    throw new IllegalArgumentException("Only Rejected or For Resubmission documents may be resubmitted.");
                }

                Integer requirementTypeId = toInteger(req.get("requirement_type_id"));
                String documentType = value(req.get("requirement_type_name"), "Document");
                String oldStoragePath = stringOrNull(req.get("requirement_storage_path"));

                if (oldStoragePath != null && !oldStoragePath.isBlank()) {
                    deleteLocalFile(oldStoragePath);
                }

                String originalName = cleanFileName(file.getOriginalFilename());
                String newStoragePath = buildStoragePath(applicationId, requirementTypeId, originalName);
                String publicUrl = saveFileLocally(file, newStoragePath);

                jdbc.update("""
                        UPDATE requirement
                        SET requirement_tracking_no = ?,
                            requirement_file_name = ?,
                            requirement_image_path = ?,
                            requirement_file_url = ?,
                            requirement_storage_path = ?,
                            requirement_upload_date = CURRENT_TIMESTAMP,
                            requirement_status_id = 1,
                            requirement_date_received = NULL,
                            requirement_processed_by_user_id = NULL,
                            requirement_processed_at = NULL,
                            rejection_reason_id = NULL,
                            rejection_reason_rejected_by_user_id = NULL,
                            rejection_reason_rejected_at = NULL,
                            requirement_remarks = NULL,
                            requirement_is_email_sent = 0
                        WHERE requirement_id = ?
                        """,
                        newTrackingNo,
                        originalName,
                        publicUrl,
                        publicUrl,
                        newStoragePath,
                        requirementId
                );

                auditLogService.log(
                        "STUDENT_RESUBMIT_DOCUMENT",
                        "requirement",
                        requirementId.longValue(),
                        "Student resubmitted " + documentType + " under tracking number " + newTrackingNo,
                        value(req.get("requirement_status_name")),
                        "Pending"
                );

                documentNames.add(documentType);
            }

            syncAutoAdmissionStatus(applicationId);
            String emailResult = sendOneResubmissionEmailSafe(applicationId, ref.trim(), newTrackingNo, documentNames);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Resubmission received under one tracking number: " + newTrackingNo + ". " + emailResult,
                    "trackingNo", newTrackingNo,
                    "documentCount", documentNames.size()
            ));

        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "Resubmission failed: " + e.getMessage()));
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verifyGet(@RequestParam String referenceNo,
                                       @RequestParam String token) {
        try {
            tokenService.verifyToken(referenceNo.trim(), token.trim());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private MultipartFile firstFile(MultiValueMap<String, MultipartFile> fileParams, String fieldName) {
        if (fileParams == null || fieldName == null) return null;
        List<MultipartFile> files = fileParams.get(fieldName);
        return files == null || files.isEmpty() ? null : files.get(0);
    }

    private String saveFileLocally(MultipartFile file, String storagePath) throws IOException {
        validateUploadFile(file);

        Path root = requirementUploadRoot();
        Path target = root.resolve(storagePath).normalize();

        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid file path.");
        }

        Files.createDirectories(target.getParent());
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/requirements/" + storagePath.replace("\\", "/");
    }

    private void deleteLocalFile(String storagePath) throws IOException {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }

        Path root = requirementUploadRoot();
        Path target = root.resolve(storagePath).normalize();

        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid file path.");
        }

        Files.deleteIfExists(target);
    }

    private Path requirementUploadRoot() {
        return Paths.get(uploadDir, "requirements").toAbsolutePath().normalize();
    }

    private String buildStoragePath(Integer applicationId, Integer requirementTypeId, String cleanFileName) {
        String safeFileName = safePathPart(cleanFileName);

        try {
            Map<String, Object> fileContext = jdbc.queryForMap("""
                    SELECT
                        a.application_reference_number,
                        ap.applicant_first_name,
                        ap.applicant_last_name,
                        COALESCE(p.program_code, 'PROGRAM') AS program_code,
                        rt.requirement_type_name
                    FROM application a
                    JOIN applicant ap ON ap.applicant_id = a.applicant_id
                    JOIN program p ON p.program_id = a.program_id
                    JOIN requirement_type rt ON rt.type_id = ?
                    WHERE a.application_id = ?
                    """, requirementTypeId, applicationId);

            String referenceNo = stringOrNull(fileContext.get("application_reference_number"));
            String firstName = stringOrNull(fileContext.get("applicant_first_name"));
            String lastName = stringOrNull(fileContext.get("applicant_last_name"));
            String programCode = stringOrNull(fileContext.get("program_code"));
            String documentType = stringOrNull(fileContext.get("requirement_type_name"));

            String studentFolder = safePathPart(
                    valueOrDefault(referenceNo, "APP-" + applicationId)
                            + "_" + valueOrDefault(lastName, "LastName")
                            + "_" + valueOrDefault(firstName, "FirstName")
                            + "_" + valueOrDefault(programCode, "Program")
            );

            String documentFolder = safePathPart(valueOrDefault(documentType, "Document"));
            String storedFileName = UUID.randomUUID() + "-" + documentFolder + "-" + safeFileName;

            return "applications/" + studentFolder + "/" + documentFolder + "/" + storedFileName;

        } catch (Exception ignored) {
            String safeApplicationId = applicationId == null ? "unknown" : String.valueOf(applicationId);
            return "applications/APP-" + safeApplicationId + "/Document/" + UUID.randomUUID() + "-" + safeFileName;
        }
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please choose a file to upload.");
        }

        String extension = getFileExtension(file.getOriginalFilename());
        if (!List.of("pdf", "jpg", "jpeg", "png", "webp").contains(extension)) {
            throw new IllegalArgumentException("Only PDF, JPG, JPEG, PNG, and WEBP files are allowed.");
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
    }

    private String cleanFileName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "document";
        }

        String cleanName = Paths.get(filename)
                .getFileName()
                .toString()
                .replaceAll("[^a-zA-Z0-9._-]", "_");

        return cleanName.isBlank() ? "document" : cleanName;
    }

    private String safePathPart(String value) {
        if (value == null || value.isBlank()) {
            return "file";
        }

        String safe = value.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        safe = safe.replaceAll("_+", "_");
        safe = safe.replaceAll("^_+|_+$", "");

        return safe.isBlank() ? "file" : safe;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() || value.equalsIgnoreCase("null") ? fallback : value;
    }

    private String stringOrNull(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value);
        return text.equalsIgnoreCase("null") ? null : text;
    }

    private String value(Object value) {
        return value(value, "");
    }

    private String value(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value);
        return text.equalsIgnoreCase("null") ? fallback : text;
    }

    private int toInteger(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private String sendOneResubmissionEmailSafe(Integer applicationId,
                                                String referenceNo,
                                                String trackingNo,
                                                List<String> documentNames) {
        try {
            Map<String, Object> info = jdbc.queryForMap("""
                    SELECT
                        ap.applicant_first_name,
                        ap.applicant_last_name,
                        ap.applicant_email_address
                    FROM application a
                    JOIN applicant ap ON ap.applicant_id = a.applicant_id
                    WHERE a.application_id = ?
                    """, applicationId);

            String firstName = value(info.get("applicant_first_name"));
            String lastName = value(info.get("applicant_last_name"));
            String email = value(info.get("applicant_email_address"));

            if (email.isBlank()) {
                return "No acknowledgement email was sent because the applicant has no email address.";
            }

            String body = "Dear " + firstName + " " + lastName + ",\n\n"
                    + "Your corrected document resubmission has been received and reset to Pending.\n\n"
                    + "Application Reference Number: " + referenceNo + "\n"
                    + "New Submission Tracking Number: " + trackingNo + "\n"
                    + "Documents Resubmitted: " + String.join(", ", documentNames) + "\n\n"
                    + "Only one tracking number was generated for this resubmission batch.";

            emailService.sendManualEmail(
                    email,
                    "[PDTS] Document Resubmission Received - " + referenceNo,
                    body,
                    "Tracking number: " + trackingNo
            );

            jdbc.update("""
                    UPDATE requirement
                    SET requirement_is_email_sent = 1
                    WHERE application_id = ?
                      AND requirement_tracking_no = ?
                    """, applicationId, trackingNo);

            return "One consolidated acknowledgement email was sent to " + email + ".";

        } catch (Exception e) {
            return "Resubmission was saved, but acknowledgement email sending failed: " + e.getMessage();
        }
    }

    private void syncAutoAdmissionStatus(Integer applicationId) {
        if (applicationId == null) {
            return;
        }

        String currentStatus = currentApplicationStatusName(applicationId);
        if (isManualFinalAdmissionStatus(currentStatus)) {
            return;
        }

        String targetStatus = targetAdmissionStatus(applicationId);
        if (targetStatus == null || targetStatus.isBlank()) {
            return;
        }

        Integer targetStatusId = applicationStatusIdByName(targetStatus);
        if (targetStatusId == null) {
            return;
        }

        jdbc.update("""
                UPDATE application
                SET application_status_id = ?
                WHERE application_id = ?
                  AND application_status_id <> ?
                """, targetStatusId, applicationId, targetStatusId);
    }

    private String targetAdmissionStatus(Integer applicationId) {
        try {
            Map<String, Object> summary = jdbc.queryForMap("""
                    SELECT
                        COUNT(*) AS total_required,
                        COALESCE(SUM(CASE WHEN latest_req.requirement_id IS NOT NULL THEN 1 ELSE 0 END), 0) AS submitted,
                        COALESCE(SUM(CASE WHEN latest_req.requirement_status_id = 3 THEN 1 ELSE 0 END), 0) AS verified
                    FROM application a
                    JOIN applicant ap ON ap.applicant_id = a.applicant_id
                    JOIN curriculum_requirement cr ON cr.category_id = ap.educational_background_category_id
                    LEFT JOIN LATERAL (
                        SELECT r.requirement_id, r.requirement_status_id
                        FROM requirement r
                        WHERE r.application_id = a.application_id
                          AND r.requirement_type_id = cr.type_id
                        ORDER BY r.requirement_upload_date DESC, r.requirement_id DESC
                        LIMIT 1
                    ) latest_req ON TRUE
                    WHERE a.application_id = ?
                      AND COALESCE(ap.applicant_is_deleted, 0) = 0
                    """, applicationId);

            int totalRequired = toInteger(summary.get("total_required"));
            int submitted = toInteger(summary.get("submitted"));
            int verified = toInteger(summary.get("verified"));

            if (totalRequired <= 0 || submitted <= 0) {
                return "Pending";
            }

            if (verified >= totalRequired) {
                return "Enrolled";
            }

            return "Temporarily Enrolled";

        } catch (Exception e) {
            return null;
        }
    }

    private String currentApplicationStatusName(Integer applicationId) {
        try {
            return jdbc.queryForObject("""
                    SELECT ast.application_status_name
                    FROM application a
                    JOIN application_status ast ON ast.application_status_id = a.application_status_id
                    WHERE a.application_id = ?
                    """, String.class, applicationId);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isManualFinalAdmissionStatus(String status) {
        if (status == null) return false;
        return "Non-Compliant".equalsIgnoreCase(status.trim())
                || "Did Not Continue".equalsIgnoreCase(status.trim())
                || "Cancelled".equalsIgnoreCase(status.trim());
    }

    private Integer applicationStatusIdByName(String statusName) {
        try {
            return jdbc.queryForObject("""
                    SELECT application_status_id
                    FROM application_status
                    WHERE LOWER(application_status_name) = LOWER(?)
                    LIMIT 1
                    """, Integer.class, statusName);
        } catch (Exception e) {
            return null;
        }
    }
}
