package com.blooddonation.spring.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_orders")
public class DeliveryOrder {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36) private String id;
    @OneToOne(optional = false) @JoinColumn(name = "request_id", nullable = false, unique = true)
    private Request request;
    @Column(nullable = false) private String courierName;
    @Column(nullable = false) private String vehicleNumber;
    private LocalDateTime scheduledAt;
    private LocalDateTime deliveredAt;
    @Column(nullable = false, length = 25) private String status = "Scheduled";
    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    public String getId() { return id; } public Request getRequest() { return request; } public void setRequest(Request request) { this.request = request; }
    public String getCourierName() { return courierName; } public void setCourierName(String courierName) { this.courierName = courierName; }
    public String getVehicleNumber() { return vehicleNumber; } public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }
    public LocalDateTime getScheduledAt() { return scheduledAt; } public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; } public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; } public void setNotes(String notes) { this.notes = notes; }
}
