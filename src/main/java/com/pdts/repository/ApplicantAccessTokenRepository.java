// Path: src/main/java/com/pdts/repository/ApplicantAccessTokenRepository.java
package com.pdts.repository;

import com.pdts.model.ApplicantAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ApplicantAccessTokenRepository extends JpaRepository<ApplicantAccessToken, Long> {
    Optional<ApplicantAccessToken> findByApplicationId(int applicationId);

    Optional<ApplicantAccessToken> findByApplicantId(int applicantId);
}
