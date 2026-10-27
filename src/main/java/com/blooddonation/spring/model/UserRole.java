package com.blooddonation.spring.model;

public enum UserRole {
    RECIPIENT("Recipient / Patient"),
    DONOR("Donor"),
    HOSPITAL_COORDINATOR("Hospital Coordinator"),
    QA_OFFICER("Quality Assurance Officer"),
    DELIVERY_OFFICER("Delivery Coordination Officer"),
    BLOOD_BANK_MANAGER("Blood Bank Manager"),
    IT_ADMIN("IT Operations Supervisor");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
