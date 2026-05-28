// Path: src/main/java/com/pdts/controller/AuthController.java
package com.pdts.controller;

import com.pdts.service.AuditLogService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
public class AuthController {

    private final JdbcTemplate jdbc;
    private final AuditLogService auditLogService;

    public AuthController(JdbcTemplate jdbc, AuditLogService auditLogService) {
        this.jdbc = jdbc;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            Model model) {
        if (error != null) {
            model.addAttribute("loginError", "Invalid credentials. Please try again.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "You have been signed out.");
        }
        return "login";
    }



    @GetMapping("/forgot-password")
    public String forgotPasswordPage(@RequestParam(value = "sent", required = false) String sent,
                                     Model model) {
        if (sent != null) {
            model.addAttribute(
                    "requestMessage",
                    "Password reset request submitted. Please ask the Head Admission user to reset your password from Settings > Manage Users."
            );
        }
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPasswordSubmit(@RequestParam("identifier") String identifier,
                                       RedirectAttributes ra) {
        String lookup = identifier == null ? "" : identifier.trim().toLowerCase();

        if (!lookup.isBlank()) {
            try {
                Map<String, Object> user = jdbc.queryForMap("""
                        SELECT
                            user_id,
                            user_username,
                            user_first_name,
                            user_last_name,
                            user_email_address
                        FROM app_user
                        WHERE LOWER(user_username) = LOWER(?)
                           OR LOWER(user_email_address) = LOWER(?)
                        LIMIT 1
                        """, lookup, lookup);

                Long userId = ((Number) user.get("user_id")).longValue();
                String username = String.valueOf(user.get("user_username"));
                String fullName = user.get("user_first_name") + " " + user.get("user_last_name");

                auditLogService.log(
                        "PASSWORD_RESET_REQUEST",
                        "app_user",
                        userId,
                        "Password reset requested from the login page for " + fullName + " (" + username + ").",
                        null,
                        "Pending Head Admission reset"
                );

            } catch (EmptyResultDataAccessException ignored) {
                // Keep the response generic so the login page does not reveal whether an account exists.
            } catch (Exception e) {
                System.out.println("[PDTS PASSWORD RESET REQUEST ERROR] " + e.getMessage());
            }
        }

        ra.addFlashAttribute(
                "requestMessage",
                "Password reset request submitted. Please ask the Head Admission user to reset your password from Settings > Manage Users."
        );
        return "redirect:/forgot-password?sent=true";
    }


    @GetMapping("/")
    public String root() {
        return "redirect:/login";
    }
}
