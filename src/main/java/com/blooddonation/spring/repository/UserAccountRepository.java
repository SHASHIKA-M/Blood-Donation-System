package com.blooddonation.spring.repository;

import com.blooddonation.spring.model.UserAccount;
import com.blooddonation.spring.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {
    Optional<UserAccount> findByUsername(String username);
    List<UserAccount> findByRole(UserRole role);
    boolean existsByUsername(String username);
    Optional<UserAccount> findByDonorId(String donorId);
}
