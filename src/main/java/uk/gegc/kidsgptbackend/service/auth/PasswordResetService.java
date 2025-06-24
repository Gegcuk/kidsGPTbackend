package uk.gegc.kidsgptbackend.service.auth;

import uk.gegc.kidsgptbackend.dto.auth.ForgotPasswordRequest;
import uk.gegc.kidsgptbackend.dto.auth.PasswordResetResponse;
import uk.gegc.kidsgptbackend.dto.auth.ResetPasswordRequest;

public interface PasswordResetService {
    PasswordResetResponse initiatePasswordReset(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    boolean validateResetToken(String token);
} 