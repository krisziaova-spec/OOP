package com.pdts.controller;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LogsPageController {

    private final JdbcTemplate jdbc;

    public LogsPageController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/logs")
    public String logs(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String keyword,
            Model model
    ) {
        AuditLogQuery query = buildAuditLogQuery(dateFrom, dateTo, user, action, keyword);

        List<Map<String, Object>> rows = jdbc.queryForList(auditLogSelectSql() + query.where + """
                ORDER BY l.user_activity_log_performed_at DESC
                LIMIT 300
                """, query.params.toArray());
        rows.forEach(this::addDisplayLabels);
        model.addAttribute("logs", rows);

        Number totalMatches = jdbc.queryForObject(
                auditLogCountSql() + query.where,
                Number.class,
                query.params.toArray()
        );

        model.addAttribute("totalMatches", totalMatches == null ? 0 : totalMatches.intValue());
        model.addAttribute("users", jdbc.queryForList("""
                SELECT DISTINCT u.user_username
                FROM user_activity_log l
                JOIN app_user u ON u.user_id = l.user_activity_log_user_id
                ORDER BY u.user_username
                """, String.class));
        List<String> rawActions = jdbc.queryForList("""
                SELECT DISTINCT user_activity_log_action_type
                FROM user_activity_log
                ORDER BY user_activity_log_action_type
                """, String.class);
        List<Map<String, String>> actionOptions = new ArrayList<>();
        for (String actionValue : rawActions) {
            Map<String, String> option = new java.util.HashMap<>();
            option.put("value", clean(actionValue));
            option.put("label", toDisplayLabel(actionValue));
            actionOptions.add(option);
        }
        model.addAttribute("actions", actionOptions);

        model.addAttribute("selectedDateFrom", clean(dateFrom));
        model.addAttribute("selectedDateTo", clean(dateTo));
        model.addAttribute("selectedUser", clean(user));
        model.addAttribute("selectedAction", clean(action));
        model.addAttribute("selectedKeyword", clean(keyword));

        return "logs";
    }

    @GetMapping(value = "/logs/export", produces = "text/csv")
    public ResponseEntity<String> exportLogs(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String keyword
    ) {
        AuditLogQuery query = buildAuditLogQuery(dateFrom, dateTo, user, action, keyword);

        List<Map<String, Object>> rows = jdbc.queryForList(auditLogSelectSql() + query.where + """
                ORDER BY l.user_activity_log_performed_at DESC
                """, query.params.toArray());

        StringBuilder csv = new StringBuilder();
        csv.append(csvLine(
                "Timestamp",
                "User",
                "Action",
                "Entity",
                "Record ID",
                "Details",
                "Old Value",
                "New Value"
        ));

        for (Map<String, Object> row : rows) {
            csv.append(csvLine(
                    row.get("user_activity_log_performed_at"),
                    row.get("user_username"),
                    toDisplayLabel(row.get("user_activity_log_action_type")),
                    toDisplayLabel(row.get("user_activity_log_entity_type")),
                    row.get("display_record_id"),
                    row.get("user_activity_log_description"),
                    row.get("user_activity_log_old_value"),
                    row.get("user_activity_log_new_value")
            ));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=PDTS_Audit_Trail.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    private String auditLogSelectSql() {
        return """
                SELECT
                    l.user_activity_log_id,
                    l.archived_record_id,
                    COALESCE(l.archived_record_id, l.user_activity_log_id) AS display_record_id,
                    u.user_username,
                    l.user_activity_log_action_type,
                    l.user_activity_log_entity_type,
                    l.user_activity_log_description,
                    l.user_activity_log_old_value,
                    l.user_activity_log_new_value,
                    l.user_activity_log_performed_at
                FROM user_activity_log l
                JOIN app_user u
                    ON u.user_id = l.user_activity_log_user_id
                """;
    }

    private String auditLogCountSql() {
        return """
                SELECT COUNT(*)
                FROM user_activity_log l
                JOIN app_user u
                    ON u.user_id = l.user_activity_log_user_id
                """;
    }

    private AuditLogQuery buildAuditLogQuery(
            String dateFrom,
            String dateTo,
            String user,
            String action,
            String keyword
    ) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
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

        String cleanUser = clean(user);
        if (!cleanUser.isBlank()) {
            where.append(" AND u.user_username = ? ");
            params.add(cleanUser);
        }

        String cleanAction = clean(action);
        if (!cleanAction.isBlank()) {
            where.append(" AND l.user_activity_log_action_type = ? ");
            params.add(cleanAction);
        }


        String cleanKeyword = clean(keyword).toLowerCase();
        if (!cleanKeyword.isBlank()) {
            String like = "%" + cleanKeyword + "%";
            where.append("""
                    AND (
                        LOWER(COALESCE(l.user_activity_log_description, '')) LIKE ?
                        OR LOWER(COALESCE(l.user_activity_log_old_value, '')) LIKE ?
                        OR LOWER(COALESCE(l.user_activity_log_new_value, '')) LIKE ?
                        OR LOWER(COALESCE(u.user_username, '')) LIKE ?
                    )
                    """);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }

        return new AuditLogQuery(where.toString(), params);
    }


    private void addDisplayLabels(Map<String, Object> row) {
        row.put("display_action_type", toDisplayLabel(row.get("user_activity_log_action_type")));
        row.put("display_entity_type", toDisplayLabel(row.get("user_activity_log_entity_type")));
    }

    private String toDisplayLabel(Object value) {
        String text = value == null ? "" : value.toString().trim();
        if (text.isBlank()) {
            return "—";
        }

        text = text.replace('_', ' ').replace('-', ' ').toLowerCase();
        StringBuilder label = new StringBuilder();
        boolean capitalizeNext = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                if (label.length() > 0 && label.charAt(label.length() - 1) != ' ') {
                    label.append(' ');
                }
                capitalizeNext = true;
            } else if (capitalizeNext) {
                label.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                label.append(c);
            }
        }
        return label.toString().trim();
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

    private static final class AuditLogQuery {
        private final String where;
        private final List<Object> params;

        private AuditLogQuery(String where, List<Object> params) {
            this.where = where;
            this.params = params;
        }
    }
}
