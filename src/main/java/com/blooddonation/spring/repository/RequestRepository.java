package com.blooddonation.spring.repository;

import com.blooddonation.spring.model.Request;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestRepository extends JpaRepository<Request, String> {

    List<Request> findByStatus(String status);

    List<Request> findByBloodGroup(String bloodGroup);

    // Real-world: counts + filtered queries without loading full table
    long countByStatus(String status);

    List<Request> findTop3ByStatusOrderByNeededByAsc(String status);
}
