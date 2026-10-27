package com.blooddonation.spring.model;

import jakarta.persistence.*;

@Entity
@Table(name = "permission_configs")
public class PermissionConfig {

    @Id
    @Column(length = 30)
    private String role;

    @Column(nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String permissions;

    public PermissionConfig() {}

    public PermissionConfig(String role, String permissions) {
        this.role = role;
        this.permissions = permissions;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getPermissions() { return permissions; }
    public void setPermissions(String permissions) { this.permissions = permissions; }
}
