// Path: src/main/java/com/pdts/model/RequirementStatus.java
package com.pdts.model;

import jakarta.persistence.*;

@Entity
@Table(name = "requirement_status")
public class RequirementStatus {

    @Id
    @Column(name = "status_id")
    private Integer statusId;

    @Column(name = "requirement_status_name", nullable = false, length = 50)
    private String statusName;

    @Column(name = "requirement_status_color", length = 10)
    private String statusColor;

    @Column(name = "requirement_status_desc", columnDefinition = "TEXT")
    private String statusDesc;

    @Column(name = "is_final")
    private Integer isFinal = 0;

    public Integer getStatusId() { return statusId; }
    public void setStatusId(Integer statusId) { this.statusId = statusId; }

    public String getStatusName() { return statusName; }
    public void setStatusName(String statusName) { this.statusName = statusName; }

    public String getStatusColor() { return statusColor; }
    public void setStatusColor(String statusColor) { this.statusColor = statusColor; }

    public String getStatusDesc() { return statusDesc; }
    public void setStatusDesc(String statusDesc) { this.statusDesc = statusDesc; }

    public Integer getIsFinal() { return isFinal; }
    public void setIsFinal(Integer isFinal) { this.isFinal = isFinal; }
}
