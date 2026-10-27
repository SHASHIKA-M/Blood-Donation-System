package com.blooddonation.spring.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_couriers")
public class DeliveryCourier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "license_number", nullable = false)
    private String licenseNumber;

    @Column(nullable = false)
    private String contact;

    @Column(name = "route_zone")
    private String routeZone;

    @Column(nullable = false, length = 20)
    private String status = "Available";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getRouteZone() { return routeZone; }
    public void setRouteZone(String routeZone) { this.routeZone = routeZone; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
