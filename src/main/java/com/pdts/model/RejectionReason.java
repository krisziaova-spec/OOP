// Path: src/main/java/com/pdts/model/RejectionReason.java
package com.pdts.model;

import jakarta.persistence.*;

@Entity
@Table(name = "rejection_reason")
public class RejectionReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rejection_reason_id")
    private Integer reasonId;

    @Column(name = "rejection_reason_name", nullable = false, length = 100)
    private String reasonName;

    @Column(name = "rejection_reason_description", nullable = false, columnDefinition = "TEXT")
    private String reasonDescription;

    @Column(name = "rejection_reason_is_active")
    private Integer isActive = 1;

    public Integer getReasonId() { return reasonId; }
    public void setReasonId(Integer reasonId) { this.reasonId = reasonId; }

    public String getReasonName() { return reasonName; }
    public void setReasonName(String reasonName) { this.reasonName = reasonName; }

    public String getReasonDescription() { return reasonDescription; }
    public void setReasonDescription(String reasonDescription) { this.reasonDescription = reasonDescription; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
}
