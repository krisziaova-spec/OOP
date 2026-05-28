// Path: src/main/java/com/pdts/service/RequirementService.java
package com.pdts.service;

import com.pdts.model.Requirement;
import com.pdts.repository.RequirementRepository;
import com.pdts.repository.RejectionReasonRepository;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RequirementService {

        @Autowired
        private RequirementRepository requirementRepo;

        @Autowired
        private RejectionReasonRepository rejectionReasonRepo;

        @Autowired
        private TrackingNumberService trackingNumberService;

        @Autowired
        private EmailService emailService;

        private final com.pdts.config.PdtsProperties pdtsProperties;

        public RequirementService(com.pdts.config.PdtsProperties pdtsProperties) {
                this.pdtsProperties = pdtsProperties;
        }

        @Transactional
        public Requirement uploadDocument(int applicationId, int typeId,
                        MultipartFile file, int uploadedByUserId) throws IOException {
                String originalName = file.getOriginalFilename();
                String ext = originalName != null && originalName.contains(".")
                                ? originalName.substring(originalName.lastIndexOf('.'))
                                : ".jpg";

                String savedName = UUID.randomUUID() + ext;
                Path uploadPath = Paths.get(pdtsProperties.getUploadDir());

                Files.createDirectories(uploadPath);
                Path filePath = uploadPath.resolve(savedName);
                Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

                String trackingNo = trackingNumberService.generateDocumentNumber();

                Requirement req = new Requirement();
                req.setApplicationId(applicationId);
                req.setRequirementTypeId(typeId);
                req.setRequirementStatusId(1);
                req.setTrackingNo(trackingNo);
                req.setFileName(originalName != null ? originalName : savedName);
                req.setImagePath(filePath.toString());
                req.setUploadedByUserId(uploadedByUserId);

                return requirementRepo.save(req);
        }

        @Transactional
        public Requirement markReceived(int requirementId, int processedByUserId,
                        String applicantEmail, String applicantName) {
                Requirement req = getById(requirementId);
                validateNotFinal(req);

                req.setRequirementStatusId(3);
                req.setDateReceived(LocalDateTime.now());
                req.setProcessedByUserId(processedByUserId);
                req.setProcessedAt(LocalDateTime.now());

                Requirement saved = requirementRepo.save(req);

                String docType = req.getRequirementType() != null
                                ? req.getRequirementType().getTypeName()
                                : "Document";
                emailService.sendDocumentStatusEmail(
                                applicantEmail, applicantName, docType,
                                req.getTrackingNo(), "Verified/Received", null);

                saved.setIsEmailSent(1);
                return requirementRepo.save(saved);
        }

        @Transactional
        public Requirement markRejected(int requirementId, int rejectionReasonId,
                        int processedByUserId,
                        String applicantEmail, String applicantName) {
                Requirement req = getById(requirementId);
                validateNotFinal(req);

                String reasonDesc = rejectionReasonRepo.findById(rejectionReasonId)
                                .orElseThrow(() -> new RuntimeException("Rejection reason not found"))
                                .getReasonDescription();

                req.setRequirementStatusId(4);
                req.setRejectionReasonId(rejectionReasonId);
                req.setRejectedByUserId(processedByUserId);
                req.setRejectedAt(LocalDateTime.now());
                req.setProcessedByUserId(processedByUserId);
                req.setProcessedAt(LocalDateTime.now());

                Requirement saved = requirementRepo.save(req);

                String docType = req.getRequirementType() != null
                                ? req.getRequirementType().getTypeName()
                                : "Document";
                emailService.sendDocumentStatusEmail(
                                applicantEmail, applicantName, docType,
                                req.getTrackingNo(), "Rejected", reasonDesc);

                saved.setIsEmailSent(1);
                return requirementRepo.save(saved);
        }

        @Transactional
        public Requirement markForResubmission(int requirementId, String remarks,
                        int processedByUserId) {
                Requirement req = getById(requirementId);
                validateNotFinal(req);

                req.setRequirementStatusId(5);
                req.setRemarks(remarks);
                req.setProcessedByUserId(processedByUserId);
                req.setProcessedAt(LocalDateTime.now());

                return requirementRepo.save(req);
        }

        public Map<String, Long> getStatusSummary() {
                Map<String, Long> summary = new HashMap<>();
                summary.put("pending", requirementRepo.countByStatus(1));
                summary.put("underReview", requirementRepo.countByStatus(2));
                summary.put("verified", requirementRepo.countByStatus(3));
                summary.put("rejected", requirementRepo.countByStatus(4));
                summary.put("forResubmission", requirementRepo.countByStatus(5));
                return summary;
        }

        public List<Requirement> findByApplicationId(int applicationId) {
                return requirementRepo.findByApplicationId(applicationId);
        }

        private Requirement getById(int id) {
                return requirementRepo.findById(id)
                                .orElseThrow(() -> new RuntimeException("Requirement not found: " + id));
        }

        private void validateNotFinal(Requirement req) {
                int statusId = req.getRequirementStatusId() != null
                                ? req.getRequirementStatusId()
                                : 0;
                if (statusId == 3 || statusId == 4) {
                        throw new IllegalStateException(
                                        "Cannot change status of a finalized document.");
                }
        }
}
