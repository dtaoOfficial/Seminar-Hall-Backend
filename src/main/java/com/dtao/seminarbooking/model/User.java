package com.dtao.seminarbooking.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "users")
public class User {
    @Id
    private String id;

    private String name;       // Full Name
    private String department; // CSE-1, MCA, etc.
    private String email;
    private String phone;
    private String password;
    private String role;       // ADMIN / DEPARTMENT

    // New fields
    private String createdBy;  // userId or name who created this user
    private Instant createdAt; // timestamp of creation
    private boolean active = true; // account active/inactive flag

    public User() {}

    public User(String name, String department, String email, String phone, String password, String role, String createdBy) {
        this.name = name;
        this.department = department;
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.role = role;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.active = true;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
