// Path: src/main/java/com/pdts/model/User.java
package com.pdts.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "user_last_name", nullable = false, length = 50)
    private String lastName;

    @Column(name = "user_first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "user_middle_name", length = 50)
    private String middleName;

    @Column(name = "user_suffix", length = 20)
    private String suffix;

    @Column(name = "role_id", nullable = false)
    private Integer roleId;

    @Column(name = "user_email_address", nullable = false, unique = true, length = 100)
    private String emailAddress;

    @Column(name = "user_password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "user_is_active", nullable = false)
    private Integer isActive = 1;

    @Column(name = "user_created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "user_last_login")
    private LocalDateTime lastLogin;

    @Column(name = "user_username", nullable = false, unique = true, length = 50)
    private String username;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private Role role;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    // ── Getters & Setters ──

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }

    public String getSuffix() { return suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix; }

    public Integer getRoleId() { return roleId; }
    public void setRoleId(Integer roleId) { this.roleId = roleId; }

    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
