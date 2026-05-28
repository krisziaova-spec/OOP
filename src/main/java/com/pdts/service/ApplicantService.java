// Path: src/main/java/com/pdts/service/ApplicantService.java
package com.pdts.service;

import com.pdts.model.Applicant;
import com.pdts.repository.ApplicantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ApplicantService {

    private static final String DUMMY_MULTI_ACCOUNT_EMAIL = "krisziamaehpamintuan@iskolarngbayan.pup.edu.ph";

    @Autowired
    private ApplicantRepository applicantRepo;

    public List<Applicant> findAll() {
        return applicantRepo.findAll();
    }

    public Optional<Applicant> findById(int id) {
        return applicantRepo.findById(id);
    }

    public List<Applicant> search(String lastName, String categoryId,
            String enrollmentStatus, String region) {
        Applicant.EnrollmentStatus status = null;
        if (enrollmentStatus != null && !enrollmentStatus.isBlank()) {
            try {
                status = Applicant.EnrollmentStatus.valueOf(enrollmentStatus);
            } catch (IllegalArgumentException ignored) {
                // invalid value — treat as no filter
            }
        }
        return applicantRepo.filterApplicants(
                (lastName != null && !lastName.isBlank()) ? lastName : null,
                (categoryId != null && !categoryId.isBlank()) ? categoryId : null,
                status,
                (region != null && !region.isBlank()) ? region : null);
    }

    @Transactional
    public Applicant save(Applicant applicant) {
        applicant.setEmailAddress(normalizeEmail(applicant.getEmailAddress()));

        if (applicant.getApplicantId() == null
                && !isDummyMultiAccountEmail(applicant.getEmailAddress())
                && applicantRepo.existsByEmailAddressIgnoreCase(applicant.getEmailAddress())) {
            throw new IllegalArgumentException("Email address already registered. Only the approved dummy email may be reused for multiple test applicant accounts.");
        }
        return applicantRepo.save(applicant);
    }

    @Transactional
    public Applicant update(int id, Applicant updated) {
        Applicant existing = applicantRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Applicant not found: " + id));

        existing.setFirstName(updated.getFirstName());
        existing.setMiddleName(updated.getMiddleName());
        existing.setLastName(updated.getLastName());
        existing.setSuffix(updated.getSuffix());
        existing.setSex(updated.getSex());
        existing.setCivilStatus(updated.getCivilStatus());
        existing.setHouseNumberStreet(updated.getHouseNumberStreet());
        existing.setBarangay(updated.getBarangay());
        existing.setCityMunicipality(updated.getCityMunicipality());
        existing.setProvince(updated.getProvince());
        existing.setRegion(updated.getRegion());
        existing.setZipCode(updated.getZipCode());
        existing.setBirthDate(updated.getBirthDate());
        existing.setContactNumber(updated.getContactNumber());
        existing.setEnrollmentStatus(updated.getEnrollmentStatus());
        existing.setEducationalBackgroundCategoryId(
                updated.getEducationalBackgroundCategoryId());

        return applicantRepo.save(existing);
    }

    private boolean isDummyMultiAccountEmail(String email) {
        return email != null && DUMMY_MULTI_ACCOUNT_EMAIL.equalsIgnoreCase(email.trim());
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}

