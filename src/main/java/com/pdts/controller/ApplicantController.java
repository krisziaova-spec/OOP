// Path: src/main/java/com/pdts/controller/ApplicantController.java
package com.pdts.controller;

import com.pdts.model.Applicant;
import com.pdts.service.ApplicantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/applicants")
public class ApplicantController {

    @Autowired
    private ApplicantService applicantService;

    @GetMapping
    public List<Applicant> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String enrollment,
            @RequestParam(required = false) String region) {
        return applicantService.search(search, category, enrollment, region);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Applicant> getOne(@PathVariable Integer id) {
        return applicantService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Applicant applicant,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            applicant.setUserId(1);
            Applicant saved = applicantService.save(applicant);
            return ResponseEntity.ok(Map.of("applicantId", saved.getApplicantId(), "message", "Applicant created."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id,
            @RequestBody Applicant applicant) {
        try {
            Applicant updated = applicantService.update(id, applicant);
            return ResponseEntity.ok(Map.of("message", "Applicant updated.", "applicantId", updated.getApplicantId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
