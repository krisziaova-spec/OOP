// Path: src/main/java/com/pdts/repository/ApplicantRepository.java
package com.pdts.repository;

import com.pdts.model.Applicant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ApplicantRepository extends JpaRepository<Applicant, Integer> {

    List<Applicant> findByLastNameContainingIgnoreCase(String lastName);

    List<Applicant> findByEducationalBackgroundCategoryId(String categoryId);

    List<Applicant> findByEnrollmentStatus(Applicant.EnrollmentStatus status);

    List<Applicant> findByRegionContainingIgnoreCase(String region);

    @Query("SELECT a FROM Applicant a WHERE " +
           "(:lastName IS NULL OR LOWER(a.lastName) LIKE LOWER(CONCAT('%', :lastName, '%'))) AND " +
           "(:categoryId IS NULL OR a.educationalBackgroundCategoryId = :categoryId) AND " +
           "(:status IS NULL OR a.enrollmentStatus = :status) AND " +
           "(:region IS NULL OR LOWER(a.region) LIKE LOWER(CONCAT('%', :region, '%')))")
    List<Applicant> filterApplicants(
        @Param("lastName") String lastName,
        @Param("categoryId") String categoryId,
        @Param("status") Applicant.EnrollmentStatus status,
        @Param("region") String region
    );

    boolean existsByEmailAddress(String emailAddress);

    boolean existsByEmailAddressIgnoreCase(String emailAddress);
}

