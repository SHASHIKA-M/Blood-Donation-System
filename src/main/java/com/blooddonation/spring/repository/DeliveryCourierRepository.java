package com.blooddonation.spring.repository;

import com.blooddonation.spring.model.DeliveryCourier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeliveryCourierRepository extends JpaRepository<DeliveryCourier, String> {
    List<DeliveryCourier> findByStatus(String status);
}
