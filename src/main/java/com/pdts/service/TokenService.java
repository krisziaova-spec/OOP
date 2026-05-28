// Path: src/main/java/com/pdts/service/TokenService.java
package com.pdts.service;

import com.pdts.model.ApplicantAccessToken;
import com.pdts.model.Application;
import com.pdts.model.Applicant;
import com.pdts.repository.ApplicantAccessTokenRepository;
import com.pdts.repository.ApplicationRepository;
import com.pdts.repository.ApplicantRepository;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class TokenService {

    @Autowired
    private ApplicantAccessTokenRepository tokenRepo;

    @Autowired
    private ApplicationRepository applicationRepo;

    @Autowired
    private ApplicantRepository applicantRepo;

    @Autowired
    private EmailService emailService;

    private final com.pdts.config.PdtsProperties pdtsProperties;

    public TokenService(com.pdts.config.PdtsProperties pdtsProperties) {
        this.pdtsProperties = pdtsProperties;
    }

    @Transactional
    public void issueToken(Integer applicationId, Integer applicantId, Integer issuedByUserId) {
        String plainToken = generateSecureToken();
        String tokenHash = sha256Hex(plainToken);

        ApplicantAccessToken token = tokenRepo.findByApplicationId(applicationId)
                .orElse(new ApplicantAccessToken());

        token.setApplicationId(applicationId);
        token.setApplicantId(applicantId);
        token.setTokenHash(tokenHash);
        token.setTokenPrefix(plainToken.substring(0, 8));
        token.setTokenExpiresAt(LocalDateTime.now().plusDays(pdtsProperties.getTokenExpiryDays()));

        token.setTokenIsRevoked(0);
        token.setTokenIssuedByUserId(issuedByUserId);
        token.setTokenEmailSent(0);

        tokenRepo.save(token);

        Applicant applicant = applicantRepo.findById((int) applicantId)
                .orElseThrow(() -> new RuntimeException("Applicant not found: " + applicantId));
        Application application = applicationRepo.findById((int) applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));

        emailService.sendTokenEmail(
                applicant.getEmailAddress(),
                applicant.getFullName(),
                application.getReferenceNumber(),
                plainToken);

        token.setTokenEmailSent(1);
        tokenRepo.save(token);
    }

    public ApplicantAccessToken verifyToken(String referenceNo, String plainToken) {
        Application application = applicationRepo.findByReferenceNumber(referenceNo)
                .orElseThrow(() -> new IllegalArgumentException("Reference number not found."));

        ApplicantAccessToken token = tokenRepo.findByApplicationId(application.getApplicationId())
                .orElseThrow(() -> new IllegalArgumentException("No token found for this application."));

        if (token.isRevoked()) {
            throw new IllegalStateException("Access token has been revoked.");
        }
        if (token.isExpired()) {
            throw new IllegalStateException("Access token has expired. Contact the Registrar.");
        }

        String submittedHash = sha256Hex(plainToken);
        if (!submittedHash.equals(token.getTokenHash())) {
            throw new IllegalArgumentException("Invalid access token.");
        }

        token.setTokenLastUsedAt(LocalDateTime.now());
        token.setTokenUseCount(token.getTokenUseCount() + 1);
        tokenRepo.save(token);

        return token;
    }

    @Transactional
    public void revokeToken(Integer applicationId, Integer revokedByUserId) {
        ApplicantAccessToken token = tokenRepo.findByApplicationId(applicationId)
                .orElseThrow(() -> new RuntimeException("Token not found for application " + applicationId));
        token.setTokenIsRevoked(1);
        token.setTokenRevokedByUserId(revokedByUserId);
        token.setTokenRevokedAt(LocalDateTime.now());
        tokenRepo.save(token);
    }

    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
