// Path: src/main/java/com/pdts/service/TrackingNumberService.java
package com.pdts.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Year;

@Service
public class TrackingNumberService {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public String generateStudentNumber() {
        return generate("student", "STU");
    }

    @Transactional
    public String generateDocumentNumber() {
        return generate("document", "DOC");
    }

    private String generate(String entityType, String prefix) {
        int currentYear = Year.now().getValue();

        var row = em.createNativeQuery(
            "SELECT tracking_sequences_last_sequence, tracking_sequences_current_year " +
            "FROM tracking_sequences WHERE tracking_sequences_entity_type = :type FOR UPDATE"
        ).setParameter("type", entityType).getSingleResult();

        Object[] cols = (Object[]) row;
        int lastSeq = ((Number) cols[0]).intValue();
        int seqYear = ((Number) cols[1]).intValue();

        int nextSeq;
        if (seqYear != currentYear) {
            nextSeq = 1;
            em.createNativeQuery(
                "UPDATE tracking_sequences SET tracking_sequences_last_sequence = :seq, " +
                "tracking_sequences_current_year = :year WHERE tracking_sequences_entity_type = :type"
            ).setParameter("seq", nextSeq)
             .setParameter("year", currentYear)
             .setParameter("type", entityType)
             .executeUpdate();
        } else {
            nextSeq = lastSeq + 1;
            em.createNativeQuery(
                "UPDATE tracking_sequences SET tracking_sequences_last_sequence = :seq " +
                "WHERE tracking_sequences_entity_type = :type"
            ).setParameter("seq", nextSeq)
             .setParameter("type", entityType)
             .executeUpdate();
        }

        return String.format("%s-%d-%04d", prefix, currentYear, nextSeq);
    }
}
