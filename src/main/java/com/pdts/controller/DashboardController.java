package com.pdts.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class DashboardController {

    private final JdbcTemplate jdbc;

    public DashboardController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        int applicantCount = count("""
                SELECT COUNT(*)
                FROM applicant
                WHERE COALESCE(applicant_is_deleted, 0) = 0
                """);

        model.addAttribute("userCount", count("SELECT COUNT(*) FROM app_user"));
        model.addAttribute("activeUserCount", count("SELECT COUNT(*) FROM app_user WHERE user_is_active = 1"));
        model.addAttribute("applicantCount", applicantCount);
        model.addAttribute("todayDate", LocalDate.now().toString());

        model.addAttribute("applicationCount", count("""
                SELECT COUNT(*)
                FROM application a
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                WHERE COALESCE(ap.applicant_is_deleted, 0) = 0
                """));

        model.addAttribute("requirementCount", count("""
                SELECT COUNT(*)
                FROM requirement r
                JOIN application a ON a.application_id = r.application_id
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                WHERE COALESCE(ap.applicant_is_deleted, 0) = 0
                """));

        model.addAttribute("pendingDocumentsCount", count("""
                SELECT COUNT(*)
                FROM requirement r
                JOIN requirement_status rs ON rs.status_id = r.requirement_status_id
                JOIN application a ON a.application_id = r.application_id
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                WHERE rs.requirement_status_name IN ('Pending', 'Under Review')
                  AND COALESCE(ap.applicant_is_deleted, 0) = 0
                """));

        model.addAttribute("temporarilyEnrolledCount", count("""
                SELECT COUNT(DISTINCT ap.applicant_id)
                FROM application a
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                JOIN application_status ast ON ast.application_status_id = a.application_status_id
                WHERE ast.application_status_name = 'Temporarily Enrolled'
                  AND COALESCE(ap.applicant_is_deleted, 0) = 0
                """));

        model.addAttribute("enrolledStudentsCount", count("""
                SELECT COUNT(DISTINCT ap.applicant_id)
                FROM application a
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                JOIN application_status ast ON ast.application_status_id = a.application_status_id
                WHERE ast.application_status_name = 'Enrolled'
                  AND COALESCE(ap.applicant_is_deleted, 0) = 0
                """));

        model.addAttribute("needsActionCount", count("""
                SELECT COUNT(*)
                FROM requirement r
                JOIN requirement_status rs ON rs.status_id = r.requirement_status_id
                JOIN application a ON a.application_id = r.application_id
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                WHERE rs.requirement_status_name IN ('Rejected', 'For Resubmission')
                  AND COALESCE(ap.applicant_is_deleted, 0) = 0
                """));

        model.addAttribute("emailsSentTodayCount", count("""
                SELECT COUNT(*)
                FROM user_activity_log
                WHERE user_activity_log_action_type IN ('SEND_EMAIL', 'SEND_BULK_EMAIL')
                  AND user_activity_log_performed_at::date = CURRENT_DATE
                """));

        model.addAttribute("pendingCount", count("""
                SELECT COUNT(*)
                FROM requirement r
                JOIN application a ON a.application_id = r.application_id
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                WHERE r.requirement_status_id = 1
                  AND COALESCE(ap.applicant_is_deleted, 0) = 0
                """));

        model.addAttribute("underReviewCount", count("""
                SELECT COUNT(*)
                FROM requirement r
                JOIN application a ON a.application_id = r.application_id
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                WHERE r.requirement_status_id = 2
                  AND COALESCE(ap.applicant_is_deleted, 0) = 0
                """));

        model.addAttribute("verifiedCount", count("""
                SELECT COUNT(*)
                FROM requirement r
                JOIN application a ON a.application_id = r.application_id
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                WHERE r.requirement_status_id = 3
                  AND COALESCE(ap.applicant_is_deleted, 0) = 0
                """));

        model.addAttribute("rejectedCount", count("""
                SELECT COUNT(*)
                FROM requirement r
                JOIN application a ON a.application_id = r.application_id
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                WHERE r.requirement_status_id = 4
                  AND COALESCE(ap.applicant_is_deleted, 0) = 0
                """));

        model.addAttribute("resubmissionCount", count("""
                SELECT COUNT(*)
                FROM requirement r
                JOIN application a ON a.application_id = r.application_id
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                WHERE r.requirement_status_id = 5
                  AND COALESCE(ap.applicant_is_deleted, 0) = 0
                """));

        model.addAttribute("recentApplicants", jdbc.queryForList("""
                SELECT applicant_id,
                       applicant_first_name,
                       applicant_last_name,
                       applicant_email_address,
                       applicant_region,
                       applicant_enrollment_status,
                       applicant_created_at
                FROM applicant
                WHERE COALESCE(applicant_is_deleted, 0) = 0
                ORDER BY applicant_created_at DESC, applicant_id DESC
                LIMIT 8
                """));

        model.addAttribute("recentRequirements", recentRequirements());
        model.addAttribute("recentApplications", recentApplications());
        model.addAttribute("admissionStatusSummary", admissionStatusSummary());
        model.addAttribute("curriculumBreakdown", curriculumBreakdown(applicantCount));
        model.addAttribute("upcomingDeadlines", upcomingDeadlines());
        model.addAttribute("liveUpdates", liveUpdates());

        return "dashboard";
    }

    @GetMapping("/dashboard/live-updates")
    @ResponseBody
    public List<Map<String, Object>> dashboardLiveUpdates() {
        return liveUpdates();
    }


    @GetMapping("/dashboard/upcoming-deadlines")
    @ResponseBody
    public List<Map<String, Object>> dashboardUpcomingDeadlines() {
        return upcomingDeadlines();
    }

    private List<Map<String, Object>> recentRequirements() {
        return jdbc.queryForList("""
                SELECT r.requirement_id,
                       r.requirement_tracking_no,
                       r.requirement_file_name,
                       TO_CHAR(r.requirement_upload_date, 'Mon DD, YYYY · HH12:MI AM') AS requirement_upload_date_display,
                       rt.requirement_type_name,
                       rs.requirement_status_name,
                       ap.applicant_first_name,
                       ap.applicant_last_name,
                       a.application_reference_number
                FROM requirement r
                JOIN requirement_type rt ON rt.type_id = r.requirement_type_id
                JOIN requirement_status rs ON rs.status_id = r.requirement_status_id
                JOIN application a ON a.application_id = r.application_id
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                WHERE COALESCE(ap.applicant_is_deleted, 0) = 0
                ORDER BY r.requirement_upload_date DESC, r.requirement_id DESC
                LIMIT 8
                """);
    }

    private List<Map<String, Object>> recentApplications() {
        return jdbc.queryForList("""
                SELECT a.application_id,
                       a.application_reference_number,
                       a.application_date,
                       ast.application_status_name,
                       p.program_code,
                       p.program_name,
                       ap.applicant_first_name,
                       ap.applicant_last_name
                FROM application a
                JOIN applicant ap ON ap.applicant_id = a.applicant_id
                JOIN program p ON p.program_id = a.program_id
                JOIN application_status ast ON ast.application_status_id = a.application_status_id
                WHERE COALESCE(ap.applicant_is_deleted, 0) = 0
                ORDER BY a.application_date DESC, a.application_id DESC
                LIMIT 8
                """);
    }

    private List<Map<String, Object>> admissionStatusSummary() {
        return jdbc.queryForList("""
                SELECT ast.application_status_name,
                       ast.application_status_color,
                       COUNT(DISTINCT CASE WHEN ap.applicant_id IS NOT NULL THEN ap.applicant_id END) AS total
                FROM application_status ast
                LEFT JOIN application a ON a.application_status_id = ast.application_status_id
                LEFT JOIN applicant ap ON ap.applicant_id = a.applicant_id
                     AND COALESCE(ap.applicant_is_deleted, 0) = 0
                GROUP BY ast.application_status_id, ast.application_status_name, ast.application_status_color
                ORDER BY ast.application_status_id
                """);
    }

    private List<Map<String, Object>> curriculumBreakdown(int applicantCount) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT ebc.category_id,
                       ebc.category_name,
                       ebc.category_code,
                       COUNT(DISTINCT ap.applicant_id) AS total
                FROM educational_background_category ebc
                LEFT JOIN applicant ap ON ap.educational_background_category_id = ebc.category_id
                     AND COALESCE(ap.applicant_is_deleted, 0) = 0
                WHERE COALESCE(ebc.category_is_active, 1) = 1
                GROUP BY ebc.category_id, ebc.category_name, ebc.category_code
                ORDER BY ebc.category_name
                """);

        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            int total = numberValue(row.get("total"));
            int percent = applicantCount <= 0 ? 0 : Math.round((total * 100.0f) / applicantCount);
            copy.put("percent", Math.max(0, Math.min(100, percent)));
            enriched.add(copy);
        }
        return enriched;
    }


    private List<Map<String, Object>> upcomingDeadlines() {
        return jdbc.queryForList("""
                WITH required_docs AS (
                    SELECT
                        rt.type_id,
                        rt.requirement_type_name,
                        (a.application_date + 30) AS first_due_date,
                        latest_req.requirement_id,
                        latest_req.requirement_status_id
                    FROM application a
                    JOIN applicant ap ON ap.applicant_id = a.applicant_id
                    JOIN curriculum_requirement cr ON cr.category_id = ap.educational_background_category_id
                    JOIN requirement_type rt ON rt.type_id = cr.type_id
                    LEFT JOIN LATERAL (
                        SELECT r.requirement_id,
                               r.requirement_status_id,
                               r.requirement_upload_date
                        FROM requirement r
                        WHERE r.application_id = a.application_id
                          AND r.requirement_type_id = rt.type_id
                        ORDER BY r.requirement_upload_date DESC, r.requirement_id DESC
                        LIMIT 1
                    ) latest_req ON TRUE
                    WHERE COALESCE(ap.applicant_is_deleted, 0) = 0
                      AND COALESCE(rt.type_is_active, 1) = 1
                      AND COALESCE(cr.is_mandatory, 1) = 1
                      AND (
                            latest_req.requirement_id IS NULL
                            OR latest_req.requirement_status_id IN (4, 5)
                          )
                ),
                rolling_deadlines AS (
                    SELECT
                        type_id,
                        requirement_type_name,
                        CASE
                            WHEN CURRENT_DATE <= first_due_date THEN first_due_date
                            ELSE first_due_date
                                 + (
                                    CEIL(((CURRENT_DATE - first_due_date)::numeric) / 30)::int
                                    * 30
                                   )
                        END AS next_deadline
                    FROM required_docs
                ),
                grouped_deadlines AS (
                    SELECT
                        type_id,
                        requirement_type_name,
                        MIN(next_deadline) AS next_deadline
                    FROM rolling_deadlines
                    GROUP BY type_id, requirement_type_name
                )
                SELECT
                    requirement_type_name AS deadline_description,
                    TO_CHAR(next_deadline, 'Mon DD, YYYY') AS deadline_date_display,
                    CASE
                        WHEN next_deadline <= CURRENT_DATE THEN 'danger'
                        WHEN next_deadline <= CURRENT_DATE + 7 THEN 'warn'
                        ELSE 'ok'
                    END AS deadline_class
                FROM grouped_deadlines
                ORDER BY next_deadline ASC, requirement_type_name ASC
                """);
    }

    private List<Map<String, Object>> liveUpdates() {
        return jdbc.queryForList("""
                WITH required_docs AS (
                    SELECT
                        a.application_id,
                        a.application_reference_number,
                        rt.type_id,
                        rt.requirement_type_name,
                        COALESCE(dl.deadline_date, a.application_date + 30) AS due_date,
                        latest_req.requirement_id,
                        latest_req.requirement_status_id,
                        latest_req.requirement_tracking_no,
                        latest_req.requirement_upload_date,
                        latest_req.requirement_date_received,
                        latest_req.requirement_processed_at
                    FROM application a
                    JOIN applicant ap ON ap.applicant_id = a.applicant_id
                    JOIN curriculum_requirement cr ON cr.category_id = ap.educational_background_category_id
                    JOIN requirement_type rt ON rt.type_id = cr.type_id
                    LEFT JOIN LATERAL (
                        SELECT d.deadline_date
                        FROM deadline d
                        WHERE d.requirement_type_id = rt.type_id
                        ORDER BY d.deadline_date DESC, d.deadline_id DESC
                        LIMIT 1
                    ) dl ON TRUE
                    LEFT JOIN LATERAL (
                        SELECT r.*
                        FROM requirement r
                        WHERE r.application_id = a.application_id
                          AND r.requirement_type_id = rt.type_id
                        ORDER BY r.requirement_upload_date DESC, r.requirement_id DESC
                        LIMIT 1
                    ) latest_req ON TRUE
                    WHERE COALESCE(ap.applicant_is_deleted, 0) = 0
                      AND COALESCE(rt.type_is_active, 1) = 1
                ),
                all_updates AS (
                    SELECT
                        due_date::timestamp AS feed_time,
                        'Overdue · ' || TO_CHAR(due_date, 'Mon DD, YYYY') AS feed_time_display,
                        requirement_type_name || ' is overdue for ' || application_reference_number || '.' AS feed_text,
                        'Overdue' AS feed_tag,
                        'red' AS feed_class,
                        1 AS feed_priority
                    FROM required_docs
                    WHERE COALESCE(requirement_status_id, 0) <> 3
                      AND due_date < CURRENT_DATE

                    UNION ALL

                    SELECT
                        due_date::timestamp AS feed_time,
                        'Due soon · ' || TO_CHAR(due_date, 'Mon DD, YYYY') AS feed_time_display,
                        requirement_type_name || ' is due soon for ' || application_reference_number || '.' AS feed_text,
                        'Due Soon' AS feed_tag,
                        'gold' AS feed_class,
                        2 AS feed_priority
                    FROM required_docs
                    WHERE COALESCE(requirement_status_id, 0) <> 3
                      AND due_date BETWEEN CURRENT_DATE AND CURRENT_DATE + 7

                    UNION ALL

                    SELECT
                        COALESCE(r.requirement_processed_at, r.rejection_reason_rejected_at, r.requirement_date_received, r.requirement_upload_date) AS feed_time,
                        TO_CHAR(COALESCE(r.requirement_processed_at, r.rejection_reason_rejected_at, r.requirement_date_received, r.requirement_upload_date), 'Mon DD, YYYY · HH12:MI AM') AS feed_time_display,
                        rt.requirement_type_name || ' needs action: ' || rs.requirement_status_name || ' for ' || a.application_reference_number || '.' AS feed_text,
                        'Needs Action' AS feed_tag,
                        'red' AS feed_class,
                        3 AS feed_priority
                    FROM requirement r
                    JOIN requirement_type rt ON rt.type_id = r.requirement_type_id
                    JOIN requirement_status rs ON rs.status_id = r.requirement_status_id
                    JOIN application a ON a.application_id = r.application_id
                    JOIN applicant ap ON ap.applicant_id = a.applicant_id
                    WHERE COALESCE(ap.applicant_is_deleted, 0) = 0
                      AND rs.requirement_status_name IN ('Rejected', 'For Resubmission')

                    UNION ALL

                    SELECT
                        r.requirement_upload_date AS feed_time,
                        TO_CHAR(r.requirement_upload_date, 'Mon DD, YYYY · HH12:MI AM') AS feed_time_display,
                        rt.requirement_type_name || ' submitted for ' || a.application_reference_number || '.' AS feed_text,
                        'Submitted' AS feed_tag,
                        'gold' AS feed_class,
                        4 AS feed_priority
                    FROM requirement r
                    JOIN requirement_type rt ON rt.type_id = r.requirement_type_id
                    JOIN requirement_status rs ON rs.status_id = r.requirement_status_id
                    JOIN application a ON a.application_id = r.application_id
                    JOIN applicant ap ON ap.applicant_id = a.applicant_id
                    WHERE COALESCE(ap.applicant_is_deleted, 0) = 0
                      AND rs.requirement_status_name IN ('Pending', 'Under Review')

                    UNION ALL

                    SELECT
                        COALESCE(r.requirement_processed_at, r.requirement_date_received, r.requirement_upload_date) AS feed_time,
                        TO_CHAR(COALESCE(r.requirement_processed_at, r.requirement_date_received, r.requirement_upload_date), 'Mon DD, YYYY · HH12:MI AM') AS feed_time_display,
                        rt.requirement_type_name || ' verified/received for ' || a.application_reference_number || '.' AS feed_text,
                        'Cleared' AS feed_tag,
                        'green' AS feed_class,
                        5 AS feed_priority
                    FROM requirement r
                    JOIN requirement_type rt ON rt.type_id = r.requirement_type_id
                    JOIN requirement_status rs ON rs.status_id = r.requirement_status_id
                    JOIN application a ON a.application_id = r.application_id
                    JOIN applicant ap ON ap.applicant_id = a.applicant_id
                    WHERE COALESCE(ap.applicant_is_deleted, 0) = 0
                      AND rs.requirement_status_name = 'Verified/Received'

                    UNION ALL

                    SELECT
                        aul.user_activity_log_performed_at AS feed_time,
                        TO_CHAR(aul.user_activity_log_performed_at, 'Mon DD, YYYY · HH12:MI AM') AS feed_time_display,
                        COALESCE(NULLIF(TRIM(aul.user_activity_log_description), ''), aul.user_activity_log_action_type) AS feed_text,
                        CASE
                            WHEN aul.user_activity_log_action_type IN ('SEND_EMAIL', 'SEND_BULK_EMAIL') THEN 'Email'
                            WHEN aul.user_activity_log_action_type = 'BULK_DOCUMENT_STATUS_UPDATE' THEN 'Bulk Update'
                            ELSE 'Status'
                        END AS feed_tag,
                        CASE
                            WHEN aul.user_activity_log_action_type IN ('SEND_EMAIL', 'SEND_BULK_EMAIL') THEN 'gray'
                            WHEN aul.user_activity_log_action_type = 'BULK_DOCUMENT_STATUS_UPDATE' THEN 'green'
                            ELSE 'green'
                        END AS feed_class,
                        6 AS feed_priority
                    FROM user_activity_log aul
                    WHERE aul.user_activity_log_action_type IN (
                        'SEND_EMAIL',
                        'SEND_BULK_EMAIL',
                        'BULK_DOCUMENT_STATUS_UPDATE',
                        'UPDATE_REQUIREMENT_STATUS',
                        'UPDATE_APPLICATION_STATUS'
                    )
                ),
                ranked_updates AS (
                    SELECT
                        feed_time,
                        feed_time_display,
                        feed_text,
                        feed_tag,
                        feed_class,
                        feed_priority,
                        ROW_NUMBER() OVER (PARTITION BY feed_tag ORDER BY feed_time DESC) AS category_rank
                    FROM all_updates
                    WHERE feed_time IS NOT NULL
                )
                SELECT feed_time_display,
                       feed_text,
                       feed_tag,
                       feed_class
                FROM ranked_updates
                WHERE category_rank <= 8
                ORDER BY feed_priority ASC, feed_time DESC
                LIMIT 30
                """);
    }

    private int numberValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Integer count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
