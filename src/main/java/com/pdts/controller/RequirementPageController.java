package com.pdts.controller;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pdts.service.AuditLogService;
import com.pdts.service.TrackingNumberService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class RequirementPageController {

    private final JdbcTemplate jdbc;
    private final AuditLogService auditLogService;
    private final TrackingNumberService trackingNumberService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${pdts.upload-dir:uploads}")
    private String uploadDir;

    private final String resendApiKey = System.getenv("RESEND_API_KEY");
    private final String resendFromEmail = System.getenv("RESEND_FROM_EMAIL");
    private final String appBaseUrl = System.getenv("APP_BASE_URL");

    public RequirementPageController(JdbcTemplate jdbc,
                                     AuditLogService auditLogService,
                                     TrackingNumberService trackingNumberService) {
        this.jdbc = jdbc;
        this.auditLogService = auditLogService;
        this.trackingNumberService = trackingNumberService;
    }

    @GetMapping("/requirements")
    public String list(@RequestParam(required = false) Integer statusId,
                       @RequestParam(required = false) String statusGroup,
                       @RequestParam(required = false) String region,
                       @RequestParam(required = false) String curriculum,
                       @RequestParam(required = false) String program,
                       @RequestParam(required = false) String search,
                       Model model) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    r.requirement_id,
                    r.requirement_tracking_no,
                    r.requirement_file_name,
                    r.requirement_upload_date,
                    TO_CHAR(r.requirement_upload_date, 'Mon DD, YYYY HH12:MI AM') AS requirement_upload_date_display,
                    r.requirement_status_id,
                    r.requirement_remarks,
                    r.requirement_file_url,
                    r.requirement_storage_path,
                    rt.requirement_type_name,
                    rs.requirement_status_name,
                    a.application_reference_number,
                    ap.applicant_id,
                    ap.applicant_first_name,
                    ap.applicant_last_name,
                    ap.applicant_email_address
                FROM requirement r
                JOIN requirement_type rt ON rt.type_id = r.requirement_type_id
                JOIN requirement_status rs ON rs.status_id = r.requirement_status_id
                JOIN application a ON a.application_id = r.application_id
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                WHERE COALESCE(ap.applicant_is_deleted, 0) = 0
                """);

        List<Object> params = new ArrayList<>();

        if (statusId != null) {
            sql.append(" AND r.requirement_status_id = ? ");
            params.add(statusId);
        }

        String dashboardFilterLabel = null;
        if (statusGroup != null && !statusGroup.isBlank()) {
            String cleanGroup = statusGroup.trim();
            if ("pendingReview".equalsIgnoreCase(cleanGroup)) {
                sql.append(" AND rs.requirement_status_name IN ('Pending', 'Under Review') ");
                dashboardFilterLabel = "Pending Documents / Awaiting Review";
            } else if ("needsAction".equalsIgnoreCase(cleanGroup)) {
                sql.append(" AND rs.requirement_status_name IN ('Rejected', 'For Resubmission') ");
                dashboardFilterLabel = "Needs Action / Rejected or For Resubmission";
            } else if ("verified".equalsIgnoreCase(cleanGroup)) {
                sql.append(" AND rs.requirement_status_name = 'Verified/Received' ");
                dashboardFilterLabel = "Verified / Received Documents";
            } else if ("pendingRequirements".equalsIgnoreCase(cleanGroup)) {
                sql.append(" AND rs.requirement_status_name <> 'Verified/Received' ");
                dashboardFilterLabel = "Pending Requirements / Not Yet Verified";
            }
        }

        if (region != null && !region.isBlank()) {
            if ("Unspecified".equalsIgnoreCase(region.trim())) {
                sql.append(" AND (ap.applicant_region IS NULL OR TRIM(ap.applicant_region) = '') ");
            } else {
                sql.append(" AND LOWER(ap.applicant_region) LIKE LOWER(?) ");
                params.add("%" + region.trim() + "%");
            }
        }

        if (curriculum != null && !curriculum.isBlank()) {
            sql.append(" AND ap.educational_background_category_id = ? ");
            params.add(curriculum.trim());
        }

        Integer selectedProgramId = parseInteger(program);
        if (selectedProgramId != null) {
            sql.append(" AND a.program_id = ? ");
            params.add(selectedProgramId);
        }

        String reportFilterLabel = buildReportFilterLabel(region, curriculum, program);
        if (!reportFilterLabel.isBlank()) {
            dashboardFilterLabel = (dashboardFilterLabel == null || dashboardFilterLabel.isBlank())
                    ? reportFilterLabel
                    : dashboardFilterLabel + " • " + reportFilterLabel;
        }

        if (search != null && !search.isBlank()) {
            sql.append("""
                    AND (
                        LOWER(r.requirement_tracking_no) LIKE LOWER(?)
                        OR LOWER(rt.requirement_type_name) LIKE LOWER(?)
                        OR LOWER(ap.applicant_first_name) LIKE LOWER(?)
                        OR LOWER(ap.applicant_last_name) LIKE LOWER(?)
                        OR LOWER(a.application_reference_number) LIKE LOWER(?)
                    )
                    """);
            String q = "%" + search.trim() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
        }

        sql.append(" ORDER BY r.requirement_upload_date DESC, r.requirement_id DESC");

        model.addAttribute("requirements", jdbc.queryForList(sql.toString(), params.toArray()));
        model.addAttribute("statusId", statusId);
        model.addAttribute("statusGroup", statusGroup);
        model.addAttribute("region", region);
        model.addAttribute("curriculum", curriculum);
        model.addAttribute("program", program);
        model.addAttribute("search", search);
        model.addAttribute("dashboardFilterLabel", dashboardFilterLabel);
        addLookups(model);

        return "requirements";
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String buildReportFilterLabel(String region, String curriculum, String program) {
        List<String> parts = new ArrayList<>();
        if (region != null && !region.isBlank()) {
            parts.add("Region: " + region.trim());
        }
        if (curriculum != null && !curriculum.isBlank()) {
            parts.add("Curriculum ID: " + curriculum.trim());
        }
        if (program != null && !program.isBlank()) {
            parts.add("Program ID: " + program.trim());
        }
        return parts.isEmpty() ? "" : String.join(" • ", parts);
    }

    @GetMapping({"/requirements/new", "/requirements/upload"})
    public String uploadForm(@RequestParam(required = false) Integer applicationId,
                             @RequestParam(required = false) Integer typeId,
                             Model model) {
        addLookups(model);
        model.addAttribute("preselectedApplicationId", applicationId);
        model.addAttribute("preselectedRequirementTypeId", typeId);
        return "requirement-form";
    }

    @GetMapping("/requirements/applications/{applicationId}/types")
    @ResponseBody
    public List<Map<String, Object>> requirementTypesForApplication(@PathVariable Integer applicationId) {
        return jdbc.queryForList("""
        SELECT
            rt.type_id AS "typeId",
            rt.requirement_type_name AS "requirementTypeName",
            cr.is_mandatory AS "mandatory",
            ebc.category_name AS "curriculumName"
        FROM application a
        JOIN applicant ap ON ap.applicant_id = a.applicant_id
        JOIN educational_background_category ebc 
            ON ebc.category_id = ap.educational_background_category_id
        JOIN LATERAL (
            SELECT cr.type_id, cr.is_mandatory
            FROM curriculum_requirement cr
            WHERE cr.category_id = ap.educational_background_category_id

            UNION

            SELECT 17 AS type_id, 1 AS is_mandatory
            WHERE ap.applicant_employment_status = 'Employed'

            UNION

            SELECT 8 AS type_id, 1 AS is_mandatory
            WHERE ap.applicant_employment_status = 'Unemployed'

            UNION

           SELECT 12 AS type_id, 1 AS is_mandatory
           WHERE ap.applicant_sex = 2
           AND ap.applicant_civil_status = 2

            UNION

            SELECT 22 AS type_id, 1 AS is_mandatory
            WHERE ap.applicant_school_records_available = 0
        ) cr ON TRUE
        JOIN requirement_type rt ON rt.type_id = cr.type_id
        WHERE a.application_id = ?
          AND COALESCE(ap.applicant_is_deleted, 0) = 0
          AND COALESCE(rt.type_is_active, 1) = 1
          AND NOT EXISTS (
              SELECT 1
              FROM requirement r
              WHERE r.application_id = a.application_id
                AND r.requirement_type_id = rt.type_id
          )
        ORDER BY cr.is_mandatory DESC, rt.requirement_type_name
        """, applicationId);
    }

    @GetMapping("/requirements/applications/{applicationId}/resubmission-items")
    @ResponseBody
    public List<Map<String, Object>> resubmissionItemsForApplication(@PathVariable Integer applicationId) {
        return jdbc.queryForList("""
                SELECT
                    r.requirement_id AS "requirementId",
                    r.requirement_type_id AS "typeId",
                    rt.requirement_type_name AS "requirementTypeName",
                    rs.requirement_status_name AS "statusName",
                    r.requirement_tracking_no AS "trackingNo",
                    COALESCE(rr.rejection_reason_description, r.requirement_remarks, '') AS "notes"
                FROM requirement r
                JOIN requirement_type rt ON rt.type_id = r.requirement_type_id
                JOIN requirement_status rs ON rs.status_id = r.requirement_status_id
                JOIN application a ON a.application_id = r.application_id
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                LEFT JOIN rejection_reason rr ON rr.rejection_reason_id = r.rejection_reason_id
                WHERE r.application_id = ?
                  AND COALESCE(ap.applicant_is_deleted, 0) = 0
                  AND r.requirement_status_id IN (4, 5)
                ORDER BY rt.requirement_type_name
                """, applicationId);
    }

    @PostMapping("/requirements")
    @Transactional
    public String create(@RequestParam Integer applicationId,
                         @RequestParam(required = false) Integer requirementTypeId,
                         @RequestParam(value = "selectedTypeIds", required = false) List<Integer> selectedTypeIds,
                         @RequestParam(value = "selectedRequirementIds", required = false) List<Integer> selectedRequirementIds,
                         @RequestParam MultiValueMap<String, MultipartFile> fileParams,
                         RedirectAttributes ra,
                         HttpServletRequest request) {

        try {
            List<Map<String, Object>> uploadedDocuments = new ArrayList<>();
            String submissionTrackingNo = nextTrackingNo();
            boolean isResubmissionBatch = selectedRequirementIds != null && !selectedRequirementIds.isEmpty();

            if (isResubmissionBatch) {
                for (Integer requirementId : selectedRequirementIds.stream().filter(id -> id != null && id > 0).distinct().toList()) {
                    MultipartFile selectedFile = firstFile(fileParams, "resubmit_file_" + requirementId);

                    if (selectedFile == null || selectedFile.isEmpty()) {
                        throw new IllegalArgumentException("Please choose a replacement file for each selected resubmission item.");
                    }

                    uploadedDocuments.add(resubmitExistingRequirement(applicationId, requirementId, selectedFile, submissionTrackingNo));
                }
            } else if (selectedTypeIds != null && !selectedTypeIds.isEmpty()) {
                for (Integer selectedTypeId : selectedTypeIds) {
                    MultipartFile selectedFile = firstFile(fileParams, "file_" + selectedTypeId);

                    if (selectedFile == null || selectedFile.isEmpty()) {
                        throw new IllegalArgumentException("Please choose a file for " + getRequirementTypeName(selectedTypeId) + ".");
                    }

                    uploadedDocuments.add(uploadRequirement(applicationId, selectedTypeId, selectedFile, submissionTrackingNo));
                }
            } else {
                MultipartFile singleFile = firstFile(fileParams, "file");

                if (requirementTypeId == null) {
                    throw new IllegalArgumentException("Please select a requirement type.");
                }

                if (singleFile == null || singleFile.isEmpty()) {
                    throw new IllegalArgumentException("Please choose a file to upload.");
                }

                uploadedDocuments.add(uploadRequirement(applicationId, requirementTypeId, singleFile, submissionTrackingNo));
            }

            syncAutoAdmissionStatus(applicationId);
            String emailResult = sendSubmissionUploadEmailSafe(applicationId, uploadedDocuments, submissionTrackingNo, request);

            if (isResubmissionBatch) {
                ra.addFlashAttribute("success", uploadedDocuments.size() + " document(s) resubmitted under one tracking number: " + submissionTrackingNo + ". " + emailResult);
            } else if (uploadedDocuments.size() == 1) {
                ra.addFlashAttribute("success", "Document uploaded. Tracking number: " + submissionTrackingNo + ". " + emailResult);
            } else {
                ra.addFlashAttribute("success", uploadedDocuments.size() + " documents uploaded under one tracking number: " + submissionTrackingNo + ". " + emailResult);
            }

            return "redirect:/requirements";

        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            ra.addFlashAttribute("error", "Upload failed: " + e.getMessage());
            String redirectUrl = "redirect:/requirements/new";
            if (applicationId != null) {
                redirectUrl += "?applicationId=" + applicationId;
            }
            return redirectUrl;
        }
    }

    private MultipartFile firstFile(MultiValueMap<String, MultipartFile> fileParams, String fieldName) {
        if (fileParams == null || fieldName == null) {
            return null;
        }

        List<MultipartFile> files = fileParams.get(fieldName);
        if (files == null || files.isEmpty()) {
            return null;
        }

        return files.get(0);
    }

    private Map<String, Object> uploadRequirement(Integer applicationId,
                                                  Integer requirementTypeId,
                                                  MultipartFile file,
                                                  String trackingNo) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please choose a file to upload.");
        }

        Integer activeApplicationCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM application a
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                WHERE a.application_id = ?
                  AND COALESCE(ap.applicant_is_deleted, 0) = 0
                """, Integer.class, applicationId);

        if (activeApplicationCount == null || activeApplicationCount == 0) {
            throw new IllegalArgumentException("Selected application does not belong to an active applicant.");
        }

        if (!requirementTypeBelongsToApplicationCurriculum(applicationId, requirementTypeId)) {
            throw new IllegalArgumentException("Selected document type is not required for this applicant curriculum.");
        }

        if (requirementAlreadyLogged(applicationId, requirementTypeId)) {
            throw new IllegalArgumentException(getRequirementTypeName(requirementTypeId) + " is already logged for the selected applicant. Use replace/status update instead of creating a duplicate entry.");
        }

        if (trackingNo == null || trackingNo.isBlank()) {
            throw new IllegalArgumentException("Tracking number could not be generated for this submission.");
        }

        String originalName = cleanFileName(file.getOriginalFilename());
        String storagePath = buildStoragePath(applicationId, requirementTypeId, originalName);
        String publicUrl = saveFileLocally(file, storagePath);
        String documentType = getRequirementTypeName(requirementTypeId);

        Integer requirementId = jdbc.queryForObject("""
                INSERT INTO requirement (
                    application_id,
                    requirement_type_id,
                    requirement_status_id,
                    requirement_tracking_no,
                    requirement_file_name,
                    requirement_image_path,
                    requirement_file_url,
                    requirement_storage_path,
                    requirement_uploaded_by_user_id
                )
                VALUES (?, ?, 1, ?, ?, ?, ?, ?, 1)
                RETURNING requirement_id
                """,
                Integer.class,
                applicationId,
                requirementTypeId,
                trackingNo,
                originalName,
                publicUrl,
                publicUrl,
                storagePath
        );

        auditLogService.log(
                "UPLOAD_DOCUMENT",
                "requirement",
                requirementId != null ? requirementId.longValue() : null,
                "Uploaded " + originalName + " under submission tracking number " + trackingNo,
                null,
                "Stored in local uploads folder"
        );

        return Map.ofEntries(
                Map.entry("requirement_id", requirementId == null ? 0 : requirementId),
                Map.entry("application_id", applicationId),
                Map.entry("requirement_tracking_no", trackingNo),
                Map.entry("requirement_type_name", documentType),
                Map.entry("requirement_status_name", "Pending")
        );
    }


    private Map<String, Object> resubmitExistingRequirement(Integer applicationId,
                                                            Integer requirementId,
                                                            MultipartFile file,
                                                            String trackingNo) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please choose a replacement file.");
        }

        Map<String, Object> requirement = jdbc.queryForMap("""
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
                JOIN application a ON a.application_id = r.application_id
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                WHERE r.requirement_id = ?
                  AND r.application_id = ?
                  AND COALESCE(ap.applicant_is_deleted, 0) = 0
                """, requirementId, applicationId);

        Integer currentStatusId = toInteger(requirement.get("requirement_status_id"));
        if (currentStatusId != 4 && currentStatusId != 5) {
            throw new IllegalArgumentException("Only Rejected or For Resubmission documents can be resubmitted in this batch form.");
        }

        Integer requirementTypeId = toInteger(requirement.get("requirement_type_id"));
        String documentType = stringOrNull(requirement.get("requirement_type_name"));
        String oldStoragePath = stringOrNull(requirement.get("requirement_storage_path"));

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
                trackingNo,
                originalName,
                publicUrl,
                publicUrl,
                newStoragePath,
                requirementId
        );

        auditLogService.log(
                "BATCH_RESUBMIT_DOCUMENT",
                "requirement",
                requirementId.longValue(),
                "Resubmitted " + originalName + " under new batch tracking number " + trackingNo,
                stringOrNull(requirement.get("requirement_status_name")),
                "Pending"
        );

        return Map.ofEntries(
                Map.entry("requirement_id", requirementId),
                Map.entry("application_id", applicationId),
                Map.entry("requirement_tracking_no", trackingNo),
                Map.entry("requirement_type_name", documentType == null || documentType.isBlank() ? "Document" : documentType),
                Map.entry("requirement_status_name", "Pending")
        );
    }

    @GetMapping("/requirements/{id}/view")
    public String viewDocument(@PathVariable Integer id, RedirectAttributes ra) {
        try {
            String fileUrl = jdbc.queryForObject("""
                    SELECT requirement_file_url
                    FROM requirement
                    WHERE requirement_id = ?
                    """, String.class, id);

            if (fileUrl == null || fileUrl.isBlank()) {
                ra.addFlashAttribute("error", "No document URL found. Please re-upload this document.");
                return "redirect:/requirements";
            }

            return "redirect:" + fileUrl;

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Document not found or unavailable.");
            return "redirect:/requirements";
        }
    }

    @PostMapping("/requirements/{id}/delete")
    public String deleteRequirement(@PathVariable Integer id, RedirectAttributes ra) {
        try {
            Map<String, Object> requirement = jdbc.queryForMap("""
                    SELECT application_id, requirement_file_name, requirement_tracking_no, requirement_storage_path
                    FROM requirement
                    WHERE requirement_id = ?
                    """, id);

            Integer applicationId = toInteger(requirement.get("application_id"));
            String trackingNo = String.valueOf(requirement.get("requirement_tracking_no"));
            String fileName = String.valueOf(requirement.get("requirement_file_name"));
            String storagePath = stringOrNull(requirement.get("requirement_storage_path"));

            if (storagePath != null && !storagePath.isBlank()) {
                deleteLocalFile(storagePath);
            }

            jdbc.update("""
                    DELETE FROM requirement
                    WHERE requirement_id = ?
                    """, id);

            auditLogService.log(
                    "DELETE_DOCUMENT",
                    "requirement",
                    id.longValue(),
                    "Deleted document " + fileName,
                    trackingNo,
                    "Removed from local uploads folder"
            );

            syncAutoAdmissionStatus(applicationId);

            ra.addFlashAttribute("success", "Document deleted successfully.");

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Delete failed: " + e.getMessage());
        }

        return "redirect:/requirements";
    }

    @PostMapping("/requirements/{id}/replace")
    public String replaceRequirement(@PathVariable Integer id,
                                     @RequestParam MultipartFile file,
                                     RedirectAttributes ra,
                                     HttpServletRequest request) {

        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Please choose a file.");
            }

            Map<String, Object> requirement = jdbc.queryForMap("""
                    SELECT application_id, requirement_type_id, requirement_tracking_no, requirement_storage_path
                    FROM requirement
                    WHERE requirement_id = ?
                    """, id);

            Integer applicationId = ((Number) requirement.get("application_id")).intValue();
            Integer requirementTypeId = ((Number) requirement.get("requirement_type_id")).intValue();
            String documentType = getRequirementTypeName(requirementTypeId);
            String oldStoragePath = stringOrNull(requirement.get("requirement_storage_path"));
            String trackingNo = String.valueOf(requirement.get("requirement_tracking_no"));

            if (oldStoragePath != null && !oldStoragePath.isBlank()) {
                deleteLocalFile(oldStoragePath);
            }

            String originalName = cleanFileName(file.getOriginalFilename());
            String newStoragePath = buildStoragePath(applicationId, requirementTypeId, originalName);
            String publicUrl = saveFileLocally(file, newStoragePath);

            jdbc.update("""
                    UPDATE requirement
                    SET requirement_file_name = ?,
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
                        requirement_remarks = NULL
                    WHERE requirement_id = ?
                    """,
                    originalName,
                    publicUrl,
                    publicUrl,
                    newStoragePath,
                    id
            );

            auditLogService.log(
                    "REUPLOAD_DOCUMENT",
                    "requirement",
                    id.longValue(),
                    "Re-uploaded document " + originalName,
                    trackingNo,
                    "Stored in local uploads folder. Status reset to Pending"
            );

            sendRequirementStatusEmailSafe(id, "Pending", trackingNo, documentType, request);
            syncAutoAdmissionStatus(applicationId);

            ra.addFlashAttribute("success", "Document replaced successfully.");

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Replace failed: " + e.getMessage());
        }

        return "redirect:/requirements";
    }

    @PostMapping("/requirements/{id}/status")
    public String updateStatus(@PathVariable Integer id,
                               @RequestParam Integer statusId,
                               @RequestParam(required = false) Integer rejectionReasonId,
                               @RequestParam(required = false) String remarks,
                               RedirectAttributes ra,
                               HttpServletRequest request) {

        try {
            if (!requirementBelongsToActiveApplicant(id)) {
                ra.addFlashAttribute("error", "Status cannot be updated because this requirement belongs to a deleted applicant.");
                return "redirect:/requirements";
            }

            Map<String, Object> requirementInfo = jdbc.queryForMap("""
                    SELECT
                        r.application_id,
                        r.requirement_tracking_no,
                        rt.requirement_type_name,
                        rs.requirement_status_name AS old_status_name
                    FROM requirement r
                    JOIN requirement_type rt ON rt.type_id = r.requirement_type_id
                    JOIN requirement_status rs ON rs.status_id = r.requirement_status_id
                    WHERE r.requirement_id = ?
                    """, id);

            Integer applicationId = toInteger(requirementInfo.get("application_id"));
            String oldStatus = String.valueOf(requirementInfo.get("old_status_name"));
            String trackingNo = String.valueOf(requirementInfo.get("requirement_tracking_no"));
            String documentType = String.valueOf(requirementInfo.get("requirement_type_name"));

           String cleanRemarks = (remarks == null || remarks.isBlank()) ? null : remarks.trim();

if (statusId == 3) {
    jdbc.update("""
            UPDATE requirement
            SET requirement_status_id = 3,
                requirement_date_received = CURRENT_TIMESTAMP,
                requirement_processed_by_user_id = 1,
                requirement_processed_at = CURRENT_TIMESTAMP,
                rejection_reason_id = NULL,
                requirement_remarks = ?
            WHERE requirement_id = ?
            """, cleanRemarks, id);

} else if (statusId == 4) {
    jdbc.update("""
            UPDATE requirement
            SET requirement_status_id = 4,
                rejection_reason_id = ?,
                rejection_reason_rejected_by_user_id = 1,
                rejection_reason_rejected_at = CURRENT_TIMESTAMP,
                requirement_processed_by_user_id = 1,
                requirement_processed_at = CURRENT_TIMESTAMP,
                requirement_remarks = ?
            WHERE requirement_id = ?
            """, rejectionReasonId, cleanRemarks, id);

} else if (statusId == 5) {
    jdbc.update("""
            UPDATE requirement
            SET requirement_status_id = 5,
                rejection_reason_id = NULL,
                requirement_remarks = ?,
                requirement_processed_by_user_id = 1,
                requirement_processed_at = CURRENT_TIMESTAMP
            WHERE requirement_id = ?
            """, cleanRemarks, id);

} else {
    jdbc.update("""
            UPDATE requirement
            SET requirement_status_id = ?,
                rejection_reason_id = NULL,
                requirement_remarks = ?,
                requirement_processed_by_user_id = 1,
                requirement_processed_at = CURRENT_TIMESTAMP
            WHERE requirement_id = ?
            """, statusId, cleanRemarks, id);
}
            String newStatus = jdbc.queryForObject("""
                    SELECT requirement_status_name
                    FROM requirement_status
                    WHERE status_id = ?
                    """, String.class, statusId);

            auditLogService.log(
                    getRequirementActionType(statusId),
                    "requirement",
                    id.longValue(),
                    "Updated " + documentType + " [" + trackingNo + "] from " + oldStatus + " to " + newStatus,
                    oldStatus,
                    newStatus
            );

            sendRequirementStatusEmailSafe(id, newStatus, trackingNo, documentType, request);
            syncAutoAdmissionStatus(applicationId);

            ra.addFlashAttribute("success", "Requirement status updated.");

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Status update failed: " + e.getMessage());
        }

        return "redirect:/requirements";
    }


    @Transactional
    @PostMapping("/requirements/bulk-status")
    public String bulkUpdateStatus(@RequestParam(value = "requirementIds", required = false) List<Integer> requirementIds,
                                   @RequestParam Integer statusId,
                                   @RequestParam(required = false) Integer rejectionReasonId,
                                   @RequestParam(required = false) String remarks,
                                   @RequestParam(required = false) Integer applicantId,
                                   RedirectAttributes ra,
                                   HttpServletRequest request) {

        String redirectTarget = applicantId == null ? "redirect:/requirements" : "redirect:/applicants/" + applicantId;

        try {
            if (requirementIds == null || requirementIds.isEmpty()) {
                throw new IllegalArgumentException("Please select at least one submitted document.");
            }

            List<Integer> cleanIds = requirementIds.stream()
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .toList();

            if (cleanIds.isEmpty()) {
                throw new IllegalArgumentException("Please select at least one valid submitted document.");
            }

            if (statusId == 4 && rejectionReasonId == null) {
                throw new IllegalArgumentException("Please select a rejection reason when rejecting documents.");
            }

            String newStatus = jdbc.queryForObject("""
                    SELECT requirement_status_name
                    FROM requirement_status
                    WHERE status_id = ?
                    """, String.class, statusId);

            String placeholders = String.join(",", cleanIds.stream().map(id -> "?").toList());
            List<Object> params = new ArrayList<>(cleanIds);

            String selectedDocumentsSql = """
                    SELECT
                        r.requirement_id,
                        r.application_id,
                        r.requirement_tracking_no,
                        rt.requirement_type_name,
                        rs.requirement_status_name AS old_status_name,
                        a.application_reference_number,
                        ap.applicant_id,
                        ap.applicant_first_name,
                        ap.applicant_last_name,
                        ap.applicant_email_address
                    FROM requirement r
                    JOIN requirement_type rt ON rt.type_id = r.requirement_type_id
                    JOIN requirement_status rs ON rs.status_id = r.requirement_status_id
                    JOIN application a ON a.application_id = r.application_id
                    JOIN applicant ap ON ap.applicant_id = a.applicant_id
                    WHERE COALESCE(ap.applicant_is_deleted, 0) = 0
                      AND r.requirement_id IN (%s)
                    ORDER BY r.requirement_upload_date DESC, r.requirement_id DESC
                    """.formatted(placeholders);

            List<Map<String, Object>> documents = jdbc.queryForList(selectedDocumentsSql, params.toArray());

            if (documents.isEmpty()) {
                throw new IllegalArgumentException("No active submitted documents were found for the selected records.");
            }

            Integer firstApplicantId = toInteger(documents.get(0).get("applicant_id"));
            Integer firstApplicationId = toInteger(documents.get(0).get("application_id"));

            boolean sameApplicant = documents.stream()
                    .allMatch(row -> firstApplicantId != null && firstApplicantId.equals(toInteger(row.get("applicant_id"))));

            if (!sameApplicant) {
                throw new IllegalArgumentException("Bulk update must be done for one student/applicant at a time.");
            }

            for (Map<String, Object> doc : documents) {
                Integer requirementId = toInteger(doc.get("requirement_id"));
                String oldStatus = stringOrNull(doc.get("old_status_name"));
                String trackingNo = stringOrNull(doc.get("requirement_tracking_no"));
                String documentType = stringOrNull(doc.get("requirement_type_name"));

                updateRequirementStatusRecord(requirementId, statusId, rejectionReasonId, remarks);

                auditLogService.log(
                        getRequirementActionType(statusId),
                        "requirement",
                        requirementId.longValue(),
                        "Bulk updated " + documentType + " [" + trackingNo + "] from " + oldStatus + " to " + newStatus,
                        oldStatus,
                        newStatus
                );
            }

            syncAutoAdmissionStatus(firstApplicationId);
            String emailResult = sendBulkRequirementStatusEmailSafe(documents, newStatus, request);

            auditLogService.log(
                    "BULK_DOCUMENT_STATUS_UPDATE",
                    "requirement",
                    firstApplicationId == null ? null : firstApplicationId.longValue(),
                    "Bulk updated " + documents.size() + " document(s) to " + newStatus + " for "
                            + documents.get(0).get("applicant_first_name") + " "
                            + documents.get(0).get("applicant_last_name"),
                    null,
                    emailResult
            );

            ra.addFlashAttribute("success", "Updated " + documents.size() + " selected document(s) to " + newStatus + ". " + emailResult);

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Bulk status update failed: " + e.getMessage());
        }

        return redirectTarget;
    }

    private void updateRequirementStatusRecord(Integer requirementId,
                                               Integer statusId,
                                               Integer rejectionReasonId,
                                               String remarks) {
        if (statusId == 3) {
            jdbc.update("""
                    UPDATE requirement
                    SET requirement_status_id = 3,
                        requirement_date_received = CURRENT_TIMESTAMP,
                        requirement_processed_by_user_id = 1,
                        requirement_processed_at = CURRENT_TIMESTAMP,
                        rejection_reason_id = NULL,
                        rejection_reason_rejected_by_user_id = NULL,
                        rejection_reason_rejected_at = NULL,
                        requirement_remarks = NULL
                    WHERE requirement_id = ?
                    """, requirementId);
            return;
        }

        if (statusId == 4) {
            jdbc.update("""
                    UPDATE requirement
                    SET requirement_status_id = 4,
                        rejection_reason_id = ?,
                        rejection_reason_rejected_by_user_id = 1,
                        rejection_reason_rejected_at = CURRENT_TIMESTAMP,
                        requirement_processed_by_user_id = 1,
                        requirement_processed_at = CURRENT_TIMESTAMP,
                        requirement_remarks = ?
                    WHERE requirement_id = ?
                    """, rejectionReasonId, blankToNull(remarks), requirementId);
            return;
        }

        if (statusId == 5) {
            jdbc.update("""
                    UPDATE requirement
                    SET requirement_status_id = 5,
                        requirement_remarks = ?,
                        requirement_processed_by_user_id = 1,
                        requirement_processed_at = CURRENT_TIMESTAMP
                    WHERE requirement_id = ?
                    """, blankToNull(remarks), requirementId);
            return;
        }

        jdbc.update("""
                UPDATE requirement
                SET requirement_status_id = ?,
                    requirement_processed_by_user_id = 1,
                    requirement_processed_at = CURRENT_TIMESTAMP
                WHERE requirement_id = ?
                """, statusId, requirementId);
    }

    @GetMapping("/uploads/requirements/**")
    public ResponseEntity<Resource> serveRequirementFile(HttpServletRequest request) throws MalformedURLException {
        String uri = request.getRequestURI();
        String prefix = "/uploads/requirements/";
        int prefixIndex = uri.indexOf(prefix);

        if (prefixIndex < 0) {
            return ResponseEntity.notFound().build();
        }

        String storagePath = URLDecoder.decode(
                uri.substring(prefixIndex + prefix.length()),
                StandardCharsets.UTF_8
        ).replace("\\", "/");

        while (storagePath.startsWith("/")) {
            storagePath = storagePath.substring(1);
        }

        Path root = requirementUploadRoot();
        Path file = root.resolve(storagePath).normalize();

        if (!file.startsWith(root) || !Files.exists(file) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(file.toUri());
        String contentType = "application/octet-stream";

        try {
            String detectedType = Files.probeContentType(file);
            if (detectedType != null && !detectedType.isBlank()) {
                contentType = detectedType;
            }
        } catch (IOException ignored) {
            // Default content type is used when probing fails.
        }

        String filename = file.getFileName().toString();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename.replace("\"", "") + "\"")
                .body(resource);
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

            return "applications/"
                    + studentFolder
                    + "/"
                    + documentFolder
                    + "/"
                    + storedFileName;

        } catch (Exception ignored) {
            String safeApplicationId = applicationId == null ? "unknown" : String.valueOf(applicationId);
            return "applications/APP-" + safeApplicationId + "/Document/" + UUID.randomUUID() + "-" + safeFileName;
        }
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please choose a file to upload.");
        }

        String originalName = file.getOriginalFilename();
        String extension = getFileExtension(originalName);

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
        return value == null || value.isBlank() || value.equalsIgnoreCase("null")
                ? fallback
                : value;
    }

    private String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }

        String text = String.valueOf(value);
        return text.equalsIgnoreCase("null") ? null : text;
    }

    private void sendRequirementStatusEmailSafe(Integer requirementId,
                                                String newStatus,
                                                String trackingNo,
                                                String documentType,
                                                HttpServletRequest request) {
        try {
            if (resendApiKey == null || resendApiKey.isBlank()
                    || resendFromEmail == null || resendFromEmail.isBlank()) {
                return;
            }

            Map<String, Object> info = jdbc.queryForMap("""
                    SELECT
                        ap.applicant_first_name,
                        ap.applicant_last_name,
                        ap.applicant_email_address,
                        a.application_reference_number
                    FROM requirement r
                    JOIN application a ON a.application_id = r.application_id
                    JOIN applicant ap ON ap.applicant_id = a.applicant_id
                    WHERE r.requirement_id = ?
                    """, requirementId);

            String firstName = String.valueOf(info.get("applicant_first_name"));
            String lastName = String.valueOf(info.get("applicant_last_name"));
            String email = String.valueOf(info.get("applicant_email_address"));
            String referenceNo = String.valueOf(info.get("application_reference_number"));

            if (!shouldSendRequirementEmail(newStatus)) {
                return;
            }

            String trackingUrl = buildPublicTrackingUrl(request, trackingNo);

            String subjectBase = "Pending".equalsIgnoreCase(newStatus)
                    ? getSettingValue("email_ack_receipt_subject", "Acknowledgment: Document Received")
                    : getSettingValue("email_status_update_subject", "PUP Document Tracking Status Update");
            String subject = subjectBase + " - " + referenceNo;

            String configuredMessage = "Pending".equalsIgnoreCase(newStatus)
                    ? getSettingValue("email_ack_receipt_body", getStatusEmailMessage(newStatus))
                    : getSettingValue("email_status_update_body", getStatusEmailMessage(newStatus));
            String statusMessage = escapeHtml(configuredMessage).replace("\n", "<br>");

            String html = """
        <div style="font-family:Arial,sans-serif;color:#222;line-height:1.6;">
            <h2 style="color:#8B0000;">Document Status Update</h2>

            <p>Dear %s %s,</p>

            <p>%s</p>

            <p><strong>Application Reference Number:</strong> %s</p>
            <p><strong>Document Tracking Number:</strong> %s</p>
            <p><strong>Document Type:</strong> %s</p>
            <p><strong>Current Status:</strong> %s</p>

            <p>
                <a href="%s" style="background:#8B0000;color:white;padding:12px 18px;text-decoration:none;border-radius:8px;display:inline-block;">
                    Track Document Status
                </a>
            </p>

            <p style="font-size:13px;color:#555;">
                If the button does not open, copy and paste this link into your browser:<br>
                <a href="%s">%s</a>
            </p>

            <p>Thank you.</p>

            <p style="color:#666;font-size:13px;">
                PUPOUS Registrar PDTS System
            </p>
        </div>
        """.formatted(
        escapeHtml(firstName),
        escapeHtml(lastName),
        statusMessage,
        escapeHtml(referenceNo),
        escapeHtml(trackingNo),
        escapeHtml(documentType),
        escapeHtml(newStatus),
        trackingUrl,
        trackingUrl,
        escapeHtml(trackingUrl)
);

            
            sendEmail(email, subject, html);

            auditLogService.log(
                    "SEND_EMAIL",
                    "requirement",
                    requirementId.longValue(),
                    "Sent automatic status email to " + email,
                    null,
                    "Status: " + newStatus + " | Tracking: " + trackingNo
            );

        } catch (Exception ignored) {
            // Status update must not fail if email sending fails.
        }
    }


    private String sendSubmissionUploadEmailSafe(Integer applicationId,
                                                 List<Map<String, Object>> documents,
                                                 String trackingNo,
                                                 HttpServletRequest request) {
        try {
            if (documents == null || documents.isEmpty()) {
                return "No acknowledgement email was sent because no document was uploaded.";
            }

            if (resendApiKey == null || resendApiKey.isBlank()
                    || resendFromEmail == null || resendFromEmail.isBlank()) {
                return "Acknowledgement email was not sent because RESEND_API_KEY or RESEND_FROM_EMAIL is missing.";
            }

            if (!shouldSendRequirementEmail("Pending")) {
                return "Acknowledgement email was not sent because acknowledgement emails are disabled in Email Notifications.";
            }

            Map<String, Object> info = jdbc.queryForMap("""
                    SELECT
                        ap.applicant_first_name,
                        ap.applicant_last_name,
                        ap.applicant_email_address,
                        a.application_reference_number
                    FROM application a
                    JOIN applicant ap ON ap.applicant_id = a.applicant_id
                    WHERE a.application_id = ?
                    """, applicationId);

            String firstName = stringOrNull(info.get("applicant_first_name"));
            String lastName = stringOrNull(info.get("applicant_last_name"));
            String email = stringOrNull(info.get("applicant_email_address"));
            String referenceNo = stringOrNull(info.get("application_reference_number"));

            if (email == null || email.isBlank()) {
                return "Acknowledgement email was not sent because the applicant has no email address.";
            }

            String subjectBase = getSettingValue("email_ack_receipt_subject", "Acknowledgment: Document Received");
            String subject = subjectBase + " - " + referenceNo;

            String configuredMessage = getSettingValue(
                    "email_ack_receipt_body",
                    "Your submitted document(s) have been received by the PDTS system and are now marked as Pending. Please use the tracking portal to monitor the status of your submission."
            );
            String statusMessage = escapeHtml(configuredMessage).replace("\n", "<br>");
            String trackingUrl = buildPublicTrackingUrl(request, trackingNo);

            StringBuilder rows = new StringBuilder();
            for (Map<String, Object> doc : documents) {
                String documentType = stringOrNull(doc.get("requirement_type_name"));
                rows.append("""
                        <tr>
                            <td style="padding:10px;border-bottom:1px solid #eee;">%s</td>
                            <td style="padding:10px;border-bottom:1px solid #eee;font-weight:bold;">Pending</td>
                        </tr>
                        """.formatted(escapeHtml(documentType)));
            }

            String html = """
                    <div style="font-family:Arial,sans-serif;color:#222;line-height:1.6;">
                        <h2 style="color:#8B0000;">Document Submission Received</h2>

                        <p>Dear %s %s,</p>

                        <p>%s</p>

                        <p><strong>Application Reference Number:</strong> %s</p>
                        <p><strong>Submission Tracking Number:</strong> %s</p>
                        <p><strong>Documents Submitted:</strong> %s</p>
                        <p><strong>Current Status:</strong> Pending</p>

                        <table style="border-collapse:collapse;width:100%%;margin-top:14px;border:1px solid #eee;">
                            <thead>
                                <tr style="background:#F5EDED;color:#8B0000;text-align:left;">
                                    <th style="padding:10px;">Document Type</th>
                                    <th style="padding:10px;">Status</th>
                                </tr>
                            </thead>
                            <tbody>
                                %s
                            </tbody>
                        </table>

                        <p style="margin-top:16px;">
                            <a href="%s" style="background:#8B0000;color:white;padding:12px 18px;text-decoration:none;border-radius:8px;display:inline-block;">
                                Track Submission Status
                            </a>
                        </p>

                        <p style="font-size:13px;color:#555;">
                            If the button does not open, copy and paste this link into your browser:<br>
                            <a href="%s">%s</a>
                        </p>

                        <p style="font-size:13px;color:#555;">
                            This is one acknowledgement email for this upload submission. The same tracking number covers all document(s) listed above.
                        </p>

                        <p>Thank you.</p>

                        <p style="color:#666;font-size:13px;">
                            PUPOUS Registrar PDTS System
                        </p>
                    </div>
                    """.formatted(
                    escapeHtml(firstName),
                    escapeHtml(lastName),
                    statusMessage,
                    escapeHtml(referenceNo),
                    escapeHtml(trackingNo),
                    documents.size(),
                    rows.toString(),
                    trackingUrl,
                    trackingUrl,
                    escapeHtml(trackingUrl)
            );

            sendEmail(email, subject, html);

            jdbc.update("""
                    UPDATE requirement
                    SET requirement_is_email_sent = 1
                    WHERE application_id = ?
                      AND requirement_tracking_no = ?
                    """, applicationId, trackingNo);

            Long firstRequirementId = null;
            Object rawId = documents.get(0).get("requirement_id");
            if (rawId instanceof Number number) {
                firstRequirementId = number.longValue();
            }

            auditLogService.log(
                    "SEND_EMAIL",
                    "requirement",
                    firstRequirementId,
                    "Sent one consolidated upload acknowledgement email to " + email,
                    null,
                    "Tracking: " + trackingNo + " | Documents: " + documents.size()
            );

            return "One consolidated acknowledgement email was sent to " + email + ".";

        } catch (Exception e) {
            return "Documents were uploaded, but acknowledgement email sending failed: " + e.getMessage();
        }
    }

    private String getStatusEmailMessage(String status) {

    if ("Pending".equalsIgnoreCase(status)) {
        return "Your submitted document has been received by the PDTS system and is now marked as Pending. Please use the tracking portal to monitor the status of your document.";
    }

    if ("Verified/Received".equalsIgnoreCase(status)) {
        return "Your submitted document has been verified and received. You may continue tracking your application status through the tracking portal.";
    }

    if ("Under Review".equalsIgnoreCase(status)) {
        return "Your submitted document is still under review. Please continue checking the tracking portal for further updates.";
    }

    if ("For Resubmission".equalsIgnoreCase(status)) {
        return "Your submitted document requires resubmission. Please review the remarks or instructions in the tracking portal, prepare the corrected document, and re-upload it for further evaluation.";
    }

    if ("Rejected".equalsIgnoreCase(status)) {
        return "Your submitted document was rejected. Please check if the uploaded document is correct, clear, complete, and matches the required document type. Kindly prepare the correct file and resubmit it through the system.";
    }

    return "Your submitted document status has been updated. Please check the tracking portal for details.";
}


    private String sendBulkRequirementStatusEmailSafe(List<Map<String, Object>> documents,
                                                       String newStatus,
                                                       HttpServletRequest request) {
        try {
            if (documents == null || documents.isEmpty()) {
                return "No email was sent because there were no selected documents.";
            }

            if (resendApiKey == null || resendApiKey.isBlank()
                    || resendFromEmail == null || resendFromEmail.isBlank()) {
                return "Email was not sent because RESEND_API_KEY or RESEND_FROM_EMAIL is missing.";
            }

            if (!shouldSendRequirementEmail(newStatus)) {
                return "Email was not sent because document status emails are disabled in Email Notifications.";
            }

            Map<String, Object> first = documents.get(0);
            String firstName = stringOrNull(first.get("applicant_first_name"));
            String lastName = stringOrNull(first.get("applicant_last_name"));
            String email = stringOrNull(first.get("applicant_email_address"));
            String referenceNo = stringOrNull(first.get("application_reference_number"));

            if (email == null || email.isBlank()) {
                return "Email was not sent because the applicant has no email address.";
            }

            String subjectBase = getSettingValue("email_status_update_subject", "PUP Document Tracking Status Update");
            String subject = subjectBase + " - " + referenceNo + " (" + documents.size() + " documents)";

            String configuredMessage = getSettingValue(
                    "email_status_update_body",
                    "The Registrar has updated the status of your submitted document(s). Please review the details below."
            );
            String statusMessage = escapeHtml(configuredMessage).replace("\n", "<br>");

            StringBuilder rows = new StringBuilder();
            for (Map<String, Object> doc : documents) {
                String trackingNo = stringOrNull(doc.get("requirement_tracking_no"));
                String documentType = stringOrNull(doc.get("requirement_type_name"));
                String trackingUrl = buildPublicTrackingUrl(request, trackingNo);

                rows.append("""
                        <tr>
                            <td style="padding:10px;border-bottom:1px solid #eee;font-weight:bold;color:#7C4A03;">%s</td>
                            <td style="padding:10px;border-bottom:1px solid #eee;">%s</td>
                            <td style="padding:10px;border-bottom:1px solid #eee;font-weight:bold;">%s</td>
                            <td style="padding:10px;border-bottom:1px solid #eee;"><a href="%s">Track</a></td>
                        </tr>
                        """.formatted(
                        escapeHtml(trackingNo),
                        escapeHtml(documentType),
                        escapeHtml(newStatus),
                        trackingUrl
                ));
            }

            String html = """
                    <div style="font-family:Arial,sans-serif;color:#222;line-height:1.6;">
                        <h2 style="color:#8B0000;">Document Status Update</h2>

                        <p>Dear %s %s,</p>

                        <p>%s</p>

                        <p><strong>Application Reference Number:</strong> %s</p>
                        <p><strong>Documents Updated:</strong> %s</p>

                        <table style="border-collapse:collapse;width:100%%;margin-top:14px;border:1px solid #eee;">
                            <thead>
                                <tr style="background:#F5EDED;color:#8B0000;text-align:left;">
                                    <th style="padding:10px;">Tracking No.</th>
                                    <th style="padding:10px;">Document Type</th>
                                    <th style="padding:10px;">New Status</th>
                                    <th style="padding:10px;">Public Tracker</th>
                                </tr>
                            </thead>
                            <tbody>
                                %s
                            </tbody>
                        </table>

                        <p style="font-size:13px;color:#555;margin-top:16px;">
                            For privacy, the public tracker shows document status only. Please use each tracking number when checking individual document updates.
                        </p>

                        <p>Thank you.</p>

                        <p style="color:#666;font-size:13px;">
                            PUPOUS Registrar PDTS System
                        </p>
                    </div>
                    """.formatted(
                    escapeHtml(firstName),
                    escapeHtml(lastName),
                    statusMessage,
                    escapeHtml(referenceNo),
                    documents.size(),
                    rows.toString()
            );

            sendEmail(email, subject, html);

            auditLogService.log(
                    "SEND_BULK_EMAIL",
                    "requirement",
                    toInteger(first.get("requirement_id")) == 0 ? null : Long.valueOf(toInteger(first.get("requirement_id"))),
                    "Sent consolidated document status email to " + email,
                    null,
                    "Status: " + newStatus + " | Documents: " + documents.size()
            );

            return "One consolidated email was sent to " + email + ".";

        } catch (Exception e) {
            return "Documents were updated, but email sending failed: " + e.getMessage();
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void sendEmail(String toEmail, String subject, String html) throws Exception {
        String body = """
                {
                  "from": "%s",
                  "to": ["%s"],
                  "subject": "%s",
                  "html": "%s"
                }
                """.formatted(
                escapeJson(resendFromEmail),
                escapeJson(toEmail),
                escapeJson(subject),
                escapeJson(html)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Resend email failed: " + response.body());
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }


    private String buildPublicTrackingUrl(HttpServletRequest request, String referenceNo) {
        String encodedTrackingNo = URLEncoder.encode(referenceNo == null ? "" : referenceNo, StandardCharsets.UTF_8);
        return buildBaseUrl(request) + "/track?trackingNumber=" + encodedTrackingNo;
    }

    private String buildBaseUrl(HttpServletRequest request) {
        if (appBaseUrl != null && !appBaseUrl.isBlank()) {
            return appBaseUrl.replaceAll("/+$", "");
        }

        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }

        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getServerName();
            int port = request.getServerPort();
            boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                    || ("https".equalsIgnoreCase(scheme) && port == 443);
            if (!defaultPort) {
                host = host + ":" + port;
            }
        }

        return scheme + "://" + host;
    }

    private String getRequirementTypeName(Integer requirementTypeId) {
        try {
            return jdbc.queryForObject("""
                    SELECT requirement_type_name
                    FROM requirement_type
                    WHERE type_id = ?
                    """, String.class, requirementTypeId);
        } catch (Exception e) {
            return "Document";
        }
    }

    private boolean shouldSendRequirementEmail(String status) {
        if ("Pending".equalsIgnoreCase(status)) {
            return getBooleanSetting("email_ack_receipt_enabled", true);
        }
        return getBooleanSetting("email_status_update_enabled", true);
    }

    private boolean getBooleanSetting(String key, boolean fallback) {
        String value = getSettingValue(key, fallback ? "true" : "false");
        return "true".equalsIgnoreCase(value)
                || "1".equals(value)
                || "yes".equalsIgnoreCase(value)
                || "on".equalsIgnoreCase(value)
                || "active".equalsIgnoreCase(value);
    }

    private String getSettingValue(String key, String fallback) {
        try {
            String value = jdbc.queryForObject("""
                    SELECT setting_value
                    FROM system_setting
                    WHERE setting_key = ?
                    LIMIT 1
                    """, String.class, key);
            return value == null || value.isBlank() ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
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

    private String getRequirementActionType(Integer statusId) {
        if (statusId == 3) return "RECEIVE_DOCUMENT";
        if (statusId == 4) return "REJECT_DOCUMENT";
        if (statusId == 5) return "FOR_RESUBMISSION";
        if (statusId == 2) return "UNDER_REVIEW";
        return "UPDATE_DOCUMENT_STATUS";
    }

    private boolean requirementBelongsToActiveApplicant(Integer requirementId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM requirement r
                JOIN application a ON a.application_id = r.application_id
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                WHERE r.requirement_id = ?
                  AND COALESCE(ap.applicant_is_deleted, 0) = 0
                """, Integer.class, requirementId);

        return count != null && count > 0;
    }

    private void addLookups(Model model) {
        model.addAttribute("statuses", jdbc.queryForList("""
                SELECT status_id, requirement_status_name
                FROM requirement_status
                ORDER BY status_id
                """));

        model.addAttribute("types", jdbc.queryForList("""
                SELECT type_id, requirement_type_name
                FROM requirement_type
                WHERE type_is_active = 1
                ORDER BY requirement_type_name
                """));

        model.addAttribute("rejectionReasons", jdbc.queryForList("""
                SELECT rejection_reason_id, rejection_reason_name
                FROM rejection_reason
                WHERE rejection_reason_is_active = 1
                ORDER BY rejection_reason_name
                """));

      model.addAttribute("applications", jdbc.queryForList("""
        SELECT
            a.application_id,
            a.application_reference_number,
            ap.applicant_first_name,
            ap.applicant_last_name
            
                FROM application a
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                WHERE COALESCE(ap.applicant_is_deleted, 0) = 0
                ORDER BY a.application_id DESC
                """));
    }

   private boolean requirementTypeBelongsToApplicationCurriculum(Integer applicationId, Integer requirementTypeId) {
    Integer count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM application a
            JOIN applicant ap
                ON ap.applicant_id = a.applicant_id
            WHERE a.application_id = ?
              AND (
                    EXISTS (
                        SELECT 1
                        FROM curriculum_requirement cr
                        WHERE cr.category_id = ap.educational_background_category_id
                          AND cr.type_id = ?
                    )
                    OR (? = 17 AND ap.applicant_employment_status = 'Employed')
                    OR (? = 8  AND ap.applicant_employment_status = 'Unemployed')
                    OR (? = 12 AND ap.applicant_sex = 2 AND ap.applicant_civil_status = 2)
                    OR (? = 22 AND ap.applicant_school_records_available = 0)
              )
            """,
            Integer.class,
            applicationId,
            requirementTypeId,
            requirementTypeId,
            requirementTypeId,
            requirementTypeId,
            requirementTypeId
    );

    return count != null && count > 0;
}

    private boolean requirementAlreadyLogged(Integer applicationId, Integer requirementTypeId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM requirement
                WHERE application_id = ?
                  AND requirement_type_id = ?
                """, Integer.class, applicationId, requirementTypeId);

        return count != null && count > 0;
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
                JOIN LATERAL (
                    SELECT cr.type_id, cr.is_mandatory
                    FROM curriculum_requirement cr
                    WHERE cr.category_id = ap.educational_background_category_id

                    UNION
                    SELECT 17, 1
                    WHERE ap.applicant_employment_status = 'Employed'

                    UNION
                    SELECT 8, 1
                    WHERE ap.applicant_employment_status = 'Unemployed'

                    UNION
                    SELECT 12, 1
                    WHERE ap.applicant_sex = 2
                    AND ap.applicant_civil_status = 2

                    UNION
                    SELECT 22, 1
                    WHERE ap.applicant_school_records_available = 0
                                                       
                ) cr ON TRUE
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
                    JOIN application_status ast
                        ON ast.application_status_id = a.application_status_id
                    WHERE a.application_id = ?
                    """, String.class, applicationId);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isManualFinalAdmissionStatus(String status) {
        if (status == null) {
            return false;
        }

        String normalized = status.trim();
        return "Non-Compliant".equalsIgnoreCase(normalized)
                || "Did Not Continue".equalsIgnoreCase(normalized)
                || "Cancelled".equalsIgnoreCase(normalized);
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

    private int toInteger(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private String nextTrackingNo() {
        return trackingNumberService.generateDocumentNumber();
    }
}
