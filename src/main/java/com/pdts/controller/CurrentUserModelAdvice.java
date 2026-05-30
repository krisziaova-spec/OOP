package com.pdts.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Map;

@ControllerAdvice(annotations = Controller.class)
public class CurrentUserModelAdvice {

    private final JdbcTemplate jdbc;

    public CurrentUserModelAdvice(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @ModelAttribute
    public void addCurrentUser(Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }

        String username = authentication.getName();
        if (username == null || username.isBlank() || "anonymousUser".equalsIgnoreCase(username)) {
            return;
        }

        String displayName = username;
        String roleName = "Registrar Office";
        String initials = buildInitials(username, null);
        String photoUrl = null;

        try {
            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT
                        u.user_username,
                        u.user_first_name,
                        u.user_last_name,
                        u.user_photo_url,
                        r.role_name
                    FROM app_user u
                    LEFT JOIN role r ON r.role_id = u.role_id
                    WHERE LOWER(u.user_username) = LOWER(?)
                    """, username);

            String firstName = asString(row.get("user_first_name"));
            String lastName = asString(row.get("user_last_name"));
            displayName = buildDisplayName(firstName, lastName, username);
            initials = buildInitials(firstName, lastName);
            roleName = asString(row.get("role_name"), roleName);
            photoUrl = asString(row.get("user_photo_url"));

        } catch (Exception firstQueryFailed) {
            // Safe fallback for databases that have not added user_photo_url yet.
            try {
                Map<String, Object> row = jdbc.queryForMap("""
                        SELECT
                            u.user_username,
                            u.user_first_name,
                            u.user_last_name,
                            r.role_name
                        FROM app_user u
                        LEFT JOIN role r ON r.role_id = u.role_id
                        WHERE LOWER(u.user_username) = LOWER(?)
                        """, username);

                String firstName = asString(row.get("user_first_name"));
                String lastName = asString(row.get("user_last_name"));
                displayName = buildDisplayName(firstName, lastName, username);
                initials = buildInitials(firstName, lastName);
                roleName = asString(row.get("role_name"), roleName);
            } catch (Exception ignored) {
                // Keep the default username/initials fallback.
            }
        }

        boolean isHeadAdmission = "Head Admission".equalsIgnoreCase(roleName);
        boolean isAdmin = "Admin".equalsIgnoreCase(roleName);
        boolean isAdmissionPersonnel = "Admission Personnel".equalsIgnoreCase(roleName);

        model.addAttribute("currentUsername", username);
        model.addAttribute("currentUserDisplayName", displayName);
        model.addAttribute("currentUserRoleName", roleName);
        model.addAttribute("currentUserInitials", initials);
        model.addAttribute("currentUserPhotoUrl", photoUrl == null ? "" : photoUrl);

        // View-only flags. These do not grant access; SecurityConfig remains the authority.
        model.addAttribute("canViewEmailNotifications", isHeadAdmission || isAdmin);
        model.addAttribute("canViewReports", isHeadAdmission || isAdmin);
        model.addAttribute("canManageSystem", isHeadAdmission);
        model.addAttribute("canUseOperationalPages", isHeadAdmission || isAdmin || isAdmissionPersonnel);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String asString(Object value, String fallback) {
        String text = asString(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private String buildDisplayName(String firstName, String lastName, String username) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String full = (first + " " + last).trim();
        return full.isBlank() ? username : full;
    }

    private String buildInitials(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();

        StringBuilder initials = new StringBuilder();
        if (!first.isBlank()) {
            initials.append(first.charAt(0));
        }
        if (!last.isBlank()) {
            initials.append(last.charAt(0));
        }

        if (initials.length() == 0 && !first.isBlank()) {
            initials.append(first.substring(0, Math.min(2, first.length())));
        }
        if (initials.length() == 0) {
            initials.append("ST");
        }

        return initials.toString().toUpperCase();
    }
}
