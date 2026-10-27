package com.blooddonation.spring.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Donation appointment booked by a donor (real blood-service flow:
 * register → book slot → donate → history).
 */
@Entity
@Table(name = "appointments", indexes = {
    @Index(name = "idx_appt_donor", columnList = "donor_id"),
    @Index(name = "idx_appt_status", columnList = "status"),
    @Index(name = "idx_appt_scheduled", columnList = "scheduled_at")
})
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(name = "donor_id", nullable = false, length = 36)
    private String donorId;

    @Column(name = "donor_name")
    private String donorName;

    private String phone;

    @Column(name = "blood_group", length = 5)
    private String bloodGroup;

    private String branch;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    /** Scheduled, Completed, Cancelled */
    @Column(nullable = false, length = 20)
    private String status = "Scheduled";

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDonorId() { return donorId; }
    public void setDonorId(String donorId) { this.donorId = donorId; }
    public String getDonorName() { return donorName; }
    public void setDonorName(String donorName) { this.donorName = donorName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
