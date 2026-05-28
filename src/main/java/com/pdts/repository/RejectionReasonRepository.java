// Path: src/main/java/com/pdts/repository/RejectionReasonRepository.java
package com.pdts.repository;

import com.pdts.model.RejectionReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RejectionReasonRepository extends JpaRepository<RejectionReason, Integer> {
    List<RejectionReason> findByIsActive(Integer isActive);
}
