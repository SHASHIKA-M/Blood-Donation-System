package com.blooddonation.spring.repository;

import com.blooddonation.spring.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, String> {

    List<Appointment> findByDonorIdOrderByScheduledAtDesc(String donorId);

    List<Appointment> findByStatusOrderByScheduledAtAsc(String status);
}
