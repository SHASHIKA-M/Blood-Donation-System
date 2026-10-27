package com.blooddonation.spring.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "requests", indexes = {
    @Index(name = "idx_req_status", columnList = "status"),
    @Index(name = "idx_req_blood_group", columnList = "blood_group"),
    @Index(name = "idx_req_needed", columnList = "needed_by"),
    @Index(name = "idx_req_city", columnList = "city")
})
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(name = "patient_name", nullable = false)
    private String patientName;

    @Column(name = "blood_group", nullable = false, length = 5)
    private String bloodGroup;

    @Column(length = 30)
    private String component = "Whole Blood";

    @Column(length = 15)
    private String urgency = "Routine";

    @Column(name = "request_type", length = 20)
    private String requestType = "Patient Request";

    private String city;

    private String hospital;

    @Column(nullable = false)
    private int units = 1;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "needed_by")
    private LocalDateTime neededBy;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(nullable = false, length = 20)
    private String status = "Pending";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }
    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getHospital() { return hospital; }
    public void setHospital(String hospital) { this.hospital = hospital; }
    public int getUnits() { return units; }
    public void setUnits(int units) { this.units = units; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public LocalDateTime getNeededBy() { return neededBy; }
    public void setNeededBy(LocalDateTime neededBy) { this.neededBy = neededBy; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
