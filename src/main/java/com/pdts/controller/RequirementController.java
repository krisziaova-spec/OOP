// Path: src/main/java/com/pdts/controller/RequirementController.java
package com.pdts.controller;

import com.pdts.service.RequirementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/requirements")
public class RequirementController {

    @Autowired
    private RequirementService requirementService;

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam Integer applicationId,
                                    @RequestParam Integer typeId,
                                    @RequestParam MultipartFile file,
                                    @RequestParam(defaultValue = "1") Integer uploadedByUserId) {
        try {
            var req = requirementService.uploadDocument(applicationId, typeId, file, uploadedByUserId);
            return ResponseEntity.ok(Map.of(
                "message",    "Document uploaded successfully.",
                "trackingNo", req.getTrackingNo(),
                "statusId",   req.getRequirementStatusId()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/receive")
    public ResponseEntity<?> receive(@PathVariable Integer id,
                                     @RequestParam(defaultValue = "1") Integer processedBy,
                                     @RequestParam String applicantEmail,
                                     @RequestParam String applicantName) {
        try {
            requirementService.markReceived(id, processedBy, applicantEmail, applicantName);
            return ResponseEntity.ok(Map.of("message", "Document marked as Verified/Received."));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Integer id,
                                    @RequestParam Integer rejectionReasonId,
                                    @RequestParam(defaultValue = "1") Integer processedBy,
                                    @RequestParam String applicantEmail,
                                    @RequestParam String applicantName) {
        try {
            requirementService.markRejected(id, rejectionReasonId, processedBy, applicantEmail, applicantName);
            return ResponseEntity.ok(Map.of("message", "Document rejected."));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/resubmit")
    public ResponseEntity<?> resubmit(@PathVariable Integer id,
                                      @RequestParam String remarks,
                                      @RequestParam(defaultValue = "1") Integer processedBy) {
        try {
            requirementService.markForResubmission(id, remarks, processedBy);
            return ResponseEntity.ok(Map.of("message", "Document flagged for resubmission."));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary() {
        return ResponseEntity.ok(requirementService.getStatusSummary());
    }
}
