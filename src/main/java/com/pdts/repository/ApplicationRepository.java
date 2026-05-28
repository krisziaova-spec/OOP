// Path: src/main/java/com/pdts/repository/ApplicationRepository.java
package com.pdts.repository;

import com.pdts.model.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Integer> {
    Optional<Application> findByReferenceNumber(String referenceNumber);
    List<Application> findByApplicantId(Integer applicantId);
    boolean existsByReferenceNumber(String referenceNumber);
}
