// Path: src/main/java/com/pdts/model/Application.java
package com.pdts.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "application")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Integer applicationId;

    @Column(name = "applicant_id", nullable = false)
    private Integer applicantId;

    @Column(name = "program_id", nullable = false)
    private Integer programId;

    @Column(name = "campus_id", nullable = false)
    private Integer campusId;

    @Column(name = "application_status_id", nullable = false)
    private Integer applicationStatusId;

    @Column(name = "deadline_id")
    private Integer deadlineId;

    @Column(name = "application_date", nullable = false)
    private LocalDate applicationDate;

    @Column(name = "application_semester", nullable = false, length = 20)
    private String semester;

    @Column(name = "application_academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "application_reference_number", nullable = false, unique = true, length = 30)
    private String referenceNumber;

    @Column(name = "application_last_notified_date")
    private LocalDateTime lastNotifiedDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "applicant_id", insertable = false, updatable = false)
    private Applicant applicant;

    // ── Getters & Setters ──

    public Integer getApplicationId() { return applicationId; }
    public void setApplicationId(Integer applicationId) { this.applicationId = applicationId; }

    public Integer getApplicantId() { return applicantId; }
    public void setApplicantId(Integer applicantId) { this.applicantId = applicantId; }

    public Integer getProgramId() { return programId; }
    public void setProgramId(Integer programId) { this.programId = programId; }

    public Integer getCampusId() { return campusId; }
    public void setCampusId(Integer campusId) { this.campusId = campusId; }

    public Integer getApplicationStatusId() { return applicationStatusId; }
    public void setApplicationStatusId(Integer applicationStatusId) { this.applicationStatusId = applicationStatusId; }

    public Integer getDeadlineId() { return deadlineId; }
    public void setDeadlineId(Integer deadlineId) { this.deadlineId = deadlineId; }

    public LocalDate getApplicationDate() { return applicationDate; }
    public void setApplicationDate(LocalDate applicationDate) { this.applicationDate = applicationDate; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public LocalDateTime getLastNotifiedDate() { return lastNotifiedDate; }
    public void setLastNotifiedDate(LocalDateTime lastNotifiedDate) { this.lastNotifiedDate = lastNotifiedDate; }

    public Applicant getApplicant() { return applicant; }
    public void setApplicant(Applicant applicant) { this.applicant = applicant; }
}
