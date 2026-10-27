package com.blooddonation.spring.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "donors", indexes = {
    @Index(name = "idx_donor_blood_group", columnList = "blood_group"),
    @Index(name = "idx_donor_city", columnList = "city"),
    @Index(name = "idx_donor_created", columnList = "created_at")
})
public class Donor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(name = "blood_group", nullable = false, length = 5)
    private String bloodGroup;

    private String city;

    private String province;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    @Column(nullable = false, length = 20)
    private String availability = "Available";

    @Column(name = "allow_call")
    private boolean allowCall = false;

    @Column(name = "last_donated_at")
    private LocalDateTime lastDonatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Embedded availability slots as a child table
    // BatchSize avoids N+1 selects when listing many donors (kept EAGER so
    // Thymeleaf works with open-in-view=false).
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "donor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<AvailabilitySlot> availabilitySlots = new ArrayList<>();

    // Embedded donation history as a child table
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "donor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<DonationHistory> donationHistory = new ArrayList<>();

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
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }
    public boolean isAllowCall() { return allowCall; }
    public void setAllowCall(boolean allowCall) { this.allowCall = allowCall; }
    public LocalDateTime getLastDonatedAt() { return lastDonatedAt; }
    public void setLastDonatedAt(LocalDateTime lastDonatedAt) { this.lastDonatedAt = lastDonatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<AvailabilitySlot> getAvailabilitySlots() { return availabilitySlots; }
    public void setAvailabilitySlots(List<AvailabilitySlot> availabilitySlots) { this.availabilitySlots = availabilitySlots; }
    public List<DonationHistory> getDonationHistory() { return donationHistory; }
    public void setDonationHistory(List<DonationHistory> donationHistory) { this.donationHistory = donationHistory; }
}
