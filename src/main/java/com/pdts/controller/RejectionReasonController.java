// Path: src/main/java/com/pdts/controller/RejectionReasonController.java
package com.pdts.controller;

import com.pdts.model.RejectionReason;
import com.pdts.repository.RejectionReasonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rejection-reasons")
public class RejectionReasonController {

    @Autowired
    private RejectionReasonRepository repo;

    @GetMapping("/active")
    public List<RejectionReason> listActive() {
        return repo.findByIsActive(1);
    }

    @GetMapping
    public List<RejectionReason> listAll() {
        return repo.findAll();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody RejectionReason reason) {
        reason.setIsActive(1);
        RejectionReason saved = repo.save(reason);

        // FIX: Null-safe retrieval of reasonId before passing to Map.of()
        Integer reasonId = saved.getReasonId();
        if (reasonId == null) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to retrieve saved ID."));
        }

        return ResponseEntity.ok(Map.of("message", "Reason created.", "id", reasonId));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivate(@PathVariable int id) {
        return repo.findById(id).map(r -> {
            r.setIsActive(0);
            repo.save(r);
            return ResponseEntity.ok(Map.of("message", "Rejection reason deactivated."));
        }).orElse(ResponseEntity.notFound().build());
    }
}
