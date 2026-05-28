// Path: src/main/java/com/pdts/controller/PublicPortalController.java
package com.pdts.controller;

import com.pdts.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/portal")
public class PublicPortalController {

    @Autowired
    private TokenService tokenService;

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody Map<String, String> body) {
        String referenceNo = body.get("referenceNo");
        String token = body.get("token");

        if (referenceNo == null || token == null || referenceNo.isBlank() || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reference number and token are required."));
        }

        try {
            tokenService.verifyToken(referenceNo.trim(), token.trim());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Token verified. Redirecting to your document status."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "Reference number not found."));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "error", "Invalid reference number or access token. Please check and try again."));
        }
    }
}
