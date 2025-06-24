package uk.gegc.kidsgptbackend.service.email;

public interface EmailService {
    void sendPasswordResetEmail(String to, String resetToken, String username);

    void sendPasswordResetConfirmation(String to, String username);
} 