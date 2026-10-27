package com.blooddonation.spring.repository;

import com.blooddonation.spring.model.DeliveryVehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeliveryVehicleRepository extends JpaRepository<DeliveryVehicle, String> {
    List<DeliveryVehicle> findByStatus(String status);
    boolean existsByVehicleNumber(String vehicleNumber);
}
