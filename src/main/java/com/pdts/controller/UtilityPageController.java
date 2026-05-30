package com.pdts.controller;

import com.pdts.service.AuditLogService;
import com.pdts.service.EmailService;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class UtilityPageController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final JdbcTemplate jdbc;

    public UtilityPageController(
            AuditLogService auditLogService,
            EmailService emailService,
            JdbcTemplate jdbc
    ) {
        this.auditLogService = auditLogService;
        this.emailService = emailService;
        this.jdbc = jdbc;
    }

    @GetMapping("/email-notifications")
    public String emailNotifications(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String emailType,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String keyword,
            Model model
    ) {
        ensureEmailReminderSettings();

        EmailLogQuery query = buildEmailLogQuery(dateFrom, dateTo, emailType, recipient, keyword);

        model.addAttribute("recipients", jdbc.queryForList("""
            SELECT
                ap.applicant_id,
                ap.applicant_first_name || ' ' || ap.applicant_last_name AS full_name,
                ap.applicant_email_address AS email,
                '' AS reference_no
            FROM applicant ap
            WHERE ap.applicant_email_address IS NOT NULL
              AND TRIM(ap.applicant_email_address) <> ''
            ORDER BY ap.applicant_created_at DESC, ap.applicant_id DESC
        """));

        List<Map<String, Object>> requirementTypes = loadRequirementTypesWithMissingCounts();
        Map<String, String> reminderSettings = loadEmailReminderSettings();
        String weeklyTarget = clean(reminderSettings.get("email_weekly_reminder_requirement_type_id"));
        String deadlineTarget = clean(reminderSettings.get("email_deadline_alert_requirement_type_id"));
        if (weeklyTarget.isBlank()) {
            weeklyTarget = "all";
        }
        if (deadlineTarget.isBlank()) {
            deadlineTarget = "all";
        }

        model.addAttribute("requirementTypes", requirementTypes);
        model.addAttribute("weeklyReminderTarget", weeklyTarget);
        model.addAttribute("deadlineReminderTarget", deadlineTarget);
        model.addAttribute("allMissingRecipientCount", countStudentsMissingRequirementTarget("all", false));
        model.addAttribute("allDeadlineRecipientCount", countStudentsMissingRequirementTarget("all", true));
        model.addAttribute("weeklyReminderRecipientCount", countStudentsMissingRequirementTarget(weeklyTarget, false));
        model.addAttribute("deadlineReminderRecipientCount", countStudentsMissingRequirementTarget(deadlineTarget, true));

        model.addAttribute("emailLogs", jdbc.queryForList(emailLogSelectSql() + query.where + """
            ORDER BY l.user_activity_log_performed_at DESC
            LIMIT 200
        """, query.params.toArray()));

        Number totalMatches = jdbc.queryForObject(
                emailLogCountSql() + query.where,
                Number.class,
                query.params.toArray()
        );

        model.addAttribute("emailLogTotal", totalMatches == null ? 0 : totalMatches.intValue());
        model.addAttribute("reminderSettings", reminderSettings);
        model.addAttribute("selectedDateFrom", clean(dateFrom));
        model.addAttribute("selectedDateTo", clean(dateTo));
        model.addAttribute("selectedEmailType", clean(emailType));
        model.addAttribute("selectedRecipient", clean(recipient));
        model.addAttribute("selectedKeyword", clean(keyword));

        return "email-notifications";
    }

    @PostMapping("/email-notifications/send")
    public String sendEmailNotification(
            @RequestParam(defaultValue = "manual") String recipientMode,
            @RequestParam(required = false) String recipients,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) Integer requirementTypeId,
            @RequestParam String emailType,
            @RequestParam String subject,
            @RequestParam("body") String body,
            @RequestParam(required = false) String remarks,
            RedirectAttributes ra
    ) {
        try {
            String cleanMode = clean(recipientMode).isBlank() ? "manual" : clean(recipientMode);
            String emailTypeLabel = getEmailTypeLabel(emailType);
            String cleanRemarks = remarks == null || remarks.isBlank()
                    ? "No additional remarks"
                    : remarks.trim();

            List<Map<String, Object>> targetRecipients;
            String blastLabel;

            if ("missing_requirement".equals(cleanMode)) {
                if (requirementTypeId == null) {
                    throw new IllegalArgumentException("Please select the requirement for the email blast.");
                }

                String requirementName = getRequirementTypeName(requirementTypeId);
                targetRecipients = findStudentsMissingRequirement(requirementTypeId);
                blastLabel = "Missing Requirement: " + requirementName;

                if (targetRecipients.isEmpty()) {
                    ra.addFlashAttribute("error", "No recipients found. No student is currently lacking " + requirementName + " based on curriculum requirements.");
                    return "redirect:/email-notifications";
                }
            } else {
                String typedRecipients = !clean(recipients).isBlank() ? recipients : recipient;
                List<String> manualEmails = parseRecipientEmails(typedRecipients);

                if (manualEmails.isEmpty()) {
                    throw new IllegalArgumentException("Please type at least one valid recipient email address.");
                }

                targetRecipients = new ArrayList<>();
                for (String email : manualEmails) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("email", email);
                    row.put("full_name", email);
                    row.put("applicant_id", null);
                    row.put("application_id", null);
                    targetRecipients.add(row);
                }
                blastLabel = targetRecipients.size() == 1 ? "Manual Recipient" : "Manual Multiple Recipients";
            }

            int sentCount = 0;
            int failedCount = 0;
            List<String> failedEmails = new ArrayList<>();

            for (Map<String, Object> target : targetRecipients) {
                String targetEmail = clean(String.valueOf(target.get("email")));
                String targetName = clean(String.valueOf(target.getOrDefault("full_name", targetEmail)));

                try {
                    emailService.sendManualEmail(targetEmail, subject, body, cleanRemarks);
                    sentCount++;

                    auditLogService.log(
                            "SEND_EMAIL",
                            "email_log",
                            toLong(target.get("applicant_id")),
                            "Sent " + emailTypeLabel + " to " + targetEmail,
                            null,
                            "Subject: " + subject +
                                    " | Mode: " + blastLabel +
                                    " | Recipient: " + targetName +
                                    " | Remarks: " + cleanRemarks
                    );
                } catch (Exception sendError) {
                    failedCount++;
                    if (failedEmails.size() < 5) {
                        failedEmails.add(targetEmail + " (" + sendError.getMessage() + ")");
                    }
                }
            }

            if (sentCount > 0) {
                String message = "Email notification sent to " + sentCount + " recipient(s).";
                if (failedCount > 0) {
                    message += " Failed: " + failedCount + ". First failed emails: " + String.join("; ", failedEmails);
                }
                ra.addFlashAttribute("success", message);
            } else {
                String message = "No email was sent.";
                if (failedCount > 0) {
                    message += " Failed: " + failedCount + ". First failed emails: " + String.join("; ", failedEmails);
                }
                ra.addFlashAttribute("error", message);
            }

        } catch (Exception e) {
            e.printStackTrace();

            String message = e.getMessage();
            if (message == null || message.isBlank()) {
                message = e.getClass().getSimpleName();
            }

            ra.addFlashAttribute("error", "Email failed: " + message);
        }

        return "redirect:/email-notifications";
    }

    private List<String> parseRecipientEmails(String rawRecipients) {
        String raw = clean(rawRecipients);
        if (raw.isBlank()) {
            return List.of();
        }

        Set<String> emails = new LinkedHashSet<>();
        String[] parts = raw.split("[,;\\n\\r\\t ]+");

        for (String part : parts) {
            String email = clean(part).toLowerCase();
            if (email.isBlank()) {
                continue;
            }
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new IllegalArgumentException("Invalid email address: " + email);
            }
            emails.add(email);
        }

        return new ArrayList<>(emails);
    }

    private List<Map<String, Object>> loadRequirementTypesWithMissingCounts() {
        return jdbc.queryForList("""
            SELECT
                rt.type_id,
                rt.requirement_type_name,
                COALESCE(m.missing_count, 0) AS missing_count
            FROM requirement_type rt
            LEFT JOIN (
                SELECT
                    rt_inner.type_id,
                    COUNT(DISTINCT ap.applicant_id) AS missing_count
                FROM applicant ap
                JOIN curriculum_requirement cr
                  ON cr.category_id = ap.educational_background_category_id
                 AND COALESCE(cr.is_mandatory, 1) = 1
                JOIN requirement_type rt_inner
                  ON rt_inner.type_id = cr.type_id
                 AND COALESCE(rt_inner.type_is_active, 1) = 1
                LEFT JOIN LATERAL (
                    SELECT a.*
                    FROM application a
                    WHERE a.applicant_id = ap.applicant_id
                    ORDER BY a.application_date DESC, a.application_id DESC
                    LIMIT 1
                ) latest_app ON TRUE
                WHERE COALESCE(ap.applicant_is_deleted, 0) = 0
                  AND latest_app.application_id IS NOT NULL
                  AND ap.applicant_email_address IS NOT NULL
                  AND TRIM(ap.applicant_email_address) <> ''
                  AND NOT EXISTS (
                        SELECT 1
                        FROM requirement r
                        JOIN requirement_status rs
                          ON rs.status_id = r.requirement_status_id
                        WHERE r.application_id = latest_app.application_id
                          AND r.requirement_type_id = rt_inner.type_id
                          AND rs.requirement_status_name IN ('Pending', 'Under Review', 'Verified/Received')
                  )
                GROUP BY rt_inner.type_id
            ) m ON m.type_id = rt.type_id
            WHERE COALESCE(rt.type_is_active, 1) = 1
            ORDER BY rt.requirement_type_name ASC
        """);
    }

    private int countStudentsMissingRequirementTarget(String target, boolean deadlineOnly) {
        String cleanTarget = clean(target);
        boolean allRequirements = cleanTarget.isBlank() || "all".equalsIgnoreCase(cleanTarget);

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(DISTINCT ap.applicant_id)
            FROM applicant ap
            JOIN curriculum_requirement cr
              ON cr.category_id = ap.educational_background_category_id
             AND COALESCE(cr.is_mandatory, 1) = 1
            JOIN requirement_type rt
              ON rt.type_id = cr.type_id
             AND COALESCE(rt.type_is_active, 1) = 1
            LEFT JOIN LATERAL (
                SELECT a.*
                FROM application a
                WHERE a.applicant_id = ap.applicant_id
                ORDER BY a.application_date DESC, a.application_id DESC
                LIMIT 1
            ) latest_app ON TRUE
            WHERE COALESCE(ap.applicant_is_deleted, 0) = 0
              AND latest_app.application_id IS NOT NULL
              AND ap.applicant_email_address IS NOT NULL
              AND TRIM(ap.applicant_email_address) <> ''
              AND NOT EXISTS (
                    SELECT 1
                    FROM requirement r
                    JOIN requirement_status rs
                      ON rs.status_id = r.requirement_status_id
                    WHERE r.application_id = latest_app.application_id
                      AND r.requirement_type_id = rt.type_id
                      AND rs.requirement_status_name IN ('Pending', 'Under Review', 'Verified/Received')
              )
        """);

        if (!allRequirements) {
            try {
                sql.append(" AND rt.type_id = ? ");
                params.add(Integer.parseInt(cleanTarget));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        if (deadlineOnly && allRequirements && tableHasRows("deadline")) {
            sql.append(" AND EXISTS (SELECT 1 FROM deadline d WHERE d.requirement_type_id = rt.type_id) ");
        }

        return queryCount(sql.toString(), params);
    }

    private boolean tableHasRows(String tableName) {
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
            return count != null && count > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<Map<String, Object>> findStudentsMissingRequirement(Integer requirementTypeId) {
        return jdbc.queryForList("""
            SELECT DISTINCT
                ap.applicant_id,
                latest_app.application_id,
                ap.applicant_first_name || ' ' || ap.applicant_last_name AS full_name,
                ap.applicant_email_address AS email,
                rt.requirement_type_name
            FROM applicant ap
            JOIN curriculum_requirement cr
              ON cr.category_id = ap.educational_background_category_id
             AND COALESCE(cr.is_mandatory, 1) = 1
            JOIN requirement_type rt
              ON rt.type_id = cr.type_id
            LEFT JOIN LATERAL (
                SELECT a.*
                FROM application a
                WHERE a.applicant_id = ap.applicant_id
                ORDER BY a.application_date DESC, a.application_id DESC
                LIMIT 1
            ) latest_app ON TRUE
            WHERE COALESCE(ap.applicant_is_deleted, 0) = 0
              AND latest_app.application_id IS NOT NULL
              AND rt.type_id = ?
              AND ap.applicant_email_address IS NOT NULL
              AND TRIM(ap.applicant_email_address) <> ''
              AND NOT EXISTS (
                    SELECT 1
                    FROM requirement r
                    JOIN requirement_status rs
                      ON rs.status_id = r.requirement_status_id
                    WHERE r.application_id = latest_app.application_id
                      AND r.requirement_type_id = rt.type_id
                      AND rs.requirement_status_name IN ('Pending', 'Under Review', 'Verified/Received')
              )
            ORDER BY full_name ASC, email ASC
        """, requirementTypeId);
    }

    private String getRequirementTypeName(Integer requirementTypeId) {
        List<String> names = jdbc.queryForList("""
            SELECT requirement_type_name
            FROM requirement_type
            WHERE type_id = ?
            LIMIT 1
        """, String.class, requirementTypeId);

        if (names.isEmpty()) {
            throw new IllegalArgumentException("Selected requirement was not found.");
        }
        return names.get(0);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return Long.parseLong(text);
    }


    @GetMapping(value = "/email-notifications/export", produces = "text/csv")
    public ResponseEntity<String> exportEmailNotifications(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String emailType,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String keyword
    ) {
        EmailLogQuery query = buildEmailLogQuery(dateFrom, dateTo, emailType, recipient, keyword);

        List<Map<String, Object>> rows = jdbc.queryForList(emailLogSelectSql() + query.where + """
            ORDER BY l.user_activity_log_performed_at DESC
        """, query.params.toArray());

        StringBuilder csv = new StringBuilder();
        csv.append(csvLine("PDTS Email Notification Log"));
        csv.append(csvLine("Date From", clean(dateFrom).isBlank() ? "All" : clean(dateFrom)));
        csv.append(csvLine("Date To", clean(dateTo).isBlank() ? "All" : clean(dateTo)));
        csv.append(csvLine("Email Type", clean(emailType).isBlank() ? "All" : clean(emailType)));
        csv.append(csvLine("Recipient Filter", clean(recipient).isBlank() ? "All" : clean(recipient)));
        csv.append(csvLine("Keyword", clean(keyword).isBlank() ? "None" : clean(keyword)));
        csv.append('\n');

        csv.append(csvLine(
                "Timestamp",
                "Performed By",
                "Email Category",
                "Entity",
                "Record ID",
                "Description / Recipient",
                "Subject / Remarks",
                "IP Address"
        ));

        for (Map<String, Object> row : rows) {
            csv.append(csvLine(
                    row.get("user_activity_log_performed_at"),
                    row.get("user_username"),
                    emailCategory(row.get("user_activity_log_entity_type"), row.get("user_activity_log_description"), row.get("user_activity_log_new_value")),
                    row.get("user_activity_log_entity_type"),
                    row.get("display_record_id"),
                    row.get("user_activity_log_description"),
                    row.get("user_activity_log_new_value"),
                    row.get("user_activity_log_ip_address")
            ));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=PDTS_Email_Notifications.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    @PostMapping("/email-notifications/reminders")
    public String updateEmailReminderSettings(
            @RequestParam Map<String, String> form,
            RedirectAttributes ra
    ) {
        try {
            ensureEmailReminderSettings();

            upsertSetting("email_weekly_reminder_enabled", checkboxValue(form, "weeklyEnabled"), "Weekly Pending Reminder Enabled", "boolean", "true,false");
            upsertSetting("email_weekly_reminder_day", requiredFromForm(form, "weeklyDay"), "Weekly Pending Reminder Day", "select", "Monday,Tuesday,Wednesday,Thursday,Friday,Saturday,Sunday");
            upsertSetting("email_weekly_reminder_time", requiredFromForm(form, "weeklyTime"), "Weekly Pending Reminder Time", "time", null);
            upsertSetting("email_weekly_reminder_requirement_type_id", requirementTargetFromForm(form, "weeklyRequirementTarget"), "Weekly Pending Reminder Target Requirement", "select", null);
            upsertSetting("email_weekly_reminder_subject", requiredFromForm(form, "weeklySubject"), "Weekly Pending Reminder Subject", "text", null);
            upsertSetting("email_weekly_reminder_body", requiredFromForm(form, "weeklyBody"), "Weekly Pending Reminder Body", "textarea", null);

            upsertSetting("email_deadline_alert_enabled", checkboxValue(form, "deadlineEnabled"), "Deadline Alert Enabled", "boolean", "true,false");
            upsertSetting("email_deadline_alert_days", requiredNumber(form, "deadlineDays", 0, 365), "Deadline Alert Days Before", "number", null);
            upsertSetting("email_deadline_alert_requirement_type_id", requirementTargetFromForm(form, "deadlineRequirementTarget"), "Deadline Alert Target Requirement", "select", null);
            upsertSetting("email_deadline_alert_subject", requiredFromForm(form, "deadlineSubject"), "Deadline Alert Subject", "text", null);
            upsertSetting("email_deadline_alert_body", requiredFromForm(form, "deadlineBody"), "Deadline Alert Body", "textarea", null);

            upsertSetting("email_ack_receipt_enabled", checkboxValue(form, "ackEnabled"), "Acknowledgment Receipt Enabled", "boolean", "true,false");
            upsertSetting("email_ack_receipt_subject", requiredFromForm(form, "ackSubject"), "Acknowledgment Receipt Subject", "text", null);
            upsertSetting("email_ack_receipt_body", requiredFromForm(form, "ackBody"), "Acknowledgment Receipt Body", "textarea", null);

            upsertSetting("email_status_update_enabled", checkboxValue(form, "statusEnabled"), "Status Update Email Enabled", "boolean", "true,false");
            upsertSetting("email_status_update_subject", requiredFromForm(form, "statusSubject"), "Status Update Email Subject", "text", null);
            upsertSetting("email_status_update_body", requiredFromForm(form, "statusBody"), "Status Update Email Body", "textarea", null);

            auditLogService.log(
                    "UPDATE_EMAIL_SETTINGS",
                    "system_setting",
                    null,
                    "Updated email notification triggers and templates",
                    null,
                    "Email reminder settings updated"
            );

            ra.addFlashAttribute("success", "Email reminders and notification triggers updated.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to update email reminders: " + e.getMessage());
        }

        return "redirect:/email-notifications";
    }

    @GetMapping("/tracking-lookup")
    public String trackingLookup(
            @RequestParam(required = false) String tracking,
            Model model
    ) {
        String cleanTracking = clean(tracking);
        model.addAttribute("searchedTracking", cleanTracking);

        if (cleanTracking.isBlank()) {
            return "tracking-lookup";
        }

        List<Map<String, Object>> refRows = jdbc.queryForList("""
            SELECT application_reference_number
            FROM vw_student_status
            WHERE application_reference_number = ?
               OR requirement_tracking_no = ?
            LIMIT 1
        """, cleanTracking, cleanTracking);

        if (refRows.isEmpty()) {
            model.addAttribute("trackingError", "No matching application or document tracking number was found.");
            return "tracking-lookup";
        }

        String applicationReferenceNumber = String.valueOf(refRows.get(0).get("application_reference_number"));

        List<Map<String, Object>> documents = jdbc.queryForList("""
            SELECT
                application_reference_number,
                applicant_full_name,
                program_name,
                campus_name,
                application_semester,
                application_academic_year,
                application_status_name,
                requirement_tracking_no,
                requirement_type_name,
                requirement_status_name,
                requirement_status_color,
                rejection_reason_description,
                requirement_upload_date,
                requirement_processed_at,
                resubmission_notes
            FROM vw_student_status
            WHERE application_reference_number = ?
            ORDER BY requirement_type_name ASC
        """, applicationReferenceNumber);

        if (documents.isEmpty()) {
            model.addAttribute("trackingError", "The application was found, but there are no document records yet.");
            return "tracking-lookup";
        }

        model.addAttribute("trackingApplication", documents.get(0));
        model.addAttribute("trackingDocuments", documents);

        return "tracking-lookup";
    }

    @GetMapping("/reports")
    public String reports(
        @RequestParam(required = false) String region,
        @RequestParam(required = false) String curriculum,
        @RequestParam(required = false) String program,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo,
        Model model
)
    
    {
        String cleanRegion = clean(region);
String cleanCurriculum = clean(curriculum);
String cleanProgram = clean(program);
String cleanStatus = clean(status);
String cleanDateFrom = clean(dateFrom);
String cleanDateTo = clean(dateTo);

ReportQuery query = buildReportQuery(cleanRegion, cleanCurriculum, cleanProgram, cleanStatus, cleanDateFrom, cleanDateTo);

        String baseFrom = baseReportFrom();

        int filteredStudents = queryCount(
                "SELECT COUNT(DISTINCT ap.applicant_id) " + baseFrom + query.where,
                query.params
        );

        int clearedStudents = queryCount(
                "SELECT COUNT(DISTINCT ap.applicant_id) " + baseFrom + query.where + clearedCondition(),
                query.params
        );

        int pendingRequirements = queryCount(
                "SELECT COUNT(*) " +
                        baseFrom +
                        " JOIN requirement r ON r.application_id = latest_app.application_id " +
                        " JOIN requirement_status rs ON rs.status_id = r.requirement_status_id " +
                        query.where +
                        " AND rs.requirement_status_name <> 'Verified/Received' ",
                query.params
        );

        List<Map<String, Object>> curriculumBreakdown = toProgressRows(
                jdbc.queryForList(
                        """
                        SELECT
                            ebc.category_name AS label,
                            ebc.category_id AS filter_value,
                            COUNT(DISTINCT ap.applicant_id) AS count
                        """ + baseFrom + query.where + """
                        GROUP BY ebc.category_name, ebc.category_id
                        ORDER BY count DESC, ebc.category_name ASC
                        """,
                        query.params.toArray()
                ),
                false
        );

        List<Map<String, Object>> regionBreakdown = toProgressRows(
                jdbc.queryForList(
                        """
                        SELECT
                            COALESCE(NULLIF(TRIM(ap.applicant_region), ''), 'Unspecified') AS label,
                            COALESCE(NULLIF(TRIM(ap.applicant_region), ''), 'Unspecified') AS filter_value,
                            COUNT(DISTINCT ap.applicant_id) AS count
                        """ + baseFrom + query.where + """
                        GROUP BY COALESCE(NULLIF(TRIM(ap.applicant_region), ''), 'Unspecified')
                        ORDER BY count DESC, label ASC
                        """,
                        query.params.toArray()
                ),
                true
        );

        model.addAttribute("regions", jdbc.queryForList("""
            SELECT DISTINCT applicant_region
            FROM applicant
            WHERE applicant_region IS NOT NULL
              AND TRIM(applicant_region) <> ''
              AND COALESCE(applicant_is_deleted, 0) = 0
            ORDER BY applicant_region
        """, String.class));

        model.addAttribute("curricula", jdbc.queryForList("""
            SELECT category_id, category_name
            FROM educational_background_category
            ORDER BY category_name
        """));

        model.addAttribute("programs", jdbc.queryForList("""
            SELECT program_id, program_code, program_name
            FROM program
            ORDER BY program_code, program_name
        """));

        model.addAttribute("selectedRegion", cleanRegion);
        model.addAttribute("selectedCurriculum", cleanCurriculum);
        model.addAttribute("selectedProgram", cleanProgram);
        model.addAttribute("selectedStatus", cleanStatus);
        model.addAttribute("selectedDateFrom", cleanDateFrom);
        model.addAttribute("selectedDateTo", cleanDateTo);

        model.addAttribute("filteredStudents", filteredStudents);
        model.addAttribute("clearedStudents", clearedStudents);
        model.addAttribute("pendingRequirements", pendingRequirements);
        model.addAttribute("curriculumBreakdown", curriculumBreakdown);
        model.addAttribute("regionBreakdown", regionBreakdown);

        return "reports";
    }

  @GetMapping(value = "/reports/export", produces = "text/csv")
public ResponseEntity<String> exportReports(
        @RequestParam(required = false) String region,
        @RequestParam(required = false) String curriculum,
        @RequestParam(required = false) String program,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo
) {
    
       String cleanRegion = clean(region);
String cleanCurriculum = clean(curriculum);
String cleanProgram = clean(program);
String cleanStatus = clean(status);
String cleanDateFrom = clean(dateFrom);
String cleanDateTo = clean(dateTo);

ReportExportData data = loadReportExportData(
        cleanRegion,
        cleanCurriculum,
        cleanProgram,
        cleanStatus,
        cleanDateFrom,
        cleanDateTo
);

        String regionLabel = cleanRegion.isBlank() ? "All Regions" : cleanRegion;
        String curriculumLabel = cleanCurriculum.isBlank() ? "All Curricula" : cleanCurriculum;
        String programLabel = cleanProgram.isBlank() ? "All Programs" : cleanProgram;
        String statusLabel = cleanStatus.isBlank() ? "All Status" : cleanStatus;

     String dateFromLabel = cleanDateFrom.isBlank() ? "All Dates" : cleanDateFrom;
String dateToLabel = cleanDateTo.isBlank() ? "All Dates" : cleanDateTo;

StringBuilder csv = new StringBuilder("\uFEFF");

/*
 * Clean CSV layout:
 * CSV is for raw spreadsheet data, not formatted reports.
 * The PDF remains the formatted official report.
 */
csv.append(csvLine(
        "Application #",
        "Student Name",
        "Email",
        "Program Code",
        "Program Name",
        "Curriculum",
        "Region",
        "Enrollment Status",
        "Uploaded Documents",
        "Verified Documents",
        "Pending Documents",
        "Latest Document Upload",
        "Region Filter",
        "Curriculum Filter",
        "Program Filter",
        "Status Filter",
        "Date From",
        "Date To"
));

for (Map<String, Object> row : data.studentRows) {
    csv.append(csvLine(
            row.get("application_reference_number"),
            row.get("applicant_name"),
            row.get("email"),
            row.get("program_code"),
            row.get("program_name"),
            row.get("curriculum"),
            row.get("region"),
            row.get("enrollment_status"),
            row.get("uploaded_documents"),
            row.get("verified_documents"),
            row.get("pending_documents"),
            row.get("latest_document_upload"),
            regionLabel,
            curriculumLabel,
            programLabel,
            statusLabel,
            dateFromLabel,
            dateToLabel
    ));
}

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=PDTS_Reports_Analytics.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString());
    }

   @GetMapping(value = "/reports/pdf", produces = "application/pdf")
public ResponseEntity<byte[]> exportReportsPdf(
        @RequestParam(required = false) String region,
        @RequestParam(required = false) String curriculum,
        @RequestParam(required = false) String program,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String dateFrom,
        @RequestParam(required = false) String dateTo
) {
    
       String cleanRegion = clean(region);
String cleanCurriculum = clean(curriculum);
String cleanProgram = clean(program);
String cleanStatus = clean(status);
String cleanDateFrom = clean(dateFrom);
String cleanDateTo = clean(dateTo);

byte[] pdf;
try {
    ReportExportData data = loadReportExportData(
            cleanRegion,
            cleanCurriculum,
            cleanProgram,
            cleanStatus,
            cleanDateFrom,
            cleanDateTo
    );
    pdf = buildReportPdf(cleanRegion, cleanCurriculum, cleanProgram, cleanStatus, data);
            
        } catch (Exception e) {
            e.printStackTrace();
            pdf = buildFallbackReportPdf(e);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=PDTS_Reports_Analytics.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private String emailLogSelectSql() {
        return """
            SELECT
                l.user_activity_log_id,
                COALESCE(l.archived_record_id, l.user_activity_log_id) AS display_record_id,
                u.user_username,
                l.user_activity_log_action_type,
                l.user_activity_log_entity_type,
                l.user_activity_log_description,
                l.user_activity_log_old_value,
                l.user_activity_log_new_value,
                l.user_activity_log_ip_address,
                l.user_activity_log_performed_at
            FROM user_activity_log l
            JOIN app_user u
              ON u.user_id = l.user_activity_log_user_id
        """;
    }

    private String emailLogCountSql() {
        return """
            SELECT COUNT(*)
            FROM user_activity_log l
            JOIN app_user u
              ON u.user_id = l.user_activity_log_user_id
        """;
    }

    private EmailLogQuery buildEmailLogQuery(
            String dateFrom,
            String dateTo,
            String emailType,
            String recipient,
            String keyword
    ) {
        StringBuilder where = new StringBuilder(" WHERE l.user_activity_log_action_type = 'SEND_EMAIL' ");
        List<Object> params = new ArrayList<>();

        Timestamp startDate = parseStartOfDay(dateFrom);
        if (startDate != null) {
            where.append(" AND l.user_activity_log_performed_at >= ? ");
            params.add(startDate);
        }

        Timestamp endDate = parseEndExclusive(dateTo);
        if (endDate != null) {
            where.append(" AND l.user_activity_log_performed_at < ? ");
            params.add(endDate);
        }

        String cleanType = clean(emailType).toLowerCase();
        if (!cleanType.isBlank()) {
            appendEmailTypeFilter(where, params, cleanType);
        }

        String cleanRecipient = clean(recipient).toLowerCase();
        if (!cleanRecipient.isBlank()) {
            String like = "%" + cleanRecipient + "%";
            where.append("""
                AND (
                    LOWER(COALESCE(l.user_activity_log_description, '')) LIKE ?
                    OR LOWER(COALESCE(l.user_activity_log_new_value, '')) LIKE ?
                )
            """);
            params.add(like);
            params.add(like);
        }

        String cleanKeyword = clean(keyword).toLowerCase();
        if (!cleanKeyword.isBlank()) {
            String like = "%" + cleanKeyword + "%";
            where.append("""
                AND (
                    LOWER(COALESCE(l.user_activity_log_description, '')) LIKE ?
                    OR LOWER(COALESCE(l.user_activity_log_new_value, '')) LIKE ?
                    OR LOWER(COALESCE(l.user_activity_log_entity_type, '')) LIKE ?
                    OR LOWER(COALESCE(u.user_username, '')) LIKE ?
                )
            """);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }

        return new EmailLogQuery(where.toString(), params);
    }

    private void appendEmailTypeFilter(StringBuilder where, List<Object> params, String emailType) {
        if ("manual".equals(emailType)) {
            where.append(" AND l.user_activity_log_entity_type = 'email_log' ");
            return;
        }

        if ("automatic_status".equals(emailType)) {
            where.append(" AND l.user_activity_log_entity_type = 'requirement' AND LOWER(COALESCE(l.user_activity_log_description, '')) LIKE ? ");
            params.add("%automatic status%");
            return;
        }

        String like;
        if ("pending".equals(emailType)) {
            like = "%pending%";
        } else if ("received".equals(emailType)) {
            like = "%receipt%";
        } else if ("rejected".equals(emailType)) {
            like = "%reject%";
        } else if ("deadline".equals(emailType)) {
            like = "%deadline%";
        } else if ("reminder".equals(emailType)) {
            like = "%reminder%";
        } else {
            like = "%" + emailType + "%";
        }

        where.append("""
            AND (
                LOWER(COALESCE(l.user_activity_log_description, '')) LIKE ?
                OR LOWER(COALESCE(l.user_activity_log_new_value, '')) LIKE ?
            )
        """);
        params.add(like);
        params.add(like);
    }

    private String emailCategory(Object entity, Object description, Object value) {
        String entityText = entity == null ? "" : entity.toString();
        String combined = ((description == null ? "" : description.toString()) + " " + (value == null ? "" : value.toString())).toLowerCase();

        if ("email_log".equalsIgnoreCase(entityText)) {
            return "Manual Email";
        }
        if (combined.contains("automatic status")) {
            return "Automatic Status Update";
        }
        if (combined.contains("deadline")) {
            return "Deadline Alert";
        }
        if (combined.contains("reminder")) {
            return "Pending Reminder";
        }
        if (combined.contains("receipt") || combined.contains("received")) {
            return "Acknowledgment Receipt";
        }
        return "Email Notification";
    }

    private void ensureEmailReminderSettings() {
        insertSettingIfMissing("email_weekly_reminder_enabled", "true", "Weekly Pending Reminder Enabled", "boolean", "true,false");
        insertSettingIfMissing("email_weekly_reminder_day", getSettingValue("email_reminder_day", "Monday"), "Weekly Pending Reminder Day", "select", "Monday,Tuesday,Wednesday,Thursday,Friday,Saturday,Sunday");
        insertSettingIfMissing("email_weekly_reminder_time", "08:00", "Weekly Pending Reminder Time", "time", null);
        insertSettingIfMissing("email_weekly_reminder_requirement_type_id", "all", "Weekly Pending Reminder Target Requirement", "select", null);
        insertSettingIfMissing("email_weekly_reminder_subject", "Reminder: Pending Requirements – Please Submit Required Documents", "Weekly Pending Reminder Subject", "text", null);
        insertSettingIfMissing("email_weekly_reminder_body", "Dear Student,\n\nThis is a reminder that you still have pending required documents. Please submit them as soon as possible.\n\nThank you.", "Weekly Pending Reminder Body", "textarea", null);

        insertSettingIfMissing("email_deadline_alert_enabled", "true", "Deadline Alert Enabled", "boolean", "true,false");
        insertSettingIfMissing("email_deadline_alert_days", "7", "Deadline Alert Days Before", "number", null);
        insertSettingIfMissing("email_deadline_alert_requirement_type_id", "all", "Deadline Alert Target Requirement", "select", null);
        insertSettingIfMissing("email_deadline_alert_subject", "Deadline Alert – Required Documents Due Soon", "Deadline Alert Subject", "text", null);
        insertSettingIfMissing("email_deadline_alert_body", "Dear Student,\n\nThis is a reminder that the deadline for submitting your required document is approaching. Please complete your submission before the due date.\n\nThank you.", "Deadline Alert Body", "textarea", null);

        insertSettingIfMissing("email_ack_receipt_enabled", "true", "Acknowledgment Receipt Enabled", "boolean", "true,false");
        insertSettingIfMissing("email_ack_receipt_subject", "Acknowledgment: Document Received", "Acknowledgment Receipt Subject", "text", null);
        insertSettingIfMissing("email_ack_receipt_body", "Dear Student,\n\nThis confirms that your submitted document has been received by the Registrar's Office and is now queued for review.\n\nThank you.", "Acknowledgment Receipt Body", "textarea", null);

        insertSettingIfMissing("email_status_update_enabled", "true", "Status Update Email Enabled", "boolean", "true,false");
        insertSettingIfMissing("email_status_update_subject", "PUP Document Tracking Status Update", "Status Update Email Subject", "text", null);
        insertSettingIfMissing("email_status_update_body", "Dear Student,\n\nYour submitted document status has been updated. Please use the document tracker to check the latest status.\n\nThank you.", "Status Update Email Body", "textarea", null);
    }

    private Map<String, String> loadEmailReminderSettings() {
        Map<String, String> settings = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT setting_key, setting_value
            FROM system_setting
            WHERE setting_key LIKE 'email_%'
            ORDER BY setting_key
        """);

        for (Map<String, Object> row : rows) {
            settings.put(String.valueOf(row.get("setting_key")), String.valueOf(row.get("setting_value")));
        }

        return settings;
    }

    private void insertSettingIfMissing(String key, String value, String label, String type, String options) {
        jdbc.update("""
            INSERT INTO system_setting (
                setting_key,
                setting_value,
                setting_label,
                setting_type,
                setting_options,
                setting_is_active,
                setting_updated_at
            )
            VALUES (?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP)
            ON CONFLICT (setting_key) DO NOTHING
        """, key, value, label, type, options);
    }

    private void upsertSetting(String key, String value, String label, String type, String options) {
        jdbc.update("""
            INSERT INTO system_setting (
                setting_key,
                setting_value,
                setting_label,
                setting_type,
                setting_options,
                setting_is_active,
                setting_updated_at
            )
            VALUES (?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP)
            ON CONFLICT (setting_key) DO UPDATE SET
                setting_value = EXCLUDED.setting_value,
                setting_label = EXCLUDED.setting_label,
                setting_type = EXCLUDED.setting_type,
                setting_options = EXCLUDED.setting_options,
                setting_is_active = 1,
                setting_updated_at = CURRENT_TIMESTAMP
        """, key, value, label, type, options);
    }

    private String getSettingValue(String key, String fallback) {
        try {
            List<String> values = jdbc.queryForList("""
                SELECT setting_value
                FROM system_setting
                WHERE setting_key = ?
                LIMIT 1
            """, String.class, key);

            if (!values.isEmpty() && values.get(0) != null && !values.get(0).isBlank()) {
                return values.get(0);
            }
        } catch (Exception ignored) {
            // Use fallback.
        }
        return fallback;
    }

    private String checkboxValue(Map<String, String> form, String key) {
        return form.containsKey(key) ? "true" : "false";
    }

    private String requirementTargetFromForm(Map<String, String> form, String key) {
        String value = clean(form.get(key));
        if (value.isBlank() || "all".equalsIgnoreCase(value)) {
            return "all";
        }

        try {
            Integer typeId = Integer.parseInt(value);
            getRequirementTypeName(typeId);
            return String.valueOf(typeId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a valid requirement selection.");
        }
    }

    private String requiredFromForm(Map<String, String> form, String key) {
        String value = form.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required.");
        }
        return value.trim();
    }

    private String requiredNumber(Map<String, String> form, String key, int min, int max) {
        String raw = requiredFromForm(form, key);
        int number = Integer.parseInt(raw);
        if (number < min || number > max) {
            throw new IllegalArgumentException(key + " must be from " + min + " to " + max + ".");
        }
        return String.valueOf(number);
    }

    private Timestamp parseStartOfDay(String value) {
        String cleanValue = clean(value);
        if (cleanValue.isBlank()) {
            return null;
        }
        return Timestamp.valueOf(LocalDate.parse(cleanValue).atStartOfDay());
    }

    private Timestamp parseEndExclusive(String value) {
        String cleanValue = clean(value);
        if (cleanValue.isBlank()) {
            return null;
        }
        return Timestamp.valueOf(LocalDate.parse(cleanValue).plusDays(1).atStartOfDay());
    }

    private ReportQuery buildReportQuery(String region, String curriculum, String program, String status, String dateFrom, String dateTo) {
        StringBuilder where = new StringBuilder(" WHERE COALESCE(ap.applicant_is_deleted, 0) = 0 ");
        List<Object> params = new ArrayList<>();

        if (!region.isBlank()) {
            where.append(" AND ap.applicant_region = ? ");
            params.add(region);
        }

        if (!curriculum.isBlank()) {
            where.append(" AND ap.educational_background_category_id = ? ");
            params.add(curriculum);
        }

        if (!program.isBlank()) {
            where.append(" AND latest_app.program_id = ? ");
            params.add(Integer.parseInt(program));
        }

        if ("continuing".equals(status) || "on_leave".equals(status)) {
            where.append(" AND ap.applicant_enrollment_status = ? ");
            params.add(status);
        }

        if ("cleared".equals(status)) {
            where.append(clearedCondition());
        }

        if ("pending".equals(status)) {
            where.append("""
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

         Timestamp startDate = parseStartOfDay(dateFrom);
if (startDate != null) {
    where.append(" AND latest_app.application_date >= ? ");
    params.add(startDate);
}

Timestamp endDate = parseEndExclusive(dateTo);
if (endDate != null) {
    where.append(" AND latest_app.application_date < ? ");
    params.add(endDate);
}

        return new ReportQuery(where.toString(), params);
    }


   private ReportExportData loadReportExportData(
        String cleanRegion,
        String cleanCurriculum,
        String cleanProgram,
        String cleanStatus,
        String cleanDateFrom,
        String cleanDateTo
){
        ReportQuery query = buildReportQuery(cleanRegion, cleanCurriculum, cleanProgram, cleanStatus, cleanDateFrom, cleanDateTo);
        String baseFrom = baseReportFrom();

        int filteredStudents = queryCount(
                "SELECT COUNT(DISTINCT ap.applicant_id) " + baseFrom + query.where,
                query.params
        );

        int clearedStudents = queryCount(
                "SELECT COUNT(DISTINCT ap.applicant_id) " + baseFrom + query.where + clearedCondition(),
                query.params
        );

        int pendingRequirements = queryCount(
                "SELECT COUNT(*) " +
                        baseFrom +
                        " JOIN requirement r ON r.application_id = latest_app.application_id " +
                        " JOIN requirement_status rs ON rs.status_id = r.requirement_status_id " +
                        query.where +
                        " AND rs.requirement_status_name <> 'Verified/Received' ",
                query.params
        );

        List<Map<String, Object>> curriculumBreakdown = jdbc.queryForList(
                """
                SELECT
                    ebc.category_name AS label,
                    ebc.category_id AS filter_value,
                    COUNT(DISTINCT ap.applicant_id) AS count
                """ + baseFrom + query.where + """
                GROUP BY ebc.category_name, ebc.category_id
                ORDER BY count DESC, ebc.category_name ASC
                """,
                query.params.toArray()
        );

        List<Map<String, Object>> regionBreakdown = jdbc.queryForList(
                """
                SELECT
                    COALESCE(NULLIF(TRIM(ap.applicant_region), ''), 'Unspecified') AS label,
                    COALESCE(NULLIF(TRIM(ap.applicant_region), ''), 'Unspecified') AS filter_value,
                    COUNT(DISTINCT ap.applicant_id) AS count
                """ + baseFrom + query.where + """
                GROUP BY COALESCE(NULLIF(TRIM(ap.applicant_region), ''), 'Unspecified')
                ORDER BY count DESC, label ASC
                """,
                query.params.toArray()
        );

        List<Map<String, Object>> studentRows = jdbc.queryForList(
                """
                SELECT
                    COALESCE(latest_app.application_reference_number, '') AS application_reference_number,
                    ap.applicant_last_name || ', ' || ap.applicant_first_name AS applicant_name,
                    COALESCE(ap.applicant_email_address, '') AS email,
                    COALESCE(p.program_code, '') AS program_code,
                    COALESCE(p.program_name, '') AS program_name,
                    ebc.category_name AS curriculum,
                    COALESCE(NULLIF(TRIM(ap.applicant_region), ''), 'Unspecified') AS region,
                    COALESCE(ap.applicant_enrollment_status, '') AS enrollment_status,
                    COUNT(r.requirement_id) AS uploaded_documents,
                    SUM(CASE WHEN rs.requirement_status_name = 'Verified/Received' THEN 1 ELSE 0 END) AS verified_documents,
                    SUM(CASE WHEN rs.requirement_status_name <> 'Verified/Received' THEN 1 ELSE 0 END) AS pending_documents,
                    MAX(r.requirement_upload_date) AS latest_document_upload
                """ + baseFrom + """
                LEFT JOIN requirement r
                  ON r.application_id = latest_app.application_id
                LEFT JOIN requirement_status rs
                  ON rs.status_id = r.requirement_status_id
                """ + query.where + """
                GROUP BY
                    latest_app.application_reference_number,
                    ap.applicant_last_name,
                    ap.applicant_first_name,
                    ap.applicant_email_address,
                    p.program_code,
                    p.program_name,
                    ebc.category_name,
                    ap.applicant_region,
                    ap.applicant_enrollment_status
                ORDER BY ap.applicant_last_name ASC, ap.applicant_first_name ASC
                """,
                query.params.toArray()
        );

        return new ReportExportData(
                filteredStudents,
                clearedStudents,
                pendingRequirements,
                curriculumBreakdown,
                regionBreakdown,
                studentRows
        );
    }

    private byte[] buildReportPdf(
            String cleanRegion,
            String cleanCurriculum,
            String cleanProgram,
            String cleanStatus,
            ReportExportData data
    ) {
        ReportPdfBuilder pdf = new ReportPdfBuilder();

        pdf.title("PDTS Reports & Analytics");
        pdf.subtitle("Official export generated from Reports & Filters");
        pdf.space(12);

        pdf.table(
                new String[]{"Generated From", "Region Filter", "Curriculum Filter", "Program Filter", "Status Filter"},
                List.of(List.of(
                        "Reports & Filters",
                        cleanRegion.isBlank() ? "All Regions" : cleanRegion,
                        cleanCurriculum.isBlank() ? "All Curricula" : cleanCurriculum,
                        cleanProgram.isBlank() ? "All Programs" : cleanProgram,
                        cleanStatus.isBlank() ? "All Status" : cleanStatus
                )),
                new int[]{135, 125, 150, 150, 120}
        );

        pdf.space(12);
        pdf.section("Summary");
        pdf.table(
                new String[]{"Filtered Students", "Cleared Students", "Pending Requirements"},
                List.of(List.of(
                        String.valueOf(data.filteredStudents),
                        String.valueOf(data.clearedStudents),
                        String.valueOf(data.pendingRequirements)
                )),
                new int[]{210, 210, 230}
        );

        pdf.space(12);
        pdf.section("Breakdown by Curriculum Type");
        pdf.table(
                new String[]{"Curriculum", "Students"},
                toPdfRows(data.curriculumBreakdown, "label", "count", "No curriculum data found.", "0"),
                new int[]{530, 100}
        );

        pdf.space(12);
        pdf.section("Breakdown by Last School Region");
        pdf.table(
                new String[]{"Region", "Students"},
                toPdfRows(data.regionBreakdown, "label", "count", "No region data found.", "0"),
                new int[]{530, 100}
        );

        pdf.space(12);
        pdf.section("Student Details");
        List<List<String>> studentRows = new ArrayList<>();
        if (data.studentRows.isEmpty()) {
            studentRows.add(List.of("No matching students found.", "", "", "", "", "", "", ""));
        } else {
            for (Map<String, Object> row : data.studentRows) {
                studentRows.add(List.of(
                        truncate(safeText(row.get("application_reference_number")), 16),
                        truncate(safeText(row.get("applicant_name")), 24),
                        truncate(safeText(row.get("program_code")), 10),
                        truncate(safeText(row.get("curriculum")), 22),
                        truncate(safeText(row.get("region")), 18),
                        safeText(row.get("uploaded_documents")),
                        safeText(row.get("verified_documents")),
                        safeText(row.get("pending_documents"))
                ));
            }
        }
        pdf.table(
                new String[]{"Application #", "Student", "Program", "Curriculum", "Region", "Uploaded", "Verified", "Pending"},
                studentRows,
                new int[]{95, 135, 70, 125, 105, 58, 58, 58}
        );

        return pdf.toPdf();
    }

    private List<List<String>> toPdfRows(
            List<Map<String, Object>> source,
            String labelKey,
            String countKey,
            String emptyLabel,
            String emptyCount
    ) {
        List<List<String>> rows = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            rows.add(List.of(emptyLabel, emptyCount));
            return rows;
        }

        for (Map<String, Object> row : source) {
            rows.add(List.of(safeText(row.get(labelKey)), safeText(row.get(countKey))));
        }
        return rows;
    }

    private final class ReportPdfBuilder {
        private final int pageWidth = 792;
        private final int pageHeight = 612;
        private final int marginLeft = 42;
        private final int marginTop = 40;
        private final int marginBottom = 36;
        private final int rowHeight = 23;
        private final List<String> pages = new ArrayList<>();
        private StringBuilder content;
        private int y;
        private boolean firstPage = true;

        private ReportPdfBuilder() {
            newPage();
        }

        private void newPage() {
            if (content != null) {
                content.append("Q\n");
                pages.add(content.toString());
            }

            content = new StringBuilder();
            content.append("q\n");
            y = pageHeight - marginTop;

            if (!firstPage) {
                drawText("PDTS Reports & Analytics - continued", marginLeft, y, 10, true, "0.545 0 0");
                y -= 24;
            }
            firstPage = false;
        }

        private void title(String text) {
            ensure(34);
            drawText(text, marginLeft, y, 18, true, "0.545 0 0");
            y -= 24;
        }

        private void subtitle(String text) {
            ensure(22);
            drawText(text, marginLeft, y, 10, false, "0.35 0.32 0.32");
            y -= 18;
        }

        private void section(String text) {
            ensure(28);
            drawText(text, marginLeft, y, 12, true, "0.545 0 0");
            y -= 18;
        }

        private void space(int amount) {
            y -= amount;
        }

        private void table(String[] headers, List<List<String>> rows, int[] widths) {
            int totalWidth = 0;
            for (int width : widths) {
                totalWidth += width;
            }

            ensure(rowHeight * 2);
            drawRow(headers, widths, true, false);

            int index = 0;
            for (List<String> row : rows) {
                ensure(rowHeight);
                drawRow(row.toArray(new String[0]), widths, false, index % 2 == 1);
                index++;
            }

            // Fine border under the table for a clean exported-report finish.
            drawLine(marginLeft, y, marginLeft + totalWidth, y, "0.80 0.80 0.80");
        }

        private void drawRow(String[] values, int[] widths, boolean header, boolean alternate) {
            int x = marginLeft;
            String fill = header ? "0.78 0.78 0.78" : (alternate ? "0.97 0.97 0.97" : "1 1 1");
            String textColor = header ? "0 0 0" : "0.10 0.10 0.10";

            for (int i = 0; i < widths.length; i++) {
                int width = widths[i];
                fillRect(x, y - rowHeight, width, rowHeight, fill);
                strokeRect(x, y - rowHeight, width, rowHeight, "0.72 0.72 0.72");

                String value = i < values.length ? values[i] : "";
                int maxChars = Math.max(6, width / 6);
                drawText(truncate(value, maxChars), x + 6, y - 15, header ? 8 : 8, header, textColor);
                x += width;
            }

            y -= rowHeight;
        }

        private void ensure(int requiredHeight) {
            if (y - requiredHeight < marginBottom) {
                newPage();
            }
        }

        private void drawText(String text, int x, int textY, int size, boolean bold, String rgb) {
            content.append("BT\n");
            content.append(bold ? "/F2 " : "/F1 ").append(size).append(" Tf\n");
            content.append(rgb).append(" rg\n");
            content.append(x).append(' ').append(textY).append(" Td\n");
            content.append('(').append(pdfEscape(text)).append(") Tj\n");
            content.append("ET\n");
        }

        private void fillRect(int x, int rectY, int width, int height, String rgb) {
            content.append(rgb).append(" rg\n");
            content.append(x).append(' ').append(rectY).append(' ').append(width).append(' ').append(height).append(" re f\n");
        }

        private void strokeRect(int x, int rectY, int width, int height, String rgb) {
            content.append(rgb).append(" RG\n");
            content.append("0.35 w\n");
            content.append(x).append(' ').append(rectY).append(' ').append(width).append(' ').append(height).append(" re S\n");
        }

        private void drawLine(int x1, int y1, int x2, int y2, String rgb) {
            content.append(rgb).append(" RG\n");
            content.append("0.5 w\n");
            content.append(x1).append(' ').append(y1).append(" m ")
                    .append(x2).append(' ').append(y2).append(" l S\n");
        }

        private byte[] toPdf() {
            if (content != null) {
                content.append("Q\n");
                pages.add(content.toString());
                content = null;
            }

            int objectCount = 4 + (pages.size() * 2);
            List<String> objects = new ArrayList<>();
            objects.add("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < pages.size(); i++) {
                kids.append(5 + i * 2).append(" 0 R ");
            }
            objects.add("2 0 obj\n<< /Type /Pages /Kids [ " + kids + "] /Count " + pages.size() + " >>\nendobj\n");
            objects.add("3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");
            objects.add("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n");

            for (int i = 0; i < pages.size(); i++) {
                int pageObj = 5 + i * 2;
                int contentObj = pageObj + 1;
                String pageObject = pageObj + " 0 obj\n"
                        + "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + pageWidth + " " + pageHeight + "] "
                        + "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + contentObj + " 0 R >>\n"
                        + "endobj\n";
                byte[] contentBytes = pages.get(i).getBytes(StandardCharsets.ISO_8859_1);
                String contentObject = contentObj + " 0 obj\n"
                        + "<< /Length " + contentBytes.length + " >>\n"
                        + "stream\n" + pages.get(i) + "endstream\n"
                        + "endobj\n";
                objects.add(pageObject);
                objects.add(contentObject);
            }

            StringBuilder pdf = new StringBuilder();
            pdf.append("%PDF-1.4\n");
            List<Integer> offsets = new ArrayList<>();
            offsets.add(0);
            for (String object : objects) {
                offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);
                pdf.append(object);
            }

            int xrefOffset = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
            pdf.append("xref\n");
            pdf.append("0 ").append(objectCount + 1).append('\n');
            pdf.append("0000000000 65535 f \n");
            for (int i = 1; i <= objectCount; i++) {
                pdf.append(String.format("%010d 00000 n \n", offsets.get(i)));
            }
            pdf.append("trailer\n");
            pdf.append("<< /Size ").append(objectCount + 1).append(" /Root 1 0 R >>\n");
            pdf.append("startxref\n");
            pdf.append(xrefOffset).append('\n');
            pdf.append("%%EOF");

            return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
        }
    }

    private String baseReportFrom() {
        return """
            FROM applicant ap
            JOIN educational_background_category ebc
              ON ebc.category_id = ap.educational_background_category_id
            LEFT JOIN LATERAL (
                SELECT a.*
                FROM application a
                WHERE a.applicant_id = ap.applicant_id
                ORDER BY a.application_date DESC, a.application_id DESC
                LIMIT 1
            ) latest_app ON TRUE
            LEFT JOIN program p
              ON p.program_id = latest_app.program_id
        """;
    }

    private String clearedCondition() {
        return """
            AND latest_app.application_id IS NOT NULL
            AND EXISTS (
                SELECT 1
                FROM requirement r0
                WHERE r0.application_id = latest_app.application_id
            )
            AND NOT EXISTS (
                SELECT 1
                FROM requirement r1
                JOIN requirement_status rs1
                  ON rs1.status_id = r1.requirement_status_id
                WHERE r1.application_id = latest_app.application_id
                  AND rs1.requirement_status_name <> 'Verified/Received'
            )
        """;
    }

    private int queryCount(String sql, List<Object> params) {
        Number count = jdbc.queryForObject(sql, Number.class, params.toArray());
        return count == null ? 0 : count.intValue();
    }

    private List<Map<String, Object>> toProgressRows(List<Map<String, Object>> rows, boolean regionRows) {
        int max = 0;
        for (Map<String, Object> row : rows) {
            max = Math.max(max, toInt(row.get("count")));
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            int count = toInt(row.get("count"));
            int percent = max == 0 ? 0 : Math.max(6, Math.round((count * 100f) / max));

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", String.valueOf(row.get("label")));
            item.put("filterValue", row.containsKey("filter_value") ? String.valueOf(row.get("filter_value")) : String.valueOf(row.get("label")));
            item.put("count", count);
            item.put("percent", percent);
            item.put("cssClass", regionRows ? "region" : curriculumCssClass(String.valueOf(row.get("label"))));
            result.add(item);
        }

        return result;
    }

    private String curriculumCssClass(String label) {
        String value = label == null ? "" : label.toLowerCase();

        if (value.contains("als")) {
            return "als";
        }
        if (value.contains("college")) {
            return "college";
        }
        if (value.contains("old")) {
            return "old";
        }
        if (value.contains("senior")) {
            return "shs";
        }
        return "";
    }

    private String reportCsvRow(
            String section,
            String category,
            Object item,
            Object value,
            String regionLabel,
            String curriculumLabel,
            String programLabel,
            String statusLabel
    ) {
        return csvLine(
                section,
                category,
                item,
                value,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                regionLabel,
                curriculumLabel,
                programLabel,
                statusLabel
        );
    }

    private byte[] buildFallbackReportPdf(Exception e) {
        ReportPdfBuilder pdf = new ReportPdfBuilder();
        pdf.title("PDTS Reports & Analytics");
        pdf.subtitle("The report PDF could not be generated completely.");
        pdf.space(12);
        pdf.section("Export Error");
        pdf.table(
                new String[]{"Field", "Details"},
                List.of(
                        List.of("Error Type", safeText(e == null ? "Unknown" : e.getClass().getSimpleName())),
                        List.of("Message", truncate(safeText(e == null ? "No message available" : e.getMessage()), 90))
                ),
                new int[]{150, 500}
        );
        return pdf.toPdf();
    }

    private String safeText(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString()
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
    }

    private String truncate(String value, int maxLength) {
        String text = safeText(value);
        if (maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        if (maxLength <= 3) {
            return text.substring(0, maxLength);
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private String pdfEscape(String value) {
        String text = safeText(value)
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");

        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 32 && c <= 126) {
                safe.append(c);
            } else if (c == 8211 || c == 8212) {
                safe.append('-');
            } else if (c == 8216 || c == 8217) {
                safe.append('\'');
            } else if (c == 8220 || c == 8221) {
                safe.append('"');
            } else {
                safe.append(' ');
            }
        }
        return safe.toString();
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(value.toString());
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String csvLine(Object... values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(csvValue(values[i]));
        }
        line.append('\n');
        return line.toString();
    }

    private String csvValue(Object value) {
        if (value == null) {
            return "\"\"";
        }
        String text = value.toString().replace("\r", " ").replace("\n", " ");
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static final class EmailLogQuery {
        private final String where;
        private final List<Object> params;

        private EmailLogQuery(String where, List<Object> params) {
            this.where = where;
            this.params = params;
        }
    }

    private static final class ReportExportData {
        private final int filteredStudents;
        private final int clearedStudents;
        private final int pendingRequirements;
        private final List<Map<String, Object>> curriculumBreakdown;
        private final List<Map<String, Object>> regionBreakdown;
        private final List<Map<String, Object>> studentRows;

        private ReportExportData(
                int filteredStudents,
                int clearedStudents,
                int pendingRequirements,
                List<Map<String, Object>> curriculumBreakdown,
                List<Map<String, Object>> regionBreakdown,
                List<Map<String, Object>> studentRows
        ) {
            this.filteredStudents = filteredStudents;
            this.clearedStudents = clearedStudents;
            this.pendingRequirements = pendingRequirements;
            this.curriculumBreakdown = curriculumBreakdown;
            this.regionBreakdown = regionBreakdown;
            this.studentRows = studentRows;
        }
    }

    private static final class ReportQuery {
        private final String where;
        private final List<Object> params;

        private ReportQuery(String where, List<Object> params) {
            this.where = where;
            this.params = params;
        }
    }

    private String getEmailTypeLabel(String emailType) {
        if ("pending".equals(emailType)) {
            return "Pending Requirements Reminder";
        }
        if ("received".equals(emailType)) {
            return "Acknowledgment Receipt";
        }
        if ("rejected".equals(emailType)) {
            return "Document Rejection Notice";
        }
        if ("deadline".equals(emailType)) {
            return "Deadline Alert";
        }
        return "Email Notification";
    }
}
