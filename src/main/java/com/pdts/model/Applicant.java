// Path: src/main/java/com/pdts/model/Applicant.java
package com.pdts.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "applicant")
public class Applicant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "applicant_id")
    private Integer applicantId;

    @Column(name = "applicant_first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "applicant_middle_name", length = 50)
    private String middleName;

    @Column(name = "applicant_last_name", nullable = false, length = 50)
    private String lastName;

    @Column(name = "applicant_suffix", length = 20)
    private String suffix;

    @Column(name = "applicant_sex", nullable = false)
    private Integer sex;

    @Column(name = "applicant_civil_status", nullable = false)
    private Integer civilStatus;

    @Column(name = "applicant_house_number_street", length = 150)
    private String houseNumberStreet;

    @Column(name = "applicant_barangay", length = 100)
    private String barangay;

    @Column(name = "applicant_city_municipality", length = 100)
    private String cityMunicipality;

    @Column(name = "applicant_province", length = 100)
    private String province;

    @Column(name = "applicant_region", length = 100)
    private String region;

    @Column(name = "applicant_zip_code", length = 10)
    private String zipCode;

    @Column(name = "applicant_birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "applicant_email_address", nullable = false, length = 100)
    private String emailAddress;

    @Column(name = "applicant_contact_number", nullable = false, length = 20)
    private String contactNumber;

    @Column(name = "educational_background_category_id", nullable = false, length = 10)
    private String educationalBackgroundCategoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicant_enrollment_status", nullable = false)
    private EnrollmentStatus enrollmentStatus;

    @Column(name = "applicant_is_protected", nullable = false)
    private Integer isProtected = 1;

    @Column(name = "applicant_created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "applicant_updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "previous_education_id")
    private Integer previousEducationId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum EnrollmentStatus {
        on_leave, continuing
    }

    public String getFullName() {
        StringBuilder sb = new StringBuilder();
        sb.append(firstName).append(" ");
        if (middleName != null && !middleName.isBlank()) {
            sb.append(middleName).append(" ");
        }
        sb.append(lastName);
        if (suffix != null && !suffix.isBlank()) {
            sb.append(" ").append(suffix);
        }
        return sb.toString().trim();
    }

    // ── Getters & Setters ──

    public Integer getApplicantId() { return applicantId; }
    public void setApplicantId(Integer applicantId) { this.applicantId = applicantId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getSuffix() { return suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix; }

    public Integer getSex() { return sex; }
    public void setSex(Integer sex) { this.sex = sex; }

    public Integer getCivilStatus() { return civilStatus; }
    public void setCivilStatus(Integer civilStatus) { this.civilStatus = civilStatus; }

    public String getHouseNumberStreet() { return houseNumberStreet; }
    public void setHouseNumberStreet(String houseNumberStreet) { this.houseNumberStreet = houseNumberStreet; }

    public String getBarangay() { return barangay; }
    public void setBarangay(String barangay) { this.barangay = barangay; }

    public String getCityMunicipality() { return cityMunicipality; }
    public void setCityMunicipality(String cityMunicipality) { this.cityMunicipality = cityMunicipality; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }

    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }

    public String getEducationalBackgroundCategoryId() { return educationalBackgroundCategoryId; }
    public void setEducationalBackgroundCategoryId(String id) { this.educationalBackgroundCategoryId = id; }

    public EnrollmentStatus getEnrollmentStatus() { return enrollmentStatus; }
    public void setEnrollmentStatus(EnrollmentStatus enrollmentStatus) { this.enrollmentStatus = enrollmentStatus; }

    public Integer getIsProtected() { return isProtected; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public Integer getPreviousEducationId() { return previousEducationId; }
    public void setPreviousEducationId(Integer previousEducationId) { this.previousEducationId = previousEducationId; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
}
