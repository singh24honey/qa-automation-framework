package com.company.qa.util;

import org.apache.commons.codec.digest.DigestUtils;

import java.security.SecureRandom;
import java.util.Base64;

public class SecurityUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int API_KEY_LENGTH = 32; // 32 bytes = 256 bits

    /**
     * Generate a cryptographically secure API key
     */
    public static String generateApiKey() {
        byte[] randomBytes = new byte[API_KEY_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Hash an API key using SHA-256
     */
    public static String hashApiKey(String apiKey) {
        return DigestUtils.sha256Hex(apiKey);
    }

    /**
     * Verify an API key against its hash
     */
    public static boolean verifyApiKey(String apiKey, String hash) {
        String computedHash = hashApiKey(apiKey);
        return computedHash.equals(hash);
    }

    /**
     * Extract IP address from request, handling proxies
     */
    public static String getClientIpAddress(String xForwardedFor, String remoteAddr) {
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        return remoteAddr;
    }
}