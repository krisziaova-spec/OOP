package com.pdts.controller;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Date;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pdts.service.AuditLogService;

@Controller
public class ApplicantPageController {

    private static final String DUMMY_MULTI_ACCOUNT_EMAIL = "krisziamaehpamintuan@iskolarngbayan.pup.edu.ph";

    private final JdbcTemplate jdbc;
    private final AuditLogService auditLogService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

private final String resendApiKey = firstEnv("RESEND_API_KEY", "EMAIL_API_KEY");
private final String resendFromEmail = firstEnv("RESEND_FROM_EMAIL", "EMAIL_FROM", "FROM_EMAIL");
private final String appBaseUrl = firstEnv("APP_BASE_URL") != null
        ? firstEnv("APP_BASE_URL")
        : "https://pdts-im.onrender.com";

    public ApplicantPageController(JdbcTemplate jdbc, AuditLogService auditLogService) {
        this.jdbc = jdbc;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/applicants")
    public String list(@RequestParam(required = false) String search,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String program,
                       @RequestParam(required = false) String enrollment,
                       @RequestParam(required = false) String admissionStatus,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String reportStatus,
                       @RequestParam(required = false) String region,
                       Model model) {

        StringBuilder sql = new StringBuilder("""
                SELECT
                    ap.applicant_id,
                    ap.applicant_first_name,
                    ap.applicant_last_name,
                    ap.applicant_email_address,
                    ap.applicant_contact_number,
                    ap.applicant_region,
                    ap.applicant_enrollment_status,
                    ap.applicant_created_at,
                    e.category_id,
                    e.category_name,
                    latest_app.application_reference_number,
                    latest_app.program_code,
                    latest_app.program_name,
                    latest_app.application_status_name,
                    latest_app.application_status_color,
                    COALESCE(
                        REPLACE(latest_app.application_reference_number, 'APP-', 'STU-'),
                        'STU-' || ap.applicant_id
                    ) AS student_tracking_no
                FROM applicant ap
                JOIN educational_background_category e
                    ON e.category_id = ap.educational_background_category_id
                LEFT JOIN LATERAL (
                    SELECT
                        a.application_id,
                        a.application_reference_number,
                        a.program_id,
                        p.program_code,
                        p.program_name,
                        ast.application_status_name,
                        ast.application_status_color
                    FROM application a
                    LEFT JOIN program p
                        ON p.program_id = a.program_id
                    LEFT JOIN application_status ast
                        ON ast.application_status_id = a.application_status_id
                    WHERE a.applicant_id = ap.applicant_id
                    ORDER BY a.application_date DESC, a.application_id DESC
                    LIMIT 1
                ) latest_app ON TRUE
                WHERE COALESCE(ap.applicant_is_deleted, 0) = 0
                """);

        List<Object> params = new ArrayList<>();

        if (search != null && !search.isBlank()) {
            sql.append("""
                    AND (
                        LOWER(ap.applicant_first_name) LIKE LOWER(?)
                        OR LOWER(ap.applicant_last_name) LIKE LOWER(?)
                        OR LOWER(ap.applicant_email_address) LIKE LOWER(?)
                        OR LOWER(COALESCE(ap.applicant_contact_number, '')) LIKE LOWER(?)
                        OR LOWER(COALESCE(latest_app.application_reference_number, '')) LIKE LOWER(?)
                        OR LOWER(COALESCE(REPLACE(latest_app.application_reference_number, 'APP-', 'STU-'), '')) LIKE LOWER(?)
                    )
                    """);

            String q = "%" + search.trim() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
        }

        if (category != null && !category.isBlank()) {
            sql.append(" AND ap.educational_background_category_id = ?");
            params.add(category.trim());
        }

        Integer selectedProgramId = parseInteger(program);
        if (selectedProgramId != null) {
            sql.append(" AND latest_app.program_id = ?");
            params.add(selectedProgramId);
        }

        if (enrollment != null && !enrollment.isBlank()) {
            sql.append(" AND ap.applicant_enrollment_status = ?");
            params.add(enrollment.trim());
        }

        String selectedAdmissionStatus = firstNonBlank(admissionStatus, status);
        if (selectedAdmissionStatus != null && !selectedAdmissionStatus.isBlank()) {
            sql.append(" AND latest_app.application_status_name = ?");
            params.add(selectedAdmissionStatus.trim());
        }

        String cleanReportStatus = reportStatus == null ? "" : reportStatus.trim();
        if ("continuing".equals(cleanReportStatus) || "on_leave".equals(cleanReportStatus)) {
            sql.append(" AND ap.applicant_enrollment_status = ?");
            params.add(cleanReportStatus);
            
       } else if ("cleared".equals(cleanReportStatus)) {
    sql.append(" AND latest_app.application_status_name = ? ");
    params.add("Enrolled");
}

            
        } else if ("pending".equals(cleanReportStatus)) {
            sql.append("""
                    AND EXISTS (
                        SELECT 1
                        FROM requirement r_pending
                        JOIN requirement_status rs_pending
                          ON rs_pending.status_id = r_pending.requirement_status_id
                        WHERE r_pending.application_id = latest_app.application_id
                          AND rs_pending.requirement_status_name <> 'Verified/Received'
                    )
                    """);
        }

        if (region != null && !region.isBlank()) {
            if ("Unspecified".equalsIgnoreCase(region.trim())) {
                sql.append(" AND (ap.applicant_region IS NULL OR TRIM(ap.applicant_region) = '')");
            } else {
                sql.append(" AND LOWER(ap.applicant_region) LIKE LOWER(?)");
                params.add("%" + region.trim() + "%");
            }
        }

        sql.append(" ORDER BY ap.applicant_created_at DESC, ap.applicant_id DESC");

        model.addAttribute("applicants", jdbc.queryForList(sql.toString(), params.toArray()));

        model.addAttribute("categories", jdbc.queryForList("""
                SELECT category_id, category_name
                FROM educational_background_category
                WHERE category_is_active = 1
                ORDER BY category_name
                """));

        model.addAttribute("applicationStatuses", jdbc.queryForList("""
                SELECT application_status_id, application_status_name, application_status_color
                FROM application_status
                ORDER BY application_status_id
                """));

        model.addAttribute("programs", jdbc.queryForList("""
                SELECT program_id, program_code, program_name
                FROM program
                ORDER BY program_code, program_name
                """));

        model.addAttribute("search", search);
        model.addAttribute("category", category);
        model.addAttribute("program", program);
        model.addAttribute("enrollment", enrollment);
        model.addAttribute("admissionStatus", selectedAdmissionStatus);
        model.addAttribute("reportStatus", cleanReportStatus);
        model.addAttribute("region", region);
        model.addAttribute("reportFilterLabel", buildReportFilterLabel(region, category, program, cleanReportStatus));

        return "applicants";
    }

