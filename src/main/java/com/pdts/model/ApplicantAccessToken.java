// Path: src/main/java/com/pdts/model/ApplicantAccessToken.java
package com.pdts.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "applicant_access_token")
public class ApplicantAccessToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;

    @Column(name = "application_id", nullable = false, unique = true)
    private Integer applicationId;

    @Column(name = "applicant_id", nullable = false)
    private Integer applicantId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "token_prefix", nullable = false, length = 8)
    private String tokenPrefix;

    @Column(name = "token_issued_at", nullable = false)
    private LocalDateTime tokenIssuedAt;

    @Column(name = "token_expires_at", nullable = false)
    private LocalDateTime tokenExpiresAt;

    @Column(name = "token_is_revoked", nullable = false)
    private Integer tokenIsRevoked = 0;

    @Column(name = "token_last_used_at")
    private LocalDateTime tokenLastUsedAt;

    @Column(name = "token_use_count", nullable = false)
    private Integer tokenUseCount = 0;

    @Column(name = "token_email_sent", nullable = false)
    private Integer tokenEmailSent = 0;

    @Column(name = "token_issued_by_user_id", nullable = false)
    private Integer tokenIssuedByUserId;

    @Column(name = "token_revoked_by_user_id")
    private Integer tokenRevokedByUserId;

    @Column(name = "token_revoked_at")
    private LocalDateTime tokenRevokedAt;

    @PrePersist
    protected void onCreate() {
        tokenIssuedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(tokenExpiresAt);
    }

    public boolean isRevoked() {
        return tokenIsRevoked == 1;
    }

    // ── Getters & Setters ──

    public Long getTokenId() { return tokenId; }
    public void setTokenId(Long tokenId) { this.tokenId = tokenId; }

    public Integer getApplicationId() { return applicationId; }
    public void setApplicationId(Integer applicationId) { this.applicationId = applicationId; }

    public Integer getApplicantId() { return applicantId; }
    public void setApplicantId(Integer applicantId) { this.applicantId = applicantId; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public String getTokenPrefix() { return tokenPrefix; }
    public void setTokenPrefix(String tokenPrefix) { this.tokenPrefix = tokenPrefix; }

    public LocalDateTime getTokenIssuedAt() { return tokenIssuedAt; }
    public void setTokenIssuedAt(LocalDateTime tokenIssuedAt) { this.tokenIssuedAt = tokenIssuedAt; }

    public LocalDateTime getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(LocalDateTime tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }

    public Integer getTokenIsRevoked() { return tokenIsRevoked; }
    public void setTokenIsRevoked(Integer tokenIsRevoked) { this.tokenIsRevoked = tokenIsRevoked; }

    public LocalDateTime getTokenLastUsedAt() { return tokenLastUsedAt; }
    public void setTokenLastUsedAt(LocalDateTime tokenLastUsedAt) { this.tokenLastUsedAt = tokenLastUsedAt; }

    public Integer getTokenUseCount() { return tokenUseCount; }
    public void setTokenUseCount(Integer tokenUseCount) { this.tokenUseCount = tokenUseCount; }

    public Integer getTokenEmailSent() { return tokenEmailSent; }
    public void setTokenEmailSent(Integer tokenEmailSent) { this.tokenEmailSent = tokenEmailSent; }

    public Integer getTokenIssuedByUserId() { return tokenIssuedByUserId; }
    public void setTokenIssuedByUserId(Integer tokenIssuedByUserId) { this.tokenIssuedByUserId = tokenIssuedByUserId; }

    public Integer getTokenRevokedByUserId() { return tokenRevokedByUserId; }
    public void setTokenRevokedByUserId(Integer tokenRevokedByUserId) { this.tokenRevokedByUserId = tokenRevokedByUserId; }

    public LocalDateTime getTokenRevokedAt() { return tokenRevokedAt; }
    public void setTokenRevokedAt(LocalDateTime tokenRevokedAt) { this.tokenRevokedAt = tokenRevokedAt; }
}
