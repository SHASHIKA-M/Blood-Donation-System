package com.blooddonation.spring.repository;

import com.blooddonation.spring.model.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, String> {

    List<ContactMessage> findByStatus(String status);
}
