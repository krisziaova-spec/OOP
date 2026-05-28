// Path: src/main/java/com/pdts/model/Requirement.java
package com.pdts.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "requirement")
public class Requirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "requirement_id")
    private Integer requirementId;

    @Column(name = "application_id", nullable = false)
    private Integer applicationId;

    @Column(name = "requirement_type_id", nullable = false)
    private Integer requirementTypeId;

    @Column(name = "requirement_status_id", nullable = false)
    private Integer requirementStatusId = 1;

    @Column(name = "requirement_tracking_no", nullable = false, length = 30)
    private String trackingNo;

    @Column(name = "requirement_file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "requirement_image_path", nullable = false, columnDefinition = "TEXT")
    private String imagePath;

    @Column(name = "requirement_upload_date", nullable = false)
    private LocalDateTime uploadDate;

    @Column(name = "requirement_uploaded_by_user_id", nullable = false)
    private Integer uploadedByUserId;

    @Column(name = "requirement_date_received")
    private LocalDateTime dateReceived;

    @Column(name = "requirement_processed_by_user_id")
    private Integer processedByUserId;

    @Column(name = "requirement_processed_at")
    private LocalDateTime processedAt;

    @Column(name = "rejection_reason_id")
    private Integer rejectionReasonId;

    @Column(name = "rejection_reason_rejected_by_user_id")
    private Integer rejectedByUserId;

    @Column(name = "rejection_reason_rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "requirement_remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "requirement_is_email_sent")
    private Integer isEmailSent = 0;

    @Column(name = "requirement_has_archive_match")
    private Integer hasArchiveMatch = 0;

    @Column(name = "archive_id")
    private Long archiveId;

    // Joined fields (read-only from related tables)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "requirement_type_id", insertable = false, updatable = false)
    private RequirementType requirementType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "requirement_status_id", insertable = false, updatable = false)
    private RequirementStatus requirementStatus;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rejection_reason_id", insertable = false, updatable = false)
    private RejectionReason rejectionReason;

    @PrePersist
    protected void onCreate() {
        uploadDate = LocalDateTime.now();
    }

    // ── Getters & Setters ──

    public Integer getRequirementId() { return requirementId; }
    public void setRequirementId(Integer requirementId) { this.requirementId = requirementId; }

    public Integer getApplicationId() { return applicationId; }
    public void setApplicationId(Integer applicationId) { this.applicationId = applicationId; }

    public Integer getRequirementTypeId() { return requirementTypeId; }
    public void setRequirementTypeId(Integer requirementTypeId) { this.requirementTypeId = requirementTypeId; }

    public Integer getRequirementStatusId() { return requirementStatusId; }
    public void setRequirementStatusId(Integer requirementStatusId) { this.requirementStatusId = requirementStatusId; }

    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public LocalDateTime getUploadDate() { return uploadDate; }
    public void setUploadDate(LocalDateTime uploadDate) { this.uploadDate = uploadDate; }

    public Integer getUploadedByUserId() { return uploadedByUserId; }
    public void setUploadedByUserId(Integer uploadedByUserId) { this.uploadedByUserId = uploadedByUserId; }

    public LocalDateTime getDateReceived() { return dateReceived; }
    public void setDateReceived(LocalDateTime dateReceived) { this.dateReceived = dateReceived; }

    public Integer getProcessedByUserId() { return processedByUserId; }
    public void setProcessedByUserId(Integer processedByUserId) { this.processedByUserId = processedByUserId; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public Integer getRejectionReasonId() { return rejectionReasonId; }
    public void setRejectionReasonId(Integer rejectionReasonId) { this.rejectionReasonId = rejectionReasonId; }

    public Integer getRejectedByUserId() { return rejectedByUserId; }
    public void setRejectedByUserId(Integer rejectedByUserId) { this.rejectedByUserId = rejectedByUserId; }

    public LocalDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(LocalDateTime rejectedAt) { this.rejectedAt = rejectedAt; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public Integer getIsEmailSent() { return isEmailSent; }
    public void setIsEmailSent(Integer isEmailSent) { this.isEmailSent = isEmailSent; }

    public Integer getHasArchiveMatch() { return hasArchiveMatch; }
    public void setHasArchiveMatch(Integer hasArchiveMatch) { this.hasArchiveMatch = hasArchiveMatch; }

    public Long getArchiveId() { return archiveId; }
    public void setArchiveId(Long archiveId) { this.archiveId = archiveId; }

    public RequirementType getRequirementType() { return requirementType; }
    public RequirementStatus getRequirementStatus() { return requirementStatus; }
    public RejectionReason getRejectionReason() { return rejectionReason; }
}