    @GetMapping("/applicants/new")
    public String newForm(Model model) {
        addLookups(model);
        model.addAttribute("mode", "create");
        model.addAttribute("emergencyContact", null);
        return "applicant-form";
    }

    @Transactional
    @PostMapping("/applicants")
    public String create(@RequestParam Map<String, String> form, RedirectAttributes ra) {
        try {
            String email = normalizeEmail(required(form, "email"));
            validateStudentEmailUniqueness(email, null);

            Integer applicantId = jdbc.queryForObject("""
                    INSERT INTO applicant (
                        applicant_first_name,
                        applicant_middle_name,
                        applicant_last_name,
                        applicant_suffix,
                        applicant_sex,
                        applicant_civil_status,
                        applicant_house_number_street,
                        applicant_barangay,
                        applicant_city_municipality,
                        applicant_province,
                        applicant_region,
                        applicant_zip_code,
                        applicant_birth_date,
                        applicant_email_address,
                        applicant_contact_number,
                        educational_background_category_id,
                       applicant_enrollment_status,
                       applicant_employment_status,
                       applicant_uses_husband_surname,
                       applicant_school_records_available,
                       user_id
                       ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                    RETURNING applicant_id
                    """,
                    Integer.class,
                    required(form, "firstName"),
                    blankToNull(form.get("middleName")),
                    required(form, "lastName"),
                    blankToNull(form.get("suffix")),
                    intValue(form, "sex", 1),
                    intValue(form, "civilStatus", 1),
                    blankToNull(form.get("street")),
                    blankToNull(form.get("barangay")),
                    blankToNull(form.get("city")),
                    blankToNull(form.get("province")),
                    blankToNull(form.get("region")),
                    blankToNull(form.get("zipCode")),
                    Date.valueOf(required(form, "birthDate")),
                    email,
                    required(form, "contactNumber"),
                   required(form, "categoryId"),
                   required(form, "enrollmentStatus"),
                   blankToNull(form.get("employmentStatus")),
                   intValue(form, "usesHusbandSurname", 0),
                   intValue(form, "schoolRecordsAvailable", 1)
            );

            String referenceNo = nextApplicationReference();

            jdbc.update("""
                    INSERT INTO application (
                        applicant_id,
                        program_id,
                        campus_id,
                        application_status_id,
                        application_date,
                        application_semester,
                        application_academic_year,
                        application_reference_number
                    ) VALUES (?, ?, ?, ?, CURRENT_DATE, ?, ?, ?)
                    """,
                    applicantId,
                    intValue(form, "programId", 1),
                    intValue(form, "campusId", 1),
                    intValue(form, "applicationStatusId", 1),
                    required(form, "semester"),
                    required(form, "academicYear"),
                    referenceNo
            );

            upsertEmergencyContact(applicantId, form);

            auditLogService.log(
                    "CREATE_STUDENT",
                    "applicant",
                    applicantId.longValue(),
                    "Created profile for " + required(form, "firstName") + " " + required(form, "lastName"),
                    null,
                    "Application reference: " + referenceNo
            );

            sendApplicantPendingEmailSafe(
        email,
        required(form, "firstName"),
        required(form, "lastName"),
        referenceNo
);

            ra.addFlashAttribute("success", "Applicant created with application reference " + referenceNo + ".");
            return "redirect:/applicants/" + applicantId;

        } catch (DuplicateKeyException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            ra.addFlashAttribute("error", "Email address or generated reference number already exists. Please try again.");
            return "redirect:/applicants/new";

        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            ra.addFlashAttribute("error", "Create failed: " + e.getMessage());
            return "redirect:/applicants/new";
        }
    }

