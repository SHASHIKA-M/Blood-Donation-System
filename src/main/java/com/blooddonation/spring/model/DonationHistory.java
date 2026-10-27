package com.blooddonation.spring.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "donation_history")
public class DonationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id", nullable = false)
    private Donor donor;

    @Column(name = "donation_date")
    private LocalDateTime date;

    @Column(length = 255)
    private String location;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String notes;

    public DonationHistory(LocalDateTime date, String location, String notes) {
        this.date = date;
        this.location = location;
        this.notes = notes;
    }

    public DonationHistory() { }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Donor getDonor() { return donor; }
    public void setDonor(Donor donor) { this.donor = donor; }
    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
