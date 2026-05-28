// Path: src/main/java/com/pdts/repository/UserRepository.java
package com.pdts.repository;

import com.pdts.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmailAddress(String emailAddress);
}
