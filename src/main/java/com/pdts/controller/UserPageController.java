package com.pdts.controller;

import com.pdts.service.AuditLogService;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Controller
public class UserPageController {

    private final JdbcTemplate jdbc;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final Path userPhotoUploadDir = Paths.get("uploads", "user-photos").toAbsolutePath().normalize();

    public UserPageController(
            JdbcTemplate jdbc,
            AuditLogService auditLogService,
            PasswordEncoder passwordEncoder
    ) {
        this.jdbc = jdbc;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/users")
    public String list(Model model) {
        model.addAttribute("users", jdbc.queryForList("""
                SELECT
                    u.user_id,
                    u.user_username,
                    u.user_first_name,
                    u.user_last_name,
                    u.user_email_address,
                    u.user_is_active,
                    r.role_name
                FROM app_user u
                JOIN role r
                    ON r.role_id = u.role_id
                ORDER BY u.user_id
                """));

        model.addAttribute("settings", jdbc.queryForList("""
                SELECT setting_key, setting_value, setting_label, setting_type, setting_options
                FROM system_setting
                WHERE setting_is_active = 1
                ORDER BY setting_key
                """));

        model.addAttribute("rejectionReasons", jdbc.queryForList("""
                SELECT rejection_reason_id, rejection_reason_name, rejection_reason_description, rejection_reason_is_active
                FROM rejection_reason
                ORDER BY rejection_reason_id
                """));

        model.addAttribute("campuses", jdbc.queryForList("""
                SELECT campus_id, campus_name, campus_address, campus_is_active
                FROM campus
                ORDER BY campus_name
                """));

        model.addAttribute("programs", jdbc.queryForList("""
                SELECT program_id, program_code, program_name, program_is_active
                FROM program
                ORDER BY program_code
                """));

        return "users";
    }

    @GetMapping("/users/new")
    public String newForm(Model model) {
        model.addAttribute("roles", jdbc.queryForList("""
                SELECT role_id, role_name
                FROM role
                WHERE NOT (
                    role_name = 'Head Admission'
                    AND (
                        SELECT COUNT(*)
                        FROM app_user au
                        JOIN role ar ON ar.role_id = au.role_id
                        WHERE ar.role_name = 'Head Admission'
                    ) >= 3
                )
                ORDER BY role_id
                """));

        return "user-form";
    }

    @PostMapping("/users")
    public String create(@RequestParam Map<String, String> form,
                         @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
                         RedirectAttributes ra) {
        try {
            String lastName = required(form, "lastName");
            String firstName = required(form, "firstName");
            String middleName = blankToNull(form.get("middleName"));
            Integer roleId = Integer.parseInt(required(form, "roleId"));

            Map<String, Object> selectedRole = jdbc.queryForMap("""
                    SELECT role_id, role_name
                    FROM role
                    WHERE role_id = ?
                    """, roleId);

            String selectedRoleName = String.valueOf(selectedRole.get("role_name"));

            if ("Head Admission".equalsIgnoreCase(selectedRoleName)) {
                Integer headCount = jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM app_user u
                        JOIN role r ON r.role_id = u.role_id
                        WHERE r.role_name = 'Head Admission'
                        """, Integer.class);

                if (headCount != null && headCount >= 3) {
                    throw new IllegalArgumentException("Maximum of three Head Admission accounts is allowed.");
                }
            }

            String email = required(form, "email").toLowerCase();
            String username = required(form, "username").toLowerCase();
            String password = required(form, "password");

            Integer usernameCount = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM app_user
                    WHERE LOWER(user_username) = LOWER(?)
                    """, Integer.class, username);

            if (usernameCount != null && usernameCount > 0) {
                throw new IllegalArgumentException("Username already exists.");
            }

            Integer emailCount = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM app_user
                    WHERE LOWER(user_email_address) = LOWER(?)
                    """, Integer.class, email);

            if (emailCount != null && emailCount > 0) {
                throw new IllegalArgumentException("Email already exists.");
            }

            PhotoSaveResult photo = savePhotoLocally(photoFile, username);

            Integer userId = jdbc.queryForObject("""
                    INSERT INTO app_user (
                        user_last_name,
                        user_first_name,
                        user_middle_name,
                        role_id,
                        user_email_address,
                        user_password_hash,
                        user_username,
                        user_is_active,
                        user_photo_url,
                        user_photo_storage_path
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
                    RETURNING user_id
                    """,
                    Integer.class,
                    lastName,
                    firstName,
                    middleName,
                    roleId,
                    email,
                    passwordEncoder.encode(password),
                    username,
                    photo.url(),
                    photo.storagePath()
            );

            auditLogService.log(
                    "CREATE_ACCOUNT",
                    "app_user",
                    userId != null ? userId.longValue() : null,
                    "Created staff account for " + firstName + " " + lastName,
                    null,
                    "Username: " + username
            );

            ra.addFlashAttribute("success", "Staff account created successfully.");

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to create staff account: " + e.getMessage());
        }

        return "redirect:/users";
    }

    @PostMapping("/users/{id}/toggle")
    public String toggle(@PathVariable Integer id, RedirectAttributes ra) {
        try {
            Map<String, Object> userInfo = jdbc.queryForMap("""
                    SELECT
                        u.user_id,
                        u.user_username,
                        u.user_first_name,
                        u.user_last_name,
                        u.user_is_active,
                        r.role_name
                    FROM app_user u
                    JOIN role r ON r.role_id = u.role_id
                    WHERE u.user_id = ?
                    """, id);

            String username = String.valueOf(userInfo.get("user_username"));
            String roleName = String.valueOf(userInfo.get("role_name"));

            if ("admin".equalsIgnoreCase(username) || id == 1 || "Head Admission".equalsIgnoreCase(roleName)) {
                throw new IllegalArgumentException("Head Admission account cannot be deactivated.");
            }

            Integer oldActiveStatus = ((Number) userInfo.get("user_is_active")).intValue();

            jdbc.update("""
                    UPDATE app_user
                    SET user_is_active = CASE
                        WHEN user_is_active = 1 THEN 0
                        ELSE 1
                    END
                    WHERE user_id = ?
                    """, id);

            Integer newActiveStatus = jdbc.queryForObject("""
                    SELECT user_is_active
                    FROM app_user
                    WHERE user_id = ?
                    """, Integer.class, id);

            String fullName = userInfo.get("user_first_name") + " " + userInfo.get("user_last_name");

            String oldValue = oldActiveStatus == 1 ? "Active" : "Inactive";
            String newValue = newActiveStatus != null && newActiveStatus == 1 ? "Active" : "Inactive";

            auditLogService.log(
                    "UPDATE_USER_STATUS",
                    "app_user",
                    id.longValue(),
                    "Updated staff account status for " + fullName + " (" + username + ") from " + oldValue + " to " + newValue,
                    oldValue,
                    newValue
            );

            ra.addFlashAttribute("success", "Staff account status updated successfully.");

        } catch (EmptyResultDataAccessException e) {
            ra.addFlashAttribute("error", "Staff account not found.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to update staff account status: " + e.getMessage());
        }

        return "redirect:/users";
    }


    @PostMapping("/users/{id}/reset-password")
    public String resetPassword(@PathVariable Integer id,
                                @RequestParam("newPassword") String newPassword,
                                @RequestParam("confirmPassword") String confirmPassword,
                                RedirectAttributes ra) {
        try {
            String password = newPassword == null ? "" : newPassword.trim();
            String confirm = confirmPassword == null ? "" : confirmPassword.trim();

            if (password.length() < 8) {
                throw new IllegalArgumentException("Password must be at least 8 characters.");
            }

            if (!password.equals(confirm)) {
                throw new IllegalArgumentException("Password confirmation does not match.");
            }

            Map<String, Object> userInfo = jdbc.queryForMap("""
                    SELECT
                        u.user_id,
                        u.user_username,
                        u.user_first_name,
                        u.user_last_name,
                        u.user_email_address,
                        r.role_name
                    FROM app_user u
                    JOIN role r ON r.role_id = u.role_id
                    WHERE u.user_id = ?
                    """, id);

            jdbc.update("""
                    UPDATE app_user
                    SET user_password_hash = ?
                    WHERE user_id = ?
                    """, passwordEncoder.encode(password), id);

            String username = String.valueOf(userInfo.get("user_username"));
            String fullName = userInfo.get("user_first_name") + " " + userInfo.get("user_last_name");

            auditLogService.log(
                    "RESET_PASSWORD",
                    "app_user",
                    id.longValue(),
                    "Reset password for staff account " + fullName + " (" + username + ").",
                    "Protected password value",
                    "Password reset by Head Admission"
            );

            ra.addFlashAttribute("success", "Password reset successfully for " + fullName + ".");

        } catch (EmptyResultDataAccessException e) {
            ra.addFlashAttribute("error", "Staff account not found.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Failed to reset password: " + e.getMessage());
        }

        return "redirect:/users?tab=users";
    }

    @PostMapping("/profile/upload-photo")
    public String uploadOwnPhoto(@RequestParam("photo") MultipartFile photo,
                                 Principal principal,
                                 HttpSession session,
                                 RedirectAttributes ra) {
        try {
            if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
                throw new IllegalArgumentException("Logged-in user not found.");
            }

            String username = principal.getName();
            PhotoSaveResult savedPhoto = savePhotoLocally(photo, username);

            if (savedPhoto.url() == null || savedPhoto.storagePath() == null) {
                throw new IllegalArgumentException("Please choose a photo.");
            }

            jdbc.update("""
                    UPDATE app_user
                    SET user_photo_url = ?,
                        user_photo_storage_path = ?
                    WHERE LOWER(user_username) = LOWER(?)
                    """,
                    savedPhoto.url(),
                    savedPhoto.storagePath(),
                    username
            );

            session.setAttribute("loggedInUserPhoto", savedPhoto.url());
            ra.addFlashAttribute("success", "Profile photo updated.");

        } catch (Exception e) {
            ra.addFlashAttribute("error", "Photo upload failed: " + e.getMessage());
        }

        return "redirect:/users";
    }

    @GetMapping("/uploads/user-photos/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> serveUserPhoto(@PathVariable String filename) throws MalformedURLException {
        Path file = userPhotoUploadDir.resolve(filename).normalize();

        if (!file.startsWith(userPhotoUploadDir) || !Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(file.toUri());
        String contentType = "application/octet-stream";

        try {
            String detectedType = Files.probeContentType(file);
            if (detectedType != null && detectedType.startsWith("image/")) {
                contentType = detectedType;
            }
        } catch (IOException ignored) {
            // Keep safe default content type.
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    private PhotoSaveResult savePhotoLocally(MultipartFile photoFile, String username) throws IOException {
        if (photoFile == null || photoFile.isEmpty()) {
            return new PhotoSaveResult(null, null);
        }

        String contentType = photoFile.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed.");
        }

        String extension = getFileExtension(photoFile.getOriginalFilename());
        if (!isAllowedImageExtension(extension)) {
            throw new IllegalArgumentException("Allowed photo types: JPG, JPEG, PNG, WEBP, or GIF.");
        }

        Files.createDirectories(userPhotoUploadDir);

        String safeUsername = username == null ? "user" : username.replaceAll("[^A-Za-z0-9._-]", "_");
        String filename = safeUsername + "-" + UUID.randomUUID() + "." + extension;
        Path target = userPhotoUploadDir.resolve(filename).normalize();

        if (!target.startsWith(userPhotoUploadDir)) {
            throw new IllegalArgumentException("Invalid photo filename.");
        }

        Files.copy(photoFile.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return new PhotoSaveResult("/uploads/user-photos/" + filename, filename);
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

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "jpg";
        }

        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private boolean isAllowedImageExtension(String extension) {
        return "jpg".equals(extension)
                || "jpeg".equals(extension)
                || "png".equals(extension)
                || "webp".equals(extension)
                || "gif".equals(extension);
    }

    private record PhotoSaveResult(String url, String storagePath) {}
}