    @GetMapping("/applicants/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        model.addAttribute("applicant", one("""
                SELECT ap.*, e.category_name
                FROM applicant ap
                JOIN educational_background_category e
                    ON e.category_id = ap.educational_background_category_id
                WHERE ap.applicant_id = ?
                  AND COALESCE(ap.applicant_is_deleted, 0) = 0
                """, id));

        List<Map<String, Object>> applications = jdbc.queryForList("""
                SELECT
                    a.*,
                    p.program_name,
                    p.program_code,
                    c.campus_name,
                    ast.application_status_name
                FROM application a
                JOIN program p
                    ON p.program_id = a.program_id
                JOIN campus c
                    ON c.campus_id = a.campus_id
                JOIN application_status ast
                    ON ast.application_status_id = a.application_status_id
                WHERE a.applicant_id = ?
                ORDER BY a.application_date DESC, a.application_id DESC
                """, id);

        Map<String, Object> latestApplication = applications.isEmpty() ? null : applications.get(0);
        model.addAttribute("applications", applications);
        model.addAttribute("latestApplication", latestApplication);
        model.addAttribute("latestApplicationId", latestApplication == null ? null : latestApplication.get("application_id"));
        model.addAttribute("studentDisplayNo", latestApplication == null
                ? "STU-" + id
                : String.valueOf(latestApplication.get("application_reference_number")).replace("APP-", "STU-"));

        model.addAttribute("emergencyContacts", jdbc.queryForList("""
                SELECT
                    contact_name,
                    relationship,
                    contact_number,
                    contact_address
                FROM applicant_emergency_contact
                WHERE applicant_id = ?
                ORDER BY contact_id
                """, id));

        List<Map<String, Object>> requirementChecklist = jdbc.queryForList("""
                SELECT
                    a.application_id,
                    a.application_reference_number,
                    rt.type_id,
                    rt.requirement_type_name,
                    cr.is_mandatory,
                    latest_req.requirement_id,
                    latest_req.requirement_tracking_no,
                    latest_req.requirement_upload_date,
                    TO_CHAR(latest_req.requirement_upload_date, 'Mon DD, YYYY HH12:MI AM') AS requirement_upload_date_display,
                    latest_req.requirement_status_id,
                    COALESCE(rs.requirement_status_name, 'Missing') AS requirement_status_name,
                    COALESCE(d.deadline_date, a.application_date + 30) AS requirement_due_date,
                    CASE
                        WHEN latest_req.requirement_status_id = 3 THEN 'Cleared'
                        WHEN COALESCE(d.deadline_date, a.application_date + 30) < CURRENT_DATE THEN 'Overdue'
                        WHEN COALESCE(d.deadline_date, a.application_date + 30) <= CURRENT_DATE + 7 THEN 'Due Soon'
                        ELSE 'On Track'
                    END AS requirement_timeline_label,
                    CASE
                        WHEN latest_req.requirement_status_id = 3 THEN 'Low'
                        WHEN latest_req.requirement_status_id IN (4, 5) THEN 'High'
                        WHEN COALESCE(d.deadline_date, a.application_date + 30) < CURRENT_DATE THEN 'High'
                        WHEN COALESCE(d.deadline_date, a.application_date + 30) <= CURRENT_DATE + 7 THEN 'Medium'
                        WHEN latest_req.requirement_id IS NULL THEN 'Medium'
                        ELSE 'Normal'
                    END AS requirement_priority_label,
                    CASE WHEN latest_req.requirement_id IS NULL THEN 0 ELSE 1 END AS is_submitted,
                    CASE WHEN latest_req.requirement_status_id = 3 THEN 1 ELSE 0 END AS is_verified
               FROM application a
JOIN applicant ap
    ON ap.applicant_id = a.applicant_id
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
JOIN requirement_type rt
    ON rt.type_id = cr.type_id
                                                                           
                   AND COALESCE(rt.type_is_active, 1) = 1
                LEFT JOIN LATERAL (
                    SELECT r.*
                    FROM requirement r
                    WHERE r.application_id = a.application_id
                      AND r.requirement_type_id = rt.type_id
                    ORDER BY r.requirement_upload_date DESC, r.requirement_id DESC
                    LIMIT 1
                ) latest_req ON TRUE
                LEFT JOIN requirement_status rs
                    ON rs.status_id = latest_req.requirement_status_id
                LEFT JOIN deadline d
                    ON d.requirement_type_id = rt.type_id
                WHERE a.applicant_id = ?
                ORDER BY a.application_date DESC, a.application_id DESC, cr.is_mandatory DESC, rt.requirement_type_name
                """, id);

        Map<String, Object> documentSummary = buildDocumentSummary(requirementChecklist);

        if (latestApplication != null) {
            Integer applicationId = toInteger(latestApplication.get("application_id"));
            String currentStatus = stringOrNull(latestApplication.get("application_status_name"));
            String displayStatus = syncAutoAdmissionStatus(applicationId, documentSummary, currentStatus);
            if (displayStatus != null && !displayStatus.isBlank()) {
                latestApplication.put("application_status_name", displayStatus);
            }
        }

        model.addAttribute("requirementChecklist", requirementChecklist);
        model.addAttribute("documentSummary", documentSummary);
        model.addAttribute("statuses", jdbc.queryForList("""
                SELECT status_id, requirement_status_name
                FROM requirement_status
                ORDER BY status_id
                """));
        model.addAttribute("rejectionReasons", jdbc.queryForList("""
                SELECT rejection_reason_id, rejection_reason_name
                FROM rejection_reason
                WHERE rejection_reason_is_active = 1
                ORDER BY rejection_reason_name
                """));

        return "applicant-detail";
    }

    @GetMapping("/applicants/{id}/edit")
    public String editForm(@PathVariable Integer id, Model model) {
        addLookups(model);
        model.addAttribute("mode", "edit");
        model.addAttribute("applicant", one("""
                SELECT *
                FROM applicant
                WHERE applicant_id = ?
                  AND COALESCE(applicant_is_deleted, 0) = 0
                """, id));
        model.addAttribute("latestApplication", latestApplicationForApplicant(id));
        model.addAttribute("emergencyContact", primaryEmergencyContact(id));
        return "applicant-form";
    }

    @Transactional
    @PostMapping("/applicants/{id}/edit")
    public String update(@PathVariable Integer id,
                         @RequestParam Map<String, String> form,
                         RedirectAttributes ra) {
        try {
            String email = normalizeEmail(required(form, "email"));
            validateStudentEmailUniqueness(email, id);

            int updated = jdbc.update("""
                    UPDATE applicant SET
                        applicant_first_name = ?,
                        applicant_middle_name = ?,
                        applicant_last_name = ?,
                        applicant_suffix = ?,
                        applicant_sex = ?,
                        applicant_civil_status = ?,
                        applicant_house_number_street = ?,
                        applicant_barangay = ?,
                        applicant_city_municipality = ?,
                        applicant_province = ?,
                        applicant_region = ?,
                        applicant_zip_code = ?,
                        applicant_birth_date = ?,
                        applicant_email_address = ?,
                        applicant_contact_number = ?,
                        educational_background_category_id = ?,
                       applicant_enrollment_status = ?,
                       applicant_employment_status = ?,
                       applicant_uses_husband_surname = ?,
                       applicant_school_records_available = ?,
                       applicant_updated_at = CURRENT_TIMESTAMP
                    WHERE applicant_id = ?
                      AND COALESCE(applicant_is_deleted, 0) = 0
                    """,
                    required(form, "firstName"),
                    blankToNull(form.get("middleName")),
                    required(form, "lastName"),
                    blankToNull(form.get("suffix")),
                    intValue(form, "sex", 1),
                    intValue(form, "civilStatus", 1),
                    blankToNull(form.get("street")),
                    blankToNull(form.get("barangay")),
                    blankToNull(form.get("city")),
                    blankToNull(form.get("province")),
                    blankToNull(form.get("region")),
                    blankToNull(form.get("zipCode")),
                    Date.valueOf(required(form, "birthDate")),
                    email,
                    required(form, "contactNumber"),
                    required(form, "categoryId"),
                    required(form, "enrollmentStatus"),
                    blankToNull(form.get("employmentStatus")),
                    intValue(form, "usesHusbandSurname", 0),
                    intValue(form, "schoolRecordsAvailable", 1),
                    id
            );

            if (updated > 0) {
                upsertEmergencyContact(id, form);
                updateLatestApplicationStatusIfProvided(id, form);

                auditLogService.log(
                        "UPDATE_STUDENT",
                        "applicant",
                        id.longValue(),
                        "Updated profile for " + required(form, "firstName") + " " + required(form, "lastName"),
                        null,
                        "Enrollment status: " + required(form, "enrollmentStatus")
                );
            }

            ra.addFlashAttribute("success", "Applicant updated.");
            return "redirect:/applicants/" + id;

        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            ra.addFlashAttribute("error", "Update failed: " + e.getMessage());
            return "redirect:/applicants/" + id + "/edit";
        }
    }

    @Transactional
    @PostMapping("/applicants/{id}/delete")
    public String delete(@PathVariable Integer id, RedirectAttributes ra) {
        List<String> applicantNames = jdbc.queryForList("""
                SELECT applicant_first_name || ' ' || applicant_last_name
                FROM applicant
                WHERE applicant_id = ?
                """, String.class, id);

        String applicantName = applicantNames.isEmpty()
                ? "Applicant ID " + id
                : applicantNames.get(0);

        List<String> documentPaths = jdbc.queryForList("""
                SELECT r.requirement_image_path
                FROM requirement r
                JOIN application a
                    ON a.application_id = r.application_id
                WHERE a.applicant_id = ?
                """, String.class, id);

        int documentsDeleted = jdbc.update("""
                DELETE FROM requirement r
                USING application a
                WHERE r.application_id = a.application_id
                  AND a.applicant_id = ?
                """, id);

        int updated = jdbc.update("""
                UPDATE applicant
                SET applicant_is_deleted = 1,
                    applicant_deleted_at = CURRENT_TIMESTAMP,
                    applicant_updated_at = CURRENT_TIMESTAMP
                WHERE applicant_id = ?
                  AND COALESCE(applicant_is_deleted, 0) = 0
                """, id);

        if (updated > 0) {
            int filesDeleted = deleteUploadedFiles(documentPaths);

            auditLogService.log(
                    "DELETE_STUDENT",
                    "applicant",
                    id.longValue(),
                    "Deleted profile for " + applicantName,
                    null,
                    "Deleted " + documentsDeleted + " document record(s) and " + filesDeleted + " uploaded file(s)."
            );

            ra.addFlashAttribute("success", "Applicant deleted from the active list. Deleted "
                    + documentsDeleted + " related document record(s) and "
                    + filesDeleted + " uploaded file(s).");

        } else {
            ra.addFlashAttribute("error", "Applicant was not found or was already deleted.");
        }

        return "redirect:/applicants";
    }

    private Map<String, Object> latestApplicationForApplicant(Integer applicantId) {
        List<Map<String, Object>> applications = jdbc.queryForList("""
                SELECT
                    a.application_id,
                    a.application_reference_number,
                    a.application_status_id,
                    ast.application_status_name
                FROM application a
                JOIN application_status ast
                    ON ast.application_status_id = a.application_status_id
                WHERE a.applicant_id = ?
                ORDER BY a.application_date DESC, a.application_id DESC
                LIMIT 1
                """, applicantId);

        return applications.isEmpty() ? null : applications.get(0);
    }

    private void updateLatestApplicationStatusIfProvided(Integer applicantId, Map<String, String> form) {
        Integer statusId = intValueOrNull(form, "applicationStatusId");
        if (statusId == null) {
            return;
        }

        Map<String, Object> latestApplication = latestApplicationForApplicant(applicantId);
        if (latestApplication == null) {
            return;
        }

        Integer applicationId = toInteger(latestApplication.get("application_id"));
        if (applicationId == null) {
            return;
        }

        jdbc.update("""
                UPDATE application
                SET application_status_id = ?
                WHERE application_id = ?
                """, statusId, applicationId);
    }

    private Map<String, Object> primaryEmergencyContact(Integer applicantId) {
        List<Map<String, Object>> contacts = jdbc.queryForList("""
                SELECT
                    contact_id,
                    contact_name,
                    relationship,
                    contact_number,
                    contact_address
                FROM applicant_emergency_contact
                WHERE applicant_id = ?
                ORDER BY contact_id
                LIMIT 1
                """, applicantId);

        return contacts.isEmpty() ? null : contacts.get(0);
    }

    private void upsertEmergencyContact(Integer applicantId, Map<String, String> form) {
        String contactName = blankToNull(form.get("emergencyContactName"));
        String relationship = blankToNull(form.get("emergencyRelationship"));
        String contactNumber = blankToNull(form.get("emergencyContactNumber"));
        String contactAddress = blankToNull(form.get("emergencyContactAddress"));

        boolean hasAnyEmergencyValue = contactName != null
                || relationship != null
                || contactNumber != null
                || contactAddress != null;

        List<Integer> existingContactIds = jdbc.queryForList("""
                SELECT contact_id
                FROM applicant_emergency_contact
                WHERE applicant_id = ?
                ORDER BY contact_id
                """, Integer.class, applicantId);

        if (!hasAnyEmergencyValue) {
            if (!existingContactIds.isEmpty()) {
                jdbc.update("""
                        DELETE FROM applicant_emergency_contact
                        WHERE applicant_id = ?
                        """, applicantId);
            }
            return;
        }

        if (contactName == null) {
            throw new IllegalArgumentException("Emergency contact name is required when emergency contact details are provided");
        }

        if (relationship == null) {
            throw new IllegalArgumentException("Emergency contact relationship is required when emergency contact details are provided");
        }

        if (existingContactIds.isEmpty()) {
            jdbc.update("""
                    INSERT INTO applicant_emergency_contact (
                        applicant_id,
                        contact_name,
                        relationship,
                        contact_number,
                        contact_address
                    ) VALUES (?, ?, ?, ?, ?)
                    """,
                    applicantId,
                    contactName,
                    relationship,
                    contactNumber,
                    contactAddress
            );
            return;
        }

        Integer primaryContactId = existingContactIds.get(0);

        jdbc.update("""
                UPDATE applicant_emergency_contact SET
                    contact_name = ?,
                    relationship = ?,
                    contact_number = ?,
                    contact_address = ?
                WHERE contact_id = ?
                """,
                contactName,
                relationship,
                contactNumber,
                contactAddress,
                primaryContactId
        );
    }

    private Map<String, Object> buildDocumentSummary(List<Map<String, Object>> checklist) {
        int totalRequired = checklist.size();
        int submitted = 0;
        int missing = 0;
        int pending = 0;
        int underReview = 0;
        int verified = 0;
        int rejected = 0;
        int forResubmission = 0;

        for (Map<String, Object> row : checklist) {
            Integer submittedFlag = toInteger(row.get("is_submitted"));
            Integer statusId = toInteger(row.get("requirement_status_id"));

            if (submittedFlag != null && submittedFlag == 1) {
                submitted++;
            } else {
                missing++;
            }

            if (statusId == null) {
                continue;
            }

            switch (statusId) {
                case 1 -> pending++;
                case 2 -> underReview++;
                case 3 -> verified++;
                case 4 -> rejected++;
                case 5 -> forResubmission++;
                default -> { }
            }
        }

        String documentStatus;
        String documentStatusHelp;

        if (totalRequired == 0) {
            documentStatus = "No requirements configured";
            documentStatusHelp = "No checklist is configured for this curriculum yet.";
        } else if (rejected > 0) {
            documentStatus = "Needs correction";
            documentStatusHelp = "At least one document was rejected and needs action.";
        } else if (forResubmission > 0) {
            documentStatus = "For resubmission";
            documentStatusHelp = "At least one document needs to be resubmitted.";
        } else if (missing == totalRequired) {
            documentStatus = "Not started";
            documentStatusHelp = "No required document has been submitted yet.";
        } else if (missing > 0) {
            documentStatus = "Partially submitted";
            documentStatusHelp = "Some requirements have been submitted, but the checklist is still incomplete.";
        } else if (pending > 0 || underReview > 0) {
            documentStatus = "Complete - for review";
            documentStatusHelp = "All requirements were submitted and are awaiting final checking.";
        } else if (verified == totalRequired) {
            documentStatus = "Cleared";
            documentStatusHelp = "All required documents have been verified/received.";
        } else {
            documentStatus = "In progress";
            documentStatusHelp = "Checklist is being processed.";
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRequired", totalRequired);
        summary.put("submitted", submitted);
        summary.put("missing", missing);
        summary.put("pending", pending);
        summary.put("underReview", underReview);
        summary.put("verified", verified);
        summary.put("rejected", rejected);
        summary.put("forResubmission", forResubmission);
        summary.put("documentStatus", documentStatus);
        summary.put("documentStatusHelp", documentStatusHelp);
        return summary;
    }

    private String syncAutoAdmissionStatus(Integer applicationId,
                                           Map<String, Object> documentSummary,
                                           String currentStatus) {
        if (applicationId == null) {
            return currentStatus;
        }

        if (isManualFinalAdmissionStatus(currentStatus)) {
            return currentStatus;
        }

        String targetStatus = targetAdmissionStatus(documentSummary);
        if (targetStatus == null || targetStatus.isBlank()) {
            return currentStatus;
        }

        Integer targetStatusId = applicationStatusIdByName(targetStatus);
        if (targetStatusId == null) {
            return currentStatus;
        }

        jdbc.update("""
                UPDATE application
                SET application_status_id = ?
                WHERE application_id = ?
                  AND application_status_id <> ?
                """, targetStatusId, applicationId, targetStatusId);

        return targetStatus;
    }

    private String targetAdmissionStatus(Map<String, Object> documentSummary) {
        int totalRequired = intValue(documentSummary, "totalRequired");
        int submitted = intValue(documentSummary, "submitted");
        int verified = intValue(documentSummary, "verified");

        if (totalRequired <= 0 || submitted <= 0) {
            return "Pending";
        }

        if (verified >= totalRequired) {
            return "Enrolled";
        }

        return "Temporarily Enrolled";
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

    private int intValue(Map<String, Object> map, String key) {
        Integer value = toInteger(map.get(key));
        return value == null ? 0 : value;
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private void addLookups(Model model) {
        model.addAttribute("categories", jdbc.queryForList("""
                SELECT category_id, category_name
                FROM educational_background_category
                WHERE category_is_active = 1
                ORDER BY category_name
                """));

        model.addAttribute("programs", jdbc.queryForList("""
                SELECT program_id, program_name, program_code
                FROM program
                WHERE program_is_active = 1
                ORDER BY program_name
                """));

        model.addAttribute("campuses", jdbc.queryForList("""
                SELECT campus_id, campus_name
                FROM campus
                WHERE campus_is_active = 1
                ORDER BY campus_name
                """));

        model.addAttribute("applicationStatuses", jdbc.queryForList("""
                SELECT application_status_id, application_status_name
                FROM application_status
                WHERE application_status_name IN (
                    'Pending',
                    'Temporarily Enrolled',
                    'Enrolled',
                    'Non-Compliant',
                    'Did Not Continue',
                    'Cancelled'
                )
                ORDER BY CASE application_status_name
                    WHEN 'Pending' THEN 1
                    WHEN 'Temporarily Enrolled' THEN 2
                    WHEN 'Enrolled' THEN 3
                    WHEN 'Non-Compliant' THEN 4
                    WHEN 'Did Not Continue' THEN 5
                    WHEN 'Cancelled' THEN 6
                    ELSE 99
                END
                """));

        String defaultAcademicYear = getSettingValue(
                "academic_year",
                Year.now().getValue() + "-" + (Year.now().getValue() + 1)
        );

        String defaultSemester = getSettingValue(
                "current_semester",
                "First Semester"
        );

        model.addAttribute("defaultAcademicYear", defaultAcademicYear);
        model.addAttribute("defaultSemester", defaultSemester);
    }

    private String getSettingValue(String key, String defaultValue) {
        try {
            String value = jdbc.queryForObject("""
                    SELECT setting_value
                    FROM system_setting
                    WHERE setting_key = ?
                      AND setting_is_active = 1
                    """, String.class, key);

            return value == null || value.isBlank() ? defaultValue : value.trim();

        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int deleteUploadedFiles(List<String> documentPaths) {
        int deleted = 0;

        for (String documentPath : documentPaths) {
            if (documentPath == null || documentPath.isBlank()) {
                continue;
            }

            try {
                Path path = Paths.get(documentPath);
                if (Files.deleteIfExists(path)) {
                    deleted++;
                }
            } catch (IOException | RuntimeException ignored) {
                // Continue deleting other files.
            }
        }

        return deleted;
    }

    private void validateStudentEmailUniqueness(String email, Integer applicantIdToExclude) {
        if (isDummyMultiAccountEmail(email)) {
            return;
        }

        Integer existingCount;

        if (applicantIdToExclude == null) {
            existingCount = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM applicant
                    WHERE LOWER(applicant_email_address) = LOWER(?)
                    """, Integer.class, email);
        } else {
            existingCount = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM applicant
                    WHERE LOWER(applicant_email_address) = LOWER(?)
                      AND applicant_id <> ?
                    """, Integer.class, email, applicantIdToExclude);
        }

        if (existingCount != null && existingCount > 0) {
            throw new IllegalArgumentException("Email address already registered. Only the approved dummy email may be reused for multiple test applicant accounts.");
        }
    }

    private boolean isDummyMultiAccountEmail(String email) {
        return email != null && DUMMY_MULTI_ACCOUNT_EMAIL.equalsIgnoreCase(email.trim());
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private Map<String, Object> one(String sql, Object... params) {
        return jdbc.queryForMap(sql, params);
    }

    private String nextApplicationReference() {
        int year = Year.now().getValue();
        String prefix = "APP-" + year + "-";
        String pattern = "^APP-" + year + "-[0-9]+$";

        Integer next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(split_part(application_reference_number, '-', 3)::int), 0) + 1
                FROM application
                WHERE application_reference_number LIKE ?
                  AND application_reference_number ~ ?
                """,
                Integer.class,
                prefix + "%",
                pattern
        );

        if (next == null) {
            next = 1;
        }

        return prefix + String.format("%04d", next);
    }

    private void sendApplicantPendingEmailSafe(String toEmail,
                                           String firstName,
                                           String lastName,
                                           String referenceNo) {
    try {

        if (resendApiKey == null || resendApiKey.isBlank()
                || resendFromEmail == null || resendFromEmail.isBlank()) {
            return;
        }
        
      String requirementsHtml = buildApplicantRequirementsHtml(referenceNo);
       
        String subject = "PUP Document Tracking Application Received - " + referenceNo;

        String html = """
                <div style="font-family:Arial,sans-serif;color:#222;line-height:1.6;">

                    <h2 style="color:#8B0000;">
                        Application Received
                    </h2>

                    <p>
                        Dear %s %s,
                    </p>

                    <p>
                        Your application has been received and is currently marked as
                        <strong>Pending</strong>.
                    </p>

                    <p>
                        <strong>Application Reference Number:</strong>
                        %s
                    </p>
                    
                 <p>
                 <strong>Required Documents for Submission:</strong>
                </p>

                %s
                   <p>
                <strong>Submission Reminder:</strong><br>
               Please submit the required documents as soon as possible. Processing of your application may only proceed once the required documents have been received and encoded by the Registrar.
         </p>

      <p>
    Once your document/s have been received and encoded, you will receive a separate Document Tracking Number that you may use to monitor the status of your submission.
      </p>

    
                    <p>
                        Thank you.
                    </p>

                    <p style="color:#666;font-size:13px;">
                        PUP Registrar PDTS System
                    </p>

            """.formatted(
        escapeJson(firstName),
        escapeJson(lastName),
        escapeJson(referenceNo),
        requirementsHtml
);
      

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

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    } catch (Exception ignored) {
        // Applicant creation must not fail if email sending fails.
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

    private String buildReportFilterLabel(String region, String category, String program, String reportStatus) {
        List<String> parts = new ArrayList<>();
        if (region != null && !region.isBlank()) {
            parts.add("Region: " + region.trim());
        }
        if (category != null && !category.isBlank()) {
            parts.add("Curriculum ID: " + category.trim());
        }
        if (program != null && !program.isBlank()) {
            parts.add("Program ID: " + program.trim());
        }
        if (reportStatus != null && !reportStatus.isBlank()) {
            parts.add("Status: " + reportStatus.trim());
        }
        return parts.isEmpty() ? "" : String.join(" • ", parts);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String required(Map<String, String> form, String key) {
        String value = form.get(key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }

        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }

        String text = String.valueOf(value).trim();
        return text.isBlank() || text.equalsIgnoreCase("null") ? null : text;
    }

    private Integer intValueOrNull(Map<String, String> form, String key) {
        String value = form.get(key);
        return value == null || value.isBlank() ? null : Integer.parseInt(value);
    }

    private Integer intValue(Map<String, String> form, String key, int defaultValue) {
        String value = form.get(key);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private String firstEnv(String... keys) {
    for (String key : keys) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
    }
    return null;
}
    private String buildApplicantRequirementsHtml(String referenceNo) {
    try {
        List<String> requirements = jdbc.queryForList("""
                SELECT rt.requirement_type_name
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
                JOIN requirement_type rt ON rt.type_id = cr.type_id
                WHERE a.application_reference_number = ?
                  AND COALESCE(rt.type_is_active, 1) = 1
                ORDER BY cr.is_mandatory DESC, rt.requirement_type_name
                """, String.class, referenceNo);

        if (requirements.isEmpty()) {
            return "<p>Please check the system for your required documents.</p>";
        }

        StringBuilder html = new StringBuilder("<ol>");
        for (String requirement : requirements) {
            html.append("<li>").append(escapeHtml(requirement)).append("</li>");
        }
        html.append("</ol>");

        return html.toString();

    } catch (Exception e) {
        return "<p>Please check the system for your required documents.</p>";
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
}
