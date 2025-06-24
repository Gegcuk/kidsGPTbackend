package uk.gegc.kidsgptbackend.util;

import java.security.SecureRandom;
import java.util.Base64;

public class TokenGenerator {
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_LENGTH = 32; // 256 bits
    
    public static String generateSecureToken() {
        byte[] tokenBytes = new byte[TOKEN_LENGTH];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
} 