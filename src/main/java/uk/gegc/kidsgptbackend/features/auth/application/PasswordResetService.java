package uk.gegc.kidsgptbackend.features.auth.application;

import uk.gegc.kidsgptbackend.features.auth.api.dto.ForgotPasswordRequest;
import uk.gegc.kidsgptbackend.features.auth.api.dto.PasswordResetResponse;
import uk.gegc.kidsgptbackend.features.auth.api.dto.ResetPasswordRequest;

public interface PasswordResetService {
    PasswordResetResponse initiatePasswordReset(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    boolean validateResetToken(String token);
} 