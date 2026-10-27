package com.blooddonation.spring.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "blood_units", indexes = {
    @Index(name = "idx_unit_status", columnList = "status"),
    @Index(name = "idx_unit_group", columnList = "blood_group"),
    @Index(name = "idx_unit_expiry", columnList = "expires_at"),
    @Index(name = "idx_unit_status_expiry", columnList = "status, expires_at"),
    @Index(name = "idx_unit_donor", columnList = "donor_id"),
    @Index(name = "idx_unit_reserved", columnList = "reserved_for_request_id")
})
public class BloodUnit {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36) private String id;
    @Column(name = "blood_group", nullable = false, length = 5) private String bloodGroup;
    @Column(nullable = false, length = 30) private String component = "Whole Blood";
    @Column(nullable = false) private String branch = "Main Blood Bank";
    @Column(nullable = false, length = 30) private String status = "Pending Screening";
    private LocalDateTime collectedAt = LocalDateTime.now();
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    /** Donor this unit was collected from (real chain of custody). Nullable for legacy batches. */
    @Column(name = "donor_id", length = 36) private String donorId;
    /** Request this unit is reserved for (status Reserved). Null otherwise. */
    @Column(name = "reserved_for_request_id", length = 36) private String reservedForRequestId;
    private Boolean hivNonReactive;
    private Boolean hepatitisBNonReactive;
    private Boolean hepatitisCNonReactive;
    private Boolean syphilisNonReactive;
    private Boolean malariaNonReactive;
    private Boolean crossMatchCompatible;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String qualityNotes;
    private LocalDateTime screenedAt;

    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public String getBloodGroup() { return bloodGroup; } public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }
    public String getComponent() { return component; } public void setComponent(String component) { this.component = component; }
    public String getBranch() { return branch; } public void setBranch(String branch) { this.branch = branch; }
    public String getStatus() { return status; } public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCollectedAt() { return collectedAt; } public void setCollectedAt(LocalDateTime collectedAt) { this.collectedAt = collectedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; } public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public Boolean getHivNonReactive() { return hivNonReactive; } public void setHivNonReactive(Boolean value) { hivNonReactive = value; }
    public Boolean getHepatitisBNonReactive() { return hepatitisBNonReactive; } public void setHepatitisBNonReactive(Boolean value) { hepatitisBNonReactive = value; }
    public Boolean getHepatitisCNonReactive() { return hepatitisCNonReactive; } public void setHepatitisCNonReactive(Boolean value) { hepatitisCNonReactive = value; }
    public Boolean getSyphilisNonReactive() { return syphilisNonReactive; } public void setSyphilisNonReactive(Boolean value) { syphilisNonReactive = value; }
    public Boolean getMalariaNonReactive() { return malariaNonReactive; } public void setMalariaNonReactive(Boolean value) { malariaNonReactive = value; }
    public Boolean getCrossMatchCompatible() { return crossMatchCompatible; } public void setCrossMatchCompatible(Boolean value) { crossMatchCompatible = value; }
    public String getQualityNotes() { return qualityNotes; } public void setQualityNotes(String qualityNotes) { this.qualityNotes = qualityNotes; }
    public LocalDateTime getScreenedAt() { return screenedAt; } public void setScreenedAt(LocalDateTime screenedAt) { this.screenedAt = screenedAt; }
    public String getDonorId() { return donorId; } public void setDonorId(String donorId) { this.donorId = donorId; }
    public String getReservedForRequestId() { return reservedForRequestId; } public void setReservedForRequestId(String reservedForRequestId) { this.reservedForRequestId = reservedForRequestId; }
}
