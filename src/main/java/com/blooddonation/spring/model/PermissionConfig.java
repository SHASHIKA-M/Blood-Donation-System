package com.blooddonation.spring.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "permission_configs")
public class PermissionConfig {

    @Id
    @Column(length = 30)
    private String role;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false)
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
