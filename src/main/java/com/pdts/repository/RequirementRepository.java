// Path: src/main/java/com/pdts/repository/RequirementRepository.java
package com.pdts.repository;

import com.pdts.model.Requirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RequirementRepository extends JpaRepository<Requirement, Integer> {

    List<Requirement> findByApplicationId(Integer applicationId);

    List<Requirement> findByRequirementStatusId(Integer statusId);

    @Query("SELECT COUNT(r) FROM Requirement r WHERE r.requirementStatusId = :statusId")
    Long countByStatus(@Param("statusId") Integer statusId);

    @Query("SELECT r FROM Requirement r WHERE r.applicationId IN " +
           "(SELECT a.applicationId FROM Application a WHERE a.applicantId = :applicantId)")
    List<Requirement> findByApplicantId(@Param("applicantId") Integer applicantId);

    boolean existsByTrackingNo(String trackingNo);
}
