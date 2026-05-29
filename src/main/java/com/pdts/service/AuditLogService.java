package com.pdts.service;

import com.pdts.model.User;
import com.pdts.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final JdbcTemplate jdbc;
    private final HttpServletRequest request;
    private final UserRepository userRepository;

    public AuditLogService(
            JdbcTemplate jdbc,
            HttpServletRequest request,
            UserRepository userRepository) {

        this.jdbc = jdbc;
        this.request = request;
        this.userRepository = userRepository;
    }

    public void log(String actionType, String entityType, Long recordId, String description) {
        log(actionType, entityType, recordId, description, null, null);
    }

    public void log(
            String actionType,
            String entityType,
            Long recordId,
            String description,
            String oldValue,
            String newValue) {

        try {
            Integer currentUserId = getCurrentUserId();
            String ipAddress = getClientIpAddress();

            jdbc.update("""
                    INSERT INTO user_activity_log (
                        user_activity_log_user_id,
                        user_activity_log_action_type,
                        user_activity_log_entity_type,
                        archived_record_id,
                        user_activity_log_description,
                        user_activity_log_old_value,
                        user_activity_log_new_value,
                        user_activity_log_ip_address
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    currentUserId,
                    actionType,
                    entityType,
                    recordId,
                    description,
                    oldValue,
                    newValue,
                    ipAddress
            );

        } catch (Exception e) {
            System.out.println("[AUDIT LOG ERROR] " + e.getMessage());
        }
    }

    private Integer getCurrentUserId() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("No logged-in user found for audit log.");
        }

        String username = authentication.getName();

        if (username == null || username.isBlank() || username.equals("anonymousUser")) {
            throw new RuntimeException("No valid logged-in user found for audit log.");
        }

        User currentUser = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Logged-in user not found: " + username));

        return currentUser.getUserId();
    }

    private String getClientIpAddress() {
        try {
            String forwardedFor = request.getHeader("X-Forwarded-For");

            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }

            String realIp = request.getHeader("X-Real-IP");

            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }

            return request.getRemoteAddr();

        } catch (Exception e) {
            return "unknown";
        }
    }
}
