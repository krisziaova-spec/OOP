package com.pdts.controller;

import com.pdts.service.AuditLogService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
public class SettingsController {

    private final JdbcTemplate jdbc;
    private final AuditLogService auditLogService;

    public SettingsController(JdbcTemplate jdbc, AuditLogService auditLogService) {
        this.jdbc = jdbc;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/settings/system")
    public String updateSystemSettings(@RequestParam Map<String, String> form, RedirectAttributes ra) {
        try {
            updateSetting("academic_year", form.get("academicYear"));
            updateSetting("current_semester", form.get("currentSemester"));
            updateSetting("max_photo_upload_kb", form.get("maxPhotoUploadKb"));
            updateSetting("email_reminder_day", form.get("emailReminderDay"));
            updateSetting("portal_status", form.get("portalStatus"));

            auditLogService.log("UPDATE_SETTINGS", "system_setting", null,
                    "Updated system configuration", null, "System settings updated");

            ra.addFlashAttribute("success", "System configuration updated.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to update system configuration: " + e.getMessage());
        }

        return "redirect:/users";
    }

    @PostMapping("/settings/rejection-reasons")
    public String addRejectionReason(@RequestParam Map<String, String> form, RedirectAttributes ra) {
        try {
            jdbc.update("""
                    INSERT INTO rejection_reason (
                        rejection_reason_name,
                        rejection_reason_description,
                        rejection_reason_is_active
                    )
                    VALUES (?, ?, 1)
                    """,
                    required(form, "reasonName"),
                    required(form, "reasonDescription")
            );

            auditLogService.log("CREATE_REJECTION_REASON", "rejection_reason", null,
                    "Created rejection reason", null, required(form, "reasonName"));

            ra.addFlashAttribute("success", "Rejection reason added.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to add rejection reason: " + e.getMessage());
        }

        return "redirect:/users";
    }

    @PostMapping("/settings/rejection-reasons/{id}/update")
    public String updateRejectionReason(@PathVariable Integer id,
                                        @RequestParam Map<String, String> form,
                                        RedirectAttributes ra) {
        try {
            jdbc.update("""
                    UPDATE rejection_reason
                    SET rejection_reason_name = ?,
                        rejection_reason_description = ?,
                        rejection_reason_updated_at = CURRENT_TIMESTAMP
                    WHERE rejection_reason_id = ?
                    """,
                    required(form, "reasonName"),
                    required(form, "reasonDescription"),
                    id
            );

            auditLogService.log("UPDATE_REJECTION_REASON", "rejection_reason", id.longValue(),
                    "Updated rejection reason", null, required(form, "reasonName"));

            ra.addFlashAttribute("success", "Rejection reason updated.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to update rejection reason: " + e.getMessage());
        }

        return "redirect:/users";
    }

    @PostMapping("/settings/rejection-reasons/{id}/toggle")
    public String toggleRejectionReason(@PathVariable Integer id, RedirectAttributes ra) {
        try {
            jdbc.update("""
                    UPDATE rejection_reason
                    SET rejection_reason_is_active = CASE
                        WHEN rejection_reason_is_active = 1 THEN 0
                        ELSE 1
                    END,
                    rejection_reason_updated_at = CURRENT_TIMESTAMP
                    WHERE rejection_reason_id = ?
                    """, id);

            auditLogService.log("TOGGLE_REJECTION_REASON", "rejection_reason", id.longValue(),
                    "Activated/deactivated rejection reason", null, null);

            ra.addFlashAttribute("success", "Rejection reason status updated.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to update rejection reason status: " + e.getMessage());
        }

        return "redirect:/users";
    }

    @PostMapping("/settings/campuses")
    public String addCampus(@RequestParam Map<String, String> form, RedirectAttributes ra) {
        try {
            jdbc.update("""
                    INSERT INTO campus (
                        campus_name,
                        campus_address,
                        campus_is_active
                    )
                    VALUES (?, ?, 1)
                    """,
                    required(form, "campusName"),
                    blankToNull(form.get("campusAddress"))
            );

            auditLogService.log("CREATE_CAMPUS", "campus", null,
                    "Created campus", null, required(form, "campusName"));

            ra.addFlashAttribute("success", "Campus added.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to add campus: " + e.getMessage());
        }

        return "redirect:/users";
    }

    @PostMapping("/settings/campuses/{id}/toggle")
    public String toggleCampus(@PathVariable Integer id, RedirectAttributes ra) {
        try {
            jdbc.update("""
                    UPDATE campus
                    SET campus_is_active = CASE
                        WHEN campus_is_active = 1 THEN 0
                        ELSE 1
                    END,
                    campus_updated_at = CURRENT_TIMESTAMP
                    WHERE campus_id = ?
                    """, id);

            auditLogService.log("TOGGLE_CAMPUS", "campus", id.longValue(),
                    "Activated/deactivated campus", null, null);

            ra.addFlashAttribute("success", "Campus status updated.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to update campus status: " + e.getMessage());
        }

        return "redirect:/users";
    }

    @PostMapping("/settings/programs")
    public String addProgram(@RequestParam Map<String, String> form, RedirectAttributes ra) {
        try {
            jdbc.update("""
                    INSERT INTO program (
                        program_name,
                        program_code,
                        program_is_active
                    )
                    VALUES (?, ?, 1)
                    """,
                    required(form, "programName"),
                    required(form, "programCode")
            );

            auditLogService.log("CREATE_PROGRAM", "program", null,
                    "Created program", null, required(form, "programCode"));

            ra.addFlashAttribute("success", "Program added.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to add program: " + e.getMessage());
        }

        return "redirect:/users";
    }

    @PostMapping("/settings/programs/{id}/toggle")
    public String toggleProgram(@PathVariable Integer id, RedirectAttributes ra) {
        try {
            jdbc.update("""
                    UPDATE program
                    SET program_is_active = CASE
                        WHEN program_is_active = 1 THEN 0
                        ELSE 1
                    END,
                    program_updated_at = CURRENT_TIMESTAMP
                    WHERE program_id = ?
                    """, id);

            auditLogService.log("TOGGLE_PROGRAM", "program", id.longValue(),
                    "Activated/deactivated program", null, null);

            ra.addFlashAttribute("success", "Program status updated.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to update program status: " + e.getMessage());
        }

        return "redirect:/users";
    }

    private void updateSetting(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required.");
        }

        jdbc.update("""
                UPDATE system_setting
                SET setting_value = ?,
                    setting_updated_at = CURRENT_TIMESTAMP
                WHERE setting_key = ?
                """, value.trim(), key);
    }

    private String required(Map<String, String> form, String key) {
        String value = form.get(key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required.");
        }

        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
