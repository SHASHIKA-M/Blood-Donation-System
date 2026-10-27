package com.blooddonation.spring.repository;
import com.blooddonation.spring.model.DeliveryOrder;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, String> { }
