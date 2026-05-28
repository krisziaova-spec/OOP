// Path: src/main/java/com/pdts/config/PdtsProperties.java
package com.pdts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pdts")
public class PdtsProperties {

    private String uploadDir;
    private int bcryptStrength;
    private long tokenExpiryDays;
    private Portal portal = new Portal();

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public int getBcryptStrength() {
        return bcryptStrength;
    }

    public void setBcryptStrength(int bcryptStrength) {
        this.bcryptStrength = bcryptStrength;
    }

    public long getTokenExpiryDays() {
        return tokenExpiryDays;
    }

    public void setTokenExpiryDays(long tokenExpiryDays) {
        this.tokenExpiryDays = tokenExpiryDays;
    }

    public Portal getPortal() {
        return portal;
    }

    public void setPortal(Portal portal) {
        this.portal = portal;
    }

    public static class Portal {

        private int rateLimit;
        private int blockAfterFailures;
        private int blockWindowMinutes;
        private int blockDurationMinutes;

        public int getRateLimit() {
            return rateLimit;
        }

        public void setRateLimit(int rateLimit) {
            this.rateLimit = rateLimit;
        }

        public int getBlockAfterFailures() {
            return blockAfterFailures;
        }

        public void setBlockAfterFailures(int blockAfterFailures) {
            this.blockAfterFailures = blockAfterFailures;
        }

        public int getBlockWindowMinutes() {
            return blockWindowMinutes;
        }

        public void setBlockWindowMinutes(int blockWindowMinutes) {
            this.blockWindowMinutes = blockWindowMinutes;
        }

        public int getBlockDurationMinutes() {
            return blockDurationMinutes;
        }

        public void setBlockDurationMinutes(int blockDurationMinutes) {
            this.blockDurationMinutes = blockDurationMinutes;
        }
    }
}
