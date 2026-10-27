package com.blooddonation.spring.repository;

import com.blooddonation.spring.model.Donor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DonorRepository extends JpaRepository<Donor, String> {

    Optional<Donor> findByPhone(String phone);

    boolean existsByPhone(String phone);

    // Real-world: DB-level filtering instead of findAll()+stream
    List<Donor> findByBloodGroupIgnoreCase(String bloodGroup);

    long countByAvailability(String availability);
}
