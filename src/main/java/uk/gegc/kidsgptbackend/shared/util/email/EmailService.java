package uk.gegc.kidsgptbackend.shared.util.email;

public interface EmailService {
    void sendPasswordResetEmail(String to, String resetToken, String username);

    void sendPasswordResetConfirmation(String to, String username);
    
    void sendVerificationEmail(String to, String verificationCode);
}

